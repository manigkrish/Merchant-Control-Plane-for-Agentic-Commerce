package com.agenttrust.decision.api;

import com.agenttrust.decision.clients.AttestationClient;
import com.agenttrust.decision.clients.DownstreamProblemException;
import com.agenttrust.decision.clients.TokenClient;
import com.agenttrust.decision.digest.ContentDigestSupport;
import com.agenttrust.decision.idempotency.IdempotencyRecord;
import com.agenttrust.decision.idempotency.IdempotencyStore;
import com.agenttrust.decision.idempotency.RequestHashComputer;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static com.agenttrust.decision.api.DecisionResponse.Decision;

@Validated
@RestController
@RequestMapping(path = "/internal/v1/decisions")
public class DecisionController {

    private static final MediaType PROBLEM_JSON =
            MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

    private final IdempotencyStore idempotencyStore;
    private final RequestHashComputer requestHashComputer;
    private final AttestationClient attestationClient;
    private final TokenClient tokenClient;
    private final ObjectMapper objectMapper;

    public DecisionController(
            IdempotencyStore idempotencyStore,
            RequestHashComputer requestHashComputer,
            AttestationClient attestationClient,
            TokenClient tokenClient,
            ObjectMapper objectMapper
    ) {
        this.idempotencyStore = idempotencyStore;
        this.requestHashComputer = requestHashComputer;
        this.attestationClient = attestationClient;
        this.tokenClient = tokenClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> decide(
            @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent,
            @RequestHeader(value = "tracestate", required = false) String tracestate,
            @RequestHeader(value = "Host", required = false) String hostHeader,
            @Valid @RequestBody DecisionRequest request
    ) {
        byte[] bodyBytes = decodeBase64(request.bodyBytesBase64());
        ContentDigestSupport.VerifiedContentDigest verifiedDigest =
                ContentDigestSupport.verifySha256(request.contentDigest(), bodyBytes);

        String requestHash = requestHashComputer.compute(tenantId, request.rawScopedToken(), verifiedDigest);

        Optional<IdempotencyRecord> cached =
                idempotencyStore.getIfPresentOrThrowConflict(tenantId, idempotencyKey, requestHash);

        if (cached.isPresent()) {
            IdempotencyRecord r = cached.get();
            return ResponseEntity.status(r.httpStatus())
                    .contentType(MediaType.valueOf(r.contentType()))
                    .body(r.bodyBytes());
        }

        String authority = (hostHeader != null && !hostHeader.isBlank()) ? hostHeader : "unknown";

        attestationClient.verifyOrThrow(
                tenantId,
                authority,
                request.signatureInput(),
                request.signature(),
                correlationId,
                traceparent,
                tracestate
        );

        var tokenResp = tokenClient.validateOrThrow(
                tenantId,
                request.action(),
                request.amount(),
                request.currency(),
                request.rawScopedToken(),
                correlationId,
                traceparent,
                tracestate
        );

        // B) Token invalid/expired/revoked => 401 Problem Details
        if (!tokenResp.valid()) {
            String reason = (tokenResp.reasonCode() == null || tokenResp.reasonCode().isBlank())
                    ? "TOKEN_INVALID"
                    : tokenResp.reasonCode();

            byte[] out = unauthorizedProblemDetailsBytes(
                    "Scoped token is invalid",
                    "SCOPED_TOKEN_INVALID",
                    reason
            );

            IdempotencyRecord record = IdempotencyRecord.of(
                    requestHash,
                    401,
                    ProblemMediaTypes.APPLICATION_PROBLEM_JSON,
                    out
            );
            idempotencyStore.putIfAbsentOrThrowConflict(tenantId, idempotencyKey, record);

            return ResponseEntity.status(401).contentType(PROBLEM_JSON).body(out);
        }

        // Deterministic rule: attestation OK + token OK => ALLOW.
        DecisionResponse allow = DecisionResponse.allow(UUID.randomUUID(), tokenResp.tokenId());
        byte[] out = writeJsonBytes(allow);

        IdempotencyRecord record = IdempotencyRecord.of(requestHash, 200, MediaType.APPLICATION_JSON_VALUE, out);
        idempotencyStore.putIfAbsentOrThrowConflict(tenantId, idempotencyKey, record);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(out);
    }

    @ExceptionHandler(DownstreamProblemException.class)
    public ResponseEntity<byte[]> handleDownstreamProblem(DownstreamProblemException ex) {
        return ResponseEntity.status(ex.httpStatus())
                .contentType(MediaType.valueOf(ex.contentType()))
                .body(ex.bodyBytes());
    }

    @ExceptionHandler(ContentDigestSupport.ContentDigestException.class)
    public ResponseEntity<byte[]> handleDigest(ContentDigestSupport.ContentDigestException ex) {
        String json = """
                {"type":"about:blank","title":"Bad Request","status":400,"detail":"%s","errorCode":"%s"}
                """.formatted(escape(ex.getMessage()), escape(ex.errorCode()));
        return ResponseEntity.badRequest()
                .contentType(PROBLEM_JSON)
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] decodeBase64(String b64) {
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ex) {
            return Base64.getUrlDecoder().decode(b64);
        }
    }

    private byte[] writeJsonBytes(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }
    }

    private byte[] unauthorizedProblemDetailsBytes(String detail, String errorCode, String reasonCode) {
        String json = """
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"%s","errorCode":"%s","reasonCode":"%s"}
                """.formatted(escape(detail), escape(errorCode), escape(reasonCode));
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
