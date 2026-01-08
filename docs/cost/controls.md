# Cost Controls (practical knobs)

This repo is designed to be safe to run without surprise bills. The default posture is: cap what can scale, degrade
gracefully, and make budgets visible.

## LLM usage
- Explanations are non-authoritative and can be disabled under load or budget pressure.
- Enforce explicit per-request budgets (see “Default knobs” below).
- Cache explanations keyed by (policy_version, decision_input_hash) where safe.

## Kafka / MSK Serverless
- Keep topic count minimal.
- Keep partition count minimal and scale only with measured need.
- Use retention windows appropriate to environment (short in staging/prod-lite).

## Storage retention
- Logs: short retention in staging/prod-lite; longer only when justified.
- Evidence bundles: store only what is necessary (citations + metadata first).

## Database sizing
- Default to small Postgres/Redis footprints in staging/prod-lite.
- Use indexes and query limits before scaling instance sizes.

## Environment guardrails
- prod-lite is the “portfolio default”: minimal footprint with safe limits.
- prod-managed is opt-in and requires explicit confirmation of budgets in infra docs.

## Default knobs (starting values)

These defaults are intentionally conservative for local/staging/prod-lite. Adjust only after measuring usage.

### LLM
- Max completion tokens per explanation: 300
- Max tool-calling turns per request (ops agent): 3
- Explanation cache TTL: 10 minutes (keyed by policy_version + decision_input_hash)
- Fail-safe: if LLM is unavailable or budget is exceeded, return deterministic decision without explanation

### RAG
- Chunk size (policy docs): 800–1200 chars
- Retrieval topK: 5
- Freshness: re-embed only on policy publish events; do not re-embed on reads

### Kafka / MSK Serverless (prod-lite posture)
- Topic count: ≤ 6 for MVP
- Partitions per topic: 1 (increase only if throughput requires it)
- Retention (staging/prod-lite): 24 hours

### Storage retention
- Logs retention (staging/prod-lite): 7 days
- Evidence bundles: store citations + metadata first; exclude raw payloads unless required

### Postgres / Redis sizing
- Prefer query limits + indexing before scaling instance size
- Redis nonce replay TTL: 10 minutes (matches signature time window)
- Rate-limits: per-tenant + per-agent key (documented in gateway controls)

### Observability cost control
- Trace sampling: 10% baseline, 100% on errors
