package com.agenttrust.token.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * Request body for the profile-gated internal issue endpoint (local/test only).
 *
 * Tenant is NOT accepted in this body. The caller must provide tenant via the internal header X-Tenant-Id.
 */
public record IssueTokenRequest(
        @NotBlank
        @Pattern(regexp = "^PURCHASE$", message = "action must be PURCHASE for Sprint 4")
        String action,

        @NotBlank
        String merchantId,

        @NotNull
        @Min(0)
        Long maxAmountMinor,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be ISO 4217 (3 uppercase letters)")
        String currency,

        /**
         * Optional: token not valid before this instant (UTC). If null, token is valid immediately.
         */
        Instant notBefore,

        /**
         * Token lifetime in seconds from issuance time.
         */
        @NotNull
        @Min(1)
        Long ttlSeconds
) {
}
