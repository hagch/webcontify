package io.webcontify.content.management.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisplayFormatResolverTest {

    @Mock private FieldTypeRegistry fieldTypeRegistry;
    @Mock private FieldTypeHandler textHandler;

    private DisplayFormatResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DisplayFormatResolver(fieldTypeRegistry);
    }

    @Test
    void resolve_withNullTemplate_returnsFallbackId() {
        var def = createDefinition(null);
        var id = UUID.randomUUID();

        String result = resolver.resolve(def, Map.of("id", id));

        assertThat(result).isEqualTo(id.toString());
    }

    @Test
    void resolve_withBlankTemplate_returnsFallbackId() {
        var def = createDefinition("  ");
        var id = UUID.randomUUID();

        String result = resolver.resolve(def, Map.of("id", id));

        assertThat(result).isEqualTo(id.toString());
    }

    @Test
    void resolve_withTemplate_replacesPlaceholders() {
        var def = createDefinition("{cf_first_name} {cf_last_name}");
        def.setFields(List.of(
                createField("first_name", FieldType.TEXT),
                createField("last_name", FieldType.TEXT)));

        when(fieldTypeRegistry.get(FieldType.TEXT)).thenReturn(textHandler);
        when(textHandler.formatForDisplay(any(), any())).thenAnswer(inv -> inv.getArgument(0).toString());

        String result = resolver.resolve(def, Map.of(
                "cf_first_name", "John",
                "cf_last_name", "Doe"));

        assertThat(result).isEqualTo("John Doe");
    }

    @Test
    void resolve_withNullValue_replacesWithEmptyString() {
        var def = createDefinition("{cf_name}");
        def.setFields(List.of(createField("name", FieldType.TEXT)));

        String result = resolver.resolve(def, Map.of("id", UUID.randomUUID()));

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_withUnknownPlaceholder_replacesWithEmptyString() {
        var def = createDefinition("{cf_unknown}");
        def.setFields(List.of(createField("name", FieldType.TEXT)));

        String result = resolver.resolve(def, Map.of("cf_name", "value"));

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_withNoIdInValues_returnsEmptyForNullTemplate() {
        var def = createDefinition(null);

        String result = resolver.resolve(def, Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void extractSearchFields_returnsPlaceholderKeys() {
        List<String> fields = resolver.extractSearchFields("{cf_first_name} {cf_last_name}");

        assertThat(fields).containsExactly("cf_first_name", "cf_last_name");
    }

    @Test
    void extractSearchFields_withNullTemplate_returnsEmptyList() {
        assertThat(resolver.extractSearchFields(null)).isEmpty();
    }

    @Test
    void extractSearchFields_withBlankTemplate_returnsEmptyList() {
        assertThat(resolver.extractSearchFields("  ")).isEmpty();
    }

    private ContentDefinition createDefinition(String displayFormat) {
        var def = new ContentDefinition();
        def.setDisplayFormat(displayFormat);
        def.setFields(List.of());
        return def;
    }

    private FieldDefinition createField(String key, FieldType type) {
        var field = new FieldDefinition();
        field.setFieldKey(key);
        field.setFieldType(type);
        return field;
    }
}
