package com.agenttrust.gateway.attestation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agenttrust.gateway.attestation")
public class AttestationClientProperties {

  /**
   * Base URL for the internal attestation-service.
   * Example: http://attestation-service:8082
   */
  private String baseUrl = "http://attestation-service:8082";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
