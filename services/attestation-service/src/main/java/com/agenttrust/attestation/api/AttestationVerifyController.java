package com.agenttrust.attestation.api;

import com.agenttrust.attestation.verify.AttestationVerifierService;
import com.agenttrust.attestation.verify.AttestationVerifierService.FailureCode;
import com.agenttrust.attestation.verify.AttestationVerifierService.VerifyOutcome;
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

  private static final URI TYPE_ATTESTATION_FAILED =
      URI.create("https://agenttrust.dev/problems/attestation-failed");

  private final AttestationVerifierService verifier;

  public AttestationVerifyController(AttestationVerifierService verifier) {
    this.verifier = verifier;
  }

  @PostMapping(
      value = "/verify",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = {MediaType.APPLICATION_JSON_VALUE, ProblemMediaTypes.APPLICATION_PROBLEM_JSON}
  )
  public ResponseEntity<?> verify(@Valid @RequestBody AttestationDtos.VerifyRequest body,
                                  HttpServletRequest request) {

    request.setAttribute("agenttrust.tenantId", body.tenantId());

    VerifyOutcome outcome = verifier.verify(body);
    if (outcome.verified()) {
      return ResponseEntity.ok(new AttestationDtos.VerifyResponse(true));
    }

    FailureCode code = outcome.failure().code();
    HttpStatus status = mapHttpStatus(code);
    String errorCode = mapErrorCode(code);

    ProblemDetails problem = new ProblemDetails(
        TYPE_ATTESTATION_FAILED,
        "Attestation verification failed",
        status.value(),
        outcome.failure().message(),
        toInstanceUri(request),
        errorCode,
        attr(request, "agenttrust.traceId"),
        attr(request, "agenttrust.requestId"),
        body.tenantId()
    );

    return ResponseEntity
        .status(status)
        .contentType(PROBLEM_JSON)
        .body(problem);
  }

  private static HttpStatus mapHttpStatus(FailureCode code) {
    return switch (code) {
      case ATTESTATION_MISSING_OR_INVALID, ATTESTATION_MISSING_COMPONENT -> HttpStatus.BAD_REQUEST;
      case ATTESTATION_REPLAY_DETECTED -> HttpStatus.CONFLICT;
      case ATTESTATION_REPLAY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case ATTESTATION_INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
      default -> HttpStatus.UNAUTHORIZED;
    };
  }

  private static String mapErrorCode(FailureCode code) {
    return switch (code) {
      case ATTESTATION_MISSING_OR_INVALID -> "ATTESTATION_INVALID_REQUEST";
      case ATTESTATION_MISSING_COMPONENT -> "ATTESTATION_MISSING_COMPONENT";
      case ATTESTATION_TIMESTAMP_INVALID -> "ATTESTATION_TIMESTAMP_INVALID";
      case ATTESTATION_KEY_UNAVAILABLE -> "ATTESTATION_KEY_UNAVAILABLE";
      case ATTESTATION_TENANT_KEY_MISMATCH -> "ATTESTATION_TENANT_KEY_MISMATCH";
      case ATTESTATION_INVALID_SIGNATURE -> "ATTESTATION_INVALID_SIGNATURE";
      case ATTESTATION_REPLAY_DETECTED -> "ATTESTATION_REPLAY_DETECTED";
      case ATTESTATION_REPLAY_UNAVAILABLE -> "ATTESTATION_REPLAY_UNAVAILABLE";
      case ATTESTATION_INTERNAL_ERROR -> "ATTESTATION_INTERNAL_ERROR";
    };
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
