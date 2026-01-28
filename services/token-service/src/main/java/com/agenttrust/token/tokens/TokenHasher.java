package com.agenttrust.token.tokens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class TokenHasher {

    private TokenHasher() {
    }

    /**
     * Computes SHA-256(rawToken) as lowercase hex (64 chars).
     *
     * Raw token must never be logged or persisted. Only the hash may be stored.
     */
    static String sha256Hex(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        byte[] bytes = rawToken.getBytes(StandardCharsets.UTF_8);
        byte[] digest = sha256(bytes);
        return toLowerHex(digest);
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            // Should never happen on a standard JRE.
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
}
