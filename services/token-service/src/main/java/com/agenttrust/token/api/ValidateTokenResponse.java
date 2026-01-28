package com.agenttrust.token.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Hot-path validation response used by decision-service.
 *
 * Never includes raw token material.
 */
public record ValidateTokenResponse(
        boolean valid,
        UUID tokenId,
        Instant expiresAt,
        String reasonCode
) {
    public static ValidateTokenResponse valid(UUID tokenId, Instant expiresAt) {
        return new ValidateTokenResponse(true, tokenId, expiresAt, null);
    }

    public static ValidateTokenResponse invalid(String reasonCode) {
        return new ValidateTokenResponse(false, null, null, reasonCode);
    }
}
