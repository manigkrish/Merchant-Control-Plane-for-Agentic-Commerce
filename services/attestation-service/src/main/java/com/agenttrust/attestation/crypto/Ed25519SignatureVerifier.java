package com.agenttrust.attestation.crypto;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;

public final class Ed25519SignatureVerifier {

  public boolean verify(PublicKey publicKey, String signatureBase, byte[] signatureBytes) {
    if (publicKey == null || signatureBase == null || signatureBytes == null) {
      return false;
    }

    try {
      Signature sig = Signature.getInstance("Ed25519");
      sig.initVerify(publicKey);
      sig.update(signatureBase.getBytes(StandardCharsets.UTF_8));
      return sig.verify(signatureBytes);
    } catch (Exception ex) {
      // Fail closed
      return false;
    }
  }
}
