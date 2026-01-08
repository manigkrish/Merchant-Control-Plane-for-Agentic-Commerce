# Architecture Overview

AgentTrust Gateway is a merchant control plane + gateway that returns **ALLOW / CHALLENGE / DENY**
for agent-initiated commerce actions, with auditable evidence.

## Core pillars (portfolio differentiators)
1) **Attestation verification (security primitive)**
   Agent requests are verified using **RFC 9421 HTTP Message Signatures** with a strict
   **TAP-compatible profile** (required signature components + timestamp window enforcement +
   keyid resolution + nonce replay protection).

2) **Replay defense (security primitive)**
   Requests must include `created/expires` and a `nonce`. We enforce a strict time window
   (profile-configurable; default 8 minutes) and block nonce reuse within the window using Redis.

3) **Scoped permission tokens (least privilege)**
   Tokens are bound by constraints (tenant/merchant/action/amount/expiry). Lifecycle events are
   emitted for issue/use/revoke/misuse attempts and stored in immutable audit logs.

4) **Policy-RAG explainability (auditability, non-authoritative)**
   RAG provides explanations with citations to versioned policy chunks.
   The LLM never authorizes allow/deny; deterministic decisioning is the source of truth.

5) **Guardrailed ops agent (safe constrained automation)**
   Ops agent can run only allowlisted tools. Tool calls require JSON schema validation, RBAC gates,
   idempotency keys for mutating actions, and full audit trails.

6) **Production operations**
   SLOs, dashboards, alerts, blue/green deployments, load testing, failure-mode testing, and
   graceful degradation (e.g., decisioning still works if LLM is down).

## Services (planned)

## Public entrypoints (edge routing)

A single ALB is the public DNS endpoint, using path-based routing:

- `/v1/agent/*` routes to **gateway-service** (data plane public entrypoint).
- `/v1/admin/*` routes to **admin-service** (control plane public entrypoint).

All other services are private (reachable only via internal networking).

## Decision path (high level)

1. A request enters through the **gateway-service** (`/v1/agent/*`), which enforces edge controls (rate limits, auth checks)
and routes to decision-service.
2. **decision-service** orchestrates verification and enforcement:
   - calls **attestation-service** to verify RFC 9421 signature, timestamp window, and nonce replay defenses
   - calls **token-service** to validate scope constraints and record token usage (idempotent)
3. decision-service computes a deterministic **ALLOW / CHALLENGE / DENY** outcome.
4. If an explanation is requested (or required for audit), decision-service calls **rag-service**
   to retrieve policy snippets and generate a citation-backed explanation (non-authoritative).
5. Audit events and evidence bundles are persisted for review and governance.

### Edge
- **gateway-service**: authN/authZ, tenant context, rate limiting, routing to internal services

### Control plane
- **admin-service**: control plane API for tenant onboarding, internal auth (MVP), RBAC admin workflows
- **agent-registry-service**: agent identities, public keys, key rotation metadata, status
- **policy-service**: policy upload/version/publish; stores documents in S3; emits ingestion events

### Data plane
- **attestation-service**: RFC 9421 verification + TAP profile enforcement + replay protection (Redis)
- **token-service**: issues/verifies/revokes scoped permission tokens; emits lifecycle events
- **decision-service**: deterministic orchestration -> ALLOW/CHALLENGE/DENY + event emission

### Explainability and ops automation (non-authoritative)
- **rag-service**: retrieves policy chunks (pgvector) and generates explanations with citations
- **ops-agent-service**: allowlisted tools only; JSON schema validation; RBAC gates; idempotency; full audit

### Workers
- **ingestion worker (Python)**: consumes policy ingestion events from Kafka, chunks docs, generates embeddings, upserts into pgvector, emits completion events

## Dependencies
- PostgreSQL (system of record + audit logs)
- pgvector (embeddings retrieval)
- Redis (nonce replay cache, idempotency keys, rate limiting)
- Kafka (MSK Serverless in AWS)
- S3 (policy docs + evidence bundles)
- OpenAI API (embeddings + explanations + tool calling)

## See also
- Bounded contexts: `bounded-contexts.md`
- Diagram (source of truth): `diagram.md`
