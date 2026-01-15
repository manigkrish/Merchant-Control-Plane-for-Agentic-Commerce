package com.agenttrust.gateway.tenancy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public final class HostTenantDeriver {

  private final TenancyProperties props;

  public HostTenantDeriver(TenancyProperties props) {
    this.props = props;
  }

  public String deriveTenantId(HttpServletRequest request) {
    String host = request.getHeader("Host");
    if (host == null || host.isBlank()) {
      host = request.getServerName();
    }
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Missing Host header; cannot derive tenant.");
    }

    String normalized = normalizeHost(host);
    String tenantId = props.getHostToTenant().get(normalized);

    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("Unknown tenant for host: " + normalized);
    }

    return tenantId;
  }

  private static String normalizeHost(String host) {
    String h = host.trim().toLowerCase(Locale.ROOT);
    int colon = h.indexOf(':');
    if (colon > 0) {
      return h.substring(0, colon);
    }
    return h;
  }
}
