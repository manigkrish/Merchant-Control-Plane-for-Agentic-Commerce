# Observability Contract (Trace Propagation)

This project uses **W3C Trace Context** propagation.

Required headers to propagate end-to-end:
- traceparent
- tracestate (if present)

Rules:
1. If an incoming request has traceparent, we join the trace.
2. If it does not, we start a new trace.
3. We propagate trace headers to all internal HTTP calls and Kafka messages (as metadata).
4. Logs must include:
   - trace_id
   - span_id
   - tenant_id (if known)
   - request_id / idempotency_key (if present)

Rationale:
- Enables distributed tracing across gateway, decisioning, ingestion workers, and ops workflows.
