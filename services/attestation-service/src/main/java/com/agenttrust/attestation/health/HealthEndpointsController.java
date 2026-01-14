package com.agenttrust.attestation.health;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthEndpointsController {

  private static final Logger log = LoggerFactory.getLogger(HealthEndpointsController.class);

  private final StringRedisTemplate redisTemplate;

  public HealthEndpointsController(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @GetMapping("/healthz")
  public Map<String, Object> healthz() {
    return Map.of(
        "status", "ok",
        "service", "attestation-service"
    );
  }

  @GetMapping("/readyz")
  public ResponseEntity<Map<String, Object>> readyz() {
    try {
      // Force the RedisCallback overload to avoid ambiguity with SessionCallback.
      String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());

      if (!"PONG".equalsIgnoreCase(pong)) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "status", "not_ready",
            "dependency", "redis",
            "detail", "Unexpected Redis PING response"
        ));
      }

      return ResponseEntity.ok(Map.of(
          "status", "ready",
          "dependency", "redis"
      ));
    } catch (Exception ex) {
      log.warn("Readiness check failed: Redis not reachable.", ex);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
          "status", "not_ready",
          "dependency", "redis",
          "detail", "Redis not reachable"
      ));
    }
  }
}
