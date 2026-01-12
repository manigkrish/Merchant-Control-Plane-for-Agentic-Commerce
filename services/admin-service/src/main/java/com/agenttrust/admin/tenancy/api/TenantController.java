package com.agenttrust.admin.tenancy.api;

import com.agenttrust.admin.tenancy.TenantEntity;
import com.agenttrust.admin.tenancy.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

import static com.agenttrust.admin.tenancy.api.TenantDtos.CreateTenantRequest;
import static com.agenttrust.admin.tenancy.api.TenantDtos.TenantResponse;

@RestController
@RequestMapping("/v1/admin/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantEntity created = tenantService.createTenant(request.tenantId(), request.displayName());
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
