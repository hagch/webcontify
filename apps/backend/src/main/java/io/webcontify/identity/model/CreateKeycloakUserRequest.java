package io.webcontify.identity.model;

public record CreateKeycloakUserRequest(String realmName, String email, String firstName, String lastName) {}
