package io.webcontify.content.management.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.storage.ContentStorage;
import io.webcontify.content.management.storage.DisplayFormatResolver;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceResolverTest {

    @Mock private ContentStorage storage;
    @Mock private ContentDefinitionRepository definitionRepository;
    @Mock private ContentDefinitionMapper definitionMapper;
    @Mock private DisplayFormatResolver displayResolver;

    private ReferenceResolver resolver;

    private static final String SCHEMA = "tenant_test";
    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final UUID TARGET_DEF_ID = UUID.randomUUID();
    private static final UUID TARGET_ENTRY_ID = UUID.randomUUID();
    private static final UUID SOURCE_DEF_ID = UUID.randomUUID();
    private static final UUID SOURCE_FIELD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new ReferenceResolver(storage, definitionRepository, definitionMapper, displayResolver);
    }

    @Test
    void resolveReferences_referenceField_replacesUuidWithEntryReference() {
        // Setup: entry has a REFERENCE field pointing to TARGET_ENTRY_ID
        var entry = createEntry(ENTRY_ID, Map.of("cf_related", (Object) TARGET_ENTRY_ID));
        var refConfig = new ReferenceFieldConfig(TARGET_DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.RESTRICT);
        var def = createDefinition(List.of(createField("related", FieldType.REFERENCE, refConfig)));

        // Mock target definition load
        var targetDefEntity = new ContentDefinitionEntity();
        var targetDef = createDefinition(List.of());
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TARGET_DEF_ID))
                .thenReturn(Optional.of(targetDefEntity));
        when(definitionMapper.toDomain(targetDefEntity)).thenReturn(targetDef);

        // Mock target entry load
        var targetEntry = createEntry(TARGET_ENTRY_ID, Map.of("cf_name", "Target Name"));
        when(storage.findById(SCHEMA, targetDef, TARGET_ENTRY_ID)).thenReturn(Optional.of(targetEntry));
        when(displayResolver.resolve(eq(targetDef), any())).thenReturn("Target Name");

        resolver.resolveReferences(entry, def, SCHEMA);

        Object resolved = entry.getValues().get("cf_related");
        assertThat(resolved).isInstanceOf(EntryReference.class);
        EntryReference ref = (EntryReference) resolved;
        assertThat(ref.id()).isEqualTo(TARGET_ENTRY_ID);
        assertThat(ref.display()).isEqualTo("Target Name");
    }

    @Test
    void resolveReferences_referenceField_missingTarget_keepsOriginalValue() {
        var entry = createEntry(ENTRY_ID, Map.of("cf_related", (Object) TARGET_ENTRY_ID));
        var refConfig = new ReferenceFieldConfig(TARGET_DEF_ID, RelationType.MANY_TO_ONE, OnDeleteStrategy.RESTRICT);
        var def = createDefinition(List.of(createField("related", FieldType.REFERENCE, refConfig)));

        // Target definition exists but entry not found
        var targetDefEntity = new ContentDefinitionEntity();
        var targetDef = createDefinition(List.of());
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(TARGET_DEF_ID))
                .thenReturn(Optional.of(targetDefEntity));
        when(definitionMapper.toDomain(targetDefEntity)).thenReturn(targetDef);
        when(storage.findById(SCHEMA, targetDef, TARGET_ENTRY_ID)).thenReturn(Optional.empty());

        resolver.resolveReferences(entry, def, SCHEMA);

        // Value should remain unchanged (UUID)
        assertThat(entry.getValues().get("cf_related")).isEqualTo(TARGET_ENTRY_ID);
    }

    @Test
    void resolveReferences_mirrorField_resolvesToEntryReferenceList() {
        var entry = createEntry(ENTRY_ID, new LinkedHashMap<>());
        var mirrorConfig = new MirrorFieldConfig(SOURCE_DEF_ID, SOURCE_FIELD_ID, UUID.randomUUID());
        var def = createDefinition(List.of(createField("mirrors", FieldType.MIRROR, mirrorConfig)));

        // Source definition
        var sourceDefEntity = new ContentDefinitionEntity();
        var sourceRefField = createField("ref_field", FieldType.REFERENCE, null);
        sourceRefField.setId(SOURCE_FIELD_ID);
        var sourceDef = createDefinition(List.of(sourceRefField));
        when(definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(SOURCE_DEF_ID))
                .thenReturn(Optional.of(sourceDefEntity));
        when(definitionMapper.toDomain(sourceDefEntity)).thenReturn(sourceDef);

        // Reverse lookup returns 2 entries
        var refEntry1 = createEntry(UUID.randomUUID(), Map.of("cf_name", "Entry A"));
        var refEntry2 = createEntry(UUID.randomUUID(), Map.of("cf_name", "Entry B"));
        when(storage.findByReferenceField(SCHEMA, sourceDef, "ref_field", ENTRY_ID))
                .thenReturn(List.of(refEntry1, refEntry2));
        when(displayResolver.resolve(eq(sourceDef), any())).thenReturn("Display");

        resolver.resolveReferences(entry, def, SCHEMA);

        Object mirrorValue = entry.getValues().get("cf_mirrors");
        assertThat(mirrorValue).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<EntryReference> refs = (List<EntryReference>) mirrorValue;
        assertThat(refs).hasSize(2);
    }

    @Test
    void resolveReferences_noFields_doesNothing() {
        var entry = createEntry(ENTRY_ID, Map.of("cf_title", "Hello"));
        var def = new ContentDefinition();
        def.setFields(null);

        resolver.resolveReferences(entry, def, SCHEMA);

        assertThat(entry.getValues().get("cf_title")).isEqualTo("Hello");
    }

    private ContentEntry createEntry(UUID id, Map<String, Object> values) {
        var entry = new ContentEntry();
        entry.setId(id);
        entry.setValues(new LinkedHashMap<>(values));
        return entry;
    }

    private ContentDefinition createDefinition(List<FieldDefinition> fields) {
        var def = new ContentDefinition();
        def.setFields(fields);
        return def;
    }

    private FieldDefinition createField(String key, FieldType type, FieldConfig config) {
        var field = new FieldDefinition();
        field.setFieldKey(key);
        field.setFieldType(type);
        field.setConfig(config);
        return field;
    }
}
