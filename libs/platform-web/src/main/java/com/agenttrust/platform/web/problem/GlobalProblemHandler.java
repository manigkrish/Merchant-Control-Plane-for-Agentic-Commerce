package com.agenttrust.platform.web.problem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Shared exception-to-ProblemDetails mapper (RFC 9457).
 *
 * Security posture:
 * - never return stack traces
 * - avoid echoing raw exception messages for generic/unknown failures
 * - keep response size bounded
 */
@RestControllerAdvice
public class GlobalProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalProblemHandler.class);

    private static final MediaType PROBLEM_JSON = MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

    // Stable problem type identifiers (human-readable and linkable later).
    private static final URI TYPE_VALIDATION = URI.create("https://agenttrust.dev/problems/validation-error");
    private static final URI TYPE_BAD_REQUEST = URI.create("https://agenttrust.dev/problems/bad-request");
    private static final URI TYPE_INTERNAL = URI.create("https://agenttrust.dev/problems/internal-error");

    private static final int MAX_DETAIL_CHARS = 300;
    private static final int MAX_LOG_CHARS = 500;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                       HttpServletRequest request) {
        String detail = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + defaultIfBlank(fe.getDefaultMessage(), "invalid"))
                .collect(Collectors.joining("; "));

        return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation failed",
                normalizeDetail(detail), request, "VALIDATION_ERROR");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetails> handleConstraintViolation(ConstraintViolationException ex,
                                                                    HttpServletRequest request) {
        String detail = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));

        return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation failed",
                normalizeDetail(detail), request, "VALIDATION_ERROR");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetails> handleIllegalArgument(IllegalArgumentException ex,
                                                                HttpServletRequest request) {
        // Do not echo raw message (can leak internals). Log it instead.
        log.warn("Bad request: {}", safeForLog(ex.getMessage()));
        return problem(HttpStatus.BAD_REQUEST, TYPE_BAD_REQUEST, "Bad request",
                "Invalid request.", request, "BAD_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleUnhandled(Exception ex,
                                                          HttpServletRequest request) {
        // Log full stack trace for operators; response stays generic.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, TYPE_INTERNAL, "Internal error",
                "An unexpected error occurred.", request, "INTERNAL_ERROR");
    }

    private ResponseEntity<ProblemDetails> problem(HttpStatus status,
                                                   URI type,
                                                   String title,
                                                   String detail,
                                                   HttpServletRequest request,
                                                   String errorCode) {

        URI instance = toInstanceUri(request);

        // These attributes will be set by request filters later in Sprint 1/2.
        String traceId = attr(request, "agenttrust.traceId");
        String requestId = attr(request, "agenttrust.requestId");

        // Only include tenantId if trusted middleware already derived it (never from body).
        String tenantId = attr(request, "agenttrust.tenantId");

        ProblemDetails body = new ProblemDetails(
                type,
                title,
                status.value(),
                detail,
                instance,
                errorCode,
                traceId,
                requestId,
                tenantId
        );

        return ResponseEntity
                .status(status)
                .contentType(PROBLEM_JSON)
                .body(body);
    }

    private static String attr(HttpServletRequest request, String key) {
        Object v = request.getAttribute(key);
        return (v instanceof String s && !s.isBlank()) ? s : null;
    }

    private static URI toInstanceUri(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return URI.create("/");
        }
        try {
            // RFC 9457: instance is a URI reference; path-only is acceptable.
            return URI.create(path);
        } catch (IllegalArgumentException e) {
            // Never fail during error handling.
            return URI.create("/");
        }
    }

    private static String normalizeDetail(String raw) {
        return sanitizeString(raw, MAX_DETAIL_CHARS);
    }

    private static String safeForLog(String raw) {
        // Keep logs bounded and single-line.
        return sanitizeString(raw, MAX_LOG_CHARS);
    }

    /**
     * Sanitizes text for safe inclusion in responses/logs:
     * - replaces control characters with spaces
     * - trims
     * - truncates to maxLength (if positive)
     */
    private static String sanitizeString(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String oneLine = raw.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (maxLength > 0 && oneLine.length() > maxLength) {
            return oneLine.substring(0, maxLength) + "...";
        }
        return oneLine;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
