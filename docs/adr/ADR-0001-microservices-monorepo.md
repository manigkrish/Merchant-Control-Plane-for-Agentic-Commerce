# ADR-0001: Microservices in a Monorepo

## Status
Accepted

## Context
I need a portfolio system with multiple services (gateway, attestation, token, policy, rag, decision, ops agent, admin).
I also need consistency across builds, tests, and CI.

## Decision
Use a monorepo with multiple Spring Boot services and shared libraries.

## Consequences
- Pros: consistent tooling; easier refactors; shared CI; unified documentation.
- Cons: requires discipline around module boundaries and ownership.
