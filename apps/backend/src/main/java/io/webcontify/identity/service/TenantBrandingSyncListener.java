package io.webcontify.identity.service;

import io.webcontify.identity.model.UpdateRealmBrandingRequest;
import io.webcontify.tenant.event.TenantBrandingUpdatedEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class TenantBrandingSyncListener {

    private static final Logger log = LoggerFactory.getLogger(TenantBrandingSyncListener.class);

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern SAFE_URL = Pattern.compile("^https?://[^\"'<>]+$");

    private final KeycloakAdminService keycloakAdminService;

    public TenantBrandingSyncListener(KeycloakAdminService keycloakAdminService) {
        this.keycloakAdminService = keycloakAdminService;
    }

    @ApplicationModuleListener
    public void onBrandingUpdated(TenantBrandingUpdatedEvent event) {
        var attributes = buildAttributes(event);
        try {
            keycloakAdminService.updateRealmBranding(
                    new UpdateRealmBrandingRequest(event.keycloakRealmName(), attributes));
            log.info("Synced branding to Keycloak realm '{}'", event.keycloakRealmName());
        } catch (Exception e) {
            log.warn("Failed to sync branding to Keycloak realm '{}': {}",
                    event.keycloakRealmName(), e.getMessage(), e);
        }
    }

    Map<String, String> buildAttributes(TenantBrandingUpdatedEvent event) {
        var attributes = new HashMap<String, String>();

        if (event.tenantName() != null) {
            attributes.put("brandTenantName", event.tenantName());
        }

        if (event.primaryColor() != null && HEX_COLOR.matcher(event.primaryColor()).matches()) {
            attributes.put("brandPrimaryColor", event.primaryColor());
        }

        if (event.secondaryColor() != null && HEX_COLOR.matcher(event.secondaryColor()).matches()) {
            attributes.put("brandSecondaryColor", event.secondaryColor());
        }

        if (event.logoPublicUrl() != null && SAFE_URL.matcher(event.logoPublicUrl()).matches()) {
            attributes.put("brandLogo", event.logoPublicUrl());
        }

        return attributes;
    }
}
