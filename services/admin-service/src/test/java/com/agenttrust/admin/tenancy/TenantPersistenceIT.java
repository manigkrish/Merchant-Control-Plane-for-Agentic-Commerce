package com.agenttrust.admin.tenancy;

import com.agenttrust.admin.testsupport.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class TenantPersistenceIT extends PostgresTestContainerSupport {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantService tenantService;

    @Test
    void platformTenant_is_seeded_by_flyway() {
        var platform = tenantRepository.findById(TenantService.PLATFORM_TENANT_ID);
        assertThat(platform).isPresent();
        assertThat(platform.get().getDisplayName()).isEqualTo("Platform");
        assertThat(platform.get().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void createTenant_persists_and_can_be_listed() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantId = "tenant_" + suffix;
        String displayName = "Demo Tenant " + suffix;

        tenantService.createTenant(tenantId, displayName);

        assertThat(tenantRepository.findById(tenantId)).isPresent();

        var all = tenantService.listTenants();
        assertThat(all.stream().anyMatch(t -> t.getTenantId().equals(tenantId))).isTrue();
}

    @Test
    void reserved_platform_tenantId_is_rejected() {
        assertThatThrownBy(() -> tenantService.createTenant("__platform__", "Should Fail"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }
}
