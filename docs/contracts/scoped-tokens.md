# Scoped Tokens (Wire Format + Transport)

## Goal
Scoped tokens represent delegated permission for agent actions. They are *not* admin JWTs and must not be used on `/v1/admin/*`.

## Token type (MVP)
- **Opaque token** (recommended for MVP): format `stkn_<random>`
- Server is authoritative for scope; token-service validates and records usage.

## Transport
- Data plane endpoints require `X-Scoped-Token: stkn_...`
- Admin endpoints use `Authorization: Bearer <admin-jwt>`

## Distinguishing from admin JWTs
- Admin JWT is only accepted on `/v1/admin/*`
- Scoped token is only accepted on `/v1/agent/*`
- Gateway enforces this separation.

## Expiry + replay
- Tokens are short-lived (minutes/hours) depending on action risk.
- Token usage is idempotent and audited by token-service.

## Logging rules
- Never log raw token values; log `token_id` only.
