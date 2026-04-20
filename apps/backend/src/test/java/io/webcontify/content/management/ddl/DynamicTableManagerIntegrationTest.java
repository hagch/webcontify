package io.webcontify.content.management.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.IntegrationTestBase;
import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.TextFieldConfig;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DynamicTableManagerIntegrationTest extends IntegrationTestBase {

    @Autowired private DynamicTableManager dynamicTableManager;
    @Autowired private DSLContext dsl;
    @Autowired private FieldTypeRegistry fieldTypeRegistry;
    @Autowired private FieldConfigConverter fieldConfigConverter;

    private static final String SCHEMA_NAME = "tenant_integration_test";

    @BeforeEach
    void setUp() {
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA_NAME);
    }

    @Test
    void shouldCreateTableWithSystemColumnsAndUserFields() {
        var definition = createDefinition("articles");
        var titleField = createTextField("title", true, false, true);
        var emailField = createTextField("email", false, true, false);
        definition.setFields(new ArrayList<>(List.of(titleField, emailField)));

        dynamicTableManager.createTable(SCHEMA_NAME, definition);

        var columns = fetchColumns(SCHEMA_NAME, "cd_articles");
        assertThat(columns).containsKeys("id", "version", "created_at", "updated_at",
                "created_by", "updated_by", "deleted_at", "deleted_by", "cf_title", "cf_email");
    }

    @Test
    void shouldCreateHistoryTable() {
        var definition = createDefinition("posts");
        definition.setFields(new ArrayList<>());

        dynamicTableManager.createTable(SCHEMA_NAME, definition);

        var historyColumns = fetchColumns(SCHEMA_NAME, "cd_posts_history");
        assertThat(historyColumns).containsKeys("id", "entry_id", "action", "changed_fields",
                "changed_at", "changed_by");
    }

    @Test
    void shouldApplyAddColumnChange() {
        var definition = createDefinition("products");
        definition.setFields(new ArrayList<>());
        dynamicTableManager.createTable(SCHEMA_NAME, definition);

        var newField = createTextField("description", false, false, false);
        var changes = List.of(new DdlChange(DdlAction.ADD_COLUMN, newField));
        dynamicTableManager.applyChanges(SCHEMA_NAME, definition, changes);

        var columns = fetchColumns(SCHEMA_NAME, "cd_products");
        assertThat(columns).containsKey("cf_description");
    }

    @Test
    void shouldApplyDropColumnChange() {
        var definition = createDefinition("items");
        var field = createTextField("obsolete", false, false, false);
        definition.setFields(new ArrayList<>(List.of(field)));
        dynamicTableManager.createTable(SCHEMA_NAME, definition);

        var changes = List.of(new DdlChange(DdlAction.DROP_COLUMN, field));
        dynamicTableManager.applyChanges(SCHEMA_NAME, definition, changes);

        var columns = fetchColumns(SCHEMA_NAME, "cd_items");
        assertThat(columns).doesNotContainKey("cf_obsolete");
    }

    @Test
    void shouldDropTableAndHistoryTable() {
        var definition = createDefinition("temp");
        definition.setFields(new ArrayList<>());
        dynamicTableManager.createTable(SCHEMA_NAME, definition);

        dynamicTableManager.dropTable(SCHEMA_NAME, definition);

        var tables = dsl.fetch(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_name IN (?, ?)",
                SCHEMA_NAME, "cd_temp", "cd_temp_history");
        assertThat(tables).isEmpty();
    }

    @Test
    void shouldSkipMirrorFieldsInCreateTable() {
        var definition = createDefinition("with_mirror");
        var textField = createTextField("name", false, false, false);
        var mirrorField = new ContentFieldEntity();
        mirrorField.setId(UUID.randomUUID());
        mirrorField.setFieldKey("related");
        mirrorField.setFieldType("MIRROR");
        definition.setFields(new ArrayList<>(List.of(textField, mirrorField)));

        dynamicTableManager.createTable(SCHEMA_NAME, definition);

        var columns = fetchColumns(SCHEMA_NAME, "cd_with_mirror");
        assertThat(columns).containsKey("cf_name");
        assertThat(columns).doesNotContainKey("cf_related");
    }

    private Map<String, Object> fetchColumns(String schema, String table) {
        var result = dsl.fetch(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
                schema, table);
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var record : result) {
            map.put(record.get(0, String.class), null);
        }
        return map;
    }

    private ContentDefinitionEntity createDefinition(String slug) {
        var entity = new ContentDefinitionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSlug(slug);
        entity.setName("Test " + slug);
        entity.setFields(new ArrayList<>());
        return entity;
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
}
