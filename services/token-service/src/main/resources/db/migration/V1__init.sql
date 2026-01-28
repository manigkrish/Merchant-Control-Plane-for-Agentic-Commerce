-- Token Service schema (Sprint 4)
-- Notes:
-- - Raw scoped tokens (stkn_*) are NEVER stored.
-- - We persist only a SHA-256 token hash (hex) plus a server-side token_id for safe logging/debug.
-- - Token constraints stored here are the minimum needed for deterministic ALLOW/DENY in Sprint 4.

CREATE TABLE scoped_tokens (
  id BIGSERIAL PRIMARY KEY,

  -- Stable identifier for logging / audit without exposing token material.
  token_id UUID NOT NULL UNIQUE,

  -- First-class tenant context; never trust tenantId from request bodies.
  tenant_id VARCHAR(128) NOT NULL,

  -- SHA-256(rawToken) as lowercase hex (64 chars). Raw token is never persisted.
  token_hash CHAR(64) NOT NULL UNIQUE,

  -- MVP constraints (enforced by decision-service in Sprint 4)
  action VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(128) NOT NULL,
  max_amount_minor BIGINT NOT NULL CHECK (max_amount_minor >= 0),
  currency CHAR(3) NOT NULL,

  issued_at TIMESTAMPTZ NOT NULL,
  not_before TIMESTAMPTZ NULL,
  expires_at TIMESTAMPTZ NOT NULL,

  revoked_at TIMESTAMPTZ NULL,
  revocation_reason VARCHAR(64) NULL
);

CREATE INDEX idx_scoped_tokens_tenant_id ON scoped_tokens (tenant_id);
CREATE INDEX idx_scoped_tokens_expires_at ON scoped_tokens (expires_at);
CREATE INDEX idx_scoped_tokens_merchant_action ON scoped_tokens (merchant_id, action);

-- Append-only usage/audit trail for validations (decision-service hot path calls token-service validate).
CREATE TABLE scoped_token_usage (
  id BIGSERIAL PRIMARY KEY,

  token_id UUID NOT NULL,
  tenant_id VARCHAR(128) NOT NULL,

  used_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Observability propagation (best-effort; may be null)
  correlation_id VARCHAR(128) NULL,
  traceparent VARCHAR(256) NULL,

  -- Record the validation outcome without leaking sensitive inputs.
  result VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NULL,

  CONSTRAINT fk_scoped_token_usage_token_id
    FOREIGN KEY (token_id) REFERENCES scoped_tokens (token_id)
);

CREATE INDEX idx_scoped_token_usage_token_id_used_at ON scoped_token_usage (token_id, used_at);
CREATE INDEX idx_scoped_token_usage_tenant_id_used_at ON scoped_token_usage (tenant_id, used_at);
