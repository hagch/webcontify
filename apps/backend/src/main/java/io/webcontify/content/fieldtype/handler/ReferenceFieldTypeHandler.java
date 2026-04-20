package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.ValidationException;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ReferenceFieldTypeHandler implements FieldTypeHandler {

    @Override
    public FieldType fieldType() {
        return FieldType.REFERENCE;
    }

    @Override
    public void validateConfig(FieldConfig config) {
        if (config == null) return;
        var c = (ReferenceFieldConfig) config;

        if (c.targetDefinitionId() == null) {
            throw validationError("config.targetDefinitionId", "error.config.targetDefinitionId.required");
        }
        if (c.relationType() == null) {
            throw validationError("config.relationType", "error.config.relationType.required");
        }
        if (c.onDelete() == null) {
            throw validationError("config.onDelete", "error.config.onDelete.required");
        }
    }

    @Override
    public Object cast(Object rawValue, FieldConfig config) {
        if (rawValue == null) return null;
        if (rawValue instanceof UUID uuid) return uuid;
        if (rawValue instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                throw validationError("value", "entry.field.invalid_uuid");
            }
        }
        throw validationError("value", "entry.field.invalid_uuid");
    }

    @Override
    public DataType<?> columnType(FieldConfig config) {
        return SQLDataType.UUID;
    }

    @Override
    public Set<Operator> supportedOperators() {
        return Set.of(Operator.EQ, Operator.NEQ, Operator.IN, Operator.ISNULL);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Condition buildCondition(Field<Object> column, Operator operator, Object value, FieldConfig config) {
        return switch (operator) {
            case EQ -> column.eq(value);
            case NEQ -> column.ne(value);
            case IN -> column.in((java.util.Collection<Object>) value);
            case ISNULL -> Boolean.TRUE.equals(value) ? column.isNull() : column.isNotNull();
            default -> throw new IllegalArgumentException("Unsupported operator " + operator + " for REFERENCE");
        };
    }

    private ValidationException validationError(String field, String messageKey) {
        return new ValidationException("VALIDATION_ERROR", List.of(new FieldError(field, messageKey)));
    }
}
