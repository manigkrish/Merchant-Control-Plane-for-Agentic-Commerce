package com.agenttrust.admin.auth.api;

import com.agenttrust.admin.auth.AdminUserEntity;
import com.agenttrust.admin.auth.AdminUserRepository;
import com.agenttrust.admin.auth.JwtIssuer;
import com.agenttrust.admin.auth.PasswordHasher;
import com.agenttrust.admin.auth.config.AuthProperties;
import com.agenttrust.admin.tenancy.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.agenttrust.admin.auth.api.AuthDtos.LoginRequest;
import static com.agenttrust.admin.auth.api.AuthDtos.LoginResponse;

@RestController
@RequestMapping("/v1/admin/auth")
public class AuthController {

    private final AuthProperties authProperties;
    private final AdminUserRepository adminUserRepository;
    private final PasswordHasher passwordHasher;
    private final JwtIssuer jwtIssuer;

    public AuthController(
            AuthProperties authProperties,
            AdminUserRepository adminUserRepository,
            PasswordHasher passwordHasher,
            JwtIssuer jwtIssuer
    ) {
        this.authProperties = Objects.requireNonNull(authProperties, "authProperties");
        this.adminUserRepository = Objects.requireNonNull(adminUserRepository, "adminUserRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.jwtIssuer = Objects.requireNonNull(jwtIssuer, "jwtIssuer");
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String username = request.username().trim();
        String password = request.password();

        AdminUserEntity user = adminUserRepository
                .findByTenantIdAndUsername(TenantService.PLATFORM_TENANT_ID, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "inactive user");
        }

        boolean ok = passwordHasher.matches(password, user.getPasswordHash());
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }

        List<String> roles = Arrays.asList(user.getRoles());

        String token = jwtIssuer.issueAdminToken(
                user.getUserId().toString(),
                user.getTenantId(),
                roles
        );

        return new LoginResponse(token, "Bearer", authProperties.getJwt().getTtlSeconds());
    }
}
