package io.webcontify.identity.service;

import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

@Component
public class ProvisioningStatusPersister {

    private final TenantRepository tenantRepository;

    public ProvisioningStatusPersister(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProvisioningSnapshot loadSnapshot(UUID tenantId) {
        var entity = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));
        var status = ProvisioningStatus.fromJson(entity.getProvisioningStatus());
        return new ProvisioningSnapshot(entity.getName(), status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistStatus(UUID tenantId, ProvisioningStatus status,
                              boolean completed, Consumer<EntityUpdater> updater) {
        var entity = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));
        entity.setProvisioningStatus(status.toJson());
        if (completed) {
            entity.setStatus(TenantStatus.ACTIVE.name());
        }
        if (updater != null) {
            updater.accept(new EntityUpdater(entity));
        }
        tenantRepository.save(entity);
    }

    public record ProvisioningSnapshot(String tenantName, ProvisioningStatus status) {}

    public static class EntityUpdater {
        private final io.webcontify.tenant.repository.entity.TenantEntity entity;

        EntityUpdater(io.webcontify.tenant.repository.entity.TenantEntity entity) {
            this.entity = entity;
        }

        public void setKeycloakRealmName(String realmName) {
            entity.setKeycloakRealmName(realmName);
        }

        public void setOpenfgaStoreId(String storeId) {
            entity.setOpenfgaStoreId(storeId);
        }
    }
}
