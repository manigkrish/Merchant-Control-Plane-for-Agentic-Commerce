package com.agenttrust.attestation.verify;

import com.agenttrust.attestation.api.AttestationDtos;
import com.agenttrust.attestation.config.AttestationProperties;
import com.agenttrust.attestation.crypto.Ed25519SignatureVerifier;
import com.agenttrust.attestation.keys.PublicKeyResolver;
import com.agenttrust.attestation.keys.PublicKeyResolver.ResolveResult;
import com.agenttrust.attestation.replay.ReplayProtectionService;
import com.agenttrust.attestation.rfc9421.Rfc9421SignatureBaseBuilder;
import com.agenttrust.attestation.rfc9421.Rfc9421SignatureHeaderParser;
import com.agenttrust.attestation.rfc9421.Rfc9421SignatureInput;
import com.agenttrust.attestation.rfc9421.Rfc9421SignatureInputParser;
import com.agenttrust.attestation.rfc9421.SignatureLabelValidator;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AttestationVerifierService {

  private final AttestationProperties props;
  private final PublicKeyResolver publicKeyResolver;
  private final ReplayProtectionService replayProtectionService;

  private final Rfc9421SignatureInputParser signatureInputParser = new Rfc9421SignatureInputParser();
  private final Rfc9421SignatureHeaderParser signatureHeaderParser = new Rfc9421SignatureHeaderParser();
  private final SignatureLabelValidator labelValidator = new SignatureLabelValidator();
  private final Rfc9421SignatureBaseBuilder signatureBaseBuilder = new Rfc9421SignatureBaseBuilder();
  private final Ed25519SignatureVerifier ed25519Verifier = new Ed25519SignatureVerifier();

  public AttestationVerifierService(AttestationProperties props,
                                    PublicKeyResolver publicKeyResolver,
                                    ReplayProtectionService replayProtectionService) {
    this.props = Objects.requireNonNull(props, "props");
    this.publicKeyResolver = Objects.requireNonNull(publicKeyResolver, "publicKeyResolver");
    this.replayProtectionService = Objects.requireNonNull(replayProtectionService, "replayProtectionService");
  }

  public VerifyOutcome verify(AttestationDtos.VerifyRequest request) {
    if (request == null) {
      return VerifyOutcome.failure(Failure.of(FailureCode.ATTESTATION_MISSING_OR_INVALID, "request is required"));
    }

    try {
      Rfc9421SignatureInputParser.Parsed parsedInput = signatureInputParser.parseSingle(request.signatureInput());
      Rfc9421SignatureHeaderParser.Parsed parsedSig = signatureHeaderParser.parseSingle(request.signature());

      labelValidator.assertSameLabel(parsedInput.label(), parsedSig.label());

      Rfc9421SignatureInput sigInput = parsedInput.input();
      Failure profileFailure = enforceProfile(sigInput);
      if (profileFailure != null) {
        return VerifyOutcome.failure(profileFailure);
      }

      Failure timeFailure = enforceCreatedExpires(sigInput.params().created(), sigInput.params().expires());
      if (timeFailure != null) {
        return VerifyOutcome.failure(timeFailure);
      }

      ResolveResult resolved = publicKeyResolver.resolve(request.tenantId(), sigInput.params().keyId());
      if (!resolved.isSuccess()) {
        Failure failure = mapKeyFailure(resolved);
        return VerifyOutcome.failure(failure);
      }

      String signatureBase = signatureBaseBuilder.build(
          request.authority(),
          request.path(),
          sigInput.coveredComponents(),
          sigInput.params()
      );

      boolean verified = ed25519Verifier.verify(resolved.key().orElseThrow().publicKey(), signatureBase, parsedSig.signatureBytes());
      if (!verified) {
        return VerifyOutcome.failure(Failure.of(FailureCode.ATTESTATION_INVALID_SIGNATURE, "signature verification failed"));
      }

      if (props.getReplay().isEnabled()) {
        int ttlSeconds = computeReplayTtlSeconds(sigInput.params().expires());
        ReplayProtectionService.Result replayResult =
            replayProtectionService.recordNonce(request.tenantId(), sigInput.params().keyId(), sigInput.params().nonce(), ttlSeconds);

        if (replayResult == ReplayProtectionService.Result.REPLAY_DETECTED) {
          return VerifyOutcome.failure(Failure.of(FailureCode.ATTESTATION_REPLAY_DETECTED, "nonce replay detected"));
        }
        if (replayResult == ReplayProtectionService.Result.UNAVAILABLE) {
          return VerifyOutcome.failure(Failure.of(FailureCode.ATTESTATION_REPLAY_UNAVAILABLE, "replay protection unavailable"));
        }
        if (replayResult == ReplayProtectionService.Result.INVALID_INPUT) {
          return VerifyOutcome.failure(Failure.of(FailureCode.ATTESTATION_MISSING_OR_INVALID, "invalid replay inputs"));
        }
      }

      return VerifyOutcome.verified(
          request.tenantId(),
          sigInput.params().keyId(),
          sigInput.params().nonce(),
          normalizeAlg(sigInput.params().alg()),
          sigInput.params().created(),
          sigInput.params().expires()
      );
    } catch (IllegalArgumentException ex) {
      return VerifyOutcome.failure(Failure.of(FailureCode.ATTESTATION_MISSING_OR_INVALID, ex.getMessage()));
    } catch (Exception ex) {
      // Fail closed for unexpected errors.
      return VerifyOutcome.failure(Failure.of(FailureCode.ATTESTATION_INTERNAL_ERROR, "internal attestation error"));
    }
  }

  private Failure enforceProfile(Rfc9421SignatureInput sigInput) {
    Set<String> covered = new HashSet<>();
    for (String c : sigInput.coveredComponents()) {
      covered.add(c.toLowerCase(Locale.ROOT).trim());
    }

    for (String required : props.getProfile().getRequiredCoveredComponents()) {
      if (required == null) continue;
      String r = required.toLowerCase(Locale.ROOT).trim();
      if (!r.isEmpty() && !covered.contains(r)) {
        return Failure.of(FailureCode.ATTESTATION_MISSING_COMPONENT, "missing required covered component: " + required);
      }
    }

    String alg = normalizeAlg(sigInput.params().alg());
    if (props.getProfile().getAllowedAlgorithms() != null && !props.getProfile().getAllowedAlgorithms().isEmpty()) {
      boolean allowed = props.getProfile().getAllowedAlgorithms().stream()
          .filter(a -> a != null && !a.isBlank())
          .map(a -> a.trim().toLowerCase(Locale.ROOT))
          .anyMatch(a -> a.equals(alg));

      if (!allowed) {
        return Failure.of(FailureCode.ATTESTATION_MISSING_OR_INVALID, "unsupported algorithm: " + sigInput.params().alg());
      }
    }

    return null;
  }

  private Failure enforceCreatedExpires(long created, long expires) {
    int maxWindow = props.getProfile().getMaxWindowSeconds();
    if (maxWindow <= 0) {
      maxWindow = 480;
    }

    if (expires <= created) {
      return Failure.of(FailureCode.ATTESTATION_TIMESTAMP_INVALID, "expires must be greater than created");
    }

    long windowSeconds = expires - created;
    if (windowSeconds > maxWindow) {
      return Failure.of(FailureCode.ATTESTATION_TIMESTAMP_INVALID, "created/expires window exceeds maximum");
    }

    long now = Instant.now().getEpochSecond();
    if (created > now) {
      return Failure.of(FailureCode.ATTESTATION_TIMESTAMP_INVALID, "created is in the future");
    }
    if (expires < now) {
      return Failure.of(FailureCode.ATTESTATION_TIMESTAMP_INVALID, "signature is expired");
    }

    return null;
  }

  private int computeReplayTtlSeconds(long expiresEpochSeconds) {
    int defaultTtl = props.getReplay().getDefaultTtlSeconds();
    if (defaultTtl <= 0) {
      defaultTtl = 480;
    }

    long now = Instant.now().getEpochSecond();
    long remaining = expiresEpochSeconds - now;

    if (remaining <= 0) {
      return 1;
    }

    long ttl = Math.min(defaultTtl, remaining);
    if (ttl > Integer.MAX_VALUE) {
      return defaultTtl;
    }
    return (int) ttl;
  }

  private Failure mapKeyFailure(ResolveResult resolved) {
    if (resolved == null || resolved.failure().isEmpty()) {
      return Failure.of(FailureCode.ATTESTATION_KEY_UNAVAILABLE, "key unavailable");
    }

    PublicKeyResolver.Failure f = resolved.failure().orElseThrow();
    return switch (f.code()) {
      case KEY_NOT_FOUND -> Failure.of(FailureCode.ATTESTATION_KEY_UNAVAILABLE, "key not found");
      case KEY_REVOKED -> Failure.of(FailureCode.ATTESTATION_KEY_UNAVAILABLE, "key revoked");
      case KEY_EXPIRED -> Failure.of(FailureCode.ATTESTATION_KEY_UNAVAILABLE, "key expired");
      case TENANT_KEY_MISMATCH -> Failure.of(FailureCode.ATTESTATION_TENANT_KEY_MISMATCH, "tenant/key mismatch");
      case INVALID_KEY_MATERIAL -> Failure.of(FailureCode.ATTESTATION_KEY_UNAVAILABLE, "invalid key material");
      case INVALID_INPUT -> Failure.of(FailureCode.ATTESTATION_MISSING_OR_INVALID, "invalid key lookup input");
    };
  }

  private static String normalizeAlg(String alg) {
    return (alg == null) ? "" : alg.trim().toLowerCase(Locale.ROOT);
  }

  public record VerifyOutcome(
      boolean verified,
      String tenantId,
      String keyId,
      String nonce,
      String alg,
      long created,
      long expires,
      Failure failure
  ) {
    public static VerifyOutcome verified(String tenantId,
                                         String keyId,
                                         String nonce,
                                         String alg,
                                         long created,
                                         long expires) {
      return new VerifyOutcome(true, tenantId, keyId, nonce, alg, created, expires, null);
    }

    public static VerifyOutcome failure(Failure failure) {
      return new VerifyOutcome(false, null, null, null, null, 0L, 0L, failure);
    }
  }

  public record Failure(FailureCode code, String message) {
    public static Failure of(FailureCode code, String message) {
      return new Failure(Objects.requireNonNull(code, "code"), safeMessage(message));
    }

    private static String safeMessage(String message) {
      if (message == null || message.isBlank()) {
        return "attestation failed";
      }
      return message;
    }
  }

  public enum FailureCode {
    ATTESTATION_MISSING_OR_INVALID,
    ATTESTATION_MISSING_COMPONENT,
    ATTESTATION_TIMESTAMP_INVALID,
    ATTESTATION_KEY_UNAVAILABLE,
    ATTESTATION_TENANT_KEY_MISMATCH,
    ATTESTATION_INVALID_SIGNATURE,
    ATTESTATION_REPLAY_DETECTED,
    ATTESTATION_REPLAY_UNAVAILABLE,
    ATTESTATION_INTERNAL_ERROR
  }
}
