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
            // Self-Service Endpoints (/api/v1/tenant/** and /api/v1/auth/**) do not require
            // Subdomain validation. The TenantId is resolved from the JWT.
            // /api/v1/auth/** endpoints (e.g. effective-permissions) are called from the
            // browser with Host: api.localhost — subdomain validation would always fail.
            String path = request.getRequestURI();
            if (path != null && (path.startsWith("/api/v1/tenant/") || path.startsWith("/api/v1/auth/"))) {
                log.debug(
                        "Self-service/auth endpoint without subdomain — tenantId from JWT: principal={}",
                        tenantAuth.getPrincipal());
                filterChain.doFilter(request, response);
                return;
            }

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
     * Extrahiert die Subdomain aus dem Host-Header.
     *
     * <p>Beispiele:
     * <ul>
     *   <li>{@code acme.api.localhost} -> {@code Optional.of("acme")}
     *   <li>{@code acme.webcontify.io} -> {@code Optional.of("acme")}
     *   <li>{@code api.localhost} -> {@code Optional.empty()} (nur ein Punkt, keine echte Subdomain)
     *   <li>{@code localhost} -> {@code Optional.empty()} (kein Punkt)
     *   <li>{@code null} -> {@code Optional.empty()}
     * </ul>
     *
     * <p>Als Subdomain gilt der erste Teil, wenn der Host mindestens zwei Punkte enthaelt.
     */
    private Optional<String> extractSubdomain(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }

        // Port entfernen (z.B. "acme.api.localhost:8080" -> "acme.api.localhost")
        String hostWithoutPort = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;

        long dotCount = hostWithoutPort.chars().filter(c -> c == '.').count();
        if (dotCount < 2) {
            return Optional.empty();
        }

        String subdomain = hostWithoutPort.substring(0, hostWithoutPort.indexOf('.'));
        return subdomain.isBlank() ? Optional.empty() : Optional.of(subdomain);
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
