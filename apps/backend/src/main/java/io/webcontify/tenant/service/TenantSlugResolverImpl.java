package io.webcontify.tenant.service;

import io.webcontify.shared.security.TenantSlugResolver;
import io.webcontify.tenant.repository.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantSlugResolverImpl implements TenantSlugResolver {

    private final TenantRepository tenantRepository;

    public TenantSlugResolverImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Optional<UUID> resolveTenantIdBySlug(String slug) {
        return tenantRepository
                .findBySlugAndAuditInfoDeletedAtIsNull(slug)
                .map(entity -> entity.getId());
    }
}
