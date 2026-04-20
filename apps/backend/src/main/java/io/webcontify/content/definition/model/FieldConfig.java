package io.webcontify.content.definition.model;

public sealed interface FieldConfig permits
        TextFieldConfig, IntegerFieldConfig, DecimalFieldConfig,
        BooleanFieldConfig, DateFieldConfig, EnumFieldConfig,
        ReferenceFieldConfig, MirrorFieldConfig {}
