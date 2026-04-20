package io.webcontify.content.definition.model;

import java.util.UUID;

public record MirrorFieldConfig(
        UUID sourceDefinitionId, UUID sourceFieldId, UUID ownerAppId
) implements FieldConfig {}
