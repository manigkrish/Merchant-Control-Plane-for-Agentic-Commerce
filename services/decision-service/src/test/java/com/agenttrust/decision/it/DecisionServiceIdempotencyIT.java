package com.agenttrust.decision.it;

import com.agenttrust.decision.testsupport.HttpStubServers;
import com.agenttrust.decision.testsupport.RedisTestContainerSupport;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DecisionServiceIdempotencyIT extends RedisTestContainerSupport {

    // IMPORTANT: DynamicPropertySource runs during Spring context initialization.
    // So the stubs must be available BEFORE the context is built.
    private static final HttpStubServers STUBS = new HttpStubServers();

    @AfterAll
    static void stopStubs() {
        STUBS.close();
    }

    @DynamicPropertySource
    static void overrideClients(DynamicPropertyRegistry registry) {
        registry.add("agenttrust.decision.clients.attestationBaseUrl", STUBS::attestationBaseUrl);
        registry.add("agenttrust.decision.clients.tokenBaseUrl", STUBS::tokenBaseUrl);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void idempotencyHit_returnsCachedResponseWithoutRecallingDownstream() throws Exception {
        STUBS.resetCounters();
        STUBS.attestationOk();
        STUBS.tokenValid(UUID.randomUUID());

        String tenantId = "t_demo";
        String idemKey = "idem-aaaaaaaaaaaaaaaa";
        String rawToken = "stkn_test_opaque_value";
        String host = "merchant.example.test";

        byte[] body = """
                {"action":"PURCHASE","amount":100,"currency":"USD"}
                """.getBytes(StandardCharsets.UTF_8);

        String digest = contentDigestSha256(body);
        String reqJson = decisionRequestJson("PURCHASE", 100, "USD", body, digest, rawToken);

        // 1st request => cache miss => downstream called once each
        var r1 = mvc.perform(post("/internal/v1/decisions")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("Idempotency-Key", idemKey)
                        .header("Host", host)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn();

        assertThat(STUBS.attestationCalls()).isEqualTo(1);
        assertThat(STUBS.tokenCalls()).isEqualTo(1);

        byte[] firstBody = r1.getResponse().getContentAsByteArray();

        // 2nd request (same idem key + same semantics) => cached hit => no new downstream calls
        var r2 = mvc.perform(post("/internal/v1/decisions")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("Idempotency-Key", idemKey)
                        .header("Host", host)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn();

        assertThat(STUBS.attestationCalls()).isEqualTo(1);
        assertThat(STUBS.tokenCalls()).isEqualTo(1);

        byte[] secondBody = r2.getResponse().getContentAsByteArray();
        assertThat(secondBody).isEqualTo(firstBody);
    }

    @Test
    void idempotencyReuseConflict_returns409ProblemDetails() throws Exception {
        STUBS.resetCounters();
        STUBS.attestationOk();
        STUBS.tokenValid(UUID.randomUUID());

        String tenantId = "t_demo";
        String idemKey = "idem-bbbbbbbbbbbbbbbb";
        String rawToken = "stkn_test_opaque_value";
        String host = "merchant.example.test";

        byte[] body1 = """
                {"action":"PURCHASE","amount":100,"currency":"USD"}
                """.getBytes(StandardCharsets.UTF_8);

        byte[] body2 = """
                {"action":"PURCHASE","amount":101,"currency":"USD"}
                """.getBytes(StandardCharsets.UTF_8);

        String digest1 = contentDigestSha256(body1);
        String digest2 = contentDigestSha256(body2);

        String reqJson1 = decisionRequestJson("PURCHASE", 100, "USD", body1, digest1, rawToken);
        String reqJson2 = decisionRequestJson("PURCHASE", 101, "USD", body2, digest2, rawToken);

        // first request stores idempotency entry
        mvc.perform(post("/internal/v1/decisions")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("Idempotency-Key", idemKey)
                        .header("Host", host)
                        .content(reqJson1))
                .andExpect(status().isOk());

        // same Idempotency-Key but different semantics (different body digest) => 409
        mvc.perform(post("/internal/v1/decisions")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("Idempotency-Key", idemKey)
                        .header("Host", host)
                        .content(reqJson2))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSE_CONFLICT"));
    }

    private static String decisionRequestJson(
            String action,
            long amount,
            String currency,
            byte[] bodyBytes,
            String contentDigest,
            String rawToken
    ) {
        String b64Body = Base64.getEncoder().encodeToString(bodyBytes);
        // signature fields are required by DTO, but NOT part of requestHash (correct by design).
        return """
                {
                  "action":"%s",
                  "amount":%d,
                  "currency":"%s",
                  "rawScopedToken":"%s",
                  "contentDigest":"%s",
                  "bodyBytesBase64":"%s",
                  "signatureInput":"siginput_dummy",
                  "signature":"sig_dummy"
                }
                """.formatted(action, amount, currency, rawToken, contentDigest, b64Body);
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
}
