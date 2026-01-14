package com.agenttrust.attestation.keys;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Optional;

/**
 * Resolves a public key for verifying message signatures.
 *
 * The resolver enforces tenant scoping: a keyId must only be usable for the tenant it is registered to.
 * Callers should map {@link Failure#code()} to stable RFC 9457 errorCode values.
 */
public interface PublicKeyResolver {

  ResolveResult resolve(String tenantId, String keyId);

  record ResolveResult(Optional<KeyMaterial> key, Optional<Failure> failure) {

    public static ResolveResult success(KeyMaterial key) {
      return new ResolveResult(Optional.of(key), Optional.empty());
    }

    public static ResolveResult failure(Failure failure) {
      return new ResolveResult(Optional.empty(), Optional.of(failure));
    }

    public boolean isSuccess() {
      return key.isPresent();
    }
  }

  record KeyMaterial(
      String tenantId,
      String keyId,
      PublicKey publicKey,
      Instant notAfter
  ) { }

  record Failure(
      FailureCode code,
      String message
  ) { }

  enum FailureCode {
    KEY_NOT_FOUND,
    KEY_REVOKED,
    KEY_EXPIRED,
    TENANT_KEY_MISMATCH,
    INVALID_KEY_MATERIAL,
    INVALID_INPUT
  }
}
