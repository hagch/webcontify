package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.IntegerFieldConfig;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.ValidationException;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class IntegerFieldTypeHandlerTest {

    private IntegerFieldTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IntegerFieldTypeHandler();
    }

    @Test
    void fieldType_returnsInteger() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.INTEGER);
    }

    // --- cast ---

    @Test
    void cast_longValue_returnsAsIs() {
        var config = new IntegerFieldConfig(null, null, null);
        assertThat(handler.cast(42L, config)).isEqualTo(42L);
    }

    @Test
    void cast_integerValue_convertsToLong() {
        var config = new IntegerFieldConfig(null, null, null);
        assertThat(handler.cast(42, config)).isEqualTo(42L);
    }

    @Test
    void cast_stringValue_parsesToLong() {
        var config = new IntegerFieldConfig(null, null, null);
        assertThat(handler.cast("123", config)).isEqualTo(123L);
    }

    @Test
    void cast_invalidString_throws() {
        var config = new IntegerFieldConfig(null, null, null);
        assertThatThrownBy(() -> handler.cast("abc", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cast_unsupportedType_throws() {
        var config = new IntegerFieldConfig(null, null, null);
        assertThatThrownBy(() -> handler.cast(new Object(), config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cast_nullValue_returnsNull() {
        var config = new IntegerFieldConfig(null, null, null);
        assertThat(handler.cast(null, config)).isNull();
    }

    // --- validateValue ---

    @Test
    void validateValue_withinRange_passes() {
        var config = new IntegerFieldConfig(0L, 100L, null);
        assertThatNoException().isThrownBy(() -> handler.validateValue(50L, config));
    }

    @Test
    void validateValue_belowMin_throws() {
        var config = new IntegerFieldConfig(10L, null, null);
        assertThatThrownBy(() -> handler.validateValue(5L, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_aboveMax_throws() {
        var config = new IntegerFieldConfig(null, 100L, null);
        assertThatThrownBy(() -> handler.validateValue(200L, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_nullValue_passes() {
        var config = new IntegerFieldConfig(10L, 100L, null);
        assertThatNoException().isThrownBy(() -> handler.validateValue(null, config));
    }

    // --- validateConfig ---

    @Test
    void validateConfig_validConfig_passes() {
        var config = new IntegerFieldConfig(0L, 100L, 50L);
        assertThatNoException().isThrownBy(() -> handler.validateConfig(config));
    }

    @Test
    void validateConfig_minGreaterThanMax_throws() {
        var config = new IntegerFieldConfig(100L, 10L, null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    // --- columnType ---

    @Test
    void columnType_returnsBigint() {
        var config = new IntegerFieldConfig(null, null, null);
        assertThat(handler.columnType(config)).isEqualTo(SQLDataType.BIGINT);
    }

    // --- supportedOperators ---

    @Test
    void supportedOperators_containsExpected() {
        assertThat(handler.supportedOperators()).containsExactlyInAnyOrder(
                Operator.EQ, Operator.NEQ, Operator.GT, Operator.GTE,
                Operator.LT, Operator.LTE, Operator.IN, Operator.ISNULL
        );
    }

    // --- buildCondition ---

    private Field<Object> testColumn() {
        return DSL.field(DSL.name("cf_count"));
    }

    @Test
    void buildCondition_eq() {
        Condition c = handler.buildCondition(testColumn(), Operator.EQ, 42L, null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_gt() {
        Condition c = handler.buildCondition(testColumn(), Operator.GT, 10L, null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_gte() {
        Condition c = handler.buildCondition(testColumn(), Operator.GTE, 10L, null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_lt() {
        Condition c = handler.buildCondition(testColumn(), Operator.LT, 100L, null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_lte() {
        Condition c = handler.buildCondition(testColumn(), Operator.LTE, 100L, null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_in() {
        Condition c = handler.buildCondition(testColumn(), Operator.IN, List.of(1L, 2L, 3L), null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_isNull() {
        Condition c = handler.buildCondition(testColumn(), Operator.ISNULL, true, null);
        assertThat(c.toString()).containsIgnoringCase("null");
    }

    @Test
    void buildCondition_unsupported_throws() {
        assertThatThrownBy(() -> handler.buildCondition(testColumn(), Operator.LIKE, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
