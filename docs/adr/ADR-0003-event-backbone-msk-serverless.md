# ADR-0003: Event Backbone via Kafka (MSK Serverless)

## Status
Accepted

## Context
I need async workflows and durable eventing for token lifecycle, ingestion, decisions, and ops actions.

## Decision
Use Kafka as the event backbone, deployed as MSK Serverless in AWS, and standardize events using a CloudEvents envelope.

## Consequences
- Pros: scalable eventing, better decoupling, and supports DLQs/reprocessing patterns.
- Cons: needs careful cost controls (topics, partitions, retention).
