package com.agenttrust.attestation.it;

import static org.junit.jupiter.api.Assertions.*;

import com.agenttrust.attestation.api.AttestationDtos;
import com.agenttrust.attestation.rfc9421.Rfc9421SignatureBaseBuilder;
import com.agenttrust.attestation.rfc9421.Rfc9421SignatureInput;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // kill Lettuce reconnect noise during shutdown
        "spring.main.banner-mode=off",
        "logging.level.io.lettuce=OFF",
        "logging.level.io.lettuce.core=OFF",
        "logging.level.io.lettuce.core.protocol=OFF",
        "logging.level.io.netty=ERROR",
        "spring.data.redis.lettuce.shutdown-timeout=0ms"
    }
)
class AttestationServiceRedisIT {

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4.1-alpine")
      .withExposedPorts(6379)
      .withStartupTimeout(Duration.ofSeconds(60));

  static {
    // Ensure mapped ports are available before DynamicPropertySource suppliers are evaluated.
    REDIS.start();
  }

  private static final String TENANT_ID = "__platform__";
  private static final String KEY_ID = "it-ed25519-1";

  private static volatile String PUBLIC_KEY_RAW_BASE64;

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) throws Exception {
    if (PUBLIC_KEY_RAW_BASE64 == null) {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
      KeyPair kp = kpg.generateKeyPair();

      byte[] spki = kp.getPublic().getEncoded();
      byte[] raw = new byte[32];
      System.arraycopy(spki, spki.length - 32, raw, 0, 32);
      PUBLIC_KEY_RAW_BASE64 = Base64.getEncoder().encodeToString(raw);

      TestKeyHolder.setKeyPair(kp);
    }

    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

    // Keys registry binding (list shape)
    registry.add("agenttrust.attestation.keys.registry[0].tenantId", () -> TENANT_ID);
    registry.add("agenttrust.attestation.keys.registry[0].keyId", () -> KEY_ID);
    registry.add("agenttrust.attestation.keys.registry[0].status", () -> "ACTIVE");
    registry.add("agenttrust.attestation.keys.registry[0].publicKeyBase64", () -> PUBLIC_KEY_RAW_BASE64);

    // Also bind the alternate shape (safe if ignored; required if your props use registry.entries)
    registry.add("agenttrust.attestation.keys.registry.entries[0].tenantId", () -> TENANT_ID);
    registry.add("agenttrust.attestation.keys.registry.entries[0].keyId", () -> KEY_ID);
    registry.add("agenttrust.attestation.keys.registry.entries[0].status", () -> "ACTIVE");
    registry.add("agenttrust.attestation.keys.registry.entries[0].publicKeyBase64", () -> PUBLIC_KEY_RAW_BASE64);

    registry.add("agenttrust.attestation.replay.enabled", () -> "true");
    registry.add("agenttrust.attestation.replay.keyPrefix", () -> "replay");
    registry.add("agenttrust.attestation.replay.defaultTtlSeconds", () -> "480");

    registry.add("agenttrust.attestation.profile.bodyless", () -> "true");
    registry.add("agenttrust.attestation.profile.maxWindowSeconds", () -> "480");

    registry.add("agenttrust.attestation.profile.allowedAlgorithms[0]", () -> "ed25519");

    registry.add("agenttrust.attestation.profile.requiredCoveredComponents[0]", () -> "@authority");
    registry.add("agenttrust.attestation.profile.requiredCoveredComponents[1]", () -> "@path");
    registry.add("agenttrust.attestation.profile.requiredCoveredComponents[2]", () -> "@signature-params");

    registry.add("agenttrust.attestation.profile.requiredSignatureParams[0]", () -> "keyid");
    registry.add("agenttrust.attestation.profile.requiredSignatureParams[1]", () -> "alg");
    registry.add("agenttrust.attestation.profile.requiredSignatureParams[2]", () -> "created");
    registry.add("agenttrust.attestation.profile.requiredSignatureParams[3]", () -> "expires");
    registry.add("agenttrust.attestation.profile.requiredSignatureParams[4]", () -> "nonce");
    registry.add("agenttrust.attestation.profile.requiredSignatureParams[5]", () -> "tag");
  }

  @Autowired
  TestRestTemplate rest;

  @LocalServerPort
  int port;

  @Test
  void verify_thenReplaySameNonce_isBlocked() throws Exception {
    String authority = "localhost:" + port;
    String path = "/v1/attestations/verify";

    long now = Instant.now().getEpochSecond();
    long created = now - 1;
    long expires = now + 120;

    String nonce = "nonce-abc-123";
    String tag = "t-1";
    String alg = "ed25519";

    List<String> covered = List.of("@authority", "@path", "@signature-params");
    Rfc9421SignatureInput.SignatureParams params =
        new Rfc9421SignatureInput.SignatureParams(KEY_ID, alg, created, expires, nonce, tag);

    String sigInputHeader =
        "sig1=(\"@authority\" \"@path\" \"@signature-params\");" +
            "created=" + created + ";expires=" + expires + ";" +
            "keyid=\"" + KEY_ID + "\";alg=\"" + alg + "\";nonce=\"" + nonce + "\";tag=\"" + tag + "\"";

    String signatureBase = new Rfc9421SignatureBaseBuilder().build(authority, path, covered, params);
    byte[] signatureBytes = signEd25519(signatureBase);

    String signatureHeader = "sig1=:" + Base64.getEncoder().encodeToString(signatureBytes) + ":";

    // VerifyRequest order (REQUIRED):
    // (method, authority, path, tenantId, signatureInput, signature)
    AttestationDtos.VerifyRequest req = new AttestationDtos.VerifyRequest(
        "POST",
        authority,
        path,
        TENANT_ID,
        sigInputHeader,
        signatureHeader
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("application/problem+json")));

    HttpEntity<AttestationDtos.VerifyRequest> entity = new HttpEntity<>(req, headers);

    ResponseEntity<String> first = rest.postForEntity(url(path), entity, String.class);
    assertEquals(
        200,
        first.getStatusCode().value(),
        "First call failed. status=" + first.getStatusCode().value() + " body=" + first.getBody()
    );
    assertNotNull(first.getBody());
    assertTrue(first.getBody().contains("\"verified\":true"));

    ResponseEntity<String> second = rest.postForEntity(url(path), entity, String.class);
    assertEquals(
        409,
        second.getStatusCode().value(),
        "Second call failed. status=" + second.getStatusCode().value() + " body=" + second.getBody()
    );
    assertNotNull(second.getBody());
    assertTrue(second.getBody().contains("\"errorCode\":\"ATTESTATION_REPLAY_DETECTED\""));
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private static byte[] signEd25519(String signatureBase) throws Exception {
    KeyPair kp = TestKeyHolder.getKeyPair();
    Signature sig = Signature.getInstance("Ed25519");
    sig.initSign(kp.getPrivate());
    sig.update(signatureBase.getBytes(StandardCharsets.UTF_8));
    return sig.sign();
  }

  static final class TestKeyHolder {
    private static volatile KeyPair KEY_PAIR;

    static void setKeyPair(KeyPair kp) {
      KEY_PAIR = kp;
    }

    static KeyPair getKeyPair() {
      KeyPair kp = KEY_PAIR;
      if (kp == null) {
        throw new IllegalStateException("KeyPair not initialized");
      }
      return kp;
    }
  }
}
