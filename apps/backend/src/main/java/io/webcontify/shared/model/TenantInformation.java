package io.webcontify.shared.model;

import java.util.UUID;

public record TenantInformation(UUID tenantId, boolean platformAdmin) {

    public TenantInformation {
        if (platformAdmin && tenantId != null) {
            throw new IllegalArgumentException("Platform admin must not have a tenant ID");
        }
        if (!platformAdmin && tenantId == null) {
            throw new IllegalArgumentException("Tenant ID is required for non-platform-admin");
        }
    }
}
