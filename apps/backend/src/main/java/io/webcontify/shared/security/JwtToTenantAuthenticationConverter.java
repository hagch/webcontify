package io.webcontify.shared.security;

import io.webcontify.shared.model.TenantInformation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtToTenantAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(JwtToTenantAuthenticationConverter.class);
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String TENANT_ID_CLAIM = "tenant_id";
    private static final String TENANT_REALM_PREFIX = "tenant-";

    private final TenantSlugResolver tenantSlugResolver;

    public JwtToTenantAuthenticationConverter(TenantSlugResolver tenantSlugResolver) {
        this.tenantSlugResolver = tenantSlugResolver;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        boolean isPlatformAdmin = hasPlatformAdminRole(jwt);

        String realmName = extractRealmName(jwt);

        TenantInformation tenantInformation;
        if (isPlatformAdmin) {
            tenantInformation = new TenantInformation(null, true, realmName, null);
            log.debug("Authenticated platform admin: sub={}", subject);
        } else {
            UUID tenantId = resolveTenantId(jwt);
            if (tenantId == null) {
                log.warn("Could not resolve tenant_id for non-admin user: sub={}", subject);
                throw new IllegalArgumentException("Could not resolve tenant_id from JWT");
            }
            String slug = tenantSlugResolver.resolveSlugByTenantId(tenantId).orElse(null);
            tenantInformation = new TenantInformation(tenantId, false, realmName, slug);
            log.debug("Authenticated tenant user: sub={}, tenantId={}, slug={}", subject, tenantId, slug);
        }

        Collection<GrantedAuthority> authorities = buildAuthorities(isPlatformAdmin);
        UUID userId = UUID.fromString(subject);
        return new TenantAwareAuthentication(tenantInformation, userId, authorities);
    }

    /**
     * Versucht die tenant_id aus dem JWT zu ermitteln:
     * 1. Direkt aus dem tenant_id Claim (Hardcoded Mapper auf tenant-ui Client)
     * 2. Fallback: Aus dem Issuer-URL den Realm-Namen extrahieren und per Slug aufloesen
     */
    private UUID resolveTenantId(Jwt jwt) {
        UUID fromClaim = extractTenantIdFromClaim(jwt);
        if (fromClaim != null) {
            return fromClaim;
        }
        return extractTenantIdFromIssuer(jwt).orElse(null);
    }

    private UUID extractTenantIdFromClaim(Jwt jwt) {
        String tenantIdStr = jwt.getClaim(TENANT_ID_CLAIM);
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant_id claim in JWT: {}", tenantIdStr);
            return null;
        }
    }

    /**
     * Extrahiert den Tenant-Slug aus dem Issuer-URL und loest ihn per TenantSlugResolver auf.
     * Issuer-Format: http://auth.localhost/realms/tenant-{slug}
     */
    private Optional<UUID> extractTenantIdFromIssuer(Jwt jwt) {
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
        if (issuer == null) {
            return Optional.empty();
        }

        int realmsIdx = issuer.lastIndexOf("/realms/");
        if (realmsIdx < 0) {
            return Optional.empty();
        }

        String realmName = issuer.substring(realmsIdx + "/realms/".length());
        if (!realmName.startsWith(TENANT_REALM_PREFIX)) {
            log.debug("Non-tenant realm in JWT issuer: {}", realmName);
            return Optional.empty();
        }

        String slug = realmName.substring(TENANT_REALM_PREFIX.length());
        Optional<UUID> tenantId = tenantSlugResolver.resolveTenantIdBySlug(slug);
        if (tenantId.isEmpty()) {
            log.warn("Tenant slug from issuer not found in database: slug={}", slug);
        }
        return tenantId;
    }

    private String extractRealmName(Jwt jwt) {
        String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
        if (issuer == null) return null;
        int realmsIdx = issuer.lastIndexOf("/realms/");
        if (realmsIdx < 0) return null;
        return issuer.substring(realmsIdx + "/realms/".length());
    }

    private boolean hasPlatformAdminRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return false;
        }
        Object rolesObj = realmAccess.get(ROLES_CLAIM);
        if (rolesObj instanceof List<?> roles) {
            return roles.contains(PLATFORM_ADMIN_ROLE);
        }
        return false;
    }

    private Collection<GrantedAuthority> buildAuthorities(boolean isPlatformAdmin) {
        if (isPlatformAdmin) {
            return List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        }
        return List.of();
    }
}
