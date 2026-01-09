# AgentTrust Local Infrastructure (Docker Compose)

This directory provides the local dependency stack for:

**AgentTrust Gateway (Agentic Commerce Trust + Scoped Tokens + Policy-RAG + Guardrailed Ops Agent) (with optional TAP verifier)**

It runs the shared infrastructure dependencies that the Java services will rely on:
- PostgreSQL + pgvector
- Redis
- Kafka (single-node KRaft)
- MinIO (S3-compatible storage)

The goal is a **repeatable local dev environment** that mirrors production concepts without requiring AWS resources during local iteration (and without overloading an 8GB RAM machine).

---

## Prerequisites

### Required
- Windows 11 + WSL2 (Ubuntu)
- Docker Desktop installed and running
- Docker Desktop **WSL Integration enabled** for your Ubuntu distro
- From WSL, these must work:

```bash
docker version
docker compose version
```

### Recommended
Install `jq` (used for optional JSON formatting in troubleshooting):

```bash
sudo apt-get update && sudo apt-get install -y jq
```

---

## What starts locally

| Component | Container | Ports | Purpose |
| --- | --- | ---: | --- |
| Postgres + pgvector | `agenttrust-postgres` | 5432 | System-of-record storage + later vector storage |
| Redis | `agenttrust-redis` | 6379 | Nonce replay cache, idempotency keys, rate limiting (later) |
| Kafka (KRaft, single node) | `agenttrust-kafka` | 9092 | Event backbone for local dev (conceptually matches MSK usage later) |
| MinIO (S3-compatible) | `agenttrust-minio` | 9000, 9001 | Local S3 for policy docs and evidence bundles |

Notes:
- Topic auto-creation is disabled by default to prevent accidental topic drift.
- Right after startup you may see `health: starting` briefly; that is normal.

---

## Quickstart (recommended)

Run these from the **repo root**:

Start infrastructure:
```bash
make up
make ps
```

Follow logs (Ctrl+C to stop following):
```bash
make logs
```

Stop infrastructure:
```bash
make down
```

Important:
- If you press Ctrl+C while following logs, `make` may print `Error 130`. That is normal and does not indicate a real failure.

---

## Direct Docker Compose (if you prefer)

Start:
```bash
docker compose -f infra/docker-compose/docker-compose.yml up -d
docker compose -f infra/docker-compose/docker-compose.yml ps
```

Stop:
```bash
docker compose -f infra/docker-compose/docker-compose.yml down
```

---

## Environment variables (`.env`)

This setup supports local overrides via:
- `infra/docker-compose/.env` (local only; must never be committed)
- `infra/docker-compose/.env.example` (committed; onboarding reference)

Create your local `.env`:
```bash
cp infra/docker-compose/.env.example infra/docker-compose/.env
nano infra/docker-compose/.env
```

Security hygiene:
- Never commit `infra/docker-compose/.env`.
- Only commit `.env.example`.

---

## Connection details and quick checks

### PostgreSQL
- Host: `localhost`
- Port: `5432`
- Database: `agenttrust`
- User: `agenttrust`
- Password: `agenttrust`

Quick check:
```bash
docker exec -it agenttrust-postgres psql -U agenttrust -d agenttrust -c "SELECT 1;"
```

### Redis
- Host: `localhost`
- Port: `6379`

Quick check:
```bash
docker exec -it agenttrust-redis redis-cli ping
```

### Kafka
- Bootstrap server: `localhost:9092`

Kafka scripts inside the container are typically under `/opt/kafka/bin/`:

```bash
docker exec -it agenttrust-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

If it prints nothing, that’s fine (no topics created yet).

### MinIO
- Console UI: http://localhost:9001
- S3 API endpoint: http://localhost:9000

Default local credentials (override via `.env`):
- user: `agenttrust`
- password: `agenttrust123`

---

## Troubleshooting

### Docker not found in WSL
Enable Docker Desktop WSL integration:
Docker Desktop → Settings → Resources → WSL Integration → enable Ubuntu distro.

Verify in WSL:
```bash
docker version
docker compose version
```

### Containers stuck in `health: starting`
Wait 10–60 seconds and re-check:
```bash
make ps
```

Optional: inspect health details (Postgres/Redis/Kafka):
```bash
docker inspect --format='{{json .State.Health}}' agenttrust-postgres | jq .
docker inspect --format='{{json .State.Health}}' agenttrust-redis | jq .
docker inspect --format='{{json .State.Health}}' agenttrust-kafka | jq .
```

### Port conflicts
If you already have local services using the same ports, Docker may fail to start.
Common conflicts:
- Postgres 5432
- Redis 6379
- Kafka 9092
- MinIO 9000/9001

Fix options:
1) Stop the conflicting service, or
2) Change the host port mapping in `docker-compose.yml`.

### Kafka “healthy” vs “running”
Kafka can be running even if the healthcheck takes longer to pass on first boot. Check:
```bash
docker logs --tail=200 agenttrust-kafka
```

---

## Reset (destructive)

This removes containers **and volumes** (all local data will be lost):
```bash
docker compose -f infra/docker-compose/docker-compose.yml down -v
```

---

## Local-only security note

This stack uses development-friendly defaults (plaintext, local passwords).
Do not reuse these values in staging/production. Production will use AWS-managed services and proper secret management.
