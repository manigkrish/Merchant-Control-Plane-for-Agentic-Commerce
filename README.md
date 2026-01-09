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
   - TAP-compatible “profile” enforcement (required signature components, created/expires window, nonce)
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

---

## High-level architecture

- **Gateway (data plane)**: public edge for agent traffic (`/v1/agent/*`). Validates attestations, enforces replay protection and scoped tokens, orchestrates
deterministic decisions, and emits auditable events.
- **Admin (control plane)**: merchant onboarding and configuration (`/v1/admin/*`). Manages policies, keys, token administration, RBAC, and audit review.

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
│     └─ strategy.md
│
├─ libs/
│  ├─ platform-web/
│  ├─ common-security/
│  ├─ common-events/
│  └─ common-observability/
│
├─ services/
│  ├─ gateway-service/
│  ├─ admin-service/
│  ├─ agent-registry-service/
│  ├─ attestation-service/
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

Gateway (port 8080):

```bash
make run-gateway
```

Admin (port 8081) in another terminal:

```bash
make run-admin
```

---

## Health endpoints

Both services expose:

- `GET /healthz`
- `GET /readyz`

Examples:

```bash
curl -sS http://localhost:8080/healthz | jq .
curl -sS http://localhost:8081/healthz | jq .
```

---

## Error model (RFC 9457 Problem Details)

All services standardize errors as `application/problem+json` with a Problem Details body.

Sprint 1 includes a gateway-only verification endpoint:

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

What Sprint 1 tests validate:

- Gateway health endpoints
- Gateway RFC 9457 Problem Details behavior and correlation/trace invariants
- Admin health endpoints and correlation/trace header presence

---

## Standards and source-of-truth docs

- OpenAPI specs: `docs/openapi/`
- API errors (RFC 9457): `docs/contracts/api-errors.md`
- Observability (W3C Trace Context): `docs/contracts/observability.md`
- Events (CloudEvents): `docs/contracts/events.md`
- Signatures (RFC 9421 / TAP profile): `docs/contracts/` and ADRs under `docs/adr/`
- Architecture diagram: `docs/architecture/diagram.md`
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

- Internal auth (JWT + JWKS) and tenant derivation
- Attestation verification service + Redis replay cache
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
