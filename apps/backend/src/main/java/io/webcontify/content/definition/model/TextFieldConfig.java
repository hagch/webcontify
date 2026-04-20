package io.webcontify.content.definition.model;

public record TextFieldConfig(
        Integer minLength, Integer maxLength, String pattern, String defaultValue
) implements FieldConfig {}
