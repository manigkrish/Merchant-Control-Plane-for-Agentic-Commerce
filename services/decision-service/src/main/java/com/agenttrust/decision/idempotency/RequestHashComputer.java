package com.agenttrust.decision.idempotency;

import com.agenttrust.decision.config.IdempotencyKeySupport;
import com.agenttrust.decision.digest.ContentDigestSupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Computes the stable semantic requestHash for idempotency.
 *
 * MUST NOT include cryptographic freshness fields like Signature or Signature-Input.
 * Those change on legitimate retries (new nonce/created) and would break idempotency.
 *
 * requestHash inputs (Sprint 4):
 * - tenantId
 * - external method/path constants (POST + /v1/agent/decisions/evaluate)
 * - tokenHash: SHA-256(raw X-Scoped-Token) hex
 * - verified body identity: Content-Digest (canonical) AFTER digest-vs-body verification
 */
public final class RequestHashComputer {

    private final IdempotencyKeySupport keySupport;

    public RequestHashComputer(IdempotencyKeySupport keySupport) {
        this.keySupport = Objects.requireNonNull(keySupport, "keySupport");
    }

    public String compute(
            String tenantId,
            String rawScopedToken,
            ContentDigestSupport.VerifiedContentDigest verifiedDigest
    ) {
        String t = requireNonBlank(tenantId, "tenantId");
        String tokenHash = sha256Hex(requireNonBlank(rawScopedToken, "X-Scoped-Token"));

        String methodPath = keySupport.requestIdentity().canonical();
        String bodyIdentity = verifiedDigest.canonicalHeader(); // verified + canonical form

        // Canonical string for hashing (versioned prefix to allow safe future evolution)
        String canonical = "v1|" + t + "|" + methodPath + "|" + tokenHash + "|" + bodyIdentity;

        return sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] digest = sha256(bytes);
        return toLowerHex(digest);
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toLowerHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            int v = b & 0xFF;
            out[i++] = Character.forDigit(v >>> 4, 16);
            out[i++] = Character.forDigit(v & 0x0F, 16);
        }
        return new String(out);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
