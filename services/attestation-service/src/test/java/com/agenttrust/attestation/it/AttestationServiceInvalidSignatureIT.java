package com.agenttrust.attestation.it;

import static org.junit.jupiter.api.Assertions.*;

import com.agenttrust.attestation.rfc9421.Rfc9421SignatureBaseBuilder;
import com.agenttrust.attestation.rfc9421.Rfc9421SignatureInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttestationServiceInvalidSignatureIT {

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

  private final ObjectMapper objectMapper = new ObjectMapper();

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) throws Exception {
    if (PUBLIC_KEY_RAW_BASE64 == null) {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");

      // Keypair A: registry/trusted key (public key injected into registry)
      KeyPair registryKeyPair = kpg.generateKeyPair();

      byte[] spki = registryKeyPair.getPublic().getEncoded();
      byte[] raw = new byte[32];
      System.arraycopy(spki, spki.length - 32, raw, 0, 32);
      PUBLIC_KEY_RAW_BASE64 = Base64.getEncoder().encodeToString(raw);

      // Keypair B: signing key (different key => invalid signature)
      KeyPair signingKeyPair = kpg.generateKeyPair();
      TestKeyHolder.setSigningKeyPair(signingKeyPair);
    }

    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

    // Bind one shape
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
  void invalidSignature_returns401ProblemDetails() throws Exception {
    String authority = "merchant.local";
    String path = "/v1/agent/verify";

    long now = Instant.now().getEpochSecond();
    long created = now - 1;
    long expires = now + 120;

    String nonce = "nonce-invalid-sig-1";
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

    // Sign with the WRONG key (does not match registry public key).
    byte[] signatureBytes = signEd25519WithWrongKey(signatureBase);
    String signatureHeader = "sig1=:" + Base64.getEncoder().encodeToString(signatureBytes) + ":";

    String jsonRequest = objectMapper.createObjectNode()
        .put("method", "POST")
        .put("authority", authority)
        .put("path", path)
        .put("tenantId", TENANT_ID)
        .put("signatureInput", sigInputHeader)
        .put("signature", signatureHeader)
        .toString();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("application/problem+json")));

    ResponseEntity<String> resp =
        rest.postForEntity(url("/v1/attestations/verify"), new HttpEntity<>(jsonRequest, headers), String.class);

    assertEquals(401, resp.getStatusCode().value());

    String body = resp.getBody();
    assertNotNull(body, "Expected Problem Details body but got null.");

    JsonNode json = objectMapper.readTree(body);
    String errorCode = json.path("errorCode").asText(null);

    assertNotNull(errorCode, "Expected errorCode field but body was: " + body);
    assertEquals("ATTESTATION_INVALID_SIGNATURE", errorCode, "Body was: " + body);

    String tenantId = json.path("tenantId").asText(null);
    assertEquals(TENANT_ID, tenantId, "Body was: " + body);
  }

  private String url(String p) {
    return "http://localhost:" + port + p;
  }

  private static byte[] signEd25519WithWrongKey(String signatureBase) throws Exception {
    KeyPair kp = TestKeyHolder.getSigningKeyPair();
    Signature sig = Signature.getInstance("Ed25519");
    sig.initSign(kp.getPrivate());
    sig.update(signatureBase.getBytes(StandardCharsets.UTF_8));
    return sig.sign();
  }

  static final class TestKeyHolder {
    private static volatile KeyPair SIGNING_KEY_PAIR;

    static void setSigningKeyPair(KeyPair kp) {
      SIGNING_KEY_PAIR = kp;
    }

    static KeyPair getSigningKeyPair() {
      KeyPair kp = SIGNING_KEY_PAIR;
      if (kp == null) {
        throw new IllegalStateException("Signing KeyPair not initialized");
      }
      return kp;
    }
  }
}
