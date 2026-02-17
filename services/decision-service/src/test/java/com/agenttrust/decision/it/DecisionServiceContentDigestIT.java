package com.agenttrust.decision.it;

import com.agenttrust.decision.testsupport.HttpStubServers;
import com.agenttrust.decision.testsupport.RedisTestContainerSupport;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import org.hamcrest.Matchers;
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
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DecisionServiceContentDigestIT extends RedisTestContainerSupport {

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
    void digestMismatch_returns400_andDoesNotCallDownstream() throws Exception {
        STUBS.resetCounters();
        STUBS.attestationOk();
        STUBS.tokenValid(UUID.randomUUID());

        String tenantId = "t_demo";
        String idemKey = "idem-digest-mismatch";
        String rawToken = "stkn_test_opaque_value";
        String host = "merchant.example.test";

        byte[] body1 = """
                {"action":"PURCHASE","amount":100,"currency":"USD"}
                """.getBytes(StandardCharsets.UTF_8);

        byte[] body2 = """
                {"action":"PURCHASE","amount":101,"currency":"USD"}
                """.getBytes(StandardCharsets.UTF_8);

        // Provide a digest for body2, but bodyBytesBase64 is body1 => mismatch.
        String digestWrong = contentDigestSha256(body2);

        String reqJson = decisionRequestJson("PURCHASE", 100, "USD", body1, digestWrong, rawToken);

        mvc.perform(post("/internal/v1/decisions")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("Idempotency-Key", idemKey)
                        .header("Host", host)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON))
                // Be tolerant across small naming differences, but still require a stable errorCode.
                .andExpect(jsonPath("$.errorCode", Matchers.anyOf(
                        is("DIGEST_MISMATCH"),
                        is("DIGEST_INVALID")
                )));

        // Fail-fast: digest is checked before calling attestation/token.
        assertThat(STUBS.attestationCalls()).isEqualTo(0);
        assertThat(STUBS.tokenCalls()).isEqualTo(0);
    }

    @Test
    void digestUnsupportedAlgorithm_returns400_andDoesNotCallDownstream() throws Exception {
        STUBS.resetCounters();
        STUBS.attestationOk();
        STUBS.tokenValid(UUID.randomUUID());

        String tenantId = "t_demo";
        String idemKey = "idem-digest-unsupported";
        String rawToken = "stkn_test_opaque_value";
        String host = "merchant.example.test";

        byte[] body = """
                {"action":"PURCHASE","amount":100,"currency":"USD"}
                """.getBytes(StandardCharsets.UTF_8);

        String digestSha512 = contentDigestSha512(body); // unsupported in Sprint 4

        String reqJson = decisionRequestJson("PURCHASE", 100, "USD", body, digestSha512, rawToken);

        mvc.perform(post("/internal/v1/decisions")
                        .contentType(APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId)
                        .header("Idempotency-Key", idemKey)
                        .header("Host", host)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode", Matchers.anyOf(
                        is("DIGEST_UNSUPPORTED"),
                        is("DIGEST_INVALID")
                )));

        // Fail-fast: digest is checked before calling attestation/token.
        assertThat(STUBS.attestationCalls()).isEqualTo(0);
        assertThat(STUBS.tokenCalls()).isEqualTo(0);
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

    private static String contentDigestSha512(byte[] bodyBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(bodyBytes);
            String b64 = Base64.getEncoder().encodeToString(digest);
            return "sha-512=:" + b64 + ":";
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
