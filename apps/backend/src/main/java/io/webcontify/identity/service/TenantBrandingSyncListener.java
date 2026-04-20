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
    private static final java.util.Set<String> ALLOWED_FONTS = java.util.Set.of(
            "system", "inter", "geist", "plus-jakarta-sans", "dm-sans",
            "roboto", "source-sans-3", "ibm-plex-sans", "open-sans",
            "lato", "nunito-sans", "poppins", "merriweather");
    private static final java.util.Set<String> ALLOWED_RADII = java.util.Set.of(
            "0", "0.25rem", "0.5rem", "0.75rem", "1rem");

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

        if (event.accentColor() != null && HEX_COLOR.matcher(event.accentColor()).matches()) {
            attributes.put("brandAccentColor", event.accentColor());
        }

        if (event.dangerColor() != null && HEX_COLOR.matcher(event.dangerColor()).matches()) {
            attributes.put("brandDangerColor", event.dangerColor());
        }

        if (event.successColor() != null && HEX_COLOR.matcher(event.successColor()).matches()) {
            attributes.put("brandSuccessColor", event.successColor());
        }

        if (event.warningColor() != null && HEX_COLOR.matcher(event.warningColor()).matches()) {
            attributes.put("brandWarningColor", event.warningColor());
        }

        if (event.fontFamily() != null && ALLOWED_FONTS.contains(event.fontFamily())) {
            attributes.put("brandFontFamily", mapFontFamilyToCss(event.fontFamily()));
        }

        if (event.borderRadius() != null && ALLOWED_RADII.contains(event.borderRadius())) {
            attributes.put("brandBorderRadius", event.borderRadius());
        }

        if (event.logoPublicUrl() != null && SAFE_URL.matcher(event.logoPublicUrl()).matches()) {
            attributes.put("brandLogo", event.logoPublicUrl());
        }

        if (event.faviconPublicUrl() != null && SAFE_URL.matcher(event.faviconPublicUrl()).matches()) {
            attributes.put("brandFavicon", event.faviconPublicUrl());
        }

        return attributes;
    }

    private String mapFontFamilyToCss(String fontFamily) {
        return switch (fontFamily) {
            case "inter" -> "'Inter', sans-serif";
            case "geist" -> "'Geist', sans-serif";
            case "plus-jakarta-sans" -> "'Plus Jakarta Sans', sans-serif";
            case "dm-sans" -> "'DM Sans', sans-serif";
            case "roboto" -> "'Roboto', sans-serif";
            case "source-sans-3" -> "'Source Sans 3', sans-serif";
            case "ibm-plex-sans" -> "'IBM Plex Sans', sans-serif";
            case "open-sans" -> "'Open Sans', sans-serif";
            case "lato" -> "'Lato', sans-serif";
            case "nunito-sans" -> "'Nunito Sans', sans-serif";
            case "poppins" -> "'Poppins', sans-serif";
            case "merriweather" -> "'Merriweather', serif";
            default -> "system-ui, sans-serif";
        };
    }
}
