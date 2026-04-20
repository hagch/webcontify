package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.DateFieldConfig;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.ValidationException;
import java.time.OffsetDateTime;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class DateFieldTypeHandlerTest {

    private DateFieldTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DateFieldTypeHandler();
    }

    @Test
    void fieldType_returnsDate() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.DATE);
    }

    // --- cast ---

    @Test
    void cast_offsetDateTime_returnsAsIs() {
        var config = new DateFieldConfig(null, null, null, null);
        var now = OffsetDateTime.now();
        assertThat(handler.cast(now, config)).isEqualTo(now);
    }

    @Test
    void cast_isoString_parsesToOffsetDateTime() {
        var config = new DateFieldConfig(null, null, null, null);
        var result = handler.cast("2026-04-16T10:30:00+02:00", config);
        assertThat(result).isEqualTo(OffsetDateTime.of(2026, 4, 16, 10, 30, 0, 0, ZoneOffset.ofHours(2)));
    }

    @Test
    void cast_invalidString_throws() {
        var config = new DateFieldConfig(null, null, null, null);
        assertThatThrownBy(() -> handler.cast("not-a-date", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cast_unsupportedType_throws() {
        var config = new DateFieldConfig(null, null, null, null);
        assertThatThrownBy(() -> handler.cast(12345, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cast_nullValue_returnsNull() {
        var config = new DateFieldConfig(null, null, null, null);
        assertThat(handler.cast(null, config)).isNull();
    }

    // --- validateConfig ---

    @Test
    void validateConfig_validConfig_passes() {
        var config = new DateFieldConfig(
                java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2026, 12, 31),
                null, null);
        assertThatNoException().isThrownBy(() -> handler.validateConfig(config));
    }

    @Test
    void validateConfig_minAfterMax_throws() {
        var config = new DateFieldConfig(
                java.time.LocalDate.of(2026, 12, 31),
                java.time.LocalDate.of(2026, 1, 1),
                null, null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_nullConfig_passes() {
        assertThatNoException().isThrownBy(() -> handler.validateConfig(null));
    }

    // --- columnType ---

    @Test
    void columnType_returnsTimestampWithTimezone() {
        var config = new DateFieldConfig(null, null, null, null);
        assertThat(handler.columnType(config)).isEqualTo(SQLDataType.TIMESTAMPWITHTIMEZONE);
    }

    // --- supportedOperators ---

    @Test
    void supportedOperators_containsExpected() {
        assertThat(handler.supportedOperators()).containsExactlyInAnyOrder(
                Operator.EQ, Operator.NEQ, Operator.GT, Operator.GTE,
                Operator.LT, Operator.LTE, Operator.ISNULL
        );
    }

    // --- formatForDisplay ---

    @Test
    void formatForDisplay_returnsIsoFormat() {
        var config = new DateFieldConfig(null, null, null, null);
        var dt = OffsetDateTime.of(2026, 4, 16, 10, 30, 0, 0, ZoneOffset.UTC);
        var result = handler.formatForDisplay(dt, config);
        assertThat(result).isEqualTo("2026-04-16T10:30:00Z");
    }

    @Test
    void formatForDisplay_nullValue_returnsEmptyString() {
        var config = new DateFieldConfig(null, null, null, null);
        assertThat(handler.formatForDisplay(null, config)).isEmpty();
    }

    // --- buildCondition ---

    private Field<Object> testColumn() {
        return DSL.field(DSL.name("cf_date"));
    }

    @Test
    void buildCondition_eq() {
        Condition c = handler.buildCondition(testColumn(), Operator.EQ, OffsetDateTime.now(), null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_gt() {
        Condition c = handler.buildCondition(testColumn(), Operator.GT, OffsetDateTime.now(), null);
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
