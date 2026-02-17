package com.agenttrust.decision.health;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
public class HealthController {

    private final StringRedisTemplate redisTemplate;

    public HealthController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping(path = "/healthz", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> healthz() {
        // Liveness: process is up.
        return Map.of("status", "ok");
    }

    @GetMapping(path = "/readyz", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> readyz() {
        // Readiness: decision-service requires Redis for idempotency.
        try {
            String key = "decision-service:readyz";
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(5));
            String val = redisTemplate.opsForValue().get(key);
            if (!"1".equals(val)) {
                return ResponseEntity.status(503)
                        .body(Map.of("status", "not_ready", "reason", "redis_unexpected_response"));
            }
            return ResponseEntity.ok(Map.of("status", "ready"));
        } catch (DataAccessException ex) {
            return ResponseEntity.status(503)
                    .body(Map.of("status", "not_ready", "reason", "redis_unreachable"));
        }
    }
}
