package io.webcontify.content.management.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

@Component
public class CursorPaginationBuilder {

    private static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);

    private final ObjectMapper objectMapper;

    public CursorPaginationBuilder() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    public record Cursor(List<SortCriterion> sorts, List<Object> lastValues, UUID lastId) {}

    public String encodeCursor(List<SortCriterion> sorts, List<Object> lastValues, UUID lastId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<Map<String, String>> sortList = sorts.stream()
                .map(s -> Map.of("col", s.column(), "dir", s.direction().name()))
                .toList();
        payload.put("sorts", sortList);
        payload.put("lastValues", lastValues);
        payload.put("lastId", lastId.toString());

        try {
            String json = objectMapper.writeValueAsString(payload);
            return Base64.getEncoder().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    public Cursor decodeCursor(String cursorStr) {
        try {
            String json = new String(Base64.getDecoder().decode(cursorStr));
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, String>> sortList = (List<Map<String, String>>) payload.get("sorts");
            List<SortCriterion> sorts = sortList.stream()
                    .map(m -> new SortCriterion(m.get("col"), SortCriterion.Direction.valueOf(m.get("dir"))))
                    .toList();

            @SuppressWarnings("unchecked")
            List<Object> lastValues = (List<Object>) payload.get("lastValues");
            UUID lastId = UUID.fromString((String) payload.get("lastId"));

            return new Cursor(sorts, lastValues, lastId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or stale cursor", e);
        }
    }

    public Condition buildKeysetCondition(List<SortCriterion> sorts, List<Object> lastValues,
                                           UUID lastId, ContentDefinition def,
                                           FieldTypeRegistry registry) {
        // Build OR-chain for keyset pagination
        // For sorts [s0, s1] with lastValues [v0, v1] and lastId lid:
        // (s0 > v0) OR (s0 = v0 AND s1 > v1) OR (s0 = v0 AND s1 = v1 AND id > lid)
        List<Condition> orConditions = new ArrayList<>();
        Map<String, FieldDefinition> fieldIndex = buildFieldIndex(def);

        for (int i = 0; i < sorts.size(); i++) {
            List<Condition> andConditions = new ArrayList<>();

            // All preceding sorts must be equal
            for (int j = 0; j < i; j++) {
                SortCriterion prevSort = sorts.get(j);
                Field<Object> prevCol = resolveField(prevSort.column(), fieldIndex, registry);
                Object prevVal = coerceCursorValue(prevSort.column(), lastValues.get(j));

                if (prevVal == null) {
                    andConditions.add(prevCol.isNull());
                } else {
                    andConditions.add(prevCol.eq(prevVal));
                }
            }

            // Current sort must be strictly greater/less
            SortCriterion currentSort = sorts.get(i);
            Field<Object> currentCol = resolveField(currentSort.column(), fieldIndex, registry);
            Object currentVal = coerceCursorValue(currentSort.column(), lastValues.get(i));

            if (currentSort.direction() == SortCriterion.Direction.ASC) {
                if (currentVal == null) {
                    // NULLs LAST for ASC: last was NULL, nothing can come after
                    continue;
                } else {
                    // Value > lastValue OR value IS NULL (NULLs LAST)
                    andConditions.add(currentCol.greaterThan(currentVal).or(currentCol.isNull()));
                }
            } else {
                if (currentVal == null) {
                    // NULLs FIRST for DESC: last was NULL, must be non-null (which comes after null in DESC NULLS FIRST)
                    andConditions.add(currentCol.isNotNull());
                } else {
                    andConditions.add(currentCol.lessThan(currentVal));
                }
            }

            if (!andConditions.isEmpty()) {
                orConditions.add(DSL.and(andConditions));
            }
        }

        // Tiebreaker: all sorts equal AND id > lastId
        List<Condition> tiebreakerAnd = new ArrayList<>();
        for (int j = 0; j < sorts.size(); j++) {
            SortCriterion s = sorts.get(j);
            Field<Object> col = resolveField(s.column(), fieldIndex, registry);
            Object val = coerceCursorValue(s.column(), lastValues.get(j));
            if (val == null) {
                tiebreakerAnd.add(col.isNull());
            } else {
                tiebreakerAnd.add(col.eq(val));
            }
        }
        tiebreakerAnd.add(ID.greaterThan(lastId));
        orConditions.add(DSL.and(tiebreakerAnd));

        return DSL.or(orConditions);
    }

    public List<SortField<?>> buildOrderBy(List<SortCriterion> sorts, ContentDefinition def,
                                            FieldTypeRegistry registry) {
        Map<String, FieldDefinition> fieldIndex = buildFieldIndex(def);
        List<SortField<?>> orderBy = new ArrayList<>();

        for (SortCriterion sort : sorts) {
            Field<Object> col = resolveField(sort.column(), fieldIndex, registry);
            if (sort.direction() == SortCriterion.Direction.ASC) {
                orderBy.add(col.asc().nullsLast());
            } else {
                orderBy.add(col.desc().nullsFirst());
            }
        }

        // Always append id ASC as final tiebreaker
        orderBy.add(ID.asc());
        return orderBy;
    }

    @SuppressWarnings("unchecked")
    private Field<Object> resolveField(String columnName, Map<String, FieldDefinition> fieldIndex,
                                        FieldTypeRegistry registry) {
        // columnName is like "cf_title"
        String fieldKey = columnName.startsWith("cf_") ? columnName.substring(3) : columnName;
        FieldDefinition fieldDef = fieldIndex.get(fieldKey);
        if (fieldDef != null) {
            FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
            return (Field<Object>) DSL.field(DSL.name(columnName), handler.columnType(fieldDef.getConfig()));
        }
        // System columns — use untyped field; coerceCursorValue ensures proper Java types
        if ("created_at".equals(columnName) || "updated_at".equals(columnName)) {
            return DSL.field(DSL.name(columnName));
        }
        return DSL.field(DSL.name(columnName));
    }

    /**
     * Coerces a cursor value to the proper Java type for system columns.
     * Cursor values are deserialized from JSON as Strings for timestamps.
     */
    private Object coerceCursorValue(String columnName, Object value) {
        if (value == null) return null;
        if (("created_at".equals(columnName) || "updated_at".equals(columnName)) && value instanceof String s) {
            return OffsetDateTime.parse(s);
        }
        return value;
    }

    private Map<String, FieldDefinition> buildFieldIndex(ContentDefinition def) {
        if (def.getFields() == null) return Map.of();
        Map<String, FieldDefinition> map = new HashMap<>();
        for (FieldDefinition f : def.getFields()) {
            map.put(f.getFieldKey(), f);
        }
        return map;
    }
}
