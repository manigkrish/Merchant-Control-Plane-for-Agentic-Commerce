package com.agenttrust.decision.idempotency;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Redis-stored idempotency record.
 *
 * Stored as JSON (String) with bodyBytesBase64 so we can return the exact same response
 * on an idempotency hit without recomputing or re-calling downstream services.
 */
public record IdempotencyRecord(
        String requestHash,
        int httpStatus,
        String contentType,
        String bodyBytesBase64
) {

    public IdempotencyRecord {
        requestHash = requireNonBlank(requestHash, "requestHash");
        contentType = requireNonBlank(contentType, "contentType");
        bodyBytesBase64 = requireNonBlank(bodyBytesBase64, "bodyBytesBase64");
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("httpStatus must be a valid HTTP status code");
        }
    }

    public static IdempotencyRecord of(String requestHash, int httpStatus, String contentType, byte[] bodyBytes) {
        Objects.requireNonNull(bodyBytes, "bodyBytes");
        String b64 = Base64.getEncoder().encodeToString(bodyBytes);
        return new IdempotencyRecord(requestHash, httpStatus, contentType, b64);
    }

    public byte[] bodyBytes() {
        return Base64.getDecoder().decode(bodyBytesBase64);
    }

    public String bodyAsUtf8() {
        return new String(bodyBytes(), StandardCharsets.UTF_8);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
