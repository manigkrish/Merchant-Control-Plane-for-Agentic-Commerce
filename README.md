# AgentTrust Gateway (Agentic Commerce Trust + Scoped Tokens + Policy-RAG + Guardrailed Ops Agent) (with optional TAP verifier)

AgentTrust Gateway is a microservices-based backend platform (Java 21 / Spring Boot 3.5.9) that helps merchants and platforms validate
agent-initiated commerce requests, enforce least-privilege permissions, and produce auditable decisions with policy-backed explanations.

---

## What it solves

As automated agents increasingly browse and transact on behalf of users, merchants and platforms need to:

- verify requests are authentic (cryptographically signed) and not replayed
- enforce least-privilege permissions (scoped tokens bounded by action/amount/time/merchant)
- produce auditable decisions for disputes and governance
- support operations workflows via automation without expanding the attack surface

---

## Key capabilities (target state)

This repository is built sprint-by-sprint toward these capabilities:

1. **Cryptographic attestations + replay defense**
   - RFC 9421 HTTP Message Signatures verification
   - TAP-compatible “profile” enforcement (required covered components, required signature parameters, created/expires window, nonce)
   - Redis-backed nonce replay cache

2. **Scoped permission tokens + lifecycle events**
   - Issue / use / revoke with immutable audit trail
   - Lifecycle events emitted through Kafka using a CloudEvents envelope

3. **Policy-RAG explanations with citations**
   - Versioned merchant policy documents
   - Retrieval + explanation generation with citations to relevant policy chunks
   - LLM is used for explanations; authoritative allow/deny logic remains deterministic

4. **Guardrailed ops agent**
   - Allowlisted tools only
   - JSON schema validation for tool arguments
   - RBAC gating, idempotency keys, and audit logs for every action

5. **Operational readiness**
   - SLOs, dashboards, alerting
   - Blue/green deployments (ECS + CodeDeploy)
   - Load testing and failure-mode testing (LLM down, Redis down, Kafka backlog, DB failover)

---

## Current state (implemented)

### Sprint 0
- Documentation-first baseline: contracts, ADRs, threat model v0, environment/cost strategy.
- Public repo guardrails documented (no secrets, CI auth via OIDC).
- Standards anchored to real specs:
  - RFC 9457 Problem Details
  - W3C Trace Context
  - CloudEvents 1.0
  - RFC 9421 HTTP Message Signatures with TAP-compatible profile

### Sprint 1
- Maven multi-module monorepo (root parent + modules)
- Two runnable Spring Boot services:
  - `gateway-service` (port `8080`)
  - `admin-service` (port `8081`)
- Shared platform library:
  - `libs/platform-web`
  - RFC 9457 Problem Details (`application/problem+json`)
  - Correlation header `X-Correlation-Id` (preserve or generate)
  - W3C Trace Context headers `traceparent` / `tracestate` (preserve; generate traceparent if missing)
- Local infrastructure via Docker Compose:
  - Postgres + pgvector, Redis, Kafka (KRaft), MinIO
- Automated smoke tests:
  - Gateway: health + Problem Details behavior end-to-end
  - Admin: health + correlation/trace headers present

### Sprint 2
- **Control-plane auth MVP (Option A)**
  - `POST /v1/admin/auth/login` issues **RS256 JWTs** (default TTL: 15 minutes)
  - `GET /.well-known/jwks.json` serves **JWKS** for signature verification
  - Bootstrap platform admin supported via configuration/env (password stored as hash in DB)
- **Multi-tenant foundation**
  - Reserved platform tenant: `__platform__` (platform admin lives here)
  - Tenant context derived from verified JWT claim (`tenantId`)
- **Tenant management (platform admin only)**
  - `POST /v1/admin/tenants` create tenant (RBAC: `ROLE_PLATFORM_ADMIN`)
  - `GET /v1/admin/tenants` list tenants (RBAC: `ROLE_PLATFORM_ADMIN`)
- **Auditability (deterministic, DB-backed)**
  - Append-only `audit_log` table
  - `TENANT_CREATED` audit event recorded with:
    - actor subject + actor tenant id
    - correlation id + traceparent
    - JSON payload including tenantId + displayName
- **Production error model enforced**
  - 401/403 responses use RFC 9457 Problem Details (`application/problem+json`) with stable `errorCode`
- **Test posture hardened**
  - Admin tests use Testcontainers Postgres
  - Test key material is isolated under `target/` (no `.local/keys` dependency)

### Sprint 3
- **Data-plane attestation verification (RFC 9421) + replay defense**
  - New runnable service: `attestation-service` (port `8082`)
  - Gateway delegates attestation verification to `attestation-service`
- **Bodyless verification path (no Content-Digest yet)**
  - Gateway endpoint is bodyless and relies on request headers for signature verification
- **Profile enforcement (RFC 9421-correct)**
  - Required covered components include:
    - `@authority`, `@path`, and **`@signature-params`** (binds signature parameters)
  - Required signature parameters include:
    - `keyid`, `alg`, `created`, `expires`, `nonce`, `tag`
  - Allowed algorithm (Sprint 3): `ed25519`
  - Enforces created/expires window (default 8 minutes)
- **Tenant derivation and anti-spoofing invariant**
  - Gateway derives tenant from `Host` / `@authority` using a config map
  - Keys are tenant-scoped, and attestation verification enforces:
    - derived tenant must match the tenant attached to the resolved `keyid`
- **Replay defense**
  - Redis-backed nonce replay cache keyed by `(tenantId, keyid, nonce)` with TTL aligned to the window
  - Fails closed if replay cache is unavailable
- **Deterministic auditability (Sprint 3)**
  - Structured security audit logs + metrics-ready counters
  - DB-backed immutable audit trail for data-plane events comes later

---

## High-level architecture

- **Gateway (data plane)**: public edge for agent traffic (`/v1/agent/*`). Validates attestations, enforces replay protection and scoped tokens, orchestrates
deterministic decisions, and emits auditable events.
- **Admin (control plane)**: merchant onboarding and configuration (`/v1/admin/*`). Manages policies, keys, token administration, RBAC, and audit review.
- **Attestation (data plane internal)**: verifies HTTP Message Signatures and enforces replay defense.

The canonical architecture diagram is in `docs/architecture/diagram.md` (Mermaid).

---

## Repository layout

```text
agenttrust-gateway/
├─ pom.xml
├─ README.md
├─ .gitignore
├─ Makefile
├─ docs/
│  ├─ architecture/
│  │  ├─ overview.md
│  │  ├─ bounded-contexts.md
│  │  └─ diagram.md
│  ├─ openapi/
│  │  ├─ gateway.yaml
│  │  ├─ decision.yaml
│  │  └─ ...
│  ├─ adr/
│  ├─ contracts/
│  ├─ security/
│  ├─ cost/
│  ├─ environments/
│  ├─ runbooks/
│  └─ testing/
│     ├─ strategy.md
│     ├─ sprint-1.md
│     ├─ sprint-2.md
│     └─ sprint-3.md
│
├─ libs/
│  ├─ platform-web/
│
├─ services/
│  ├─ gateway-service/
│  ├─ admin-service/
│  ├─ attestation-service/
│  ├─ agent-registry-service/
│  ├─ decision-service/
│  ├─ token-service/
│  ├─ policy-service/
│  ├─ rag-service/
│  └─ ops-agent-service/
│
├─ workers/
│  └─ ingestion-worker/
│
├─ infra/
│  ├─ docker-compose/
│  ├─ terraform/
│  └─ scripts/
│
├─ tools/
│  └─ precommit/
│     └─ no-secrets.sh
│
└─ .github/
   └─ workflows/
```

Note: the runnable services implemented so far are `gateway-service`, `admin-service`, and `attestation-service`.

---

## Prerequisites (WSL-first)

Recommended environment: Windows 11 + WSL2 (Ubuntu) + Docker Desktop.

Toolchain:

- Java 21
- Maven
- Docker Desktop + Docker Compose
- Terraform
- AWS CLI
- `jq` (recommended)

Verify in WSL:

```bash
java -version
mvn -v
docker --version
docker compose version
terraform -version
aws --version
```

---

## Local infrastructure (Docker Compose)

Start:

```bash
make up
make ps
```

Tail logs:

```bash
make logs
```

Stop:

```bash
make down
```

Local ports:

- Postgres: `5432`
- Redis: `6379`
- Kafka: `9092`
- MinIO: `9000` (S3 API), `9001` (console)

See `infra/docker-compose/README.md` for details.

### Environment variables (local only)

A committed example file exists:

- `infra/docker-compose/.env.example`

To override locally (do not commit):

```bash
cp infra/docker-compose/.env.example infra/docker-compose/.env
```

---

## Run the services

Recommended local order (data plane first):

1. Redis (via Docker Compose)
2. `attestation-service`
3. `gateway-service`
4. `admin-service`

### Attestation (port 8082)

If Redis is running in Docker Compose and the service runs on your host, point Redis to `localhost`:

```bash
cd services/attestation-service
ATTESTATION_REDIS_HOST=localhost mvn -q spring-boot:run
```

### Gateway (port 8080)

In a second terminal:

```bash
cd services/gateway-service
AGENTTRUST_ATTESTATION_CLIENT_BASE_URL=http://localhost:8082 mvn -q spring-boot:run
```

### Admin (port 8081)

In a third terminal:

```bash
cd services/admin-service
mvn -q spring-boot:run
```

If you need to bootstrap the platform admin (first run on a clean DB), configure:

- `agenttrust.auth.bootstrap-admin.username`
- `agenttrust.auth.bootstrap-admin.password`

You can supply them via env vars or a local-only config mechanism. After bootstrapping, only the password hash is stored in DB.

---

## Health endpoints

All services expose:

- `GET /healthz`
- `GET /readyz`

Examples:

```bash
curl -sS http://localhost:8080/healthz | jq .
curl -sS http://localhost:8081/healthz | jq .
curl -sS http://localhost:8082/healthz | jq .
```

Note: `admin-service` readiness is designed to fail if the database is unavailable.

---

## Data-plane attestation verification (Sprint 3)

### Gateway entrypoint (bodyless)

Gateway exposes:

- `POST /v1/agent/verify`

This endpoint is **bodyless** and expects the signature headers:

- `Signature-Input`
- `Signature`

The gateway also derives tenant from `Host` / `@authority` using a config mapping.

In Sprint 3, the most reliable way to validate correctness end-to-end is to run tests, because generating a correct RFC 9421 signature by hand is easy to get wrong.

### Attestation internal API (JSON, internal-only)

Attestation service exposes:

- `POST /v1/attestations/verify`

Gateway calls this internally with a minimal payload (method, authority, path, tenantId, signature headers).

---

## Admin auth + tenants (Sprint 2)

### 1) Login (JWT)

```bash
curl -sS -X POST http://localhost:8081/v1/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"change-me-strong"}' | jq .
```

Response includes:

- `accessToken`
- `tokenType` (Bearer)

Store token:

```bash
TOKEN="$(curl -sS -X POST http://localhost:8081/v1/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"change-me-strong"}' | jq -r .accessToken)"
```

### 2) JWKS

```bash
curl -sS http://localhost:8081/.well-known/jwks.json | jq .
```

### 3) Create tenant (platform admin only)

```bash
curl -sS -X POST http://localhost:8081/v1/admin/tenants \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"tenant_demo","displayName":"Demo Tenant"}' | jq .
```

### 4) List tenants

```bash
curl -sS http://localhost:8081/v1/admin/tenants \
  -H "Authorization: Bearer ${TOKEN}" | jq .
```

### 5) Auth failures are RFC 9457 Problem Details

Unauthorized (missing/invalid token) and forbidden (missing role) return:

- `Content-Type: application/problem+json`
- `status` (401/403)
- `errorCode` (stable code for auditability/diagnostics)

---

## Audit log (Sprint 2)

Tenant creation writes an audit record to `audit_log`:

- `event_type = TENANT_CREATED`
- includes actor + correlation + traceparent + JSON payload

Example query (local Postgres):

```bash
psql "postgresql://agenttrust:agenttrust@localhost:5432/agenttrust" \
  -c "select event_type, tenant_id, actor_subject, correlation_id, occurred_at, payload_json from audit_log order by occurred_at desc limit 10;"
```

---

## Error model (RFC 9457 Problem Details)

All services standardize errors as `application/problem+json` with a Problem Details body.

Gateway includes a development-only verification endpoint:

- `GET /__debug/problem`

Test it:

```bash
curl -sS -D - http://localhost:8080/__debug/problem -o /tmp/problem.json
cat /tmp/problem.json | jq .
```

Expected:

- HTTP 400
- `Content-Type: application/problem+json`
- headers: `X-Correlation-Id`, `traceparent`
- body includes: `type`, `title`, `status`, `requestId`, `traceId`

Note: this debug endpoint is development-only and should not ship to production deployments.

---

## Testing

Run all tests:

```bash
make test
```

Run service tests only:

```bash
mvn -q -pl services/admin-service -am test
mvn -q -pl services/attestation-service -am test
mvn -q -pl services/gateway-service -am test
```

What Sprint 1 tests validate:

- Gateway health endpoints
- Gateway RFC 9457 Problem Details behavior and correlation/trace invariants
- Admin health endpoints and correlation/trace header presence

What Sprint 2 tests validate:

- Admin auth login issues JWT and JWKS is served
- RBAC enforcement returns RFC 9457 Problem Details (401/403) with stable error codes
- Tenant creation persists and tenant listing works
- `TENANT_CREATED` audit log is written with actor + correlation/trace + JSON payload
- Testcontainers-based Postgres integration tests (DB isolation)
- Test key material is isolated under `target/` (no `.local/keys` dependency)

What Sprint 3 tests validate:

- Attestation-service RFC 9421 verification behavior for `ed25519`
- Replay protection behavior with Redis (Testcontainers)
- Invalid signature returns RFC 9457 Problem Details with stable `errorCode`
- Gateway delegates to attestation-service and passes through Problem Details correctly

See:

- `docs/testing/sprint-1.md`
- `docs/testing/sprint-2.md`
- `docs/testing/sprint-3.md`

---

## Standards and source-of-truth docs

- OpenAPI specs: `docs/openapi/`
- API errors (RFC 9457): `docs/contracts/api-errors.md`
- Observability (W3C Trace Context): `docs/contracts/observability.md`
- Events (CloudEvents): `docs/contracts/events.md`
- Signatures (RFC 9421): ADRs under `docs/adr/` and security docs under `docs/security/`
- Architecture diagram: `docs/architecture/diagram.md`
- Runbooks: `docs/runbooks/`
- Security docs: `docs/security/`
- Environment strategy: `docs/environments/strategy.md`
- Cost controls: `docs/cost/controls.md`
- ADRs: `docs/adr/`

---

## Security and data handling (baseline)

- No secrets committed. Use local `.env` (gitignored) and GitHub Actions secrets where needed.
- CI-to-AWS authentication is planned via GitHub Actions OIDC (no long-lived AWS keys).
- Tenant context is first-class; tenant will be derived from verified identity (JWT) and never trusted from request body.
- LLM usage will include strict redaction and audit logging; only allowed data is sent to the provider.

See:

- `docs/security/repo-guardrails.md`
- `docs/security/ci-oidc.md`
- `docs/security/threat-model-v0.md`

---

## Roadmap (high level)

- Internal auth (JWT + JWKS) and tenant derivation (implemented)
- Attestation verification service + Redis replay cache (implemented)
- Agent registry (key resolution and rotation)
- Scoped token service + lifecycle events (Kafka/CloudEvents)
- Policy service + Python ingestion worker (chunking + embeddings + pgvector)
- RAG service for explanations with citations
- Decision service orchestration (deterministic ALLOW/CHALLENGE/DENY)
- Guardrailed ops agent service (allowlisted tools + schema validation + idempotency + audit)
- Deployment (ECS + CodeDeploy blue/green), dashboards/alerts, and failure testing

---

## License

MIT. See `LICENSE`.
