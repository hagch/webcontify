package io.webcontify.content.management.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.webcontify.IntegrationTestBase;
import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.fieldtype.Operator;
import io.webcontify.content.management.ddl.DynamicTableManager;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.query.*;
import io.webcontify.shared.exception.OptimisticLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DedicatedTableStorageIntegrationTest extends IntegrationTestBase {

    @Autowired private DedicatedTableStorage storage;
    @Autowired private DynamicTableManager dynamicTableManager;
    @Autowired private DSLContext dsl;
    @Autowired private FieldTypeRegistry fieldTypeRegistry;
    @Autowired private FieldConfigConverter fieldConfigConverter;

    private static final String SCHEMA_NAME = "tenant_storage_test";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private ContentDefinition domainDef;
    private ContentDefinitionEntity ddlEntity;

    @BeforeEach
    void setUp() {
        dsl.execute("DROP SCHEMA IF EXISTS " + SCHEMA_NAME + " CASCADE");
        dsl.execute("CREATE SCHEMA " + SCHEMA_NAME);

        // Create DDL entity with TEXT "title" and INTEGER "count" fields
        ddlEntity = new ContentDefinitionEntity();
        ddlEntity.setId(UUID.randomUUID());
        ddlEntity.setSlug("articles");
        ddlEntity.setName("Articles");

        var titleField = createTextField("title", false, false, false);
        var countField = createIntegerField("count", false, false, false);
        ddlEntity.setFields(new ArrayList<>(List.of(titleField, countField)));

        dynamicTableManager.createTable(SCHEMA_NAME, ddlEntity);

        // Build the domain model ContentDefinition that mirrors the DDL entity
        domainDef = new ContentDefinition();
        domainDef.setId(ddlEntity.getId());
        domainDef.setSlug("articles");
        domainDef.setName("Articles");

        var titleFieldDef = new FieldDefinition();
        titleFieldDef.setFieldKey("title");
        titleFieldDef.setFieldType(FieldType.TEXT);

        var countFieldDef = new FieldDefinition();
        countFieldDef.setFieldKey("count");
        countFieldDef.setFieldType(FieldType.INTEGER);

        domainDef.setFields(List.of(titleFieldDef, countFieldDef));
    }

    @Test
    void save_insertsContentRowAndHistoryRow() {
        var entry = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Hello", "count", 42), USER_ID);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getVersion()).isEqualTo(0L);
        assertThat(entry.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(entry.getValues()).containsEntry("cf_title", "Hello");
        assertThat(entry.getValues().get("cf_count")).isEqualTo(42L);

        var history = storage.getHistory(SCHEMA_NAME, domainDef, entry.getId(), 10);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().action()).isEqualTo("CREATED");
    }

    @Test
    void findById_returnsSavedEntry() {
        var saved = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Test"), USER_ID);

        var found = storage.findById(SCHEMA_NAME, domainDef, saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getValues()).containsEntry("cf_title", "Test");
    }

    @Test
    void findById_returnsEmptyForSoftDeleted() {
        var saved = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "ToDelete"), USER_ID);
        storage.softDelete(SCHEMA_NAME, domainDef, saved.getId(), USER_ID);

        var found = storage.findById(SCHEMA_NAME, domainDef, saved.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void update_changesValuesAndIncrementsVersion() {
        var saved = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Original"), USER_ID);

        var updated = storage.update(SCHEMA_NAME, domainDef, saved.getId(), 0L,
                Map.of("title", "Updated"), OTHER_USER_ID);

        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getValues()).containsEntry("cf_title", "Updated");
        assertThat(updated.getUpdatedBy()).isEqualTo(OTHER_USER_ID);
    }

    @Test
    void update_throwsOptimisticLockExceptionOnVersionMismatch() {
        var saved = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Original"), USER_ID);

        assertThatThrownBy(() ->
                storage.update(SCHEMA_NAME, domainDef, saved.getId(), 999L,
                        Map.of("title", "Should Fail"), USER_ID))
                .isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void update_recordsDiffInHistory() {
        var saved = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Before", "count", 1), USER_ID);

        storage.update(SCHEMA_NAME, domainDef, saved.getId(), 0L,
                Map.of("title", "After", "count", 2), USER_ID);

        var history = storage.getHistory(SCHEMA_NAME, domainDef, saved.getId(), 10);
        assertThat(history).hasSize(2);

        // Most recent first
        var updateRecord = history.getFirst();
        assertThat(updateRecord.action()).isEqualTo("UPDATED");
        assertThat(updateRecord.changedFields()).containsKey("cf_title");
        assertThat(updateRecord.changedFields().get("cf_title").oldValue()).isEqualTo("Before");
        assertThat(updateRecord.changedFields().get("cf_title").newValue()).isEqualTo("After");
    }

    @Test
    void softDelete_setsDeletedAtAndInsertsHistory() {
        var saved = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "ToDelete"), USER_ID);

        storage.softDelete(SCHEMA_NAME, domainDef, saved.getId(), USER_ID);

        assertThat(storage.findById(SCHEMA_NAME, domainDef, saved.getId())).isEmpty();

        var history = storage.getHistory(SCHEMA_NAME, domainDef, saved.getId(), 10);
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().action()).isEqualTo("DELETED");
    }

    @Test
    void findAll_excludesSoftDeleted() {
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Keep"), USER_ID);
        var toDelete = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Remove"), USER_ID);
        storage.softDelete(SCHEMA_NAME, domainDef, toDelete.getId(), USER_ID);

        var all = storage.findAll(SCHEMA_NAME, domainDef, 100);

        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getValues()).containsEntry("cf_title", "Keep");
    }

    @Test
    void findByReferenceField_returnsMatchingEntries() {
        // Create a table with a UUID reference field
        var refEntity = new ContentDefinitionEntity();
        refEntity.setId(UUID.randomUUID());
        refEntity.setSlug("refs");
        refEntity.setName("Refs");
        var refField = new ContentFieldEntity();
        refField.setId(UUID.randomUUID());
        refField.setFieldKey("target");
        refField.setFieldType("REFERENCE");
        refField.setConfig("{\"targetDefinitionId\":\"" + UUID.randomUUID() + "\",\"relationType\":\"MANY_TO_ONE\",\"onDelete\":\"SET_NULL\"}");
        refEntity.setFields(new ArrayList<>(List.of(refField)));
        dynamicTableManager.createTable(SCHEMA_NAME, refEntity);

        var refDef = new ContentDefinition();
        refDef.setId(refEntity.getId());
        refDef.setSlug("refs");
        var refFieldDef = new FieldDefinition();
        refFieldDef.setFieldKey("target");
        refFieldDef.setFieldType(FieldType.REFERENCE);
        refDef.setFields(List.of(refFieldDef));

        UUID targetId = UUID.randomUUID();
        storage.save(SCHEMA_NAME, refDef, Map.of("target", targetId.toString()), USER_ID);
        storage.save(SCHEMA_NAME, refDef, Map.of("target", UUID.randomUUID().toString()), USER_ID);

        var results = storage.findByReferenceField(SCHEMA_NAME, refDef, "target", targetId);

        assertThat(results).hasSize(1);
    }

    @Test
    void existsByFieldValue_excludesSoftDeleted_andExcludeId() {
        var saved = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Unique"), USER_ID);

        assertThat(storage.existsByFieldValue(SCHEMA_NAME, domainDef, "title", "Unique", null)).isTrue();
        assertThat(storage.existsByFieldValue(SCHEMA_NAME, domainDef, "title", "Unique", saved.getId())).isFalse();

        storage.softDelete(SCHEMA_NAME, domainDef, saved.getId(), USER_ID);
        assertThat(storage.existsByFieldValue(SCHEMA_NAME, domainDef, "title", "Unique", null)).isFalse();
    }

    @Test
    void nullifyField_setsValueToNull() {
        // Create a table with a UUID reference field for nullify
        var nullEntity = new ContentDefinitionEntity();
        nullEntity.setId(UUID.randomUUID());
        nullEntity.setSlug("nulltest");
        nullEntity.setName("NullTest");
        var refField = new ContentFieldEntity();
        refField.setId(UUID.randomUUID());
        refField.setFieldKey("ref_field");
        refField.setFieldType("REFERENCE");
        refField.setConfig("{\"targetDefinitionId\":\"" + UUID.randomUUID() + "\",\"relationType\":\"MANY_TO_ONE\",\"onDelete\":\"SET_NULL\"}");
        refEntity(nullEntity, refField);
        dynamicTableManager.createTable(SCHEMA_NAME, nullEntity);

        var nullDef = new ContentDefinition();
        nullDef.setId(nullEntity.getId());
        nullDef.setSlug("nulltest");
        var fieldDef = new FieldDefinition();
        fieldDef.setFieldKey("ref_field");
        fieldDef.setFieldType(FieldType.REFERENCE);
        nullDef.setFields(List.of(fieldDef));

        UUID targetId = UUID.randomUUID();
        var saved = storage.save(SCHEMA_NAME, nullDef, Map.of("ref_field", targetId.toString()), USER_ID);

        storage.nullifyField(SCHEMA_NAME, nullDef, "ref_field", targetId, OTHER_USER_ID);

        var found = storage.findById(SCHEMA_NAME, nullDef, saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getValues().get("cf_ref_field")).isNull();
    }

    @Test
    void getHistory_returnsRecordsInDescendingOrder() {
        var saved = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "V1"), USER_ID);
        storage.update(SCHEMA_NAME, domainDef, saved.getId(), 0L, Map.of("title", "V2"), USER_ID);
        storage.update(SCHEMA_NAME, domainDef, saved.getId(), 1L, Map.of("title", "V3"), USER_ID);

        var history = storage.getHistory(SCHEMA_NAME, domainDef, saved.getId(), 10);

        assertThat(history).hasSize(3);
        assertThat(history.get(0).action()).isEqualTo("UPDATED");
        assertThat(history.get(2).action()).isEqualTo("CREATED");
        // Verify descending order
        for (int i = 0; i < history.size() - 1; i++) {
            assertThat(history.get(i).changedAt()).isAfterOrEqualTo(history.get(i + 1).changedAt());
        }
    }

    // ---- query tests ----

    @Test
    void query_returnsAllEntriesByDefault() {
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "A", "count", 1), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "B", "count", 2), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "C", "count", 3), USER_ID);

        var result = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(List.of(), List.of(), new CursorPageRequest(null, 20)));

        assertThat(result.items()).hasSize(3);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void query_filtersByEqOperator() {
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Hello", "count", 1), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "World", "count", 2), USER_ID);

        var result = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(
                        List.of(new Filter("cf_title", Operator.EQ, "Hello")),
                        List.of(),
                        new CursorPageRequest(null, 20)));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().getValues()).containsEntry("cf_title", "Hello");
    }

    @Test
    void query_filtersByGteOperator() {
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "A", "count", 5), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "B", "count", 10), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "C", "count", 15), USER_ID);

        var result = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(
                        List.of(new Filter("cf_count", Operator.GTE, 10L)),
                        List.of(),
                        new CursorPageRequest(null, 20)));

        assertThat(result.items()).hasSize(2);
    }

    @Test
    void query_excludesSoftDeleted() {
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Keep"), USER_ID);
        var toDelete = storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Remove"), USER_ID);
        storage.softDelete(SCHEMA_NAME, domainDef, toDelete.getId(), USER_ID);

        var result = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(List.of(), List.of(), new CursorPageRequest(null, 20)));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().getValues()).containsEntry("cf_title", "Keep");
    }

    @Test
    void query_paginatesViaCursor() {
        // Insert 5 entries
        for (int i = 1; i <= 5; i++) {
            storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Entry" + i, "count", i), USER_ID);
        }

        // Page 1: size 2
        var sorts = List.of(new SortCriterion("cf_count", SortCriterion.Direction.ASC));
        var page1 = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(List.of(), sorts, new CursorPageRequest(null, 2)));

        assertThat(page1.items()).hasSize(2);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.nextCursor()).isNotBlank();

        // Page 2
        var page2 = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(List.of(), sorts, new CursorPageRequest(page1.nextCursor(), 2)));

        assertThat(page2.items()).hasSize(2);
        assertThat(page2.hasNext()).isTrue();

        // Page 3 (last page)
        var page3 = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(List.of(), sorts, new CursorPageRequest(page2.nextCursor(), 2)));

        assertThat(page3.items()).hasSize(1);
        assertThat(page3.hasNext()).isFalse();

        // Verify no duplicates across pages
        var allIds = new ArrayList<UUID>();
        page1.items().forEach(e -> allIds.add(e.getId()));
        page2.items().forEach(e -> allIds.add(e.getId()));
        page3.items().forEach(e -> allIds.add(e.getId()));
        assertThat(allIds).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void query_sortsAscending() {
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "C", "count", 3), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "A", "count", 1), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "B", "count", 2), USER_ID);

        var result = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(List.of(),
                        List.of(new SortCriterion("cf_title", SortCriterion.Direction.ASC)),
                        new CursorPageRequest(null, 20)));

        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).getValues().get("cf_title")).isEqualTo("A");
        assertThat(result.items().get(1).getValues().get("cf_title")).isEqualTo("B");
        assertThat(result.items().get(2).getValues().get("cf_title")).isEqualTo("C");
    }

    @Test
    void query_sortsDescending() {
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "A", "count", 1), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "C", "count", 3), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "B", "count", 2), USER_ID);

        var result = storage.query(SCHEMA_NAME, domainDef,
                new ContentQuery(List.of(),
                        List.of(new SortCriterion("cf_title", SortCriterion.Direction.DESC)),
                        new CursorPageRequest(null, 20)));

        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).getValues().get("cf_title")).isEqualTo("C");
        assertThat(result.items().get(1).getValues().get("cf_title")).isEqualTo("B");
        assertThat(result.items().get(2).getValues().get("cf_title")).isEqualTo("A");
    }

    // ---- search tests ----

    @Test
    void search_returnsMatchingByDisplayTemplate() {
        domainDef.setDisplayFormat("{cf_title}");

        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Alice Smith", "count", 1), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Bob Jones", "count", 2), USER_ID);
        storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Alice Brown", "count", 3), USER_ID);

        var result = storage.search(SCHEMA_NAME, domainDef, "Alice", new CursorPageRequest(null, 20));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).allSatisfy(ref -> assertThat(ref.display()).contains("Alice"));
    }

    @Test
    void search_paginatesViaCursor() {
        domainDef.setDisplayFormat("{cf_title}");

        for (int i = 1; i <= 5; i++) {
            storage.save(SCHEMA_NAME, domainDef, Map.of("title", "Match" + i, "count", i), USER_ID);
        }

        var page1 = storage.search(SCHEMA_NAME, domainDef, "Match", new CursorPageRequest(null, 2));

        assertThat(page1.items()).hasSize(2);
        assertThat(page1.hasNext()).isTrue();

        var page2 = storage.search(SCHEMA_NAME, domainDef, "Match", new CursorPageRequest(page1.nextCursor(), 2));

        assertThat(page2.items()).hasSize(2);
        assertThat(page2.hasNext()).isTrue();

        var page3 = storage.search(SCHEMA_NAME, domainDef, "Match", new CursorPageRequest(page2.nextCursor(), 2));

        assertThat(page3.items()).hasSize(1);
        assertThat(page3.hasNext()).isFalse();
    }

    // ---- Helper methods ----

    private void refEntity(ContentDefinitionEntity entity, ContentFieldEntity field) {
        entity.setFields(new ArrayList<>(List.of(field)));
    }

    private ContentFieldEntity createTextField(String key, boolean sortable, boolean unique, boolean required) {
        var entity = new ContentFieldEntity();
        entity.setId(UUID.randomUUID());
        entity.setFieldKey(key);
        entity.setFieldType("TEXT");
        entity.setSortable(sortable);
        entity.setUniqueValue(unique);
        entity.setRequired(required);
        entity.setConfig(null);
        return entity;
    }

    private ContentFieldEntity createIntegerField(String key, boolean sortable, boolean unique, boolean required) {
        var entity = new ContentFieldEntity();
        entity.setId(UUID.randomUUID());
        entity.setFieldKey(key);
        entity.setFieldType("INTEGER");
        entity.setSortable(sortable);
        entity.setUniqueValue(unique);
        entity.setRequired(required);
        entity.setConfig(null);
        return entity;
    }
}
