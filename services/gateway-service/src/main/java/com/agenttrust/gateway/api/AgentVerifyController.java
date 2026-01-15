package com.agenttrust.gateway.api;

import com.agenttrust.gateway.attestation.client.AttestationClientDtos;
import com.agenttrust.gateway.attestation.client.AttestationServiceClient;
import com.agenttrust.gateway.attestation.client.AttestationServiceClient.AttestationServiceClientException;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import com.agenttrust.gateway.tenancy.HostTenantDeriver;

@RestController
public class AgentVerifyController {

  private static final MediaType APPLICATION_PROBLEM_JSON =
      MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

  private final HostTenantDeriver tenantDeriver;
  private final AttestationServiceClient attestationClient;

  public AgentVerifyController(HostTenantDeriver tenantDeriver, AttestationServiceClient attestationClient) {
    this.tenantDeriver = tenantDeriver;
    this.attestationClient = attestationClient;
  }

  @PostMapping(path = "/v1/agent/verify", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> verify(HttpServletRequest request) {
    String signatureInput = headerRequired(request, "Signature-Input");
    String signature = headerRequired(request, "Signature");

    String tenantId = tenantDeriver.deriveTenantId(request);

    String method = request.getMethod();
    String authority = headerRequired(request, "Host");
    String path = request.getRequestURI();

    AttestationClientDtos.VerifyRequest verifyRequest = new AttestationClientDtos.VerifyRequest(
        method,
        authority,
        path,
        tenantId,
        signatureInput,
        signature
    );

    try {
      AttestationClientDtos.VerifyResponse resp = attestationClient.verify(verifyRequest, request);
      if (resp == null || !resp.verified()) {
        // Defensive: attestation-service should not return verified=false for Sprint 3.
        return ResponseEntity.status(502).body(Map.of("verified", false));
      }
      return ResponseEntity.ok(Map.of("verified", true));
    } catch (AttestationServiceClientException ex) {
      MediaType ct = ex.contentType();
      if (ct != null && ct.isCompatibleWith(APPLICATION_PROBLEM_JSON)) {
        return ResponseEntity
            .status(ex.status())
            .contentType(APPLICATION_PROBLEM_JSON)
            .body(ex.body());
      }
      return ResponseEntity.status(ex.status()).body(ex.body());
    }
  }

  private static String headerRequired(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required header: " + name);
    }
    return value;
  }
}
