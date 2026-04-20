package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.TextFieldConfig;
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

class TextFieldTypeHandlerTest {

    private TextFieldTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TextFieldTypeHandler();
    }

    @Test
    void fieldType_returnsText() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.TEXT);
    }

    // --- cast ---

    @Test
    void cast_nonNullValue_returnsString() {
        var config = new TextFieldConfig(null, null, null, null);
        assertThat(handler.cast(42, config)).isEqualTo("42");
    }

    @Test
    void cast_stringValue_returnsAsIs() {
        var config = new TextFieldConfig(null, null, null, null);
        assertThat(handler.cast("hello", config)).isEqualTo("hello");
    }

    @Test
    void cast_nullValue_returnsNull() {
        var config = new TextFieldConfig(null, null, null, null);
        assertThat(handler.cast(null, config)).isNull();
    }

    // --- validateValue ---

    @Test
    void validateValue_validString_passes() {
        var config = new TextFieldConfig(2, 10, null, null);
        assertThatNoException().isThrownBy(() -> handler.validateValue("hello", config));
    }

    @Test
    void validateValue_belowMinLength_throws() {
        var config = new TextFieldConfig(5, null, null, null);
        assertThatThrownBy(() -> handler.validateValue("ab", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_exceedsMaxLength_throws() {
        var config = new TextFieldConfig(null, 3, null, null);
        assertThatThrownBy(() -> handler.validateValue("toolong", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_patternMismatch_throws() {
        var config = new TextFieldConfig(null, null, "^[a-z]+$", null);
        assertThatThrownBy(() -> handler.validateValue("ABC123", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_patternMatch_passes() {
        var config = new TextFieldConfig(null, null, "^[a-z]+$", null);
        assertThatNoException().isThrownBy(() -> handler.validateValue("abc", config));
    }

    @Test
    void validateValue_nullValue_passes() {
        var config = new TextFieldConfig(5, 10, null, null);
        assertThatNoException().isThrownBy(() -> handler.validateValue(null, config));
    }

    // --- validateConfig ---

    @Test
    void validateConfig_validConfig_passes() {
        var config = new TextFieldConfig(1, 100, "[a-z]+", "hello");
        assertThatNoException().isThrownBy(() -> handler.validateConfig(config));
    }

    @Test
    void validateConfig_minGreaterThanMax_throws() {
        var config = new TextFieldConfig(100, 10, null, null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_invalidRegex_throws() {
        var config = new TextFieldConfig(null, null, "[invalid", null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    // --- columnType ---

    @Test
    void columnType_withMaxLength_returnsVarchar() {
        var config = new TextFieldConfig(null, 200, null, null);
        var type = handler.columnType(config);
        assertThat(type.length()).isEqualTo(200);
    }

    @Test
    void columnType_noMaxLength_returnsVarchar4000() {
        var config = new TextFieldConfig(null, null, null, null);
        var type = handler.columnType(config);
        assertThat(type.length()).isEqualTo(4000);
    }

    @Test
    void columnType_largeMaxLength_returnsClob() {
        var config = new TextFieldConfig(null, 15000, null, null);
        var type = handler.columnType(config);
        assertThat(type).isEqualTo(org.jooq.impl.SQLDataType.CLOB);
    }

    // --- supportedOperators ---

    @Test
    void supportedOperators_containsExpected() {
        assertThat(handler.supportedOperators()).containsExactlyInAnyOrder(
                Operator.EQ, Operator.NEQ, Operator.LIKE, Operator.IN, Operator.ISNULL
        );
    }

    // --- isVirtual ---

    @Test
    void isVirtual_returnsFalse() {
        assertThat(handler.isVirtual()).isFalse();
    }

    // --- buildCondition ---

    private Field<Object> testColumn() {
        return DSL.field(DSL.name("cf_title"));
    }

    @Test
    void buildCondition_eq_returnsEqualCondition() {
        Condition c = handler.buildCondition(testColumn(), Operator.EQ, "Hello", null);
        assertThat(c.toString()).contains("cf_title");
    }

    @Test
    void buildCondition_neq_returnsNotEqualCondition() {
        Condition c = handler.buildCondition(testColumn(), Operator.NEQ, "Hello", null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_like_wrapsValue() {
        Condition c = handler.buildCondition(testColumn(), Operator.LIKE, "world", null);
        assertThat(c.toString()).containsIgnoringCase("like");
    }

    @Test
    void buildCondition_in_acceptsList() {
        Condition c = handler.buildCondition(testColumn(), Operator.IN, List.of("a", "b"), null);
        assertThat(c.toString()).contains("cf_title");
    }

    @Test
    void buildCondition_isNull_true() {
        Condition c = handler.buildCondition(testColumn(), Operator.ISNULL, true, null);
        assertThat(c.toString()).containsIgnoringCase("null");
    }

    @Test
    void buildCondition_isNull_false_returnsNotNull() {
        Condition c = handler.buildCondition(testColumn(), Operator.ISNULL, false, null);
        assertThat(c.toString()).containsIgnoringCase("not null");
    }

    @Test
    void buildCondition_unsupported_throws() {
        assertThatThrownBy(() -> handler.buildCondition(testColumn(), Operator.GT, "x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
