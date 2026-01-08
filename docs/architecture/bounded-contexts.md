# Bounded Contexts: Control Plane vs Data Plane

## Control Plane (admin-facing)
Purpose: configuration, governance, onboarding, policy management, key lifecycle management, and audit review.

Primary service:
- **admin-service**: control plane API for tenant onboarding, internal auth (MVP), RBAC administration, and operator workflows.

Other control-plane services:
- **agent-registry-service**: agent identities + public key lifecycle + status
- **policy-service**: policy upload/version/publish; S3 storage; ingestion event emission
- **token-service**: token issuance/revocation operations (admin-controlled aspects)
- audit/evidence workflows (cross-cutting)

## Data Plane (runtime decisioning)
Purpose: evaluate agent requests in real time and return **ALLOW / CHALLENGE / DENY**.

Primary services:
- **gateway-service**: edge routing, authN/authZ, rate limits, tenant context propagation
- **attestation-service**: **RFC 9421 verification + TAP-compatible profile enforcement + replay defense**
- **token-service**: scoped token verification and constraint enforcement
- **decision-service**: deterministic allow/challenge/deny orchestration + event emission
- **rag-service**: explanation generation with citations (non-authoritative)
- **ops-agent-service**: constrained tool-calling for operational workflows (non-authoritative)

**Public entrypoints**

- `/v1/admin/*` is the control plane surface and is exposed via **admin-service**.
- `/v1/agent/*` is the data plane surface and is exposed via **gateway-service**.
- All other services are internal-only and communicate over private networks (internal ALBs / ECS service discovery).

**Owned by**
Control plane is implemented by **admin-service** (public API) plus internal control-plane services
(policy-service, agent-registry-service, and token-service for admin ops).

Routing model (MVP):
- A single ALB is the public DNS endpoint, using path-based routing:
  - `/v1/agent/*` -> gateway-service
  - `/v1/admin/*` -> admin-service
  - All other services are internal-only.

Internal (east-west):
- key lookup, signature verification, token verification, audit fetch, ingestion events

## Multi-tenancy rule (non-negotiable)
Tenant context is always derived from a verified identity (cryptographic attestation and/or trusted internal auth).
External clients cannot supply tenant identity via request bodies, headers, or client-provided identifiers.
