package com.agenttrust.admin.auth;

import com.agenttrust.admin.auth.config.AuthProperties;
import com.agenttrust.admin.tenancy.TenantService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final AuthProperties authProperties;
    private final AdminUserRepository adminUserRepository;
    private final PasswordHasher passwordHasher;

    public BootstrapAdminInitializer(
            AuthProperties authProperties,
            AdminUserRepository adminUserRepository,
            PasswordHasher passwordHasher
    ) {
        this.authProperties = Objects.requireNonNull(authProperties, "authProperties");
        this.adminUserRepository = Objects.requireNonNull(adminUserRepository, "adminUserRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = safeTrim(authProperties.getBootstrapAdmin().getUsername());
        String password = safeTrim(authProperties.getBootstrapAdmin().getPassword());

        if (username == null || password == null) {
            // No bootstrap credentials supplied; skip.
            return;
        }

        boolean exists = adminUserRepository.existsByTenantIdAndUsername(TenantService.PLATFORM_TENANT_ID, username);
        if (exists) {
            return;
        }

        String passwordHash = passwordHasher.hash(password);

        AdminUserEntity user = new AdminUserEntity(
                UUID.randomUUID(),
                TenantService.PLATFORM_TENANT_ID,
                username,
                passwordHash,
                new String[]{"platform_admin"},
                true
        );

        adminUserRepository.save(user);
    }

    private static String safeTrim(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
