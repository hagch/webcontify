package io.webcontify.identity.model;

import java.util.UUID;

public record DisableKeycloakUserRequest(String realmName, UUID userId) {}
