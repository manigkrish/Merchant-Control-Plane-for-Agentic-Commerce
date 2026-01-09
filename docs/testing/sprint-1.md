# Sprint 1 — Platform Foundation: Repo Spine, Service Baselines, and Operational Standards

Project: **AgentTrust Gateway (Agentic Commerce Trust + Scoped Tokens + Policy-RAG + Guardrailed Ops Agent) (with optional TAP verifier)**

Sprint 1 establishes the foundation required to ship AgentTrust as a real backend platform. The objective is to make the system **buildable, runnable, testable, and consistent**
from day one, so future sprints can add security primitives (RFC 9421 attestations, nonce replay defense, scoped tokens), policy versioning + RAG, and operational hardening without refactors.

This sprint intentionally focuses on **platform spine** over features: common standards, shared libraries, service templates, local infrastructure, and automated verification.

---

## Sprint goal

Deliver a baseline that guarantees:

- A consistent **multi-module build** and dependency management across all services.
- Two services that start reliably with standardized **liveness/readiness** endpoints.
- A shared web foundation enforcing:
  - **RFC 9457 Problem Details** (`application/problem+json`) for consistent errors
  - **W3C Trace Context** (`traceparent`/`tracestate`) propagation rules
  - A consistent **correlation ID** (`X-Correlation-Id`) for end-to-end request tracking
- Local infrastructure dependencies can be booted predictably with Docker Compose.
- Automated tests validate behavior so regressions are caught immediately.

---

## What shipped in Sprint 1

### 1) Multi-module Maven monorepo (standardized build)
- Root `pom.xml` is the parent + aggregator:
  - Java 21
  - Spring Boot **3.5.9**
  - Maven Enforcer rules to fail fast if toolchain is incorrect
- Modules included:
  - `libs/platform-web`
  - `services/gateway-service`
  - `services/admin-service`

**Why this matters**
- Platform work scales only if every service shares consistent build and dependency conventions.
- Central management prevents drift across services over time.

---

### 2) Shared platform library: `libs/platform-web`
This is the shared “web runtime baseline” used by all Spring services.

#### RFC 9457 Problem Details (canonical error format)
- Errors are returned using `application/problem+json`.
- Shared error model includes safe extensions for operations and support workflows:
  - `errorCode`
  - `traceId`
  - `requestId`
  - `tenantId` (reserved for later; must be derived from verified identity)

#### Global exception mapping (safe-by-default)
- Validation failures → `400`
- IllegalArgumentException → `400`
- Unhandled exceptions → `500` with a generic message (no stack traces or sensitive leakage)

#### Trace + correlation bootstrap (W3C Trace Context + X-Correlation-Id)
- Accepts `traceparent` / `tracestate` if present (W3C Trace Context).
- Generates a new `traceparent` if absent/invalid so every request has a trace ID.
- Preserves `X-Correlation-Id` if provided; otherwise generates one.
- Populates MDC (`traceId`, `correlationId`) so logs can be consistently correlated.

#### Auto-configuration enabled (no manual wiring per service)
- `platform-web` registers its filter and error handler via Spring Boot auto-configuration,
  ensuring consistent behavior across services by dependency inclusion alone.

---

### 3) Services baseline: Gateway and Admin

#### Gateway Service — `services/gateway-service`
- Port: **8080**
- Purpose (Sprint 1): establish the entry service template and operational conventions.
- Endpoints:
  - `GET /healthz` (liveness)
  - `GET /readyz` (readiness)

Temporary verification endpoint (Sprint 1 only):
- `GET /__debug/problem`
  - intentionally triggers a controlled error
  - used to verify Problem Details + correlation/trace behavior end-to-end

> This endpoint is a short-lived verification mechanism. It must be removed or gated before any production deployment path is enabled.

#### Admin Service — `services/admin-service`
- Port: **8081**
- Purpose (Sprint 1): establish the control-plane service template and operational conventions.
- Endpoints:
  - `GET /healthz`
  - `GET /readyz`

No admin debug endpoint by design. In Sprint 1 we validate RFC 9457 behavior through:
- gateway runtime behavior, and
- automated tests

---

### 4) Local infrastructure (Docker Compose)
Location: `infra/docker-compose/docker-compose.yml`

Dependencies started:
- Postgres + pgvector
- Redis
- Kafka (single-node KRaft)
- MinIO (S3-compatible local storage)

Also included:
- `infra/docker-compose/.env.example` (safe defaults; `.env` remains local-only)
- `infra/docker-compose/README.md` (how to run and troubleshoot)

**Why this matters**
- A platform is only real if it can be run locally and iterated quickly.
- A stable local stack enables integration work in later sprints (nonce replay caches, eventing, policy ingestion, etc.).

---

### 5) Developer workflow commands (Makefile)
Root Makefile provides simple, repeatable commands:
- `make up` / `make down` / `make ps` / `make logs`
- `make test`
- `make run-gateway`
- `make run-admin`

---

### 6) Automated tests (behavior validation)
No placeholder tests. Tests validate runtime expectations.

- Gateway smoke tests validate:
  - health endpoint responses
  - RFC 9457 Problem Details format from `/__debug/problem`
  - correlation + trace header invariants (`X-Correlation-Id`, `traceparent`)
  - requestId/traceId consistency between headers and body
- Admin smoke tests validate:
  - health endpoints
  - correlation + trace headers are present (platform-web is wired)

---

## Repository structure after Sprint 1

- `pom.xml` (root parent + aggregator)
- `libs/`
  - `platform-web/`
    - web baseline: Problem Details, trace/correlation filter, auto-config
- `services/`
  - `gateway-service/` (8080)
  - `admin-service/` (8081)
- `infra/docker-compose/`
  - docker-compose stack + env example + readme
- `docs/`
  - contracts, ADRs, security docs remain the source of truth established in Sprint 0

---

## How to run locally

### Prerequisites
In WSL Ubuntu:
- Java 21
- Maven
- Docker Desktop + Docker Compose
- `jq` recommended

Verify:
```bash
java -version
mvn -v
docker --version
docker compose version
```

### Start infrastructure

```bash
make up
make ps
```

Stop:

```bash
make down
```

### Run services

Gateway:

```bash
make run-gateway
```

Admin (in a separate terminal):

```bash
make run-admin
```

### Verify health endpoints

```bash
curl -sS http://localhost:8080/healthz | jq .
curl -sS http://localhost:8080/readyz  | jq .
curl -sS http://localhost:8081/healthz | jq .
curl -sS http://localhost:8081/readyz  | jq .
```

Expected:

- HTTP 200
- JSON includes `status`, `service`, `time`
- Response headers include `X-Correlation-Id` and `traceparent`

---

## Verify RFC 9457 Problem Details (Sprint 1)

### Runtime verification (Gateway only in Sprint 1)

```bash
curl -sS -D - http://localhost:8080/__debug/problem -o /tmp/problem.json
cat /tmp/problem.json | jq .
```

Expected:

- HTTP 400
- `Content-Type: application/problem+json`
- Headers include:

  - `X-Correlation-Id`
  - `traceparent`
- JSON body includes:

  - `type`, `title`, `status`, `errorCode`
  - `requestId` matches `X-Correlation-Id`
  - `traceId` matches the trace id component of `traceparent`

### Automated verification (authoritative)

```bash
make test
```

---

## Operational notes and scope boundaries

### Readiness semantics (Sprint 1)

- `/readyz` currently means: “service is running and can serve HTTP.”
- Later sprints will make readiness dependency-aware (DB/Redis/Kafka connectivity) once services depend on them.

### Logging/tracing (Sprint 1)

- Sprint 1 establishes trace/correlation headers and MDC keys.
- Structured logs and OpenTelemetry export will be added when we wire metrics/traces end-to-end (including deployment targets).

### Temporary endpoint policy

- `/__debug/problem` exists only as a short-lived verification endpoint.
- It must be removed or gated before enabling any production deployment workflow.

---

## Sprint 1 Exit Criteria

Sprint 1 is complete only when all of the following are true:

### Build and tests

- `make test` succeeds from repo root.
- Gateway and Admin tests pass consistently.

### Services run locally

- `make run-gateway` starts successfully on port 8080.
- `make run-admin` starts successfully on port 8081.
- `/healthz` and `/readyz` return HTTP 200 for both services.

### Standards are enforced by code

- Gateway error mapping returns `application/problem+json` for controlled errors.
- `X-Correlation-Id` and `traceparent` are present and validated by tests.
- Shared behavior is enforced via `libs/platform-web` auto-configuration.

### Local infrastructure is operational

- `make up` starts local infra without manual steps.
- `make ps` shows containers running and stable enough for continued development.

### Documentation is usable

- Root README contains the Sprint 1 quickstart instructions.
- `infra/docker-compose/README.md` exists and matches the real commands.

---

## Next sprint (preview only)

Sprint 2 will begin the security spine and control plane foundations:

- MVP internal auth (JWT issuance + JWKS) and verification in gateway/admin
- Tenant context derived from verified identity (never from request body)
- Remove or gate `/__debug/problem`
- Introduce initial protected endpoints and enforce standards end-to-end
