package io.webcontify.content.fieldtype;

import io.webcontify.content.definition.model.FieldConfig;
import io.webcontify.content.definition.model.FieldType;
import org.jooq.DataType;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class FieldTypeRegistryTest {

    @Test
    void get_existingType_returnsHandler() {
        var handler = stubHandler(FieldType.TEXT);
        var registry = new FieldTypeRegistry(List.of(handler));

        assertThat(registry.get(FieldType.TEXT)).isSameAs(handler);
    }

    @Test
    void get_missingType_throwsIllegalState() {
        var registry = new FieldTypeRegistry(List.of());

        assertThatThrownBy(() -> registry.get(FieldType.TEXT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No handler for TEXT");
    }

    @Test
    void constructor_multipleHandlers_allRegistered() {
        var textHandler = stubHandler(FieldType.TEXT);
        var intHandler = stubHandler(FieldType.INTEGER);
        var registry = new FieldTypeRegistry(List.of(textHandler, intHandler));

        assertThat(registry.get(FieldType.TEXT)).isSameAs(textHandler);
        assertThat(registry.get(FieldType.INTEGER)).isSameAs(intHandler);
    }

    private FieldTypeHandler stubHandler(FieldType type) {
        return new FieldTypeHandler() {
            @Override public FieldType fieldType() { return type; }
            @Override public void validateConfig(FieldConfig config) { }
            @Override public DataType<?> columnType(FieldConfig config) { return SQLDataType.VARCHAR; }
            @Override public Set<Operator> supportedOperators() { return Set.of(); }
        };
    }
}
