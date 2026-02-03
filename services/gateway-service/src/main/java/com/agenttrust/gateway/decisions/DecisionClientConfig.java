package com.agenttrust.gateway.decisions;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class DecisionClientConfig {

    @Bean
    @ConfigurationProperties(prefix = "agenttrust.gateway.decision")
    DecisionClientProperties decisionClientProperties() {
        return new DecisionClientProperties();
    }

    @Bean
    RestClient decisionRestClient(DecisionClientProperties props) {
        return buildRestClient(props.baseUrl(), props.connectTimeout(), props.readTimeout());
    }

    private static RestClient buildRestClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory jdk = new JdkClientHttpRequestFactory(httpClient);
        jdk.setReadTimeout(readTimeout);

        // Buffering ensures we can reliably read downstream Problem Details bodies on error responses.
        ClientHttpRequestFactory rf = new BufferingClientHttpRequestFactory(jdk);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .build();
    }

    public static final class DecisionClientProperties {

        /**
         * Internal base URL for decision-service.
         * Local compose example: http://decision-service:8083
         */
        private String baseUrl = "http://decision-service:8083";

        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);

        public String baseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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
