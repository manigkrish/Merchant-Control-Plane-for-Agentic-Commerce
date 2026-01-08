# Runbooks

This directory contains operational playbooks for AgentTrust Gateway.
The intent is that a developer (you) can diagnose issues quickly using repeatable steps,
even in a “prod-lite” portfolio environment.

Conventions:
- Use trace propagation headers per `docs/contracts/observability.md` (W3C Trace Context).
- Error responses follow RFC 9457 Problem Details (`docs/contracts/api-errors.md`).
- Security posture is fail-safe: when verification cannot be performed reliably, the system should not silently allow.

---

## 0) Fast triage checklist (use first)

1) Confirm what is broken:
   - Is the gateway reachable?
   - Is it a single tenant or all tenants?
   - Are failures deterministic (always) or intermittent?

2) Capture identifiers for investigation:
   - `traceparent` value (if caller has it)
   - correlation id (if you add one later) or request timestamp + client request id
   - tenant id (from admin JWT or resolved identity)

3) Check CI hygiene if you suspect a docs/tooling regression:
   - `make check`

---

## 1) Documentation + repo hygiene checks (Sprint 0)

### Symptoms
- GitHub Actions fails on `markdownlint` or `gitleaks`
- Docs links break or architecture files contradict each other

### Steps
1) Run local checks:
   - `make check`

2) Validate architecture consistency manually:
   - Confirm these tell the same story:
     - `docs/architecture/overview.md`
     - `docs/architecture/bounded-contexts.md`
     - `docs/architecture/diagram.md`

3) If markdownlint fails:
   - Fix malformed code fences (``` must be balanced)
   - If line-length rules cause noisy failures, adjust `.markdownlint.json` (preferred) rather than contorting prose.

4) If gitleaks fails:
   - Identify the file/line it flagged
   - Remove the secret immediately and rotate it (if real)
   - Add patterns to prevent recurrence (guardrails + `.gitignore`)

### Expected outcome
- CI is green
- Docs are navigable from `docs/README.md` without dead links
- No secrets present in repository history

---

## 2) RFC 9421 signature verification failures (Attestation)

### Symptoms
- Requests fail with 401/403 Problem Details
- Errors indicate missing/invalid `Signature-Input` / `Signature`
- Failures start suddenly for a previously working client

### Steps
1) Confirm required headers are present (at gateway ingress):
   - `Signature-Input`
   - `Signature`
   - `Content-Digest` (required when body present)
   - `X-Scoped-Token` (required for data-plane endpoints, e.g., `POST /v1/agent/decisions/evaluate`)

2) Confirm client clock skew (common root cause):
   - If `created/expires` are outside the allowed window (default 8 minutes), verification must fail.

3) Confirm `keyid` resolution:
   - If `keyid` cannot be resolved, is expired, or is disabled, verification must fail (TAP-compatible behavior).

4) Use tracing to follow the request:
   - Find the trace id in logs (per `docs/contracts/observability.md`) and walk:
     gateway -> attestation -> registry lookup

### Mitigation
- For client errors: fix client signing inputs and/or clock
- For registry issues: rotate key, re-enable key, or fix cache invalidation (depending on the failure type)

### Follow-up
- Add test coverage for the specific signing profile mismatch you observed
- Add metrics for:
  - signature verification failures by category
  - key lookup failures
  - timestamp window violations

---

## 3) Nonce replay-cache outage / replay storm (Redis impact)

### Symptoms
- Spike in deny/challenge rate
- Replay-blocks increase sharply (or replay defense becomes unavailable)
- Redis connectivity errors (timeouts/refused)

### Steps
1) Confirm Redis reachability (local/staging):
   - `redis-cli PING` (should return `PONG`)

2) Determine whether this is:
   - A real replay storm (nonces repeated), or
   - A cache outage (Redis unavailable), or
   - A TTL/misconfiguration issue (nonces not expiring)

3) Check for patterns:
   - A single tenant/keyid spiking suggests a single client bug or attack
   - Many tenants simultaneously suggests infra outage or shared dependency issue

### Mitigation (security-first)
- If replay defense cannot be enforced reliably, the system should fail-safe:
  - Prefer **CHALLENGE/DENY** over ALLOW until replay cache is restored.
- If it is an attack pattern:
  - Rate limit by tenant/keyid
  - Temporarily disable the offending key in registry (admin workflow)

### Follow-up
- Add alerts on:
  - replay blocks rate
  - Redis connectivity errors
  - replay-cache “unavailable” counter
- Confirm nonce key TTL equals the allowed window and expires as expected

---

## 4) Kafka backlog / ingestion delays (Policy ingestion)

### Symptoms
- Policy publishes succeed but RAG explanations lag behind
- Ingestion worker falls behind (consumer lag grows)
- Completion events delayed

### Steps
1) Identify where the delay is:
   - Producer: policy-service publishing ingestion requests
   - Broker: Kafka throughput/backpressure
   - Consumer: ingestion worker health / error loops

2) Local check (once Sprint 1 compose exists):
   - check worker logs for repeated failures
   - check broker health

3) AWS check (later stages):
   - CloudWatch MSK Serverless throughput metrics
   - consumer lag metrics (if emitted)

### Mitigation
- Reduce ingestion volume temporarily (throttle publishes)
- Increase worker concurrency (if safe) and/or reduce chunk size for embeddings
- Ensure dead-letter handling exists for poison messages (design requirement)

### Follow-up
- Add a dashboard panel for ingestion lag and completion time
- Add alert thresholds based on SLO for “policy publish -> searchable” latency

---

## 5) LLM provider degradation/outage (RAG + Ops Agent)

### Expected behavior (non-negotiable)
- Deterministic decisioning remains available.
- Explanations may degrade (e.g., “explanation unavailable”) but the system must not silently invent citations.
- Ops agent must not execute unsafe tool calls under degraded validation conditions.

### Symptoms
- Increased latency/timeouts in explanation generation
- 5xx from LLM provider
- High error rate in RAG/ops-agent services

### Steps
1) Confirm decisioning is still healthy:
   - `/healthz` and `/readyz`
   - p95 latency and error rate for decision endpoint

2) Confirm only explanation path is failing:
   - RAG service errors vs decision service errors

3) Confirm “cite-or-refuse” behavior:
   - If citations cannot be produced, explanation should be refused (not hallucinated)

### Mitigation
- Temporarily disable explanation generation (serve deterministic decision only)
- Temporarily disable ops-agent LLM calls (ops workflows should fall back to manual operations)

### Follow-up
- Add explicit alerts for:
  - explanation error rate
  - explanation latency p95
  - “explanation unavailable” rate
- Add resilience tests: “LLM down” scenario in system test suite
