package io.webcontify.content.management.query;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.ValidationException;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Parses RHS-operator filter syntax from query parameters.
 * Format: {@code ?cf_title=eq:Hello&cf_count=gte:5}
 */
@Component
public class RhsOperatorParser {

    private final FieldTypeRegistry registry;

    public RhsOperatorParser(FieldTypeRegistry registry) {
        this.registry = registry;
    }

    public List<Filter> parse(Map<String, String> queryParams, ContentDefinition def,
                               FieldTypeRegistry registry) {
        Map<String, FieldDefinition> fieldIndex = buildFieldIndex(def);
        List<Filter> filters = new ArrayList<>();

        for (var entry : queryParams.entrySet()) {
            String paramKey = entry.getKey();
            if (!paramKey.startsWith("cf_")) continue;

            String fieldKey = paramKey.substring(3);
            FieldDefinition fieldDef = fieldIndex.get(fieldKey);
            if (fieldDef == null) continue;

            FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
            String rawParamValue = entry.getValue();

            int colonIndex = rawParamValue.indexOf(':');
            if (colonIndex < 0) {
                throw new ValidationException("VALIDATION_ERROR",
                        List.of(new FieldError(paramKey, "entry.filter.invalid_format")));
            }

            String operatorStr = rawParamValue.substring(0, colonIndex).toUpperCase();
            String valueStr = rawParamValue.substring(colonIndex + 1);

            Operator operator;
            try {
                operator = Operator.valueOf(operatorStr);
            } catch (IllegalArgumentException e) {
                throw new ValidationException("VALIDATION_ERROR",
                        List.of(new FieldError(paramKey, "entry.filter.unknown_operator")));
            }

            if (!handler.supportedOperators().contains(operator)) {
                throw new ValidationException("VALIDATION_ERROR",
                        List.of(new FieldError(paramKey, "entry.filter.unsupported_operator")));
            }

            Object castedValue;
            if (operator == Operator.ISNULL) {
                castedValue = Boolean.parseBoolean(valueStr);
            } else if (operator == Operator.IN) {
                String[] parts = valueStr.split(",");
                List<Object> values = new ArrayList<>();
                for (String part : parts) {
                    values.add(handler.cast(part.trim(), fieldDef.getConfig()));
                }
                castedValue = values;
            } else {
                castedValue = handler.cast(valueStr, fieldDef.getConfig());
            }

            filters.add(new Filter(paramKey, operator, castedValue));
        }

        return filters;
    }

    private Map<String, FieldDefinition> buildFieldIndex(ContentDefinition def) {
        if (def.getFields() == null) return Map.of();
        Map<String, FieldDefinition> map = new HashMap<>();
        for (FieldDefinition f : def.getFields()) {
            map.put(f.getFieldKey(), f);
        }
        return map;
    }
}
