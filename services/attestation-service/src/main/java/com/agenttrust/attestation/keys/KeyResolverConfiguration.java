package com.agenttrust.attestation.keys;

import com.agenttrust.attestation.config.AttestationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeyResolverConfiguration {

  @Bean
  public PublicKeyResolver publicKeyResolver(AttestationProperties props) {
    return new YamlPublicKeyResolver(props);
  }
}
