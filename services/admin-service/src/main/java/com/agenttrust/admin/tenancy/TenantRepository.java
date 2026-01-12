package com.agenttrust.admin.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, String> {
    boolean existsByDisplayName(String displayName);
}
