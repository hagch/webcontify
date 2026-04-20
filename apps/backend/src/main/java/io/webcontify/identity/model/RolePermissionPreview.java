package io.webcontify.identity.model;

import java.util.List;

public record RolePermissionPreview(String resourceType, List<String> actions) {}
