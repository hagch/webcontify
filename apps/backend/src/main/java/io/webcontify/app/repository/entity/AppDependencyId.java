package io.webcontify.app.repository.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AppDependencyId implements Serializable {

    private UUID appId;
    private UUID dependsOnId;

    public AppDependencyId() {
    }

    public AppDependencyId(UUID appId, UUID dependsOnId) {
        this.appId = appId;
        this.dependsOnId = dependsOnId;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppDependencyId that = (AppDependencyId) o;
        return Objects.equals(appId, that.appId) && Objects.equals(dependsOnId, that.dependsOnId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, dependsOnId);
    }
}
