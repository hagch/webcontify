package io.webcontify.shared.security;

import io.webcontify.shared.model.TenantInformation;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class TenantContext {

    private TenantContext() {}

    public static TenantInformation getTenantInformation() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof TenantAwareAuthentication tenantAuth) {
            return tenantAuth.getTenantInformation();
        }
        throw new IllegalStateException("No tenant information available in SecurityContext");
    }
}
