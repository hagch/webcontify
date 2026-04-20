package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.TextFieldConfig;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.ValidationException;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class TextFieldTypeHandler implements FieldTypeHandler {

    private static final int DEFAULT_MAX_LENGTH = 4000;
    private static final int CLOB_THRESHOLD = 10000;

    @Override
    public FieldType fieldType() {
        return FieldType.TEXT;
    }

    @Override
    public void validateConfig(FieldConfig config) {
        if (config == null) return;
        var c = (TextFieldConfig) config;

        if (c.minLength() != null && c.maxLength() != null && c.minLength() > c.maxLength()) {
            throw validationError("config.minLength", "error.config.minLength.greaterThanMaxLength");
        }
        if (c.pattern() != null) {
            try {
                Pattern.compile(c.pattern());
            } catch (PatternSyntaxException e) {
                throw validationError("config.pattern", "error.config.pattern.invalidRegex");
            }
        }
        if (c.defaultValue() != null) {
            if (c.minLength() != null && c.defaultValue().length() < c.minLength()) {
                throw validationError("config.defaultValue", "error.config.defaultValue.belowMinLength");
            }
            if (c.maxLength() != null && c.defaultValue().length() > c.maxLength()) {
                throw validationError("config.defaultValue", "error.config.defaultValue.exceedsMaxLength");
            }
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
        var c = (TextFieldConfig) config;
        var str = (String) value;

        if (c.minLength() != null && str.length() < c.minLength()) {
            throw validationError("value", "backendError.below_min_length");
        }
        if (c.maxLength() != null && str.length() > c.maxLength()) {
            throw validationError("value", "backendError.exceeds_max_length");
        }
        if (c.pattern() != null && !Pattern.matches(c.pattern(), str)) {
            throw validationError("value", "backendError.pattern_mismatch");
        }
    }

    @Override
    public DataType<?> columnType(FieldConfig config) {
        var c = (TextFieldConfig) config;
        int maxLength = c != null && c.maxLength() != null ? c.maxLength() : DEFAULT_MAX_LENGTH;
        if (maxLength > CLOB_THRESHOLD) {
            return SQLDataType.CLOB;
        }
        return SQLDataType.VARCHAR(maxLength);
    }

    @Override
    public Set<Operator> supportedOperators() {
        return Set.of(Operator.EQ, Operator.NEQ, Operator.LIKE, Operator.IN, Operator.ISNULL);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Condition buildCondition(Field<Object> column, Operator operator, Object value, FieldConfig config) {
        return switch (operator) {
            case EQ -> column.eq(value);
            case NEQ -> column.ne(value);
            case LIKE -> column.likeIgnoreCase("%" + value + "%");
            case IN -> column.in((java.util.Collection<Object>) value);
            case ISNULL -> Boolean.TRUE.equals(value) ? column.isNull() : column.isNotNull();
            default -> throw new IllegalArgumentException("Unsupported operator " + operator + " for TEXT");
        };
    }

    private ValidationException validationError(String field, String messageKey) {
        return new ValidationException("VALIDATION_ERROR", List.of(new FieldError(field, messageKey)));
    }
}
