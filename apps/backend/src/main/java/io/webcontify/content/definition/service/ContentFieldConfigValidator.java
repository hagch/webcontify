package io.webcontify.content.definition.service;

import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import org.springframework.stereotype.Component;

@Component
public class ContentFieldConfigValidator {

    private final FieldTypeRegistry fieldTypeRegistry;

    public ContentFieldConfigValidator(FieldTypeRegistry fieldTypeRegistry) {
        this.fieldTypeRegistry = fieldTypeRegistry;
    }

    public void validate(FieldType fieldType, FieldConfig config) {
        if (config == null) {
            return;
        }

        fieldTypeRegistry.get(fieldType).validateConfig(config);
    }
}
