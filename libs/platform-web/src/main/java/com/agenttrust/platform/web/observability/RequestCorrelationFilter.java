package com.agenttrust.platform.web.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Request correlation + trace context bootstrap.
 *
 * Contracts (docs/contracts/observability.md):
 * - Correlation header: X-Correlation-Id (preserve or generate)
 * - Trace headers: traceparent / tracestate (W3C Trace Context)
 *
 * This filter:
 * - sets request attributes used by GlobalProblemHandler to populate RFC9457 extensions
 * - stores traceId/correlationId in MDC for structured logging
 *
 * Note: In Sprint 1 we generate traceparent when missing so every request has a traceId,
 * even before OpenTelemetry export is wired (later sprint).
 */
public final class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String HEADER_TRACESTATE = "tracestate";

    public static final String ATTR_REQUEST_ID = "agenttrust.requestId";
    public static final String ATTR_TRACE_ID = "agenttrust.traceId";
    public static final String ATTR_TRACEPARENT = "agenttrust.traceparent";
    public static final String ATTR_TRACESTATE = "agenttrust.tracestate";

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_TRACE_ID = "traceId";

    private static final int MAX_CORRELATION_ID_LEN = 128;

    private static final SecureRandom RNG = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String correlationId = sanitizeCorrelationId(request.getHeader(HEADER_CORRELATION_ID));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        TraceContext traceContext = TraceContext.parse(request.getHeader(HEADER_TRACEPARENT));
        if (traceContext == null) {
            traceContext = TraceContext.newRoot();
        }

        String tracestate = sanitizeHeaderValue(request.getHeader(HEADER_TRACESTATE), 512);

        // Request attributes (used by GlobalProblemHandler for RFC9457 extensions)
        request.setAttribute(ATTR_REQUEST_ID, correlationId);
        request.setAttribute(ATTR_TRACE_ID, traceContext.traceId());
        request.setAttribute(ATTR_TRACEPARENT, traceContext.traceparent());
        if (tracestate != null) {
            request.setAttribute(ATTR_TRACESTATE, tracestate);
        }

        // Response headers (helps client-side correlation; safe to echo)
        response.setHeader(HEADER_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_TRACEPARENT, traceContext.traceparent());
        if (tracestate != null) {
            response.setHeader(HEADER_TRACESTATE, tracestate);
        }

        // Logging correlation
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_TRACE_ID, traceContext.traceId());

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private static String sanitizeCorrelationId(String raw) {
        String v = sanitizeHeaderValue(raw, MAX_CORRELATION_ID_LEN);
        if (v == null || v.isBlank()) {
            return null;
        }
        return v;
    }

    /**
     * Removes control characters, trims, and enforces a maximum length.
     * We intentionally avoid strict regex enforcement to prevent breaking real clients,
     * while still protecting logs/headers from unbounded or control-char input.
     */
    private static String sanitizeHeaderValue(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Remove ASCII control chars (0x00-0x1F, 0x7F)
        String cleaned = trimmed.replaceAll("[\\x00-\\x1F\\x7F]+", "");
        if (cleaned.length() > maxLen) {
            cleaned = cleaned.substring(0, maxLen);
        }
        return cleaned;
    }

    /**
     * Minimal W3C Trace Context handling (traceparent).
     * Format: version-trace-id-parent-id-flags
     */
    static final class TraceContext {

        private final String traceId;
        private final String parentId;
        private final String flags;

        private TraceContext(String traceId, String parentId, String flags) {
            this.traceId = Objects.requireNonNull(traceId, "traceId");
            this.parentId = Objects.requireNonNull(parentId, "parentId");
            this.flags = Objects.requireNonNull(flags, "flags");
        }

        String traceId() {
            return traceId;
        }

        String traceparent() {
            return "00-" + traceId + "-" + parentId + "-" + flags;
        }

        static TraceContext parse(String traceparent) {
            if (traceparent == null) {
                return null;
            }
            String v = traceparent.trim().toLowerCase(Locale.ROOT);
            String[] parts = v.split("-");
            if (parts.length != 4) {
                return null;
            }

            String version = parts[0];
            String traceId = parts[1];
            String parentId = parts[2];
            String flags = parts[3];

            if (version.length() != 2) return null;
            if (!isLowerHex(version, 2)) return null;

            if (traceId.length() != 32) return null;
            if (!isLowerHex(traceId, 32)) return null;
            if (isAllZeros(traceId)) return null;

            if (parentId.length() != 16) return null;
            if (!isLowerHex(parentId, 16)) return null;
            if (isAllZeros(parentId)) return null;

            if (flags.length() != 2) return null;
            if (!isLowerHex(flags, 2)) return null;

            // We intentionally accept only version "00" for now.
            if (!"00".equals(version)) {
                return null;
            }

            return new TraceContext(traceId, parentId, flags);
        }

        static TraceContext newRoot() {
            String traceId = randomHex(16);  // 16 bytes => 32 hex chars
            String parentId = randomHex(8);  // 8 bytes => 16 hex chars
            String flags = "01"; // sampled; later OpenTelemetry will control this
            return new TraceContext(traceId, parentId, flags);
        }

        private static String randomHex(int bytes) {
            byte[] b = new byte[bytes];
            RNG.nextBytes(b);
            StringBuilder sb = new StringBuilder(bytes * 2);
            for (byte value : b) {
                sb.append(Character.forDigit((value >> 4) & 0xF, 16));
                sb.append(Character.forDigit(value & 0xF, 16));
            }
            return sb.toString();
        }

        private static boolean isLowerHex(String s, int len) {
            if (s.length() != len) return false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
                if (!ok) return false;
            }
            return true;
        }

        private static boolean isAllZeros(String s) {
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) != '0') {
                    return false;
                }
            }
            return true;
        }
    }
}
