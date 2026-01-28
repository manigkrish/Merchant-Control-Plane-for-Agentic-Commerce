package com.agenttrust.token.tokens;

import com.agenttrust.token.api.IssueTokenRequest;
import com.agenttrust.token.api.IssueTokenResponse;
import com.agenttrust.token.api.ValidateTokenRequest;
import com.agenttrust.token.api.ValidateTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TOKEN_PREFIX = "stkn_";

    private final ScopedTokenRepository tokenRepository;
    private final ScopedTokenUsageRepository usageRepository;
    private final Clock clock;

    public TokenService(
            ScopedTokenRepository tokenRepository,
            ScopedTokenUsageRepository usageRepository,
            ObjectProvider<Clock> clockProvider
    ) {
        this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository");
        this.usageRepository = Objects.requireNonNull(usageRepository, "usageRepository");
        this.clock = Optional.ofNullable(clockProvider.getIfAvailable()).orElse(Clock.systemUTC());
    }

    /**
     * Issues a new opaque scoped token.
     *
     * IMPORTANT:
     * - Returns the raw token only at issue time.
     * - Persists only tokenHash (SHA-256(rawToken) hex) and tokenId for safe audit/logging.
     *
     * Merchant identity in Sprint 4:
     * - merchantId constraint is enforced as equal to tenantId (Option A).
     */
    @Transactional
    public IssueTokenResponse issue(String tenantId, IssueTokenRequest request) {
        String t = requireNonBlank(tenantId, "tenantId");
        IssueTokenRequest req = Objects.requireNonNull(request, "request");

        // Option A invariant: merchant identity equals tenant identity.
        if (!t.equals(req.merchantId())) {
            throw new IllegalArgumentException("merchantId must equal tenantId for Sprint 4");
        }
        if (req.maxAmountMinor() == null || req.maxAmountMinor() < 0) {
            throw new IllegalArgumentException("maxAmountMinor must be >= 0");
        }
        if (req.ttlSeconds() == null || req.ttlSeconds() < 1) {
            throw new IllegalArgumentException("ttlSeconds must be >= 1");
        }

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(req.ttlSeconds());

        // Rare collisions can happen with unique constraints; retry a few times safely.
        for (int attempt = 1; attempt <= 3; attempt++) {
            UUID tokenId = UUID.randomUUID();
            String rawToken = generateRawToken();
            String tokenHash = TokenHasher.sha256Hex(rawToken);

            ScopedToken entity = new ScopedToken(
                    tokenId,
                    t,
                    tokenHash,
                    req.action(),
                    req.merchantId(),
                    req.maxAmountMinor(),
                    req.currency(),
                    now,
                    req.notBefore(),
                    expiresAt
            );

            try {
                tokenRepository.save(entity);
                // Never log rawToken.
                log.info("Issued scoped token tokenId={} tenantId={}", tokenId, t);
                return new IssueTokenResponse(tokenId, rawToken, expiresAt);
            } catch (DataIntegrityViolationException ex) {
                // Could be token_hash or token_id uniqueness collision. Extremely unlikely, but safe to retry.
                log.warn("Token issuance collision attempt={} tenantId={}", attempt, t);
            }
        }

        throw new IllegalStateException("Failed to issue token after retries");
    }

    /**
     * Validates token + constraints for the decision hot path.
     *
     * Behavior:
     * - Uses rawToken only to compute tokenHash and look up the token record.
     * - Enforces tenant scoping by lookup using (tenantId, tokenHash).
     * - Records usage/audit when a token record exists (valid or invalid due to constraints).
     *
     * Does NOT log raw token.
     */
    @Transactional
    public ValidateTokenResponse validate(
            String tenantId,
            ValidateTokenRequest request,
            String correlationId,
            String traceparent
    ) {
        String t = requireNonBlank(tenantId, "tenantId");
        ValidateTokenRequest req = Objects.requireNonNull(request, "request");

        Instant now = Instant.now(clock);
        Instant usedAt = now;

        String tokenHash = TokenHasher.sha256Hex(req.rawToken());
        Optional<ScopedToken> tokenOpt = tokenRepository.findByTenantIdAndTokenHash(t, tokenHash);

        if (tokenOpt.isEmpty()) {
            // Token not found: no tokenId exists to insert into scoped_token_usage table.
            // We return INVALID and rely on upstream services to log correlated Problem Details.
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_NOT_FOUND);
        }

        ScopedToken token = tokenOpt.get();

        // Option A invariant: merchant identity equals tenant identity.
        if (!t.equals(token.getMerchantId())) {
            recordUsage(token, t, usedAt, "INVALID", ReasonCodes.TOKEN_MERCHANT_MISMATCH, correlationId, traceparent);
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_MERCHANT_MISMATCH);
        }

        if (token.isRevoked()) {
            recordUsage(token, t, usedAt, "INVALID", ReasonCodes.TOKEN_REVOKED, correlationId, traceparent);
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_REVOKED);
        }
        if (token.isNotYetValidAt(now)) {
            recordUsage(token, t, usedAt, "INVALID", ReasonCodes.TOKEN_NOT_YET_VALID, correlationId, traceparent);
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_NOT_YET_VALID);
        }
        if (token.isExpiredAt(now)) {
            recordUsage(token, t, usedAt, "INVALID", ReasonCodes.TOKEN_EXPIRED, correlationId, traceparent);
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_EXPIRED);
        }

        if (!token.getAction().equals(req.action())) {
            recordUsage(token, t, usedAt, "INVALID", ReasonCodes.TOKEN_ACTION_MISMATCH, correlationId, traceparent);
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_ACTION_MISMATCH);
        }
        if (!token.getCurrency().equals(req.currency())) {
            recordUsage(token, t, usedAt, "INVALID", ReasonCodes.TOKEN_CURRENCY_MISMATCH, correlationId, traceparent);
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_CURRENCY_MISMATCH);
        }
        if (req.amount() == null || req.amount() < 0) {
            recordUsage(token, t, usedAt, "INVALID", ReasonCodes.TOKEN_AMOUNT_INVALID, correlationId, traceparent);
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_AMOUNT_INVALID);
        }
        if (req.amount() > token.getMaxAmountMinor()) {
            recordUsage(token, t, usedAt, "INVALID", ReasonCodes.TOKEN_AMOUNT_EXCEEDS_LIMIT, correlationId, traceparent);
            return ValidateTokenResponse.invalid(ReasonCodes.TOKEN_AMOUNT_EXCEEDS_LIMIT);
        }

        recordUsage(token, t, usedAt, "VALID", null, correlationId, traceparent);
        return ValidateTokenResponse.valid(token.getTokenId(), token.getExpiresAt());
    }

    @Transactional
    public void revoke(String tenantId, UUID tokenId, String reasonCode) {
        String t = requireNonBlank(tenantId, "tenantId");
        UUID id = Objects.requireNonNull(tokenId, "tokenId");

        ScopedToken token = tokenRepository.findByTokenId(id)
                .orElseThrow(() -> new IllegalArgumentException("tokenId not found"));

        if (!t.equals(token.getTenantId())) {
            throw new IllegalArgumentException("token does not belong to tenant");
        }

        if (!token.isRevoked()) {
            token.revoke(Instant.now(clock), reasonCode);
            tokenRepository.save(token);
            log.info("Revoked scoped token tokenId={} tenantId={}", id, t);
        }
    }

    private void recordUsage(
            ScopedToken token,
            String tenantId,
            Instant usedAt,
            String result,
            String reasonCode,
            String correlationId,
            String traceparent
    ) {
        ScopedTokenUsage usage = new ScopedTokenUsage(
                token.getTokenId(),
                tenantId,
                usedAt,
                result,
                reasonCode,
                correlationId,
                traceparent
        );
        usageRepository.save(usage);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String random = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return TOKEN_PREFIX + random;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static final class ReasonCodes {
        private ReasonCodes() {
        }

        static final String TOKEN_NOT_FOUND = "TOKEN_NOT_FOUND";
        static final String TOKEN_REVOKED = "TOKEN_REVOKED";
        static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
        static final String TOKEN_NOT_YET_VALID = "TOKEN_NOT_YET_VALID";
        static final String TOKEN_ACTION_MISMATCH = "TOKEN_ACTION_MISMATCH";
        static final String TOKEN_CURRENCY_MISMATCH = "TOKEN_CURRENCY_MISMATCH";
        static final String TOKEN_AMOUNT_INVALID = "TOKEN_AMOUNT_INVALID";
        static final String TOKEN_AMOUNT_EXCEEDS_LIMIT = "TOKEN_AMOUNT_EXCEEDS_LIMIT";
        static final String TOKEN_MERCHANT_MISMATCH = "TOKEN_MERCHANT_MISMATCH";
    }
}
