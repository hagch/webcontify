package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.EnumFieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.ValidationException;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EnumFieldTypeHandlerTest {

    private EnumFieldTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EnumFieldTypeHandler();
    }

    @Test
    void fieldType_returnsEnum() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.ENUM);
    }

    // --- cast ---

    @Test
    void cast_anyValue_returnsToString() {
        var config = new EnumFieldConfig(List.of("A", "B"), null);
        assertThat(handler.cast("A", config)).isEqualTo("A");
    }

    @Test
    void cast_integerValue_returnsString() {
        var config = new EnumFieldConfig(List.of("1"), null);
        assertThat(handler.cast(1, config)).isEqualTo("1");
    }

    @Test
    void cast_nullValue_returnsNull() {
        var config = new EnumFieldConfig(List.of("A"), null);
        assertThat(handler.cast(null, config)).isNull();
    }

    // --- validateValue ---

    @Test
    void validateValue_allowedValue_passes() {
        var config = new EnumFieldConfig(List.of("A", "B", "C"), null);
        assertThatNoException().isThrownBy(() -> handler.validateValue("B", config));
    }

    @Test
    void validateValue_notAllowedValue_throws() {
        var config = new EnumFieldConfig(List.of("A", "B"), null);
        assertThatThrownBy(() -> handler.validateValue("X", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_nullValue_passes() {
        var config = new EnumFieldConfig(List.of("A"), null);
        assertThatNoException().isThrownBy(() -> handler.validateValue(null, config));
    }

    // --- validateConfig ---

    @Test
    void validateConfig_validConfig_passes() {
        var config = new EnumFieldConfig(List.of("A", "B"), "A");
        assertThatNoException().isThrownBy(() -> handler.validateConfig(config));
    }

    @Test
    void validateConfig_emptyAllowedValues_throws() {
        var config = new EnumFieldConfig(List.of(), null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_defaultNotInAllowed_throws() {
        var config = new EnumFieldConfig(List.of("A", "B"), "C");
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_nullConfig_passes() {
        assertThatNoException().isThrownBy(() -> handler.validateConfig(null));
    }

    // --- columnType ---

    @Test
    void columnType_returnsVarchar255() {
        var config = new EnumFieldConfig(List.of("A"), null);
        var type = handler.columnType(config);
        assertThat(type.length()).isEqualTo(255);
    }

    // --- supportedOperators ---

    @Test
    void supportedOperators_containsExpected() {
        assertThat(handler.supportedOperators()).containsExactlyInAnyOrder(
                Operator.EQ, Operator.NEQ, Operator.IN, Operator.ISNULL
        );
    }

    // --- buildCondition ---

    private Field<Object> testColumn() {
        return DSL.field(DSL.name("cf_status"));
    }

    @Test
    void buildCondition_eq() {
        Condition c = handler.buildCondition(testColumn(), Operator.EQ, "ACTIVE", null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_in() {
        Condition c = handler.buildCondition(testColumn(), Operator.IN, List.of("ACTIVE", "DRAFT"), null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_isNull() {
        Condition c = handler.buildCondition(testColumn(), Operator.ISNULL, true, null);
        assertThat(c.toString()).containsIgnoringCase("null");
    }

    @Test
    void buildCondition_unsupported_throws() {
        assertThatThrownBy(() -> handler.buildCondition(testColumn(), Operator.GT, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
