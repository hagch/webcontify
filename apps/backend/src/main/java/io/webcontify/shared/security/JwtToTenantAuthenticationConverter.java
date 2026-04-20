package io.webcontify.shared.security;

import io.webcontify.shared.model.TenantInformation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        boolean isPlatformAdmin = hasPlatformAdminRole(jwt);
        UUID tenantId = extractTenantId(jwt);

        TenantInformation tenantInformation;
        if (isPlatformAdmin) {
            tenantInformation = new TenantInformation(null, true);
            log.debug("Authenticated platform admin: sub={}", subject);
        } else {
            if (tenantId == null) {
                log.warn("JWT missing tenant_id claim for non-admin user: sub={}", subject);
                throw new IllegalArgumentException("JWT missing required tenant_id claim");
            }
            tenantInformation = new TenantInformation(tenantId, false);
            log.debug("Authenticated tenant user: sub={}, tenantId={}", subject, tenantId);
        }

        Collection<GrantedAuthority> authorities = buildAuthorities(isPlatformAdmin);
        return new TenantAwareAuthentication(tenantInformation, subject, authorities);
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

    private UUID extractTenantId(Jwt jwt) {
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

    private Collection<GrantedAuthority> buildAuthorities(boolean isPlatformAdmin) {
        if (isPlatformAdmin) {
            return List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        }
        return List.of();
    }
}
