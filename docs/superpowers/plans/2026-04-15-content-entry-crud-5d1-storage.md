# Content Entry CRUD — Part 1: Storage Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Build the storage foundation for content entries: domain models, storage interface, jOOQ-based DedicatedTableStorage, FieldValueHandler casting/validation, and DisplayFormatResolver. Part 2 (service + API) comes separately.

**Architecture:** `ContentStorage` interface with CRUD + history methods. `DedicatedTableStorage` uses jOOQ `DSL.name`/`DSL.field` for dynamic identifiers. Each write method opens a Spring transaction that inserts into content table + history table atomically. Value handling uses `FieldTypeRegistry` to dispatch cast/validate to per-type handlers.

**Tech Stack:** Java 21, Spring Boot 4, jOOQ 3.x, Jackson, PostgreSQL 17

---

## Prerequisites

- Phase 5a done: `FieldTypeRegistry` + 8 handlers in `io.webcontify.content.fieldtype.handler`
- Phase 5b done: tenant schemas, `TenantInformation.slug`
- Phase 5c done: `DynamicTableManager` creates `tenant_{slug}.cd_{def_slug}` tables with system columns + `cf_{field_key}` columns + companion `_history` table

## Package layout

```
io.webcontify.content.management.model         — domain POJOs (ContentEntry, FieldValue, etc.)
io.webcontify.content.management.storage       — ContentStorage interface + DedicatedTableStorage
io.webcontify.content.fieldtype                — FieldValueHandler (add cast + validateValue)
io.webcontify.content.fieldtype.handler        — 8 concrete handlers (override cast/validateValue)
```

Tests mirror the same packages under `src/test/java/`.

---

## Task 1 — ContentEntry domain model + FieldValue record

**Files to create:**
- `apps/backend/src/main/java/io/webcontify/content/management/model/ContentEntry.java`
- `apps/backend/src/main/java/io/webcontify/content/management/model/FieldValue.java`

**Test file:**
- `apps/backend/src/test/java/io/webcontify/content/management/model/ContentEntryTest.java`

### Red

```java
// ContentEntryTest.java
package io.webcontify.content.management.model;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ContentEntryTest {

    @Test
    void contentEntry_storesAllFields() {
        UUID id = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> values = Map.of("cf_title", "Hello");

        ContentEntry entry = new ContentEntry();
        entry.setId(id);
        entry.setDefinitionId(defId);
        entry.setValues(values);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        entry.setCreatedBy(userId);
        entry.setUpdatedBy(userId);

        assertThat(entry.getId()).isEqualTo(id);
        assertThat(entry.getDefinitionId()).isEqualTo(defId);
        assertThat(entry.getValues()).isEqualTo(values);
        assertThat(entry.getCreatedAt()).isEqualTo(now);
        assertThat(entry.getUpdatedAt()).isEqualTo(now);
        assertThat(entry.getCreatedBy()).isEqualTo(userId);
        assertThat(entry.getUpdatedBy()).isEqualTo(userId);
    }

    @Test
    void fieldValue_record_holdsKeyTypeAndValue() {
        FieldValue fv = new FieldValue("title", io.webcontify.content.definition.model.FieldType.TEXT, "Hello");
        assertThat(fv.fieldKey()).isEqualTo("title");
        assertThat(fv.value()).isEqualTo("Hello");
    }
}
```

Run test — fails (classes do not exist).

### Green

**`ContentEntry.java`:**
```java
package io.webcontify.content.management.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public class ContentEntry {
    private UUID id;
    private UUID definitionId;
    private Map<String, Object> values;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;

    public ContentEntry() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }
    public Map<String, Object> getValues() { return values; }
    public void setValues(Map<String, Object> values) { this.values = values; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
```

**`FieldValue.java`:**
```java
package io.webcontify.content.management.model;

import io.webcontify.content.definition.model.FieldType;

public record FieldValue(String fieldKey, FieldType fieldType, Object value) {}
```

Run test — passes.

### Commit
```
feat(content): add ContentEntry domain model and FieldValue record
```

---

## Task 2 — EntryReference + EntryHistoryRecord domain models

**Files to create:**
- `apps/backend/src/main/java/io/webcontify/content/management/model/EntryReference.java`
- `apps/backend/src/main/java/io/webcontify/content/management/model/EntryHistoryRecord.java`

**Test file:**
- `apps/backend/src/test/java/io/webcontify/content/management/model/EntryHistoryRecordTest.java`

### Red

```java
// EntryHistoryRecordTest.java
package io.webcontify.content.management.model;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class EntryHistoryRecordTest {

    @Test
    void entryHistoryRecord_storesAllFields() {
        UUID id = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, EntryHistoryRecord.FieldChange> diff = Map.of(
            "cf_title", new EntryHistoryRecord.FieldChange(null, "Hello")
        );

        EntryHistoryRecord record = new EntryHistoryRecord(id, entryId, "CREATED", diff, now, changedBy);

        assertThat(record.id()).isEqualTo(id);
        assertThat(record.entryId()).isEqualTo(entryId);
        assertThat(record.action()).isEqualTo("CREATED");
        assertThat(record.changedFields()).isEqualTo(diff);
        assertThat(record.changedAt()).isEqualTo(now);
        assertThat(record.changedBy()).isEqualTo(changedBy);
    }

    @Test
    void fieldChange_holdsOldAndNewValue() {
        EntryHistoryRecord.FieldChange change = new EntryHistoryRecord.FieldChange("old", "new");
        assertThat(change.oldValue()).isEqualTo("old");
        assertThat(change.newValue()).isEqualTo("new");
    }

    @Test
    void entryReference_holdsIdAndDisplay() {
        UUID id = UUID.randomUUID();
        EntryReference ref = new EntryReference(id, "John Doe");
        assertThat(ref.id()).isEqualTo(id);
        assertThat(ref.display()).isEqualTo("John Doe");
    }
}
```

Run test — fails.

### Green

**`EntryReference.java`:**
```java
package io.webcontify.content.management.model;

import java.util.UUID;

public record EntryReference(UUID id, String display) {}
```

**`EntryHistoryRecord.java`:**
```java
package io.webcontify.content.management.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record EntryHistoryRecord(
    UUID id,
    UUID entryId,
    String action,
    Map<String, FieldChange> changedFields,
    OffsetDateTime changedAt,
    UUID changedBy
) {
    public record FieldChange(Object oldValue, Object newValue) {}
}
```

Run test — passes.

### Commit
```
feat(content): add EntryReference and EntryHistoryRecord domain models
```

---

## Task 3 — Extend FieldValueHandler with cast() + validateValue(); override in 7 concrete handlers

**Files to modify:**
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/FieldValueHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/TextFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/IntegerFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/DecimalFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/BooleanFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/DateFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/EnumFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/ReferenceFieldTypeHandler.java`
- `apps/backend/src/main/java/io/webcontify/content/fieldtype/handler/MirrorFieldTypeHandler.java` (validateValue throws, cast returns null)

**Test files to modify:**
- `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/TextFieldTypeHandlerTest.java` (add cast + validateValue tests)
- `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/IntegerFieldTypeHandlerTest.java`
- `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/DecimalFieldTypeHandlerTest.java`
- `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/BooleanFieldTypeHandlerTest.java`
- `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/DateFieldTypeHandlerTest.java`
- `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/EnumFieldTypeHandlerTest.java`
- `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/ReferenceFieldTypeHandlerTest.java`
- `apps/backend/src/test/java/io/webcontify/content/fieldtype/handler/MirrorFieldTypeHandlerTest.java`

### Red — add failing tests to each handler test class

**TextFieldTypeHandlerTest additions:**
```java
// cast
@Test
void cast_stringValue_returnsString() {
    assertThat(handler.cast("hello", null)).isEqualTo("hello");
}

@Test
void cast_integerValue_returnsStringRepresentation() {
    assertThat(handler.cast(42, null)).isEqualTo("42");
}

@Test
void cast_nullValue_returnsNull() {
    assertThat(handler.cast(null, null)).isNull();
}

// validateValue
@Test
void validateValue_withinLimits_passes() {
    var config = new TextFieldConfig(2, 10, null, null);
    assertThatNoException().isThrownBy(() -> handler.validateValue("hello", config));
}

@Test
void validateValue_belowMinLength_throws() {
    var config = new TextFieldConfig(5, null, null, null);
    assertThatThrownBy(() -> handler.validateValue("hi", config))
        .isInstanceOf(ValidationException.class);
}

@Test
void validateValue_exceedsMaxLength_throws() {
    var config = new TextFieldConfig(null, 5, null, null);
    assertThatThrownBy(() -> handler.validateValue("toolongvalue", config))
        .isInstanceOf(ValidationException.class);
}

@Test
void validateValue_patternMismatch_throws() {
    var config = new TextFieldConfig(null, null, "^[0-9]+$", null);
    assertThatThrownBy(() -> handler.validateValue("abc", config))
        .isInstanceOf(ValidationException.class);
}

@Test
void validateValue_nullConfig_passes() {
    assertThatNoException().isThrownBy(() -> handler.validateValue("hello", null));
}
```

**IntegerFieldTypeHandlerTest additions:**
```java
@Test
void cast_longValue_returnsLong() {
    assertThat(handler.cast(42L, null)).isEqualTo(42L);
}

@Test
void cast_integerValue_returnsLong() {
    assertThat(handler.cast(42, null)).isEqualTo(42L);
}

@Test
void cast_stringValue_parsesLong() {
    assertThat(handler.cast("99", null)).isEqualTo(99L);
}

@Test
void cast_invalidString_throws() {
    assertThatThrownBy(() -> handler.cast("abc", null))
        .isInstanceOf(ValidationException.class);
}

@Test
void cast_nullValue_returnsNull() {
    assertThat(handler.cast(null, null)).isNull();
}

@Test
void validateValue_withinRange_passes() {
    var config = new IntegerFieldConfig(0L, 100L, null);
    assertThatNoException().isThrownBy(() -> handler.validateValue(50L, config));
}

@Test
void validateValue_belowMin_throws() {
    var config = new IntegerFieldConfig(10L, null, null);
    assertThatThrownBy(() -> handler.validateValue(5L, config))
        .isInstanceOf(ValidationException.class);
}

@Test
void validateValue_exceedsMax_throws() {
    var config = new IntegerFieldConfig(null, 100L, null);
    assertThatThrownBy(() -> handler.validateValue(200L, config))
        .isInstanceOf(ValidationException.class);
}
```

**DecimalFieldTypeHandlerTest additions:**
```java
@Test
void cast_bigDecimalValue_returnsBigDecimal() {
    BigDecimal val = new BigDecimal("3.14");
    assertThat(handler.cast(val, null)).isEqualTo(val);
}

@Test
void cast_stringValue_parsesBigDecimal() {
    assertThat(handler.cast("3.14", null)).isEqualTo(new BigDecimal("3.14"));
}

@Test
void cast_intValue_returnsBigDecimal() {
    assertThat(handler.cast(5, null)).isEqualTo(new BigDecimal("5"));
}

@Test
void cast_invalidString_throws() {
    assertThatThrownBy(() -> handler.cast("abc", null))
        .isInstanceOf(ValidationException.class);
}

@Test
void cast_nullValue_returnsNull() {
    assertThat(handler.cast(null, null)).isNull();
}

@Test
void validateValue_withinRange_passes() {
    var config = new DecimalFieldConfig(BigDecimal.ZERO, new BigDecimal("100"), null, null, null);
    assertThatNoException().isThrownBy(() -> handler.validateValue(new BigDecimal("50"), config));
}

@Test
void validateValue_belowMin_throws() {
    var config = new DecimalFieldConfig(new BigDecimal("10"), null, null, null, null);
    assertThatThrownBy(() -> handler.validateValue(new BigDecimal("5"), config))
        .isInstanceOf(ValidationException.class);
}
```

**BooleanFieldTypeHandlerTest additions:**
```java
@Test
void cast_booleanValue_returnsBoolean() {
    assertThat(handler.cast(true, null)).isEqualTo(true);
}

@Test
void cast_stringTrue_returnsTrue() {
    assertThat(handler.cast("true", null)).isEqualTo(true);
}

@Test
void cast_stringFalse_returnsFalse() {
    assertThat(handler.cast("false", null)).isEqualTo(false);
}

@Test
void cast_nullValue_returnsNull() {
    assertThat(handler.cast(null, null)).isNull();
}

@Test
void validateValue_noOpForBoolean_passes() {
    assertThatNoException().isThrownBy(() -> handler.validateValue(true, null));
}
```

**DateFieldTypeHandlerTest additions:**
```java
@Test
void cast_offsetDateTimeValue_returnsAsIs() {
    OffsetDateTime now = OffsetDateTime.now();
    assertThat(handler.cast(now, null)).isEqualTo(now);
}

@Test
void cast_isoStringValue_parsesOffsetDateTime() {
    String iso = "2026-04-15T10:00:00Z";
    Object result = handler.cast(iso, null);
    assertThat(result).isInstanceOf(OffsetDateTime.class);
}

@Test
void cast_invalidString_throws() {
    assertThatThrownBy(() -> handler.cast("not-a-date", null))
        .isInstanceOf(ValidationException.class);
}

@Test
void cast_nullValue_returnsNull() {
    assertThat(handler.cast(null, null)).isNull();
}

@Test
void validateValue_noOpForDate_passes() {
    assertThatNoException().isThrownBy(
        () -> handler.validateValue(OffsetDateTime.now(), null));
}
```

**EnumFieldTypeHandlerTest additions:**
```java
@Test
void cast_stringValue_returnsString() {
    assertThat(handler.cast("active", null)).isEqualTo("active");
}

@Test
void cast_nullValue_returnsNull() {
    assertThat(handler.cast(null, null)).isNull();
}

@Test
void validateValue_valueInAllowedValues_passes() {
    var config = new EnumFieldConfig(List.of("active", "inactive"), null);
    assertThatNoException().isThrownBy(() -> handler.validateValue("active", config));
}

@Test
void validateValue_valueNotInAllowedValues_throws() {
    var config = new EnumFieldConfig(List.of("active", "inactive"), null);
    assertThatThrownBy(() -> handler.validateValue("unknown", config))
        .isInstanceOf(ValidationException.class);
}

@Test
void validateValue_nullConfig_passes() {
    assertThatNoException().isThrownBy(() -> handler.validateValue("active", null));
}
```

**ReferenceFieldTypeHandlerTest additions:**
```java
@Test
void cast_uuidValue_returnsUuid() {
    UUID id = UUID.randomUUID();
    assertThat(handler.cast(id, null)).isEqualTo(id);
}

@Test
void cast_stringUuid_parsesUuid() {
    UUID id = UUID.randomUUID();
    assertThat(handler.cast(id.toString(), null)).isEqualTo(id);
}

@Test
void cast_invalidString_throws() {
    assertThatThrownBy(() -> handler.cast("not-a-uuid", null))
        .isInstanceOf(ValidationException.class);
}

@Test
void cast_nullValue_returnsNull() {
    assertThat(handler.cast(null, null)).isNull();
}

@Test
void validateValue_noOp_passes() {
    assertThatNoException().isThrownBy(() -> handler.validateValue(UUID.randomUUID(), null));
}
```

**MirrorFieldTypeHandlerTest additions:**
```java
@Test
void cast_anyValue_returnsNull() {
    assertThat(handler.cast("anything", null)).isNull();
}

@Test
void validateValue_anyValue_throwsValidationException() {
    assertThatThrownBy(() -> handler.validateValue("anything", null))
        .isInstanceOf(ValidationException.class);
}
```

Run all tests — new tests fail.

### Green

**Update `FieldValueHandler.java`** — add two default methods:
```java
package io.webcontify.content.fieldtype;

import io.webcontify.content.definition.model.FieldConfig;

public interface FieldValueHandler {

    void validateConfig(FieldConfig config);

    Class<? extends FieldConfig> configClass();

    default String formatForDisplay(Object value, FieldConfig config) {
        return value != null ? value.toString() : "";
    }

    default Object cast(Object rawValue, FieldConfig config) {
        return rawValue;
    }

    default void validateValue(Object value, FieldConfig config) {
        // no-op by default
    }
}
```

**Update `TextFieldTypeHandler.java`** — add override methods:
```java
@Override
public Object cast(Object rawValue, FieldConfig config) {
    if (rawValue == null) return null;
    return rawValue.toString();
}

@Override
public void validateValue(Object value, FieldConfig config) {
    if (value == null || config == null) return;
    TextFieldConfig c = (TextFieldConfig) config;
    String s = (String) value;
    if (c.minLength() != null && s.length() < c.minLength()) {
        throw validationError("value", "error.value.belowMinLength");
    }
    if (c.maxLength() != null && s.length() > c.maxLength()) {
        throw validationError("value", "error.value.exceedsMaxLength");
    }
    if (c.pattern() != null && !s.matches(c.pattern())) {
        throw validationError("value", "error.value.patternMismatch");
    }
}
```

**Update `IntegerFieldTypeHandler.java`** — add override methods:
```java
@Override
public Object cast(Object rawValue, FieldConfig config) {
    if (rawValue == null) return null;
    if (rawValue instanceof Long l) return l;
    if (rawValue instanceof Integer i) return i.longValue();
    if (rawValue instanceof Number n) return n.longValue();
    try {
        return Long.parseLong(rawValue.toString());
    } catch (NumberFormatException e) {
        throw validationError("value", "error.value.invalidInteger");
    }
}

@Override
public void validateValue(Object value, FieldConfig config) {
    if (value == null || config == null) return;
    IntegerFieldConfig c = (IntegerFieldConfig) config;
    Long v = (Long) value;
    if (c.min() != null && v < c.min()) {
        throw validationError("value", "error.value.belowMin");
    }
    if (c.max() != null && v > c.max()) {
        throw validationError("value", "error.value.exceedsMax");
    }
}
```

**Update `DecimalFieldTypeHandler.java`** — add override methods:
```java
@Override
public Object cast(Object rawValue, FieldConfig config) {
    if (rawValue == null) return null;
    if (rawValue instanceof BigDecimal bd) return bd;
    if (rawValue instanceof Number n) return new BigDecimal(n.toString());
    try {
        return new BigDecimal(rawValue.toString());
    } catch (NumberFormatException e) {
        throw validationError("value", "error.value.invalidDecimal");
    }
}

@Override
public void validateValue(Object value, FieldConfig config) {
    if (value == null || config == null) return;
    DecimalFieldConfig c = (DecimalFieldConfig) config;
    BigDecimal v = (BigDecimal) value;
    if (c.min() != null && v.compareTo(c.min()) < 0) {
        throw validationError("value", "error.value.belowMin");
    }
    if (c.max() != null && v.compareTo(c.max()) > 0) {
        throw validationError("value", "error.value.exceedsMax");
    }
}
```

Add import `import java.math.BigDecimal;` to `DecimalFieldTypeHandler.java` (already present).

**Update `BooleanFieldTypeHandler.java`** — add override methods:
```java
@Override
public Object cast(Object rawValue, FieldConfig config) {
    if (rawValue == null) return null;
    if (rawValue instanceof Boolean b) return b;
    return Boolean.parseBoolean(rawValue.toString());
}
```
No `validateValue` override needed — default no-op is correct.

**Update `DateFieldTypeHandler.java`** — add override methods:
```java
@Override
public Object cast(Object rawValue, FieldConfig config) {
    if (rawValue == null) return null;
    if (rawValue instanceof OffsetDateTime odt) return odt;
    try {
        return OffsetDateTime.parse(rawValue.toString());
    } catch (java.time.format.DateTimeParseException e) {
        throw validationError("value", "error.value.invalidDate");
    }
}
```
Add import `import java.time.OffsetDateTime;` (already present via columnType).

**Update `EnumFieldTypeHandler.java`** — add override methods:
```java
@Override
public Object cast(Object rawValue, FieldConfig config) {
    if (rawValue == null) return null;
    return rawValue.toString();
}

@Override
public void validateValue(Object value, FieldConfig config) {
    if (value == null || config == null) return;
    EnumFieldConfig c = (EnumFieldConfig) config;
    if (c.allowedValues() != null && !c.allowedValues().contains(value.toString())) {
        throw validationError("value", "error.value.notInAllowedValues");
    }
}
```

**Update `ReferenceFieldTypeHandler.java`** — add override methods:
```java
@Override
public Object cast(Object rawValue, FieldConfig config) {
    if (rawValue == null) return null;
    if (rawValue instanceof UUID id) return id;
    try {
        return UUID.fromString(rawValue.toString());
    } catch (IllegalArgumentException e) {
        throw validationError("value", "error.value.invalidUuid");
    }
}
```
Add import `import java.util.UUID;` (already present).

**Update `MirrorFieldTypeHandler.java`** — override both methods:
```java
@Override
public Object cast(Object rawValue, FieldConfig config) {
    return null;  // mirror fields are never written
}

@Override
public void validateValue(Object value, FieldConfig config) {
    throw validationError("value", "error.value.mirrorFieldNotWriteable");
}
```

Run all tests — all pass.

### Commit
```
feat(content): add cast() and validateValue() to FieldValueHandler and override in all handlers
```

---

## Task 4 — DisplayFormatResolver

**Files to create:**
- `apps/backend/src/main/java/io/webcontify/content/management/storage/DisplayFormatResolver.java`

**Test file:**
- `apps/backend/src/test/java/io/webcontify/content/management/storage/DisplayFormatResolverTest.java`

### Red

```java
// DisplayFormatResolverTest.java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.fieldtype.handler.TextFieldTypeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DisplayFormatResolverTest {

    private FieldTypeRegistry registry;
    private DisplayFormatResolver resolver;

    @BeforeEach
    void setUp() {
        registry = mock(FieldTypeRegistry.class);
        resolver = new DisplayFormatResolver(registry);

        TextFieldTypeHandler textHandler = new TextFieldTypeHandler();
        when(registry.get(FieldType.TEXT)).thenReturn(textHandler);
    }

    private ContentDefinition definitionWithTemplate(String template) {
        ContentDefinition def = new ContentDefinition();
        def.setDisplayFormat(template);
        FieldDefinition field = new FieldDefinition();
        field.setFieldKey("first_name");
        field.setFieldType(FieldType.TEXT);
        field.setConfig(null);
        def.setFields(List.of(field));
        return def;
    }

    @Test
    void resolve_templateWithPlaceholder_substitutesValue() {
        ContentDefinition def = definitionWithTemplate("{cf_first_name} Smith");
        Map<String, Object> values = Map.of("cf_first_name", "John");

        String result = resolver.resolve(def, values);

        assertThat(result).isEqualTo("John Smith");
    }

    @Test
    void resolve_nullTemplate_fallsBackToEntryId() {
        ContentDefinition def = new ContentDefinition();
        def.setDisplayFormat(null);
        def.setFields(List.of());
        UUID id = UUID.randomUUID();
        Map<String, Object> values = Map.of("id", id);

        String result = resolver.resolve(def, values);

        assertThat(result).isEqualTo(id.toString());
    }

    @Test
    void resolve_unknownPlaceholder_replacesWithEmpty() {
        ContentDefinition def = definitionWithTemplate("{cf_unknown}");
        Map<String, Object> values = Map.of();

        String result = resolver.resolve(def, values);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_nullFieldValue_replacesWithEmpty() {
        ContentDefinition def = definitionWithTemplate("Hello {cf_first_name}");
        Map<String, Object> values = Map.of();  // key absent

        String result = resolver.resolve(def, values);

        assertThat(result).isEqualTo("Hello ");
    }

    @Test
    void extractSearchFields_returnsAllPlaceholders() {
        List<String> fields = resolver.extractSearchFields("{cf_first_name} {cf_last_name}");

        assertThat(fields).containsExactly("cf_first_name", "cf_last_name");
    }

    @Test
    void extractSearchFields_nullTemplate_returnsEmptyList() {
        assertThat(resolver.extractSearchFields(null)).isEmpty();
    }
}
```

Run test — fails (class does not exist).

### Green

```java
// DisplayFormatResolver.java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DisplayFormatResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(cf_\\w+)\\}");

    private final FieldTypeRegistry registry;

    public DisplayFormatResolver(FieldTypeRegistry registry) {
        this.registry = registry;
    }

    public String resolve(ContentDefinition definition, Map<String, Object> values) {
        String template = definition.getDisplayFormat();
        if (template == null || template.isBlank()) {
            Object id = values.get("id");
            return id != null ? id.toString() : "";
        }

        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (m.find()) {
            String columnKey = m.group(1); // e.g. cf_first_name
            String fieldKey = columnKey.substring(3); // e.g. first_name

            FieldDefinition field = definition.getFields().stream()
                .filter(f -> f.getFieldKey().equals(fieldKey))
                .findFirst()
                .orElse(null);

            if (field == null) {
                m.appendReplacement(result, "");
                continue;
            }

            Object value = values.get(columnKey);
            String formatted = registry.get(field.getFieldType())
                .formatForDisplay(value, field.getConfig());
            m.appendReplacement(result, Matcher.quoteReplacement(formatted));
        }
        m.appendTail(result);
        return result.toString();
    }

    public List<String> extractSearchFields(String template) {
        if (template == null) return List.of();
        List<String> fields = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            fields.add(m.group(1));
        }
        return fields;
    }
}
```

Run test — passes.

### Commit
```
feat(content): add DisplayFormatResolver for display template resolution
```

---

## Task 5 — ContentStorage interface

**Files to create:**
- `apps/backend/src/main/java/io/webcontify/content/management/storage/ContentStorage.java`

**Test file:**
- `apps/backend/src/test/java/io/webcontify/content/management/storage/ContentStorageContractTest.java`

### Red

```java
// ContentStorageContractTest.java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContentStorageContractTest {

    @Test
    void contentStorage_declaresSaveMethod() throws NoSuchMethodException {
        assertThat(ContentStorage.class.getDeclaredMethod(
            "save", String.class, ContentDefinition.class, Map.class, UUID.class))
            .isNotNull();
    }

    @Test
    void contentStorage_declaresUpdateMethod() throws NoSuchMethodException {
        assertThat(ContentStorage.class.getDeclaredMethod(
            "update", String.class, ContentDefinition.class, UUID.class, Map.class, UUID.class))
            .isNotNull();
    }

    @Test
    void contentStorage_declaresSoftDeleteMethod() throws NoSuchMethodException {
        assertThat(ContentStorage.class.getDeclaredMethod(
            "softDelete", String.class, ContentDefinition.class, UUID.class, UUID.class))
            .isNotNull();
    }

    @Test
    void contentStorage_declaresFindByIdMethod() throws NoSuchMethodException {
        assertThat(ContentStorage.class.getDeclaredMethod(
            "findById", String.class, ContentDefinition.class, UUID.class))
            .isNotNull();
    }

    @Test
    void contentStorage_declaresFindAllMethod() throws NoSuchMethodException {
        assertThat(ContentStorage.class.getDeclaredMethod(
            "findAll", String.class, ContentDefinition.class, int.class))
            .isNotNull();
    }

    @Test
    void contentStorage_declaresGetHistoryMethod() throws NoSuchMethodException {
        assertThat(ContentStorage.class.getDeclaredMethod(
            "getHistory", String.class, ContentDefinition.class, UUID.class, int.class))
            .isNotNull();
    }
}
```

Run test — fails (interface does not exist).

### Green

```java
// ContentStorage.java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ContentStorage {

    ContentEntry save(String schemaName, ContentDefinition definition, Map<String, Object> values, UUID createdBy);

    ContentEntry update(String schemaName, ContentDefinition definition, UUID entryId, Map<String, Object> values, UUID updatedBy);

    void softDelete(String schemaName, ContentDefinition definition, UUID entryId, UUID deletedBy);

    Optional<ContentEntry> findById(String schemaName, ContentDefinition definition, UUID entryId);

    List<ContentEntry> findAll(String schemaName, ContentDefinition definition, int limit);

    List<EntryHistoryRecord> getHistory(String schemaName, ContentDefinition definition, UUID entryId, int limit);
}
```

Run test — passes.

### Commit
```
feat(content): add ContentStorage interface
```

---

## Task 6 — DedicatedTableStorage.save

**Files to create:**
- `apps/backend/src/main/java/io/webcontify/content/management/storage/DedicatedTableStorage.java`

**Test file:**
- `apps/backend/src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageSaveTest.java`

This is an integration test requiring Testcontainers + `@SpringBootTest`. It creates a schema and a content table first using the existing `DynamicTableManager`, then tests `DedicatedTableStorage.save`.

> **Note:** If Docker is not available, the test will fail with a Testcontainers error — this is expected in CI without Docker. The test must be written; do not skip it.

### Red

```java
// DedicatedTableStorageSaveTest.java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.definition.storage.DynamicTableManager;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TestSecurityConfiguration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.*;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfiguration.class)
class DedicatedTableStorageSaveTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    PermissionService permissionService;

    @Autowired DedicatedTableStorage storage;
    @Autowired DynamicTableManager tableManager;
    @Autowired DSLContext dsl;

    static final String SCHEMA = "tenant_storage_save_test";

    @BeforeEach
    void createSchemaAndTable() {
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);

        ContentDefinitionEntity def = new ContentDefinitionEntity();
        def.setId(UUID.randomUUID());
        def.setSlug("article");

        ContentFieldEntity titleField = new ContentFieldEntity();
        titleField.setId(UUID.randomUUID());
        titleField.setFieldKey("title");
        titleField.setFieldType("TEXT");
        titleField.setRequired(false);
        titleField.setSortable(true);
        titleField.setConfig(null);

        def.setFields(List.of(titleField));
        tableManager.createTable(SCHEMA, def);
    }

    private ContentDefinition buildDefinition() {
        ContentDefinition def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setSlug("article");

        FieldDefinition titleField = new FieldDefinition();
        titleField.setFieldKey("title");
        titleField.setFieldType(FieldType.TEXT);
        titleField.setConfig(new TextFieldConfig(null, 4000, null, null));

        def.setFields(List.of(titleField));
        return def;
    }

    @Test
    void save_insertsRowIntoContentTable() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();

        ContentEntry entry = storage.save(SCHEMA, def, Map.of("cf_title", "Hello World"), userId);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getCreatedBy()).isEqualTo(userId);
        assertThat(entry.getValues()).containsEntry("cf_title", "Hello World");

        int count = dsl.selectCount()
            .from(table(name(SCHEMA, "cd_article")))
            .where(field(name("id"), org.jooq.impl.SQLDataType.UUID).eq(entry.getId()))
            .fetchOne(0, int.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void save_insertsHistoryRow() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();

        ContentEntry entry = storage.save(SCHEMA, def, Map.of("cf_title", "Hello World"), userId);

        int historyCount = dsl.selectCount()
            .from(table(name(SCHEMA, "cd_article_history")))
            .where(field(name("entry_id"), org.jooq.impl.SQLDataType.UUID).eq(entry.getId()))
            .fetchOne(0, int.class);
        assertThat(historyCount).isEqualTo(1);
    }
}
```

Run test — fails (DedicatedTableStorage does not exist).

### Green

Create `DedicatedTableStorage.java` with `save` and the private helper methods `insertHistory` and `buildCreatedDiff`. Stub the remaining interface methods to throw `UnsupportedOperationException` for now (they will be implemented in Tasks 7–9).

```java
// DedicatedTableStorage.java
package io.webcontify.content.management.storage;

import static org.jooq.impl.DSL.*;
import static org.jooq.impl.SQLDataType.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.shared.util.UuidGenerator;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Component
public class DedicatedTableStorage implements ContentStorage {

    private final DSLContext dsl;
    private final FieldTypeRegistry registry;
    private final ObjectMapper objectMapper;

    public DedicatedTableStorage(DSLContext dsl, FieldTypeRegistry registry, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ContentEntry save(String schemaName, ContentDefinition definition, Map<String, Object> values, UUID createdBy) {
        String tableName = "cd_" + definition.getSlug();
        UUID entryId = UuidGenerator.generate();
        OffsetDateTime now = OffsetDateTime.now();

        Map<Field<Object>, Object> insertValues = new LinkedHashMap<>();
        insertValues.put(castField(name("id"), UUID), entryId);
        insertValues.put(castField(name("created_at"), TIMESTAMPWITHTIMEZONE), now);
        insertValues.put(castField(name("updated_at"), TIMESTAMPWITHTIMEZONE), now);
        insertValues.put(castField(name("created_by"), UUID), createdBy);
        insertValues.put(castField(name("updated_by"), UUID), createdBy);

        for (FieldDefinition field : definition.getFields()) {
            FieldTypeHandler handler = registry.get(field.getFieldType());
            if (handler.isVirtual()) continue;
            String columnName = "cf_" + field.getFieldKey();
            Object raw = values.get(columnName);
            Object cast = handler.cast(raw, field.getConfig());
            insertValues.put(castField(name(columnName), handler.columnType(field.getConfig())), cast);
        }

        dsl.insertInto(table(name(schemaName, tableName)))
            .set(insertValues)
            .execute();

        insertHistory(schemaName, tableName, entryId, "CREATED",
            buildCreatedDiff(values), createdBy, now);

        ContentEntry entry = new ContentEntry();
        entry.setId(entryId);
        entry.setDefinitionId(definition.getId());
        entry.setValues(values);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        entry.setCreatedBy(createdBy);
        entry.setUpdatedBy(createdBy);
        return entry;
    }

    @Override
    public ContentEntry update(String schemaName, ContentDefinition definition, UUID entryId,
                               Map<String, Object> values, UUID updatedBy) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void softDelete(String schemaName, ContentDefinition definition, UUID entryId, UUID deletedBy) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Optional<ContentEntry> findById(String schemaName, ContentDefinition definition, UUID entryId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<ContentEntry> findAll(String schemaName, ContentDefinition definition, int limit) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<EntryHistoryRecord> getHistory(String schemaName, ContentDefinition definition, UUID entryId, int limit) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // --- private helpers ---

    @SuppressWarnings("unchecked")
    private <T> Field<Object> castField(org.jooq.Name fieldName, org.jooq.DataType<T> type) {
        return (Field<Object>) field(fieldName, type);
    }

    private Map<String, Object> buildCreatedDiff(Map<String, Object> values) {
        Map<String, Object> diff = new LinkedHashMap<>();
        values.forEach((k, v) -> diff.put(k, Map.of("old", (Object) null, "new", v != null ? v : "null")));
        return diff;
    }

    private void insertHistory(String schemaName, String tableName, UUID entryId,
                               String action, Map<String, Object> changedFields,
                               UUID changedBy, OffsetDateTime when) {
        String historyName = tableName + "_history";
        try {
            String json = objectMapper.writeValueAsString(changedFields);
            dsl.insertInto(table(name(schemaName, historyName)))
                .set(field(name("id"), SQLDataType.UUID), UuidGenerator.generate())
                .set(field(name("entry_id"), SQLDataType.UUID), entryId)
                .set(field(name("action"), SQLDataType.VARCHAR), action)
                .set(field(name("changed_fields"), SQLDataType.JSONB), org.jooq.JSONB.valueOf(json))
                .set(field(name("changed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE), when)
                .set(field(name("changed_by"), SQLDataType.UUID), changedBy)
                .execute();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize history changedFields", e);
        }
    }
}
```

Run test — passes.

### Commit
```
feat(content): add DedicatedTableStorage.save with history insert
```

---

## Task 7 — DedicatedTableStorage.findById

**Test file:**
- `apps/backend/src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageFindByIdTest.java`

### Red

```java
// DedicatedTableStorageFindByIdTest.java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.definition.storage.DynamicTableManager;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TestSecurityConfiguration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfiguration.class)
class DedicatedTableStorageFindByIdTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    PermissionService permissionService;

    @Autowired DedicatedTableStorage storage;
    @Autowired DynamicTableManager tableManager;
    @Autowired DSLContext dsl;

    static final String SCHEMA = "tenant_storage_findbyid_test";

    @BeforeEach
    void createSchemaAndTable() {
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);

        ContentDefinitionEntity def = new ContentDefinitionEntity();
        def.setId(UUID.randomUUID());
        def.setSlug("page");

        ContentFieldEntity titleField = new ContentFieldEntity();
        titleField.setId(UUID.randomUUID());
        titleField.setFieldKey("title");
        titleField.setFieldType("TEXT");
        titleField.setRequired(false);
        titleField.setSortable(true);
        titleField.setConfig(null);
        def.setFields(List.of(titleField));
        tableManager.createTable(SCHEMA, def);
    }

    private ContentDefinition buildDefinition() {
        ContentDefinition def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setSlug("page");
        FieldDefinition f = new FieldDefinition();
        f.setFieldKey("title");
        f.setFieldType(FieldType.TEXT);
        f.setConfig(new TextFieldConfig(null, 4000, null, null));
        def.setFields(List.of(f));
        return def;
    }

    @Test
    void findById_savedEntry_returnsEntry() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_title", "My Page"), userId);

        Optional<ContentEntry> found = storage.findById(SCHEMA, def, saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getValues()).containsKey("cf_title");
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        ContentDefinition def = buildDefinition();

        Optional<ContentEntry> found = storage.findById(SCHEMA, def, UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findById_softDeletedEntry_returnsEmpty() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_title", "To Delete"), userId);

        // manually soft-delete: set deleted_at
        dsl.execute("UPDATE " + SCHEMA + ".cd_page SET deleted_at = NOW() WHERE id = '" + saved.getId() + "'");

        Optional<ContentEntry> found = storage.findById(SCHEMA, def, saved.getId());
        assertThat(found).isEmpty();
    }
}
```

Run test — fails (findById throws UnsupportedOperationException).

### Green

Implement `findById` and the private helper `recordToEntry` in `DedicatedTableStorage.java`:

```java
@Override
@Transactional(readOnly = true)
public Optional<ContentEntry> findById(String schemaName, ContentDefinition definition, UUID entryId) {
    String tableName = "cd_" + definition.getSlug();

    List<Field<?>> selectFields = new ArrayList<>();
    selectFields.add(field(name("id"), SQLDataType.UUID));
    selectFields.add(field(name("created_at"), TIMESTAMPWITHTIMEZONE));
    selectFields.add(field(name("updated_at"), TIMESTAMPWITHTIMEZONE));
    selectFields.add(field(name("created_by"), SQLDataType.UUID));
    selectFields.add(field(name("updated_by"), SQLDataType.UUID));

    for (FieldDefinition fieldDef : definition.getFields()) {
        FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
        if (handler.isVirtual()) continue;
        selectFields.add(field(name("cf_" + fieldDef.getFieldKey()), handler.columnType(fieldDef.getConfig())));
    }

    org.jooq.Record record = dsl.select(selectFields)
        .from(table(name(schemaName, tableName)))
        .where(field(name("id"), SQLDataType.UUID).eq(entryId))
        .and(field(name("deleted_at"), TIMESTAMPWITHTIMEZONE).isNull())
        .fetchOne();

    if (record == null) return Optional.empty();
    return Optional.of(recordToEntry(record, definition));
}

private ContentEntry recordToEntry(org.jooq.Record record, ContentDefinition definition) {
    ContentEntry entry = new ContentEntry();
    entry.setId(record.get(field(name("id"), SQLDataType.UUID)));
    entry.setDefinitionId(definition.getId());
    entry.setCreatedAt(record.get(field(name("created_at"), TIMESTAMPWITHTIMEZONE)));
    entry.setUpdatedAt(record.get(field(name("updated_at"), TIMESTAMPWITHTIMEZONE)));
    entry.setCreatedBy(record.get(field(name("created_by"), SQLDataType.UUID)));
    entry.setUpdatedBy(record.get(field(name("updated_by"), SQLDataType.UUID)));

    Map<String, Object> values = new LinkedHashMap<>();
    for (FieldDefinition fieldDef : definition.getFields()) {
        FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
        if (handler.isVirtual()) continue;
        String columnName = "cf_" + fieldDef.getFieldKey();
        values.put(columnName, record.get(field(name(columnName), handler.columnType(fieldDef.getConfig()))));
    }
    entry.setValues(values);
    return entry;
}
```

Add import `import java.util.ArrayList;` if not already present.

Run test — passes.

### Commit
```
feat(content): add DedicatedTableStorage.findById with soft delete filter
```

---

## Task 8 — DedicatedTableStorage.update

**Test file:**
- `apps/backend/src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageUpdateTest.java`

### Red

```java
// DedicatedTableStorageUpdateTest.java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.definition.storage.DynamicTableManager;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TestSecurityConfiguration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfiguration.class)
class DedicatedTableStorageUpdateTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    PermissionService permissionService;

    @Autowired DedicatedTableStorage storage;
    @Autowired DynamicTableManager tableManager;
    @Autowired DSLContext dsl;

    static final String SCHEMA = "tenant_storage_update_test";

    @BeforeEach
    void setup() {
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        ContentDefinitionEntity def = new ContentDefinitionEntity();
        def.setId(UUID.randomUUID());
        def.setSlug("note");
        ContentFieldEntity f = new ContentFieldEntity();
        f.setId(UUID.randomUUID());
        f.setFieldKey("body");
        f.setFieldType("TEXT");
        f.setRequired(false);
        f.setSortable(false);
        f.setConfig(null);
        def.setFields(List.of(f));
        tableManager.createTable(SCHEMA, def);
    }

    private ContentDefinition buildDefinition() {
        ContentDefinition def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setSlug("note");
        FieldDefinition f = new FieldDefinition();
        f.setFieldKey("body");
        f.setFieldType(FieldType.TEXT);
        f.setConfig(new TextFieldConfig(null, 4000, null, null));
        def.setFields(List.of(f));
        return def;
    }

    @Test
    void update_changesFieldValueInTable() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_body", "original"), userId);

        storage.update(SCHEMA, def, saved.getId(), Map.of("cf_body", "updated"), userId);

        Optional<ContentEntry> found = storage.findById(SCHEMA, def, saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getValues()).containsEntry("cf_body", "updated");
    }

    @Test
    void update_insertsHistoryRowWithDiff() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_body", "original"), userId);

        storage.update(SCHEMA, def, saved.getId(), Map.of("cf_body", "updated"), userId);

        int historyCount = dsl.selectCount()
            .from(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name(SCHEMA, "cd_note_history")))
            .where(org.jooq.impl.DSL.field(org.jooq.impl.DSL.name("entry_id"),
                org.jooq.impl.SQLDataType.UUID).eq(saved.getId()))
            .fetchOne(0, int.class);
        assertThat(historyCount).isEqualTo(2); // CREATED + UPDATED
    }

    @Test
    void update_returnsUpdatedEntry() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_body", "original"), userId);

        ContentEntry updated = storage.update(SCHEMA, def, saved.getId(), Map.of("cf_body", "updated"), userId);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getValues()).containsEntry("cf_body", "updated");
    }
}
```

Run test — fails (update throws UnsupportedOperationException).

### Green

Implement `update` in `DedicatedTableStorage.java`:

```java
@Override
@Transactional
public ContentEntry update(String schemaName, ContentDefinition definition, UUID entryId,
                           Map<String, Object> values, UUID updatedBy) {
    String tableName = "cd_" + definition.getSlug();
    OffsetDateTime now = OffsetDateTime.now();

    // fetch existing values for diff
    Optional<ContentEntry> existing = findById(schemaName, definition, entryId);
    Map<String, Object> oldValues = existing.map(ContentEntry::getValues).orElse(Map.of());

    Map<Field<Object>, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(castField(name("updated_at"), TIMESTAMPWITHTIMEZONE), now);
    updateFields.put(castField(name("updated_by"), SQLDataType.UUID), updatedBy);

    for (FieldDefinition fieldDef : definition.getFields()) {
        FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
        if (handler.isVirtual()) continue;
        String columnName = "cf_" + fieldDef.getFieldKey();
        if (values.containsKey(columnName)) {
            Object cast = handler.cast(values.get(columnName), fieldDef.getConfig());
            updateFields.put(castField(name(columnName), handler.columnType(fieldDef.getConfig())), cast);
        }
    }

    dsl.update(table(name(schemaName, tableName)))
        .set(updateFields)
        .where(field(name("id"), SQLDataType.UUID).eq(entryId))
        .execute();

    // build diff: only fields present in values map
    Map<String, Object> diff = new LinkedHashMap<>();
    values.forEach((k, newVal) -> {
        Object oldVal = oldValues.get(k);
        diff.put(k, Map.of("old", oldVal != null ? oldVal : "null", "new", newVal != null ? newVal : "null"));
    });

    insertHistory(schemaName, tableName, entryId, "UPDATED", diff, updatedBy, now);

    ContentEntry entry = new ContentEntry();
    entry.setId(entryId);
    entry.setDefinitionId(definition.getId());
    entry.setValues(values);
    entry.setUpdatedAt(now);
    entry.setUpdatedBy(updatedBy);
    return entry;
}
```

Run test — passes.

### Commit
```
feat(content): add DedicatedTableStorage.update with diff history
```

---

## Task 9 — DedicatedTableStorage.softDelete + logChange + getHistory + findAll

**Test file:**
- `apps/backend/src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageReadDeleteTest.java`

### Red

```java
// DedicatedTableStorageReadDeleteTest.java
package io.webcontify.content.management.storage;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.definition.storage.DynamicTableManager;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryHistoryRecord;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TestSecurityConfiguration;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfiguration.class)
class DedicatedTableStorageReadDeleteTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    PermissionService permissionService;

    @Autowired DedicatedTableStorage storage;
    @Autowired DynamicTableManager tableManager;
    @Autowired DSLContext dsl;

    static final String SCHEMA = "tenant_storage_rdd_test";

    @BeforeEach
    void setup() {
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        ContentDefinitionEntity def = new ContentDefinitionEntity();
        def.setId(UUID.randomUUID());
        def.setSlug("task");
        ContentFieldEntity f = new ContentFieldEntity();
        f.setId(UUID.randomUUID());
        f.setFieldKey("name");
        f.setFieldType("TEXT");
        f.setRequired(false);
        f.setSortable(false);
        f.setConfig(null);
        def.setFields(List.of(f));
        tableManager.createTable(SCHEMA, def);
    }

    private ContentDefinition buildDefinition() {
        ContentDefinition def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setSlug("task");
        FieldDefinition f = new FieldDefinition();
        f.setFieldKey("name");
        f.setFieldType(FieldType.TEXT);
        f.setConfig(new TextFieldConfig(null, 4000, null, null));
        def.setFields(List.of(f));
        return def;
    }

    @Test
    void softDelete_setsDeletedAt() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_name", "Task A"), userId);

        storage.softDelete(SCHEMA, def, saved.getId(), userId);

        Optional<ContentEntry> found = storage.findById(SCHEMA, def, saved.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void softDelete_insertsDeletedHistoryRow() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_name", "Task A"), userId);

        storage.softDelete(SCHEMA, def, saved.getId(), userId);

        List<EntryHistoryRecord> history = storage.getHistory(SCHEMA, def, saved.getId(), 10);
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        assertThat(history.stream().anyMatch(r -> "DELETED".equals(r.action()))).isTrue();
    }

    @Test
    void findAll_returnsOnlyNonDeletedEntries() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        storage.save(SCHEMA, def, Map.of("cf_name", "Keep"), userId);
        ContentEntry toDelete = storage.save(SCHEMA, def, Map.of("cf_name", "Delete"), userId);
        storage.softDelete(SCHEMA, def, toDelete.getId(), userId);

        List<ContentEntry> all = storage.findAll(SCHEMA, def, 100);

        assertThat(all).anyMatch(e -> {
            Object val = e.getValues().get("cf_name");
            return "Keep".equals(val);
        });
        assertThat(all.stream().noneMatch(e -> toDelete.getId().equals(e.getId()))).isTrue();
    }

    @Test
    void getHistory_returnsHistoryInDescendingOrder() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_name", "Task A"), userId);
        storage.update(SCHEMA, def, saved.getId(), Map.of("cf_name", "Task B"), userId);

        List<EntryHistoryRecord> history = storage.getHistory(SCHEMA, def, saved.getId(), 10);

        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        // most recent first
        assertThat(history.get(0).changedAt())
            .isAfterOrEqualTo(history.get(1).changedAt());
    }

    @Test
    void getHistory_limitIsRespected() {
        ContentDefinition def = buildDefinition();
        UUID userId = UUID.randomUUID();
        ContentEntry saved = storage.save(SCHEMA, def, Map.of("cf_name", "Task A"), userId);
        storage.update(SCHEMA, def, saved.getId(), Map.of("cf_name", "Task B"), userId);
        storage.update(SCHEMA, def, saved.getId(), Map.of("cf_name", "Task C"), userId);

        List<EntryHistoryRecord> history = storage.getHistory(SCHEMA, def, saved.getId(), 1);

        assertThat(history).hasSize(1);
    }
}
```

Run test — fails (methods throw UnsupportedOperationException).

### Green

Implement `softDelete`, `findAll`, and `getHistory` in `DedicatedTableStorage.java`:

```java
@Override
@Transactional
public void softDelete(String schemaName, ContentDefinition definition, UUID entryId, UUID deletedBy) {
    String tableName = "cd_" + definition.getSlug();
    OffsetDateTime now = OffsetDateTime.now();

    dsl.update(table(name(schemaName, tableName)))
        .set(field(name("deleted_at"), TIMESTAMPWITHTIMEZONE), now)
        .set(field(name("deleted_by"), SQLDataType.UUID), deletedBy)
        .where(field(name("id"), SQLDataType.UUID).eq(entryId))
        .execute();

    insertHistory(schemaName, tableName, entryId, "DELETED", Map.of(), deletedBy, now);
}

@Override
@Transactional(readOnly = true)
public List<ContentEntry> findAll(String schemaName, ContentDefinition definition, int limit) {
    String tableName = "cd_" + definition.getSlug();

    List<Field<?>> selectFields = new ArrayList<>();
    selectFields.add(field(name("id"), SQLDataType.UUID));
    selectFields.add(field(name("created_at"), TIMESTAMPWITHTIMEZONE));
    selectFields.add(field(name("updated_at"), TIMESTAMPWITHTIMEZONE));
    selectFields.add(field(name("created_by"), SQLDataType.UUID));
    selectFields.add(field(name("updated_by"), SQLDataType.UUID));

    for (FieldDefinition fieldDef : definition.getFields()) {
        FieldTypeHandler handler = registry.get(fieldDef.getFieldType());
        if (handler.isVirtual()) continue;
        selectFields.add(field(name("cf_" + fieldDef.getFieldKey()), handler.columnType(fieldDef.getConfig())));
    }

    return dsl.select(selectFields)
        .from(table(name(schemaName, tableName)))
        .where(field(name("deleted_at"), TIMESTAMPWITHTIMEZONE).isNull())
        .orderBy(field(name("created_at"), TIMESTAMPWITHTIMEZONE).desc())
        .limit(limit)
        .fetch()
        .map(record -> recordToEntry(record, definition));
}

@Override
@Transactional(readOnly = true)
public List<EntryHistoryRecord> getHistory(String schemaName, ContentDefinition definition, UUID entryId, int limit) {
    String historyName = "cd_" + definition.getSlug() + "_history";

    return dsl.select(
            field(name("id"), SQLDataType.UUID),
            field(name("entry_id"), SQLDataType.UUID),
            field(name("action"), SQLDataType.VARCHAR),
            field(name("changed_fields"), SQLDataType.JSONB),
            field(name("changed_at"), TIMESTAMPWITHTIMEZONE),
            field(name("changed_by"), SQLDataType.UUID)
        )
        .from(table(name(schemaName, historyName)))
        .where(field(name("entry_id"), SQLDataType.UUID).eq(entryId))
        .orderBy(field(name("changed_at"), TIMESTAMPWITHTIMEZONE).desc())
        .limit(limit)
        .fetch()
        .map(record -> {
            UUID id = record.get(field(name("id"), SQLDataType.UUID));
            String action = record.get(field(name("action"), SQLDataType.VARCHAR));
            org.jooq.JSONB jsonb = record.get(field(name("changed_fields"), SQLDataType.JSONB));
            java.time.OffsetDateTime changedAt = record.get(field(name("changed_at"), TIMESTAMPWITHTIMEZONE));
            UUID changedBy = record.get(field(name("changed_by"), SQLDataType.UUID));

            Map<String, EntryHistoryRecord.FieldChange> changedFields;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> raw = objectMapper.readValue(
                    jsonb != null ? jsonb.data() : "{}", Map.class);
                changedFields = new LinkedHashMap<>();
                raw.forEach((k, v) -> changedFields.put(k,
                    new EntryHistoryRecord.FieldChange(v.get("old"), v.get("new"))));
            } catch (JsonProcessingException e) {
                changedFields = Map.of();
            }

            return new EntryHistoryRecord(id, entryId, action, changedFields, changedAt, changedBy);
        });
}
```

Run test — passes.

### Commit
```
feat(content): add DedicatedTableStorage.softDelete, findAll, getHistory
```

---

## Test commands

```bash
# Unit tests (no Docker needed)
JAVA_HOME=$(/usr/libexec/java_home -v 21) cd apps/backend && ./gradlew test \
  --tests "io.webcontify.content.management.model.*" \
  --tests "io.webcontify.content.fieldtype.*" \
  --tests "io.webcontify.content.management.storage.DisplayFormatResolverTest" \
  --tests "io.webcontify.content.management.storage.ContentStorageContractTest"

# Integration tests (requires Docker)
JAVA_HOME=$(/usr/libexec/java_home -v 21) cd apps/backend && ./gradlew test \
  --tests "io.webcontify.content.management.storage.DedicatedTableStorage*"

# All content tests
JAVA_HOME=$(/usr/libexec/java_home -v 21) cd apps/backend && ./gradlew test \
  --tests "io.webcontify.content.*"
```

---

## Summary of created/modified files

| Action | File |
|--------|------|
| create | `src/main/java/io/webcontify/content/management/model/ContentEntry.java` |
| create | `src/main/java/io/webcontify/content/management/model/FieldValue.java` |
| create | `src/main/java/io/webcontify/content/management/model/EntryReference.java` |
| create | `src/main/java/io/webcontify/content/management/model/EntryHistoryRecord.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/FieldValueHandler.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/handler/TextFieldTypeHandler.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/handler/IntegerFieldTypeHandler.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/handler/DecimalFieldTypeHandler.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/handler/BooleanFieldTypeHandler.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/handler/DateFieldTypeHandler.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/handler/EnumFieldTypeHandler.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/handler/ReferenceFieldTypeHandler.java` |
| modify | `src/main/java/io/webcontify/content/fieldtype/handler/MirrorFieldTypeHandler.java` |
| create | `src/main/java/io/webcontify/content/management/storage/DisplayFormatResolver.java` |
| create | `src/main/java/io/webcontify/content/management/storage/ContentStorage.java` |
| create | `src/main/java/io/webcontify/content/management/storage/DedicatedTableStorage.java` |
| create | `src/test/java/io/webcontify/content/management/model/ContentEntryTest.java` |
| create | `src/test/java/io/webcontify/content/management/model/EntryHistoryRecordTest.java` |
| create | `src/test/java/io/webcontify/content/management/storage/DisplayFormatResolverTest.java` |
| create | `src/test/java/io/webcontify/content/management/storage/ContentStorageContractTest.java` |
| create | `src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageSaveTest.java` |
| create | `src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageFindByIdTest.java` |
| create | `src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageUpdateTest.java` |
| create | `src/test/java/io/webcontify/content/management/storage/DedicatedTableStorageReadDeleteTest.java` |
| modify | `src/test/java/io/webcontify/content/fieldtype/handler/TextFieldTypeHandlerTest.java` |
| modify | `src/test/java/io/webcontify/content/fieldtype/handler/IntegerFieldTypeHandlerTest.java` |
| modify | `src/test/java/io/webcontify/content/fieldtype/handler/DecimalFieldTypeHandlerTest.java` |
| modify | `src/test/java/io/webcontify/content/fieldtype/handler/BooleanFieldTypeHandlerTest.java` |
| modify | `src/test/java/io/webcontify/content/fieldtype/handler/DateFieldTypeHandlerTest.java` |
| modify | `src/test/java/io/webcontify/content/fieldtype/handler/EnumFieldTypeHandlerTest.java` |
| modify | `src/test/java/io/webcontify/content/fieldtype/handler/ReferenceFieldTypeHandlerTest.java` |
| modify | `src/test/java/io/webcontify/content/fieldtype/handler/MirrorFieldTypeHandlerTest.java` |
