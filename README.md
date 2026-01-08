# AgentTrust Gateway (Agentic Commerce Trust + Scoped Tokens + Policy-RAG + Guardrailed Ops Agent) (with optional TAP verifier)

AgentTrust Gateway is a backend platform I’m building to explore trust, security, and policy enforcement for agent-initiated commerce.

The system is designed as a set of Java 21 / Spring Boot microservices, with a clear separation
between a merchant control plane and a runtime gateway that handles requests coming from autonomous agents.

This repo is public and intentionally conservative about security and operational hygiene.

---

## What this project is about

The core ideas behind AgentTrust Gateway are:

- Verifying that agent-initiated requests are authentic and not replayed
- Restricting what an agent is allowed to do using scoped, auditable permissions
- Making policy decisions explainable and traceable to merchant-owned rules
- Running an operations agent that is powerful but tightly constrained
- Treating production concerns (deploys, SLOs, alerts) as first-class design inputs

This is not a demo app. It’s meant to look and feel like something you could realistically operate.

---

## High-level architecture

The platform is split into two main areas:

- **Control plane (admin service)**
  Used by merchants to define policies, issue and revoke scoped tokens, and inspect audits.

- **Data plane (gateway)**
  Receives agent-initiated requests, verifies cryptographic attestations, enforces policy, and executes allow-listed operations.

Supporting components handle policy retrieval, auditing, observability, and ops automation.

---

## Security model (summary)

- HTTP requests are signed using RFC 9421 HTTP Message Signatures
- Strict timestamp windows and nonce replay protection (TAP-compatible profile)
- Scoped permission tokens with explicit lifecycle events:
  - issue
  - use
  - revoke
  - audit
- Policy decisions are explainable and tied to versioned merchant policies
- Ops automation is guarded by:
  - explicit allowlists
  - JSON Schema validation
  - idempotent execution

More details live under `docs/security/`.

---

## Repo status

Sprint 0 only: documentation and repo hygiene.

There are no services implemented yet.
The current focus is on making architectural intent, contracts, and security assumptions explicit before writing code.

---

## Docs layout

- Architecture overview: `docs/architecture/overview.md`
- Bounded contexts: `docs/architecture/bounded-contexts.md`
- Architecture diagram (Mermaid): `docs/architecture/diagram.md`
- API contracts (OpenAPI source of truth): `docs/openapi/`
- ADRs: `docs/adr/`
- Security notes: `docs/security/`
- Contracts (errors, events, observability): `docs/contracts/`
- Environments & cost controls: `docs/environments/`, `docs/cost/`
- Testing strategy: `docs/testing/strategy.md`

---

## Local development prerequisites

I recommend running everything inside WSL (Ubuntu).

Required tools:

- Java 21
- Maven
- Docker + Docker Compose
- Terraform
- AWS CLI

---

## Security hygiene (public repo)

- Never commit secrets
- GitHub secret scanning and push protection are enabled
- Sprint 1 CI is build/test only (markdownlint + gitleaks); no AWS access from GitHub Actions
- OIDC-based AWS authentication is planned for later deploy sprints (see docs/security/ci-oidc.md)
- No long-lived AWS credentials will be stored in GitHub

See:

- `docs/security/repo-guardrails.md`
- `docs/security/ci-oidc.md`

---

## Local git hooks (recommended)

Enable the pre-commit hook to block accidental secret commits:

```bash
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
tools/precommit/no-secrets.sh
```
