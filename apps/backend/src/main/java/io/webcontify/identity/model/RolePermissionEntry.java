package io.webcontify.identity.model;

import java.util.List;

public record RolePermissionEntry(String resourceType, List<String> actions) {}
