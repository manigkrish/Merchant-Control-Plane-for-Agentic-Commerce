package com.agenttrust.attestation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AttestationProperties.class)
public class AttestationConfiguration {
  // Intentionally empty: enables strongly-typed configuration binding.
}
