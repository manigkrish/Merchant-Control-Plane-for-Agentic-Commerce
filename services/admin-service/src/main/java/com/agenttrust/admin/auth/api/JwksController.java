package com.agenttrust.admin.auth.api;

import com.agenttrust.admin.auth.keys.JwtRsaKeyManager;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class JwksController {

    private final JwtRsaKeyManager keyManager;

    public JwksController(JwtRsaKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jwks() {
        // Cacheable but short-lived for MVP; key rotation later will require careful cache behavior.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(keyManager.jwksJson());
    }
}
