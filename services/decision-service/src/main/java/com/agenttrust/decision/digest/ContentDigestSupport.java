package com.agenttrust.decision.digest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContentDigestSupport {

    private static final Pattern SHA256_PATTERN =
            Pattern.compile("(?i)^sha-256\\s*=\\s*:(?<b64>[^:]+):\\s*$");

    private ContentDigestSupport() {
    }

    /**
     * Verifies Content-Digest header against the body bytes.
     *
     * Sprint 4 constraints:
     * - Only sha-256 is supported.
     * - Only a single digest value is supported (no comma-separated lists).
     *
     * Returns a canonical digest string suitable for requestHash identity.
     */
    public static VerifiedContentDigest verifySha256(String contentDigestHeader, byte[] bodyBytes) {
        if (contentDigestHeader == null || contentDigestHeader.isBlank()) {
            throw new ContentDigestException("DIGEST_MISSING", "Content-Digest header is required");
        }
        Objects.requireNonNull(bodyBytes, "bodyBytes");

        String trimmed = contentDigestHeader.trim();
        if (trimmed.contains(",")) {
            throw new ContentDigestException("DIGEST_INVALID_FORMAT", "Multiple digests are not supported in Sprint 4");
        }

        Matcher m = SHA256_PATTERN.matcher(trimmed);
        if (!m.matches()) {
            // Either unsupported algorithm or invalid syntax; keep error explicit for Sprint 4.
            if (trimmed.toLowerCase().startsWith("sha-")) {
                throw new ContentDigestException("DIGEST_UNSUPPORTED", "Only sha-256 Content-Digest is supported in Sprint 4");
            }
            throw new ContentDigestException("DIGEST_INVALID_FORMAT", "Invalid Content-Digest format");
        }

        byte[] provided;
        String b64 = m.group("b64").trim();
        try {
            provided = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ex) {
            // Some clients accidentally use URL-safe base64; accept it to be pragmatic.
            try {
                provided = Base64.getUrlDecoder().decode(b64);
            } catch (IllegalArgumentException ex2) {
                throw new ContentDigestException("DIGEST_INVALID_FORMAT", "Content-Digest base64 is invalid");
            }
        }

        byte[] computed = sha256(bodyBytes);
        if (!MessageDigest.isEqual(computed, provided)) {
            throw new ContentDigestException("DIGEST_MISMATCH", "Content-Digest does not match request body");
        }

        String canonical = "sha-256=:" + Base64.getEncoder().encodeToString(provided) + ":";
        return new VerifiedContentDigest(canonical, provided);
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record VerifiedContentDigest(
            String canonicalHeader,
            byte[] digestBytes
    ) {
        public VerifiedContentDigest {
            if (canonicalHeader == null || canonicalHeader.isBlank()) {
                throw new IllegalArgumentException("canonicalHeader must not be blank");
            }
            Objects.requireNonNull(digestBytes, "digestBytes");
        }
    }

    /**
     * Used to produce stable RFC 9457 ProblemDetails errorCodes for digest failures.
     */
    public static final class ContentDigestException extends RuntimeException {
        private final String errorCode;

        public ContentDigestException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
