# API Error Model (RFC 9457 Problem Details)

This project uses **RFC 9457 Problem Details** for all HTTP API errors.

Content-Type:
- application/problem+json

Required fields:
- type (URI identifying the problem type)
- title
- status

Recommended fields:
- detail
- instance
- traceId (our addition for correlation)

Example:
```json
{
  "type": "https://agenttrust-gateway.local/problems/invalid-attestation",
  "title": "Invalid attestation signature",
  "status": 400,
  "detail": "Signature validation failed for keyid=agent-key-123",
  "instance": "/v1/agent/decisions/evaluate",
  "traceId": "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
}
```
