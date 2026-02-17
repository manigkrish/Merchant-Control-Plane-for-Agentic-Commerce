package com.agenttrust.decision.clients;

import com.agenttrust.decision.config.IdempotencyKeySupport;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Objects;

import static com.agenttrust.decision.clients.DecisionClientsDtos.AttestationVerifyRequest;
import static com.agenttrust.decision.clients.DecisionClientsDtos.AttestationVerifyResponse;

public class AttestationClient {

    private static final Logger log = LoggerFactory.getLogger(AttestationClient.class);

    private static final MediaType PROBLEM_JSON =
            MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

    private final RestClient restClient;
    private final IdempotencyKeySupport keySupport;

    public AttestationClient(
            @Qualifier("attestationRestClient") RestClient restClient,
            IdempotencyKeySupport keySupport
    ) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.keySupport = Objects.requireNonNull(keySupport, "keySupport");
    }

    /**
     * Calls attestation-service to verify RFC 9421 signature for the external evaluate request.
     *
     * IMPORTANT:
     * - This is NOT part of requestHash (idempotency). It is always verified on first execution.
     * - On downstream Problem Details, we throw DownstreamProblemException to allow pass-through.
     */
    public void verifyOrThrow(
            String tenantId,
            String authority,
            String contentDigest,
            boolean requireContentDigestCovered,
            String signatureInput,
            String signature,
            String correlationId,
            String traceparent,
            String tracestate
    ) {
        String method = keySupport.requestIdentity().method();
        String path = keySupport.requestIdentity().path();

        AttestationVerifyRequest request = new AttestationVerifyRequest(
                method,
                requireNonBlank(authority, "authority"),
                path,
                requireNonBlank(tenantId, "tenantId"),
                contentDigest,
                requireContentDigestCovered,
                requireNonBlank(signatureInput, "Signature-Input"),
                requireNonBlank(signature, "Signature")
        );

        try {
            AttestationVerifyResponse resp = restClient.post()
                    .uri("/v1/attestations/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, PROBLEM_JSON)
                    .headers(h -> {
                        putIfPresent(h, "X-Correlation-Id", correlationId);
                        putIfPresent(h, "traceparent", traceparent);
                        putIfPresent(h, "tracestate", tracestate);
                    })
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        int httpStatus = res.getStatusCode().value();
                        String contentType = (res.getHeaders().getContentType() != null)
                                ? res.getHeaders().getContentType().toString()
                                : ProblemMediaTypes.APPLICATION_PROBLEM_JSON;

                        byte[] bodyBytes;
                        try {
                            bodyBytes = (res.getBody() == null) ? new byte[0] : res.getBody().readAllBytes();
                        } catch (IOException e) {
                            bodyBytes = new byte[0];
                        }

                        throw new DownstreamProblemException(httpStatus, contentType, bodyBytes);
                    })
                    .body(AttestationVerifyResponse.class);

            if (resp == null || !resp.verified()) {
                // Attestation-service should normally return Problem Details on failure.
                throw new IllegalStateException("Attestation verify returned verified=false without error response");
            }
        } catch (DownstreamProblemException ex) {
            // Never log signature content.
            log.info("Attestation verification failed tenantId={} status={}", tenantId, ex.httpStatus());
            throw ex;
        }
    }

    private static void putIfPresent(org.springframework.http.HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
