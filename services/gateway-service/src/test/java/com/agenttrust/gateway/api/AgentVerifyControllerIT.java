package com.agenttrust.gateway.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AgentVerifyControllerIT {

  private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
  private static final Map<String, String> LAST_HEADERS = new ConcurrentHashMap<>();

  private static volatile HttpServer server;
  private static volatile int port;

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void overrideProps(DynamicPropertyRegistry registry) {
    startServerIfNeeded();
    registry.add("agenttrust.gateway.attestation.base-url", () -> "http://localhost:" + port);
  }

  @AfterAll
  static void shutdown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void verify_success_returns200_and_forwards_headers() throws Exception {
    LAST_BODY.set(null);
    LAST_HEADERS.clear();

    mockMvc.perform(post("/v1/agent/verify")
            .header("Host", "merchant.local")
            .header("Signature-Input", "siginput")
            .header("Signature", "good")
            .header("X-Correlation-Id", "cid-123")
            .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
            .header("tracestate", "vendor=state"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(content().string(containsString("\"verified\":true")));

    assertThat(LAST_HEADERS.get("x-correlation-id"), equalTo("cid-123"));
    assertThat(LAST_HEADERS.get("traceparent"),
        equalTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
    assertThat(LAST_HEADERS.get("tracestate"), equalTo("vendor=state"));

    String body = LAST_BODY.get();
    assertThat(body, containsString("\"tenantId\":\"__platform__\""));
    assertThat(body, containsString("\"authority\":\"merchant.local\""));
    assertThat(body, containsString("\"path\":\"/v1/agent/verify\""));
    assertThat(body, containsString("\"method\":\"POST\""));
  }

  @Test
  void verify_downstream_problem_details_is_passed_through() throws Exception {
    LAST_BODY.set(null);
    LAST_HEADERS.clear();

    mockMvc.perform(post("/v1/agent/verify")
            .header("Host", "merchant.local")
            .header("Signature-Input", "siginput")
            .header("Signature", "bad")
            .header("X-Correlation-Id", "cid-999"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType("application/problem+json"))
        .andExpect(content().string(containsString("\"errorCode\":\"ATTESTATION_INVALID_SIGNATURE\"")));
  }

  private static void startServerIfNeeded() {
    if (server != null) {
      return;
    }
    synchronized (AgentVerifyControllerIT.class) {
      if (server != null) {
        return;
      }
      try {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/v1/attestations/verify", new VerifyHandler());
        server.start();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start local stub attestation-service", e);
      }
    }
  }

  private static final class VerifyHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        String body = new String(bytes, StandardCharsets.UTF_8);
        LAST_BODY.set(body);

        String corr = first(exchange, "X-Correlation-Id");
        String tp = first(exchange, "traceparent");
        String ts = first(exchange, "tracestate");

        // store lower-cased keys for easy asserts
        if (corr != null) LAST_HEADERS.put("x-correlation-id", corr);
        if (tp != null) LAST_HEADERS.put("traceparent", tp);
        if (ts != null) LAST_HEADERS.put("tracestate", ts);

        boolean bad = body.contains("\"signature\":\"bad\"");
        if (bad) {
          String problem = """
              {"type":"https://agenttrust.dev/problems/attestation-failed","title":"Attestation verification failed","status":401,"detail":"invalid signature","instance":"/v1/attestations/verify","errorCode":"ATTESTATION_INVALID_SIGNATURE"}
              """.trim();

          exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
          byte[] resp = problem.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(401, resp.length);
          exchange.getResponseBody().write(resp);
          return;
        }

        String ok = "{\"verified\":true}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] resp = ok.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, resp.length);
        exchange.getResponseBody().write(resp);
      } finally {
        exchange.close();
      }
    }

    private static String first(HttpExchange exchange, String name) {
      return exchange.getRequestHeaders().getFirst(name);
    }
  }
}
