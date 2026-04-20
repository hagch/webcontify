package io.webcontify.app.model;

import java.util.List;

public record ImportPreview(
        boolean canImport,
        List<ImportPreviewApp> apps,
        List<String> warnings) {

    public record ImportPreviewApp(
            String slug,
            String name,
            boolean hasSlugCollision,
            String suggestedSlug) {}
}
