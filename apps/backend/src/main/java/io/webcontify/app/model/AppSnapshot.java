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
            String id,
            String name,
            String slug,
            String description,
            String displayFormat,
            List<AppSnapshotField> fields) {}

    public record AppSnapshotField(
            String id,
            String fieldKey,
            String fieldType,
            boolean required,
            boolean uniqueValue,
            boolean sortable,
            boolean filterable,
            String config,
            int position) {}
}
