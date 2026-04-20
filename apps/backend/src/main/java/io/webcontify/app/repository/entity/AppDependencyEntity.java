package io.webcontify.app.repository.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_dependency")
@IdClass(AppDependencyId.class)
public class AppDependencyEntity {

    @Id
    @Column(name = "app_id", nullable = false)
    private UUID appId;

    @Id
    @Column(name = "depends_on_id", nullable = false)
    private UUID dependsOnId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    public UUID getAppId() {
        return appId;
    }

    public void setAppId(UUID appId) {
        this.appId = appId;
    }

    public UUID getDependsOnId() {
        return dependsOnId;
    }

    public void setDependsOnId(UUID dependsOnId) {
        this.dependsOnId = dependsOnId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
