package com.agenttrust.attestation.rfc9421;

import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of a single RFC 9421 Signature-Input entry.
 *
 * This model is intentionally strict and minimal: it captures only what the verifier needs.
 * Parsing and canonicalization are handled by dedicated components.
 */
public final class Rfc9421SignatureInput {

  private final String label;
  private final List<String> coveredComponents;
  private final SignatureParams params;

  public Rfc9421SignatureInput(String label, List<String> coveredComponents, SignatureParams params) {
    this.label = requireNonBlank(label, "label");
    this.coveredComponents = List.copyOf(Objects.requireNonNull(coveredComponents, "coveredComponents"));
    this.params = Objects.requireNonNull(params, "params");
  }

  public String label() {
    return label;
  }

  public List<String> coveredComponents() {
    return coveredComponents;
  }

  public SignatureParams params() {
    return params;
  }

  public record SignatureParams(
      String keyId,
      String alg,
      long created,
      long expires,
      String nonce,
      String tag
  ) {
    public SignatureParams {
      keyId = requireNonBlank(keyId, "keyId");
      alg = requireNonBlank(alg, "alg");
      nonce = requireNonBlank(nonce, "nonce");
      tag = requireNonBlank(tag, "tag");
    }
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}
