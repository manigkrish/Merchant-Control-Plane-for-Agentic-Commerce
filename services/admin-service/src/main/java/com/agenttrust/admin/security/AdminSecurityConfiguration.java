package com.agenttrust.admin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AdminSecurityConfiguration {

  @Bean
  SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        // This is a stateless API (no cookies, no server-side sessions)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // Disable browser-style auth mechanisms; we will use JWT later
        .csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())

        // Authorization rules
        .authorizeHttpRequests(auth -> auth
            // Always-public endpoints
            .requestMatchers("/healthz", "/readyz").permitAll()
            .requestMatchers("/.well-known/jwks.json").permitAll()
            .requestMatchers("/v1/admin/auth/login").permitAll()
            .requestMatchers("/actuator/health/**").permitAll()

            // Secure default: block everything else until JWT auth is implemented
            .anyRequest().denyAll()
        );

    return http.build();
  }
}
