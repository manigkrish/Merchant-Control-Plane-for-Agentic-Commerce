# ADR-0002: AWS ECS with CodeDeploy Blue/Green

## Status
Accepted

## Context
I need production-grade deployments with safe rollouts and an easy rollback path.

## Decision
Deploy services on AWS ECS behind an ALB, and use CodeDeploy blue/green to shift traffic safely during releases.

## Consequences
- Pros: safer releases, automated rollback, and a clear ops story for interviews.
- Cons: more setup complexity; I’ll phase this in by environment (staging → prod-lite → prod-managed).
