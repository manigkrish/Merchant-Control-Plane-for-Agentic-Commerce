package com.agenttrust.admin.tenancy;

import com.agenttrust.admin.testsupport.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
        tenantService.createTenant("tenant_demo", "Demo Tenant");

        assertThat(tenantRepository.findById("tenant_demo")).isPresent();

        var all = tenantService.listTenants();
        assertThat(all.stream().anyMatch(t -> t.getTenantId().equals("tenant_demo"))).isTrue();
    }

    @Test
    void reserved_platform_tenantId_is_rejected() {
        assertThatThrownBy(() -> tenantService.createTenant("__platform__", "Should Fail"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }
}
