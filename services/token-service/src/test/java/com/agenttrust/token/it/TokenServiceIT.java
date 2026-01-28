package com.agenttrust.token.it;

import com.agenttrust.token.TokenServiceApplication;
import com.agenttrust.token.api.IssueTokenRequest;
import com.agenttrust.token.api.IssueTokenResponse;
import com.agenttrust.token.api.ValidateTokenRequest;
import com.agenttrust.token.api.ValidateTokenResponse;
import com.agenttrust.token.testsupport.PostgresTestContainerSupport;
import com.agenttrust.token.tokens.ScopedToken;
import com.agenttrust.token.tokens.ScopedTokenRepository;
import com.agenttrust.token.tokens.ScopedTokenUsage;
import com.agenttrust.token.tokens.ScopedTokenUsageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {TokenServiceApplication.class, TokenServiceIT.TestClockConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenServiceIT extends PostgresTestContainerSupport {

    private static final String TENANT_ID = "__platform__";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ScopedTokenRepository tokenRepository;

    @Autowired
    ScopedTokenUsageRepository usageRepository;

    @Autowired
    AdjustableClock adjustableClock;

    @Test
    void issue_validate_revoke_writesUsageAndEnforcesRevocation() throws Exception {
        adjustableClock.setInstant(Instant.parse("2026-01-01T00:00:00Z"));

        IssueTokenRequest issue = new IssueTokenRequest(
                "PURCHASE",
                TENANT_ID,       // Option A: merchantId == tenantId
                5_000L,
                "USD",
                null,
                3600L
        );

        IssueTokenResponse issued = issueToken(issue);

        assertThat(issued.rawToken()).startsWith("stkn_");
        assertThat(issued.tokenId()).isNotNull();
        assertThat(issued.expiresAt()).isAfter(adjustableClock.instant());

        // Verify we persisted only the hash, not raw token.
        String tokenHash = sha256Hex(issued.rawToken());
        Optional<ScopedToken> stored = tokenRepository.findByTenantIdAndTokenHash(TENANT_ID, tokenHash);
        assertThat(stored).isPresent();
        assertThat(stored.get().getTokenId()).isEqualTo(issued.tokenId());

        ValidateTokenRequest validate = new ValidateTokenRequest(
                "PURCHASE",
                1299L,
                "USD",
                issued.rawToken()
        );

        ValidateTokenResponse validResp = validateToken(validate, "corr_1", sampleTraceparent());
        assertThat(validResp.valid()).isTrue();
        assertThat(validResp.tokenId()).isEqualTo(issued.tokenId());

        revokeToken(issued.tokenId(), "MANUAL_REVOKE");

        ValidateTokenResponse revokedResp = validateToken(validate, "corr_2", sampleTraceparent());
        assertThat(revokedResp.valid()).isFalse();
        assertThat(revokedResp.reasonCode()).isEqualTo("TOKEN_REVOKED");

        List<ScopedTokenUsage> usages = usageRepository.findByTokenIdOrderByUsedAtDesc(issued.tokenId());
        assertThat(usages).hasSize(2);
        assertThat(usages.stream().map(ScopedTokenUsage::getResult)).containsExactly("INVALID", "VALID");
        assertThat(usages.stream().map(ScopedTokenUsage::getTenantId).distinct()).containsExactly(TENANT_ID);
    }

    @Test
    void currencyMismatch_returnsInvalidAndWritesUsage() throws Exception {
        adjustableClock.setInstant(Instant.parse("2026-01-01T00:00:00Z"));

        IssueTokenResponse issued = issueToken(new IssueTokenRequest(
                "PURCHASE",
                TENANT_ID,
                5_000L,
                "USD",
                null,
                3600L
        ));

        ValidateTokenResponse resp = validateToken(new ValidateTokenRequest(
                "PURCHASE",
                100L,
                "EUR", // mismatch
                issued.rawToken()
        ), "corr_cur_1", sampleTraceparent());

        assertThat(resp.valid()).isFalse();
        assertThat(resp.reasonCode()).isEqualTo("TOKEN_CURRENCY_MISMATCH");

        List<ScopedTokenUsage> usages = usageRepository.findByTokenIdOrderByUsedAtDesc(issued.tokenId());
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).getResult()).isEqualTo("INVALID");
        assertThat(usages.get(0).getReasonCode()).isEqualTo("TOKEN_CURRENCY_MISMATCH");
    }

    @Test
    void amountExceedsLimit_returnsInvalidAndWritesUsage() throws Exception {
        adjustableClock.setInstant(Instant.parse("2026-01-01T00:00:00Z"));

        IssueTokenResponse issued = issueToken(new IssueTokenRequest(
                "PURCHASE",
                TENANT_ID,
                500L,     // limit
                "USD",
                null,
                3600L
        ));

        ValidateTokenResponse resp = validateToken(new ValidateTokenRequest(
                "PURCHASE",
                501L,     // exceeds
                "USD",
                issued.rawToken()
        ), "corr_amt_1", sampleTraceparent());

        assertThat(resp.valid()).isFalse();
        assertThat(resp.reasonCode()).isEqualTo("TOKEN_AMOUNT_EXCEEDS_LIMIT");

        List<ScopedTokenUsage> usages = usageRepository.findByTokenIdOrderByUsedAtDesc(issued.tokenId());
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).getResult()).isEqualTo("INVALID");
        assertThat(usages.get(0).getReasonCode()).isEqualTo("TOKEN_AMOUNT_EXCEEDS_LIMIT");
    }

    @Test
    void notBefore_returnsInvalidAndWritesUsage() throws Exception {
        adjustableClock.setInstant(Instant.parse("2026-01-01T00:00:00Z"));
        Instant now = adjustableClock.instant();

        IssueTokenResponse issued = issueToken(new IssueTokenRequest(
                "PURCHASE",
                TENANT_ID,
                5_000L,
                "USD",
                now.plusSeconds(300), // not valid yet
                3600L
        ));

        ValidateTokenResponse resp = validateToken(new ValidateTokenRequest(
                "PURCHASE",
                100L,
                "USD",
                issued.rawToken()
        ), "corr_nb_1", sampleTraceparent());

        assertThat(resp.valid()).isFalse();
        assertThat(resp.reasonCode()).isEqualTo("TOKEN_NOT_YET_VALID");

        List<ScopedTokenUsage> usages = usageRepository.findByTokenIdOrderByUsedAtDesc(issued.tokenId());
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).getResult()).isEqualTo("INVALID");
        assertThat(usages.get(0).getReasonCode()).isEqualTo("TOKEN_NOT_YET_VALID");
    }

    @Test
    void expiry_isDeterministic_noSleeps() throws Exception {
        adjustableClock.setInstant(Instant.parse("2026-01-01T00:00:00Z"));

        IssueTokenRequest issue = new IssueTokenRequest(
                "PURCHASE",
                TENANT_ID,
                5_000L,
                "USD",
                null,
                60L
        );

        IssueTokenResponse issued = issueToken(issue);

        ValidateTokenRequest validate = new ValidateTokenRequest(
                "PURCHASE",
                100L,
                "USD",
                issued.rawToken()
        );

        ValidateTokenResponse first = validateToken(validate, "corr_exp_1", sampleTraceparent());
        assertThat(first.valid()).isTrue();

        adjustableClock.setInstant(issued.expiresAt().plusSeconds(1));
        ValidateTokenResponse expired = validateToken(validate, "corr_exp_2", sampleTraceparent());
        assertThat(expired.valid()).isFalse();
        assertThat(expired.reasonCode()).isEqualTo("TOKEN_EXPIRED");
    }

    private IssueTokenResponse issueToken(IssueTokenRequest req) throws Exception {
        String body = objectMapper.writeValueAsString(req);

        String json = mockMvc.perform(
                        post("/internal/v1/tokens/issue")
                                .header("X-Tenant-Id", TENANT_ID)
                                .contentType(APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readValue(json, IssueTokenResponse.class);
    }

    private ValidateTokenResponse validateToken(ValidateTokenRequest req, String correlationId, String traceparent) throws Exception {
        String body = objectMapper.writeValueAsString(req);

        String json = mockMvc.perform(
                        post("/internal/v1/tokens/validate")
                                .header("X-Tenant-Id", TENANT_ID)
                                .header("X-Correlation-Id", correlationId)
                                .header("traceparent", traceparent)
                                .contentType(APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readValue(json, ValidateTokenResponse.class);
    }

    private void revokeToken(UUID tokenId, String reasonCode) throws Exception {
        String body = objectMapper.writeValueAsString(new RevokeTokenRequest(tokenId, reasonCode));

        mockMvc.perform(
                        post("/internal/v1/tokens/revoke")
                                .header("X-Tenant-Id", TENANT_ID)
                                .contentType(APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isNoContent());
    }

    record RevokeTokenRequest(UUID tokenId, String reasonCode) {
    }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);

        void setInstant(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {

        @Bean
        AdjustableClock adjustableClock() {
            return new AdjustableClock();
        }

        // IMPORTANT:
        // Do NOT declare a separate Clock bean.
        // AdjustableClock already extends Clock, so it is itself the single Clock bean.
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[digest.length * 2];
            int i = 0;
            for (byte b : digest) {
                int v = b & 0xFF;
                out[i++] = Character.forDigit(v >>> 4, 16);
                out[i++] = Character.forDigit(v & 0x0F, 16);
            }
            return new String(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sampleTraceparent() {
        // 00-<32 hex trace-id>-<16 hex parent-id>-01
        SecureRandom r = new SecureRandom();
        byte[] traceId = new byte[16];
        byte[] parentId = new byte[8];
        r.nextBytes(traceId);
        r.nextBytes(parentId);
        return "00-" + toHex(traceId) + "-" + toHex(parentId) + "-01";
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            int v = b & 0xFF;
            out[i++] = Character.forDigit(v >>> 4, 16);
            out[i++] = Character.forDigit(v & 0x0F, 16);
        }
        return new String(out);
    }
}
