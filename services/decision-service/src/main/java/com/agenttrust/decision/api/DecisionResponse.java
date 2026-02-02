package com.agenttrust.decision.api;

import java.util.UUID;

/**
 * Internal decision response returned to gateway.
 *
 * Gateway translates this into the public API response shape.
 */
public record DecisionResponse(
        Decision decision,
        UUID decisionId,
        UUID tokenId,
        String reasonCode
) {
    public enum Decision {
        ALLOW,
        CHALLENGE,
        DENY
    }

    public static DecisionResponse allow(UUID decisionId, UUID tokenId) {
        return new DecisionResponse(Decision.ALLOW, decisionId, tokenId, null);
    }

    public static DecisionResponse deny(UUID decisionId, UUID tokenId, String reasonCode) {
        return new DecisionResponse(Decision.DENY, decisionId, tokenId, reasonCode);
    }
}
