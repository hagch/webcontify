package io.webcontify.identity.model;

import java.util.UUID;

public record EnableKeycloakUserRequest(String realmName, UUID userId) {}
