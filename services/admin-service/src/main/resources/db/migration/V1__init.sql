-- V1__init.sql
-- Sprint 2: initial admin-service schema (tenants + admin_users)

CREATE TABLE IF NOT EXISTS tenants (
  tenant_id       TEXT PRIMARY KEY,
  display_name    TEXT NOT NULL,
  status          TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT tenants_tenant_id_len CHECK (char_length(tenant_id) BETWEEN 3 AND 64),
  CONSTRAINT tenants_status_valid CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS tenants_display_name_uq
  ON tenants (display_name);

-- Seed the reserved platform tenant required by Sprint 2 auth model.
INSERT INTO tenants (tenant_id, display_name, status)
VALUES ('__platform__', 'Platform', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS admin_users (
  user_id         UUID PRIMARY KEY,
  tenant_id       TEXT NOT NULL,
  username        TEXT NOT NULL,
  password_hash   TEXT NOT NULL,
  roles           TEXT[] NOT NULL,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT admin_users_username_len CHECK (char_length(username) BETWEEN 3 AND 128),
  CONSTRAINT admin_users_password_hash_len CHECK (char_length(password_hash) BETWEEN 20 AND 255),
  CONSTRAINT admin_users_roles_not_empty CHECK (array_length(roles, 1) IS NOT NULL),
  CONSTRAINT admin_users_tenant_fk FOREIGN KEY (tenant_id) REFERENCES tenants (tenant_id)
);

-- Usernames are unique within a tenant (multi-tenant safe).
CREATE UNIQUE INDEX IF NOT EXISTS admin_users_tenant_username_uq
  ON admin_users (tenant_id, username);

CREATE INDEX IF NOT EXISTS admin_users_tenant_id_idx
  ON admin_users (tenant_id);
