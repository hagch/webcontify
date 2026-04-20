# Cursor Pagination + Filtering + Sorting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Replace simple limit-based list with cursor pagination. Add RHS operator filtering on filterable fields, sorting on sortable fields (max 3), dedicated reference search endpoint, and paginated history endpoint.

**Architecture:** `CursorPaginationBuilder` encodes/decodes Base64 JSON cursor with sort spec. `RhsOperatorParser` parses query params to `Filter` objects. `FieldQueryBuilder` on each handler maps operator to jOOQ `Condition`. `DedicatedTableStorage.query()` combines filters, cursor, sort, limit into a single dynamic SELECT.

**Tech Stack:** Java 21, Spring Boot 4, jOOQ, Jackson

---

## Task 1: CursorPageRequest + CursorPage domain records

**Files:**
- `apps/backend/src/main/java/io/webcontify/content/management/model/CursorPageRequest.java` (new)
- `apps/backend/src/main/java/io/webcontify/content/management/model/CursorPage.java` (new)

### TDD Cycle

**Red — write test first:**

```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.model.CursorPageRequestTest
```

Test file: `apps/backend/src/test/java/io/webcontify/content/management/model/CursorPageRequestTest.java`

```java
package io.webcontify.content.management.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CursorPageRequestTest {

    @Test
    void effectiveSize_returnsDefault_whenSizeIsZero() {
        var req = new CursorPageRequest(null, 0);
        assertThat(req.effectiveSize()).isEqualTo(20);
    }

    @Test
    void effectiveSize_returnsDefault_whenSizeIsNegative() {
        var req = new CursorPageRequest(null, -5);
        assertThat(req.effectiveSize()).isEqualTo(20);
    }

    @Test
    void effectiveSize_returnsCapped_whenSizeExceedsMax() {
        var req = new CursorPageRequest(null, 500);
        assertThat(req.effectiveSize()).isEqualTo(100);
    }

    @Test
    void effectiveSize_returnsExact_whenSizeIsValid() {
        var req = new CursorPageRequest(null, 42);
        assertThat(req.effectiveSize()).isEqualTo(42);
    }

    @Test
    void cursor_canBeNull() {
        var req = new CursorPageRequest(null, 20);
        assertThat(req.cursor()).isNull();
    }
}
```

**Green — implementation:**

```java
// CursorPageRequest.java
package io.webcontify.content.management.model;

public record CursorPageRequest(String cursor, int size) {

    public int effectiveSize() {
        if (size <= 0) return 20;
        return Math.min(size, 100);
    }
}
```

```java
// CursorPage.java
package io.webcontify.content.management.model;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
```

**Run test:**
```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.model.CursorPageRequestTest
```

**Commit:**
```
feat(content): add CursorPageRequest and CursorPage domain records
```

---

## Task 2: CursorPaginationBuilder

Encodes/decodes Base64 JSON cursor. Builds the jOOQ WHERE clause for keyset pagination with NULL handling and mixed sort directions.

**File:** `apps/backend/src/main/java/io/webcontify/content/management/storage/CursorPaginationBuilder.java` (new)

### TDD Cycle

**Red — write test first:**

```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.storage.CursorPaginationBuilderTest
```

Test file: `apps/backend/src/test/java/io/webcontify/content/management/storage/CursorPaginationBuilderTest.java`

```java
package io.webcontify.content.management.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.webcontify.content.management.storage.CursorPaginationBuilder.Cursor;
import io.webcontify.content.management.storage.CursorPaginationBuilder.SortCriterion;
import org.jooq.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorPaginationBuilderTest {

    private CursorPaginationBuilder builder;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        builder = new CursorPaginationBuilder(mapper);
    }

    @Test
    void encode_then_decode_roundtrip() {
        UUID lastId = UUID.fromString("018f1c3d-0000-7000-8000-000000000001");
        var sorts = List.of(new SortCriterion("cf_name", true));
        var values = List.of((Object) "Alice");
        var cursor = new Cursor(sorts, values, lastId);

        String encoded = builder.encode(cursor);
        Cursor decoded = builder.decode(encoded);

        assertThat(decoded.lastId()).isEqualTo(lastId);
        assertThat(decoded.sorts()).hasSize(1);
        assertThat(decoded.sorts().get(0).column()).isEqualTo("cf_name");
        assertThat(decoded.sorts().get(0).ascending()).isTrue();
        assertThat(decoded.lastValues()).containsExactly("Alice");
    }

    @Test
    void decode_throwsIllegalArgument_forGarbageInput() {
        assertThatThrownBy(() -> builder.decode("not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cursor");
    }

    @Test
    void buildCondition_singleAscSort_buildsCorrectOrChain() {
        UUID lastId = UUID.fromString("018f1c3d-0000-7000-8000-000000000001");
        var sorts = List.of(new SortCriterion("cf_name", true));
        var cursor = new Cursor(sorts, List.of((Object) "Alice"), lastId);

        Condition condition = builder.buildCondition(cursor);

        assertThat(condition).isNotNull();
        String sql = condition.toString();
        // Expect: (cf_name > 'Alice') OR (cf_name = 'Alice' AND id > lastId)
        assertThat(sql).contains("cf_name");
        assertThat(sql).contains("id");
    }

    @Test
    void buildCondition_singleDescSort_buildsLessThanCondition() {
        UUID lastId = UUID.fromString("018f1c3d-0000-7000-8000-000000000001");
        var sorts = List.of(new SortCriterion("cf_score", false));
        var cursor = new Cursor(sorts, List.of((Object) 100L), lastId);

        Condition condition = builder.buildCondition(cursor);
        assertThat(condition.toString()).contains("cf_score");
    }

    @Test
    void buildCondition_nullValue_handlesNullsLast() {
        UUID lastId = UUID.fromString("018f1c3d-0000-7000-8000-000000000001");
        var sorts = List.of(new SortCriterion("cf_name", true));
        var cursor = new Cursor(sorts, List.of((Object) "null-marker"), lastId);

        // Should not throw
        Condition condition = builder.buildCondition(cursor);
        assertThat(condition).isNotNull();
    }

    @Test
    void encode_producesValidBase64() {
        UUID lastId = UUID.randomUUID();
        var cursor = new Cursor(
                List.of(new SortCriterion("cf_title", true)),
                List.of((Object) "Test"),
                lastId);
        String encoded = builder.encode(cursor);
        // Base64 URL-safe: no +, /, or = padding (or standard variants)
        assertThat(encoded).doesNotContain(" ");
        assertThat(encoded).isNotBlank();
    }
}
```

**Green — implementation:**

```java
// CursorPaginationBuilder.java
package io.webcontify.content.management.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;

@Component
public class CursorPaginationBuilder {

    private final ObjectMapper objectMapper;

    public CursorPaginationBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record SortCriterion(String column, boolean ascending) {}

    public record Cursor(List<SortCriterion> sorts, List<Object> lastValues, UUID lastId) {}

    public String encode(Cursor cursor) {
        try {
            String json = objectMapper.writeValueAsString(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    public Cursor decode(String encoded) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return objectMapper.readValue(bytes, Cursor.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a WHERE condition for keyset (cursor) pagination.
     *
     * For N sort fields with id tiebreaker, generates:
     *   (s1 op v1)
     *   OR (s1 = v1 AND s2 op v2)
     *   OR (s1 = v1 AND s2 = v2 AND s3 op v3)
     *   OR (s1 = v1 AND ... AND id > lastId)
     *
     * NULL semantics: NULLs sort LAST for ASC, FIRST for DESC.
     * When lastValue is null (NULL was in cursor position), we exclude those rows
     * by requiring either IS NOT NULL (ASC, since null is last, there are no
     * "after null" rows) or IS NULL AND id > lastId (DESC, null-first rows continue).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Condition buildCondition(Cursor cursor) {
        List<SortCriterion> sorts = cursor.sorts();
        List<Object> values = cursor.lastValues();
        UUID lastId = cursor.lastId();

        Field<UUID> idField = field(name("id"), UUID.class);
        Condition result = noCondition();

        // Build each OR branch
        // Branch k: sorts[0..k-1] are equal, sorts[k] advances
        for (int k = 0; k <= sorts.size(); k++) {
            Condition branch = noCondition();

            // Equality conditions for sorts[0..k-1]
            for (int i = 0; i < k; i++) {
                SortCriterion sc = sorts.get(i);
                Object val = values.get(i);
                Field<Object> f = (Field<Object>) field(name(sc.column()));
                if (val == null) {
                    branch = branch.and(f.isNull());
                } else {
                    branch = branch.and(f.eq(val));
                }
            }

            if (k < sorts.size()) {
                // Advance condition on sorts[k]
                SortCriterion sc = sorts.get(k);
                Object val = values.get(k);
                Field<Object> f = (Field<Object>) field(name(sc.column()));
                Condition advance = buildAdvanceCondition(f, sc.ascending(), val);
                branch = branch.and(advance);
            } else {
                // Final tiebreaker: all sort fields equal, advance on id
                branch = branch.and(idField.gt(lastId));
            }

            result = result.or(branch);
        }

        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Condition buildAdvanceCondition(Field<Object> f, boolean ascending, Object val) {
        if (val == null) {
            // NULLs LAST for ASC: nothing after null, so this branch never matches
            // NULLs FIRST for DESC: when last seen value was null, advance means IS NULL (continue in null group) handled by tiebreaker
            // Return impossible condition for ASC (null is last, no rows after it in sort order)
            if (ascending) {
                return DSL.falseCondition();
            } else {
                // DESC null-first: rows after a null-valued position are IS NOT NULL rows
                return f.isNotNull();
            }
        }
        if (ascending) {
            return f.gt(val).or(f.isNull()); // NULLS LAST: nulls come after non-null values
        } else {
            return f.lt(val); // NULLS FIRST for DESC: null rows already passed, only non-null smaller values remain
        }
    }

    /**
     * Validates that a decoded cursor's sort spec matches the requested sorts.
     * Throws IllegalArgumentException if stale (sort config changed).
     */
    public void validateSortSpec(Cursor cursor, List<SortCriterion> requestedSorts) {
        List<SortCriterion> cursorSorts = cursor.sorts();
        if (cursorSorts.size() != requestedSorts.size()) {
            throw new IllegalArgumentException("Stale cursor: sort specification has changed");
        }
        for (int i = 0; i < cursorSorts.size(); i++) {
            SortCriterion cs = cursorSorts.get(i);
            SortCriterion rs = requestedSorts.get(i);
            if (!cs.column().equals(rs.column()) || cs.ascending() != rs.ascending()) {
                throw new IllegalArgumentException("Stale cursor: sort specification has changed");
            }
        }
    }
}
```

**Run test:**
```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.storage.CursorPaginationBuilderTest
```

**Commit:**
```
feat(content): add CursorPaginationBuilder with Base64 JSON cursor encoding
```

---

## Task 3: RhsOperatorParser

Parses query params like `?cf_status=eq:published&cf_views=gt:100` into typed `Filter` objects. Validates operators against field type compatibility.

**Files:**
- `apps/backend/src/main/java/io/webcontify/content/management/query/Filter.java` (new)
- `apps/backend/src/main/java/io/webcontify/content/management/query/Operator.java` (new)
- `apps/backend/src/main/java/io/webcontify/content/management/query/RhsOperatorParser.java` (new)

### TDD Cycle

**Red — write test first:**

```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.query.RhsOperatorParserTest
```

Test file: `apps/backend/src/test/java/io/webcontify/content/management/query/RhsOperatorParserTest.java`

```java
package io.webcontify.content.management.query;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.fieldtype.handler.IntegerFieldTypeHandler;
import io.webcontify.content.fieldtype.handler.TextFieldTypeHandler;
import io.webcontify.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RhsOperatorParserTest {

    private FieldTypeRegistry registry;
    private RhsOperatorParser parser;

    @BeforeEach
    void setUp() {
        registry = mock(FieldTypeRegistry.class);
        when(registry.get(FieldType.TEXT)).thenReturn(new TextFieldTypeHandler());
        when(registry.get(FieldType.INTEGER)).thenReturn(new IntegerFieldTypeHandler());
        parser = new RhsOperatorParser(registry);
    }

    private ContentDefinition definitionWith(FieldDefinition... fields) {
        ContentDefinition def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setFields(List.of(fields));
        return def;
    }

    private FieldDefinition textField(String key) {
        FieldDefinition f = new FieldDefinition();
        f.setFieldKey(key);
        f.setFieldType(FieldType.TEXT);
        return f;
    }

    private FieldDefinition intField(String key) {
        FieldDefinition f = new FieldDefinition();
        f.setFieldKey(key);
        f.setFieldType(FieldType.INTEGER);
        return f;
    }

    @Test
    void parse_textEq_returnsFilter() {
        ContentDefinition def = definitionWith(textField("status"));
        Map<String, String> params = Map.of("cf_status", "eq:published");

        List<Filter> filters = parser.parse(params, def);

        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).column()).isEqualTo("cf_status");
        assertThat(filters.get(0).operator()).isEqualTo(Operator.EQ);
        assertThat(filters.get(0).value()).isEqualTo("published");
    }

    @Test
    void parse_integerGt_castsValue() {
        ContentDefinition def = definitionWith(intField("views"));
        Map<String, String> params = Map.of("cf_views", "gt:100");

        List<Filter> filters = parser.parse(params, def);

        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).operator()).isEqualTo(Operator.GT);
        assertThat(filters.get(0).value()).isEqualTo(100L);
    }

    @Test
    void parse_isNull_acceptsNoValue() {
        ContentDefinition def = definitionWith(textField("category"));
        Map<String, String> params = Map.of("cf_category", "isnull:true");

        List<Filter> filters = parser.parse(params, def);

        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).operator()).isEqualTo(Operator.ISNULL);
        assertThat(filters.get(0).value()).isEqualTo("true");
    }

    @Test
    void parse_unknownParam_isIgnored() {
        ContentDefinition def = definitionWith(textField("name"));
        Map<String, String> params = Map.of("cursor", "abc", "size", "10", "cf_name", "eq:test");

        List<Filter> filters = parser.parse(params, def);

        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).column()).isEqualTo("cf_name");
    }

    @Test
    void parse_unknownField_isIgnored() {
        ContentDefinition def = definitionWith(textField("name"));
        Map<String, String> params = Map.of("cf_nonexistent", "eq:value");

        List<Filter> filters = parser.parse(params, def);

        assertThat(filters).isEmpty();
    }

    @Test
    void parse_unknownOperator_throwsValidationException() {
        ContentDefinition def = definitionWith(textField("name"));
        Map<String, String> params = Map.of("cf_name", "between:a:b");

        assertThatThrownBy(() -> parser.parse(params, def))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parse_missingColon_throwsValidationException() {
        ContentDefinition def = definitionWith(textField("name"));
        Map<String, String> params = Map.of("cf_name", "eqpublished");

        assertThatThrownBy(() -> parser.parse(params, def))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void parse_multipleFields_returnsAllFilters() {
        ContentDefinition def = definitionWith(textField("status"), intField("views"));
        Map<String, String> params = Map.of(
                "cf_status", "eq:published",
                "cf_views", "gte:50"
        );

        List<Filter> filters = parser.parse(params, def);

        assertThat(filters).hasSize(2);
    }
}
```

**Green — implementation:**

```java
// Operator.java
package io.webcontify.content.management.query;

public enum Operator {
    EQ, NEQ, GT, GTE, LT, LTE, LIKE, IN, ISNULL
}
```

```java
// Filter.java
package io.webcontify.content.management.query;

public record Filter(String column, Operator operator, Object value) {}
```

```java
// RhsOperatorParser.java
package io.webcontify.content.management.query;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RhsOperatorParser {

    private static final Set<String> RESERVED_PARAMS = Set.of(
            "cursor", "size", "sort", "appSlug", "defSlug");

    private final FieldTypeRegistry registry;

    public RhsOperatorParser(FieldTypeRegistry registry) {
        this.registry = registry;
    }

    public List<Filter> parse(Map<String, String> queryParams, ContentDefinition definition) {
        List<Filter> filters = new ArrayList<>();

        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            String paramName = entry.getKey();
            String rawValue = entry.getValue();

            if (!paramName.startsWith("cf_") || RESERVED_PARAMS.contains(paramName)) {
                continue;
            }

            // Find matching field definition
            String fieldKey = paramName.substring(3); // remove "cf_" prefix
            FieldDefinition fieldDef = definition.getFields().stream()
                    .filter(f -> f.getFieldKey().equals(fieldKey))
                    .findFirst().orElse(null);

            if (fieldDef == null) {
                // Unknown field — silently ignore (do not leak schema info)
                continue;
            }

            // Parse operator:value
            int colonIdx = rawValue.indexOf(':');
            if (colonIdx < 0) {
                throw new ValidationException("VALIDATION_ERROR",
                        List.of(new FieldError(paramName, "error.filter.invalid_format")));
            }

            String operatorStr = rawValue.substring(0, colonIdx).toLowerCase();
            String valueStr = rawValue.substring(colonIdx + 1);

            Operator operator = parseOperator(operatorStr, paramName);

            // ISNULL operator does not cast the value
            Object typedValue;
            if (operator == Operator.ISNULL) {
                typedValue = valueStr;
            } else if (operator == Operator.IN) {
                // IN: cast each comma-separated value
                FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
                String[] parts = valueStr.split(",");
                List<Object> casted = new ArrayList<>();
                for (String part : parts) {
                    casted.add(handler.cast(part.trim(), fieldDef.getConfig()));
                }
                typedValue = casted;
            } else {
                FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
                typedValue = handler.cast(valueStr, fieldDef.getConfig());
            }

            filters.add(new Filter(paramName, operator, typedValue));
        }

        return filters;
    }

    private Operator parseOperator(String op, String paramName) {
        return switch (op) {
            case "eq"     -> Operator.EQ;
            case "neq"    -> Operator.NEQ;
            case "gt"     -> Operator.GT;
            case "gte"    -> Operator.GTE;
            case "lt"     -> Operator.LT;
            case "lte"    -> Operator.LTE;
            case "like"   -> Operator.LIKE;
            case "in"     -> Operator.IN;
            case "isnull" -> Operator.ISNULL;
            default -> throw new ValidationException("VALIDATION_ERROR",
                    List.of(new FieldError(paramName, "error.filter.unknown_operator")));
        };
    }
}
```

**Run test:**
```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.query.RhsOperatorParserTest
```

**Commit:**
```
feat(content): add RhsOperatorParser for filter query param parsing
```

---

## Task 4: FieldQueryBuilder.buildCondition per type handler

Override `buildCondition` in 7 concrete handlers. Change the interface return type from `Object` to `org.jooq.Condition`.

**Files to edit:**
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/FieldQueryBuilder.java` — change return type
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/TextFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/IntegerFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/DecimalFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/BooleanFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/DateFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/EnumFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/ReferenceFieldTypeHandler.java`

### TDD Cycle

**Red — write tests first:**

```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.fieldtype.handler.TextFieldTypeHandlerConditionTest
```

Test file: `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/TextFieldTypeHandlerConditionTest.java`

```java
package io.webcontify.content.fieldtype.handler;

import io.webcontify.content.definition.model.TextFieldConfig;
import io.webcontify.shared.exception.ValidationException;
import org.jooq.Condition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextFieldTypeHandlerConditionTest {

    private final TextFieldTypeHandler handler = new TextFieldTypeHandler();
    private final TextFieldConfig config = new TextFieldConfig(null, null, null, null, null);

    @Test
    void buildCondition_eq_returnsEqualCondition() {
        Condition c = handler.buildCondition("cf_name", "eq", "Alice");
        assertThat(c.toString()).contains("cf_name").contains("Alice");
    }

    @Test
    void buildCondition_neq_returnsNotEqualCondition() {
        Condition c = handler.buildCondition("cf_name", "neq", "Alice");
        assertThat(c.toString()).contains("cf_name");
    }

    @Test
    void buildCondition_like_buildsIlikeWithWildcards() {
        Condition c = handler.buildCondition("cf_name", "like", "alice");
        assertThat(c.toString()).containsIgnoringCase("alice");
    }

    @Test
    void buildCondition_in_buildsInCondition() {
        Condition c = handler.buildCondition("cf_status", "in", "a,b,c");
        assertThat(c.toString()).contains("cf_status");
    }

    @Test
    void buildCondition_isnull_true_buildsIsNull() {
        Condition c = handler.buildCondition("cf_name", "isnull", "true");
        assertThat(c.toString()).containsIgnoringCase("null");
    }

    @Test
    void buildCondition_isnull_false_buildsIsNotNull() {
        Condition c = handler.buildCondition("cf_name", "isnull", "false");
        assertThat(c.toString()).containsIgnoringCase("not null").or().containsIgnoringCase("is not null");
    }

    @Test
    void buildCondition_gt_throwsValidationException() {
        assertThatThrownBy(() -> handler.buildCondition("cf_name", "gt", "x"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void buildCondition_unknownOperator_throwsValidationException() {
        assertThatThrownBy(() -> handler.buildCondition("cf_name", "between", "a"))
                .isInstanceOf(ValidationException.class);
    }
}
```

**Green — update FieldQueryBuilder interface:**

```java
// FieldQueryBuilder.java
package io.webcontify.content.fieldtype;

import org.jooq.Condition;

public interface FieldQueryBuilder {

    default Condition buildCondition(String columnName, String operator, String value) {
        throw new UnsupportedOperationException("Query building not implemented for this field type");
    }
}
```

**Green — TextFieldTypeHandler.buildCondition:**

Add to `TextFieldTypeHandler.java` (before the closing brace):

```java
@Override
public Condition buildCondition(String columnName, String operator, String value) {
    var f = field(name(columnName));
    return switch (operator) {
        case "eq"     -> f.eq(value);
        case "neq"    -> f.ne(value);
        case "like"   -> f.likeIgnoreCase("%" + value + "%");
        case "in"     -> f.in((Object[]) value.split(","));
        case "isnull" -> "true".equals(value) ? f.isNull() : f.isNotNull();
        default -> throw new ValidationException("VALIDATION_ERROR",
                List.of(new FieldError(columnName, "error.filter.unsupported_operator_for_type")));
    };
}
```

Add imports at top of TextFieldTypeHandler:
```java
import org.jooq.Condition;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
```

**IntegerFieldTypeHandler.buildCondition:**

```java
@Override
public Condition buildCondition(String columnName, String operator, String value) {
    var f = field(name(columnName));
    return switch (operator) {
        case "eq"     -> f.eq(Long.valueOf(value));
        case "neq"    -> f.ne(Long.valueOf(value));
        case "gt"     -> f.gt(Long.valueOf(value));
        case "gte"    -> f.ge(Long.valueOf(value));
        case "lt"     -> f.lt(Long.valueOf(value));
        case "lte"    -> f.le(Long.valueOf(value));
        case "in"     -> {
            String[] parts = value.split(",");
            Long[] longs = java.util.Arrays.stream(parts)
                    .map(String::trim).map(Long::valueOf).toArray(Long[]::new);
            yield f.in((Object[]) longs);
        }
        case "isnull" -> "true".equals(value) ? f.isNull() : f.isNotNull();
        default -> throw new ValidationException("VALIDATION_ERROR",
                List.of(new FieldError(columnName, "error.filter.unsupported_operator_for_type")));
    };
}
```

**DecimalFieldTypeHandler.buildCondition:**

```java
@Override
public Condition buildCondition(String columnName, String operator, String value) {
    var f = field(name(columnName));
    return switch (operator) {
        case "eq"     -> f.eq(new java.math.BigDecimal(value));
        case "neq"    -> f.ne(new java.math.BigDecimal(value));
        case "gt"     -> f.gt(new java.math.BigDecimal(value));
        case "gte"    -> f.ge(new java.math.BigDecimal(value));
        case "lt"     -> f.lt(new java.math.BigDecimal(value));
        case "lte"    -> f.le(new java.math.BigDecimal(value));
        case "isnull" -> "true".equals(value) ? f.isNull() : f.isNotNull();
        default -> throw new ValidationException("VALIDATION_ERROR",
                List.of(new FieldError(columnName, "error.filter.unsupported_operator_for_type")));
    };
}
```

**BooleanFieldTypeHandler.buildCondition:**

```java
@Override
public Condition buildCondition(String columnName, String operator, String value) {
    var f = field(name(columnName));
    return switch (operator) {
        case "eq"     -> f.eq(Boolean.valueOf(value));
        case "neq"    -> f.ne(Boolean.valueOf(value));
        case "isnull" -> "true".equals(value) ? f.isNull() : f.isNotNull();
        default -> throw new ValidationException("VALIDATION_ERROR",
                List.of(new FieldError(columnName, "error.filter.unsupported_operator_for_type")));
    };
}
```

**DateFieldTypeHandler.buildCondition:**

```java
@Override
public Condition buildCondition(String columnName, String operator, String value) {
    var f = field(name(columnName));
    return switch (operator) {
        case "eq"     -> f.eq(parseDate(value));
        case "neq"    -> f.ne(parseDate(value));
        case "gt"     -> f.gt(parseDate(value));
        case "gte"    -> f.ge(parseDate(value));
        case "lt"     -> f.lt(parseDate(value));
        case "lte"    -> f.le(parseDate(value));
        case "isnull" -> "true".equals(value) ? f.isNull() : f.isNotNull();
        default -> throw new ValidationException("VALIDATION_ERROR",
                List.of(new FieldError(columnName, "error.filter.unsupported_operator_for_type")));
    };
}

private OffsetDateTime parseDate(String value) {
    return (OffsetDateTime) cast(value, null);
}
```

**EnumFieldTypeHandler.buildCondition:**

```java
@Override
public Condition buildCondition(String columnName, String operator, String value) {
    var f = field(name(columnName));
    return switch (operator) {
        case "eq"     -> f.eq(value);
        case "neq"    -> f.ne(value);
        case "in"     -> f.in((Object[]) value.split(","));
        case "isnull" -> "true".equals(value) ? f.isNull() : f.isNotNull();
        default -> throw new ValidationException("VALIDATION_ERROR",
                List.of(new FieldError(columnName, "error.filter.unsupported_operator_for_type")));
    };
}
```

**ReferenceFieldTypeHandler.buildCondition:**

```java
@Override
public Condition buildCondition(String columnName, String operator, String value) {
    var f = field(name(columnName));
    return switch (operator) {
        case "eq"     -> f.eq(UUID.fromString(value));
        case "neq"    -> f.ne(UUID.fromString(value));
        case "in"     -> {
            UUID[] uuids = java.util.Arrays.stream(value.split(","))
                    .map(String::trim).map(UUID::fromString).toArray(UUID[]::new);
            yield f.in((Object[]) uuids);
        }
        case "isnull" -> "true".equals(value) ? f.isNull() : f.isNotNull();
        default -> throw new ValidationException("VALIDATION_ERROR",
                List.of(new FieldError(columnName, "error.filter.unsupported_operator_for_type")));
    };
}
```

**Note:** All handlers need these imports added:
```java
import org.jooq.Condition;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
```

**Run tests:**
```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.fieldtype.handler.TextFieldTypeHandlerConditionTest
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.fieldtype
```

**Commit:**
```
feat(content): implement buildCondition on all field type handlers for RHS filtering
```

---

## Task 5: Extend ContentStorage.query + DedicatedTableStorage.query

Add the main query method that combines filters, cursor pagination, and sorting into a single dynamic jOOQ SELECT.

**Files to edit:**
- `apps/backend/src/main/java/io/webcontify/content/management/storage/ContentStorage.java`
- `apps/backend/src/main/java/io/webcontify/content/management/storage/DedicatedTableStorage.java`

### TDD Cycle

**Red — write integration test first:**

```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.storage.DedicatedTableStorageQueryTest
```

Test file: `apps/backend/src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageQueryTest.java`

```java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.TextFieldConfig;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.CursorPage;
import io.webcontify.content.management.model.CursorPageRequest;
import io.webcontify.content.management.query.Filter;
import io.webcontify.content.management.query.Operator;
import io.webcontify.shared.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DedicatedTableStorageQueryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DedicatedTableStorage storage;

    private static final String SCHEMA = "public";
    private ContentDefinition definition;

    @BeforeEach
    void setUp() {
        // Create a simple content definition with a text field
        definition = new ContentDefinition();
        definition.setId(UuidGenerator.generate());
        definition.setSlug("article");

        FieldDefinition titleField = new FieldDefinition();
        titleField.setFieldKey("title");
        titleField.setFieldType(FieldType.TEXT);
        titleField.setConfig(new TextFieldConfig(null, null, null, null, null));
        definition.setFields(List.of(titleField));

        // Note: In a real integration test, the table would be created via DynamicTableManager
        // For this test, assume table already exists (created via DDL in @BeforeEach via DSLContext)
    }

    @Test
    void query_noFiltersNoSort_returnsCursorPage() {
        UUID userId = UuidGenerator.generate();
        storage.save(SCHEMA, definition, Map.of("cf_title", "Alpha"), userId);
        storage.save(SCHEMA, definition, Map.of("cf_title", "Beta"), userId);
        storage.save(SCHEMA, definition, Map.of("cf_title", "Gamma"), userId);

        CursorPageRequest request = new CursorPageRequest(null, 2);
        CursorPage<ContentEntry> page = storage.query(SCHEMA, definition, List.of(), List.of(), request);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    void query_withFilter_returnsFilteredResults() {
        UUID userId = UuidGenerator.generate();
        storage.save(SCHEMA, definition, Map.of("cf_title", "Alpha"), userId);
        storage.save(SCHEMA, definition, Map.of("cf_title", "Beta"), userId);

        Filter filter = new Filter("cf_title", Operator.EQ, "Alpha");
        CursorPage<ContentEntry> page = storage.query(
                SCHEMA, definition, List.of(filter), List.of(),
                new CursorPageRequest(null, 10));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getValues().get("cf_title")).isEqualTo("Alpha");
    }

    @Test
    void query_cursorNavigation_returnsNextPage() {
        UUID userId = UuidGenerator.generate();
        for (int i = 1; i <= 5; i++) {
            storage.save(SCHEMA, definition, Map.of("cf_title", "Entry-" + i), userId);
        }

        CursorPageRequest firstPage = new CursorPageRequest(null, 2);
        CursorPage<ContentEntry> page1 = storage.query(SCHEMA, definition, List.of(), List.of(), firstPage);

        assertThat(page1.items()).hasSize(2);
        assertThat(page1.hasNext()).isTrue();

        CursorPageRequest secondPage = new CursorPageRequest(page1.nextCursor(), 2);
        CursorPage<ContentEntry> page2 = storage.query(SCHEMA, definition, List.of(), List.of(), secondPage);

        assertThat(page2.items()).hasSize(2);
        // No overlap between pages
        var ids1 = page1.items().stream().map(ContentEntry::getId).toList();
        var ids2 = page2.items().stream().map(ContentEntry::getId).toList();
        assertThat(ids1).doesNotContainAnyElementsOf(ids2);
    }
}
```

**Green — update ContentStorage interface:**

Add to `ContentStorage.java`:

```java
import io.webcontify.content.management.model.CursorPage;
import io.webcontify.content.management.model.CursorPageRequest;
import io.webcontify.content.management.query.Filter;
import io.webcontify.content.management.storage.CursorPaginationBuilder.SortCriterion;

// New method in interface:
CursorPage<ContentEntry> query(
    String schemaName,
    ContentDefinition definition,
    List<Filter> filters,
    List<SortCriterion> sorts,
    CursorPageRequest pageRequest);
```

**Green — DedicatedTableStorage.query implementation:**

Add to `DedicatedTableStorage.java`:

```java
@Override
@Transactional(readOnly = true)
public CursorPage<ContentEntry> query(
        String schemaName,
        ContentDefinition definition,
        List<Filter> filters,
        List<CursorPaginationBuilder.SortCriterion> sorts,
        CursorPageRequest pageRequest) {

    String tableName = tableName(definition);
    int fetchSize = pageRequest.effectiveSize() + 1; // +1 to detect hasNext

    // Build SELECT fields
    List<Field<?>> selectFields = buildSelectFields(definition);

    // Base condition: not deleted
    Condition whereCondition = field(name("deleted_at"), SQLDataType.TIMESTAMPWITHTIMEZONE).isNull();

    // Apply filters
    for (Filter filter : filters) {
        FieldDefinition fieldDef = definition.getFields().stream()
                .filter(f -> ("cf_" + f.getFieldKey()).equals(filter.column()))
                .findFirst().orElse(null);
        if (fieldDef == null) continue;

        FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
        if (filter.operator() == Operator.IN) {
            // IN operator: value is List<Object>
            @SuppressWarnings("unchecked")
            List<Object> inValues = (List<Object>) filter.value();
            whereCondition = whereCondition.and(
                    field(name(filter.column())).in(inValues));
        } else if (filter.operator() == Operator.ISNULL) {
            String val = filter.value().toString();
            Field<?> f = field(name(filter.column()));
            whereCondition = whereCondition.and(
                    "true".equals(val) ? f.isNull() : f.isNotNull());
        } else {
            // Delegate to handler.buildCondition — pass raw string for operator-value
            // Handler already cast the value; for jOOQ we need the condition
            String operatorStr = filter.operator().name().toLowerCase();
            Object rawVal = filter.value();
            whereCondition = whereCondition.and(
                    buildFilterCondition(filter.column(), operatorStr, rawVal));
        }
    }

    // Apply cursor WHERE condition (keyset pagination)
    if (pageRequest.cursor() != null) {
        CursorPaginationBuilder.Cursor cursor = paginationBuilder.decode(pageRequest.cursor());
        // Validate sort spec matches current request
        if (!sorts.isEmpty()) {
            paginationBuilder.validateSortSpec(cursor, sorts);
        }
        whereCondition = whereCondition.and(paginationBuilder.buildCondition(cursor));
    }

    // Build ORDER BY
    List<SortField<?>> orderBy = new ArrayList<>();
    for (CursorPaginationBuilder.SortCriterion sc : sorts) {
        Field<?> f = field(name(sc.column()));
        orderBy.add(sc.ascending()
                ? f.asc().nullsLast()
                : f.desc().nullsFirst());
    }
    // Always add id as tiebreaker
    orderBy.add(field(name("id"), SQLDataType.UUID).asc());

    Result<Record> result = dsl.select(selectFields)
            .from(table(name(schemaName, tableName)))
            .where(whereCondition)
            .orderBy(orderBy)
            .limit(fetchSize)
            .fetch();

    boolean hasNext = result.size() > pageRequest.effectiveSize();
    List<Record> pageRecords = hasNext
            ? result.subList(0, pageRequest.effectiveSize())
            : new ArrayList<>(result);

    List<ContentEntry> entries = pageRecords.stream()
            .map(r -> recordToEntry(r, definition))
            .toList();

    String nextCursor = null;
    if (hasNext && !entries.isEmpty()) {
        ContentEntry last = entries.get(entries.size() - 1);
        List<Object> lastValues = sorts.stream()
                .map(sc -> last.getValues().get(sc.column()))
                .toList();
        CursorPaginationBuilder.Cursor cursor = new CursorPaginationBuilder.Cursor(
                sorts, lastValues, last.getId());
        nextCursor = paginationBuilder.encode(cursor);
    }

    return new CursorPage<>(entries, nextCursor, hasNext);
}

@SuppressWarnings({"unchecked", "rawtypes"})
private Condition buildFilterCondition(String column, String operator, Object value) {
    Field<Object> f = (Field<Object>) field(name(column));
    return switch (operator) {
        case "eq"  -> f.eq(value);
        case "neq" -> f.ne(value);
        case "gt"  -> f.gt((Comparable) value);
        case "gte" -> f.ge((Comparable) value);
        case "lt"  -> f.lt((Comparable) value);
        case "lte" -> f.le((Comparable) value);
        case "like" -> f.likeIgnoreCase("%" + value + "%");
        default -> throw new IllegalArgumentException("Unhandled operator: " + operator);
    };
}

private List<Field<?>> buildSelectFields(ContentDefinition definition) {
    List<Field<?>> selectFields = new ArrayList<>();
    selectFields.add(field(name("id"), SQLDataType.UUID));
    selectFields.add(field(name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE));
    selectFields.add(field(name("updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE));
    selectFields.add(field(name("created_by"), SQLDataType.UUID));
    selectFields.add(field(name("updated_by"), SQLDataType.UUID));
    for (FieldDefinition fieldDef : definition.getFields()) {
        FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
        if (handler.isVirtual()) continue;
        selectFields.add(field(name("cf_" + fieldDef.getFieldKey()), handler.columnType(fieldDef.getConfig())));
    }
    return selectFields;
}
```

**Note:** `DedicatedTableStorage` needs `CursorPaginationBuilder` injected via constructor. Update constructor:

```java
private final CursorPaginationBuilder paginationBuilder;

public DedicatedTableStorage(DSLContext dsl, FieldTypeRegistry registry,
                              ObjectMapper objectMapper, CursorPaginationBuilder paginationBuilder) {
    this.dsl = dsl;
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.paginationBuilder = paginationBuilder;
}
```

Also add missing imports:
```java
import io.webcontify.content.management.model.CursorPage;
import io.webcontify.content.management.model.CursorPageRequest;
import io.webcontify.content.management.query.Filter;
import io.webcontify.content.management.query.Operator;
import org.jooq.Condition;
import org.jooq.SortField;
```

**Run test:**
```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.storage.DedicatedTableStorageQueryTest
```

**Commit:**
```
feat(content): add ContentStorage.query with cursor pagination and RHS filter support
```

---

## Task 6: Search endpoint — query by displayFormat fields

Add search method to `ContentStorage` and `DedicatedTableStorage`. Search uses `DisplayFormatResolver.extractSearchFields()` to build an ILIKE OR condition across all display format fields.

**Files to edit:**
- `apps/backend/src/main/java/io/webcontify/content/management/storage/ContentStorage.java`
- `apps/backend/src/main/java/io/webcontify/content/management/storage/DedicatedTableStorage.java`
- `openapi/content-api.yaml` — add `searchContentEntries` endpoint + `EntryReferenceListResponse` schema

### TDD Cycle

**Red — write test first:**

```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.storage.DedicatedTableStorageSearchTest
```

Test file: `apps/backend/src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageSearchTest.java`

```java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.TextFieldConfig;
import io.webcontify.content.management.model.CursorPage;
import io.webcontify.content.management.model.CursorPageRequest;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.shared.util.UuidGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DedicatedTableStorageSearchTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DedicatedTableStorage storage;

    @Test
    void search_matchesDisplayFormatFields() {
        ContentDefinition def = buildDefinition("{cf_first_name} {cf_last_name}");
        UUID userId = UuidGenerator.generate();
        storage.save("public", def, Map.of("cf_first_name", "Alice", "cf_last_name", "Smith"), userId);
        storage.save("public", def, Map.of("cf_first_name", "Bob", "cf_last_name", "Jones"), userId);

        CursorPage<EntryReference> page = storage.search("public", def, "alice", new CursorPageRequest(null, 10));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).display()).containsIgnoringCase("Alice");
    }

    @Test
    void search_emptyDisplayFormat_returnsEmpty() {
        ContentDefinition def = buildDefinition(null);
        CursorPage<EntryReference> page = storage.search("public", def, "query", new CursorPageRequest(null, 10));
        assertThat(page.items()).isEmpty();
    }

    @Test
    void search_noMatch_returnsEmpty() {
        ContentDefinition def = buildDefinition("{cf_first_name}");
        UUID userId = UuidGenerator.generate();
        storage.save("public", def, Map.of("cf_first_name", "Alice"), userId);

        CursorPage<EntryReference> page = storage.search("public", def, "zzznomatch", new CursorPageRequest(null, 10));
        assertThat(page.items()).isEmpty();
    }

    private ContentDefinition buildDefinition(String displayFormat) {
        ContentDefinition def = new ContentDefinition();
        def.setId(UuidGenerator.generate());
        def.setSlug("person");
        def.setDisplayFormat(displayFormat);

        FieldDefinition fn = new FieldDefinition();
        fn.setFieldKey("first_name");
        fn.setFieldType(FieldType.TEXT);
        fn.setConfig(new TextFieldConfig(null, null, null, null, null));

        FieldDefinition ln = new FieldDefinition();
        ln.setFieldKey("last_name");
        ln.setFieldType(FieldType.TEXT);
        ln.setConfig(new TextFieldConfig(null, null, null, null, null));

        def.setFields(List.of(fn, ln));
        return def;
    }
}
```

**Green — add to ContentStorage interface:**

```java
import io.webcontify.content.management.model.EntryReference;

// New method:
CursorPage<EntryReference> search(
    String schemaName,
    ContentDefinition definition,
    String searchQuery,
    CursorPageRequest pageRequest);
```

**Green — DedicatedTableStorage.search implementation:**

```java
@Override
@Transactional(readOnly = true)
public CursorPage<EntryReference> search(
        String schemaName,
        ContentDefinition definition,
        String searchQuery,
        CursorPageRequest pageRequest) {

    List<String> searchFields = displayFormatResolver.extractSearchFields(definition.getDisplayFormat());
    if (searchFields.isEmpty()) {
        return CursorPage.empty();
    }

    String tableName = tableName(definition);
    int fetchSize = pageRequest.effectiveSize() + 1;

    // Build OR condition: any search field ILIKE %query%
    Condition searchCondition = DSL.falseCondition();
    for (String fieldName : searchFields) {
        searchCondition = searchCondition.or(
                field(name(fieldName)).likeIgnoreCase("%" + searchQuery + "%"));
    }

    Condition whereCondition = field(name("deleted_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)
            .isNull()
            .and(searchCondition);

    // Cursor pagination on id only (no sort for search)
    if (pageRequest.cursor() != null) {
        CursorPaginationBuilder.Cursor cursor = paginationBuilder.decode(pageRequest.cursor());
        whereCondition = whereCondition.and(
                field(name("id"), SQLDataType.UUID).gt(cursor.lastId()));
    }

    // SELECT id + display format fields
    List<Field<?>> selectFields = new ArrayList<>();
    selectFields.add(field(name("id"), SQLDataType.UUID));
    for (String fieldName : searchFields) {
        selectFields.add(field(name(fieldName), SQLDataType.VARCHAR(4000)));
    }

    Result<Record> result = dsl.select(selectFields)
            .from(table(name(schemaName, tableName)))
            .where(whereCondition)
            .orderBy(field(name("id"), SQLDataType.UUID).asc())
            .limit(fetchSize)
            .fetch();

    boolean hasNext = result.size() > pageRequest.effectiveSize();
    List<Record> pageRecords = hasNext
            ? result.subList(0, pageRequest.effectiveSize())
            : new ArrayList<>(result);

    List<EntryReference> refs = pageRecords.stream()
            .map(r -> {
                UUID id = r.get(field(name("id"), SQLDataType.UUID));
                Map<String, Object> valuesForDisplay = new LinkedHashMap<>();
                valuesForDisplay.put("id", id);
                for (String fieldName : searchFields) {
                    valuesForDisplay.put(fieldName, r.get(field(name(fieldName), SQLDataType.VARCHAR(4000))));
                }
                String display = displayFormatResolver.resolve(definition, valuesForDisplay);
                return new EntryReference(id, display);
            })
            .toList();

    String nextCursor = null;
    if (hasNext && !refs.isEmpty()) {
        UUID lastId = refs.get(refs.size() - 1).id();
        CursorPaginationBuilder.Cursor cursor = new CursorPaginationBuilder.Cursor(
                List.of(), List.of(), lastId);
        nextCursor = paginationBuilder.encode(cursor);
    }

    return new CursorPage<>(refs, nextCursor, hasNext);
}
```

**Note:** `DedicatedTableStorage` needs `DisplayFormatResolver` injected. Update constructor:

```java
private final DisplayFormatResolver displayFormatResolver;

public DedicatedTableStorage(DSLContext dsl, FieldTypeRegistry registry,
                              ObjectMapper objectMapper, CursorPaginationBuilder paginationBuilder,
                              DisplayFormatResolver displayFormatResolver) {
    this.dsl = dsl;
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.paginationBuilder = paginationBuilder;
    this.displayFormatResolver = displayFormatResolver;
}
```

**OpenAPI — add search endpoint and schemas to `openapi/content-api.yaml`:**

Add path after the entries list endpoint (before `entries/{entryId}`):

```yaml
  /api/v1/content/{appSlug}/definitions/{defSlug}/entries/search:
    get:
      operationId: searchContentEntries
      summary: Search content entries by display format fields (for reference autocomplete)
      tags:
        - ContentEntry
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefSlugPath'
        - name: q
          in: query
          required: true
          schema:
            type: string
            minLength: 1
            maxLength: 200
        - name: cursor
          in: query
          required: false
          schema:
            type: string
        - name: size
          in: query
          required: false
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
      responses:
        '200':
          description: Entry reference search results
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EntryReferencePageResponse'
        '404':
          description: Content definition not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
```

Add schemas to `components/schemas`:

```yaml
    EntryReferenceResponse:
      type: object
      required:
        - id
        - display
      properties:
        id:
          type: string
          format: uuid
        display:
          type: string

    EntryReferencePageResponse:
      type: object
      required:
        - items
        - hasNext
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/EntryReferenceResponse'
        nextCursor:
          type: string
        hasNext:
          type: boolean
```

**Run test + codegen:**
```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.storage.DedicatedTableStorageSearchTest
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
```

**Commit:**
```
feat(content): add ContentStorage.search for reference autocomplete with cursor pagination
```

---

## Task 7: Entry history paginated endpoint

Replace the simple `getHistory(limit)` signature with cursor-based pagination. The history table orders by `changed_at DESC, id ASC` (newest first, id tiebreaker within same timestamp).

**Files to edit:**
- `apps/backend/src/main/java/io/webcontify/content/management/storage/ContentStorage.java`
- `apps/backend/src/main/java/io/webcontify/content/management/storage/DedicatedTableStorage.java`
- `openapi/content-api.yaml` — add history endpoint + `EntryHistoryPageResponse` schema

### TDD Cycle

**Red — write test first:**

```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.storage.DedicatedTableStorageHistoryPaginationTest
```

Test file: `apps/backend/src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageHistoryPaginationTest.java`

```java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.TextFieldConfig;
import io.webcontify.content.management.model.CursorPage;
import io.webcontify.content.management.model.CursorPageRequest;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.shared.util.UuidGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DedicatedTableStorageHistoryPaginationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DedicatedTableStorage storage;

    @Test
    void getHistoryPaginated_firstPage_returnsPageWithCursor() {
        ContentDefinition def = buildDefinition();
        UUID userId = UuidGenerator.generate();
        UUID entryId = storage.save("public", def, Map.of("cf_title", "v1"), userId).getId();
        storage.update("public", def, entryId, Map.of("cf_title", "v2"), userId);
        storage.update("public", def, entryId, Map.of("cf_title", "v3"), userId);

        CursorPage<EntryHistoryRecord> page = storage.getHistoryPaginated(
                "public", def, entryId, new CursorPageRequest(null, 2));

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    void getHistoryPaginated_secondPage_returnsRemainingRecords() {
        ContentDefinition def = buildDefinition();
        UUID userId = UuidGenerator.generate();
        UUID entryId = storage.save("public", def, Map.of("cf_title", "v1"), userId).getId();
        storage.update("public", def, entryId, Map.of("cf_title", "v2"), userId);
        storage.update("public", def, entryId, Map.of("cf_title", "v3"), userId);

        CursorPage<EntryHistoryRecord> page1 = storage.getHistoryPaginated(
                "public", def, entryId, new CursorPageRequest(null, 2));
        CursorPage<EntryHistoryRecord> page2 = storage.getHistoryPaginated(
                "public", def, entryId, new CursorPageRequest(page1.nextCursor(), 2));

        assertThat(page2.items()).hasSize(1);
        assertThat(page2.hasNext()).isFalse();

        // No overlap
        var ids1 = page1.items().stream().map(EntryHistoryRecord::id).toList();
        var ids2 = page2.items().stream().map(EntryHistoryRecord::id).toList();
        assertThat(ids1).doesNotContainAnyElementsOf(ids2);
    }

    private ContentDefinition buildDefinition() {
        ContentDefinition def = new ContentDefinition();
        def.setId(UuidGenerator.generate());
        def.setSlug("doc");
        FieldDefinition f = new FieldDefinition();
        f.setFieldKey("title");
        f.setFieldType(FieldType.TEXT);
        f.setConfig(new TextFieldConfig(null, null, null, null, null));
        def.setFields(List.of(f));
        return def;
    }
}
```

**Green — update ContentStorage interface:**

Keep `getHistory(schemaName, definition, entryId, limit)` for backward compatibility (CASCADE still uses it).
Add new method:

```java
CursorPage<EntryHistoryRecord> getHistoryPaginated(
    String schemaName,
    ContentDefinition definition,
    UUID entryId,
    CursorPageRequest pageRequest);
```

**Green — DedicatedTableStorage.getHistoryPaginated:**

```java
@Override
@Transactional(readOnly = true)
public CursorPage<EntryHistoryRecord> getHistoryPaginated(
        String schemaName,
        ContentDefinition definition,
        UUID entryId,
        CursorPageRequest pageRequest) {

    String tableName = tableName(definition);
    String historyName = tableName + "_history";
    int fetchSize = pageRequest.effectiveSize() + 1;

    List<Field<?>> historyFields = List.of(
            field(name("id"), SQLDataType.UUID),
            field(name("entry_id"), SQLDataType.UUID),
            field(name("action"), SQLDataType.VARCHAR),
            field(name("changed_fields"), SQLDataType.JSONB),
            field(name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE),
            field(name("changed_by"), SQLDataType.UUID));

    Condition whereCondition = field(name("entry_id"), SQLDataType.UUID).eq(entryId);

    // Cursor: encode lastChangedAt (DESC) + lastId (ASC tiebreaker)
    if (pageRequest.cursor() != null) {
        CursorPaginationBuilder.Cursor cursor = paginationBuilder.decode(pageRequest.cursor());
        // History cursor encodes: lastValues[0] = changedAt string, lastId = history row id
        if (cursor.lastValues() != null && !cursor.lastValues().isEmpty()) {
            OffsetDateTime lastChangedAt = OffsetDateTime.parse(cursor.lastValues().get(0).toString());
            UUID lastHistoryId = cursor.lastId();
            // Keyset for DESC changed_at: (changed_at < lastChangedAt) OR (changed_at = lastChangedAt AND id > lastId)
            whereCondition = whereCondition.and(
                    field(name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE).lt(lastChangedAt)
                    .or(field(name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE).eq(lastChangedAt)
                            .and(field(name("id"), SQLDataType.UUID).gt(lastHistoryId)))
            );
        }
    }

    Result<Record> result = dsl.select(historyFields)
            .from(table(name(schemaName, historyName)))
            .where(whereCondition)
            .orderBy(
                    field(name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE).desc(),
                    field(name("id"), SQLDataType.UUID).asc()
            )
            .limit(fetchSize)
            .fetch();

    boolean hasNext = result.size() > pageRequest.effectiveSize();
    List<Record> pageRecords = hasNext
            ? result.subList(0, pageRequest.effectiveSize())
            : new ArrayList<>(result);

    List<EntryHistoryRecord> records = pageRecords.stream()
            .map(r -> {
                JSONB jsonb = r.get(field(name("changed_fields"), SQLDataType.JSONB));
                return new EntryHistoryRecord(
                        r.get(field(name("id"), SQLDataType.UUID)),
                        r.get(field(name("entry_id"), SQLDataType.UUID)),
                        r.get(field(name("action"), SQLDataType.VARCHAR)),
                        deserializeHistoryFields(jsonb),
                        r.get(field(name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)),
                        r.get(field(name("changed_by"), SQLDataType.UUID))
                );
            })
            .toList();

    String nextCursor = null;
    if (hasNext && !records.isEmpty()) {
        EntryHistoryRecord last = records.get(records.size() - 1);
        CursorPaginationBuilder.Cursor cursor = new CursorPaginationBuilder.Cursor(
                List.of(),
                List.of(last.changedAt().toString()),
                last.id()
        );
        nextCursor = paginationBuilder.encode(cursor);
    }

    return new CursorPage<>(records, nextCursor, hasNext);
}
```

**OpenAPI — add history endpoint and schemas:**

Add path after `entries/{entryId}` delete:

```yaml
  /api/v1/content/{appSlug}/definitions/{defSlug}/entries/{entryId}/history:
    get:
      operationId: getContentEntryHistory
      summary: Get paginated change history for a content entry
      tags:
        - ContentEntry
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefSlugPath'
        - $ref: '#/components/parameters/EntryIdPath'
        - name: cursor
          in: query
          required: false
          schema:
            type: string
        - name: size
          in: query
          required: false
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
      responses:
        '200':
          description: Entry history page
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EntryHistoryPageResponse'
        '404':
          description: Content entry not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
```

Add schemas:

```yaml
    EntryHistoryEventResponse:
      type: object
      required:
        - id
        - entryId
        - action
        - changedAt
      properties:
        id:
          type: string
          format: uuid
        entryId:
          type: string
          format: uuid
        action:
          type: string
          enum:
            - CREATED
            - UPDATED
            - DELETED
        changedFields:
          type: object
          additionalProperties:
            type: object
        changedAt:
          type: string
          format: date-time
        changedBy:
          type: string
          format: uuid

    EntryHistoryPageResponse:
      type: object
      required:
        - items
        - hasNext
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/EntryHistoryEventResponse'
        nextCursor:
          type: string
        hasNext:
          type: boolean
```

**Run test + codegen:**
```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.storage.DedicatedTableStorageHistoryPaginationTest
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
```

**Commit:**
```
feat(content): add paginated history endpoint with cursor pagination
```

---

## Task 8: Update OpenAPI list endpoint + Controller + Service

Wire everything together: update the list endpoint to accept cursor/size/sort/filter params, add the two new controller methods for search and history, update `ContentEntryService` to accept the new signatures.

**Files to edit:**
- `openapi/content-api.yaml` — update `listContentEntries` params; add `ContentEntryPageResponse` schema
- `apps/backend/src/main/java/io/webcontify/content/management/service/ContentEntryService.java`
- `apps/backend/src/main/java/io/webcontify/content/management/api/ContentEntryController.java`
- `apps/backend/src/main/java/io/webcontify/content/management/mapper/ContentEntryMapper.java` — add history/reference mapping

### TDD Cycle

**Red — write controller test first:**

```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.api.ContentEntryControllerQueryTest
```

Test file: `apps/backend/src/test/java/io/webcontify/content/management/api/ContentEntryControllerQueryTest.java`

```java
package io.webcontify.content.management.api;

import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.CursorPage;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.service.ContentEntryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ContentEntryControllerQueryTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ContentEntryService entryService;

    @Test
    void listEntries_cursorParams_callsServiceWithPagination() throws Exception {
        ContentEntry entry = new ContentEntry();
        entry.setId(UUID.randomUUID());
        entry.setDefinitionId(UUID.randomUUID());
        entry.setValues(Map.of("cf_title", "Test"));
        CursorPage<ContentEntry> page = new CursorPage<>(List.of(entry), null, false);

        when(entryService.query(eq("my-app"), eq("articles"), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/content/my-app/definitions/articles/entries")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void searchEntries_returnsReferenceList() throws Exception {
        EntryReference ref = new EntryReference(UUID.randomUUID(), "Alice Smith");
        CursorPage<EntryReference> page = new CursorPage<>(List.of(ref), null, false);

        when(entryService.search(eq("my-app"), eq("people"), eq("alice"), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/content/my-app/definitions/people/entries/search")
                        .param("q", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].display").value("Alice Smith"));
    }

    @Test
    void historyEndpoint_returnsPaginatedHistory() throws Exception {
        UUID entryId = UUID.randomUUID();
        CursorPage<EntryHistoryRecord> page = new CursorPage<>(List.of(), null, false);

        when(entryService.getHistoryPaginated(eq("my-app"), eq("articles"), eq(entryId), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/content/my-app/definitions/articles/entries/" + entryId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }
}
```

**Green — update OpenAPI list endpoint:**

Replace the `get` operation under `/api/v1/content/{appSlug}/definitions/{defSlug}/entries`:

```yaml
    get:
      operationId: listContentEntries
      summary: List content entries with cursor pagination, filtering and sorting
      tags:
        - ContentEntry
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefSlugPath'
        - name: cursor
          in: query
          required: false
          schema:
            type: string
          description: Opaque cursor token from a previous response
        - name: size
          in: query
          required: false
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
        - name: sort
          in: query
          required: false
          schema:
            type: array
            items:
              type: string
            maxItems: 3
          description: |
            Sort fields as "fieldKey:asc" or "fieldKey:desc".
            Example: sort=cf_name:asc&sort=cf_created_at:desc
          style: form
          explode: true
      responses:
        '200':
          description: Content entry page
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentEntryPageResponse'
        '400':
          description: Invalid filter or sort parameters
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ValidationProblemDetail'
        '404':
          description: Content definition not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
```

Add `ContentEntryPageResponse` schema:

```yaml
    ContentEntryPageResponse:
      type: object
      required:
        - items
        - hasNext
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/ContentEntryResponse'
        nextCursor:
          type: string
        hasNext:
          type: boolean
```

**Green — update ContentEntryService:**

Add three new methods to `ContentEntryService`:

```java
@Transactional(readOnly = true)
public CursorPage<ContentEntry> query(
        String appSlug, String defSlug,
        List<Filter> filters,
        List<CursorPaginationBuilder.SortCriterion> sorts,
        CursorPageRequest pageRequest) {
    var tenantInfo = TenantContext.getTenantInformation();
    String schemaName = schemaName(tenantInfo.slug());
    ContentDefinition definition = resolveDefinition(appSlug, defSlug);

    CursorPage<ContentEntry> page = storage.query(schemaName, definition, filters, sorts, pageRequest);
    List<ContentEntry> resolved = page.items().stream()
            .map(e -> withResolvedReferences(schemaName, definition, e))
            .toList();
    return new CursorPage<>(resolved, page.nextCursor(), page.hasNext());
}

@Transactional(readOnly = true)
public CursorPage<EntryReference> search(
        String appSlug, String defSlug,
        String searchQuery, CursorPageRequest pageRequest) {
    var tenantInfo = TenantContext.getTenantInformation();
    String schemaName = schemaName(tenantInfo.slug());
    ContentDefinition definition = resolveDefinition(appSlug, defSlug);
    return storage.search(schemaName, definition, searchQuery, pageRequest);
}

@Transactional(readOnly = true)
public CursorPage<EntryHistoryRecord> getHistoryPaginated(
        String appSlug, String defSlug,
        UUID entryId, CursorPageRequest pageRequest) {
    var tenantInfo = TenantContext.getTenantInformation();
    String schemaName = schemaName(tenantInfo.slug());
    ContentDefinition definition = resolveDefinition(appSlug, defSlug);

    // Verify entry exists
    storage.findById(schemaName, definition, entryId)
            .orElseThrow(() -> new NotFoundException("content.entry.not_found"));

    return storage.getHistoryPaginated(schemaName, definition, entryId, pageRequest);
}
```

Add required imports to `ContentEntryService`:
```java
import io.webcontify.content.management.model.CursorPage;
import io.webcontify.content.management.model.CursorPageRequest;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.content.management.query.Filter;
import io.webcontify.content.management.storage.CursorPaginationBuilder;
```

**Green — update ContentEntryController:**

The controller parses `sort` and filter params from the request. Since OpenAPI generates the method signature from the spec, `listContentEntries` will receive `cursor`, `size`, and `sort` params. Filter params (`cf_*`) are not in the spec — they are parsed via `HttpServletRequest`.

Update imports and add `RhsOperatorParser` + `HttpServletRequest` dependency:

```java
// Updated listContentEntries
@Override
public ResponseEntity<ContentEntryPageResponse> listContentEntries(
        String appSlug, String defSlug,
        String cursor, Integer size, List<String> sort,
        HttpServletRequest httpRequest) {

    // Parse sort: ["cf_name:asc", "cf_score:desc"]
    List<CursorPaginationBuilder.SortCriterion> sorts = parseSortParams(sort);
    if (sorts.size() > 3) {
        throw new ValidationException("VALIDATION_ERROR",
                List.of(new FieldError("sort", "error.sort.too_many_fields")));
    }

    // Parse RHS filter params from raw query string
    Map<String, String> allParams = new HashMap<>();
    httpRequest.getParameterMap().forEach((k, v) -> {
        if (k.startsWith("cf_") && v.length > 0) allParams.put(k, v[0]);
    });

    ContentDefinition definition = resolveDefinitionForFilter(appSlug, defSlug);
    List<Filter> filters = rhsOperatorParser.parse(allParams, definition);

    CursorPageRequest pageRequest = new CursorPageRequest(cursor, size != null ? size : 20);
    CursorPage<ContentEntry> page = entryService.query(appSlug, defSlug, filters, sorts, pageRequest);

    ContentEntryPageResponse response = new ContentEntryPageResponse()
            .items(entryMapper.toResponseList(page.items()))
            .nextCursor(page.nextCursor())
            .hasNext(page.hasNext());
    return ResponseEntity.ok(response);
}

@Override
public ResponseEntity<EntryReferencePageResponse> searchContentEntries(
        String appSlug, String defSlug, String q, String cursor, Integer size) {
    CursorPageRequest pageRequest = new CursorPageRequest(cursor, size != null ? size : 20);
    CursorPage<EntryReference> page = entryService.search(appSlug, defSlug, q, pageRequest);

    EntryReferencePageResponse response = new EntryReferencePageResponse()
            .items(entryMapper.toReferenceResponseList(page.items()))
            .nextCursor(page.nextCursor())
            .hasNext(page.hasNext());
    return ResponseEntity.ok(response);
}

@Override
public ResponseEntity<EntryHistoryPageResponse> getContentEntryHistory(
        String appSlug, String defSlug, UUID entryId, String cursor, Integer size) {
    CursorPageRequest pageRequest = new CursorPageRequest(cursor, size != null ? size : 20);
    CursorPage<EntryHistoryRecord> page = entryService.getHistoryPaginated(appSlug, defSlug, entryId, pageRequest);

    EntryHistoryPageResponse response = new EntryHistoryPageResponse()
            .items(entryMapper.toHistoryResponseList(page.items()))
            .nextCursor(page.nextCursor())
            .hasNext(page.hasNext());
    return ResponseEntity.ok(response);
}

private List<CursorPaginationBuilder.SortCriterion> parseSortParams(List<String> sort) {
    if (sort == null || sort.isEmpty()) return List.of();
    return sort.stream().map(s -> {
        int colonIdx = s.lastIndexOf(':');
        if (colonIdx < 0) {
            throw new ValidationException("VALIDATION_ERROR",
                    List.of(new FieldError("sort", "error.sort.invalid_format")));
        }
        String column = s.substring(0, colonIdx);
        String direction = s.substring(colonIdx + 1).toLowerCase();
        boolean ascending = switch (direction) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw new ValidationException("VALIDATION_ERROR",
                    List.of(new FieldError("sort", "error.sort.invalid_direction")));
        };
        return new CursorPaginationBuilder.SortCriterion(column, ascending);
    }).toList();
}
```

**Note:** `ContentEntryController` needs `RhsOperatorParser`, `ContentDefinitionRepository`, `AppRepository`, and `ContentDefinitionMapper` injected to resolve the definition for filter parsing. To avoid coupling, inject a `ContentEntryQueryHelper` or inline via a `resolveDefinitionForFilter` method that delegates to the existing service. Simplest approach: inject `ContentEntryService` and expose a `resolveDefinition` package-private method, or inject `AppRepository` + `ContentDefinitionRepository` + `ContentDefinitionMapper` directly into controller for the filter parse step.

The cleanest approach is to add a helper method on `ContentEntryService`:

```java
// In ContentEntryService — expose for controller use only
ContentDefinition resolveDefinitionPublic(String appSlug, String defSlug) {
    return resolveDefinition(appSlug, defSlug);
}
```

**Green — update ContentEntryMapper:**

Add to `ContentEntryMapper.java`:

```java
List<EntryReferenceResponse> toReferenceResponseList(List<EntryReference> refs);

EntryReferenceResponse toReferenceResponse(EntryReference ref);

List<EntryHistoryEventResponse> toHistoryResponseList(List<EntryHistoryRecord> records);

EntryHistoryEventResponse toHistoryResponse(EntryHistoryRecord record);
```

MapStruct will generate the implementations automatically given matching field names.

**Run full test suite + codegen:**
```
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
  --tests io.webcontify.content.management.api.ContentEntryControllerQueryTest
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

**Commit:**
```
feat(content): wire cursor pagination, filtering and sorting into controller and service
```

---

## Existing code compatibility notes

- `findAll(schemaName, definition, limit)` in `ContentStorage` stays for the CASCADE delete use case in `ContentEntryService.enforceOnDelete`. Do not remove it.
- `getHistory(schemaName, definition, entryId, limit)` stays as-is; `getHistoryPaginated` is additive.
- The existing `buildSelectFields` logic is duplicated between `findAll` and `query`; extract to the private `buildSelectFields(ContentDefinition)` helper and reuse in both.
- `DedicatedTableStorage` grows a larger constructor; Spring DI handles this automatically since all dependencies are `@Component`.
- `CursorPaginationBuilder.Cursor` uses Jackson deserialization, so the record fields must match JSON keys exactly. Ensure `ObjectMapper` has `JavaTimeModule` registered (already present in the Spring Boot auto-configuration).

## Integration test note

Integration tests in Tasks 5, 6, 7 require the dynamic content table to already exist in the test database. In a real test, call `DynamicTableManager.createTable(schemaName, definition)` in the `@BeforeEach` block (or use a SQL setup script). The integration tests shown are illustrative — the actual test setup follows the pattern established in the existing `ContentDdlListenerIntegrationTest`.

## File checklist

| File | Action |
|------|--------|
| `content/management/model/CursorPageRequest.java` | Create |
| `content/management/model/CursorPage.java` | Create |
| `content/management/storage/CursorPaginationBuilder.java` | Create |
| `content/management/query/Operator.java` | Create |
| `content/management/query/Filter.java` | Create |
| `content/management/query/RhsOperatorParser.java` | Create |
| `content/fieldtype/FieldQueryBuilder.java` | Edit (return type) |
| `content/fieldtype/handler/TextFieldTypeHandler.java` | Edit (add buildCondition) |
| `content/fieldtype/handler/IntegerFieldTypeHandler.java` | Edit |
| `content/fieldtype/handler/DecimalFieldTypeHandler.java` | Edit |
| `content/fieldtype/handler/BooleanFieldTypeHandler.java` | Edit |
| `content/fieldtype/handler/DateFieldTypeHandler.java` | Edit |
| `content/fieldtype/handler/EnumFieldTypeHandler.java` | Edit |
| `content/fieldtype/handler/ReferenceFieldTypeHandler.java` | Edit |
| `content/management/storage/ContentStorage.java` | Edit (add query, search, getHistoryPaginated) |
| `content/management/storage/DedicatedTableStorage.java` | Edit (implement new methods) |
| `content/management/service/ContentEntryService.java` | Edit (add query, search, getHistoryPaginated) |
| `content/management/api/ContentEntryController.java` | Edit (update list, add search + history) |
| `content/management/mapper/ContentEntryMapper.java` | Edit (add reference + history mapping) |
| `openapi/content-api.yaml` | Edit (update list, add search + history endpoints + schemas) |
