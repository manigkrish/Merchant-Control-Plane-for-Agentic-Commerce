package com.agenttrust.gateway.attestation.client;

import com.agenttrust.gateway.attestation.config.AttestationClientProperties;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public final class AttestationServiceClient {

  private static final MediaType APPLICATION_PROBLEM_JSON =
      MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

  private final RestClient restClient;

  public AttestationServiceClient(RestClient.Builder restClientBuilder, AttestationClientProperties props) {
    this.restClient = restClientBuilder
        .baseUrl(props.getBaseUrl())
        .build();
  }

  public AttestationClientDtos.VerifyResponse verify(
      AttestationClientDtos.VerifyRequest request,
      HttpServletRequest incomingRequest
  ) {
    try {
      return restClient
          .post()
          .uri("/v1/attestations/verify")
          .headers(h -> propagateTraceHeaders(incomingRequest, h))
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON, APPLICATION_PROBLEM_JSON)
          .body(request)
          .retrieve()
          .body(AttestationClientDtos.VerifyResponse.class);
    } catch (HttpStatusCodeException ex) {
      MediaType contentType =
          ex.getResponseHeaders() != null ? ex.getResponseHeaders().getContentType() : null;

      throw new AttestationServiceClientException(
          ex.getStatusCode().value(),
          contentType,
          ex.getResponseBodyAsString()
      );
    }
  }

  private static void propagateTraceHeaders(HttpServletRequest incoming, HttpHeaders outgoing) {
    copyIfPresent(incoming, outgoing, "X-Correlation-Id");
    copyIfPresent(incoming, outgoing, "traceparent");
    copyIfPresent(incoming, outgoing, "tracestate");
  }

  private static void copyIfPresent(HttpServletRequest incoming, HttpHeaders outgoing, String headerName) {
    Optional.ofNullable(incoming.getHeader(headerName))
        .filter(v -> !v.isBlank())
        .ifPresent(v -> outgoing.set(headerName, v));
  }

  public static final class AttestationServiceClientException extends RuntimeException {
    private final int status;
    private final MediaType contentType;
    private final String body;

    public AttestationServiceClientException(int status, MediaType contentType, String body) {
      super("attestation-service call failed with status=" + status);
      this.status = status;
      this.contentType = contentType;
      this.body = body;
    }

    public int status() {
      return status;
    }

    public MediaType contentType() {
      return contentType;
    }

    public String body() {
      return body;
    }
  }
}
