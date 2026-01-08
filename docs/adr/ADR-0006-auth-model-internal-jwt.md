# ADR-0006: Auth Model for Control Plane (MVP Internal JWT, Future IdP)

## Status
Accepted

## Context
I need working authentication for the MVP without integrating an external IdP, but I also need a clean path to swap to Cognito/Okta/Auth0 later.

## Decision
MVP: the Admin service issues JWT access tokens and publishes a JWKS endpoint for verification.
Design rule: services validate JWTs via JWKS, not by calling Admin directly.

## Consequences
- Pros: fast MVP, realistic architecture, and an easy future swap (the IdP becomes the JWT issuer).
- Cons: the Admin service becomes security-sensitive; it needs key rotation and strict auditing.
