package io.webcontify.tenant.repository.entity;

import io.webcontify.shared.persistence.AuditableBaseEntity;
import io.webcontify.tenant.model.TenantConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenant")
public class TenantEntity extends AuditableBaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private TenantConfig config;

    @Column(name = "openfga_store_id")
    private String openfgaStoreId;

    @Column(name = "keycloak_realm_name")
    private String keycloakRealmName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provisioning_status", columnDefinition = "jsonb")
    private String provisioningStatus;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
}
