package com.agenttrust.attestation.rfc9421;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Rfc9421SignatureInputParserTest {

  private final Rfc9421SignatureInputParser parser = new Rfc9421SignatureInputParser();

  @Test
  void parseSingle_validSignatureInput_parsesLabelComponentsAndParams() {
    String header =
        "sig1=(\"@authority\" \"@path\" \"@signature-params\");" +
            "created=1700000000;expires=1700000480;" +
            "keyid=\"dev-ed25519-1\";alg=\"ed25519\";nonce=\"n-123\";tag=\"t-1\"";

    Rfc9421SignatureInputParser.Parsed parsed = parser.parseSingle(header);

    assertEquals("sig1", parsed.label());
    assertNotNull(parsed.input());

    Rfc9421SignatureInput input = parsed.input();
    assertEquals("sig1", input.label());
    assertEquals(3, input.coveredComponents().size());
    assertEquals("@authority", input.coveredComponents().get(0));
    assertEquals("@path", input.coveredComponents().get(1));
    assertEquals("@signature-params", input.coveredComponents().get(2));

    assertEquals("dev-ed25519-1", input.params().keyId());
    assertEquals("ed25519", input.params().alg());
    assertEquals(1700000000L, input.params().created());
    assertEquals(1700000480L, input.params().expires());
    assertEquals("n-123", input.params().nonce());
    assertEquals("t-1", input.params().tag());
  }

  @Test
  void parseSingle_multipleLabels_rejected() {
    String header =
        "sig1=(\"@authority\" \"@path\" \"@signature-params\");created=1;expires=2;keyid=\"k\";alg=\"ed25519\";nonce=\"n\";tag=\"t\", " +
            "sig2=(\"@authority\" \"@path\" \"@signature-params\");created=1;expires=2;keyid=\"k\";alg=\"ed25519\";nonce=\"n\";tag=\"t\"";

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parseSingle(header));
    assertTrue(ex.getMessage().toLowerCase().contains("multiple"), "Expected multiple-label rejection message");
  }

  @Test
  void parseSingle_missingRequiredParam_rejected() {
    String header =
        "sig1=(\"@authority\" \"@path\" \"@signature-params\");" +
            "created=1700000000;expires=1700000480;" +
            "keyid=\"dev-ed25519-1\";alg=\"ed25519\";tag=\"t-1\"";

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parseSingle(header));
    assertTrue(ex.getMessage().toLowerCase().contains("missing required"), "Expected missing-param rejection message");
  }
}
