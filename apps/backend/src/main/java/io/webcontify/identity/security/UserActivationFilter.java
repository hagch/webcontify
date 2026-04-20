package io.webcontify.identity.security;

import io.webcontify.identity.service.UserActivationService;
import io.webcontify.shared.security.TenantAwareAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class UserActivationFilter extends OncePerRequestFilter {

    private final UserActivationService activationService;

    public UserActivationFilter(UserActivationService activationService) {
        this.activationService = activationService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof TenantAwareAuthentication tenantAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Platform Admin hat keinen tenant_user Eintrag
        if (tenantAuth.getTenantInformation().platformAdmin()) {
            filterChain.doFilter(request, response);
            return;
        }

        Object principal = tenantAuth.getPrincipal();
        UUID userId;
        try {
            userId = principal instanceof UUID uid ? uid : UUID.fromString(principal.toString());
        } catch (IllegalArgumentException e) {
            filterChain.doFilter(request, response);
            return;
        }

        activationService.activateOnFirstLogin(userId);

        filterChain.doFilter(request, response);
    }
}
