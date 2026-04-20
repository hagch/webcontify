package io.webcontify.shared.security;

import io.webcontify.shared.model.TenantInformation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Test security configuration that disables JWT validation and injects a
 * TenantAwareAuthentication into the SecurityContext for all requests. Used by integration tests
 * that do not have a running Keycloak instance.
 *
 * <p>By default injects a platform admin. To simulate a tenant user, set the request header
 * {@code X-Test-Tenant-Id} to the desired tenant UUID.
 *
 * <p>Provides a no-op {@link JwtDecoder} to satisfy the production {@link SecurityConfiguration}
 * dependency, and overrides the security filter chain with a permissive policy.
 */
@TestConfiguration
public class TestSecurityConfiguration {

    /**
     * Header name used in integration tests to simulate a tenant user. When present, the filter
     * injects a TenantAwareAuthentication with the given tenantId instead of platform admin.
     */
    public static final String TEST_TENANT_ID_HEADER = "X-Test-Tenant-Id";

    /**
     * No-op JwtDecoder — satisfies the production SecurityConfiguration bean dependency without
     * contacting Keycloak.
     */
    @Bean
    public JwtDecoder noOpJwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException(
                    "JWT decoding is disabled in test context. Use TestSecurityConfiguration.");
        };
    }

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new TestAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    /**
     * Injects a TenantAwareAuthentication into the SecurityContext for every test request.
     *
     * <ul>
     *   <li>If the request contains the header {@value TEST_TENANT_ID_HEADER}, a tenant user
     *       authentication is injected with the given tenant UUID.
     *   <li>Otherwise, a platform admin authentication is injected.
     * </ul>
     */
    static class TestAuthenticationFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain)
                throws ServletException, IOException {

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String tenantIdHeader = request.getHeader(TEST_TENANT_ID_HEADER);
                TenantAwareAuthentication auth;
                if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
                    TenantInformation tenantUser =
                            new TenantInformation(UUID.fromString(tenantIdHeader), false);
                    auth = new TenantAwareAuthentication(
                            tenantUser, "test-tenant-user", List.of());
                } else {
                    TenantInformation platformAdmin = new TenantInformation(null, true);
                    auth = new TenantAwareAuthentication(
                            platformAdmin, "test-platform-admin", List.of());
                }
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            filterChain.doFilter(request, response);
        }
    }
}
