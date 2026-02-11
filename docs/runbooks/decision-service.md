# decision-service runbook

## What this service does

`decision-service` is the **internal** orchestrator for the “Decision Path MVP”.

For each request, it:

1. **Verifies the request body identity** using `Content-Digest` (Sprint 4: `sha-256` only).
2. Computes a **stable requestHash** for idempotency (semantic identity; does **not** include RFC 9421 freshness fields like nonce/created/signature).
3. Enforces **idempotency** using Redis:
   - Same `Idempotency-Key` + same `requestHash` → returns cached response.
   - Same `Idempotency-Key` + different `requestHash` → returns **409** Problem Details (`IDEMPOTENCY_KEY_REUSE_CONFLICT`).
4. On first execution (cache miss), calls:
   - `attestation-service` for RFC 9421 verification
   - `token-service` for scoped token validation
5. Returns a deterministic decision response (Sprint 4 MVP: `ALLOW` on success; token invalid → `401` Problem Details; downstream attestation errors are passed through).

**Internal-only posture:** This service is not intended to be exposed publicly. The gateway calls it over the internal network (local compose is OK).

---

## Endpoints

### Health
- `GET /healthz` → liveness
- `GET /readyz` → readiness (**depends on Redis**)

### Internal API
- `POST /internal/v1/decisions`

This is called by `gateway-service`.

#### Required headers
- `X-Tenant-Id` (required) — derived by gateway (Host→tenant). **Never accepted from request body.**
- `Idempotency-Key` (required)

#### Propagated headers (not required, but expected when available)
- `X-Correlation-Id`
- `traceparent`
- `tracestate`

#### Request body (high level)
The request includes:
- `rawScopedToken` (the raw `X-Scoped-Token` value)
- `contentDigest` (the `Content-Digest` header value)
- `bodyBytesBase64` (base64 of the external request body bytes)
- `signatureInput`, `signature` (forwarded to attestation-service)
- semantic fields used by token validation (action/amount/currency)

---

## Idempotency model (production rule)

### requestHash inputs
Sprint 4 requestHash is based on **stable semantic identity**, not cryptographic freshness.

- `tenantId`
- **external** method/path constants: `POST` + `/v1/agent/decisions/evaluate`
- raw scoped token (from `X-Scoped-Token`)
- verified body identity:
  - either the **verified** `Content-Digest` value (after digest-vs-body verification), or
  - `SHA-256(bodyBytes)` if you choose to hash body bytes directly

> Important: do **not** include `Signature-Input` or `Signature` in requestHash.
> Retries typically generate new signatures (new nonce/created), and idempotency must still treat them as the same request.

### Cache hit behavior (Sprint 4)
If the idempotency record exists for the same key+hash:
- decision-service returns the cached status/body **immediately**
- it does **not** re-call attestation-service or token-service

This avoids breaking legitimate retries due to replay protection / signature freshness.

---

## Content-Digest rules (Sprint 4)

Decision-service verifies digest-vs-body for every request before idempotency:

- Supported format: `Content-Digest: sha-256=:<base64>:` only
- Missing digest / malformed digest / mismatch → **400** Problem Details
- Unsupported algorithm → **400** Problem Details (example errorCode: `DIGEST_UNSUPPORTED`)

This guarantees the body identity used in requestHash is not attacker-controlled.

---

## Dependencies

### Redis (required)
Used for idempotency storage.

- Readiness depends on Redis connectivity.
- If Redis is down, `/readyz` should fail (fail-safe posture).

### Internal downstreams (required)
- `attestation-service`:
  - `POST /v1/attestations/verify`
- `token-service`:
  - `POST /internal/v1/tokens/validate`

Downstream errors from attestation/token are returned to the gateway as Problem Details (pass-through), preserving the HTTP status and `application/problem+json`.

---

## Running locally

### 1) Run tests (decision-service only)
From repo root:

```bash
mvn -q -pl services/decision-service -am test
```

### 2) Run with Docker Compose (recommended)

Use your local compose stack (Redis must be running). Once started:

- `decision-service` readiness: `GET http://<internal-host>:8083/readyz`
- gateway public entrypoint: `POST http://localhost:8080/v1/agent/decisions/evaluate`

(Decision-service itself should remain internal-only; do not publish its port unless you are debugging.)

---

## Troubleshooting

### A) `/readyz` fails

Most common causes:

- Redis not running
- Wrong Redis host/port
- Container network DNS mismatch in compose

Fix:

- ensure Redis is healthy
- confirm the service is pointing to the correct Redis host/port

### B) You get 409 `IDEMPOTENCY_KEY_REUSE_CONFLICT`

Meaning:

- same `Idempotency-Key` was reused with different request semantics (different requestHash)

Common causes:

- you changed the body (amount/action/currency) but reused the same Idempotency-Key
- you changed the scoped token but reused the same Idempotency-Key
- you changed Content-Digest/bodyBytes but reused the same Idempotency-Key

Fix:

- generate a new Idempotency-Key for a semantically different request

### C) You get 400 digest errors

Meaning:

- Content-Digest missing/invalid, or digest does not match `bodyBytesBase64`

Fix:

- ensure the gateway forwards the exact body bytes that were digested
- ensure Content-Digest format is `sha-256=:...:`

### D) You get 401 from attestation/token

- attestation 401/400/409 are passed through from attestation-service
- token invalid/expired/revoked produces a 401 Problem Details response from decision-service

Fix:

- validate your signing inputs (authority/path) and key registry for attestation
- validate the token issuance/validation setup for token-service

---

## Operational safety notes

- Never log raw scoped tokens. Log only token identifiers/hashes.
- Never accept tenantId from request body.
- Idempotency cache hit returns cached response immediately (no downstream calls).
- Keep this service internal-only (no public ingress).
