package com.agenttrust.decision.clients;

import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Objects;

import static com.agenttrust.decision.clients.DecisionClientsDtos.ValidateTokenRequest;
import static com.agenttrust.decision.clients.DecisionClientsDtos.ValidateTokenResponse;

public class TokenClient {

    private static final Logger log = LoggerFactory.getLogger(TokenClient.class);

    private static final MediaType PROBLEM_JSON =
            MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

    private final RestClient restClient;

    public TokenClient(@Qualifier("tokenRestClient") RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
    }

    public ValidateTokenResponse validateOrThrow(
            String tenantId,
            String action,
            long amount,
            String currency,
            String rawToken,
            String correlationId,
            String traceparent,
            String tracestate
    ) {
        ValidateTokenRequest req = new ValidateTokenRequest(
                requireNonBlank(action, "action"),
                amount,
                requireNonBlank(currency, "currency"),
                requireNonBlank(rawToken, "rawToken")
        );

        try {
            ValidateTokenResponse resp = restClient.post()
                    .uri("/internal/v1/tokens/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, PROBLEM_JSON)
                    .headers(h -> {
                        h.set("X-Tenant-Id", requireNonBlank(tenantId, "tenantId"));
                        putIfPresent(h, "X-Correlation-Id", correlationId);
                        putIfPresent(h, "traceparent", traceparent);
                        putIfPresent(h, "tracestate", tracestate);
                    })
                    .body(req)
                    .retrieve()
                    .onStatus(status -> status.isError(), (r, res) -> {
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
                    .body(ValidateTokenResponse.class);

            if (resp == null) {
                throw new IllegalStateException("Token validate returned null body");
            }
            return resp;
        } catch (DownstreamProblemException ex) {
            // Never log raw token.
            log.info("Token validation call failed tenantId={} status={}", tenantId, ex.httpStatus());
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
