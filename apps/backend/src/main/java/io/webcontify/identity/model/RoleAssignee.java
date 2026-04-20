package io.webcontify.identity.model;

import java.util.UUID;

public record RoleAssignee(UUID userId, String email, String firstName, String lastName) {}
