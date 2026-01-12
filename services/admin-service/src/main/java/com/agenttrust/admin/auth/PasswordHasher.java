package com.agenttrust.admin.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    public PasswordHasher() {
        // Cost factor 10 is a reasonable baseline for a portfolio MVP.
        // We can make this configurable later.
        this.encoder = new BCryptPasswordEncoder(10);
    }

    public String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(passwordHash, "passwordHash");
        return encoder.matches(rawPassword, passwordHash);
    }
}
