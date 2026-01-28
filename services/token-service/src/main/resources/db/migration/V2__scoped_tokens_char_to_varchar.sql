-- Align column types with Hibernate defaults (VARCHAR) to keep ddl-auto=validate strict.
-- V1 used CHAR(n) for token_hash and currency; Hibernate validates these as VARCHAR by default.

ALTER TABLE scoped_tokens
  ALTER COLUMN token_hash TYPE VARCHAR(64);

ALTER TABLE scoped_tokens
  ALTER COLUMN currency TYPE VARCHAR(3);
