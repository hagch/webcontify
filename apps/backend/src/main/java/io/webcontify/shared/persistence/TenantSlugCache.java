package io.webcontify.shared.persistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.webcontify.shared.security.TenantSlugResolver;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class TenantSlugCache {

    private final Cache<UUID, Optional<String>> cache;
    private final TenantSlugResolver tenantSlugResolver;

    public TenantSlugCache(@Lazy TenantSlugResolver tenantSlugResolver) {
        this.tenantSlugResolver = tenantSlugResolver;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .build();
    }

    public Optional<String> resolveSlug(UUID tenantId) {
        return cache.get(tenantId, tenantSlugResolver::resolveSlugByTenantId);
    }

    /** Returns cached slug without triggering a JPA lookup. Null if not cached. */
    public String getIfPresent(UUID tenantId) {
        Optional<String> cached = cache.getIfPresent(tenantId);
        return cached != null ? cached.orElse(null) : null;
    }

    /** Populate cache from a direct JDBC lookup (used by RlsConnectionProvider to avoid recursion). */
    public void put(UUID tenantId, String slug) {
        cache.put(tenantId, Optional.of(slug));
    }

    public void evict(UUID tenantId) {
        cache.invalidate(tenantId);
    }
}
