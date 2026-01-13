package com.agenttrust.admin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "actor_tenant_id")
    private String actorTenantId;

    @Column(name = "actor_subject")
    private String actorSubject;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "traceparent")
    private String traceparent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode payloadJson;

    protected AuditLogEntity() {
        // JPA
    }

    public AuditLogEntity(
            UUID auditId,
            String eventType,
            String tenantId,
            String actorTenantId,
            String actorSubject,
            String correlationId,
            String traceparent,
            JsonNode payloadJson
    ) {
        this.auditId = auditId;
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.actorTenantId = actorTenantId;
        this.actorSubject = actorSubject;
        this.correlationId = correlationId;
        this.traceparent = traceparent;
        this.payloadJson = payloadJson;
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (auditId == null) {
            auditId = UUID.randomUUID();
        }
        if (payloadJson == null) {
            payloadJson = JsonNodeFactory.instance.objectNode();
        }
    }

    public UUID getAuditId() {
        return auditId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getActorTenantId() {
        return actorTenantId;
    }

    public String getActorSubject() {
        return actorSubject;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceparent() {
        return traceparent;
    }

    public JsonNode getPayloadJson() {
        return payloadJson;
    }
}
