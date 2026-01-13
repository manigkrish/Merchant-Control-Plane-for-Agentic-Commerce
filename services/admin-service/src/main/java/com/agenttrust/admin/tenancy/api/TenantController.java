package com.agenttrust.admin.tenancy.api;

import com.agenttrust.admin.audit.AuditService;
import com.agenttrust.admin.tenancy.TenantEntity;
import com.agenttrust.admin.tenancy.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import static com.agenttrust.admin.tenancy.api.TenantDtos.CreateTenantRequest;
import static com.agenttrust.admin.tenancy.api.TenantDtos.TenantResponse;

@RestController
@RequestMapping("/v1/admin/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final AuditService auditService;

    public TenantController(TenantService tenantService, AuditService auditService) {
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTenantRequest request
    ) {
        TenantEntity created = tenantService.createTenant(request.tenantId(), request.displayName());

        // Audit: best-effort identity extraction from JWT (should exist because endpoint is ROLE_PLATFORM_ADMIN)
        String actorSubject = jwt != null ? jwt.getSubject() : null;
        String actorTenantId = jwt != null ? jwt.getClaimAsString("tenantId") : null;

        auditService.recordTenantCreated(
                actorTenantId,
                actorSubject,
                correlationId,
                traceparent,
                created.getTenantId(),
                created.getDisplayName()
        );

        URI location = URI.create("/v1/admin/tenants/" + created.getTenantId());
        return ResponseEntity.created(location).body(toResponse(created));
    }

    @GetMapping
    public ResponseEntity<List<TenantResponse>> listTenants() {
        List<TenantResponse> tenants = tenantService.listTenants()
                .stream()
                .map(TenantController::toResponse)
                .toList();
        return ResponseEntity.ok(tenants);
    }

    private static TenantResponse toResponse(TenantEntity entity) {
        return new TenantResponse(
                entity.getTenantId(),
                entity.getDisplayName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
