package com.agenttrust.attestation.api;

import com.agenttrust.platform.web.problem.ProblemDetails;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/attestations")
public class AttestationVerifyController {

  private static final MediaType PROBLEM_JSON =
      MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

  private static final URI TYPE_NOT_IMPLEMENTED =
      URI.create("https://agenttrust.dev/problems/attestation-not-implemented");

  @PostMapping(
      value = "/verify",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = {MediaType.APPLICATION_JSON_VALUE, ProblemMediaTypes.APPLICATION_PROBLEM_JSON}
  )
  public ResponseEntity<?> verify(@Valid @RequestBody AttestationDtos.VerifyRequest body,
                                  HttpServletRequest request) {

    // Internal-only endpoint: tenantId is derived by gateway and passed in the internal request.
    // We attach it to request attributes so RFC 9457 responses can include tenantId consistently.
    request.setAttribute("agenttrust.tenantId", body.tenantId());

    ProblemDetails problem = new ProblemDetails(
        TYPE_NOT_IMPLEMENTED,
        "Attestation verification not implemented",
        HttpStatus.NOT_IMPLEMENTED.value(),
        "Verifier engine will be wired in subsequent Sprint 3 steps.",
        toInstanceUri(request),
        "ATTESTATION_NOT_IMPLEMENTED",
        attr(request, "agenttrust.traceId"),
        attr(request, "agenttrust.requestId"),
        body.tenantId()
    );

    return ResponseEntity
        .status(HttpStatus.NOT_IMPLEMENTED)
        .contentType(PROBLEM_JSON)
        .body(problem);
  }

  private static String attr(HttpServletRequest request, String key) {
    Object v = request.getAttribute(key);
    return (v instanceof String s && !s.isBlank()) ? s : null;
  }

  private static URI toInstanceUri(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null || path.isBlank()) {
      return URI.create("/");
    }
    try {
      return URI.create(path);
    } catch (IllegalArgumentException e) {
      return URI.create("/");
    }
  }
}
