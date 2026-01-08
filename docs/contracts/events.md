# Event Envelope Contract (CloudEvents)

Decision: Use **CloudEvents** (JSON) as the canonical event envelope for Kafka messages.

Required CloudEvents attributes:
- specversion
- id
- source
- type
- time (recommended)
- datacontenttype (recommended)

Versioning rule:
- `type` includes a version suffix, e.g.:
  - com.agenttrust.token.issued.v1
  - com.agenttrust.decision.made.v1

## Field naming convention (events)
Event `data` fields use **snake_case** (e.g., `tenant_id`, `token_id`) to stay consistent across languages and services.

Partition key rule:
- Default partition key = tenant_id
- If event pertains to a token, use token_id as an ordering key only when needed (still include tenant_id for scoping).

Schema rule:
- Each event type has a JSON schema stored in-repo (added later in Sprint 2/3).
