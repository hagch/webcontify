package io.webcontify.tenant.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Tenant {

    private UUID id;
    private String name;
    private String slug;
    private TenantStatus status;
    private TenantConfig config;
    private String openfgaStoreId;
    private String keycloakRealmName;
    private String provisioningStatus;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public TenantConfig getConfig() {
        return config;
    }

    public void setConfig(TenantConfig config) {
        this.config = config;
    }

    public String getOpenfgaStoreId() {
        return openfgaStoreId;
    }

    public void setOpenfgaStoreId(String openfgaStoreId) {
        this.openfgaStoreId = openfgaStoreId;
    }

    public String getKeycloakRealmName() {
        return keycloakRealmName;
    }

    public void setKeycloakRealmName(String keycloakRealmName) {
        this.keycloakRealmName = keycloakRealmName;
    }

    public String getProvisioningStatus() {
        return provisioningStatus;
    }

    public void setProvisioningStatus(String provisioningStatus) {
        this.provisioningStatus = provisioningStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
