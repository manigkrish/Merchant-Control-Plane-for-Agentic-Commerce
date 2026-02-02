package com.agenttrust.decision.config;

import java.util.Objects;

/**
 * Small helper for consistent idempotency keying + documentation of invariants.
 *
 * Redis key shape:
 *   idem:{tenantId}:{idempotencyKey}
 */
public final class IdempotencyKeySupport {

    private static final int IDEMPOTENCY_KEY_MIN = 16;
    private static final int IDEMPOTENCY_KEY_MAX = 128;

    private final DecisionProperties properties;
    private final DecisionConfig.DecisionRequestIdentity requestIdentity;

    public IdempotencyKeySupport(
            DecisionProperties properties,
            DecisionConfig.DecisionRequestIdentity requestIdentity
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.requestIdentity = Objects.requireNonNull(requestIdentity, "requestIdentity");
    }

    public long ttlSeconds() {
        return properties.idempotency().ttlSeconds();
    }

    /**
     * External semantic identity for requestHash:
     * method=POST, path=/v1/agent/decisions/evaluate (constants for Sprint 4).
     */
    public DecisionConfig.DecisionRequestIdentity requestIdentity() {
        return requestIdentity;
    }

    public String redisKey(String tenantId, String idempotencyKey) {
        String t = requireNonBlank(tenantId, "tenantId");
        String k = normalizeAndValidateIdempotencyKey(idempotencyKey);
        return "idem:" + t + ":" + k;
    }

    public String normalizeAndValidateIdempotencyKey(String idempotencyKey) {
        String k = requireNonBlank(idempotencyKey, "Idempotency-Key");
        if (k.length() < IDEMPOTENCY_KEY_MIN || k.length() > IDEMPOTENCY_KEY_MAX) {
            throw new IllegalArgumentException("Idempotency-Key length must be between "
                    + IDEMPOTENCY_KEY_MIN + " and " + IDEMPOTENCY_KEY_MAX);
        }
        return k;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
