package com.agenttrust.token.api;

import com.agenttrust.token.tokens.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal-only endpoints used by decision-service and local/test utilities.
 *
 * Tenant identity is never accepted in request bodies.
 * Gateway (or internal caller) must provide tenant context via X-Tenant-Id.
 */
@Validated
@RestController
@RequestMapping(path = "/internal/v1/tokens", produces = MediaType.APPLICATION_JSON_VALUE)
class TokenValidationController {

    private final TokenService tokenService;

    TokenValidationController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Hot-path validation endpoint called by decision-service.
     *
     * Returns 200 with {valid: true/false}. Invalid tokens are represented as valid=false + reasonCode
     * (decision-service translates this into RFC 9457 Problem Details for the public API).
     */
    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ValidateTokenResponse validate(
            @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent,
            @Valid @RequestBody ValidateTokenRequest request
    ) {
        return tokenService.validate(tenantId, request, correlationId, traceparent);
    }

    /**
     * Internal revoke endpoint (useful for tests and local manual verification).
     */
    @PostMapping(path = "/revoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> revoke(
            @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
            @Valid @RequestBody RevokeTokenRequest request
    ) {
        tokenService.revoke(tenantId, request.tokenId(), request.reasonCode());
        return ResponseEntity.noContent().build();
    }

    record RevokeTokenRequest(
            @NotNull UUID tokenId,
            String reasonCode
    ) {
    }
}

/**
 * Profile-gated token issuance endpoint for local/test only.
 *
 * Raw stkn_* is returned only at issue time. It must never be persisted or logged.
 */
@Validated
@Profile({"local", "test"})
@RestController
@RequestMapping(path = "/internal/v1/tokens", produces = MediaType.APPLICATION_JSON_VALUE)
class TokenIssueController {

    private final TokenService tokenService;

    TokenIssueController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping(path = "/issue", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IssueTokenResponse issue(
            @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
            @Valid @RequestBody IssueTokenRequest request
    ) {
        return tokenService.issue(tenantId, request);
    }
}
