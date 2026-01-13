package com.agenttrust.admin.security;

import com.agenttrust.admin.auth.config.AuthProperties;
import com.agenttrust.admin.auth.keys.JwtRsaKeyManager;
import com.agenttrust.platform.web.problem.ProblemDetails;
import com.agenttrust.platform.web.problem.ProblemMediaTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class AdminSecurityConfiguration {

  private static final URI TYPE_UNAUTHORIZED = URI.create("https://agenttrust.dev/problems/unauthorized");
  private static final URI TYPE_FORBIDDEN = URI.create("https://agenttrust.dev/problems/forbidden");

  // Must match RequestCorrelationFilter + GlobalProblemHandler attribute keys
  private static final String ATTR_TRACE_ID = "agenttrust.traceId";
  private static final String ATTR_REQUEST_ID = "agenttrust.requestId";
  private static final String ATTR_TENANT_ID = "agenttrust.tenantId";

  @Bean
  SecurityFilterChain adminSecurityFilterChain(
      HttpSecurity http,
      AuthenticationEntryPoint problemDetailsAuthenticationEntryPoint,
      AccessDeniedHandler problemDetailsAccessDeniedHandler
  ) throws Exception {
    http
        // Stateless API (no cookies, no server-side sessions)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // Disable browser-style auth mechanisms; use JWT only
        .csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())

        // RFC 9457 Problem Details for 401/403
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(problemDetailsAuthenticationEntryPoint)
            .accessDeniedHandler(problemDetailsAccessDeniedHandler)
        )

        // Authorization rules
        .authorizeHttpRequests(auth -> auth
            // Always-public endpoints
            .requestMatchers("/healthz", "/readyz").permitAll()
            .requestMatchers("/.well-known/jwks.json").permitAll()
            .requestMatchers(HttpMethod.POST, "/v1/admin/auth/login").permitAll()
            .requestMatchers("/actuator/health/**").permitAll()

            // Tenant management is platform-admin only
            .requestMatchers("/v1/admin/tenants", "/v1/admin/tenants/**").hasRole("PLATFORM_ADMIN")

            // Other admin APIs require a valid JWT
            .requestMatchers("/v1/admin/**").authenticated()

            // Secure default
            .anyRequest().denyAll()
        )

        // JWT resource server verification
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        );

    return http.build();
  }

  /**
   * Provide a JwtDecoder so the app can start without requiring jwk-set-uri.
   * Admin-service validates tokens using its local public key material.
   */
  @Bean
  JwtDecoder jwtDecoder(JwtRsaKeyManager keyManager, AuthProperties authProperties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyManager.publicKey()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(authProperties.getJwt().getIssuer()));
    return decoder;
  }

  /**
   * Map our custom claim "roles": ["platform_admin", ...]
   * to Spring Security authorities: ROLE_PLATFORM_ADMIN, etc.
   */
  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      Object rolesClaim = jwt.getClaims().get("roles");
      if (rolesClaim instanceof Collection<?> col) {
        List<String> roles = col.stream()
            .map(Object::toString)
            .toList();

        return roles.stream()
            .map(r -> "ROLE_" + r.toUpperCase())
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toSet());
      }
      return List.of();
    });
    return converter;
  }

  @Bean
  AuthenticationEntryPoint problemDetailsAuthenticationEntryPoint(ObjectMapper objectMapper) {
    return (request, response, authException) -> writeProblem(
        request,
        response,
        objectMapper,
        HttpStatus.UNAUTHORIZED,
        TYPE_UNAUTHORIZED,
        "Unauthorized",
        "Missing or invalid access token",
        "AUTH_UNAUTHORIZED"
    );
  }

  @Bean
  AccessDeniedHandler problemDetailsAccessDeniedHandler(ObjectMapper objectMapper) {
    return (request, response, accessDeniedException) -> writeProblem(
        request,
        response,
        objectMapper,
        HttpStatus.FORBIDDEN,
        TYPE_FORBIDDEN,
        "Forbidden",
        "You do not have permission to perform this action",
        "AUTH_FORBIDDEN"
    );
  }

  private static void writeProblem(
      HttpServletRequest request,
      HttpServletResponse response,
      ObjectMapper objectMapper,
      HttpStatus status,
      URI type,
      String title,
      String detail,
      String errorCode
  ) throws IOException {

    response.setStatus(status.value());
    response.setContentType(ProblemMediaTypes.APPLICATION_PROBLEM_JSON);

    URI instance = toInstanceUri(request);

    String traceId = attr(request, ATTR_TRACE_ID);
    String requestId = attr(request, ATTR_REQUEST_ID);
    String tenantId = attr(request, ATTR_TENANT_ID);

    ProblemDetails body = new ProblemDetails(
        type,
        title,
        status.value(),
        detail,
        instance,
        errorCode,
        traceId,
        requestId,
        tenantId
    );

    objectMapper.writeValue(response.getOutputStream(), body);
  }

  private static String attr(HttpServletRequest request, String key) {
    Object v = request.getAttribute(key);
    return (v instanceof String s && !s.isBlank()) ? s : null;
  }

  private static URI toInstanceUri(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null || path.isBlank()) {
      return URI.create("/");
    }
    try {
      return URI.create(path);
    } catch (IllegalArgumentException e) {
      return URI.create("/");
    }
  }
}
