# Sprint 3 Testing & Local Verification

Sprint 3 introduces a new runnable microservice `attestation-service` that verifies RFC 9421 HTTP Message Signatures and enforces
nonce replay defense via Redis. The gateway delegates verification to the attestation service and derives `tenantId` from the incoming `Host` header via a static mapping.

This document shows how to run tests and how to do a basic local smoke check.

---

## What Sprint 3 covers

### Attestation Service (port 8082)
- Endpoint: `POST /v1/attestations/verify`
- Bodyless verification (no `Content-Digest` yet)
- Required covered components:
  - `@authority`
  - `@path`
  - `@signature-params`
- Required signature parameters:
  - `keyid`, `alg`, `created`, `expires`, `nonce`, `tag`
- Allowed algorithm:
  - `ed25519`
- Replay defense:
  - Redis `SET key value NX EX ttl`
  - Key is scoped by `(tenantId, keyId, nonce)` (plus a prefix)

### Gateway delegation
- Endpoint: `POST /v1/agent/verify`
- Reads `Signature-Input` and `Signature` headers from the incoming request
- Derives `tenantId` from `Host` using a configured mapping
- Calls `attestation-service` and passes correlation/trace headers through
- If `attestation-service` returns Problem Details (`application/problem+json`), gateway passes it through

---

## Prerequisites

- Java 21
- Maven 3.8.6+
- Docker (for Testcontainers integration tests)

---

## Run tests

### 1) Run all attestation-service tests (unit + integration tests)
```bash
mvn -q -pl services/attestation-service -am test
echo $?
```

What to expect:

- `AttestationServiceRedisIT` uses Testcontainers Redis and verifies replay defense (first request OK, replay blocked).
- `AttestationServiceInvalidSignatureIT` uses Testcontainers Redis and verifies invalid signatures return `401` with `errorCode=ATTESTATION_INVALID_SIGNATURE`.

### 2) Run only the attestation-service integration tests (optional)

If you want to narrow to a single test class:

```bash
mvn -q -pl services/attestation-service -Dtest=AttestationServiceRedisIT -Dsurefire.failIfNoSpecifiedTests=false test
echo $?
```

### 3) Run gateway-service tests (including delegation test)

```bash
mvn -q -pl services/gateway-service -am test
echo $?
```

To run only the delegation test:

```bash
mvn -q -pl services/gateway-service -Dtest=GatewayAttestationDelegationTest -Dsurefire.failIfNoSpecifiedTests=false test
echo $?
```

---

## Local runtime smoke check (optional)

This is optional because the integration tests already validate cryptography + replay defense end-to-end.

### A) Start Redis locally

If your repo already includes a local docker-compose with Redis, start it (adjust path if needed):

```bash
docker compose -f infra/docker-compose/docker-compose.yml up -d redis
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### B) Run attestation-service (host process)

If you run `attestation-service` directly on your host, set Redis host to `localhost` (because the default `redis` hostname is only resolvable inside a compose network):

```bash
cd services/attestation-service
ATTESTATION_REDIS_HOST=localhost mvn -q spring-boot:run
```

If port 8082 is already used:

```bash
sudo ss -ltnp | grep ':8082'
# then kill the PID shown (example):
# sudo kill -9 <PID>
```

### C) Run gateway-service (host process)

In another terminal:

```bash
cd services/gateway-service
mvn -q spring-boot:run
```

Notes:

- Gateway must have:

  - `agenttrust.tenancy.hostToTenant` mapping in its `application.yml`
  - `agenttrust.attestation.client.baseUrl` pointing to the attestation-service URL (for host-run: `http://localhost:8082`)

---

## Quick validation checklist

### Attestation-service

- `GET /healthz` returns 200
- `GET /readyz` returns 200 when Redis is reachable
- Integration tests pass:

  - replay detected returns `409` with `errorCode=ATTESTATION_REPLAY_DETECTED`
  - invalid signature returns `401` with `errorCode=ATTESTATION_INVALID_SIGNATURE`

### Gateway-service

- Delegation test passes:

  - tenantId derived from `Host`
  - `X-Correlation-Id`, `traceparent`, `tracestate` forwarded
  - Problem Details passed through as-is when returned by attestation-service

---

## Common issues & fixes

### 1) “No tests matching pattern …” when using `-am`

If you run a single test with `-am`, Maven will also build upstream modules that don’t have that test class.
Use:

```bash
mvn -q -pl services/gateway-service -Dtest=GatewayAttestationDelegationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

### 2) Redis hostname mismatch when running on host

`ATTESTATION_REDIS_HOST` defaults to `redis`. If you run the app on your host, use:

```bash
ATTESTATION_REDIS_HOST=localhost
```

### 3) Port already in use

Find and stop the process:

```bash
sudo ss -ltnp | grep ':8082'
sudo kill -9 <PID>
```

### Quick check command (after adding the file)
```bash
# just to ensure nothing breaks and tests still pass
mvn -q -pl services/attestation-service -am test
echo $?
```
