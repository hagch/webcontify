package io.webcontify.identity.model;

import java.util.UUID;

public record DeleteKeycloakUserRequest(String realmName, UUID userId) {}
