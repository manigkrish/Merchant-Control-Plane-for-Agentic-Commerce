package com.agenttrust.attestation.rfc9421;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class Rfc9421SignatureBaseBuilderTest {

  private final Rfc9421SignatureBaseBuilder builder = new Rfc9421SignatureBaseBuilder();

  @Test
  void build_requiredComponents_buildsDeterministicBase() {
    List<String> covered = List.of("@authority", "@path", "@signature-params");

    Rfc9421SignatureInput.SignatureParams params =
        new Rfc9421SignatureInput.SignatureParams(
            "dev-ed25519-1",
            "ed25519",
            1700000000L,
            1700000480L,
            "n-123",
            "t-1"
        );

    String base = builder.build("Example.COM", "/v1/agent/verify", covered, params);

    String expected =
        "\"@authority\": \"example.com\"\n" +
            "\"@path\": \"/v1/agent/verify\"\n" +
            "\"@signature-params\": (\"@authority\" \"@path\" \"@signature-params\");" +
            "created=1700000000;expires=1700000480;" +
            "keyid=\"dev-ed25519-1\";alg=\"ed25519\";nonce=\"n-123\";tag=\"t-1\"";

    assertEquals(expected, base);
  }

  @Test
  void build_unsupportedComponent_rejected() {
    List<String> covered = List.of("@method");

    Rfc9421SignatureInput.SignatureParams params =
        new Rfc9421SignatureInput.SignatureParams(
            "dev-ed25519-1",
            "ed25519",
            1L,
            2L,
            "n",
            "t"
        );

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> builder.build("example.com", "/x", covered, params));

    assertTrue(ex.getMessage().toLowerCase().contains("unsupported"));
  }
}
