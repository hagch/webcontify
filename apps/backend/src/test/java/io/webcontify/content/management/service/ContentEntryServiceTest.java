package io.webcontify.content.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.definition.service.ContentDefinitionService;
import io.webcontify.content.management.event.ContentEntryCreatedEvent;
import io.webcontify.content.management.event.ContentEntryDeletedEvent;
import io.webcontify.content.management.event.ContentEntryUpdatedEvent;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.query.ContentQuery;
import io.webcontify.content.management.query.CursorPage;
import io.webcontify.content.management.query.CursorPageRequest;
import io.webcontify.content.management.storage.ContentStorage;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ContentEntryServiceTest {

    @Mock private ContentDefinitionService definitionService;
    @Mock private ContentValidationService validationService;
    @Mock private ReferenceResolver referenceResolver;
    @Mock private ContentStorage storage;
    @Mock private ContentFieldRepository fieldRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ContentEntryService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String TENANT_SLUG = "my-tenant";
    private static final UUID APP_ID = UUID.randomUUID();
    private static final String DEF_SLUG = "articles";
    private static final UUID DEF_ID = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String SCHEMA = "tenant_my_tenant";

    @BeforeEach
    void setUp() {
        service = new ContentEntryService(
                definitionService, validationService, referenceResolver,
                storage, fieldRepository, eventPublisher);
    }

    private ContentDefinition createDefinition(List<FieldDefinition> fields) {
        var def = new ContentDefinition();
        def.setId(DEF_ID);
        def.setSlug(DEF_SLUG);
        def.setFields(fields);
        return def;
    }

    private FieldDefinition createField(String key, FieldType type, boolean unique) {
        var field = new FieldDefinition();
        field.setFieldKey(key);
        field.setFieldType(type);
        field.setUniqueValue(unique);
        return field;
    }

    private ContentEntry createEntry(UUID id) {
        var entry = new ContentEntry();
        entry.setId(id);
        entry.setDefinitionId(DEF_ID);
        entry.setVersion(0L);
        entry.setValues(Map.of("cf_title", "Hello"));
        entry.setCreatedAt(OffsetDateTime.now());
        entry.setUpdatedAt(OffsetDateTime.now());
        entry.setCreatedBy(USER_ID);
        entry.setUpdatedBy(USER_ID);
        return entry;
    }

    @Nested
    class Create {

        @Test
        void happyPath_createsEntryAndPublishesEvent() {
            var def = createDefinition(List.of(createField("title", FieldType.TEXT, false)));
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            when(validationService.validateAndCast(any(), eq(def)))
                    .thenReturn(Map.of("cf_title", "Hello"));
            var savedEntry = createEntry(ENTRY_ID);
            when(storage.save(eq(SCHEMA), eq(def), any(), eq(USER_ID))).thenReturn(savedEntry);

            ContentEntry result = service.create(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG,
                    Map.of("cf_title", "Hello"), USER_ID);

            assertThat(result.getId()).isEqualTo(ENTRY_ID);

            ArgumentCaptor<ContentEntryCreatedEvent> captor = ArgumentCaptor.forClass(ContentEntryCreatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
            assertThat(captor.getValue().definitionId()).isEqualTo(DEF_ID);
            assertThat(captor.getValue().entryId()).isEqualTo(ENTRY_ID);
        }

        @Test
        void definitionNotFound_throwsNotFoundException() {
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID))
                    .thenThrow(new NotFoundException("content_definition.not_found"));

            assertThatThrownBy(() -> service.create(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG,
                    Map.of(), USER_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Update {

        @Test
        void happyPath_updatesEntryAndPublishesEvent() {
            var def = createDefinition(List.of(createField("title", FieldType.TEXT, false)));
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            when(validationService.validateAndCast(any(), eq(def)))
                    .thenReturn(Map.of("cf_title", "Updated"));
            var updatedEntry = createEntry(ENTRY_ID);
            updatedEntry.setVersion(1L);
            when(storage.update(eq(SCHEMA), eq(def), eq(ENTRY_ID), eq(0L), any(), eq(USER_ID)))
                    .thenReturn(updatedEntry);

            ContentEntry result = service.update(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG,
                    ENTRY_ID, 0L, Map.of("cf_title", "Updated"), USER_ID);

            assertThat(result.getVersion()).isEqualTo(1L);

            ArgumentCaptor<ContentEntryUpdatedEvent> captor = ArgumentCaptor.forClass(ContentEntryUpdatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().entryId()).isEqualTo(ENTRY_ID);
            assertThat(captor.getValue().changedFieldKeys()).contains("cf_title");
        }
    }

    @Nested
    class Delete {

        @Test
        void happyPath_deletesEntryAndPublishesEvent() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            when(storage.findById(SCHEMA, def, ENTRY_ID))
                    .thenReturn(Optional.of(createEntry(ENTRY_ID)));
            when(fieldRepository.findReferenceFieldsByTargetDefinitionId(any()))
                    .thenReturn(List.of());

            service.delete(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG, ENTRY_ID, USER_ID);

            verify(storage).softDelete(SCHEMA, def, ENTRY_ID, USER_ID);
            ArgumentCaptor<ContentEntryDeletedEvent> captor = ArgumentCaptor.forClass(ContentEntryDeletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().entryId()).isEqualTo(ENTRY_ID);
        }

        @Test
        void entryNotFound_throwsNotFoundException() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            when(storage.findById(SCHEMA, def, ENTRY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG,
                    ENTRY_ID, USER_ID))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void restrict_throwsConflictWhenReferencesExist() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            when(storage.findById(SCHEMA, def, ENTRY_ID))
                    .thenReturn(Optional.of(createEntry(ENTRY_ID)));

            // Set up a referencing field with RESTRICT strategy
            var refFieldEntity = new ContentFieldEntity();
            refFieldEntity.setId(UUID.randomUUID());
            refFieldEntity.setDefinitionId(UUID.randomUUID());
            refFieldEntity.setFieldKey("ref_field");
            when(fieldRepository.findReferenceFieldsByTargetDefinitionId(any()))
                    .thenReturn(List.of(refFieldEntity));

            var refConfig = new ReferenceFieldConfig(DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.RESTRICT);
            var refField = createField("ref_field", FieldType.REFERENCE, false);
            refField.setId(refFieldEntity.getId());
            refField.setConfig(refConfig);
            var referencingDef = createDefinition(List.of(refField));
            referencingDef.setId(refFieldEntity.getDefinitionId());
            when(definitionService.getById(refFieldEntity.getDefinitionId())).thenReturn(referencingDef);

            when(storage.findByReferenceField(SCHEMA, referencingDef, "ref_field", ENTRY_ID))
                    .thenReturn(List.of(createEntry(UUID.randomUUID())));

            assertThatThrownBy(() -> service.delete(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG,
                    ENTRY_ID, USER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("entry.delete_restricted");
        }

        @Test
        void setNull_nullifiesReferencingEntries() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            when(storage.findById(SCHEMA, def, ENTRY_ID))
                    .thenReturn(Optional.of(createEntry(ENTRY_ID)));

            var refFieldEntity = new ContentFieldEntity();
            refFieldEntity.setId(UUID.randomUUID());
            refFieldEntity.setDefinitionId(UUID.randomUUID());
            refFieldEntity.setFieldKey("ref_field");
            when(fieldRepository.findReferenceFieldsByTargetDefinitionId(any()))
                    .thenReturn(List.of(refFieldEntity));

            var refConfig = new ReferenceFieldConfig(DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.SET_NULL);
            var refField = createField("ref_field", FieldType.REFERENCE, false);
            refField.setId(refFieldEntity.getId());
            refField.setConfig(refConfig);
            var referencingDef = createDefinition(List.of(refField));
            referencingDef.setId(refFieldEntity.getDefinitionId());
            when(definitionService.getById(refFieldEntity.getDefinitionId())).thenReturn(referencingDef);

            when(storage.findByReferenceField(SCHEMA, referencingDef, "ref_field", ENTRY_ID))
                    .thenReturn(List.of(createEntry(UUID.randomUUID())));

            service.delete(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG, ENTRY_ID, USER_ID);

            verify(storage).nullifyField(SCHEMA, referencingDef, "ref_field", ENTRY_ID, USER_ID);
            verify(storage).softDelete(SCHEMA, def, ENTRY_ID, USER_ID);
        }

        @Test
        void cascade_deletesReferencingEntries() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            when(storage.findById(SCHEMA, def, ENTRY_ID))
                    .thenReturn(Optional.of(createEntry(ENTRY_ID)));

            var refFieldEntity = new ContentFieldEntity();
            refFieldEntity.setId(UUID.randomUUID());
            refFieldEntity.setDefinitionId(UUID.randomUUID());
            refFieldEntity.setFieldKey("ref_field");
            when(fieldRepository.findReferenceFieldsByTargetDefinitionId(any()))
                    .thenReturn(List.of(refFieldEntity));

            var refConfig = new ReferenceFieldConfig(DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
            var refField = createField("ref_field", FieldType.REFERENCE, false);
            refField.setId(refFieldEntity.getId());
            refField.setConfig(refConfig);
            var referencingDef = createDefinition(List.of(refField));
            referencingDef.setId(refFieldEntity.getDefinitionId());
            when(definitionService.getById(refFieldEntity.getDefinitionId())).thenReturn(referencingDef);

            var cascadedEntry = createEntry(UUID.randomUUID());
            when(storage.findByReferenceField(SCHEMA, referencingDef, "ref_field", ENTRY_ID))
                    .thenReturn(List.of(cascadedEntry));

            // The cascaded entry's definition has no further references
            String cascadeTargetJson = "{\"targetDefinitionId\":\"" + referencingDef.getId() + "\"}";
            when(fieldRepository.findReferenceFieldsByTargetDefinitionId(cascadeTargetJson))
                    .thenReturn(List.of());

            service.delete(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG, ENTRY_ID, USER_ID);

            // Cascaded entry should be soft-deleted
            verify(storage).softDelete(SCHEMA, referencingDef, cascadedEntry.getId(), USER_ID);
            // Original entry should be soft-deleted
            verify(storage).softDelete(SCHEMA, def, ENTRY_ID, USER_ID);
        }
    }

    @Nested
    class FindById {

        @Test
        void happyPath_returnsEntryWithResolvedReferences() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            var entry = createEntry(ENTRY_ID);
            when(storage.findById(SCHEMA, def, ENTRY_ID)).thenReturn(Optional.of(entry));

            ContentEntry result = service.findById(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG, ENTRY_ID);

            assertThat(result.getId()).isEqualTo(ENTRY_ID);
            verify(referenceResolver).resolveReferences(entry, def, SCHEMA);
        }

        @Test
        void notFound_throwsNotFoundException() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            when(storage.findById(SCHEMA, def, ENTRY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG, ENTRY_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Query {

        @Test
        void delegatesToStorage() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            var page = new CursorPage<>(List.of(createEntry(ENTRY_ID)), null, false);
            var query = new ContentQuery(List.of(), List.of(), new CursorPageRequest(null, 20));
            when(storage.query(SCHEMA, def, query)).thenReturn(page);

            CursorPage<ContentEntry> result = service.query(TENANT_ID, TENANT_SLUG, APP_ID, DEF_SLUG, query);

            assertThat(result.items()).hasSize(1);
        }
    }

    @Nested
    class Search {

        @Test
        void delegatesToStorage() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            var pageReq = new CursorPageRequest(null, 10);
            var page = new CursorPage<>(List.of(new EntryReference(ENTRY_ID, "Test")), null, false);
            when(storage.search(SCHEMA, def, "test", pageReq)).thenReturn(page);

            CursorPage<EntryReference> result = service.search(TENANT_ID, TENANT_SLUG, APP_ID,
                    DEF_SLUG, "test", pageReq);

            assertThat(result.items()).hasSize(1);
        }
    }

    @Nested
    class History {

        @Test
        void delegatesToStorage() {
            var def = createDefinition(List.of());
            when(definitionService.getBySlugAndAppId(DEF_SLUG, APP_ID)).thenReturn(def);
            var pageReq = new CursorPageRequest(null, 20);
            var record = new EntryHistoryRecord(UUID.randomUUID(), ENTRY_ID, "CREATED",
                    Map.of(), OffsetDateTime.now(), USER_ID);
            when(storage.getHistory(SCHEMA, def, ENTRY_ID, 20)).thenReturn(List.of(record));

            CursorPage<EntryHistoryRecord> result = service.getHistory(TENANT_ID, TENANT_SLUG, APP_ID,
                    DEF_SLUG, ENTRY_ID, pageReq);

            assertThat(result.items()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
        }
    }
}
