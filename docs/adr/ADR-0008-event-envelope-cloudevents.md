# ADR-0008: Event Envelope (CloudEvents)

## Status
Accepted

## Context
I need interoperable events with consistent metadata for routing and observability.

## Decision
Use CloudEvents JSON as the canonical event envelope for Kafka.

## Consequences
- Pros: standard attributes, easier tooling, and clearer contracts.
- Cons: some overhead; acceptable for a platform-grade system.
