# Documentation Index

This directory is the source of truth for architecture, contracts, security posture, and operational decisions.
The goal is to make assumptions explicit before implementation.

---

## Architecture

- Overview: `architecture/overview.md`
- Bounded contexts (control plane vs data plane, public vs internal): `architecture/bounded-contexts.md`
- System diagram (Mermaid): `architecture/diagram.md`

---

## Contracts (standards)

- API errors (RFC 9457 Problem Details): `contracts/api-errors.md`
- HTTP message signatures (RFC 9421) + TAP-compatible profile: `contracts/http-signatures.md`
- Scoped tokens (wire format + transport): `contracts/scoped-tokens.md`
- Tenancy (how tenant context is derived and propagated): `contracts/tenancy.md`
- Events (CloudEvents envelope for Kafka): `contracts/events.md`
- Observability (logs/metrics/tracing conventions): `contracts/observability.md`
- Audit policy (what we log, what we redact): `contracts/audit-policy.md`

---

## OpenAPI (source of truth)

- Gateway API: `openapi/gateway.yaml`
- Admin (control plane) API: `openapi/admin.yaml`
- Decision service API (internal): `openapi/decision.yaml`

---

## Security

- Security policy: `SECURITY.md`
- Repo guardrails: `security/repo-guardrails.md`
- CI OIDC notes: `security/ci-oidc.md`
- Threat model v0: `security/threat-model-v0.md`

---

## Operations foundations

- Environments strategy: `environments/strategy.md`
- Cost controls: `cost/controls.md`
- Testing strategy: `testing/strategy.md`
- Runbooks: `runbooks/README.md`

---

## ADRs

Architectural Decision Records live in: `adr/`
