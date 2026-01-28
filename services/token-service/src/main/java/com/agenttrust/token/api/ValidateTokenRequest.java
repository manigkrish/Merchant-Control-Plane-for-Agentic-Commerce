package com.agenttrust.token.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Hot-path validation request called by decision-service.
 *
 * Tenant context is NOT accepted here; it comes from internal header X-Tenant-Id.
 * Raw token is provided so token-service can hash it and perform lookup without exposing token material elsewhere.
 */
public record ValidateTokenRequest(
        @NotBlank
        @Pattern(regexp = "^PURCHASE$", message = "action must be PURCHASE for Sprint 4")
        String action,

        @NotNull
        @Min(0)
        Long amount,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be ISO 4217 (3 uppercase letters)")
        String currency,

        /**
         * The raw scoped token value as received in X-Scoped-Token header (e.g., stkn_...).
         * This must never be logged or persisted.
         */
        @NotBlank
        String rawToken
) {
}
