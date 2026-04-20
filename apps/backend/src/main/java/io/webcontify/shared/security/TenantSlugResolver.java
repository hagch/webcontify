package io.webcontify.shared.security;

import java.util.Optional;
import java.util.UUID;

public interface TenantSlugResolver {

    Optional<UUID> resolveTenantIdBySlug(String slug);

    Optional<String> resolveSlugByTenantId(UUID tenantId);
}
