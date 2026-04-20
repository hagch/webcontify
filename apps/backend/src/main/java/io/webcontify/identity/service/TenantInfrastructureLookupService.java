package io.webcontify.identity.service;

import io.webcontify.tenant.model.TenantInfrastructureInfo;
import io.webcontify.tenant.service.TenantService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantInfrastructureLookupService {

    private final TenantService tenantService;

    public TenantInfrastructureLookupService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Cacheable(value = "tenantInfrastructure", key = "#tenantId")
    public TenantInfrastructureInfo getInfrastructure(UUID tenantId) {
        return tenantService.getInfrastructureInfo(tenantId);
    }

    @CacheEvict(value = "tenantInfrastructure", key = "#tenantId")
    public void evict(UUID tenantId) {
        // Cache eviction only
    }
}
