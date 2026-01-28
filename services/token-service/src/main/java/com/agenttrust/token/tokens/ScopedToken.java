package com.agenttrust.token.tokens;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "scoped_tokens")
public class ScopedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_id", nullable = false, unique = true)
    private UUID tokenId;

    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;

    /**
     * SHA-256(rawToken) as lowercase hex (64 chars). Raw token is never stored.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "merchant_id", nullable = false, length = 128)
    private String merchantId;

    @Column(name = "max_amount_minor", nullable = false)
    private long maxAmountMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "not_before")
    private Instant notBefore;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 64)
    private String revocationReason;

    protected ScopedToken() {
        // for JPA
    }

    public ScopedToken(
            UUID tokenId,
            String tenantId,
            String tokenHash,
            String action,
            String merchantId,
            long maxAmountMinor,
            String currency,
            Instant issuedAt,
            Instant notBefore,
            Instant expiresAt
    ) {
        this.tokenId = Objects.requireNonNull(tokenId, "tokenId");
        this.tenantId = requireNonBlank(tenantId, "tenantId");
        this.tokenHash = requireNonBlank(tokenHash, "tokenHash");
        this.action = requireNonBlank(action, "action");
        this.merchantId = requireNonBlank(merchantId, "merchantId");
        this.maxAmountMinor = maxAmountMinor;
        this.currency = requireNonBlank(currency, "currency");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.notBefore = notBefore;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
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

    public String getTokenHash() {
        return tokenHash;
    }

    public String getAction() {
        return action;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public long getMaxAmountMinor() {
        return maxAmountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void revoke(Instant revokedAt, String reason) {
        this.revokedAt = Objects.requireNonNull(revokedAt, "revokedAt");
        this.revocationReason = reason;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        // Expired at the boundary (now >= expiresAt)
        return !now.isBefore(expiresAt);
    }

    public boolean isNotYetValidAt(Instant now) {
        return notBefore != null && now.isBefore(notBefore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScopedToken that)) return false;
        return tokenId != null && tokenId.equals(that.tokenId);
    }

    @Override
    public int hashCode() {
        return (tokenId == null) ? 0 : tokenId.hashCode();
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
