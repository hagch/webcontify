package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.OnDeleteStrategy;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
import io.webcontify.content.definition.model.RelationType;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.shared.exception.ValidationException;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ReferenceFieldTypeHandlerTest {

    private ReferenceFieldTypeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReferenceFieldTypeHandler();
    }

    @Test
    void fieldType_returnsReference() {
        assertThat(handler.fieldType()).isEqualTo(FieldType.REFERENCE);
    }

    // --- cast ---

    @Test
    void cast_uuidValue_returnsAsIs() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        var uuid = UUID.randomUUID();
        assertThat(handler.cast(uuid, config)).isEqualTo(uuid);
    }

    @Test
    void cast_stringValue_parsesToUuid() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        var uuid = UUID.randomUUID();
        assertThat(handler.cast(uuid.toString(), config)).isEqualTo(uuid);
    }

    @Test
    void cast_invalidString_throws() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        assertThatThrownBy(() -> handler.cast("not-a-uuid", config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cast_unsupportedType_throws() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        assertThatThrownBy(() -> handler.cast(12345, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void cast_nullValue_returnsNull() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        assertThat(handler.cast(null, config)).isNull();
    }

    // --- validateConfig ---

    @Test
    void validateConfig_validConfig_passes() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        assertThatNoException().isThrownBy(() -> handler.validateConfig(config));
    }

    @Test
    void validateConfig_nullTargetDefinitionId_throws() {
        var config = new ReferenceFieldConfig(null, RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_nullRelationType_throws() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), null, OnDeleteStrategy.CASCADE);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_nullOnDelete_throws() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, null);
        assertThatThrownBy(() -> handler.validateConfig(config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateConfig_nullConfig_passes() {
        assertThatNoException().isThrownBy(() -> handler.validateConfig(null));
    }

    // --- columnType ---

    @Test
    void columnType_returnsUuid() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        assertThat(handler.columnType(config)).isEqualTo(SQLDataType.UUID);
    }

    // --- supportedOperators ---

    @Test
    void supportedOperators_containsExpected() {
        assertThat(handler.supportedOperators()).containsExactlyInAnyOrder(
                Operator.EQ, Operator.NEQ, Operator.IN, Operator.ISNULL
        );
    }

    // --- formatForDisplay ---

    @Test
    void formatForDisplay_returnsUuidString() {
        var config = new ReferenceFieldConfig(UUID.randomUUID(), RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        var uuid = UUID.randomUUID();
        assertThat(handler.formatForDisplay(uuid, config)).isEqualTo(uuid.toString());
    }

    // --- buildCondition ---

    private Field<Object> testColumn() {
        return DSL.field(DSL.name("cf_ref"));
    }

    @Test
    void buildCondition_eq() {
        Condition c = handler.buildCondition(testColumn(), Operator.EQ, UUID.randomUUID(), null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_in() {
        Condition c = handler.buildCondition(testColumn(), Operator.IN, List.of(UUID.randomUUID()), null);
        assertThat(c).isNotNull();
    }

    @Test
    void buildCondition_isNull() {
        Condition c = handler.buildCondition(testColumn(), Operator.ISNULL, true, null);
        assertThat(c.toString()).containsIgnoringCase("null");
    }

    @Test
    void buildCondition_unsupported_throws() {
        assertThatThrownBy(() -> handler.buildCondition(testColumn(), Operator.GT, UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
