package com.agenttrust.attestation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Internal DTOs for attestation verification.
 *
 * - Bodyless verification (no Content-Digest)
 * - Minimal safe payload (no full request body forwarding)
 * - tenantId is derived by gateway (trusted internal propagation)
 */
public final class AttestationDtos {

  private AttestationDtos() {
    // utility holder
  }

  public record VerifyRequest(
      @NotBlank
      @Size(max = 32)
      String method,

      @NotBlank
      @Size(max = 255)
      String authority,

      @NotBlank
      @Size(max = 2048)
      String path,

      @NotBlank
      @Size(max = 128)
      String tenantId,

      @NotBlank
      @Size(max = 8192)
      String signatureInput,

      @NotBlank
      @Size(max = 8192)
      String signature
  ) { }

  public record VerifyResponse(
      boolean verified
  ) { }
}
