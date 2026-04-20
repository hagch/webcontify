package io.webcontify.shared.security;

import io.webcontify.shared.model.TenantInformation;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class TenantAwareAuthentication extends AbstractAuthenticationToken {

    private final TenantInformation tenantInformation;
    private final Object principal;

    public TenantAwareAuthentication(
            TenantInformation tenantInformation,
            Object principal,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.tenantInformation = tenantInformation;
        this.principal = principal;
        setAuthenticated(true);
    }

    public TenantInformation getTenantInformation() {
        return tenantInformation;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
