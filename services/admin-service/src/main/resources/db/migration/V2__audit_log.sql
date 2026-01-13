CREATE TABLE IF NOT EXISTS audit_log (
  audit_id UUID PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  event_type TEXT NOT NULL,
  tenant_id TEXT NOT NULL,

  actor_tenant_id TEXT,
  actor_subject TEXT,

  correlation_id TEXT,
  traceparent TEXT,

  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_time
  ON audit_log (tenant_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_event_time
  ON audit_log (event_type, tenant_id, occurred_at DESC);
