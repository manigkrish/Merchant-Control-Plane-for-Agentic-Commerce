# ADR-0009: Observability Standard (W3C Trace Context + OTLP)

## Status
Accepted

## Context
I need consistent tracing and correlation across services, Kafka, and external dependencies.

## Decision
Use W3C Trace Context headers for propagation, and export telemetry via OpenTelemetry/OTLP.

## Consequences
- Pros: standard tracing and strong production readiness.
- Cons: some initial setup complexity; I’ll handle this in shared service templates (Sprint 1).
