package com.agenttrust.admin.tenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class TenantDtos {

    private TenantDtos() {
        // utility holder
    }

    public record CreateTenantRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$",
                     message = "tenantId must be 3-64 chars and match [A-Za-z0-9][A-Za-z0-9_-]*")
            String tenantId,

            @NotBlank
            @Size(max = 128)
            String displayName
    ) { }

    public record TenantResponse(
            String tenantId,
            String displayName,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) { }
}
