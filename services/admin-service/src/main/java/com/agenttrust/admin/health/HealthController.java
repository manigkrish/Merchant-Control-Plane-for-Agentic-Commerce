package com.agenttrust.admin.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthz() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "admin-service");
        body.put("time", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> readyz() {
        boolean dbUp = isDatabaseUp();

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", dbUp ? "up" : "down");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "ready" : "not_ready");
        body.put("service", "admin-service");
        body.put("checks", checks);
        body.put("time", OffsetDateTime.now(ZoneOffset.UTC).toString());

        if (!dbUp) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private boolean isDatabaseUp() {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement()) {
            s.execute("SELECT 1");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
