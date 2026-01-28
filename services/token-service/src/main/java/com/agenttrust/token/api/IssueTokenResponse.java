package com.agenttrust.token.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned only at issue time. The rawToken must never be persisted or logged.
 */
public record IssueTokenResponse(
        UUID tokenId,
        String rawToken,
        Instant expiresAt
) {
}
