package io.webcontify.identity.model;

import java.util.UUID;

public record CheckPermissionRequest(UUID userId, String action, String resourceType, UUID resourceId) {}
