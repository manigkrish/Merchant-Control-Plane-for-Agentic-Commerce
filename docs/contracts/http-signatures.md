# HTTP Message Signatures Contract (RFC 9421 + TAP-Compatible Profile)

This document defines the request attestation contract for AgentTrust Gateway.

## Purpose
Agent runtime requests must be cryptographically authenticated using **RFC 9421 HTTP Message Signatures**.
We enforce a strict **TAP-compatible profile** to match merchant-side verification expectations:
- strict required fields/components
- strict timestamp window enforcement (default 8 minutes)
- nonce replay protection
- key resolution by `keyid` with caching; block if missing/expired

## Required headers
Requests MUST include:
- `Signature-Input` (required)
- `Signature` (required)
- `Content-Digest` (required when a request body is present)

## Required signature metadata (profile rules)
The signature **must** include the following metadata/parameters:
- `keyid` (required): key identifier used to resolve the public key from the agent registry
- `alg` (required): signing algorithm (allowed algorithms defined below)
- `created` (required): unix timestamp seconds
- `expires` (required): unix timestamp seconds
- `nonce` (required): unique per request within the allowed window
- `tag` (required): profile tag (used to identify the verification profile)

If any required parameter is missing, the request is rejected.

## Required covered components (profile rules)
The signature base MUST cover, at minimum:
- `@authority`
- `@path`
- `content-digest` (required when a body is present)
- `created`
- `expires`
- `nonce`

Notes:
- If a request has a body, it MUST include `Content-Digest` and the signature MUST cover it.
- The canonicalization rules follow RFC 9421.

## Timestamp rules (TAP-compatible)
- `created` MUST be within the allowed window relative to the server clock.
- `expires` MUST be greater than `created`.
- Default allowed window: **8 minutes** (configurable).
- If timestamps are invalid or outside the allowed window, the request is rejected.

## Nonce replay protection rules (TAP-compatible)
- `nonce` MUST be unique within the allowed window for a given `(tenant_id, keyid)` scope.
- The system stores nonce values in Redis with TTL equal to the allowed window.
- If a nonce repeats within the window, the request is rejected and a replay-block metric is emitted.

## Key resolution rules (TAP-compatible)
- `keyid` resolves to a public key via the **agent-registry-service**.
- Verification blocks if the key:
  - cannot be retrieved,
  - is disabled,
  - is expired,
  - is not valid for the tenant.
- Key lookups may be cached for a short TTL. Cache entries must respect key expiry.

## Allowed algorithms (MVP)
MVP supports:
- `ed25519`

Future candidates (explicitly not in MVP unless added intentionally):
- ECDSA (P-256 / P-384)

## Failure handling
The gateway maps failures to RFC 9457 Problem Details responses.
Recommended error categories:
- invalid/missing signature headers: `ATTESTATION_MISSING_OR_INVALID`
- signature verification failed: `ATTESTATION_INVALID`
- key not found/expired/disabled: `ATTESTATION_KEY_UNAVAILABLE`
- timestamp invalid/outside window: `ATTESTATION_TIMESTAMP_INVALID`
- nonce replay detected: `ATTESTATION_REPLAY`

## Observability requirements
For every verification attempt, emit:
- result (success/failure category)
- tenant_id
- keyid (hashed or truncated if needed)
- traceId and correlationId
- latency

## Client integration note
This contract is the authoritative integration reference for agent clients.
The OpenAPI spec defines the required headers; this document defines the required profile semantics.
