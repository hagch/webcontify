package io.webcontify.app.model;

import java.util.UUID;

public class AppDependency {

    private UUID appId;
    private UUID dependsOnId;
    private UUID tenantId;
    private String dependsOnAppName;
    private String dependsOnAppSlug;
    private boolean direct;

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

    public String getDependsOnAppName() {
        return dependsOnAppName;
    }

    public void setDependsOnAppName(String dependsOnAppName) {
        this.dependsOnAppName = dependsOnAppName;
    }

    public String getDependsOnAppSlug() {
        return dependsOnAppSlug;
    }

    public void setDependsOnAppSlug(String dependsOnAppSlug) {
        this.dependsOnAppSlug = dependsOnAppSlug;
    }

    public boolean isDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
    }
}
