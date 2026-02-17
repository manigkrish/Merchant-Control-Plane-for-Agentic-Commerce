# token-service runbook

## Purpose

`token-service` is the **internal** control-plane service responsible for:

- Issuing **opaque scoped tokens** with prefix `stkn_...`
- Validating scoped tokens for a requested action (hot path for decision-service)
- Recording **usage/audit** (never storing or logging the raw token value)

This service is **not** a public API. Only internal callers (gateway/decision-service in local compose; later private networking) should reach it.

---

## Ports and health

- Port: **8084**
- Liveness: `GET /healthz`
- Readiness: `GET /readyz`
  - Readiness depends on the database (if DB is down/unreachable, readiness should fail)

---

## Core endpoints

### Validate (hot path)

`POST /internal/v1/tokens/validate`

Behavior expectations:

- Returns **200 OK** with a JSON body indicating `valid=true/false`.
- Invalid/expired/revoked tokens should typically be represented as:
  - `valid=false`
  - a `reasonCode` (examples you already use in tests: `TOKEN_EXPIRED`, `TOKEN_REVOKED`, etc.)
- Only malformed requests (missing required fields, invalid formats) should produce **400** `application/problem+json`.

**Important:** token-service is allowed to return 200 even when invalid. The **decision-service** is the layer that converts invalid tokens into the desired external behavior (e.g., 401 Problem Details).

### Issue (local/test only)

If you enabled the issuance endpoint for local development/testing:

`POST /internal/v1/tokens/issue`

Rules:

- The response may contain the **raw token value** exactly once (the newly created `stkn_...`).
- The database **must persist only a token hash** (e.g., SHA-256 of the raw token), never the raw token.

This endpoint must be **gated** so it is not enabled in production (e.g., only active under `local` / `test` profile).

> If you chose not to expose issue via HTTP, issuance is done via repository inserts in integration tests instead.

---

## Data and storage invariants

### Opaque token handling

- Tokens are presented by clients via the header `X-Scoped-Token: stkn_...`
- token-service must:
  - **Hash** the raw token value (e.g., SHA-256) for lookup/storage
  - Never log or store the raw token
  - When needed for logs/audit, use `tokenId` or a **tokenHash** (not the token string)

### Typical persistence tables (conceptual)

You already implemented Flyway/JPA for Sprint 4. At a minimum, token-service stores:

- Token identity and constraints (tenant, allowed action(s), optional limits like amount/currency/time window)
- Token state (ACTIVE/REVOKED/EXPIRED) and timestamps
- Usage/audit append-only records (validation calls, decisions, reason codes)

---

## Configuration

token-service should use standard Spring Boot configuration knobs:

### Database

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.flyway.enabled=true`

In docker-compose, these are typically supplied via environment variables or `application.yml`.
If you run locally without compose, export:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Logging

- Never log `X-Scoped-Token`.
- If you must correlate requests, log:
  - `tenantId`
  - `tokenId` (if resolved)
  - `correlationId`, `traceId` (already supported by your platform-web layer)

---

## Local run

### Option 1: docker compose

From `infra/docker-compose/`:

```bash
docker compose up -d postgres
docker compose up -d token-service
docker compose logs -f token-service
```

Then:

```bash
curl -s http://localhost:8084/healthz
curl -s http://localhost:8084/readyz
```

### Option 2: run directly (dev)

From repo root:

```bash
mvn -q -pl services/token-service -am spring-boot:run
```

---

## Troubleshooting

### 1) `/readyz` fails

Most common causes:

- Postgres not running / not reachable
- Wrong datasource URL/credentials
- Flyway migration failure (check logs for the first failing migration)

### 2) Validate always returns invalid

Typical causes:

- Issued tokens are not being persisted (Flyway/JPA misconfig)
- Token hashing mismatch (issue path hashes one way; validate path hashes differently)
- Tenant mismatch (validate request uses a different tenant than the token was issued under)

### 3) Raw token appears in logs

This is a security bug.
Fix immediately by removing any log statements that include request headers/body fields containing the token.
Prefer logging only `tokenId` or `tokenHash`.

---

## Operational notes

- token-service is internal-only by design; do not expose it via public ports/ALB in real deployment.
- Treat token issuance as a controlled capability:

  - `local/test` only for now
  - later: admin-controlled issuance with RBAC and audit (future sprint)
