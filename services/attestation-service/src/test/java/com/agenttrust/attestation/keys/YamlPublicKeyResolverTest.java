package com.agenttrust.attestation.keys;

import static org.junit.jupiter.api.Assertions.*;

import com.agenttrust.attestation.config.AttestationProperties;
import com.agenttrust.attestation.config.AttestationProperties.Keys.KeyEntry;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class YamlPublicKeyResolverTest {

  // Ed25519 SubjectPublicKeyInfo DER prefix (matches YamlPublicKeyResolver behavior).
  private static final byte[] ED25519_SPKI_PREFIX = new byte[] {
      0x30, 0x2a,
      0x30, 0x05,
      0x06, 0x03,
      0x2b, 0x65, 0x70,
      0x03, 0x21, 0x00
  };

  @Test
  void resolve_raw32ByteEd25519PublicKey_succeedsAndBuildsEquivalentKey() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
    KeyPair kp = kpg.generateKeyPair();

    PublicKey original = kp.getPublic();
    byte[] spki = original.getEncoded();
    assertNotNull(spki);
    assertTrue(spki.length >= ED25519_SPKI_PREFIX.length + 32);

    // Extract raw 32-byte public key (last 32 bytes in the SPKI for Ed25519).
    byte[] raw = new byte[32];
    System.arraycopy(spki, spki.length - 32, raw, 0, 32);

    String rawB64 = Base64.getEncoder().encodeToString(raw);

    AttestationProperties props = new AttestationProperties();
    KeyEntry entry = new KeyEntry();
    entry.setTenantId("__platform__");
    entry.setKeyId("dev-ed25519-1");
    entry.setStatus("ACTIVE");
    entry.setPublicKeyBase64(rawB64);

    props.getKeys().getRegistry().setEntries(List.of(entry));

    YamlPublicKeyResolver resolver = new YamlPublicKeyResolver(props);

    PublicKeyResolver.ResolveResult result = resolver.resolve("__platform__", "dev-ed25519-1");
    assertTrue(result.isSuccess(), "Expected key resolution success");
    assertTrue(result.failure().isEmpty());

    PublicKey resolved = result.key().orElseThrow().publicKey();
    assertNotNull(resolved);

    // The resolver wraps raw bytes back into SPKI; it should match the original Ed25519 SPKI encoding.
    assertArrayEquals(spki, resolved.getEncoded());
  }

  @Test
  void resolve_keyExistsButDifferentTenant_returnsTenantKeyMismatch() {
    AttestationProperties props = new AttestationProperties();
    KeyEntry entry = new KeyEntry();
    entry.setTenantId("__platform__");
    entry.setKeyId("k1");
    entry.setStatus("ACTIVE");
    entry.setPublicKeyBase64("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="); // 32 zero bytes (base64)

    props.getKeys().getRegistry().setEntries(List.of(entry));

    YamlPublicKeyResolver resolver = new YamlPublicKeyResolver(props);

    PublicKeyResolver.ResolveResult result = resolver.resolve("tenantA", "k1");
    assertFalse(result.isSuccess());
    assertTrue(result.key().isEmpty());

    PublicKeyResolver.Failure failure = result.failure().orElseThrow();
    assertEquals(PublicKeyResolver.FailureCode.TENANT_KEY_MISMATCH, failure.code());
  }
}
