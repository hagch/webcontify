package io.webcontify.identity.model;

import java.util.UUID;

public record OpenFgaCheckRequest(
        String storeId,
        UUID userId,
        String action,
        String resourceType,
        UUID resourceId,
        String userSubject
) {
    public OpenFgaCheckRequest(String storeId, UUID userId, String action, String resourceType, UUID resourceId) {
        this(storeId, userId, action, resourceType, resourceId, null);
    }
}
