package com.agenttrust.admin;

<<<<<<< HEAD
import com.agenttrust.admin.testsupport.PostgresTestContainerSupport;
=======
>>>>>>> main
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = true)
<<<<<<< HEAD
class AdminSmokeTest extends PostgresTestContainerSupport {
=======
class AdminSmokeTest {
>>>>>>> main

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthz_returns_ok() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("admin-service"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().exists("traceparent"));
    }

    @Test
    void readyz_returns_ready() throws Exception {
        mockMvc.perform(get("/readyz"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.service").value("admin-service"))
<<<<<<< HEAD
                .andExpect(jsonPath("$.checks.database").value("up"))
=======
>>>>>>> main
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(header().exists("traceparent"));
    }
}
