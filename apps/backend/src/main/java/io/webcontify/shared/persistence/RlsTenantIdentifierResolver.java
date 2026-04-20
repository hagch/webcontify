package io.webcontify.shared.persistence;

import io.webcontify.shared.security.TenantAwareAuthentication;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class RlsTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String PLATFORM_TENANT_CONTEXT = "";
    @Override
    public String resolveCurrentTenantIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof TenantAwareAuthentication tenantAuth) {
            var info = tenantAuth.getTenantInformation();
            if (!info.platformAdmin()) {
                return info.tenantId().toString();
            }
        }
        return PLATFORM_TENANT_CONTEXT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
