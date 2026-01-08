# ADR-0010: LLM Provider + Data Policy (OpenAI API)

## Status
Accepted

## Context
I will use the OpenAI API for embeddings (policy ingestion) and for explanations/tool-calling in ops agent mode.
I need to prevent sending secrets/PII and keep the system auditable.

## Decision
Use the OpenAI API with strict controls:
- Redact secrets/PII before sending prompts or retrieved policy text.
- Prefer minimal context windows.
- “Cite-or-refuse” policy: explanations must cite retrieved policy chunks, otherwise refuse.
- Store LLM inputs/outputs in our audit system only when required and safe.

## Consequences
- Pros: high-quality models; clear production controls.
- Cons: external dependency; must degrade safely when the LLM is down (decisioning remains deterministic).

## Reference
OpenAI’s published documentation describes how API data may be retained for abuse monitoring and how
qualifying customers can request Modified Abuse Monitoring / Zero Data Retention for eligible endpoints.
This project treats OpenAI retention behavior as time-sensitive and defers to the official OpenAI ‘Your data’
and privacy documentation for the current terms.
We will explicitly set `store=false` when using APIs that support storage controls and avoid sending PII.
https://platform.openai.com/docs/guides/your-data
