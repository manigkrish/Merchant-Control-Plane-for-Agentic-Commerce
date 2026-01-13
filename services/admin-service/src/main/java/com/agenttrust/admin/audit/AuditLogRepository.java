package com.agenttrust.admin.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    boolean existsByEventTypeAndTenantId(String eventType, String tenantId);

    Optional<AuditLogEntity> findTopByEventTypeAndTenantIdOrderByOccurredAtDesc(String eventType, String tenantId);
}
