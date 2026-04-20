package io.webcontify.content.definition.model;

import java.util.UUID;

public record ReferenceFieldConfig(
        UUID targetDefinitionId, RelationType relationType, OnDeleteStrategy onDelete
) implements FieldConfig {}
