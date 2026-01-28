package com.agenttrust.decision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agenttrust.decision")
public record DecisionProperties(
        Idempotency idempotency
) {
    public record Idempotency(
            long ttlSeconds
    ) {
    }
}
