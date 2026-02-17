package com.agenttrust.decision.clients;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Internal client DTOs used by decision-service when calling other internal services.
 *
 * Note: These are intentionally "thin" wire DTOs. They are not domain objects.
 */
public final class DecisionClientsDtos {

    private DecisionClientsDtos() {
    }

    /**
     * Matches attestation-service POST /v1/attestations/verify request shape.
     *
     * IMPORTANT:
     * - contentDigest is optional unless requireContentDigestCovered=true.
     * - For the external evaluate flow, decision-service will set requireContentDigestCovered=true
     *   and pass the Content-Digest header value through.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttestationVerifyRequest(
            String method,
            String authority,
            String path,
            String tenantId,
            String contentDigest,
            boolean requireContentDigestCovered,
            @JsonProperty("signatureInput") String signatureInput,
            @JsonProperty("signature") String signature
    ) {
    }

    /** Matches attestation-service verify response: {"verified":true} */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttestationVerifyResponse(
            boolean verified
    ) {
    }

    /** Matches token-service POST /internal/v1/tokens/validate request shape. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValidateTokenRequest(
            String action,
            long amount,
            String currency,
            @JsonProperty("rawToken") String rawToken
    ) {
    }

    /** Matches token-service validate response shape. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValidateTokenResponse(
            boolean valid,
            UUID tokenId,
            String reasonCode
    ) {
    }
}
