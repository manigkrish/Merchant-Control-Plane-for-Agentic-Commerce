package com.agenttrust.decision.clients;

import com.agenttrust.decision.config.IdempotencyKeySupport;
import com.agenttrust.decision.idempotency.RequestHashComputer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientsWiringConfig {

    @Bean
    AttestationClient attestationClient(RestClient attestationRestClient, IdempotencyKeySupport keySupport) {
        return new AttestationClient(attestationRestClient, keySupport);
    }

    @Bean
    TokenClient tokenClient(RestClient tokenRestClient) {
        return new TokenClient(tokenRestClient);
    }

    @Bean
    RequestHashComputer requestHashComputer(IdempotencyKeySupport keySupport) {
        return new RequestHashComputer(keySupport);
    }
}
