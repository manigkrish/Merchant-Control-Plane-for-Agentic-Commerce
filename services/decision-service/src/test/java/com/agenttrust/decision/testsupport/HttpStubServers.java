package com.agenttrust.decision.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tiny, dependency-free HTTP stub servers for decision-service tests.
 *
 * Why this exists:
 * - decision-service calls attestation-service + token-service over HTTP.
 * - For ITs, we want deterministic responses + call counters.
 * - Avoid adding WireMock or other dependencies.
 */
public final class HttpStubServers implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StubServer attestationServer;
    private final StubServer tokenServer;

    public HttpStubServers() {
        this.attestationServer = new StubServer("attestation-stub");
        this.tokenServer = new StubServer("token-stub");

        attestationServer.startAttestationStub();
        tokenServer.startTokenStub();
    }

    public String attestationBaseUrl() {
        return attestationServer.baseUrl();
    }

    public String tokenBaseUrl() {
        return tokenServer.baseUrl();
    }

    public int attestationCalls() {
        return attestationServer.calls.get();
    }

    public int tokenCalls() {
        return tokenServer.calls.get();
    }

    public void resetCounters() {
        attestationServer.calls.set(0);
        tokenServer.calls.set(0);
    }

    // ---- Attestation behavior ----

    public void attestationOk() {
        attestationServer.attestationMode.set(AttestationMode.OK);
    }

    public void attestationFailUnauthorized() {
        attestationServer.attestationMode.set(AttestationMode.UNAUTHORIZED);
    }

    // ---- Token validate behavior ----

    public void tokenValid(UUID tokenId) {
        Objects.requireNonNull(tokenId, "tokenId");
        tokenServer.tokenMode.set(TokenMode.valid(tokenId));
    }

    public void tokenInvalid(String reasonCode) {
        tokenServer.tokenMode.set(TokenMode.invalid(reasonCode));
    }

    @Override
    public void close() {
        attestationServer.stop();
        tokenServer.stop();
    }

    private enum AttestationMode { OK, UNAUTHORIZED }

    private static final class TokenMode {
        final boolean valid;
        final UUID tokenId;
        final String reasonCode;

        private TokenMode(boolean valid, UUID tokenId, String reasonCode) {
            this.valid = valid;
            this.tokenId = tokenId;
            this.reasonCode = reasonCode;
        }

        static TokenMode valid(UUID tokenId) {
            return new TokenMode(true, tokenId, null);
        }

        static TokenMode invalid(String reasonCode) {
            String rc = (reasonCode == null || reasonCode.isBlank()) ? "TOKEN_INVALID" : reasonCode;
            return new TokenMode(false, null, rc);
        }
    }

    private static final class StubServer {
        private final String name;

        private final AtomicInteger calls = new AtomicInteger(0);
        private final AtomicReference<AttestationMode> attestationMode = new AtomicReference<>(AttestationMode.OK);
        private final AtomicReference<TokenMode> tokenMode = new AtomicReference<>(TokenMode.valid(UUID.randomUUID()));

        private HttpServer server;
        private int port;

        StubServer(String name) {
            this.name = name;
        }

        String baseUrl() {
            return "http://localhost:" + port;
        }

        void startAttestationStub() {
            start();
            server.createContext("/v1/attestations/verify", this::handleAttestationVerify);
        }

        void startTokenStub() {
            start();
            server.createContext("/internal/v1/tokens/validate", this::handleTokenValidate);
        }

        private void start() {
            if (server != null) {
                return;
            }
            try {
                server = HttpServer.create(new InetSocketAddress(0), 0);
                server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setName(name);
                    t.setDaemon(true);
                    return t;
                }));
                server.start();
                port = server.getAddress().getPort();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start " + name, e);
            }
        }

        void stop() {
            if (server != null) {
                server.stop(0);
                server = null;
            }
        }

        private void handleAttestationVerify(HttpExchange ex) throws IOException {
            calls.incrementAndGet();
            drainRequestBody(ex);

            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "application/problem+json",
                        """
                        {"type":"about:blank","title":"Method Not Allowed","status":405,"detail":"Only POST supported","errorCode":"METHOD_NOT_ALLOWED"}
                        """);
                return;
            }

            AttestationMode mode = attestationMode.get();
            if (mode == AttestationMode.OK) {
                writeJson(ex, 200, "application/json", "{\"verified\":true}");
                return;
            }

            writeJson(ex, 401, "application/problem+json",
                    """
                    {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Invalid signature","errorCode":"ATTESTATION_INVALID_SIGNATURE"}
                    """);
        }

        private void handleTokenValidate(HttpExchange ex) throws IOException {
            calls.incrementAndGet();
            drainRequestBody(ex);

            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, "application/problem+json",
                        """
                        {"type":"about:blank","title":"Method Not Allowed","status":405,"detail":"Only POST supported","errorCode":"METHOD_NOT_ALLOWED"}
                        """);
                return;
            }

            // Enforce your invariant: decision-service must pass tenant via header.
            String tenantHeader = ex.getRequestHeaders().getFirst("X-Tenant-Id");
            if (tenantHeader == null || tenantHeader.isBlank()) {
                writeJson(ex, 400, "application/problem+json",
                        """
                        {"type":"about:blank","title":"Bad Request","status":400,"detail":"Missing X-Tenant-Id","errorCode":"TENANT_HEADER_MISSING"}
                        """);
                return;
            }

            TokenMode mode = tokenMode.get();
            String json = OBJECT_MAPPER.writeValueAsString(
                    new TokenValidateResponse(mode.valid, mode.tokenId, mode.reasonCode)
            );
            writeJson(ex, 200, "application/json", json);
        }

        private static void drainRequestBody(HttpExchange ex) {
            try (InputStream in = ex.getRequestBody()) {
                if (in != null) {
                    // Drain to avoid connection reuse issues in some JDK implementations.
                    in.readAllBytes();
                }
            } catch (IOException ignored) {
                // Ignore stub-side read errors
            }
        }

        private static void writeJson(HttpExchange ex, int status, String contentType, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            } finally {
                ex.close();
            }
        }
    }

    private record TokenValidateResponse(boolean valid, UUID tokenId, String reasonCode) { }
}
