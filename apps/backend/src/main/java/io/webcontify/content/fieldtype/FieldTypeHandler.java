package io.webcontify.content.fieldtype;

import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;

import java.util.Set;

public interface FieldTypeHandler {

    FieldType fieldType();

    /** Validates the FieldConfig structure (e.g. min<=max). Throws ValidationException on error. */
    void validateConfig(FieldConfig config);

    /** Casts a raw user-provided value into the canonical Java type. Throws ValidationException on bad input. */
    default Object cast(Object rawValue, FieldConfig config) { return rawValue; }

    /** Validates a casted value against the config (min/max/pattern/allowedValues). */
    default void validateValue(Object value, FieldConfig config) { }

    /** Returns the jOOQ DataType for the column on disk. Used by DDL. */
    DataType<?> columnType(FieldConfig config);

    /** Formats a value for display in templates. Default: toString. */
    default String formatForDisplay(Object value, FieldConfig config) {
        return value != null ? value.toString() : "";
    }

    /** True for fields with no on-disk column (MIRROR). DDL + storage skip these. */
    default boolean isVirtual() { return false; }

    /** Returns the set of operators this type supports for filtering. */
    Set<Operator> supportedOperators();

    /** Builds a jOOQ Condition for a filter on this field. Implemented in sub-plan 5e. */
    default Condition buildCondition(Field<Object> column, Operator operator, Object value, FieldConfig config) {
        throw new UnsupportedOperationException("buildCondition not implemented for " + fieldType());
    }
}
