# Audit Policy (What we log, what we redact)

## Goal
Make decisions and actions reviewable without leaking sensitive data.

## Must-audit actions
The following actions must emit an audit event:

### Data plane
- Decision computed: ALLOW / CHALLENGE / DENY
- Attestation verification result (success/failure + reason category)
- Nonce replay blocked
- Scoped token issued / used / rejected / revoked

### Control plane
- Tenant created/disabled
- Agent key registered / rotated / revoked
- Policy uploaded / versioned / published / rolled back
- Role/RBAC changes

### Ops agent
- Tool-call attempt (even if blocked)
- Tool-call executed (with validated parameters)
- Tool-call result (success/failure category)

## Required audit fields
Each audit event must include:
- event_type (versioned)
- time (UTC)
- actor_type (agent | admin | system)
- actor_id (agent_id or admin_user_id or system)
- tenant_id (when applicable)
- request_id / idempotency_key (if present)
- trace_id (from trace context)
- outcome (success/failure + reason category)
- resource identifiers (token_id, policy_version, decision_id, etc. as applicable)

## Redaction rules (non-negotiable)
Never log:
- raw secrets (API keys, JWTs, private keys)
- full token values (log token_id only)
- full payment instrument data
- full user PII (email/phone/address) unless explicitly required and approved

If sensitive content is required for debugging, log a hashed/summarized form and gate it behind restricted access in production.

## Evidence bundles
When evidence needs to be preserved for review (e.g., disputes):
- store an evidence bundle in S3 (decision_id as key prefix)
- include policy citations (chunk ids), not full policy text unless required

(Option for later hardening: S3 Object Lock for WORM-style retention.)
