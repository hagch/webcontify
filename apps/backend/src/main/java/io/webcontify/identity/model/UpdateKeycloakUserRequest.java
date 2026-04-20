package io.webcontify.identity.model;

import java.util.UUID;

public record UpdateKeycloakUserRequest(String realmName, UUID userId, String email, String firstName, String lastName) {}
