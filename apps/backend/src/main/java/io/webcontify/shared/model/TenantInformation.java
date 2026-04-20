package io.webcontify.shared.model;

import java.util.UUID;

public record TenantInformation(UUID tenantId, boolean platformAdmin, String realmName) {

    public TenantInformation(UUID tenantId, boolean platformAdmin) {
        this(tenantId, platformAdmin, null);
    }

    public TenantInformation {
        // Platform admins CAN have a tenantId when operating on a tenant subdomain
        if (!platformAdmin && tenantId == null) {
            throw new IllegalArgumentException("Tenant ID is required for non-platform-admin");
        }
    }
}
