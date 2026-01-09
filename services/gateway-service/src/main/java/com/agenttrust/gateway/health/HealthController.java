package com.agenttrust.gateway.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthz() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "gateway-service",
                "time", Instant.now().toString()
        ));
    }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> readyz() {
        // Sprint 1: readiness means the process is up and serving HTTP.
        // Later sprints will add checks for DB/Redis/Kafka connectivity.
        return ResponseEntity.ok(Map.of(
                "status", "ready",
                "service", "gateway-service",
                "time", Instant.now().toString()
        ));
    }
}
