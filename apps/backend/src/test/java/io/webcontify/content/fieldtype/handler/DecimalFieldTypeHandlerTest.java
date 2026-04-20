package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.DecimalFieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.ValidationException;
import java.math.BigDecimal;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DecimalFieldTypeHandlerTest {

    private DecimalFieldTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DecimalFieldTypeHandler();
    }

    @Test
    void fieldType_returnsDecimal() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.DECIMAL);
    }

    // --- cast ---

    @Test
    void cast_bigDecimalValue_returnsAsIs() {
        var config = new DecimalFieldConfig(null, null, null, null, null);
        var bd = new BigDecimal("12.34");
        assertThat(handler.cast(bd, config)).isEqualTo(bd);
    }

    @Test
    void cast_integerValue_convertsToBigDecimal() {
        var config = new DecimalFieldConfig(null, null, null, null, null);
        assertThat((BigDecimal) handler.cast(42, config)).isEqualByComparingTo(new BigDecimal("42"));
    }

    @Test
    void cast_doubleValue_convertsToBigDecimal() {
        var config = new DecimalFieldConfig(null, null, null, null, null);
        var result = (BigDecimal) handler.cast(3.14, config);
        assertThat(result).isEqualByComparingTo(new BigDecimal("3.14"));
    }

    @Test
    void cast_stringValue_parsesToBigDecimal() {
        var config = new DecimalFieldConfig(null, null, null, null, null);
        assertThat(handler.cast("99.99", config)).isEqualTo(new BigDecimal("99.99"));
    }

    @Test
    void cast_invalidString_throws() {
        var config = new DecimalFieldConfig(null, null, null, null, null);
        assertThatThrownBy(() -> handler.cast("abc", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cast_unsupportedType_throws() {
        var config = new DecimalFieldConfig(null, null, null, null, null);
        assertThatThrownBy(() -> handler.cast(new Object(), config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cast_nullValue_returnsNull() {
        var config = new DecimalFieldConfig(null, null, null, null, null);
        assertThat(handler.cast(null, config)).isNull();
    }

    // --- validateValue ---

    @Test
    void validateValue_withinRange_passes() {
        var config = new DecimalFieldConfig(BigDecimal.ZERO, BigDecimal.TEN, null, null, null);
        assertThatNoException().isThrownBy(() -> handler.validateValue(new BigDecimal("5.5"), config));
    }

    @Test
    void validateValue_belowMin_throws() {
        var config = new DecimalFieldConfig(BigDecimal.TEN, null, null, null, null);
        assertThatThrownBy(() -> handler.validateValue(new BigDecimal("5"), config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_aboveMax_throws() {
        var config = new DecimalFieldConfig(null, BigDecimal.TEN, null, null, null);
        assertThatThrownBy(() -> handler.validateValue(new BigDecimal("20"), config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_nullValue_passes() {
        var config = new DecimalFieldConfig(BigDecimal.TEN, BigDecimal.TEN, null, null, null);
        assertThatNoException().isThrownBy(() -> handler.validateValue(null, config));
    }

    // --- validateConfig ---

    @Test
    void validateConfig_validConfig_passes() {
        var config = new DecimalFieldConfig(BigDecimal.ONE, BigDecimal.TEN, 10, 2, null);
        assertThatNoException().isThrownBy(() -> handler.validateConfig(config));
    }

    @Test
    void validateConfig_minGreaterThanMax_throws() {
        var config = new DecimalFieldConfig(BigDecimal.TEN, BigDecimal.ONE, null, null, null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_precisionLessThanScale_throws() {
        var config = new DecimalFieldConfig(null, null, 3, 5, null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    // --- columnType ---

    @Test
    void columnType_withPrecisionAndScale_returnsDecimal() {
        var config = new DecimalFieldConfig(null, null, 10, 2, null);
        var type = handler.columnType(config);
        assertThat(type.precision()).isEqualTo(10);
        assertThat(type.scale()).isEqualTo(2);
    }

    @Test
    void columnType_withDefaults_returnsDecimal19_4() {
        var config = new DecimalFieldConfig(null, null, null, null, null);
        var type = handler.columnType(config);
        assertThat(type.precision()).isEqualTo(19);
        assertThat(type.scale()).isEqualTo(4);
    }

    // --- supportedOperators ---

    @Test
    void supportedOperators_containsExpected() {
        assertThat(handler.supportedOperators()).containsExactlyInAnyOrder(
                Operator.EQ, Operator.NEQ, Operator.GT, Operator.GTE,
                Operator.LT, Operator.LTE, Operator.ISNULL
        );
    }

    // --- buildCondition ---

    private Field<Object> testColumn() {
        return DSL.field(DSL.name("cf_price"));
    }

    @Test
    void buildCondition_eq() {
        Condition c = handler.buildCondition(testColumn(), Operator.EQ, new BigDecimal("9.99"), null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_gt() {
        Condition c = handler.buildCondition(testColumn(), Operator.GT, new BigDecimal("0"), null);
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
