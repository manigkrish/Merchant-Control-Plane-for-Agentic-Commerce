package com.agenttrust.attestation.rfc9421;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class Rfc9421SignatureHeaderParserTest {

  private final Rfc9421SignatureHeaderParser parser = new Rfc9421SignatureHeaderParser();

  @Test
  void parseSingle_validSignature_parsesLabelAndBytes() {
    byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
    String b64 = Base64.getEncoder().encodeToString(payload);

    String header = "sig1=:" + b64 + ":";

    Rfc9421SignatureHeaderParser.Parsed parsed = parser.parseSingle(header);
    assertEquals("sig1", parsed.label());
    assertArrayEquals(payload, parsed.signatureBytes());
  }

  @Test
  void parseSingle_multipleLabels_rejected() {
    String header = "sig1=:AAAA:, sig2=:BBBB:";

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parseSingle(header));
    assertTrue(ex.getMessage().toLowerCase().contains("multiple"), "Expected multiple-label rejection message");
  }

  @Test
  void parseSingle_invalidBase64_rejected() {
    String header = "sig1=:not-base64!:";

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parseSingle(header));
    assertTrue(ex.getMessage().toLowerCase().contains("base64"), "Expected base64 rejection message");
  }
}
