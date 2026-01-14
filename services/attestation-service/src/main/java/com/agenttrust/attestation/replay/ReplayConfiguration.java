package com.agenttrust.attestation.replay;

import com.agenttrust.attestation.config.AttestationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ReplayConfiguration {

  @Bean
  public ReplayProtectionService replayProtectionService(StringRedisTemplate redisTemplate,
                                                         AttestationProperties props) {
    String keyPrefix = props.getReplay().getKeyPrefix();
    int defaultTtlSeconds = props.getReplay().getDefaultTtlSeconds();
    return new ReplayProtectionService(redisTemplate, keyPrefix, defaultTtlSeconds);
  }
}
