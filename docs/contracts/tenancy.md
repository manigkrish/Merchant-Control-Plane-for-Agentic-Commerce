# Tenancy Contract (Tenant Derivation + Propagation)

## Goal
Tenant (merchant) isolation is enforced consistently across services.

## Tenant derivation (source of truth)
Tenant identity is derived from verified identity, not from client-supplied fields:

- For data plane requests: tenant is derived from cryptographic attestation verification (RFC 9421) and registry mapping.
- For control plane requests: tenant context is derived from authenticated admin/operator identity and the target tenant resource.

External clients must not be able to select tenant by passing tenantId in a request body or header.

## Propagation rules (internal only)
Once tenant has been derived:
- Tenant context is propagated only via trusted internal mechanisms (e.g., internal auth token claims).
- Tenant context must not be accepted from arbitrary headers set by external clients.

## Logging requirements
All services must include `tenant_id` in logs and events when it is known and applicable.
If tenant cannot be determined, logs must explicitly show `tenant_id=unknown` and include the correlation/trace id.

## Event envelope requirement
CloudEvents payloads that are tenant-scoped must include `tenant_id` in the event data.
Partitioning defaults to `tenant_id` unless a stronger ordering key is required.
