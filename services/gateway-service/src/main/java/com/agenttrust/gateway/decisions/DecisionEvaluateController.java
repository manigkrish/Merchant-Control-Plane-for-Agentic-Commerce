package com.agenttrust.gateway.decisions;

import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

@Validated
@RestController
@RequestMapping(path = "/v1/agent/decisions")
public class DecisionEvaluateController {

    private static final Logger log = LoggerFactory.getLogger(DecisionEvaluateController.class);

    private static final MediaType PROBLEM_JSON =
            MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

    private final RestClient decisionRestClient;
    private final ObjectMapper objectMapper;
    private final com.agenttrust.gateway.tenancy.HostTenantDeriver hostTenantDeriver;

    public DecisionEvaluateController(
            RestClient decisionRestClient,
            ObjectMapper objectMapper,
            com.agenttrust.gateway.tenancy.HostTenantDeriver hostTenantDeriver
    ) {
        this.decisionRestClient = Objects.requireNonNull(decisionRestClient, "decisionRestClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.hostTenantDeriver = Objects.requireNonNull(hostTenantDeriver, "hostTenantDeriver");
    }

    @PostMapping(
            path = "/evaluate",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<byte[]> evaluate(
            @RequestHeader("Signature-Input") @NotBlank String signatureInput,
            @RequestHeader("Signature") @NotBlank String signature,
            @RequestHeader("Content-Digest") @NotBlank String contentDigest,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestHeader("X-Scoped-Token") @NotBlank String scopedToken,

            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent,
            @RequestHeader(value = "tracestate", required = false) String tracestate,

            // External authority for RFC 9421 is the public Host header seen by gateway.
            @RequestHeader(value = "Host", required = false) String hostHeader,

            HttpServletRequest servletRequest,
            @RequestBody byte[] bodyBytes
    ) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return badRequest("Request body must not be empty", "INVALID_REQUEST_BODY");
        }

        // IMPORTANT:
        // Tenant derivation must use the same gateway logic already used elsewhere (HostTenantDeriver),
        // and the existing API expects HttpServletRequest.
        String tenantId = hostTenantDeriver.deriveTenantId(servletRequest);

        String authority = firstNonBlank(hostHeader, servletRequest.getHeader("Host"), "unknown");

        EvaluateRequest parsed;
        try {
            parsed = objectMapper.readValue(bodyBytes, EvaluateRequest.class);
        } catch (Exception e) {
            return badRequest("Invalid JSON body", "INVALID_REQUEST_BODY");
        }

        if (parsed.action() == null || parsed.action().isBlank()) {
            return badRequest("action must not be blank", "INVALID_REQUEST_BODY");
        }
        if (parsed.currency() == null || parsed.currency().isBlank()) {
            return badRequest("currency must not be blank", "INVALID_REQUEST_BODY");
        }

        String bodyB64 = Base64.getEncoder().encodeToString(bodyBytes);

        DecisionServiceRequest dsReq = new DecisionServiceRequest(
                parsed.action(),
                parsed.amount(),
                parsed.currency(),
                scopedToken,
                contentDigest,
                bodyB64,
                signatureInput,
                signature
        );

        try {
            byte[] out = decisionRestClient.post()
                    .uri("/internal/v1/decisions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, PROBLEM_JSON)
                    .headers(h -> {
                        h.set("X-Tenant-Id", tenantId);
                        h.set("Idempotency-Key", idempotencyKey);
                        h.set("X-External-Authority", authority);

                        putIfPresent(h, "X-Correlation-Id", correlationId);
                        putIfPresent(h, "traceparent", traceparent);
                        putIfPresent(h, "tracestate", tracestate);
                    })
                    .body(dsReq)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        int httpStatus = res.getStatusCode().value();
                        String contentType = (res.getHeaders().getContentType() != null)
                                ? res.getHeaders().getContentType().toString()
                                : ProblemMediaTypes.APPLICATION_PROBLEM_JSON;

                        byte[] bytes;
                        try {
                            bytes = (res.getBody() == null) ? new byte[0] : res.getBody().readAllBytes();
                        } catch (IOException ioe) {
                            bytes = new byte[0];
                        }
                        throw new DownstreamProblemException(httpStatus, contentType, bytes);
                    })
                    .body(byte[].class);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(out);

        } catch (DownstreamProblemException ex) {
            // Never log raw token; at most log tenantId + status.
            log.info("Decision evaluate failed tenantId={} status={}", tenantId, ex.httpStatus());
            return ResponseEntity.status(ex.httpStatus())
                    .contentType(MediaType.valueOf(ex.contentType()))
                    .body(ex.bodyBytes());
        }
    }

    private static ResponseEntity<byte[]> badRequest(String detail, String errorCode) {
        String json = """
                {"type":"about:blank","title":"Bad Request","status":400,"detail":"%s","errorCode":"%s"}
                """.formatted(escape(detail), escape(errorCode));
        return ResponseEntity.badRequest()
                .contentType(PROBLEM_JSON)
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void putIfPresent(org.springframework.http.HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return fallback;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record EvaluateRequest(String action, long amount, String currency) {}

    record DecisionServiceRequest(
            String action,
            long amount,
            String currency,
            String rawScopedToken,
            String contentDigest,
            String bodyBytesBase64,
            String signatureInput,
            String signature
    ) {}
}

final class DownstreamProblemException extends RuntimeException {

    private final int httpStatus;
    private final String contentType;
    private final byte[] bodyBytes;

    DownstreamProblemException(int httpStatus, String contentType, byte[] bodyBytes) {
        super("Downstream error " + httpStatus);
        this.httpStatus = httpStatus;
        this.contentType = (contentType == null || contentType.isBlank())
                ? ProblemMediaTypes.APPLICATION_PROBLEM_JSON
                : contentType;
        this.bodyBytes = (bodyBytes == null) ? new byte[0] : bodyBytes;
    }

    int httpStatus() {
        return httpStatus;
    }

    String contentType() {
        return contentType;
    }

    byte[] bodyBytes() {
        return bodyBytes;
    }
}
