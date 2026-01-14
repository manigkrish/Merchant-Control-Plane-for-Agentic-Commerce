package com.agenttrust.attestation.replay;

import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

public final class ReplayProtectionService {

  private static final Logger log = LoggerFactory.getLogger(ReplayProtectionService.class);

  public enum Result {
    FIRST_SEEN,
    REPLAY_DETECTED,
    UNAVAILABLE,
    INVALID_INPUT
  }

  private final StringRedisTemplate redisTemplate;
  private final String keyPrefix;
  private final int defaultTtlSeconds;

  public ReplayProtectionService(StringRedisTemplate redisTemplate, String keyPrefix, int defaultTtlSeconds) {
    this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "replay" : keyPrefix;
    this.defaultTtlSeconds = defaultTtlSeconds > 0 ? defaultTtlSeconds : 480;
  }

  public Result recordNonce(String tenantId, String keyId, String nonce, Integer ttlSecondsOverride) {
    if (isBlank(tenantId) || isBlank(keyId) || isBlank(nonce)) {
      return Result.INVALID_INPUT;
    }

    int ttlSeconds = ttlSecondsOverride != null && ttlSecondsOverride > 0 ? ttlSecondsOverride : defaultTtlSeconds;
    String redisKey = buildKey(tenantId, keyId, nonce);

    try {
      ValueOperations<String, String> ops = redisTemplate.opsForValue();
      Boolean wasSet = ops.setIfAbsent(redisKey, "1", Duration.ofSeconds(ttlSeconds));

      if (Boolean.TRUE.equals(wasSet)) {
        return Result.FIRST_SEEN;
      }
      if (Boolean.FALSE.equals(wasSet)) {
        return Result.REPLAY_DETECTED;
      }
      return Result.UNAVAILABLE;
    } catch (Exception ex) {
      log.warn("Replay cache unavailable (redis). Failing closed.", ex);
      return Result.UNAVAILABLE;
    }
  }

  private String buildKey(String tenantId, String keyId, String nonce) {
    return keyPrefix + ":" + tenantId + ":" + keyId + ":" + nonce;
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
