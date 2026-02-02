package com.agenttrust.decision.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Internal request payload for decision evaluation.
 *
 * Tenant context must NOT be accepted in this body.
 * Gateway provides tenantId via internal header X-Tenant-Id.
 */
public record DecisionRequest(
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
         * Raw scoped token value from X-Scoped-Token. Must never be logged.
         */
        @NotBlank
        String rawScopedToken,

        /**
         * Content-Digest header (must be sha-256 for Sprint 4). Decision-service will verify vs bodyBytesBase64.
         */
        @NotBlank
        String contentDigest,

        /**
         * Base64-encoded request body bytes (application/json) for digest verification and stable body identity.
         */
        @NotBlank
        String bodyBytesBase64,

        /**
         * RFC 9421 headers forwarded for attestation verification.
         * Not used for requestHash (by design).
         */
        @NotBlank
        String signatureInput,

        @NotBlank
        String signature
) {
}
