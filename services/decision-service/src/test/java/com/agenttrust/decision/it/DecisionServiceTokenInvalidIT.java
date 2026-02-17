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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DecisionServiceTokenInvalidIT extends RedisTestContainerSupport {

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
    void tokenInvalid_returns401ProblemDetails_andIsCached() throws Exception {
        STUBS.resetCounters();
        STUBS.attestationOk();
        STUBS.tokenInvalid("TOKEN_REVOKED");

        String tenantId = "t_demo";
        String idemKey = "idem-token-invalid";
        String rawToken = "stkn_test_opaque_value";
        String host = "merchant.example.test";

        byte[] body = """
                {"action":"PURCHASE","amount":100,"currency":"USD"}
                """.getBytes(StandardCharsets.UTF_8);

        String digest = contentDigestSha256(body);
        String reqJson = decisionRequestJson("PURCHASE", 100, "USD", body, digest, rawToken);

        // First call => attestation + token called
        mvc.perform(post("/internal/v1/decisions")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("Idempotency-Key", idemKey)
                        .header("Host", host)
                        .content(reqJson))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("SCOPED_TOKEN_INVALID"))
                .andExpect(jsonPath("$.reasonCode").value("TOKEN_REVOKED"));

        assertThat(STUBS.attestationCalls()).isEqualTo(1);
        assertThat(STUBS.tokenCalls()).isEqualTo(1);

        // Second call (same semantics) => cached => no more downstream calls
        mvc.perform(post("/internal/v1/decisions")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("Idempotency-Key", idemKey)
                        .header("Host", host)
                        .content(reqJson))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("SCOPED_TOKEN_INVALID"))
                .andExpect(jsonPath("$.reasonCode").value("TOKEN_REVOKED"));

        assertThat(STUBS.attestationCalls()).isEqualTo(1);
        assertThat(STUBS.tokenCalls()).isEqualTo(1);
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
