# Architecture diagram

```mermaid
flowchart LR
  subgraph Clients
    A[Agent Client<br/>RFC 9421 Signed Requests]
    M[Merchant Admin Client<br/>Control Plane]
  end

  subgraph Edge["Edge (Public)"]
    ALB[ALB (DNS endpoint)<br/>Path routing]
    GW[gateway-service<br/>Data plane edge: AuthZ + Rate Limit + Routing]
    ADM[admin-service<br/>Control plane public API: RBAC + onboarding]
  end

  subgraph Core["Core Services (Private)"]
    DEC[decision-service<br/>Orchestrates deterministic ALLOW/CHALLENGE/DENY]
    ATT[attestation-service<br/>RFC 9421 verify + TAP profile + replay defense]
    REG[agent-registry-service<br/>Keys + rotation + status]
    TOK[token-service<br/>Scoped tokens + lifecycle]
    POL[policy-service<br/>Version + publish policies]
    RAG[rag-service<br/>Retrieve + explain w/ citations (non-authoritative)]
    OPS[ops-agent-service<br/>Allowlisted tools + schema validation]
    ING[ingestion-worker (Python)<br/>Chunk + embed + upsert]
  end

  subgraph Data["Data & Infra"]
    PG[(PostgreSQL + pgvector)]
    R[(Redis)]
    K[(Kafka / MSK Serverless)]
    S3[(S3: policy docs + evidence exports)]
    LLM[(OpenAI API)]
  end

  %% Public routing
  A --> ALB -->|/v1/agent/*| GW
  M --> ALB -->|/v1/admin/*| ADM

  %% Data plane orchestration
  GW --> DEC
  DEC --> ATT
  ATT --> REG
  ATT --> R
  DEC --> TOK
  TOK --> R
  DEC --> RAG
  RAG --> PG
  RAG --> LLM

  %% Control plane
  ADM --> POL
  ADM --> REG
  ADM --> TOK
  POL --> S3
  POL --> PG
  POL --> K

  %% Ingestion
  K --> ING
  ING --> PG
  ING --> K

  %% Ops agent (internal; can be invoked by admins/ops)
  ADM --> OPS
  OPS --> K
  OPS --> PG
```
