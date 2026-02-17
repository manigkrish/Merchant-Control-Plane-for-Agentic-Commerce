package com.agenttrust.gateway.decisions;

import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayDecisionEvaluateDelegationTest {

    private static final DecisionStubServer STUB = new DecisionStubServer();

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @DynamicPropertySource
    static void overrideDecisionBaseUrl(DynamicPropertyRegistry registry) {
        // Support both relaxed and exact binder forms.
        registry.add("agenttrust.gateway.decision.base-url", STUB::baseUrl);
        registry.add("agenttrust.gateway.decision.baseUrl", STUB::baseUrl);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.agenttrust.gateway.tenancy.HostTenantDeriver hostTenantDeriver;

    @Test
    void evaluate_delegatesToDecisionService_setsInternalHeaders_andPassesThroughProblemDetails() throws Exception {
        when(hostTenantDeriver.deriveTenantId(any(HttpServletRequest.class))).thenReturn("t_demo");

        STUB.reset();
        byte[] downstreamProblem = """
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"bad signature","errorCode":"ATTESTATION_INVALID_SIGNATURE"}
                """.getBytes(StandardCharsets.UTF_8);
        STUB.respondWith(401, ProblemMediaTypes.APPLICATION_PROBLEM_JSON, downstreamProblem);

        String host = "merchant.example.test";
        String correlationId = UUID.randomUUID().toString();
        String traceparent = "00-71d77be958993f6f4230b4a75959d095-2724d06670a10f97-01";
        String tracestate = "congo=t61rcWkgMzE";

        String idempotencyKey = "idem-gw-delegate";
        String scopedToken = "stkn_test_opaque_value";
        String signatureInput = "siginput_dummy";
        String signature = "sig_dummy";

        byte[] bodyBytes = """
                {"action":"PURCHASE","amount":100,"currency":"USD"}
                """.getBytes(StandardCharsets.UTF_8);

        String contentDigest = contentDigestSha256(bodyBytes);

        mvc.perform(post("/v1/agent/decisions/evaluate")
                        .contentType(APPLICATION_JSON)
                        .header("Host", host)
                        .header("X-Correlation-Id", correlationId)
                        .header("traceparent", traceparent)
                        .header("tracestate", tracestate)
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Scoped-Token", scopedToken)
                        .header("Signature-Input", signatureInput)
                        .header("Signature", signature)
                        .header("Content-Digest", contentDigest)
                        .content(bodyBytes))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON))
                .andExpect(content().bytes(downstreamProblem));

        // Verify we invoked tenant derivation exactly once
        verify(hostTenantDeriver, times(1)).deriveTenantId(any(HttpServletRequest.class));

        // Ensure the gateway actually called our decision stub
        assertThat(STUB.awaitRequest(2, TimeUnit.SECONDS)).isTrue();

        DecisionStubServer.CapturedRequest req = STUB.lastRequest();
        assertThat(req).isNotNull();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.path()).isEqualTo("/internal/v1/decisions");

        // Internal headers set by gateway
        assertThat(req.header("X-Tenant-Id")).isEqualTo("t_demo");
        assertThat(req.header("Idempotency-Key")).isEqualTo(idempotencyKey);
        assertThat(req.header("X-External-Authority")).isEqualTo(host);

        // Observability headers propagated
        assertThat(req.header("X-Correlation-Id")).isEqualTo(correlationId);
        assertThat(req.header("traceparent")).isEqualTo(traceparent);
        assertThat(req.header("tracestate")).isEqualTo(tracestate);

        // Verify request JSON content forwarded correctly
        JsonNode json = objectMapper.readTree(req.body());
        assertThat(json.get("action").asText()).isEqualTo("PURCHASE");
        assertThat(json.get("amount").asLong()).isEqualTo(100L);
        assertThat(json.get("currency").asText()).isEqualTo("USD");

        assertThat(json.get("rawScopedToken").asText()).isEqualTo(scopedToken);
        assertThat(json.get("contentDigest").asText()).isEqualTo(contentDigest);
        assertThat(json.get("signatureInput").asText()).isEqualTo(signatureInput);
        assertThat(json.get("signature").asText()).isEqualTo(signature);

        String bodyB64 = json.get("bodyBytesBase64").asText();
        byte[] decoded = Base64.getDecoder().decode(bodyB64);
        assertThat(decoded).isEqualTo(bodyBytes);
    }

    private static String contentDigestSha256(byte[] bodyBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bodyBytes);
            String b64 = Base64.getEncoder().encodeToString(digest);
            return "sha-256=:" + b64 + ":";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Tiny in-JVM HTTP server to act as decision-service.
     * Avoids extra dependencies (WireMock/MockWebServer).
     */
    static final class DecisionStubServer implements AutoCloseable {

        private final HttpServer server;
        private volatile ResponsePlan responsePlan = new ResponsePlan(200, "application/json", new byte[0]);

        private volatile CapturedRequest lastRequest;
        private volatile CountDownLatch latch = new CountDownLatch(1);

        DecisionStubServer() {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/internal/v1/decisions", this::handle);
                server.setExecutor(null);
                server.start();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start DecisionStubServer", e);
            }
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void respondWith(int status, String contentType, byte[] body) {
            this.responsePlan = new ResponsePlan(status, contentType, body);
        }

        void reset() {
            this.lastRequest = null;
            this.latch = new CountDownLatch(1);
        }

        boolean awaitRequest(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        CapturedRequest lastRequest() {
            return lastRequest;
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body = exchange.getRequestBody().readAllBytes();

            CapturedRequest captured = new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(),
                    body
            );
            this.lastRequest = captured;

            ResponsePlan plan = this.responsePlan;

            exchange.getResponseHeaders().set("Content-Type", plan.contentType());
            exchange.sendResponseHeaders(plan.status(), plan.body().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(plan.body());
            } finally {
                latch.countDown();
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }

        record ResponsePlan(int status, String contentType, byte[] body) {}

        static final class CapturedRequest {
            private final String method;
            private final String path;
            private final com.sun.net.httpserver.Headers headers;
            private final byte[] body;

            CapturedRequest(String method, String path, com.sun.net.httpserver.Headers headers, byte[] body) {
                this.method = method;
                this.path = path;
                this.headers = headers;
                this.body = (body == null) ? new byte[0] : body;
            }

            String method() {
                return method;
            }

            String path() {
                return path;
            }

            byte[] body() {
                return body;
            }

            String header(String name) {
                List<String> vals = headers.get(name);
                if (vals == null || vals.isEmpty()) return null;
                return vals.get(0);
            }
        }
    }
}
