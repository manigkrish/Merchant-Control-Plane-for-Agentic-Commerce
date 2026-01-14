package com.agenttrust.attestation.keys;

import com.agenttrust.attestation.config.AttestationProperties;
import com.agenttrust.attestation.config.AttestationProperties.Keys.KeyEntry;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class YamlPublicKeyResolver implements PublicKeyResolver {

  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String KEY_ALG_ED25519 = "Ed25519";

  /**
   * ASN.1 DER SubjectPublicKeyInfo prefix for Ed25519.
   *
   * SPKI = SEQUENCE(
   *   SEQUENCE( OID 1.3.101.112 ),
   *   BIT STRING (0 unused bits, 32-byte public key)
   * )
   */
  private static final byte[] ED25519_SPKI_PREFIX = new byte[] {
      0x30, 0x2a,
      0x30, 0x05,
      0x06, 0x03,
      0x2b, 0x65, 0x70,
      0x03, 0x21, 0x00
  };

  private final Clock clock;
  private final Map<CompositeKey, KeyEntry> byTenantAndKeyId;
  private final Map<String, List<KeyEntry>> byKeyId;

  public YamlPublicKeyResolver(AttestationProperties props) {
    this(props, Clock.systemUTC());
  }

  public YamlPublicKeyResolver(AttestationProperties props, Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    List<KeyEntry> entries = safeEntries(props);

    this.byTenantAndKeyId = entries.stream()
        .filter(e -> notBlank(e.getTenantId()) && notBlank(e.getKeyId()))
        .collect(Collectors.toUnmodifiableMap(
            e -> new CompositeKey(e.getTenantId(), e.getKeyId()),
            e -> e
       ));

    this.byKeyId = entries.stream()
        .filter(e -> notBlank(e.getKeyId()))
        .collect(Collectors.groupingBy(
            KeyEntry::getKeyId,
            Collectors.toUnmodifiableList()
        ));
  }

  @Override
  public ResolveResult resolve(String tenantId, String keyId) {
    if (!notBlank(tenantId) || !notBlank(keyId)) {
      return ResolveResult.failure(new Failure(FailureCode.INVALID_INPUT, "tenantId and keyId are required"));
    }

    KeyEntry exact = byTenantAndKeyId.get(new CompositeKey(tenantId, keyId));
    if (exact == null) {
      // Detect tenant mismatch: keyId exists, but not for this tenant.
      List<KeyEntry> candidates = byKeyId.get(keyId);
      if (candidates != null && !candidates.isEmpty()) {
        return ResolveResult.failure(new Failure(FailureCode.TENANT_KEY_MISMATCH, "keyId is not registered for tenant"));
      }
      return ResolveResult.failure(new Failure(FailureCode.KEY_NOT_FOUND, "keyId not found"));
    }

    Failure statusFailure = checkStatusAndExpiry(exact);
    if (statusFailure != null) {
      return ResolveResult.failure(statusFailure);
    }

    PublicKey publicKey = decodeEd25519PublicKey(exact.getPublicKeyBase64());
    if (publicKey == null) {
      return ResolveResult.failure(new Failure(FailureCode.INVALID_KEY_MATERIAL, "invalid Ed25519 public key material"));
    }

    return ResolveResult.success(new KeyMaterial(
        exact.getTenantId(),
        exact.getKeyId(),
        publicKey,
        exact.getNotAfter()
    ));
  }

  private Failure checkStatusAndExpiry(KeyEntry entry) {
    if (!STATUS_ACTIVE.equalsIgnoreCase(nullToEmpty(entry.getStatus()))) {
      return new Failure(FailureCode.KEY_REVOKED, "key is not active");
    }
    Instant notAfter = entry.getNotAfter();
    if (notAfter != null && Instant.now(clock).isAfter(notAfter)) {
      return new Failure(FailureCode.KEY_EXPIRED, "key is expired");
    }
    return null;
  }

  private PublicKey decodeEd25519PublicKey(String base64) {
    if (!notBlank(base64)) {
      return null;
    }

    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException ex) {
      return null;
    }

    byte[] spki;
    if (decoded.length == 32) {
      // raw Ed25519 public key → wrap into SPKI for Java KeyFactory
      spki = new byte[ED25519_SPKI_PREFIX.length + decoded.length];
      System.arraycopy(ED25519_SPKI_PREFIX, 0, spki, 0, ED25519_SPKI_PREFIX.length);
      System.arraycopy(decoded, 0, spki, ED25519_SPKI_PREFIX.length, decoded.length);
    } else if (looksLikeEd25519Spki(decoded)) {
      // accept already-SPKI encoded keys to reduce friction for future upgrades
      spki = decoded;
    } else {
      return null;
    }

    try {
      KeyFactory kf = KeyFactory.getInstance(KEY_ALG_ED25519);
      return kf.generatePublic(new X509EncodedKeySpec(spki));
    } catch (Exception ex) {
      return null;
    }
  }

  private static boolean looksLikeEd25519Spki(byte[] decoded) {
    if (decoded.length < ED25519_SPKI_PREFIX.length + 32) {
      return false;
    }
    for (int i = 0; i < ED25519_SPKI_PREFIX.length; i++) {
      if (decoded[i] != ED25519_SPKI_PREFIX[i]) {
        return false;
      }
    }
    return true;
  }

  private static List<KeyEntry> safeEntries(AttestationProperties props) {
    if (props == null || props.getKeys() == null || props.getKeys().getRegistry() == null) {
      return List.of();
    }
    return Optional.ofNullable(props.getKeys().getRegistry().getEntries()).orElse(List.of());
  }

  private static boolean notBlank(String s) {
    return s != null && !s.trim().isEmpty();
  }

  private static String nullToEmpty(String s) {
    return (s == null) ? "" : s;
  }

  private record CompositeKey(String tenantId, String keyId) { }
}
