package com.agenttrust.attestation.replay;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ReplayProtectionServiceTest {

  @Test
  void recordNonce_blankInputs_returnsInvalidInput() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ReplayProtectionService svc = new ReplayProtectionService(redis, "replay", 480);

    assertEquals(ReplayProtectionService.Result.INVALID_INPUT, svc.recordNonce(null, "k", "n", 10));
    assertEquals(ReplayProtectionService.Result.INVALID_INPUT, svc.recordNonce("t", null, "n", 10));
    assertEquals(ReplayProtectionService.Result.INVALID_INPUT, svc.recordNonce("t", "k", null, 10));
    assertEquals(ReplayProtectionService.Result.INVALID_INPUT, svc.recordNonce(" ", "k", "n", 10));
  }

  @Test
  void recordNonce_firstSeen_setsNxWithTtl_andReturnsFirstSeen() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> ops = mock(ValueOperations.class);

    when(redis.opsForValue()).thenReturn(ops);
    when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(Boolean.TRUE);

    ReplayProtectionService svc = new ReplayProtectionService(redis, "replay", 480);
    ReplayProtectionService.Result result = svc.recordNonce("tenantA", "key1", "nonce1", 120);

    assertEquals(ReplayProtectionService.Result.FIRST_SEEN, result);

    ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Duration> ttlCap = ArgumentCaptor.forClass(Duration.class);

    verify(ops).setIfAbsent(keyCap.capture(), eq("1"), ttlCap.capture());

    assertEquals("replay:tenantA:key1:nonce1", keyCap.getValue());
    assertEquals(Duration.ofSeconds(120), ttlCap.getValue());
  }

  @Test
  void recordNonce_existingKey_returnsReplayDetected() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> ops = mock(ValueOperations.class);

    when(redis.opsForValue()).thenReturn(ops);
    when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(Boolean.FALSE);

    ReplayProtectionService svc = new ReplayProtectionService(redis, "replay", 480);
    ReplayProtectionService.Result result = svc.recordNonce("tenantA", "key1", "nonce1", 120);

    assertEquals(ReplayProtectionService.Result.REPLAY_DETECTED, result);
  }

  @Test
  void recordNonce_nullReturnFromRedis_treatedAsUnavailable() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> ops = mock(ValueOperations.class);

    when(redis.opsForValue()).thenReturn(ops);
    when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(null);

    ReplayProtectionService svc = new ReplayProtectionService(redis, "replay", 480);
    ReplayProtectionService.Result result = svc.recordNonce("tenantA", "key1", "nonce1", 120);

    assertEquals(ReplayProtectionService.Result.UNAVAILABLE, result);
  }

  @Test
  void recordNonce_exceptionFromRedis_treatedAsUnavailable() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenThrow(new RuntimeException("boom"));

    ReplayProtectionService svc = new ReplayProtectionService(redis, "replay", 480);
    ReplayProtectionService.Result result = svc.recordNonce("tenantA", "key1", "nonce1", 120);

    assertEquals(ReplayProtectionService.Result.UNAVAILABLE, result);
  }
}
