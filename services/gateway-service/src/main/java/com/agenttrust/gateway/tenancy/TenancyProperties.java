package com.agenttrust.gateway.tenancy;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway tenancy configuration.
 *
 * Security posture:
 * - tenantId is derived by trusted gateway logic
 * - never trust tenantId from request bodies
 *
 * For Sprint 3, we derive tenantId from the incoming Host header using a static map.
 * This is safe for MVP if key material is tenant-scoped and the verifier enforces tenant/key binding.
 */
@ConfigurationProperties(prefix = "agenttrust.gateway.tenancy")
public class TenancyProperties {

  /**
   * Map of Host/@authority -> tenantId.
   *
   * Example:
   *   merchant-a.local -> tenantA
   *   merchant-b.local -> tenantB
   */
  private Map<String, String> hostToTenant = new HashMap<>();

  public Map<String, String> getHostToTenant() {
    return hostToTenant;
  }

  public void setHostToTenant(Map<String, String> hostToTenant) {
    this.hostToTenant = (hostToTenant == null) ? new HashMap<>() : new HashMap<>(hostToTenant);
  }
}
