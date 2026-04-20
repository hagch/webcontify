package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.BooleanFieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.Operator;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BooleanFieldTypeHandlerTest {

    private BooleanFieldTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BooleanFieldTypeHandler();
    }

    @Test
    void fieldType_returnsBoolean() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.BOOLEAN);
    }

    @Test
    void cast_booleanTrue_returnsTrue() {
        var config = new BooleanFieldConfig(null);
        assertThat(handler.cast(true, config)).isEqualTo(true);
    }

    @Test
    void cast_booleanFalse_returnsFalse() {
        var config = new BooleanFieldConfig(null);
        assertThat(handler.cast(false, config)).isEqualTo(false);
    }

    @Test
    void cast_stringTrue_returnsTrue() {
        var config = new BooleanFieldConfig(null);
        assertThat(handler.cast("true", config)).isEqualTo(true);
    }

    @Test
    void cast_stringFalse_returnsFalse() {
        var config = new BooleanFieldConfig(null);
        assertThat(handler.cast("false", config)).isEqualTo(false);
    }

    @Test
    void cast_nullValue_returnsNull() {
        var config = new BooleanFieldConfig(null);
        assertThat(handler.cast(null, config)).isNull();
    }

    @Test
    void validateConfig_nullConfig_passes() {
        assertThatNoException().isThrownBy(() -> handler.validateConfig(null));
    }

    @Test
    void validateConfig_validConfig_passes() {
        var config = new BooleanFieldConfig(true);
        assertThatNoException().isThrownBy(() -> handler.validateConfig(config));
    }

    @Test
    void columnType_returnsBoolean() {
        var config = new BooleanFieldConfig(null);
        assertThat(handler.columnType(config)).isEqualTo(SQLDataType.BOOLEAN);
    }

    @Test
    void supportedOperators_containsExpected() {
        assertThat(handler.supportedOperators()).containsExactlyInAnyOrder(
                Operator.EQ, Operator.NEQ, Operator.ISNULL
        );
    }

    // --- buildCondition ---

    private Field<Object> testColumn() {
        return DSL.field(DSL.name("cf_active"));
    }

    @Test
    void buildCondition_eq() {
        Condition c = handler.buildCondition(testColumn(), Operator.EQ, true, null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_neq() {
        Condition c = handler.buildCondition(testColumn(), Operator.NEQ, false, null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_isNull() {
        Condition c = handler.buildCondition(testColumn(), Operator.ISNULL, true, null);
        assertThat(c.toString()).containsIgnoringCase("null");
    }

    @Test
    void buildCondition_unsupported_throws() {
        assertThatThrownBy(() -> handler.buildCondition(testColumn(), Operator.GT, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
