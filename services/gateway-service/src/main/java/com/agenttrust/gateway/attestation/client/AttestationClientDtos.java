package com.agenttrust.gateway.attestation.client;

import jakarta.validation.constraints.NotBlank;

public final class AttestationClientDtos {

  private AttestationClientDtos() {}

  public record VerifyRequest(
      @NotBlank String method,
      @NotBlank String authority,
      @NotBlank String path,
      @NotBlank String tenantId,
      @NotBlank String signatureInput,
      @NotBlank String signature
  ) {}

  public record VerifyResponse(
      boolean verified
  ) {}
}
