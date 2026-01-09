package com.agenttrust.platform.web.problem;

import java.net.URI;

/**
 * RFC 9457 Problem Details for HTTP APIs.
 * Media type: application/problem+json
 * Minimal fields used across AgentTrust Gateway services:
 * - type, title, status are required
 * - detail, instance are optional
 * - extensions: errorCode, traceId, requestId, tenantId (when safe to expose)
 */
public record ProblemDetails(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String errorCode,
        String traceId,
        String requestId,
        String tenantId
) {

    public ProblemDetails {
        validate(type, title, status);
    }

    private static void validate(URI type, String title, int status) {
        if (type == null) throw new IllegalArgumentException("ProblemDetails.type must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("ProblemDetails.title must not be blank");
        if (status < 100 || status > 599) throw new IllegalArgumentException("ProblemDetails.status must be a valid HTTP status code");
    }

    public static ProblemDetails of(URI type, String title, int status) {
        return new ProblemDetails(type, title, status, null, null, null, null, null, null);
    }
}
