package io.webcontify.content.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.shared.exception.ValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentValidationServiceTest {

    @Mock private FieldTypeRegistry fieldTypeRegistry;
    @Mock private FieldTypeHandler textHandler;
    @Mock private FieldTypeHandler mirrorHandler;

    private ContentValidationService service;

    @BeforeEach
    void setUp() {
        service = new ContentValidationService(fieldTypeRegistry);
    }

    @Test
    void validateAndCast_happyPath_returnsCastedValues() {
        var def = createDefinition(List.of(createField("title", FieldType.TEXT, false)));
        when(fieldTypeRegistry.get(FieldType.TEXT)).thenReturn(textHandler);
        when(textHandler.isVirtual()).thenReturn(false);
        when(textHandler.cast("hello", null)).thenReturn("hello");

        Map<String, Object> result = service.validateAndCast(Map.of("cf_title", "hello"), def);

        assertThat(result).containsEntry("cf_title", "hello");
    }

    @Test
    void validateAndCast_castFailure_throwsValidationException() {
        var def = createDefinition(List.of(createField("count", FieldType.INTEGER, false)));
        var intHandler = mock(FieldTypeHandler.class);
        when(fieldTypeRegistry.get(FieldType.INTEGER)).thenReturn(intHandler);
        when(intHandler.isVirtual()).thenReturn(false);
        when(intHandler.cast("notANumber", null)).thenThrow(new RuntimeException("bad"));

        assertThatThrownBy(() -> service.validateAndCast(Map.of("cf_count", "notANumber"), def))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    var ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).hasSize(1);
                    assertThat(ve.getFieldErrors().get(0).field()).isEqualTo("cf_count");
                    assertThat(ve.getFieldErrors().get(0).message()).isEqualTo("backendError.invalid_value");
                });
    }

    @Test
    void validateAndCast_requiredFieldMissing_throwsValidationException() {
        var def = createDefinition(List.of(createField("title", FieldType.TEXT, true)));
        when(fieldTypeRegistry.get(FieldType.TEXT)).thenReturn(textHandler);
        when(textHandler.isVirtual()).thenReturn(false);

        assertThatThrownBy(() -> service.validateAndCast(Map.of(), def))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    var ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).hasSize(1);
                    assertThat(ve.getFieldErrors().get(0).field()).isEqualTo("cf_title");
                    assertThat(ve.getFieldErrors().get(0).message()).isEqualTo("backendError.required");
                });
    }

    @Test
    void validateAndCast_validateValueFails_throwsValidationException() {
        var def = createDefinition(List.of(createField("title", FieldType.TEXT, false)));
        when(fieldTypeRegistry.get(FieldType.TEXT)).thenReturn(textHandler);
        when(textHandler.isVirtual()).thenReturn(false);
        when(textHandler.cast("x", null)).thenReturn("x");
        doThrow(new RuntimeException("too short")).when(textHandler).validateValue("x", null);

        assertThatThrownBy(() -> service.validateAndCast(Map.of("cf_title", "x"), def))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    var ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).hasSize(1);
                    assertThat(ve.getFieldErrors().get(0).message()).isEqualTo("backendError.invalid_value");
                });
    }

    @Test
    void validateAndCast_skipsVirtualFields() {
        var def = createDefinition(List.of(createField("mirror_field", FieldType.MIRROR, false)));
        when(fieldTypeRegistry.get(FieldType.MIRROR)).thenReturn(mirrorHandler);
        when(mirrorHandler.isVirtual()).thenReturn(true);

        Map<String, Object> result = service.validateAndCast(Map.of(), def);

        assertThat(result).isEmpty();
    }

    @Test
    void validateAndCast_nullOptionalField_includesNullValue() {
        var def = createDefinition(List.of(createField("optional", FieldType.TEXT, false)));
        when(fieldTypeRegistry.get(FieldType.TEXT)).thenReturn(textHandler);
        when(textHandler.isVirtual()).thenReturn(false);

        Map<String, Object> result = service.validateAndCast(Map.of(), def);

        assertThat(result).containsEntry("cf_optional", null);
    }

    @Test
    void validateAndCast_emptyDefinitionFields_returnsEmptyMap() {
        var def = createDefinition(List.of());

        Map<String, Object> result = service.validateAndCast(Map.of("cf_anything", "value"), def);

        assertThat(result).isEmpty();
    }

    @Test
    void validateAndCast_multipleErrors_collectsAllErrors() {
        var def = createDefinition(List.of(
                createField("title", FieldType.TEXT, true),
                createField("count", FieldType.INTEGER, true)));

        when(fieldTypeRegistry.get(FieldType.TEXT)).thenReturn(textHandler);
        when(textHandler.isVirtual()).thenReturn(false);

        var intHandler = mock(FieldTypeHandler.class);
        when(fieldTypeRegistry.get(FieldType.INTEGER)).thenReturn(intHandler);
        when(intHandler.isVirtual()).thenReturn(false);

        assertThatThrownBy(() -> service.validateAndCast(Map.of(), def))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    var ve = (ValidationException) ex;
                    assertThat(ve.getFieldErrors()).hasSize(2);
                });
    }

    private ContentDefinition createDefinition(List<FieldDefinition> fields) {
        var def = new ContentDefinition();
        def.setFields(fields);
        return def;
    }

    private FieldDefinition createField(String key, FieldType type, boolean required) {
        var field = new FieldDefinition();
        field.setFieldKey(key);
        field.setFieldType(type);
        field.setRequired(required);
        return field;
    }
}
