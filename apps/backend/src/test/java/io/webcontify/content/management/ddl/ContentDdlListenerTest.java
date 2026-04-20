package io.webcontify.content.management.ddl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.management.ddl.repository.ContentFieldChangeLogRepository;
import io.webcontify.content.management.ddl.repository.entity.ContentFieldChangeLogEntity;
import io.webcontify.shared.persistence.TenantSlugCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentDdlListenerTest {

    @Mock private ContentDefinitionRepository definitionRepository;
    @Mock private ContentFieldRepository fieldRepository;
    @Mock private DdlDiffEngine ddlDiffEngine;
    @Mock private DynamicTableManager dynamicTableManager;
    @Mock private ContentFieldChangeLogRepository changeLogRepository;
    @Mock private TenantSlugCache tenantSlugCache;

    private ContentDdlListener listener;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID APP_ID = UUID.randomUUID();
    private static final String TENANT_SLUG = "test-tenant";

    @BeforeEach
    void setUp() {
        listener = new ContentDdlListener(
                definitionRepository, fieldRepository, ddlDiffEngine,
                dynamicTableManager, changeLogRepository, tenantSlugCache);
    }

    @Test
    void shouldCreateTableOnFirstPublish() {
        var event = new AppPublishedEvent(APP_ID, TENANT_ID, "1.0.0");
        var definition = createDefinition("articles");
        var field = createField("title", "TEXT", "ADDED", false);
        definition.setFields(new ArrayList<>(List.of(field)));

        when(tenantSlugCache.resolveSlug(TENANT_ID)).thenReturn(Optional.of(TENANT_SLUG));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(APP_ID))
                .thenReturn(List.of(definition));
        when(fieldRepository.findByDefinitionIdAndPendingActionIsNotNull(definition.getId()))
                .thenReturn(List.of(field));
        when(ddlDiffEngine.diff(true, List.of(field)))
                .thenReturn(List.of(new DdlChange(DdlAction.CREATE_TABLE, null)));

        listener.on(event);

        verify(dynamicTableManager).createTable("tenant_test_tenant", definition);
        verify(dynamicTableManager, never()).applyChanges(any(), any(), any());
        assertThat(field.isPublished()).isTrue();
        assertThat(field.getPendingAction()).isNull();
    }

    @Test
    void shouldApplyChangesOnSubsequentPublish() {
        var event = new AppPublishedEvent(APP_ID, TENANT_ID, "2.0.0");
        var definition = createDefinition("articles");
        var existingField = createField("title", "TEXT", null, true);
        var newField = createField("description", "TEXT", "ADDED", false);
        definition.setFields(new ArrayList<>(List.of(existingField, newField)));

        when(tenantSlugCache.resolveSlug(TENANT_ID)).thenReturn(Optional.of(TENANT_SLUG));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(APP_ID))
                .thenReturn(List.of(definition));
        when(fieldRepository.findByDefinitionIdAndPendingActionIsNotNull(definition.getId()))
                .thenReturn(List.of(newField));

        var addColumnChange = new DdlChange(DdlAction.ADD_COLUMN, newField);
        when(ddlDiffEngine.diff(false, List.of(newField)))
                .thenReturn(List.of(addColumnChange));

        listener.on(event);

        verify(dynamicTableManager, never()).createTable(any(), any());
        verify(dynamicTableManager).applyChanges(
                eq("tenant_test_tenant"), eq(definition), eq(List.of(addColumnChange)));
        assertThat(newField.isPublished()).isTrue();
        assertThat(newField.getPendingAction()).isNull();
    }

    @Test
    void shouldSetPublishedFalseForRemovedField() {
        var event = new AppPublishedEvent(APP_ID, TENANT_ID, "3.0.0");
        var definition = createDefinition("articles");
        var publishedField = createField("title", "TEXT", null, true);
        var removedField = createField("obsolete", "TEXT", "REMOVED", true);
        definition.setFields(new ArrayList<>(List.of(publishedField, removedField)));

        when(tenantSlugCache.resolveSlug(TENANT_ID)).thenReturn(Optional.of(TENANT_SLUG));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(APP_ID))
                .thenReturn(List.of(definition));
        when(fieldRepository.findByDefinitionIdAndPendingActionIsNotNull(definition.getId()))
                .thenReturn(List.of(removedField));

        var dropChange = new DdlChange(DdlAction.DROP_COLUMN, removedField);
        when(ddlDiffEngine.diff(false, List.of(removedField)))
                .thenReturn(List.of(dropChange));

        listener.on(event);

        assertThat(removedField.isPublished()).isFalse();
        assertThat(removedField.getPendingAction()).isNull();
    }

    @Test
    void shouldWriteChangeLogEntries() {
        var event = new AppPublishedEvent(APP_ID, TENANT_ID, "1.0.0");
        var definition = createDefinition("articles");
        var field = createField("title", "TEXT", "ADDED", false);
        definition.setFields(new ArrayList<>(List.of(field)));

        when(tenantSlugCache.resolveSlug(TENANT_ID)).thenReturn(Optional.of(TENANT_SLUG));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(APP_ID))
                .thenReturn(List.of(definition));
        when(fieldRepository.findByDefinitionIdAndPendingActionIsNotNull(definition.getId()))
                .thenReturn(List.of(field));
        when(ddlDiffEngine.diff(true, List.of(field)))
                .thenReturn(List.of(new DdlChange(DdlAction.CREATE_TABLE, null)));

        listener.on(event);

        ArgumentCaptor<ContentFieldChangeLogEntity> logCaptor =
                ArgumentCaptor.forClass(ContentFieldChangeLogEntity.class);
        verify(changeLogRepository).save(logCaptor.capture());

        var logEntry = logCaptor.getValue();
        assertThat(logEntry.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(logEntry.getDefinitionId()).isEqualTo(definition.getId());
        assertThat(logEntry.getFieldId()).isEqualTo(field.getId());
        assertThat(logEntry.getAction()).isEqualTo("ADDED");
        assertThat(logEntry.getNewType()).isEqualTo("TEXT");
        assertThat(logEntry.isDataLoss()).isFalse();
    }

    @Test
    void shouldSkipDefinitionsWithNoPendingFields() {
        var event = new AppPublishedEvent(APP_ID, TENANT_ID, "1.0.0");
        var definition = createDefinition("articles");

        when(tenantSlugCache.resolveSlug(TENANT_ID)).thenReturn(Optional.of(TENANT_SLUG));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(APP_ID))
                .thenReturn(List.of(definition));
        when(fieldRepository.findByDefinitionIdAndPendingActionIsNotNull(definition.getId()))
                .thenReturn(List.of());

        listener.on(event);

        verifyNoInteractions(dynamicTableManager);
        verifyNoInteractions(changeLogRepository);
    }

    @Test
    void shouldMarkRemovedFieldChangeLogWithDataLoss() {
        var event = new AppPublishedEvent(APP_ID, TENANT_ID, "2.0.0");
        var definition = createDefinition("articles");
        var publishedField = createField("title", "TEXT", null, true);
        var removedField = createField("old_col", "INTEGER", "REMOVED", true);
        definition.setFields(new ArrayList<>(List.of(publishedField, removedField)));

        when(tenantSlugCache.resolveSlug(TENANT_ID)).thenReturn(Optional.of(TENANT_SLUG));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(APP_ID))
                .thenReturn(List.of(definition));
        when(fieldRepository.findByDefinitionIdAndPendingActionIsNotNull(definition.getId()))
                .thenReturn(List.of(removedField));
        when(ddlDiffEngine.diff(false, List.of(removedField)))
                .thenReturn(List.of(new DdlChange(DdlAction.DROP_COLUMN, removedField)));

        listener.on(event);

        ArgumentCaptor<ContentFieldChangeLogEntity> logCaptor =
                ArgumentCaptor.forClass(ContentFieldChangeLogEntity.class);
        verify(changeLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().isDataLoss()).isTrue();
        assertThat(logCaptor.getValue().getOldType()).isEqualTo("INTEGER");
    }

    private ContentDefinitionEntity createDefinition(String slug) {
        var entity = new ContentDefinitionEntity();
        entity.setId(UUID.randomUUID());
        entity.setSlug(slug);
        entity.setName("Test " + slug);
        entity.setFields(new ArrayList<>());
        return entity;
    }

    private ContentFieldEntity createField(String key, String type, String pendingAction, boolean published) {
        var entity = new ContentFieldEntity();
        entity.setId(UUID.randomUUID());
        entity.setFieldKey(key);
        entity.setFieldType(type);
        entity.setPendingAction(pendingAction);
        entity.setPublished(published);
        return entity;
    }
}
