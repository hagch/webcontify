package io.webcontify.identity.service;

import io.webcontify.shared.security.TenantAwareAuthentication;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final KeycloakAdminService keycloakAdminService;
    private final String keycloakBaseUrl;

    public AuthService(
            KeycloakAdminService keycloakAdminService,
            @Value("${keycloak.admin.server-url}") String keycloakBaseUrl) {
        this.keycloakAdminService = keycloakAdminService;
        this.keycloakBaseUrl = keycloakBaseUrl;
    }

    public UserRepresentation getMe() {
        var realm = extractRealmFromIssuer();
        var userId = resolveCurrentUserId();
        log.debug("Fetching profile for userId={} in realm={}", userId, realm);
        return keycloakAdminService.getUserById(realm, userId);
    }

    public String getAccountUrl() {
        var realm = extractRealmFromIssuer();
        return keycloakBaseUrl + "/realms/" + realm + "/account/#/security/signingin";
    }

    private String extractRealmFromIssuer() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            var issuer = jwtAuth.getToken().getIssuer().toString();
            // issuer format: http://auth.localhost/realms/webcontify-platform
            return issuer.substring(issuer.lastIndexOf("/") + 1);
        }
        throw new IllegalStateException("Cannot extract realm from non-JWT authentication");
    }

    private UUID resolveCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return UUID.fromString(jwtAuth.getToken().getSubject());
        }
        var tenantAuth = (TenantAwareAuthentication) authentication;
        return UUID.fromString((String) tenantAuth.getPrincipal());
    }
}
