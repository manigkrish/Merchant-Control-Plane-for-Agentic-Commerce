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
        // Idempotency is keyed to the external semantic identity (evaluate), not the internal transport hop.
        return DecisionRequestIdentity.evaluate();
    }

    @Bean
    IdempotencyKeySupport idempotencyKeySupport(DecisionProperties props, DecisionRequestIdentity identity) {
        return new IdempotencyKeySupport(props, identity);
    }

    /**
     * External semantic identity used when computing requestHash for idempotency.
     *
     * For Sprint 4, idempotency uses the EXTERNAL evaluate identity (POST + /v1/agent/decisions/evaluate)
     * as constants, not the internal endpoint path (/internal/v1/decisions).
     */
    public record DecisionRequestIdentity(String method, String path) {

        public static DecisionRequestIdentity evaluate() {
            return new DecisionRequestIdentity("POST", "/v1/agent/decisions/evaluate");
        }

        public String canonical() {
            return method + " " + path;
        }
    }
}
