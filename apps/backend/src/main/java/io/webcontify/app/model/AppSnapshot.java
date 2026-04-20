package io.webcontify.app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppSnapshot(
        String appId,
        String version,
        AppSnapshotMetadata metadata,
        List<AppSnapshotDependency> dependencies,
        List<AppSnapshotDefinition> contentDefinitions,
        List<AppSnapshotGraphNode> graphNodes,
        List<AppSnapshotGraphEdge> graphEdges) {
    public record AppSnapshotMetadata(String name, String slug, String description) {}

    public record AppSnapshotDependency(String appSlug, String version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AppSnapshotGraphNode(
            String id,
            boolean accessible,
            String name,
            String slug,
            String status,
            boolean isCurrent) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AppSnapshotGraphEdge(
            String source,
            String target,
            boolean direct) {}

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
