package com.agenttrust.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class AuditService {

    public static final String EVENT_TENANT_CREATED = "TENANT_CREATED";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public void recordTenantCreated(
            String actorTenantId,
            String actorSubject,
            String correlationId,
            String traceparent,
            String tenantId,
            String displayName
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(displayName, "displayName");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("tenantId", tenantId);
        payload.put("displayName", displayName);

        AuditLogEntity entry = new AuditLogEntity(
                UUID.randomUUID(),
                EVENT_TENANT_CREATED,
                tenantId,
                blankToNull(actorTenantId),
                blankToNull(actorSubject),
                blankToNull(correlationId),
                blankToNull(traceparent),
                payload
        );

        auditLogRepository.save(entry);
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
