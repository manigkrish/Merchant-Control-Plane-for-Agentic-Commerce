# ADR-0004: PostgreSQL (RDS) + pgvector for Policy-RAG

## Status
Accepted

## Context
I need a system-of-record database and vector similarity search for policy citations.

## Decision
Use PostgreSQL on RDS for relational data and audit logs, and enable pgvector for embedding storage and retrieval.

## Consequences
- Pros: strong consistency for audit data, a simpler overall architecture, and pgvector is widely adopted.
- Cons: requires index tuning and cost management; mitigate with environment sizing and retention policies.
