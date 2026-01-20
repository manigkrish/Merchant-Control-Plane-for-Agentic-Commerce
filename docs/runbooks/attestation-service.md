# Attestation Service Runbook

`attestation-service` verifies RFC 9421 HTTP Message Signatures for incoming data-plane requests and enforces nonce replay defense using Redis.

This runbook documents how to run, configure, and troubleshoot the service.

---

## Service overview

- Default port: **8082**
- Health endpoints:
  - `GET /healthz`
  - `GET /readyz`
- Core API:
  - `POST /v1/attestations/verify`

### What it does

- Validates `Signature-Input` and `Signature` headers using RFC 9421 concepts.
- Builds a signature base that covers:
  - `@authority`
  - `@path`
  - `@signature-params` (required, binds signature parameters like `keyid`, `alg`, `created`, `expires`, `nonce`, `tag`)
- Enforces:
  - Allowed algorithms (e.g., `ed25519`)
  - `created` / `expires` window constraints
  - Required signature parameters (`keyid`, `alg`, `created`, `expires`, `nonce`, `tag`)
- Applies replay defense:
  - Stores `(tenantId, keyId, nonce)` as a Redis key with TTL derived from `expires` (or a safe default)
  - Rejects replays (same nonce) with a conflict error

### Body handling

This service is configured for **bodyless verification** (no request body validation). In this mode, `Content-Digest` is not required and is not validated.

---

## How to run locally

### 1) Start Redis

Use your existing local infra (compose) that already includes Redis.

If you run Redis via compose, confirm it is up:

```bash
docker compose -f infra/docker-compose/docker-compose.yml ps
````

### 2) Run attestation-service on your host machine

From repo root:

```bash
cd services/attestation-service
ATTESTATION_REDIS_HOST=localhost mvn -q spring-boot:run
```

### About “internal-only” in compose

In your preferred setup, `attestation-service` is **internal-only** when running under compose (its port is not published to the host).
That means:

- If `attestation-service` runs on your host machine: `curl http://localhost:8082/...` works.
- If `attestation-service` runs only inside compose without port publishing: you typically test it through **gateway**, or by exec’ing into a container on the same compose network.

---

## Configuration

Configuration is read from `application.yml` (and environment overrides). Redis defaults assume a compose/network hostname.

### Redis connectivity

Environment overrides:

- `ATTESTATION_REDIS_HOST` (default `redis`)
- `ATTESTATION_REDIS_PORT` (default `6379`)
- `ATTESTATION_REDIS_TIMEOUT` (default `2s`)

When running the service on your host machine with Redis also on your host:

```bash
ATTESTATION_REDIS_HOST=localhost mvn -q spring-boot:run
```

### Attestation verification profile

Key fields (YAML prefix: `agenttrust.attestation.profile`):

- `bodyless` (boolean)

  - If `true`, `Content-Digest` is not required.
- `requiredSignatureParams` (list)

  - e.g. `keyid`, `alg`, `created`, `expires`, `nonce`, `tag`
- `requiredCoveredComponents` (list)

  - e.g. `@authority`, `@path`, `@signature-params`
- `allowedAlgorithms` (list)

  - e.g. `ed25519`
- `maxWindowSeconds` (int)

  - Maximum allowed `expires - created` and acceptable clock window.

### Replay defense

YAML prefix: `agenttrust.attestation.replay`

- `enabled` (boolean)
- `keyPrefix` (string)
- `defaultTtlSeconds` (int)

---

## Key registry (public keys only)

The service uses a YAML-configured registry for public keys (tenant-scoped). Keys must be public only.

YAML prefix: `agenttrust.attestation.keys.registry`

Each entry:

- `tenantId`
- `keyId`
- `status` (e.g., `ACTIVE`)
- `publicKeyBase64`

  - Base64-encoded raw Ed25519 public key (32 bytes), not PEM.

Important: ensure that requests use a `keyid` matching a registry entry for the derived `tenantId`.

---

## API behavior

### `POST /v1/attestations/verify`

Request body is a JSON DTO containing (at minimum):

- HTTP method
- authority (host)
- path
- tenantId
- `Signature-Input`
- `Signature`

On success:

- `200 OK`
- JSON like `{ "verified": true }`

On failure:

- `401` for signature verification failures
- `409` for nonce replay detection
- Responses may use Problem Details (`application/problem+json`) with a stable `errorCode`.

Common `errorCode` values:

- `ATTESTATION_INVALID_SIGNATURE`
- `ATTESTATION_REPLAY_DETECTED`
- `ATTESTATION_KEY_UNAVAILABLE` (no matching key for tenant/keyid)
- `ATTESTATION_REDIS_UNAVAILABLE` (replay defense cannot access Redis; service fails closed)

---

## Operational checks

### Verify the process is listening

```bash
sudo ss -ltnp | grep ':8082'
```

### Health endpoints (when running on host)

```bash
curl -sS -i http://localhost:8082/healthz
curl -sS -i http://localhost:8082/readyz
```

Expected:

- `healthz` should be `200` if the app is running.
- `readyz` should be `200` when dependencies (notably Redis) are reachable.

### Quick test command

From repo root:

```bash
mvn -q -pl services/attestation-service -am test
```

---

## Troubleshooting

### Port already in use (8082)

Symptoms:

- App fails to start with “Port 8082 was already in use”.

Fix:

```bash
sudo ss -ltnp | grep ':8082'
sudo kill <PID>
# If it does not stop, then:
sudo kill -9 <PID>
```

### Redis hostname mismatch

Symptoms:

- Running on host machine but Redis host is set to `redis`, leading to connection failures.

Fix:

```bash
ATTESTATION_REDIS_HOST=localhost mvn -q spring-boot:run
```

### Replay defense warnings during tests

Some unit tests intentionally simulate Redis failures to validate “fail closed” behavior. If logs are noisy, use a test `logback-test.xml` to reduce stack traces for expected failure paths.

### “Mapped port can only be obtained after the container is started”

Symptoms:

- Integration tests fail when accessing `getMappedPort()` too early.

Fix:

- Ensure the Testcontainers container is started before properties reference mapped ports (either via proper `@Container` lifecycle or a controlled start before property suppliers are evaluated).

---

## Where this service is used

Gateway delegates verification requests to `attestation-service`. Gateway:

- derives `tenantId` from `Host`
- forwards `Signature-Input` and `Signature`
- propagates correlation/trace headers
- passes through Problem Details responses when returned
