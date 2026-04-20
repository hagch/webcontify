package io.webcontify.app.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AppBlueprint(
        OffsetDateTime exportedAt,
        UUID exportedBy,
        List<BlueprintApp> apps) {
    public record BlueprintApp(
            String slug,
            String name,
            String description,
            String version,
            List<String> dependencies,
            AppSnapshot snapshot) {}
}
