package io.webcontify.identity.model;

import java.util.UUID;

public record OpenFgaListObjectsRequest(String storeId, UUID userId, String action, String resourceType) {}
