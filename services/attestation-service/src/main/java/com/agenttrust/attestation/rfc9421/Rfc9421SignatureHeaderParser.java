package com.agenttrust.attestation.rfc9421;

import java.util.Base64;

public final class Rfc9421SignatureHeaderParser {

  public record Parsed(String label, byte[] signatureBytes) { }

  /**
   * Parses a raw Signature header value.
   *
   * Constraints:
   * - Single signature label only. Multiple labels are rejected.
   * - Strict parsing: expects label=:base64: with no trailing tokens.
   *
   * Example:
   *   sig1=:MEUCIQ...:
   */
  public Parsed parseSingle(String signatureHeader) {
    if (signatureHeader == null || signatureHeader.trim().isEmpty()) {
      throw new IllegalArgumentException("Signature is required");
    }

    String raw = signatureHeader.trim();

    // Base64 does not include ','; a top-level comma indicates multiple labels.
    if (raw.indexOf(',') >= 0) {
      throw new IllegalArgumentException("Multiple Signature labels are not supported");
    }

    int eq = raw.indexOf('=');
    if (eq <= 0) {
      throw new IllegalArgumentException("Invalid Signature format");
    }

    String label = raw.substring(0, eq).trim();
    if (label.isEmpty()) {
      throw new IllegalArgumentException("Signature label is required");
    }

    String rest = raw.substring(eq + 1).trim();
    if (rest.length() < 2 || rest.charAt(0) != ':') {
      throw new IllegalArgumentException("Signature value must be a byte sequence");
    }

    int secondColon = rest.indexOf(':', 1);
    if (secondColon < 0) {
      throw new IllegalArgumentException("Unterminated signature byte sequence");
    }

    String b64 = rest.substring(1, secondColon).trim();
    if (b64.isEmpty()) {
      throw new IllegalArgumentException("Signature bytes must not be empty");
    }

    String trailing = rest.substring(secondColon + 1).trim();
    if (!trailing.isEmpty()) {
      throw new IllegalArgumentException("Invalid trailing content in Signature header");
    }

    byte[] sigBytes;
    try {
      sigBytes = Base64.getDecoder().decode(b64);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid base64 in Signature header");
    }

    return new Parsed(label, sigBytes);
  }
}
