package com.agenttrust.admin.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUserEntity, UUID> {

    Optional<AdminUserEntity> findByTenantIdAndUsername(String tenantId, String username);

    boolean existsByTenantIdAndUsername(String tenantId, String username);
}
