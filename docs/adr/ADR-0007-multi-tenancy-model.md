# ADR-0007: Multi-Tenancy Model (Tenant Context First-Class)

## Status
Accepted

## Context
Multi-tenancy is required from day 1.

## Decision
Tenant context is derived from a verified identity (JWT claims and/or verified attestation identity), not from request payload fields.
All service-layer operations require tenant_id context and must enforce tenant scoping at the database query level.

## Consequences
- Pros: consistent tenant isolation and easier audits.
- Cons: more boilerplate; mitigated with shared libraries and request filters.
