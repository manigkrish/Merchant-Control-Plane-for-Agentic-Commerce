package com.agenttrust.token.health;

import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping(path = "/healthz", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> healthz() {
        // Liveness: process is up.
        return Map.of("status", "ok");
    }

    @GetMapping(path = "/readyz", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> readyz() {
        // Readiness: service can reach its required dependencies (DB).
        try {
            // Lightweight connectivity check; avoids ORM overhead.
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (one == null || one != 1) {
                return ResponseEntity.status(503)
                        .body(Map.of("status", "not_ready", "reason", "db_unexpected_response"));
            }
            return ResponseEntity.ok(Map.of("status", "ready"));
        } catch (DataAccessException ex) {
            return ResponseEntity.status(503)
                    .body(Map.of("status", "not_ready", "reason", "db_unreachable"));
        }
    }
}
