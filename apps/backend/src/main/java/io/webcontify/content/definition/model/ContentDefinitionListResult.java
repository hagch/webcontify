package io.webcontify.content.definition.model;

import java.util.List;
import java.util.UUID;

/**
 * Carries the visible content definitions of an app plus minimal metadata for
 * REFERENCE targets that are not in the visible set (either because the target
 * belongs to another app, or because the user cannot access it under override
 * semantics).
 *
 * <p>When a referenced definition is not accessible, the ghost entry carries
 * only {@code id} and {@code accessible=false}. Name and app metadata stay
 * {@code null} to prevent leaking information about restricted definitions.
 */
public record ContentDefinitionListResult(
        List<ContentDefinition> items,
        List<ReferencedDefinitionInfo> referencedDefinitions) {

    public record ReferencedDefinitionInfo(
            UUID id,
            boolean accessible,
            /** null when !accessible */ String name,
            /** null when !accessible */ UUID appId,
            /** null when !accessible */ String appName) {
        public static ReferencedDefinitionInfo inaccessible(UUID id) {
            return new ReferencedDefinitionInfo(id, false, null, null, null);
        }

        public static ReferencedDefinitionInfo accessible(UUID id, String name, UUID appId, String appName) {
            return new ReferencedDefinitionInfo(id, true, name, appId, appName);
        }
    }
}
