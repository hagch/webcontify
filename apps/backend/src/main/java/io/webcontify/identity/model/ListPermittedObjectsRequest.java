package io.webcontify.identity.model;

import java.util.UUID;

public record ListPermittedObjectsRequest(UUID userId, String action, String resourceType) {}
