# Sprint 2 — Testing Notes (admin-service)

This document explains what Sprint 2 tests validate, how to run them locally, and how to troubleshoot common failures.

Sprint 2 adds production-oriented control-plane primitives to `admin-service`:

- internal auth (Option A) issuing RS256 JWTs
- JWKS endpoint for verification
- multi-tenant foundation with reserved platform tenant `__platform__`
- platform-admin RBAC for tenant management
- deterministic, append-only audit log in Postgres (`audit_log`)
- RFC 9457 Problem Details for 401/403 (with stable `errorCode`)

---

## What tests exist in Sprint 2

All Sprint 2 tests are under:

- `services/admin-service/src/test/java/...`

### 1) `AdminSmokeTest`
Purpose:
- verifies the service starts
- verifies `/healthz` and `/readyz` are reachable
- verifies correlation/tracing headers behavior is present (from platform-web)

Notes:
- readiness is expected to fail if the database is unavailable (this is intentional)

### 2) `TenantPersistenceIT`
Purpose:
- validates tenant persistence against a real Postgres instance (Testcontainers)
- validates the service layer can create and list tenants

What it proves:
- Flyway migrations work
- JPA mappings are correct
- repository/service logic is correct against a real database

### 3) `AdminAuthFlowIT`
Purpose:
End-to-end validation of:
- **401** for missing auth with RFC 9457 Problem Details (`application/problem+json`)
- login flow at `POST /v1/admin/auth/login`
- **platform admin RBAC** required for tenant creation
- **403** for non-platform admin with RFC 9457 Problem Details
- `TENANT_CREATED` audit event is persisted to `audit_log` with actor + correlation/trace + JSON payload

What it proves:
- security posture is real (not mocked)
- error contracts are stable
- auditability is enforced deterministically

---

## How to run Sprint 2 tests

### Run all tests (repo-wide)
From repo root:

```bash
make test
````

### Run only admin-service tests

From repo root:

```bash
mvn -q -pl services/admin-service -am test
```

### Run a single test class

From repo root:

```bash
mvn -q -pl services/admin-service -am -Dtest=AdminAuthFlowIT test
```

---

## Why you see multiple Spring Boot banners during tests

You may see multiple Spring Boot startup banners and multiple Hikari pools in one `mvn test`.

This is expected right now because each integration test class starts its own Spring application context:

- `AdminAuthFlowIT` starts one context
- `TenantPersistenceIT` starts one context
- `AdminSmokeTest` starts one context

This is not a correctness issue. It is a runtime/performance optimization we can address later by reusing contexts where safe.

---

## Testcontainers behavior and requirements

Sprint 2 uses Testcontainers for Postgres in admin-service integration tests.

Requirements:

- Docker Desktop must be running
- WSL integration for Docker must be enabled (Windows + WSL users)

If Docker is not running, you will typically see errors like:

- `Could not find a valid Docker environment`
- `Docker daemon is not running`
- `Connection refused`

Fix:

- start Docker Desktop
- re-run tests

---

## Key material in tests (important)

Sprint 2 supports local dev key storage under `.local/keys/` for manual runs.

However, **tests must not depend on `.local/`**.

Sprint 2 enforces this by:

- generating test keys under `target/test-keys/...`
- using test-only config in `services/admin-service/src/test/resources/application.yml`

This ensures:

- CI safety (no workstation state required)
- no accidental commit risk
- reproducibility across machines

---

## What to look for when tests fail

### A) Ready endpoint failing

If `/readyz` fails (or readiness checks fail), the most common reason is:

- Postgres is not reachable

This is expected behavior for production readiness: the service should not be “ready” if it cannot reach its database.

In tests, Postgres is provided by Testcontainers, so failures usually mean:

- Docker is not running
- Testcontainers could not pull/start the Postgres image

### B) Auth failures (401/403) not returning Problem Details

If `AdminAuthFlowIT` expects `application/problem+json` and instead receives a different response type, likely causes:

- the security exception handling path changed
- Problem Details handling was bypassed

Fix path:

- check your admin security configuration
- confirm the Problem Details dependency and handler behavior are still wired
- verify the test asserts match the intended contract

### C) Audit assertions failing

If the tenant is created but no audit row exists, likely causes:

- `TenantController` is not calling `AuditService.recordTenantCreated(...)`
- Flyway migration `V2__audit_log.sql` did not run
- entity mapping mismatch (JSON column mapping)

Quick checks:

- confirm `audit_log` table exists
- confirm `TENANT_CREATED` row is inserted for the created tenant
- confirm the repository query method names match entity field names

### D) JWT/JWKS failures

If login works but downstream authorization fails unexpectedly, likely causes:

- keystore path not readable/writable
- keypair not generated or not loaded correctly
- JWT claim names changed (e.g., `tenantId` missing)

Quick checks:

- confirm JWKS endpoint returns keys
- confirm JWT contains `sub`, `tenantId`, and roles/authorities in the expected structure
- confirm token TTL is reasonable (15 minutes)

---

## What Sprint 2 tests DO NOT cover (yet)

These are intentionally deferred to later sprints:

- RFC 9421 HTTP Message Signatures verification (attestation)
- nonce replay cache behavior (Redis)
- scoped permission tokens and lifecycle events
- Kafka event emission (CloudEvents)
- RAG pipeline (embeddings ingestion, retrieval, citations)
- ops agent tool allowlisting, schema validation, idempotency
- load testing and failure-mode testing (LLM down, Redis down, Kafka backlog, DB failover)

---

## Exit criteria recap (Sprint 2)

Sprint 2 is considered complete when:

- `mvn -q -pl services/admin-service -am test` passes consistently
- admin auth login issues RS256 JWT
- JWKS endpoint is available
- platform-admin RBAC is enforced for tenant endpoints
- 401/403 return RFC 9457 Problem Details (`application/problem+json`) with stable `errorCode`
- tenant creation writes a deterministic audit record (`TENANT_CREATED`) including actor + correlation/trace + payload JSON
- tests do not depend on `.local/` (key material isolated under `target/`)
