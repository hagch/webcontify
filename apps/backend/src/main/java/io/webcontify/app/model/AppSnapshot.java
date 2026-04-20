package io.webcontify.app.model;

import java.util.List;

public record AppSnapshot(
        String appId,
        String version,
        AppSnapshotMetadata metadata,
        List<AppSnapshotDependency> dependencies,
        List<AppSnapshotDefinition> contentDefinitions) {
    public record AppSnapshotMetadata(String name, String slug, String description) {}

    public record AppSnapshotDependency(String appSlug, String version) {}

    public record AppSnapshotDefinition(
            String name, String slug, List<AppSnapshotField> fields) {}

    public record AppSnapshotField(String name, String type, boolean required, String target) {}
}
