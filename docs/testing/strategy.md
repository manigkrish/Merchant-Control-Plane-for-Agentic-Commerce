# Testing Strategy

This project test at four levels:

## 1) Unit tests (per service)
- Pure logic tests (token scope checks, decision rules, signature parsing helpers)
- No network calls

## 2) Integration tests (per service)
- Testcontainers for Postgres/Redis/Kafka where applicable
- Verify database constraints and tenant scoping

## 3) Contract tests (cross-service)
- Validate OpenAPI contracts and error model consistency
- Validate CloudEvents envelope compatibility

## 4) System / resilience tests (end-to-end)
- Load tests for p95 latency and error rates
- Failure injection:
  - Redis down
  - Kafka backlog
  - DB failover behavior
  - LLM down (explanations degrade; decisioning still works)

Repository rule:
- Tests live with the service that owns the behavior.
- Cross-service tests live under a dedicated folder only when needed (added later).
