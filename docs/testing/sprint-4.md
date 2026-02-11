# Sprint 4 testing

This document explains how to run Sprint 4 tests and do a minimal end-to-end validation locally.

Sprint 4 delivers the **Decision Path MVP**:

- `token-service` (8084)
- `decision-service` (8083)
- `gateway-service` (8080) public endpoint: `POST /v1/agent/decisions/evaluate`
- `attestation-service` (8082) internal verification endpoint: `POST /v1/attestations/verify`

All services follow platform conventions:
- RFC 9457 Problem Details (`application/problem+json`) on errors
- Correlation (`X-Correlation-Id`) and W3C Trace Context propagation (`traceparent`, `tracestate`)
- Tenant context derived in gateway and propagated internally (never trusted from request body)

---

## 1) Run the full test suite

From repo root:

```bash
mvn -q test
```

If you want to run only Sprint 4 services:

```bash
mvn -q -pl services/token-service -am test
mvn -q -pl services/decision-service -am test
mvn -q -pl services/gateway-service -am test
mvn -q -pl services/attestation-service -am test
```

Expected:

- All tests pass.
- Some tests may include intentional fail-closed cases (e.g., replay protection) and should still pass.

---

## 2) Run services locally with docker-compose

Sprint 4 expects Postgres + Redis plus all services. Your compose file already includes Redis and Postgres.

From:

```bash
cd infra/docker-compose
docker compose up -d postgres redis token-service decision-service attestation-service gateway-service
docker compose ps
```

Then check health/readiness:

```bash
curl -sS localhost:8080/healthz
curl -sS localhost:8080/readyz

# internal services are not published to host by default in compose unless you expose/port-map them
# if you do port-map them locally, validate:
# curl -sS localhost:8083/healthz
# curl -sS localhost:8084/readyz
# curl -sS localhost:8082/readyz
```

---

## 3) Minimal end-to-end call: gateway evaluate

### What you need

Gateway `POST /v1/agent/decisions/evaluate` requires:

- `Signature-Input`
- `Signature`
- `Content-Digest` (Sprint 4 supports **sha-256 only**)
- `Idempotency-Key`
- `X-Scoped-Token`

The request body must match the digest. The digest is a semantic identity input for idempotency.

### Common failure modes (expected)

If you send dummy signature headers, attestation verification should fail with Problem Details (401/400/409 depending on reason).

Example (expected to fail attestation if signature is dummy):

```bash
BODY='{"action":"PURCHASE","amount":100,"currency":"USD"}'

# NOTE: This is not a real Content-Digest computation. For real tests,
# generate Content-Digest sha-256 from BODY bytes and send it.
curl -i \
  -H 'Host: merchant.example.test' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json,application/problem+json' \
  -H 'Idempotency-Key: idem-1' \
  -H 'X-Scoped-Token: <SCOPED_TOKEN>' \
  -H 'Content-Digest: sha-256=:REPLACE_WITH_REAL_BASE64_DIGEST=:' \
  -H 'Signature-Input: sig1=(\"@authority\" \"@path\" \"@signature-params\");created=...;expires=...;keyid=\"...\";alg=\"ed25519\";nonce=\"...\";tag=\"...\"' \
  -H 'Signature: sig1=:REPLACE_WITH_REAL_SIGNATURE_BASE64=:' \
  --data "$BODY" \
  http://localhost:8080/v1/agent/decisions/evaluate
```

---

## 4) Idempotency behavioral checks

Idempotency is enforced in `decision-service`:

- Same `Idempotency-Key` + same `requestHash` => returns cached response immediately.
- Same `Idempotency-Key` + different `requestHash` => 409 Problem Details with `errorCode=IDEMPOTENCY_KEY_REUSE_CONFLICT`.

Important:

- `requestHash` must NOT include `Signature` or `Signature-Input`.
- `requestHash` uses the **verified body identity**, based on:

  - verified `Content-Digest` (sha-256) and/or
  - SHA-256 of body bytes after verification.

Because real retries will re-sign with new nonce/created/expires, the idempotency layer must treat them as the same semantic request.

---

## 5) What “success” looks like in Sprint 4

With valid attestation and a valid scoped token:

- Gateway returns `200 application/json`
- Body includes a deterministic decision:

  - `ALLOW` when token is valid and constraints pass
  - `DENY` when token constraints fail
- Decision-service stores an idempotency record keyed by:

  - tenant + idempotencyKey + requestHash

With invalid token (expired/revoked/invalid):

- Gateway returns `401 application/problem+json`

With attestation failure:

- Gateway returns the Problem Details produced by attestation-service
- Decision-service must not call token-service if attestation fails (fail-fast)

---

## 6) Log expectations (security)

- No logs should contain raw token values (`stkn_*`).
- Log tokenId or tokenHash only.
- Correlation and trace headers should appear in logs consistently.

---

## 7) Cleanup

```bash
cd infra/docker-compose
docker compose down -v
```
