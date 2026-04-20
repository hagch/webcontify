package io.webcontify.content.management.ddl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.TextFieldConfig;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.*;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamicTableManagerTest {

    @Mock private DSLContext dsl;
    @Mock private FieldTypeRegistry fieldTypeRegistry;
    @Mock private FieldConfigConverter fieldConfigConverter;

    private DynamicTableManager manager;

    @BeforeEach
    void setUp() {
        manager = new DynamicTableManager(dsl, fieldTypeRegistry, fieldConfigConverter);
    }

    @Test
    void shouldNormalizeSlugsReplacingHyphensWithUnderscores() {
        // Verify the normalize method works through a dropTable call
        var definition = createDefinition("my-table");

        @SuppressWarnings("unchecked")
        DropTableStep dropStep1 = mock(DropTableStep.class);
        @SuppressWarnings("unchecked")
        DropTableStep dropStep2 = mock(DropTableStep.class);

        when(dsl.dropTableIfExists(any(Name.class))).thenReturn(dropStep1, dropStep2);

        manager.dropTable("tenant_my-slug", definition);

        // Verify the normalized names are used (hyphens replaced with underscores)
        verify(dsl, times(2)).dropTableIfExists(any(Name.class));
        verify(dsl).execute("SET LOCAL lock_timeout = '5s'");
    }

    @Test
    void shouldCallSetLockTimeoutBeforeDdl() {
        var definition = createDefinition("test");

        @SuppressWarnings("unchecked")
        DropTableStep dropStep = mock(DropTableStep.class);
        when(dsl.dropTableIfExists(any(Name.class))).thenReturn(dropStep);

        manager.dropTable("tenant_test", definition);

        verify(dsl).execute("SET LOCAL lock_timeout = '5s'");
    }

    @Test
    void shouldApplyAddColumnChange() {
        var definition = createDefinition("articles");
        var field = createField("title", "TEXT", false, false);

        FieldTypeHandler handler = mock(FieldTypeHandler.class);
        when(handler.isVirtual()).thenReturn(false);
        doReturn(SQLDataType.VARCHAR(255)).when(handler).columnType(any());
        when(fieldTypeRegistry.get(FieldType.TEXT)).thenReturn(handler);
        when(fieldConfigConverter.fromJson(null, FieldType.TEXT)).thenReturn(null);

        @SuppressWarnings("unchecked")
        AlterTableStep alterStep = mock(AlterTableStep.class);
        @SuppressWarnings("unchecked")
        AlterTableAddStep addStep = mock(AlterTableAddStep.class);
        when(dsl.alterTable(any(Name.class))).thenReturn(alterStep);
        when(alterStep.addColumn(any(Name.class), any(DataType.class))).thenReturn(addStep);

        var changes = List.of(new DdlChange(DdlAction.ADD_COLUMN, field));
        manager.applyChanges("tenant_test", definition, changes);

        verify(dsl).alterTable(any(Name.class));
        verify(alterStep).addColumn(any(Name.class), any(DataType.class));
    }

    @Test
    void shouldSkipVirtualFieldsInAddColumn() {
        var definition = createDefinition("articles");
        var field = createField("mirror_col", "MIRROR", false, false);

        FieldTypeHandler handler = mock(FieldTypeHandler.class);
        when(handler.isVirtual()).thenReturn(true);
        when(fieldTypeRegistry.get(FieldType.MIRROR)).thenReturn(handler);

        var changes = List.of(new DdlChange(DdlAction.ADD_COLUMN, field));
        manager.applyChanges("tenant_test", definition, changes);

        verify(dsl, never()).alterTable(any(Name.class));
    }

    @Test
    void shouldApplyDropColumnChange() {
        var definition = createDefinition("articles");
        var field = createField("old_col", "TEXT", false, false);

        @SuppressWarnings("unchecked")
        AlterTableStep alterStep = mock(AlterTableStep.class);
        @SuppressWarnings("unchecked")
        AlterTableDropStep dropStep = mock(AlterTableDropStep.class);
        when(dsl.alterTable(any(Name.class))).thenReturn(alterStep);
        when(alterStep.dropColumnIfExists(any(Name.class))).thenReturn(dropStep);

        var changes = List.of(new DdlChange(DdlAction.DROP_COLUMN, field));
        manager.applyChanges("tenant_test", definition, changes);

        verify(alterStep).dropColumnIfExists(any(Name.class));
    }

    private ContentDefinitionEntity createDefinition(String slug) {
        var entity = new ContentDefinitionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSlug(slug);
        entity.setName("Test");
        entity.setFields(new ArrayList<>());
        return entity;
    }

    private ContentFieldEntity createField(String key, String type, boolean sortable, boolean unique) {
        var entity = new ContentFieldEntity();
        entity.setId(UUID.randomUUID());
        entity.setFieldKey(key);
        entity.setFieldType(type);
        entity.setSortable(sortable);
        entity.setUniqueValue(unique);
        return entity;
    }
}
