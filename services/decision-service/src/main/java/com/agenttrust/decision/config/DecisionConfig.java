package com.agenttrust.decision.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(DecisionProperties.class)
public class DecisionConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DecisionRequestIdentity decisionRequestIdentity() {
        // For Sprint 4, idempotency uses the EXTERNAL evaluate identity (not the internal endpoint path).
        return DecisionRequestIdentity.evaluate();
    }

    @Bean
    IdempotencyKeySupport idempotencyKeySupport(DecisionProperties props, DecisionRequestIdentity identity) {
        return new IdempotencyKeySupport(props, identity);
    }
}

/**
 * External semantic identity used when computing requestHash for idempotency.
 *
 * We intentionally do NOT use the internal endpoint path (/internal/v1/decisions) to avoid confusion:
 * idempotency is keyed to the semantic request (evaluate), not the internal transport hop.
 */
record DecisionRequestIdentity(String method, String path) {

    static DecisionRequestIdentity evaluate() {
        return new DecisionRequestIdentity("POST", "/v1/agent/decisions/evaluate");
    }

    String canonical() {
        return method + " " + path;
    }
}

/**
 * Small helper for consistent idempotency keying + documentation of invariants.
 *
 * Redis key shape:
 *   idem:{tenantId}:{idempotencyKey}
 */
final class IdempotencyKeySupport {

    private static final int IDEMPOTENCY_KEY_MIN = 16;
    private static final int IDEMPOTENCY_KEY_MAX = 128;

    private final DecisionProperties properties;
    private final DecisionRequestIdentity requestIdentity;

    IdempotencyKeySupport(DecisionProperties properties, DecisionRequestIdentity requestIdentity) {
        this.properties = properties;
        this.requestIdentity = requestIdentity;
    }

    long ttlSeconds() {
        return properties.idempotency().ttlSeconds();
    }

    DecisionRequestIdentity requestIdentity() {
        return requestIdentity;
    }

    String redisKey(String tenantId, String idempotencyKey) {
        String t = requireNonBlank(tenantId, "tenantId");
        String k = normalizeAndValidateIdempotencyKey(idempotencyKey);
        return "idem:" + t + ":" + k;
    }

    String normalizeAndValidateIdempotencyKey(String idempotencyKey) {
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
