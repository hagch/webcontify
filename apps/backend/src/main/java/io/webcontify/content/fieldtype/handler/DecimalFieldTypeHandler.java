package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.DecimalFieldConfig;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Component
public class DecimalFieldTypeHandler implements FieldTypeHandler {

    private static final int DEFAULT_PRECISION = 19;
    private static final int DEFAULT_SCALE = 4;

    @Override
    public FieldType fieldType() {
        return FieldType.DECIMAL;
    }

    @Override
    public void validateConfig(FieldConfig config) {
        if (config == null) return;
        var c = (DecimalFieldConfig) config;

        if (c.min() != null && c.max() != null && c.min().compareTo(c.max()) > 0) {
            throw validationError("config.min", "error.config.min.greaterThanMax");
        }
        if (c.precision() != null && c.scale() != null && c.precision() < c.scale()) {
            throw validationError("config.precision", "error.config.precision.lessThanScale");
        }
    }

    @Override
    public Object cast(Object rawValue, FieldConfig config) {
        if (rawValue == null) return null;
        if (rawValue instanceof BigDecimal bd) return bd;
        if (rawValue instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (rawValue instanceof String s) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                throw validationError("value", "entry.field.invalid_decimal");
            }
        }
        throw validationError("value", "entry.field.invalid_decimal");
    }

    @Override
    public void validateValue(Object value, FieldConfig config) {
        if (value == null) return;
        var c = (DecimalFieldConfig) config;
        var v = (BigDecimal) value;

        if (c.min() != null && v.compareTo(c.min()) < 0) {
            throw validationError("value", "entry.field.below_min");
        }
        if (c.max() != null && v.compareTo(c.max()) > 0) {
            throw validationError("value", "entry.field.exceeds_max");
        }
    }

    @Override
    public DataType<?> columnType(FieldConfig config) {
        var c = (DecimalFieldConfig) config;
        int precision = c != null && c.precision() != null ? c.precision() : DEFAULT_PRECISION;
        int scale = c != null && c.scale() != null ? c.scale() : DEFAULT_SCALE;
        return SQLDataType.DECIMAL(precision, scale);
    }

    @Override
    public Set<Operator> supportedOperators() {
        return Set.of(Operator.EQ, Operator.NEQ, Operator.GT, Operator.GTE,
                Operator.LT, Operator.LTE, Operator.ISNULL);
    }

    @Override
    public Condition buildCondition(Field<Object> column, Operator operator, Object value, FieldConfig config) {
        return switch (operator) {
            case EQ -> column.eq(value);
            case NEQ -> column.ne(value);
            case GT -> column.greaterThan(value);
            case GTE -> column.greaterOrEqual(value);
            case LT -> column.lessThan(value);
            case LTE -> column.lessOrEqual(value);
            case ISNULL -> Boolean.TRUE.equals(value) ? column.isNull() : column.isNotNull();
            default -> throw new IllegalArgumentException("Unsupported operator " + operator + " for DECIMAL");
        };
    }

    private ValidationException validationError(String field, String messageKey) {
        return new ValidationException("VALIDATION_ERROR", List.of(new FieldError(field, messageKey)));
    }
}
