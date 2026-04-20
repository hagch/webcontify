package io.webcontify.content.management.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.SortField;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CursorPaginationBuilderTest {

    private CursorPaginationBuilder builder;
    private FieldTypeRegistry registry;
    private ContentDefinition def;

    @BeforeEach
    void setUp() {
        builder = new CursorPaginationBuilder();
        registry = mock(FieldTypeRegistry.class);

        var textHandler = mock(FieldTypeHandler.class);
        when(textHandler.columnType(null)).thenAnswer(inv -> SQLDataType.VARCHAR(4000));
        when(registry.get(FieldType.TEXT)).thenReturn(textHandler);

        var intHandler = mock(FieldTypeHandler.class);
        when(intHandler.columnType(null)).thenAnswer(inv -> SQLDataType.BIGINT);
        when(registry.get(FieldType.INTEGER)).thenReturn(intHandler);

        def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setSlug("articles");

        var titleField = new FieldDefinition();
        titleField.setFieldKey("title");
        titleField.setFieldType(FieldType.TEXT);

        var countField = new FieldDefinition();
        countField.setFieldKey("count");
        countField.setFieldType(FieldType.INTEGER);

        def.setFields(List.of(titleField, countField));
    }

    @Test
    void encodeDecode_roundTrip() {
        var sorts = List.of(new SortCriterion("cf_title", SortCriterion.Direction.ASC));
        var lastValues = List.<Object>of("Hello");
        UUID lastId = UUID.randomUUID();

        String cursor = builder.encodeCursor(sorts, lastValues, lastId);
        assertThat(cursor).isNotBlank();

        var decoded = builder.decodeCursor(cursor);
        assertThat(decoded.sorts()).isEqualTo(sorts);
        assertThat(decoded.lastId()).isEqualTo(lastId);
        assertThat(decoded.lastValues()).hasSize(1);
        assertThat(decoded.lastValues().getFirst()).isEqualTo("Hello");
    }

    @Test
    void decodeCursor_invalidBase64_throws() {
        assertThatThrownBy(() -> builder.decodeCursor("not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or stale cursor");
    }

    @Test
    void decodeCursor_invalidJson_throws() {
        String badCursor = java.util.Base64.getEncoder().encodeToString("not json".getBytes());
        assertThatThrownBy(() -> builder.decodeCursor(badCursor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildKeysetCondition_singleSort_generatesCondition() {
        var sorts = List.of(new SortCriterion("cf_title", SortCriterion.Direction.ASC));
        var lastValues = List.<Object>of("Hello");
        UUID lastId = UUID.randomUUID();

        Condition condition = builder.buildKeysetCondition(sorts, lastValues, lastId, def, registry);

        // Should produce: (cf_title > 'Hello' OR cf_title IS NULL) OR (cf_title = 'Hello' AND id > lastId)
        assertThat(condition).isNotNull();
        String sql = condition.toString();
        assertThat(sql).contains("cf_title");
    }

    @Test
    void buildKeysetCondition_multiSort_generatesCondition() {
        var sorts = List.of(
                new SortCriterion("cf_title", SortCriterion.Direction.ASC),
                new SortCriterion("cf_count", SortCriterion.Direction.DESC));
        var lastValues = List.<Object>of("Hello", 42L);
        UUID lastId = UUID.randomUUID();

        Condition condition = builder.buildKeysetCondition(sorts, lastValues, lastId, def, registry);

        assertThat(condition).isNotNull();
        String sql = condition.toString();
        assertThat(sql).contains("cf_title");
        assertThat(sql).contains("cf_count");
    }

    @Test
    void buildKeysetCondition_nullLastValue_handlesNullsLast() {
        var sorts = List.of(new SortCriterion("cf_title", SortCriterion.Direction.ASC));
        var lastValues = new java.util.ArrayList<Object>();
        lastValues.add(null);
        UUID lastId = UUID.randomUUID();

        Condition condition = builder.buildKeysetCondition(sorts, lastValues, lastId, def, registry);

        // For ASC with null last value: only tiebreaker should apply (NULL is last, nothing after)
        assertThat(condition).isNotNull();
        String sql = condition.toString();
        assertThat(sql).contains("id");
    }

    @Test
    void buildOrderBy_singleSort_appendsIdTiebreaker() {
        var sorts = List.of(new SortCriterion("cf_title", SortCriterion.Direction.ASC));

        List<SortField<?>> orderBy = builder.buildOrderBy(sorts, def, registry);

        assertThat(orderBy).hasSize(2);
        assertThat(orderBy.getLast().toString()).contains("id");
    }

    @Test
    void buildOrderBy_descSort_usesDescNullsFirst() {
        var sorts = List.of(new SortCriterion("cf_count", SortCriterion.Direction.DESC));

        List<SortField<?>> orderBy = builder.buildOrderBy(sorts, def, registry);

        assertThat(orderBy).hasSize(2);
        String firstSort = orderBy.getFirst().toString();
        assertThat(firstSort).containsIgnoringCase("desc");
    }

    @Test
    void buildOrderBy_emptySorts_onlyIdTiebreaker() {
        List<SortField<?>> orderBy = builder.buildOrderBy(List.of(), def, registry);

        assertThat(orderBy).hasSize(1);
        assertThat(orderBy.getFirst().toString()).contains("id");
    }
}
