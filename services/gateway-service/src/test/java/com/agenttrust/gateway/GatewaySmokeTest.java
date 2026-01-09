package com.agenttrust.gateway;

import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = true)
class GatewaySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthz_returns_ok() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("gateway-service"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().exists("traceparent"));
    }

    @Test
    void problemDetails_is_returned_for_illegalArgumentException() throws Exception {
        MvcResult result = mockMvc.perform(get("/__debug/problem"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().exists("traceparent"))
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType)
                .as("Content-Type should be RFC 9457 media type")
                .isNotNull()
                .startsWith(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        String traceparent = result.getResponse().getHeader("traceparent");
        assertThat(correlationId).isNotBlank();
        assertThat(traceparent).isNotBlank();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isEqualTo("Bad request");
        assertThat(body.get("type").asText()).isEqualTo("https://agenttrust.dev/problems/bad-request");
        assertThat(body.get("errorCode").asText()).isEqualTo("BAD_REQUEST");

        // Correlation invariants:
        assertThat(body.get("requestId").asText()).isEqualTo(correlationId);

        // traceparent format: 00-<traceId>-<spanId>-<flags>
        String[] parts = traceparent.split("-");
        assertThat(parts).hasSize(4);
        String traceIdFromHeader = parts[1];

        assertThat(body.get("traceId").asText()).isEqualTo(traceIdFromHeader);
    }

    @Test
    void correlationId_is_preserved_when_client_provides_one() throws Exception {
        String clientCorrelationId = "client-corr-123";

        MvcResult result = mockMvc.perform(get("/healthz").header("X-Correlation-Id", clientCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isEqualTo(clientCorrelationId);
    }
}
