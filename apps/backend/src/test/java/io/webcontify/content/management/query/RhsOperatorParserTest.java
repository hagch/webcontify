package io.webcontify.content.management.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.content.fieldtype.handler.*;
import io.webcontify.shared.exception.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RhsOperatorParserTest {

    private RhsOperatorParser parser;
    private FieldTypeRegistry registry;
    private ContentDefinition def;

    @BeforeEach
    void setUp() {
        registry = new FieldTypeRegistry(List.of(
                new TextFieldTypeHandler(),
                new IntegerFieldTypeHandler(),
                new DecimalFieldTypeHandler(),
                new BooleanFieldTypeHandler(),
                new DateFieldTypeHandler(),
                new EnumFieldTypeHandler(),
                new ReferenceFieldTypeHandler(),
                new MirrorFieldTypeHandler()));
        parser = new RhsOperatorParser(registry);

        def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setSlug("articles");

        var titleField = new FieldDefinition();
        titleField.setFieldKey("title");
        titleField.setFieldType(FieldType.TEXT);

        var countField = new FieldDefinition();
        countField.setFieldKey("count");
        countField.setFieldType(FieldType.INTEGER);

        var activeField = new FieldDefinition();
        activeField.setFieldKey("active");
        activeField.setFieldType(FieldType.BOOLEAN);

        def.setFields(List.of(titleField, countField, activeField));
    }

    @Test
    void parse_eqOperator_returnsFilter() {
        var filters = parser.parse(Map.of("cf_title", "eq:Hello"), def, registry);

        assertThat(filters).hasSize(1);
        assertThat(filters.getFirst().column()).isEqualTo("cf_title");
        assertThat(filters.getFirst().operator()).isEqualTo(Operator.EQ);
        assertThat(filters.getFirst().rawValue()).isEqualTo("Hello");
    }

    @Test
    void parse_gteOperator_returnsFilterWithCastedValue() {
        var filters = parser.parse(Map.of("cf_count", "gte:5"), def, registry);

        assertThat(filters).hasSize(1);
        assertThat(filters.getFirst().operator()).isEqualTo(Operator.GTE);
        assertThat(filters.getFirst().rawValue()).isEqualTo(5L);
    }

    @Test
    void parse_likeOperator_forText() {
        var filters = parser.parse(Map.of("cf_title", "like:world"), def, registry);

        assertThat(filters).hasSize(1);
        assertThat(filters.getFirst().operator()).isEqualTo(Operator.LIKE);
        assertThat(filters.getFirst().rawValue()).isEqualTo("world");
    }

    @Test
    void parse_inOperator_splitsByComma() {
        var filters = parser.parse(Map.of("cf_title", "in:a,b,c"), def, registry);

        assertThat(filters).hasSize(1);
        assertThat(filters.getFirst().operator()).isEqualTo(Operator.IN);
        assertThat(filters.getFirst().rawValue()).isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void parse_isNullOperator() {
        var filters = parser.parse(Map.of("cf_title", "isnull:true"), def, registry);

        assertThat(filters).hasSize(1);
        assertThat(filters.getFirst().operator()).isEqualTo(Operator.ISNULL);
        assertThat(filters.getFirst().rawValue()).isEqualTo(true);
    }

    @Test
    void parse_unsupportedOperatorForType_throws() {
        // LIKE is not supported for BOOLEAN
        assertThatThrownBy(() -> parser.parse(Map.of("cf_active", "like:true"), def, registry))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parse_unknownOperator_throws() {
        assertThatThrownBy(() -> parser.parse(Map.of("cf_title", "foo:bar"), def, registry))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parse_nonCfParamsIgnored() {
        var filters = parser.parse(Map.of("page", "1", "size", "20", "cf_title", "eq:Hello"), def, registry);
        assertThat(filters).hasSize(1);
    }

    @Test
    void parse_unknownFieldIgnored() {
        var filters = parser.parse(Map.of("cf_nonexistent", "eq:val"), def, registry);
        assertThat(filters).isEmpty();
    }

    @Test
    void parse_multipleFilters() {
        var filters = parser.parse(Map.of("cf_title", "eq:Test", "cf_count", "gt:10"), def, registry);
        assertThat(filters).hasSize(2);
    }

    @Test
    void parse_missingColon_throws() {
        assertThatThrownBy(() -> parser.parse(Map.of("cf_title", "eqHello"), def, registry))
                .isInstanceOf(ValidationException.class);
    }
}
