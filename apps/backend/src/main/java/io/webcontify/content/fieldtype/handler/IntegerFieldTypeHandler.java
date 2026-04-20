package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.IntegerFieldConfig;
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
public class IntegerFieldTypeHandler implements FieldTypeHandler {

    @Override
    public FieldType fieldType() {
        return FieldType.INTEGER;
    }

    @Override
    public void validateConfig(FieldConfig config) {
        if (config == null) return;
        var c = (IntegerFieldConfig) config;

        if (c.min() != null && c.max() != null && c.min() > c.max()) {
            throw validationError("config.min", "error.config.min.greaterThanMax");
        }
        if (c.defaultValue() != null) {
            if (c.min() != null && c.defaultValue() < c.min()) {
                throw validationError("config.defaultValue", "error.config.defaultValue.belowMin");
            }
            if (c.max() != null && c.defaultValue() > c.max()) {
                throw validationError("config.defaultValue", "error.config.defaultValue.exceedsMax");
            }
        }
    }

    @Override
    public Object cast(Object rawValue, FieldConfig config) {
        if (rawValue == null) return null;
        if (rawValue instanceof Long l) return l;
        if (rawValue instanceof Number n) return n.longValue();
        if (rawValue instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw validationError("value", "entry.field.invalid_integer");
            }
        }
        throw validationError("value", "entry.field.invalid_integer");
    }

    @Override
    public void validateValue(Object value, FieldConfig config) {
        if (value == null) return;
        var c = (IntegerFieldConfig) config;
        var v = (Long) value;

        if (c.min() != null && v < c.min()) {
            throw validationError("value", "entry.field.below_min");
        }
        if (c.max() != null && v > c.max()) {
            throw validationError("value", "entry.field.exceeds_max");
        }
    }

    @Override
    public DataType<?> columnType(FieldConfig config) {
        return SQLDataType.BIGINT;
    }

    @Override
    public Set<Operator> supportedOperators() {
        return Set.of(Operator.EQ, Operator.NEQ, Operator.GT, Operator.GTE,
                Operator.LT, Operator.LTE, Operator.IN, Operator.ISNULL);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Condition buildCondition(Field<Object> column, Operator operator, Object value, FieldConfig config) {
        return switch (operator) {
            case EQ -> column.eq(value);
            case NEQ -> column.ne(value);
            case GT -> column.greaterThan(value);
            case GTE -> column.greaterOrEqual(value);
            case LT -> column.lessThan(value);
            case LTE -> column.lessOrEqual(value);
            case IN -> column.in((java.util.Collection<Object>) value);
            case ISNULL -> Boolean.TRUE.equals(value) ? column.isNull() : column.isNotNull();
            default -> throw new IllegalArgumentException("Unsupported operator " + operator + " for INTEGER");
        };
    }

    private ValidationException validationError(String field, String messageKey) {
        return new ValidationException("VALIDATION_ERROR", List.of(new FieldError(field, messageKey)));
    }
}
