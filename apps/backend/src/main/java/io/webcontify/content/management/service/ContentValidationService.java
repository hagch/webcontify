package io.webcontify.content.management.service;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.ValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Validates and casts raw entry values against a content definition's field schema.
 * Performs cast, required check, and field-level value validation (min/max/pattern/allowedValues).
 * Unique checks and reference target checks are done by {@code ContentEntryService}.
 */
@Component
public class ContentValidationService {

    private final FieldTypeRegistry fieldTypeRegistry;

    public ContentValidationService(FieldTypeRegistry fieldTypeRegistry) {
        this.fieldTypeRegistry = fieldTypeRegistry;
    }

    /**
     * Validates and casts raw values against the given definition's fields.
     *
     * @param rawValues map of raw values keyed by field key (with "cf_" prefix)
     * @param def       the content definition with field metadata
     * @return map of casted values keyed by column name ("cf_xxx")
     * @throws ValidationException if any field fails validation
     */
    public Map<String, Object> validateAndCast(Map<String, Object> rawValues, ContentDefinition def) {
        if (def.getFields() == null || def.getFields().isEmpty()) {
            return Map.of();
        }

        Map<String, Object> castedValues = new LinkedHashMap<>();
        List<FieldError> errors = new ArrayList<>();

        for (FieldDefinition field : def.getFields()) {
            FieldTypeHandler handler = fieldTypeRegistry.get(field.getFieldType());

            // Skip virtual fields (MIRROR)
            if (handler.isVirtual()) {
                continue;
            }

            String columnName = "cf_" + field.getFieldKey();
            Object rawValue = rawValues.get(columnName);

            // Cast
            Object castedValue = null;
            if (rawValue != null) {
                try {
                    castedValue = handler.cast(rawValue, field.getConfig());
                } catch (Exception e) {
                    errors.add(new FieldError(columnName, "entry.field.invalid_value"));
                    continue;
                }
            }

            // Required check
            if (field.isRequired() && castedValue == null) {
                errors.add(new FieldError(columnName, "entry.field.required"));
                continue;
            }

            // Value validation (min/max/pattern/allowedValues)
            if (castedValue != null) {
                try {
                    handler.validateValue(castedValue, field.getConfig());
                } catch (Exception e) {
                    errors.add(new FieldError(columnName, "entry.field.invalid_value"));
                    continue;
                }
            }

            castedValues.put(columnName, castedValue);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("VALIDATION_ERROR", errors);
        }

        return castedValues;
    }
}
