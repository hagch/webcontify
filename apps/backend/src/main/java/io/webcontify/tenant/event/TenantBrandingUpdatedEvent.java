package io.webcontify.tenant.event;

import java.util.UUID;

public record TenantBrandingUpdatedEvent(
    UUID tenantId,
    String tenantName,
    String keycloakRealmName,
    String primaryColor,
    String secondaryColor,
    String accentColor,
    String dangerColor,
    String successColor,
    String warningColor,
    String fontFamily,
    String borderRadius,
    String logoPublicUrl,
    String faviconPublicUrl
) {}
