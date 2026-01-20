# Attestation Verification Profile (RFC 9421) — Data Plane

## Purpose

This document defines the **attestation verification profile** enforced on data-plane requests in:

**AgentTrust Gateway (Agentic Commerce Trust + Scoped Tokens + Policy-RAG + Guardrailed Ops Agent) (with optional TAP verifier)**

It describes exactly what the verifier requires for a request to be accepted.

This profile is designed to remain compatible with an optional TAP-style verifier module later, without changing the core verification pipeline.

---

## Current scope

### In scope
- RFC 9421-style HTTP Message Signature verification
- Tenant-scoped key resolution
- Nonce replay defense using Redis (fail-closed)

### Out of scope (current)
- Request body signing and `Content-Digest` validation (bodyless endpoint)
- External key registry integration
- Kafka, RAG, scoped tokens, ops agent

---

## Inputs to verification

Verification uses:

- `method` (e.g., `POST`)
- `authority` (from `Host`, used as `@authority`)
- `path` (e.g., `/v1/agent/verify`, used as `@path`)
- `tenantId` (derived by gateway; never trusted from request body)
- `Signature-Input` header value
- `Signature` header value

---

## Required covered components

The signature MUST cover these components:

1. `@authority`
2. `@path`
3. `@signature-params`

### Why `@signature-params` is required

In RFC 9421, these values are **signature parameters**:

- `keyid`, `alg`, `created`, `expires`, `nonce`, `tag`

They are bound to the signature base through the derived component `@signature-params`. This prevents tampering with signature parameters without invalidating the signature.

---

## Required signature parameters

The `Signature-Input` MUST contain all of:

- `keyid`
- `alg`
- `created`
- `expires`
- `nonce`
- `tag`

If any are missing, verification fails.

---

## Allowed algorithms

Allowlisted algorithms (current):

- `ed25519`

Any algorithm not explicitly allowlisted fails verification.

---

## Time validity rules

The verifier enforces:

- `created` MUST be present and parseable
- `expires` MUST be present and parseable
- `expires` MUST be greater than `created`
- `(expires - created)` MUST be <= `maxWindowSeconds` (default 480 seconds)
- The current server time MUST fall within the allowed validity window

If any rule fails, verification fails.

---

## Tenant derivation and tenant-key binding

### Tenant derivation (gateway)

Gateway derives tenant identity from a configured mapping:

- `Host` → `tenantId`

### Tenant-key binding (mandatory)

To prevent tenant spoofing, key resolution is tenant-scoped and verification enforces:

- derived `tenantId` MUST match the tenant bound to the resolved `keyid`

If the key exists but is not active for the derived tenant, verification fails.

---

## Public key resolution (current)

The attestation verifier resolves public keys from configuration (YAML).

Each registry entry includes:

- `tenantId`
- `keyId`
- `status` (e.g., `ACTIVE`)
- `publicKeyBase64` (raw Ed25519 public key bytes, base64-encoded)

Only public keys are stored.

---

## Replay defense

Replay protection is enforced using Redis.

### Key format

- `replay:{tenantId}:{keyId}:{nonce}`

### Storage rule

- store with: `SET key 1 NX EX <ttlSeconds>`

### TTL rule

- prefer TTL derived from `(expires - created)`
- otherwise fall back to `defaultTtlSeconds` (default 480 seconds)

### Fail-closed behavior

If Redis is unavailable, verification fails.

---

## Failure model (RFC 9457 Problem Details)

Failures are returned as RFC 9457 Problem Details:

- `Content-Type: application/problem+json`

The response includes:
- `status`, `title`, `detail`, `instance`
- `errorCode`
- correlation/trace fields (as provided by platform-web)

### Stable error codes (current)

- `ATTESTATION_MISSING_COMPONENT`
- `ATTESTATION_TIMESTAMP_INVALID`
- `ATTESTATION_KEY_UNAVAILABLE`
- `ATTESTATION_TENANT_KEY_MISMATCH`
- `ATTESTATION_INVALID_SIGNATURE`
- `ATTESTATION_REPLAY_DETECTED`

---

## Future extension (when bodies are supported)

When request bodies are introduced, the profile will be extended to:

- require `Content-Digest`
- validate `Content-Digest`
- require `content-digest` to be included in covered components

This will be done without changing existing semantics for `@authority`, `@path`, and `@signature-params`.
