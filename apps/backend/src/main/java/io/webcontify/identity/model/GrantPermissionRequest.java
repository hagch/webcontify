package io.webcontify.identity.model;

import java.util.UUID;

public record GrantPermissionRequest(UUID userId, String relation, String resourceType, UUID resourceId) {}
