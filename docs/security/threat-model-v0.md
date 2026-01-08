# Threat Model v0

This is the initial threat model for AgentTrust Gateway.

## Assets
- Tenant isolation boundary (data separation across merchants)
- Agent public keys and key rotation metadata
- Scoped permission tokens (issue/use/revoke)
- Audit logs (immutability and integrity)
- Policy documents + embeddings
- Ops agent tool endpoints and audit trails

## Trust boundaries
1. Public internet → Gateway (untrusted)
2. Gateway → internal services (trusted network, still authenticated)
3. Services → data stores (Postgres/Redis/S3/Kafka)
4. RAG/Ops agent → LLM provider (external)

## Major threats and mitigations

### Attestation replay
Threat: attacker replays a signed request within the valid window.
Mitigations:
- Nonce replay cache in Redis with TTL equal to allowed window
- Enforce created/expires constraints (TAP profile)

### Key compromise / key substitution
Threat: attacker uses a compromised key or tricks key lookup.
Mitigations:
- Keyid resolved from trusted registry
- Cache with expiry; block if missing/expired (TAP profile)
- Audit key rotations and verification outcomes

### Token abuse / privilege escalation
Threat: token used outside allowed scope (merchant/action/amount/expiry).
Mitigations:
- Deterministic enforcement in Token/Decision services
- Emit token misuse events + rate limit suspicious principals

### Tenant isolation failure
Threat: cross-tenant access to policies, tokens, audits.
Mitigations:
- Tenant derived from verified identity only (never from request body)
- Row-level checks in all repositories (tenant_id required in queries)
- TenantId included in Kafka partitioning and event metadata

### Prompt injection / data exfiltration via RAG
Threat: malicious policy text tries to override instructions or exfiltrate secrets.
Mitigations:
- Strict redaction of secrets/PII before sending to LLM
- Retrieval filtering by tenant + policy version
- “Cite-or-refuse”: model must cite retrieved policy chunks; no citations => refuse
- Constrain context window size; avoid arbitrary document dumping

### Ops agent tool abuse
Threat: attacker tries to call unauthorized tools or harmful parameters.
Mitigations:
- Allowlist-only tools
- JSON schema validation on tool input/output
- RBAC gate per tool
- Idempotency keys for state-changing tools
- Immutable audit logs for every tool call

## Out of scope (v0)
- Full PCI scope and payment instrument handling (we do not store card data)
- Human identity proofing / KYC
- Browser/device attestation
