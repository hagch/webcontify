package io.webcontify.identity.model;

import java.util.List;
import java.util.Map;

public record EffectivePermissions(
        Map<String, List<String>> permissions,
        boolean tenantAdmin
) {}
