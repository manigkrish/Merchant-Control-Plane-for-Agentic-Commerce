package com.agenttrust.gateway.attestation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.agenttrust.gateway.api.AgentVerifyController;
import com.agenttrust.gateway.attestation.client.AttestationClientDtos;
import com.agenttrust.gateway.attestation.client.AttestationServiceClient;
import com.agenttrust.gateway.attestation.client.AttestationServiceClient.AttestationServiceClientException;
import com.agenttrust.gateway.tenancy.HostTenantDeriver;
import com.agenttrust.gateway.tenancy.TenancyProperties;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AgentVerifyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GatewayAttestationDelegationTest.TestBeans.class)
class GatewayAttestationDelegationTest {

  private static final String HOST = "merchant.local";
  private static final String TENANT_ID = "__platform__";

  private static final String SIG_INPUT =
      "sig1=(\"@authority\" \"@path\" \"@signature-params\");created=1;expires=2;keyid=\"k\";alg=\"ed25519\";nonce=\"n\";tag=\"t\"";
  private static final String SIG = "sig1=:ZmFrZQ==:";

  @MockBean
  AttestationServiceClient attestationClient;

  @Autowired
  MockMvc mvc;

  @Test
  void verify_delegatesToAttestationService_andPassesTenantAndHeaders() throws Exception {
    // Do NOT mock VerifyResponse (often a record/final type). Return a real instance.
    Mockito.when(attestationClient.verify(any(AttestationClientDtos.VerifyRequest.class), any(HttpServletRequest.class)))
        .thenReturn(new AttestationClientDtos.VerifyResponse(true));

    mvc.perform(
            post("/v1/agent/verify")
                .header("Host", HOST)
                .header("Signature-Input", SIG_INPUT)
                .header("Signature", SIG)
                .header("X-Correlation-Id", "cid-123")
                .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.verified").value(true));

    ArgumentCaptor<AttestationClientDtos.VerifyRequest> reqCaptor =
        ArgumentCaptor.forClass(AttestationClientDtos.VerifyRequest.class);
    ArgumentCaptor<HttpServletRequest> httpReqCaptor =
        ArgumentCaptor.forClass(HttpServletRequest.class);

    verify(attestationClient).verify(reqCaptor.capture(), httpReqCaptor.capture());

    AttestationClientDtos.VerifyRequest verifyReq = reqCaptor.getValue();
    assertNotNull(verifyReq);

    assertAll(
        () -> assertEquals("POST", verifyReq.method()),
        () -> assertEquals(HOST, verifyReq.authority()),
        () -> assertEquals("/v1/agent/verify", verifyReq.path()),
        () -> assertEquals(TENANT_ID, verifyReq.tenantId()),
        () -> assertEquals(SIG_INPUT, verifyReq.signatureInput()),
        () -> assertEquals(SIG, verifyReq.signature())
    );

    HttpServletRequest forwardedIncoming = httpReqCaptor.getValue();
    assertNotNull(forwardedIncoming);
    assertEquals("cid-123", forwardedIncoming.getHeader("X-Correlation-Id"));
    assertNotNull(forwardedIncoming.getHeader("traceparent"));
  }

  @Test
  void verify_passesThroughProblemDetails_fromAttestationService() throws Exception {
    String problemBody =
        "{\"type\":\"https://agenttrust.dev/problems/attestation-failed\"," +
            "\"title\":\"Attestation verification failed\"," +
            "\"status\":401," +
            "\"detail\":\"invalid signature\"," +
            "\"instance\":\"/v1/attestations/verify\"," +
            "\"errorCode\":\"ATTESTATION_INVALID_SIGNATURE\"}";

    Mockito.when(attestationClient.verify(any(AttestationClientDtos.VerifyRequest.class), any(HttpServletRequest.class)))
        .thenThrow(new AttestationServiceClientException(
            401,
            MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON),
            problemBody
        ));

    // Controller mapping produces application/json. If we send Accept only application/problem+json,
    // Spring can return 406 without invoking the controller. So accept BOTH.
    mvc.perform(
            post("/v1/agent/verify")
                .header("Host", HOST)
                .header("Signature-Input", SIG_INPUT)
                .header("Signature", SIG)
                .accept(MediaType.APPLICATION_JSON, MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON))
        )
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf(ProblemMediaTypes.APPLICATION_PROBLEM_JSON)))
        .andExpect(jsonPath("$.errorCode").value("ATTESTATION_INVALID_SIGNATURE"));
  }

  @TestConfiguration
  static class TestBeans {

    @Bean
    TenancyProperties tenancyProperties() {
      TenancyProperties p = new TenancyProperties();
      Map<String, String> map = new HashMap<>();
      map.put(HOST, TENANT_ID);
      p.setHostToTenant(map);
      return p;
    }

    @Bean
    HostTenantDeriver hostTenantDeriver(TenancyProperties props) {
      return new HostTenantDeriver(props);
    }
  }
}
