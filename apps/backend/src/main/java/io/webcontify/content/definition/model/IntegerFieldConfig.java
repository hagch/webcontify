package io.webcontify.content.definition.model;

public record IntegerFieldConfig(
        Long min, Long max, Long defaultValue
) implements FieldConfig {}
