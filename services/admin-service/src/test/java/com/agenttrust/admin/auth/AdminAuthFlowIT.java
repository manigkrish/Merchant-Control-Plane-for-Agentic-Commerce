package com.agenttrust.admin.auth;

import com.agenttrust.admin.audit.AuditLogEntity;
import com.agenttrust.admin.audit.AuditLogRepository;
import com.agenttrust.admin.audit.AuditService;
import com.agenttrust.admin.testsupport.PostgresTestContainerSupport;
import com.agenttrust.admin.tenancy.TenantService;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = true)
class AdminAuthFlowIT extends PostgresTestContainerSupport {

    private static final String BOOTSTRAP_USER = "admin";
    private static final String BOOTSTRAP_PASS = "change-me-strong";

    private static final String KEY_ROOT = "target/test-keys/admin-jwt-" + UUID.randomUUID();

    @DynamicPropertySource
    static void registerAuthProps(DynamicPropertyRegistry registry) {
        // Enable bootstrap admin creation for this test run
        registry.add("agenttrust.auth.bootstrap-admin.username", () -> BOOTSTRAP_USER);
        registry.add("agenttrust.auth.bootstrap-admin.password", () -> BOOTSTRAP_PASS);

        // Keep test key material out of .local/ and isolate per build/run
        registry.add("agenttrust.auth.jwt.keystore.path", () -> KEY_ROOT);
        registry.add("agenttrust.auth.jwt.ttl-seconds", () -> "900");
        registry.add("agenttrust.auth.jwt.issuer", () -> "agenttrust-admin-test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void auth_rbac_and_audit_tenant_created_strong_assertions() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        String tenantId = "tenant_" + suffix;
        String displayName = "Demo Tenant " + suffix;

        // Explicit headers so we can assert they are persisted into audit_log deterministically.
        String correlationId = "it-" + suffix;
        String traceparent = "00-00000000000000000000000000000001-0000000000000001-01";

        // 1) Without token -> 401 Problem Details
        MvcResult unauthorized = mockMvc.perform(post("/v1/admin/tenants")
                        .header("X-Correlation-Id", correlationId)
                        .header("traceparent", traceparent)
                        .contentType(APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenantId + "\",\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isUnauthorized())
                // Response must include correlation headers (exact echo is not required everywhere, but existence is)
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().exists("traceparent"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON)))
                .andReturn();

        assertProblemDetails(unauthorized, 401, "AUTH_UNAUTHORIZED");

        // 2) Login as bootstrap platform admin -> get JWT
        String adminToken = loginAndGetToken(BOOTSTRAP_USER, BOOTSTRAP_PASS);

        // 3) With platform admin token -> create tenant must succeed (201)
        mockMvc.perform(post("/v1/admin/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Correlation-Id", correlationId)
                        .header("traceparent", traceparent)
                        .contentType(APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + tenantId + "\",\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/admin/tenants/" + tenantId))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.displayName").value(displayName))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 4) Strong audit assertions: fetch latest TENANT_CREATED row for that tenant
        AuditLogEntity audit = auditLogRepository
                .findTopByEventTypeAndTenantIdOrderByOccurredAtDesc(AuditService.EVENT_TENANT_CREATED, tenantId)
                .orElseThrow(() -> new AssertionError("Expected TENANT_CREATED audit row for tenantId=" + tenantId));

        // Actor info must be recorded (platform admin creates tenants)
        assertThat(audit.getActorTenantId()).isEqualTo(TenantService.PLATFORM_TENANT_ID);
        assertThat(audit.getActorSubject()).isNotBlank();

        // Correlation/trace must be persisted exactly as received (we set these explicitly)
        assertThat(audit.getCorrelationId()).isEqualTo(correlationId);
        assertThat(audit.getTraceparent()).isEqualTo(traceparent);

        // Payload JSON must contain tenantId + displayName
        JsonNode payload = audit.getPayloadJson();
        assertThat(payload).as("payload_json must be present").isNotNull();
        assertThat(payload.get("tenantId").asText()).isEqualTo(tenantId);
        assertThat(payload.get("displayName").asText()).isEqualTo(displayName);

        // 5) Create a non-platform admin user (viewer role) -> login -> tenant create must be 403
        String viewerUser = "viewer_" + suffix;
        String viewerPass = "viewer-pass-" + suffix;

        adminUserRepository.save(new AdminUserEntity(
                UUID.randomUUID(),
                TenantService.PLATFORM_TENANT_ID,
                viewerUser,
                passwordHasher.hash(viewerPass),
                new String[]{"viewer"},
                true
        ));

        String viewerToken = loginAndGetToken(viewerUser, viewerPass);

        String blockedTenantId = "tenant_blocked_" + suffix;
        String blockedDisplayName = "Blocked Tenant " + suffix;

        MvcResult forbidden = mockMvc.perform(post("/v1/admin/tenants")
                        .header("Authorization", "Bearer " + viewerToken)
                        .header("X-Correlation-Id", correlationId)
                        .header("traceparent", traceparent)
                        .contentType(APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + blockedTenantId + "\",\"displayName\":\"" + blockedDisplayName + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().exists("traceparent"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON)))
                .andReturn();

        assertProblemDetails(forbidden, 403, "AUTH_FORBIDDEN");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        String loginResponseBody = mockMvc.perform(post("/v1/admin/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(loginResponseBody);
        String token = json.get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private void assertProblemDetails(MvcResult result, int expectedStatus, String expectedErrorCode) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);

        assertThat(json.get("status").asInt()).isEqualTo(expectedStatus);

        JsonNode errorCodeNode = json.get("errorCode");
        assertThat(errorCodeNode).as("ProblemDetails.errorCode must exist").isNotNull();
        assertThat(errorCodeNode.asText()).isEqualTo(expectedErrorCode);
    }
}
