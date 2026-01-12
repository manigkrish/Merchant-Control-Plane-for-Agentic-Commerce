package com.agenttrust.admin.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
        // utility holder
    }

    public record LoginRequest(
            @NotBlank
            @Size(min = 3, max = 128)
            String username,

            @NotBlank
            @Size(min = 8, max = 256)
            String password
    ) { }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds
    ) { }
}
