package io.webcontify.content.definition.model;

import java.util.List;

public record EnumFieldConfig(
        List<String> allowedValues, String defaultValue
) implements FieldConfig {}
