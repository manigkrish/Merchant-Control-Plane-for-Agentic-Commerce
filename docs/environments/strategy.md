# Environments Strategy

Region: us-east-2 (Ohio)

This project defines four environments:

## 1) local
- docker-compose stack (Postgres, Redis, Kafka, S3-compatible storage)
- All services run locally

## 2) staging
- Minimal AWS footprint
- Purpose: integration validation, CI deploy rehearsal

## 3) prod-lite (portfolio MVP)
- ECS + ALB + RDS + Redis + S3
- Kafka usage minimized (or smaller footprint)
- Observability baseline dashboards

## 4) prod-managed (full target)
- ECS + CodeDeploy blue/green
- MSK Serverless
- Full dashboards + alerts + load and fault testing automation

Matrix rule:
- Any feature must run in local first.
- staging must be deployable from CI with OIDC.
