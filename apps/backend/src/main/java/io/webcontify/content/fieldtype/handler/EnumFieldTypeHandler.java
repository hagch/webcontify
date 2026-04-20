package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.EnumFieldConfig;
import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
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

@Component
public class EnumFieldTypeHandler implements FieldTypeHandler {

    @Override
    public FieldType fieldType() {
        return FieldType.ENUM;
    }

    @Override
    public void validateConfig(FieldConfig config) {
        if (config == null) return;
        var c = (EnumFieldConfig) config;

        if (c.allowedValues() == null || c.allowedValues().isEmpty()) {
            throw validationError("config.allowedValues", "error.config.allowedValues.empty");
        }
        if (c.defaultValue() != null && !c.allowedValues().contains(c.defaultValue())) {
            throw validationError("config.defaultValue", "error.config.defaultValue.notInAllowedValues");
        }
    }

    @Override
    public Object cast(Object rawValue, FieldConfig config) {
        if (rawValue == null) return null;
        return rawValue.toString();
    }

    @Override
    public void validateValue(Object value, FieldConfig config) {
        if (value == null) return;
        var c = (EnumFieldConfig) config;
        var str = (String) value;

        if (!c.allowedValues().contains(str)) {
            throw validationError("value", "backendError.not_in_allowed_values");
        }
    }

    @Override
    public DataType<?> columnType(FieldConfig config) {
        return SQLDataType.VARCHAR(255);
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
            default -> throw new IllegalArgumentException("Unsupported operator " + operator + " for ENUM");
        };
    }

    private ValidationException validationError(String field, String messageKey) {
        return new ValidationException("VALIDATION_ERROR", List.of(new FieldError(field, messageKey)));
    }
}
