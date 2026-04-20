package io.webcontify.content.management.service;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.OnDeleteStrategy;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
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
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.exception.ValidationException;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ContentEntryService {

    private final ContentDefinitionService definitionService;
    private final ContentValidationService validationService;
    private final ReferenceResolver referenceResolver;
    private final ContentStorage storage;
    private final ContentFieldRepository fieldRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ContentEntryService(ContentDefinitionService definitionService,
                               ContentValidationService validationService,
                               ReferenceResolver referenceResolver,
                               ContentStorage storage,
                               ContentFieldRepository fieldRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.definitionService = definitionService;
        this.validationService = validationService;
        this.referenceResolver = referenceResolver;
        this.storage = storage;
        this.fieldRepository = fieldRepository;
        this.eventPublisher = eventPublisher;
    }

    public ContentEntry create(UUID tenantId, String tenantSlug, UUID appId,
                               String defSlug, Map<String, Object> rawValues, UUID userId) {
        ContentDefinition def = resolveDefinition(defSlug, appId);
        String schema = schemaName(tenantSlug);

        Map<String, Object> castedValues = validationService.validateAndCast(rawValues, def);
        checkUniqueConstraints(schema, def, castedValues, null);
        checkReferenceTargets(schema, def, castedValues);

        // Storage expects values keyed by fieldKey (without cf_ prefix)
        Map<String, Object> storageValues = toFieldKeyValues(castedValues);
        ContentEntry entry = storage.save(schema, def, storageValues, userId);
        eventPublisher.publishEvent(new ContentEntryCreatedEvent(tenantId, def.getId(), entry.getId()));
        return entry;
    }

    public ContentEntry update(UUID tenantId, String tenantSlug, UUID appId,
                               String defSlug, UUID entryId, long version,
                               Map<String, Object> rawValues, UUID userId) {
        ContentDefinition def = resolveDefinition(defSlug, appId);
        String schema = schemaName(tenantSlug);

        Map<String, Object> castedValues = validationService.validateAndCast(rawValues, def);
        checkUniqueConstraints(schema, def, castedValues, entryId);
        checkReferenceTargets(schema, def, castedValues);

        // Storage update expects values keyed by fieldKey (not column name)
        Map<String, Object> storageValues = toFieldKeyValues(castedValues);
        ContentEntry entry = storage.update(schema, def, entryId, version, storageValues, userId);

        Set<String> changedFieldKeys = castedValues.keySet();
        eventPublisher.publishEvent(new ContentEntryUpdatedEvent(tenantId, def.getId(), entryId, changedFieldKeys));
        return entry;
    }

    public void delete(UUID tenantId, String tenantSlug, UUID appId,
                       String defSlug, UUID entryId, UUID userId) {
        ContentDefinition def = resolveDefinition(defSlug, appId);
        String schema = schemaName(tenantSlug);

        // Verify entry exists
        storage.findById(schema, def, entryId)
                .orElseThrow(() -> new NotFoundException("entry.not_found"));

        // Enforce onDelete strategies for referencing definitions
        enforceOnDelete(schema, def, entryId, userId, new HashSet<>());

        storage.softDelete(schema, def, entryId, userId);
        eventPublisher.publishEvent(new ContentEntryDeletedEvent(tenantId, def.getId(), entryId));
    }

    @Transactional(readOnly = true)
    public ContentEntry findById(UUID tenantId, String tenantSlug, UUID appId,
                                 String defSlug, UUID entryId) {
        ContentDefinition def = resolveDefinition(defSlug, appId);
        String schema = schemaName(tenantSlug);

        ContentEntry entry = storage.findById(schema, def, entryId)
                .orElseThrow(() -> new NotFoundException("entry.not_found"));

        referenceResolver.resolveReferences(entry, def, schema);
        return entry;
    }

    @Transactional(readOnly = true)
    public CursorPage<ContentEntry> query(UUID tenantId, String tenantSlug, UUID appId,
                                          String defSlug, ContentQuery query) {
        ContentDefinition def = resolveDefinition(defSlug, appId);
        String schema = schemaName(tenantSlug);
        return storage.query(schema, def, query);
    }

    @Transactional(readOnly = true)
    public CursorPage<EntryReference> search(UUID tenantId, String tenantSlug, UUID appId,
                                             String defSlug, String searchTerm, CursorPageRequest page) {
        ContentDefinition def = resolveDefinition(defSlug, appId);
        String schema = schemaName(tenantSlug);
        return storage.search(schema, def, searchTerm, page);
    }

    @Transactional(readOnly = true)
    public CursorPage<EntryHistoryRecord> getHistory(UUID tenantId, String tenantSlug, UUID appId,
                                                     String defSlug, UUID entryId, CursorPageRequest page) {
        ContentDefinition def = resolveDefinition(defSlug, appId);
        String schema = schemaName(tenantSlug);
        int limit = page != null ? page.effectiveSize() : 20;
        List<EntryHistoryRecord> records = storage.getHistory(schema, def, entryId, limit);
        return new CursorPage<>(records, null, false);
    }

    // ---- Private helpers ----

    private ContentDefinition resolveDefinition(String defSlug, UUID appId) {
        return definitionService.getBySlugAndAppId(defSlug, appId);
    }

    private String schemaName(String tenantSlug) {
        return "tenant_" + tenantSlug.replace('-', '_');
    }

    private void checkUniqueConstraints(String schema, ContentDefinition def,
                                         Map<String, Object> castedValues, UUID excludeEntryId) {
        if (def.getFields() == null) return;

        List<FieldError> errors = new ArrayList<>();
        for (FieldDefinition field : def.getFields()) {
            if (!field.isUniqueValue()) continue;

            String columnName = "cf_" + field.getFieldKey();
            Object value = castedValues.get(columnName);
            if (value == null) continue;

            if (storage.existsByFieldValue(schema, def, field.getFieldKey(), value, excludeEntryId)) {
                errors.add(new FieldError(columnName, "entry.field.duplicate_value"));
            }
        }

        if (!errors.isEmpty()) {
            throw new ConflictException("entry.field.duplicate_value");
        }
    }

    private void checkReferenceTargets(String schema, ContentDefinition def,
                                        Map<String, Object> castedValues) {
        if (def.getFields() == null) return;

        List<FieldError> errors = new ArrayList<>();
        for (FieldDefinition field : def.getFields()) {
            if (field.getFieldType() != FieldType.REFERENCE) continue;
            if (!(field.getConfig() instanceof ReferenceFieldConfig refConfig)) continue;

            String columnName = "cf_" + field.getFieldKey();
            Object value = castedValues.get(columnName);
            if (value == null) continue;

            if (!(value instanceof UUID targetEntryId)) continue;

            // Load target definition
            ContentDefinition targetDef = definitionService.getById(refConfig.targetDefinitionId());
            if (storage.findById(schema, targetDef, targetEntryId).isEmpty()) {
                errors.add(new FieldError(columnName, "entry.field.invalid_reference_target"));
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("VALIDATION_ERROR", errors);
        }
    }

    private void enforceOnDelete(String schema, ContentDefinition def, UUID entryId,
                                 UUID userId, Set<UUID> visited) {
        if (visited.contains(entryId)) return;
        visited.add(entryId);

        // Find all REFERENCE fields that point to this definition
        String targetJson = "{\"targetDefinitionId\":\"" + def.getId() + "\"}";
        List<ContentFieldEntity> referencingFields = fieldRepository.findReferenceFieldsByTargetDefinitionId(targetJson);

        for (ContentFieldEntity refFieldEntity : referencingFields) {
            ContentDefinition referencingDef = definitionService.getById(
                    refFieldEntity.getDefinitionId());

            FieldDefinition refField = findFieldInDef(referencingDef, refFieldEntity.getId());
            if (refField == null || !(refField.getConfig() instanceof ReferenceFieldConfig refConfig)) {
                continue;
            }

            OnDeleteStrategy strategy = refConfig.onDelete();
            if (strategy == null) strategy = OnDeleteStrategy.RESTRICT;

            // Find entries in referencingDef that reference the entry being deleted
            List<ContentEntry> referencingEntries = storage.findByReferenceField(
                    schema, referencingDef, refField.getFieldKey(), entryId);

            if (referencingEntries.isEmpty()) continue;

            switch (strategy) {
                case RESTRICT ->
                    throw new ConflictException("entry.delete_restricted");
                case SET_NULL ->
                    storage.nullifyField(schema, referencingDef, refField.getFieldKey(), entryId, userId);
                case CASCADE -> {
                    for (ContentEntry referencingEntry : referencingEntries) {
                        enforceOnDelete(schema, referencingDef, referencingEntry.getId(), userId, visited);
                        storage.softDelete(schema, referencingDef, referencingEntry.getId(), userId);
                    }
                }
            }
        }
    }

    private FieldDefinition findFieldInDef(ContentDefinition def, UUID fieldId) {
        if (def.getFields() == null) return null;
        return def.getFields().stream()
                .filter(f -> fieldId.equals(f.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Converts column-name-keyed values (cf_xxx) to field-key-keyed values (xxx)
     * for storage update compatibility.
     */
    private Map<String, Object> toFieldKeyValues(Map<String, Object> columnValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : columnValues.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("cf_")) {
                result.put(key.substring(3), entry.getValue());
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }
}
