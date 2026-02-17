package com.agenttrust.attestation.api;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

/**
 * Internal DTOs for attestation verification.
 *
 * - Supports bodyless verification (default)
 * - Optionally supports requiring Content-Digest coverage for body-present calls
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

      /**
       * Raw Content-Digest header value (e.g. "sha-256=:...:").
       * Optional unless requireContentDigestCovered=true.
       */
      @Size(max = 512)
      String contentDigest,

      /**
       * When true, attestation verification fails if:
       * - contentDigest is missing/blank
       * - Content-Digest algorithm is unsupported (Sprint 4: sha-256 only)
       * - "content-digest" is not present in the covered components
       */
      boolean requireContentDigestCovered,

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
