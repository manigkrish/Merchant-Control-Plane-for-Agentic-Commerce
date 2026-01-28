package com.agenttrust.token.tokens;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "scoped_token_usage")
public class ScopedTokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * References scoped_tokens.token_id (UUID). Raw token is never stored.
     */
    @Column(name = "token_id", nullable = false)
    private UUID tokenId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "traceparent", length = 256)
    private String traceparent;

    /**
     * Validation outcome, e.g. VALID / INVALID / EXPIRED / REVOKED.
     */
    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    protected ScopedTokenUsage() {
        // for JPA
    }

    public ScopedTokenUsage(
            UUID tokenId,
            String tenantId,
            String result,
            String reasonCode,
            String correlationId,
            String traceparent
    ) {
        this.tokenId = Objects.requireNonNull(tokenId, "tokenId");
        this.tenantId = requireNonBlank(tenantId, "tenantId");
        this.result = requireNonBlank(result, "result");
        this.reasonCode = reasonCode;
        this.correlationId = correlationId;
        this.traceparent = traceparent;
    }

    @PrePersist
    void onInsert() {
        if (usedAt == null) {
            usedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getTokenId() {
        return tokenId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceparent() {
        return traceparent;
    }

    public String getResult() {
        return result;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
