package com.agenttrust.attestation.rfc9421;

public final class SignatureLabelValidator {

  public void assertSameLabel(String signatureInputLabel, String signatureLabel) {
    if (signatureInputLabel == null || signatureInputLabel.isBlank()) {
      throw new IllegalArgumentException("Signature-Input label is required");
    }
    if (signatureLabel == null || signatureLabel.isBlank()) {
      throw new IllegalArgumentException("Signature label is required");
    }
    if (!signatureInputLabel.equals(signatureLabel)) {
      throw new IllegalArgumentException("Signature label does not match Signature-Input label");
    }
  }
}
