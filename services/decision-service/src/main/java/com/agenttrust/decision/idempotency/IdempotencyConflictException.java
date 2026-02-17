package com.agenttrust.decision.idempotency;

/**
 * Thrown when the same Idempotency-Key is reused for a different logical request (different requestHash).
 *
 * This will be mapped to:
 *  - HTTP 409
 *  - application/problem+json
 *  - errorCode = IDEMPOTENCY_KEY_REUSE_CONFLICT
 */
public class IdempotencyConflictException extends RuntimeException {

    private final String tenantId;
    private final String idempotencyKey;

    public IdempotencyConflictException(String tenantId, String idempotencyKey) {
        super("Idempotency-Key reuse conflict");
        this.tenantId = tenantId;
        this.idempotencyKey = idempotencyKey;
    }

    public String tenantId() {
        return tenantId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
