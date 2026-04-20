package io.webcontify.content.definition.service;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.fieldtype.handler.*;
import io.webcontify.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ContentFieldConfigValidatorTest {

    private ContentFieldConfigValidator validator;

    @BeforeEach
    void setUp() {
        var registry = new FieldTypeRegistry(List.of(
                new TextFieldTypeHandler(),
                new IntegerFieldTypeHandler(),
                new DecimalFieldTypeHandler(),
                new BooleanFieldTypeHandler(),
                new DateFieldTypeHandler(),
                new EnumFieldTypeHandler(),
                new ReferenceFieldTypeHandler(),
                new MirrorFieldTypeHandler()
        ));
        validator = new ContentFieldConfigValidator(registry);
    }

    @Test
    void validate_textValidConfig_passes() {
        var config = new TextFieldConfig(1, 100, null, "hello");
        assertThatNoException().isThrownBy(() -> validator.validate(FieldType.TEXT, config));
    }

    @Test
    void validate_textMinLengthGreaterThanMaxLength_throws() {
        var config = new TextFieldConfig(100, 10, null, null);
        assertThatThrownBy(() -> validator.validate(FieldType.TEXT, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_textInvalidRegexPattern_throws() {
        var config = new TextFieldConfig(null, null, "[invalid", null);
        assertThatThrownBy(() -> validator.validate(FieldType.TEXT, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_textDefaultValueExceedsMaxLength_throws() {
        var config = new TextFieldConfig(null, 5, null, "toolongvalue");
        assertThatThrownBy(() -> validator.validate(FieldType.TEXT, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_integerValidConfig_passes() {
        var config = new IntegerFieldConfig(0L, 100L, 50L);
        assertThatNoException().isThrownBy(() -> validator.validate(FieldType.INTEGER, config));
    }

    @Test
    void validate_integerMinGreaterThanMax_throws() {
        var config = new IntegerFieldConfig(100L, 10L, null);
        assertThatThrownBy(() -> validator.validate(FieldType.INTEGER, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_integerDefaultOutOfRange_throws() {
        var config = new IntegerFieldConfig(0L, 10L, 50L);
        assertThatThrownBy(() -> validator.validate(FieldType.INTEGER, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_decimalPrecisionLessThanScale_throws() {
        var config = new DecimalFieldConfig(null, null, 3, 5, null);
        assertThatThrownBy(() -> validator.validate(FieldType.DECIMAL, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_decimalMinGreaterThanMax_throws() {
        var config = new DecimalFieldConfig(BigDecimal.TEN, BigDecimal.ONE, null, null, null);
        assertThatThrownBy(() -> validator.validate(FieldType.DECIMAL, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_enumEmptyAllowedValues_throws() {
        var config = new EnumFieldConfig(List.of(), null);
        assertThatThrownBy(() -> validator.validate(FieldType.ENUM, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_enumDefaultNotInAllowed_throws() {
        var config = new EnumFieldConfig(List.of("A", "B"), "C");
        assertThatThrownBy(() -> validator.validate(FieldType.ENUM, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_dateMinAfterMax_throws() {
        var config = new DateFieldConfig(
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), null, null);
        assertThatThrownBy(() -> validator.validate(FieldType.DATE, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_referenceNullTargetDefinitionId_throws() {
        var config = new ReferenceFieldConfig(null, RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        assertThatThrownBy(() -> validator.validate(FieldType.REFERENCE, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_referenceNullRelationType_throws() {
        var config = new ReferenceFieldConfig(java.util.UUID.randomUUID(), null, OnDeleteStrategy.CASCADE);
        assertThatThrownBy(() -> validator.validate(FieldType.REFERENCE, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_mirrorNullSourceDefinitionId_throws() {
        var config = new MirrorFieldConfig(null, java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        assertThatThrownBy(() -> validator.validate(FieldType.MIRROR, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_nullConfig_passes() {
        assertThatNoException().isThrownBy(() -> validator.validate(FieldType.TEXT, null));
    }

    @Test
    void validate_booleanConfig_passes() {
        var config = new BooleanFieldConfig(true);
        assertThatNoException().isThrownBy(() -> validator.validate(FieldType.BOOLEAN, config));
    }
}
