package com.agenttrust.decision.clients;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ClientsConfig {

    @Bean
    @ConfigurationProperties(prefix = "agenttrust.decision.clients")
    DecisionClientsProperties decisionClientsProperties() {
        return new DecisionClientsProperties();
    }

    @Bean
    RestClient attestationRestClient(DecisionClientsProperties props) {
        return buildRestClient(props.attestationBaseUrl(), props.connectTimeout(), props.readTimeout());
    }

    @Bean
    RestClient tokenRestClient(DecisionClientsProperties props) {
        return buildRestClient(props.tokenBaseUrl(), props.connectTimeout(), props.readTimeout());
    }

    private static RestClient buildRestClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) connectTimeout.toMillis());
        rf.setReadTimeout((int) readTimeout.toMillis());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .build();
    }

    /**
     * Properties holder for internal service client config.
     */
    public static final class DecisionClientsProperties {
        /**
         * Example: http://attestation-service:8082
         */
        private String attestationBaseUrl = "http://attestation-service:8082";

        /**
         * Example: http://token-service:8084
         */
        private String tokenBaseUrl = "http://token-service:8084";

        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);

        public String attestationBaseUrl() {
            return attestationBaseUrl;
        }

        public void setAttestationBaseUrl(String attestationBaseUrl) {
            this.attestationBaseUrl = attestationBaseUrl;
        }

        public String tokenBaseUrl() {
            return tokenBaseUrl;
        }

        public void setTokenBaseUrl(String tokenBaseUrl) {
            this.tokenBaseUrl = tokenBaseUrl;
        }

        public Duration connectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration readTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}
