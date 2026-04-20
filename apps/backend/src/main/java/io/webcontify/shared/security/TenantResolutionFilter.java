package io.webcontify.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);
    private static final String CONTENT_TYPE_PROBLEM_JSON = "application/problem+json";
    private static final String ERROR_CODE_TENANT_MISMATCH = "tenant.subdomain_mismatch";

    private final TenantSlugResolver tenantSlugResolver;
    private final ObjectMapper objectMapper;

    public TenantResolutionFilter(TenantSlugResolver tenantSlugResolver) {
        this.tenantSlugResolver = tenantSlugResolver;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof TenantAwareAuthentication tenantAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (tenantAuth.getTenantInformation().platformAdmin()) {
            // Platform admins skip subdomain validation but we resolve tenantId
            // from subdomain if present (needed for tenant-scoped operations)
            String host = request.getHeader("Host");
            Optional<String> subdomain = extractSubdomain(host);
            if (subdomain.isPresent()) {
                Optional<UUID> resolvedTenantId = tenantSlugResolver.resolveTenantIdBySlug(subdomain.get());
                if (resolvedTenantId.isPresent()) {
                    var enrichedInfo = new io.webcontify.shared.model.TenantInformation(
                            resolvedTenantId.get(), true, tenantAuth.getTenantInformation().realmName());
                    var enrichedAuth = new TenantAwareAuthentication(
                            enrichedInfo, tenantAuth.getPrincipal(), tenantAuth.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(enrichedAuth);
                    log.debug("Platform admin on tenant subdomain: slug={}, tenantId={}", subdomain.get(), resolvedTenantId.get());
                }
            } else {
                log.debug("Platform admin request — no tenant subdomain");
            }
            filterChain.doFilter(request, response);
            return;
        }

        String host = request.getHeader("Host");
        Optional<String> subdomain = extractSubdomain(host);

        if (subdomain.isEmpty()) {
            log.warn(
                    "Tenant user request without subdomain: host={}, principal={}",
                    host,
                    tenantAuth.getPrincipal());
            writeForbiddenResponse(response, "No tenant subdomain found in Host header");
            return;
        }

        String slug = subdomain.get();
        UUID jwtTenantId = tenantAuth.getTenantInformation().tenantId();

        Optional<UUID> resolvedTenantId = tenantSlugResolver.resolveTenantIdBySlug(slug);

        if (resolvedTenantId.isEmpty()) {
            log.warn(
                    "Tenant user request with unknown subdomain: slug={}, principal={}",
                    slug,
                    tenantAuth.getPrincipal());
            writeForbiddenResponse(response, "Tenant with subdomain '" + slug + "' not found");
            return;
        }

        UUID dbTenantId = resolvedTenantId.get();
        if (!dbTenantId.equals(jwtTenantId)) {
            log.warn(
                    "Tenant mismatch: jwtTenantId={}, dbTenantId={}, slug={}, principal={}",
                    jwtTenantId,
                    dbTenantId,
                    slug,
                    tenantAuth.getPrincipal());
            writeForbiddenResponse(
                    response, "JWT tenant does not match subdomain '" + slug + "'");
            return;
        }

        log.debug(
                "Subdomain validated: slug={}, tenantId={}, principal={}",
                slug,
                jwtTenantId,
                tenantAuth.getPrincipal());
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the tenant slug from the Host header.
     *
     * <p>Supported patterns:
     * <ul>
     *   <li>{@code api.acme.localhost} -> {@code Optional.of("acme")} (API with tenant subdomain)
     *   <li>{@code acme.localhost} -> {@code Optional.of("acme")} (direct tenant subdomain)
     *   <li>{@code acme.webcontify.io} -> {@code Optional.of("acme")} (production)
     *   <li>{@code api.localhost} -> {@code Optional.empty()} (API without tenant)
     *   <li>{@code localhost} -> {@code Optional.empty()} (no subdomain)
     * </ul>
     */
    private Optional<String> extractSubdomain(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }

        String hostWithoutPort = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
        String[] parts = hostWithoutPort.split("\\.");

        // api.{slug}.localhost → slug is parts[1]
        if (parts.length == 3 && "api".equals(parts[0])) {
            return Optional.of(parts[1]);
        }

        // {slug}.localhost or {slug}.webcontify.io → slug is parts[0]
        if (parts.length >= 2) {
            String candidate = parts[0];
            if ("api".equals(candidate) || "platform".equals(candidate)
                    || "auth".equals(candidate) || "localhost".equals(candidate)) {
                return Optional.empty();
            }
            return Optional.of(candidate);
        }

        return Optional.empty();
    }

    private void writeForbiddenResponse(HttpServletResponse response, String detail)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(CONTENT_TYPE_PROBLEM_JSON);

        Map<String, Object> body = Map.of(
                "type", "https://webcontify.io/errors/tenant-subdomain-mismatch",
                "title", "Forbidden",
                "status", HttpServletResponse.SC_FORBIDDEN,
                "code", ERROR_CODE_TENANT_MISMATCH,
                "detail", detail);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
