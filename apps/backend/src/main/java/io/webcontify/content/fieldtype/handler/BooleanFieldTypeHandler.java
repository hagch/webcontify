package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.BooleanFieldConfig;
import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.Operator;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class BooleanFieldTypeHandler implements FieldTypeHandler {

    @Override
    public FieldType fieldType() {
        return FieldType.BOOLEAN;
    }

    @Override
    public void validateConfig(FieldConfig config) {
        // No constraints to validate for boolean config
    }

    @Override
    public Object cast(Object rawValue, FieldConfig config) {
        if (rawValue == null) return null;
        if (rawValue instanceof Boolean b) return b;
        if (rawValue instanceof String s) return Boolean.parseBoolean(s);
        return Boolean.valueOf(rawValue.toString());
    }

    @Override
    public DataType<?> columnType(FieldConfig config) {
        return SQLDataType.BOOLEAN;
    }

    @Override
    public Set<Operator> supportedOperators() {
        return Set.of(Operator.EQ, Operator.NEQ, Operator.ISNULL);
    }

    @Override
    public Condition buildCondition(Field<Object> column, Operator operator, Object value, FieldConfig config) {
        return switch (operator) {
            case EQ -> column.eq(value);
            case NEQ -> column.ne(value);
            case ISNULL -> Boolean.TRUE.equals(value) ? column.isNull() : column.isNotNull();
            default -> throw new IllegalArgumentException("Unsupported operator " + operator + " for BOOLEAN");
        };
    }
}
