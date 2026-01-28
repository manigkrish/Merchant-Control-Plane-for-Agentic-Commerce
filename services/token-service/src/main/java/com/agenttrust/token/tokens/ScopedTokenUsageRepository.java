package com.agenttrust.token.tokens;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScopedTokenUsageRepository extends JpaRepository<ScopedTokenUsage, Long> {

    List<ScopedTokenUsage> findByTenantIdOrderByUsedAtDesc(String tenantId);

    List<ScopedTokenUsage> findByTokenIdOrderByUsedAtDesc(UUID tokenId);
}
