package io.webcontify.content.management.service;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.MirrorFieldConfig;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.storage.ContentStorage;
import io.webcontify.content.management.storage.DisplayFormatResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves REFERENCE and MIRROR fields on content entries for read responses.
 * REFERENCE fields are resolved to {@link EntryReference} with id + display text.
 * MIRROR fields are resolved to a list of {@link EntryReference} from reverse lookups.
 */
@Component
public class ReferenceResolver {

    private final ContentStorage storage;
    private final ContentDefinitionRepository definitionRepository;
    private final ContentDefinitionMapper definitionMapper;
    private final DisplayFormatResolver displayResolver;

    public ReferenceResolver(ContentStorage storage,
                             ContentDefinitionRepository definitionRepository,
                             ContentDefinitionMapper definitionMapper,
                             DisplayFormatResolver displayResolver) {
        this.storage = storage;
        this.definitionRepository = definitionRepository;
        this.definitionMapper = definitionMapper;
        this.displayResolver = displayResolver;
    }

    /**
     * Resolves reference and mirror fields on the given entry in-place.
     * REFERENCE field values (UUID) are replaced with {@link EntryReference}.
     * MIRROR field values are set to {@code List<EntryReference>} from reverse lookup.
     */
    public void resolveReferences(ContentEntry entry, ContentDefinition def, String schemaName) {
        if (def.getFields() == null || entry.getValues() == null) {
            return;
        }

        for (FieldDefinition field : def.getFields()) {
            if (field.getFieldType() == FieldType.REFERENCE) {
                resolveReferenceField(entry, field, schemaName);
            } else if (field.getFieldType() == FieldType.MIRROR) {
                resolveMirrorField(entry, field, schemaName);
            }
        }
    }

    private void resolveReferenceField(ContentEntry entry, FieldDefinition field, String schemaName) {
        String columnName = "cf_" + field.getFieldKey();
        Object rawValue = entry.getValues().get(columnName);

        if (!(rawValue instanceof UUID targetEntryId)) {
            return;
        }

        if (!(field.getConfig() instanceof ReferenceFieldConfig refConfig)) {
            return;
        }

        ContentDefinition targetDef = loadDefinition(refConfig.targetDefinitionId());
        if (targetDef == null) {
            return;
        }

        Optional<ContentEntry> targetEntry = storage.findById(schemaName, targetDef, targetEntryId);
        if (targetEntry.isPresent()) {
            String display = displayResolver.resolve(targetDef, targetEntry.get().getValues());
            entry.getValues().put(columnName, new EntryReference(targetEntryId, display));
        }
    }

    private void resolveMirrorField(ContentEntry entry, FieldDefinition field, String schemaName) {
        if (!(field.getConfig() instanceof MirrorFieldConfig mirrorConfig)) {
            return;
        }

        // Find the source definition (the one holding the REFERENCE field)
        ContentDefinition sourceDef = loadDefinition(mirrorConfig.sourceDefinitionId());
        if (sourceDef == null) {
            return;
        }

        // Find the source REFERENCE field by ID to get its fieldKey
        FieldDefinition sourceField = findFieldById(sourceDef, mirrorConfig.sourceFieldId());
        if (sourceField == null) {
            return;
        }

        // Reverse lookup: find entries in sourceDef that reference this entry
        List<ContentEntry> referencingEntries = storage.findByReferenceField(
                schemaName, sourceDef, sourceField.getFieldKey(), entry.getId());

        List<EntryReference> refs = referencingEntries.stream()
                .map(e -> new EntryReference(e.getId(), displayResolver.resolve(sourceDef, e.getValues())))
                .toList();

        String mirrorColumnName = "cf_" + field.getFieldKey();
        entry.getValues().put(mirrorColumnName, refs);
    }

    private ContentDefinition loadDefinition(UUID definitionId) {
        return definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(definitionId)
                .map(definitionMapper::toDomain)
                .orElse(null);
    }

    private FieldDefinition findFieldById(ContentDefinition def, UUID fieldId) {
        if (def.getFields() == null) return null;
        return def.getFields().stream()
                .filter(f -> fieldId.equals(f.getId()))
                .findFirst()
                .orElse(null);
    }
}
