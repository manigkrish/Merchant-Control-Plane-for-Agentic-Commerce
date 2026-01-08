# Security Policy

This repository is a public portfolio project. Security is a core focus of the design and implementation.

## Reporting a vulnerability

Please do **not** open a public GitHub issue for security vulnerabilities.

Instead, report privately by emailing:
- manikandan.gkrish@gmail.com

Include:
- A clear description of the issue and potential impact
- Steps to reproduce (proof-of-concept if possible)
- Any logs or screenshots that help diagnosis
- Your suggested fix, if you have one

## Response expectations

Best-effort response within:
- 7 days for initial acknowledgement
- 14 days for an initial remediation plan (or status update)

## Scope

In scope:
- Request attestation verification (RFC 9421 + TAP-compatible profile)
- Nonce replay protection logic
- Scoped permission tokens and enforcement
- Multi-tenant isolation controls
- Ops agent tool allowlisting and schema validation
- Secret handling, CI/CD authentication posture, and audit logging behavior

Out of scope (portfolio context):
- Internet-scale DDoS mitigation
- Full PCI compliance program
- Third-party service security beyond documented integration boundaries
