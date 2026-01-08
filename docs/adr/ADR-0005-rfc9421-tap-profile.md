# ADR-0005: RFC 9421 HTTP Message Signatures + TAP-Compatible Profile

## Status
Accepted

## Context
I need cryptographic attestations and merchant-side verification that matches Visa TAP reference behaviors.

## Decision
Use RFC 9421 HTTP Message Signatures for signed requests and enforce a “TAP-compatible profile”:
- Required signature components and required metadata must be present
- created/expires timestamp rules (8-minute window)
- nonce replay protection (block duplicates within the window)
- key lookup via keyid with caching; reject if missing or expired

## Consequences
- Pros: standards-based and credible security primitive; aligns with public Visa TAP guidance.
- Cons: requires careful canonicalization and strict parsing; I’ll implement this in a dedicated Attestation service with exhaustive tests.
