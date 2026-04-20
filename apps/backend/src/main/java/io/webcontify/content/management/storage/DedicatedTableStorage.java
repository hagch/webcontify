package io.webcontify.content.management.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.model.EntryHistoryRecord.FieldChange;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.query.*;
import io.webcontify.shared.exception.OptimisticLockException;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * jOOQ-based storage for content entries in per-definition dedicated tables.
 * All read methods enforce soft-delete filtering via {@code WHERE deleted_at IS NULL}.
 */
@Component
public class DedicatedTableStorage implements ContentStorage {

    private static final Logger log = LoggerFactory.getLogger(DedicatedTableStorage.class);

    private static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);
    private static final Field<Long> VERSION = DSL.field(DSL.name("version"), SQLDataType.BIGINT);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
    private static final Field<UUID> CREATED_BY = DSL.field(DSL.name("created_by"), SQLDataType.UUID);
    private static final Field<UUID> UPDATED_BY = DSL.field(DSL.name("updated_by"), SQLDataType.UUID);
    private static final Field<OffsetDateTime> DELETED_AT = DSL.field(DSL.name("deleted_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
    private static final Field<UUID> DELETED_BY = DSL.field(DSL.name("deleted_by"), SQLDataType.UUID);

    private final DSLContext dsl;
    private final FieldTypeRegistry registry;
    private final ObjectMapper objectMapper;
    private final CursorPaginationBuilder cursorBuilder;
    private final DisplayFormatResolver displayResolver;

    public DedicatedTableStorage(DSLContext dsl, FieldTypeRegistry registry,
                                  CursorPaginationBuilder cursorBuilder,
                                  DisplayFormatResolver displayResolver) {
        this.dsl = dsl;
        this.registry = registry;
        this.cursorBuilder = cursorBuilder;
        this.displayResolver = displayResolver;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    // ---- save ----

    @Override
    @Transactional
    public ContentEntry save(String schemaName, ContentDefinition def, Map<String, Object> values, UUID createdBy) {
        String schema = normalize(schemaName);
        String table = tableName(def);
        Name qualifiedTable = DSL.name(schema, table);

        UUID entryId = UuidGenerator.generate();
        OffsetDateTime now = OffsetDateTime.now();

        List<Field<?>> columns = new ArrayList<>();
        List<Object> insertValues = new ArrayList<>();

        columns.add(ID);           insertValues.add(entryId);
        columns.add(VERSION);      insertValues.add(0L);
        columns.add(CREATED_AT);   insertValues.add(now);
        columns.add(UPDATED_AT);   insertValues.add(now);
        columns.add(CREATED_BY);   insertValues.add(createdBy);
        columns.add(UPDATED_BY);   insertValues.add(createdBy);

        Map<String, Object> castedValues = new LinkedHashMap<>();
        if (def.getFields() != null) {
            for (FieldDefinition field : def.getFields()) {
                FieldTypeHandler handler = registry.get(field.getFieldType());
                if (handler.isVirtual()) continue;

                String columnName = "cf_" + field.getFieldKey();
                Object rawValue = values.get(field.getFieldKey());
                Object castedValue = rawValue != null ? handler.cast(rawValue, field.getConfig()) : null;

                columns.add(DSL.field(DSL.name(columnName), handler.columnType(field.getConfig())));
                insertValues.add(castedValue);
                castedValues.put(columnName, castedValue);
            }
        }

        dsl.insertInto(DSL.table(qualifiedTable))
                .columns(columns)
                .values(insertValues)
                .execute();

        // History: CREATED with full diff (old=null for each value)
        Map<String, Object> historyChanges = new LinkedHashMap<>();
        for (var entry : castedValues.entrySet()) {
            if (entry.getValue() != null) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("old", null);
                change.put("new", entry.getValue());
                historyChanges.put(entry.getKey(), change);
            }
        }
        insertHistoryRow(schema, table, entryId, "CREATED", historyChanges, createdBy, now);

        ContentEntry result = new ContentEntry();
        result.setId(entryId);
        result.setDefinitionId(def.getId());
        result.setVersion(0L);
        result.setCreatedAt(now);
        result.setUpdatedAt(now);
        result.setCreatedBy(createdBy);
        result.setUpdatedBy(createdBy);
        result.setValues(castedValues);
        return result;
    }

    // ---- findById ----

    @Override
    public Optional<ContentEntry> findById(String schemaName, ContentDefinition def, UUID entryId) {
        String schema = normalize(schemaName);
        List<Field<?>> selectFields = buildSelectFields(def);

        Record record = selectFromTableNotDeleted(schema, def, selectFields)
                .and(ID.eq(entryId))
                .fetchOne();

        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(recordToEntry(record, def));
    }

    // ---- update ----

    @Override
    @Transactional
    public ContentEntry update(String schemaName, ContentDefinition def, UUID entryId,
                               long expectedVersion, Map<String, Object> values, UUID updatedBy) {
        String schema = normalize(schemaName);
        String table = tableName(def);
        Name qualifiedTable = DSL.name(schema, table);

        // Fetch current entry for diff computation
        Optional<ContentEntry> current = findById(schemaName, def, entryId);
        if (current.isEmpty()) {
            throw new OptimisticLockException();
        }

        OffsetDateTime now = OffsetDateTime.now();

        @SuppressWarnings("unchecked")
        var updateStep = dsl.update(DSL.table(qualifiedTable))
                .set(VERSION, VERSION.plus(1))
                .set(UPDATED_AT, now)
                .set(UPDATED_BY, updatedBy);

        Map<String, Object> newCastedValues = new LinkedHashMap<>();
        if (def.getFields() != null) {
            for (FieldDefinition field : def.getFields()) {
                FieldTypeHandler handler = registry.get(field.getFieldType());
                if (handler.isVirtual()) continue;

                String columnName = "cf_" + field.getFieldKey();
                if (values.containsKey(field.getFieldKey())) {
                    Object rawValue = values.get(field.getFieldKey());
                    Object castedValue = rawValue != null ? handler.cast(rawValue, field.getConfig()) : null;
                    @SuppressWarnings("unchecked")
                    Field<Object> col = (Field<Object>) DSL.field(DSL.name(columnName), handler.columnType(field.getConfig()));
                    updateStep = updateStep.set(col, castedValue);
                    newCastedValues.put(columnName, castedValue);
                }
            }
        }

        int affected = updateStep
                .where(ID.eq(entryId))
                .and(VERSION.eq(expectedVersion))
                .and(DELETED_AT.isNull())
                .execute();

        if (affected == 0) {
            throw new OptimisticLockException();
        }

        // Compute diff for history
        Map<String, Object> oldValues = current.get().getValues();
        Map<String, Object> historyChanges = new LinkedHashMap<>();
        for (var entry : newCastedValues.entrySet()) {
            Object oldVal = oldValues != null ? oldValues.get(entry.getKey()) : null;
            Object newVal = entry.getValue();
            if (!Objects.equals(oldVal, newVal)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("old", oldVal);
                change.put("new", newVal);
                historyChanges.put(entry.getKey(), change);
            }
        }
        insertHistoryRow(schema, table, entryId, "UPDATED", historyChanges, updatedBy, now);

        return findById(schemaName, def, entryId)
                .orElseThrow(OptimisticLockException::new);
    }

    // ---- softDelete ----

    @Override
    @Transactional
    public void softDelete(String schemaName, ContentDefinition def, UUID entryId, UUID deletedBy) {
        String schema = normalize(schemaName);
        String table = tableName(def);
        Name qualifiedTable = DSL.name(schema, table);
        OffsetDateTime now = OffsetDateTime.now();

        int affected = dsl.update(DSL.table(qualifiedTable))
                .set(DELETED_AT, now)
                .set(DELETED_BY, deletedBy)
                .set(VERSION, VERSION.plus(1))
                .where(ID.eq(entryId))
                .and(DELETED_AT.isNull())
                .execute();

        if (affected > 0) {
            insertHistoryRow(schema, table, entryId, "DELETED", Map.of(), deletedBy, now);
        }
    }

    // ---- findAll ----

    @Override
    public List<ContentEntry> findAll(String schemaName, ContentDefinition def, int limit) {
        String schema = normalize(schemaName);
        List<Field<?>> selectFields = buildSelectFields(def);

        Result<? extends Record> records = selectFromTableNotDeleted(schema, def, selectFields)
                .orderBy(CREATED_AT.desc())
                .limit(limit)
                .fetch();

        return records.stream().map(r -> recordToEntry(r, def)).toList();
    }

    // ---- findByReferenceField ----

    @Override
    public List<ContentEntry> findByReferenceField(String schemaName, ContentDefinition def,
                                                    String fieldKey, UUID targetEntryId) {
        String schema = normalize(schemaName);
        List<Field<?>> selectFields = buildSelectFields(def);
        Field<UUID> refCol = DSL.field(DSL.name("cf_" + fieldKey), SQLDataType.UUID);

        Result<? extends Record> records = selectFromTableNotDeleted(schema, def, selectFields)
                .and(refCol.eq(targetEntryId))
                .fetch();

        return records.stream().map(r -> recordToEntry(r, def)).toList();
    }

    // ---- existsByFieldValue ----

    @Override
    public boolean existsByFieldValue(String schemaName, ContentDefinition def, String fieldKey,
                                      Object value, UUID excludeEntryId) {
        String schema = normalize(schemaName);
        String table = tableName(def);
        Name qualifiedTable = DSL.name(schema, table);
        Field<Object> col = DSL.field(DSL.name("cf_" + fieldKey));

        var condition = col.eq(value)
                .and(DELETED_AT.isNull());

        if (excludeEntryId != null) {
            condition = condition.and(ID.ne(excludeEntryId));
        }

        return dsl.fetchExists(
                dsl.selectOne()
                        .from(DSL.table(qualifiedTable))
                        .where(condition));
    }

    // ---- nullifyField ----

    @Override
    @Transactional
    public void nullifyField(String schemaName, ContentDefinition def, String fieldKey,
                             UUID targetEntryId, UUID updatedBy) {
        String schema = normalize(schemaName);
        String table = tableName(def);
        Name qualifiedTable = DSL.name(schema, table);
        Field<UUID> refCol = DSL.field(DSL.name("cf_" + fieldKey), SQLDataType.UUID);
        OffsetDateTime now = OffsetDateTime.now();

        dsl.update(DSL.table(qualifiedTable))
                .setNull(refCol)
                .set(UPDATED_AT, now)
                .set(UPDATED_BY, updatedBy)
                .set(VERSION, VERSION.plus(1))
                .where(refCol.eq(targetEntryId))
                .and(DELETED_AT.isNull())
                .execute();
    }

    // ---- getHistory ----

    @Override
    public List<EntryHistoryRecord> getHistory(String schemaName, ContentDefinition def,
                                                UUID entryId, int limit) {
        String schema = normalize(schemaName);
        String historyTable = tableName(def) + "_history";
        Name qualifiedHistoryTable = DSL.name(schema, historyTable);

        Field<UUID> hId = DSL.field(DSL.name("id"), SQLDataType.UUID);
        Field<UUID> hEntryId = DSL.field(DSL.name("entry_id"), SQLDataType.UUID);
        Field<String> hAction = DSL.field(DSL.name("action"), SQLDataType.VARCHAR);
        Field<Object> hChangedFields = DSL.field(DSL.name("changed_fields"));
        Field<OffsetDateTime> hChangedAt = DSL.field(DSL.name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
        Field<UUID> hChangedBy = DSL.field(DSL.name("changed_by"), SQLDataType.UUID);

        var records = dsl.select(hId, hEntryId, hAction, hChangedFields, hChangedAt, hChangedBy)
                .from(DSL.table(qualifiedHistoryTable))
                .where(hEntryId.eq(entryId))
                .orderBy(hChangedAt.desc())
                .limit(limit)
                .fetch();

        return records.stream().map(r -> {
            String jsonb = r.get(hChangedFields) != null ? r.get(hChangedFields).toString() : null;
            return new EntryHistoryRecord(
                    r.get(hId),
                    r.get(hEntryId),
                    r.get(hAction),
                    deserializeChangedFields(jsonb),
                    r.get(hChangedAt),
                    r.get(hChangedBy));
        }).toList();
    }

    // ---- query ----

    @Override
    public CursorPage<ContentEntry> query(String schemaName, ContentDefinition def, ContentQuery query) {
        String schema = normalize(schemaName);
        List<Field<?>> selectFields = buildSelectFields(def);

        // Start with soft-delete filter
        var step = selectFromTableNotDeleted(schema, def, selectFields);

        // Apply user filters
        if (query.filters() != null) {
            for (Filter filter : query.filters()) {
                String fieldKey = filter.column().startsWith("cf_") ? filter.column().substring(3) : filter.column();
                FieldDefinition fieldDef = findFieldByKey(def, fieldKey);
                if (fieldDef != null) {
                    FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
                    @SuppressWarnings("unchecked")
                    Field<Object> col = (Field<Object>) DSL.field(
                            DSL.name(filter.column()), handler.columnType(fieldDef.getConfig()));
                    Condition condition = handler.buildCondition(col, filter.operator(), filter.rawValue(), fieldDef.getConfig());
                    step = step.and(condition);
                }
            }
        }

        // Determine sorts (default: created_at DESC)
        List<SortCriterion> sorts = query.sorts() != null && !query.sorts().isEmpty()
                ? query.sorts()
                : List.of(new SortCriterion("created_at", SortCriterion.Direction.DESC));

        // Decode cursor and apply keyset condition if present
        CursorPageRequest pageReq = query.page() != null ? query.page() : new CursorPageRequest(null, 20);
        if (pageReq.cursor() != null && !pageReq.cursor().isBlank()) {
            CursorPaginationBuilder.Cursor cursor = cursorBuilder.decodeCursor(pageReq.cursor());
            // Validate that sort spec matches
            if (!cursor.sorts().equals(sorts)) {
                throw new IllegalArgumentException("Cursor sort specification does not match current query");
            }
            Condition keysetCondition = cursorBuilder.buildKeysetCondition(
                    cursor.sorts(), cursor.lastValues(), cursor.lastId(), def, registry);
            step = step.and(keysetCondition);
        }

        // Build ORDER BY
        List<SortField<?>> orderBy = cursorBuilder.buildOrderBy(sorts, def, registry);

        // Fetch effectiveSize + 1 to detect hasNext
        int fetchSize = pageReq.effectiveSize() + 1;
        Result<? extends Record> records = step.orderBy(orderBy).limit(fetchSize).fetch();

        boolean hasNext = records.size() > pageReq.effectiveSize();
        List<? extends Record> resultRecords = hasNext
                ? records.subList(0, pageReq.effectiveSize())
                : records;

        List<ContentEntry> entries = resultRecords.stream()
                .map(r -> recordToEntry(r, def))
                .toList();

        String nextCursor = null;
        if (hasNext && !entries.isEmpty()) {
            ContentEntry last = entries.getLast();
            List<Object> lastValues = new ArrayList<>();
            for (SortCriterion sort : sorts) {
                if (sort.column().startsWith("cf_")) {
                    lastValues.add(last.getValues().get(sort.column()));
                } else if ("created_at".equals(sort.column())) {
                    lastValues.add(last.getCreatedAt() != null ? last.getCreatedAt().toString() : null);
                } else if ("updated_at".equals(sort.column())) {
                    lastValues.add(last.getUpdatedAt() != null ? last.getUpdatedAt().toString() : null);
                }
            }
            nextCursor = cursorBuilder.encodeCursor(sorts, lastValues, last.getId());
        }

        return new CursorPage<>(entries, nextCursor, hasNext);
    }

    // ---- search ----

    @Override
    public CursorPage<EntryReference> search(String schemaName, ContentDefinition def,
                                              String searchTerm, CursorPageRequest page) {
        String schema = normalize(schemaName);
        List<Field<?>> selectFields = buildSelectFields(def);

        // Start with soft-delete filter
        var step = selectFromTableNotDeleted(schema, def, selectFields);

        // Build OR condition for search across display format fields
        if (searchTerm != null && !searchTerm.isBlank()) {
            List<String> searchColumns = displayResolver.extractSearchFields(def.getDisplayFormat());
            List<Condition> searchConditions = new ArrayList<>();

            Map<String, FieldDefinition> fieldIndex = buildFieldIndex(def);
            for (String colName : searchColumns) {
                String fieldKey = colName.startsWith("cf_") ? colName.substring(3) : colName;
                FieldDefinition fieldDef = fieldIndex.get(fieldKey);
                if (fieldDef != null && fieldDef.getFieldType() == FieldType.TEXT) {
                    Field<Object> col = DSL.field(DSL.name(colName));
                    searchConditions.add(col.likeIgnoreCase("%" + searchTerm + "%"));
                }
            }

            if (!searchConditions.isEmpty()) {
                step = step.and(DSL.or(searchConditions));
            }
        }

        // Sort by created_at DESC, tiebreaker on id
        List<SortCriterion> sorts = List.of(new SortCriterion("created_at", SortCriterion.Direction.DESC));

        CursorPageRequest pageReq = page != null ? page : new CursorPageRequest(null, 20);
        if (pageReq.cursor() != null && !pageReq.cursor().isBlank()) {
            CursorPaginationBuilder.Cursor cursor = cursorBuilder.decodeCursor(pageReq.cursor());
            Condition keysetCondition = cursorBuilder.buildKeysetCondition(
                    cursor.sorts(), cursor.lastValues(), cursor.lastId(), def, registry);
            step = step.and(keysetCondition);
        }

        List<SortField<?>> orderBy = cursorBuilder.buildOrderBy(sorts, def, registry);
        int fetchSize = pageReq.effectiveSize() + 1;
        Result<? extends Record> records = step.orderBy(orderBy).limit(fetchSize).fetch();

        boolean hasNext = records.size() > pageReq.effectiveSize();
        List<? extends Record> resultRecords = hasNext
                ? records.subList(0, pageReq.effectiveSize())
                : records;

        List<ContentEntry> entries = resultRecords.stream()
                .map(r -> recordToEntry(r, def))
                .toList();

        List<EntryReference> references = entries.stream()
                .map(entry -> new EntryReference(entry.getId(), displayResolver.resolve(def, entry.getValues())))
                .toList();

        String nextCursor = null;
        if (hasNext && !entries.isEmpty()) {
            ContentEntry last = entries.getLast();
            List<Object> lastValues = List.of(
                    last.getCreatedAt() != null ? last.getCreatedAt().toString() : null);
            nextCursor = cursorBuilder.encodeCursor(sorts, lastValues, last.getId());
        }

        return new CursorPage<>(references, nextCursor, hasNext);
    }

    // ---- Private helpers ----

    private SelectConditionStep<? extends Record> selectFromTableNotDeleted(String schema,
                                                                            ContentDefinition def,
                                                                            List<Field<?>> selectFields) {
        String table = tableName(def);
        Name qualifiedTable = DSL.name(schema, table);

        return dsl.select(selectFields)
                .from(DSL.table(qualifiedTable))
                .where(DELETED_AT.isNull());
    }

    private void insertHistoryRow(String schema, String contentTableName, UUID entryId,
                                  String action, Map<String, Object> changedFields,
                                  UUID changedBy, OffsetDateTime when) {
        String historyTable = contentTableName + "_history";
        Name qualifiedHistoryTable = DSL.name(schema, historyTable);

        String jsonb;
        try {
            jsonb = objectMapper.writeValueAsString(changedFields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize history changed_fields", e);
        }

        List<Field<?>> histCols = List.of(
                DSL.field(DSL.name("id"), SQLDataType.UUID),
                DSL.field(DSL.name("entry_id"), SQLDataType.UUID),
                DSL.field(DSL.name("action"), SQLDataType.VARCHAR),
                DSL.field(DSL.name("changed_fields"), SQLDataType.JSONB),
                DSL.field(DSL.name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE),
                DSL.field(DSL.name("changed_by"), SQLDataType.UUID));

        List<Object> histVals = List.of(
                UuidGenerator.generate(), entryId, action, jsonb, when, changedBy);

        dsl.insertInto(DSL.table(qualifiedHistoryTable))
                .columns(histCols)
                .values(histVals)
                .execute();
    }

    private List<Field<?>> buildSelectFields(ContentDefinition def) {
        List<Field<?>> fields = new ArrayList<>();
        fields.add(ID);
        fields.add(VERSION);
        fields.add(CREATED_AT);
        fields.add(UPDATED_AT);
        fields.add(CREATED_BY);
        fields.add(UPDATED_BY);

        if (def.getFields() != null) {
            for (FieldDefinition field : def.getFields()) {
                FieldTypeHandler handler = registry.get(field.getFieldType());
                if (handler.isVirtual()) continue;
                fields.add(DSL.field(DSL.name("cf_" + field.getFieldKey()), handler.columnType(field.getConfig())));
            }
        }
        return fields;
    }

    private ContentEntry recordToEntry(Record record, ContentDefinition def) {
        ContentEntry entry = new ContentEntry();
        entry.setId(record.get(ID));
        entry.setDefinitionId(def.getId());
        entry.setVersion(record.get(VERSION));
        entry.setCreatedAt(record.get(CREATED_AT));
        entry.setUpdatedAt(record.get(UPDATED_AT));
        entry.setCreatedBy(record.get(CREATED_BY));
        entry.setUpdatedBy(record.get(UPDATED_BY));

        Map<String, Object> values = new LinkedHashMap<>();
        if (def.getFields() != null) {
            for (FieldDefinition field : def.getFields()) {
                FieldTypeHandler handler = registry.get(field.getFieldType());
                if (handler.isVirtual()) continue;
                String columnName = "cf_" + field.getFieldKey();
                values.put(columnName, record.get(DSL.field(DSL.name(columnName), handler.columnType(field.getConfig()))));
            }
        }
        entry.setValues(values);
        return entry;
    }

    private Map<String, FieldChange> deserializeChangedFields(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Map<String, Object>> raw = objectMapper.readValue(jsonb,
                    new TypeReference<>() {});
            Map<String, FieldChange> result = new LinkedHashMap<>();
            for (var entry : raw.entrySet()) {
                result.put(entry.getKey(), new FieldChange(
                        entry.getValue().get("old"),
                        entry.getValue().get("new")));
            }
            return result;
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize history changed_fields: {}", jsonb, e);
            return Map.of();
        }
    }

    private FieldDefinition findFieldByKey(ContentDefinition def, String fieldKey) {
        if (def.getFields() == null) return null;
        return def.getFields().stream()
                .filter(f -> f.getFieldKey().equals(fieldKey))
                .findFirst()
                .orElse(null);
    }

    private Map<String, FieldDefinition> buildFieldIndex(ContentDefinition def) {
        if (def.getFields() == null) return Map.of();
        Map<String, FieldDefinition> map = new HashMap<>();
        for (FieldDefinition f : def.getFields()) {
            map.put(f.getFieldKey(), f);
        }
        return map;
    }

    private String tableName(ContentDefinition def) {
        return "cd_" + normalize(def.getSlug());
    }

    private String normalize(String value) {
        return value.replace("-", "_");
    }
}
