package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.DateFieldConfig;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Component
public class DateFieldTypeHandler implements FieldTypeHandler {

    @Override
    public FieldType fieldType() {
        return FieldType.DATE;
    }

    @Override
    public void validateConfig(FieldConfig config) {
        if (config == null) return;
        var c = (DateFieldConfig) config;

        if (c.minDate() != null && c.maxDate() != null && c.minDate().isAfter(c.maxDate())) {
            throw validationError("config.minDate", "error.config.minDate.afterMaxDate");
        }
    }

    @Override
    public Object cast(Object rawValue, FieldConfig config) {
        if (rawValue == null) return null;
        if (rawValue instanceof OffsetDateTime odt) return odt;
        if (rawValue instanceof String s) {
            try {
                return OffsetDateTime.parse(s);
            } catch (DateTimeParseException e) {
                throw validationError("value", "entry.field.invalid_date");
            }
        }
        throw validationError("value", "entry.field.invalid_date");
    }

    @Override
    public DataType<?> columnType(FieldConfig config) {
        return SQLDataType.TIMESTAMPWITHTIMEZONE;
    }

    @Override
    public String formatForDisplay(Object value, FieldConfig config) {
        if (value == null) return "";
        return ((OffsetDateTime) value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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
            default -> throw new IllegalArgumentException("Unsupported operator " + operator + " for DATE");
        };
    }

    private ValidationException validationError(String field, String messageKey) {
        return new ValidationException("VALIDATION_ERROR", List.of(new FieldError(field, messageKey)));
    }
}
