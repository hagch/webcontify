package io.webcontify.content.management.model;

import io.webcontify.content.definition.model.FieldType;

public record FieldValue(String fieldKey, FieldType fieldType, Object value) {}
