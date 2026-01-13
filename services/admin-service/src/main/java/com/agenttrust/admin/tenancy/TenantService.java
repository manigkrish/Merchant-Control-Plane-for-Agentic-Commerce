package com.agenttrust.admin.tenancy;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class TenantService {

    /**
     * Sprint 2 decision: reserved platform tenant ID.
     * This value is seeded via Flyway migration and must be treated as non-creatable by API callers.
     */
    public static final String PLATFORM_TENANT_ID = "__platform__";

    /**
     * Tenant IDs for customer tenants must be 3-64 chars and start with an alphanumeric.
     * Note: PLATFORM_TENANT_ID is reserved and handled explicitly before this validation.
     */
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{2,63}$");

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
    }

    @Transactional
    public TenantEntity createTenant(String tenantId, String displayName) {
        String trimmedTenantId = requireTrimmed(tenantId, "tenantId");

        // Reserved ID must be rejected with a reserved-specific message (even though it does not match the normal pattern).
        if (PLATFORM_TENANT_ID.equals(trimmedTenantId)) {
            throw new IllegalArgumentException("tenantId is reserved");
        }

        String normalizedTenantId = validateTenantId(trimmedTenantId);
        String normalizedDisplayName = normalizeDisplayName(displayName);

        if (tenantRepository.existsById(normalizedTenantId)) {
            throw new IllegalArgumentException("tenantId already exists");
        }

        if (tenantRepository.existsByDisplayName(normalizedDisplayName)) {
            throw new IllegalArgumentException("displayName already exists");
        }

        TenantEntity entity = new TenantEntity(normalizedTenantId, normalizedDisplayName, "ACTIVE");
        return tenantRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<TenantEntity> listTenants() {
        return tenantRepository.findAll(Sort.by(Sort.Direction.ASC, "tenantId"));
    }

    private String validateTenantId(String tenantId) {
        if (!TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("tenantId must match pattern " + TENANT_ID_PATTERN.pattern());
        }
        return tenantId;
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            throw new IllegalArgumentException("displayName is required");
        }
        String v = displayName.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (v.length() > 128) {
            throw new IllegalArgumentException("displayName must be <= 128 characters");
        }
        return v;
    }

    private String requireTrimmed(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
