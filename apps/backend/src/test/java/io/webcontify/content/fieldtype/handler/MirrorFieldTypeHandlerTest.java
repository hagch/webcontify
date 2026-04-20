package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.MirrorFieldConfig;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.ValidationException;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MirrorFieldTypeHandlerTest {

    private MirrorFieldTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MirrorFieldTypeHandler();
    }

    @Test
    void fieldType_returnsMirror() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.MIRROR);
    }

    @Test
    void isVirtual_returnsTrue() {
        assertThat(handler.isVirtual()).isTrue();
    }

    // --- cast ---

    @Test
    void cast_anyValue_returnsNull() {
        var config = new MirrorFieldConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(handler.cast("something", config)).isNull();
    }

    @Test
    void cast_nullValue_returnsNull() {
        var config = new MirrorFieldConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThat(handler.cast(null, config)).isNull();
    }

    // --- validateValue ---

    @Test
    void validateValue_anyValue_throws() {
        var config = new MirrorFieldConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> handler.validateValue("anything", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateValue_nullValue_throws() {
        var config = new MirrorFieldConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> handler.validateValue(null, config))
                .isInstanceOf(ValidationException.class);
    }

    // --- validateConfig ---

    @Test
    void validateConfig_validConfig_passes() {
        var config = new MirrorFieldConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatNoException().isThrownBy(() -> handler.validateConfig(config));
    }

    @Test
    void validateConfig_nullSourceDefinitionId_throws() {
        var config = new MirrorFieldConfig(null, UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_nullSourceFieldId_throws() {
        var config = new MirrorFieldConfig(UUID.randomUUID(), null, UUID.randomUUID());
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_nullOwnerAppId_throws() {
        var config = new MirrorFieldConfig(UUID.randomUUID(), UUID.randomUUID(), null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_nullConfig_passes() {
        assertThatNoException().isThrownBy(() -> handler.validateConfig(null));
    }

    // --- columnType ---

    @Test
    void columnType_throwsUnsupportedOperation() {
        var config = new MirrorFieldConfig(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> handler.columnType(config))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Mirror fields have no column");
    }

    // --- supportedOperators ---

    @Test
    void supportedOperators_returnsEmptySet() {
        assertThat(handler.supportedOperators()).isEmpty();
    }

    // --- buildCondition ---

    @Test
    void buildCondition_throwsUnsupportedOperation() {
        Field<Object> col = DSL.field(DSL.name("cf_mirror"));
        assertThatThrownBy(() -> handler.buildCondition(col, Operator.EQ, "x", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
