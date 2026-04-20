# Content Entry CRUD — Part 2: Service + API

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Expose content entry CRUD via REST API. Orchestrates validation, storage, reference resolution, onDelete enforcement.

**Architecture:** `ContentEntryService` uses `ContentValidationService` (validates via FieldTypeRegistry) + `ContentStorage` (jOOQ persistence from 5d1) + `DisplayFormatResolver` (resolves references). `ContentEntryController` implements generated OpenAPI interface.

**Tech Stack:** Java 21, Spring Boot 4, jOOQ, OpenAPI generator, MapStruct, OpenFGA

---

## Package structure (additions to 5d1)

```
io.webcontify.content.management/
  service/
    ContentEntryService.java          ← new
    ContentValidationService.java     ← new
    DisplayFormatResolver.java        ← 5d1
  storage/
    ContentStorage.java               ← 5d1
    DedicatedTableStorage.java        ← 5d1
  mapper/
    ContentEntryMapper.java           ← new
  api/
    ContentEntryController.java       ← new
  model/
    ContentEntry.java                 ← 5d1
    FieldValue.java                   ← 5d1
    EntryReference.java               ← 5d1
    EntryHistoryRecord.java           ← 5d1
```

---

## Task 1 — ContentValidationService: cast + required check

**File:** `apps/backend/src/main/java/io/webcontify/content/management/service/ContentValidationService.java`

```java
package io.webcontify.content.management.service;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.shared.exception.FieldError;
import io.webcontify.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContentValidationService {

    private final FieldTypeRegistry registry;

    public ContentValidationService(FieldTypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Casts raw input values to typed values and checks required constraints.
     * Virtual fields (MIRROR) are skipped — they are never in the input map.
     * Returns a new map keyed by "cf_<fieldKey>".
     */
    public Map<String, Object> validateAndCastValues(
            ContentDefinition definition, Map<String, Object> rawValues) {

        List<FieldError> errors = new ArrayList<>();
        Map<String, Object> casted = new LinkedHashMap<>();

        for (FieldDefinition field : definition.getFields()) {
            FieldTypeHandler handler = registry.get(field.getFieldType());
            if (handler.isVirtual()) continue;

            String columnName = "cf_" + field.getFieldKey();
            Object raw = rawValues.get(columnName);

            if (raw == null && field.isRequired()) {
                errors.add(new FieldError(columnName, "error.value.required"));
                continue;
            }
            if (raw == null) {
                casted.put(columnName, null);
                continue;
            }

            try {
                Object castedValue = handler.cast(raw, field.getConfig());
                handler.validateValue(castedValue, field.getConfig());
                casted.put(columnName, castedValue);
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError(columnName, e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("content.entry.validation_failed", errors);
        }
        return casted;
    }
}
```

### Test

**File:** `apps/backend/src/test/java/io/webcontify/content/management/service/ContentValidationServiceTest.java`

```java
package io.webcontify.content.management.service;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.TextFieldConfig;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.fieldtype.handler.TextFieldTypeHandler;
import io.webcontify.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentValidationServiceTest {

    private ContentValidationService service;

    @BeforeEach
    void setUp() {
        var registry = new FieldTypeRegistry(List.of(new TextFieldTypeHandler()));
        service = new ContentValidationService(registry);
    }

    @Test
    void validateAndCastValues_returnsTypedValues_whenInputValid() {
        var definition = definitionWith(textField("name", true));
        Map<String, Object> result = service.validateAndCastValues(definition,
                Map.of("cf_name", "Alice"));
        assertThat(result).containsEntry("cf_name", "Alice");
    }

    @Test
    void validateAndCastValues_throwsValidationException_whenRequiredFieldMissing() {
        var definition = definitionWith(textField("name", true));
        assertThatThrownBy(() ->
                service.validateAndCastValues(definition, Map.of()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateAndCastValues_allowsNull_whenFieldNotRequired() {
        var definition = definitionWith(textField("bio", false));
        Map<String, Object> result = service.validateAndCastValues(definition, Map.of());
        assertThat(result).containsEntry("cf_bio", null);
    }

    private ContentDefinition definitionWith(FieldDefinition... fields) {
        var def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setFields(List.of(fields));
        return def;
    }

    private FieldDefinition textField(String key, boolean required) {
        var f = new FieldDefinition();
        f.setFieldKey(key);
        f.setFieldType(FieldType.TEXT);
        f.setRequired(required);
        f.setConfig(new TextFieldConfig(null, null, false));
        return f;
    }
}
```

**Run:** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.management.service.ContentValidationServiceTest`

**Commit:** `feat(content): add ContentValidationService cast and required validation`

---

## Task 2 — ContentValidationService: unique + reference target checks

Extend `ContentValidationService` with two additional methods. These are called by `ContentEntryService` after `validateAndCastValues` succeeds.

**Add to `ContentValidationService`:**

```java
// Additional imports
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
import io.webcontify.content.management.storage.ContentStorage;
import io.webcontify.shared.exception.ConflictException;
import org.jooq.DSLContext;
import java.util.UUID;
import static org.jooq.impl.DSL.*;

// Add constructor param: DSLContext dsl (inject via constructor)

/**
 * For each field with isUniqueValue=true, verify no other row (excluding excludeEntryId) has
 * the same value. Throws ConflictException on first violation.
 */
public void validateUniqueness(
        String schemaName, ContentDefinition definition,
        Map<String, Object> values, UUID excludeEntryId) {

    for (FieldDefinition field : definition.getFields()) {
        if (!field.isUniqueValue()) continue;
        FieldTypeHandler handler = registry.get(field.getFieldType());
        if (handler.isVirtual()) continue;

        String columnName = "cf_" + field.getFieldKey();
        Object value = values.get(columnName);
        if (value == null) continue;

        String tableName = "cd_" + definition.getSlug().replace("-", "_");

        var condition = field(name(columnName)).equal(value)
                .and(field(name("deleted_at")).isNull());

        if (excludeEntryId != null) {
            condition = condition.and(field(name("id")).notEqual(excludeEntryId));
        }

        boolean exists = dsl.fetchExists(
                dsl.selectOne()
                        .from(table(name(schemaName, tableName)))
                        .where(condition));

        if (exists) {
            throw new ConflictException(
                    "content.entry.duplicate_value",
                    List.of(new FieldError(columnName, "error.value.not_unique")));
        }
    }
}

/**
 * For each REFERENCE field, verify the referenced entry exists in the target table.
 * Throws ValidationException if target not found.
 */
public void validateReferenceTargets(
        String schemaName, ContentDefinition definition,
        Map<String, Object> values,
        java.util.function.Function<UUID, ContentDefinition> definitionLoader) {

    for (FieldDefinition field : definition.getFields()) {
        if (field.getFieldType() != FieldType.REFERENCE) continue;
        String columnName = "cf_" + field.getFieldKey();
        Object raw = values.get(columnName);
        if (raw == null) continue;

        UUID targetId = raw instanceof UUID u ? u : UUID.fromString(raw.toString());
        ReferenceFieldConfig config = (ReferenceFieldConfig) field.getConfig();
        ContentDefinition targetDef = definitionLoader.apply(config.targetDefinitionId());
        String targetTable = "cd_" + targetDef.getSlug().replace("-", "_");

        boolean exists = dsl.fetchExists(
                dsl.selectOne()
                        .from(table(name(schemaName, targetTable)))
                        .where(field(name("id")).equal(targetId)
                                .and(field(name("deleted_at")).isNull())));

        if (!exists) {
            throw new ValidationException("content.entry.validation_failed",
                    List.of(new FieldError(columnName, "error.reference.target_not_found")));
        }
    }
}
```

### Test

**File:** `apps/backend/src/test/java/io/webcontify/content/management/service/ContentValidationServiceUniquenessTest.java`

```java
package io.webcontify.content.management.service;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.fieldtype.handler.TextFieldTypeHandler;
import io.webcontify.shared.exception.ConflictException;
import org.jooq.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentValidationServiceUniquenessTest {

    @Mock DSLContext dsl;
    @Mock SelectSelectStep<Record1<Integer>> selectStep;
    @Mock SelectConditionStep<Record1<Integer>> condStep;

    private ContentValidationService service;

    @BeforeEach
    void setUp() {
        var registry = new FieldTypeRegistry(List.of(new TextFieldTypeHandler()));
        service = new ContentValidationService(registry, dsl);
    }

    @Test
    void validateUniqueness_throwsConflict_whenDuplicateExists() {
        var field = uniqueTextField("email");
        var definition = definitionWith(field);
        definition.setSlug("person");

        when(dsl.selectOne()).thenReturn(selectStep);
        when(selectStep.from(any(Table.class))).thenReturn(any());
        // Simulate existing row by returning true from fetchExists
        when(dsl.fetchExists(any(Select.class))).thenReturn(true);

        assertThatThrownBy(() ->
                service.validateUniqueness("tenant_test", definition,
                        Map.of("cf_email", "alice@example.com"), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void validateUniqueness_passes_whenNoConflict() {
        var field = uniqueTextField("email");
        var definition = definitionWith(field);
        definition.setSlug("person");

        when(dsl.fetchExists(any(Select.class))).thenReturn(false);
        when(dsl.selectOne()).thenReturn(selectStep);
        when(selectStep.from(any(Table.class))).thenReturn(any());

        // No exception expected
        service.validateUniqueness("tenant_test", definition,
                Map.of("cf_email", "alice@example.com"), null);
    }

    private FieldDefinition uniqueTextField(String key) {
        var f = new FieldDefinition();
        f.setFieldKey(key);
        f.setFieldType(FieldType.TEXT);
        f.setUniqueValue(true);
        f.setConfig(new TextFieldConfig(null, null, false));
        return f;
    }

    private ContentDefinition definitionWith(FieldDefinition... fields) {
        var def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setFields(List.of(fields));
        return def;
    }
}
```

**Run:** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.management.service.ContentValidationServiceUniquenessTest`

**Commit:** `feat(content): add uniqueness and reference target validation to ContentValidationService`

---

## Task 3 — Reference + Mirror resolution logic

Add a `ReferenceResolver` component. It encapsulates loading a referenced entry and building the `EntryReference` display. Mirror resolution queries inversely — intra-app only for now.

**File:** `apps/backend/src/main/java/io/webcontify/content/management/service/ReferenceResolver.java`

```java
package io.webcontify.content.management.service;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.storage.ContentStorage;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ReferenceResolver {

    private final ContentStorage storage;
    private final DisplayFormatResolver displayFormatResolver;

    public ReferenceResolver(ContentStorage storage, DisplayFormatResolver displayFormatResolver) {
        this.storage = storage;
        this.displayFormatResolver = displayFormatResolver;
    }

    /**
     * For each REFERENCE field in the entry, replace the raw UUID value with an
     * EntryReference{id, display} resolved via the target definition and its displayFormat.
     *
     * For MIRROR fields (virtual), resolve inversely: find all entries in target definition
     * that hold a REFERENCE field pointing back to this entry.
     */
    public Map<String, Object> resolveReferences(
            String schemaName,
            ContentDefinition definition,
            ContentEntry entry,
            java.util.function.Function<UUID, ContentDefinition> definitionLoader) {

        if (definition.getFields() == null) return entry.getValues();

        Map<String, Object> resolved = new LinkedHashMap<>(entry.getValues());

        for (FieldDefinition field : definition.getFields()) {
            if (field.getFieldType() == FieldType.REFERENCE) {
                String col = "cf_" + field.getFieldKey();
                Object rawId = resolved.get(col);
                if (rawId == null) continue;

                UUID targetId = rawId instanceof UUID u ? u : UUID.fromString(rawId.toString());
                ReferenceFieldConfig config = (ReferenceFieldConfig) field.getConfig();
                ContentDefinition targetDef = definitionLoader.apply(config.targetDefinitionId());

                storage.findById(schemaName, targetDef, targetId).ifPresentOrElse(
                        targetEntry -> {
                            String display = displayFormatResolver.resolve(targetDef, targetEntry.getValues());
                            resolved.put(col, new EntryReference(targetId, display));
                        },
                        () -> resolved.put(col, new EntryReference(targetId, targetId.toString()))
                );

            } else if (field.getFieldType() == FieldType.MIRROR) {
                String col = "cf_" + field.getFieldKey();
                MirrorFieldConfig mirrorConfig = (MirrorFieldConfig) field.getConfig();
                // Find source REFERENCE field on the source definition
                ContentDefinition sourceDef = definitionLoader.apply(mirrorConfig.sourceDefinitionId());
                String sourceCol = "cf_" + sourceField(sourceDef, mirrorConfig.sourceFieldId());
                if (sourceCol.equals("cf_null")) {
                    resolved.put(col, List.of());
                    continue;
                }
                List<ContentEntry> referencing = storage.findAll(schemaName, sourceDef, 200);
                List<EntryReference> items = referencing.stream()
                        .filter(e -> {
                            Object val = e.getValues().get(sourceCol);
                            if (val == null) return false;
                            UUID id = val instanceof UUID u ? u : UUID.fromString(val.toString());
                            return id.equals(entry.getId());
                        })
                        .map(e -> {
                            String display = displayFormatResolver.resolve(sourceDef, e.getValues());
                            return new EntryReference(e.getId(), display);
                        })
                        .toList();
                resolved.put(col, items);
            }
        }
        return resolved;
    }

    private String sourceField(ContentDefinition def, UUID sourceFieldId) {
        if (def.getFields() == null) return "null";
        return def.getFields().stream()
                .filter(f -> f.getId().equals(sourceFieldId))
                .map(FieldDefinition::getFieldKey)
                .findFirst().orElse("null");
    }
}
```

**Note:** `MirrorFieldConfig` must expose `sourceDefinitionId()` and `sourceFieldId()`. Verify the record signature:

```java
// Check: apps/backend/src/main/java/io/webcontify/content/definition/model/MirrorFieldConfig.java
public record MirrorFieldConfig(UUID sourceDefinitionId, UUID sourceFieldId) implements FieldConfig {}
```

Adjust field access names if the record uses different component names.

### Test

**File:** `apps/backend/src/test/java/io/webcontify/content/management/service/ReferenceResolverTest.java`

```java
package io.webcontify.content.management.service;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.model.EntryReference;
import io.webcontify.content.management.storage.ContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceResolverTest {

    @Mock ContentStorage storage;
    @Mock DisplayFormatResolver displayFormatResolver;

    private ReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ReferenceResolver(storage, displayFormatResolver);
    }

    @Test
    void resolveReferences_replacesUUIDWithEntryReference_forReferenceField() {
        UUID targetId = UUID.randomUUID();
        UUID targetDefId = UUID.randomUUID();

        var refField = referenceField("company", targetDefId);
        var sourceDef = definitionWith(UUID.randomUUID(), "person", refField);
        var targetDef = definitionWith(targetDefId, "company");

        var entry = new ContentEntry();
        entry.setId(UUID.randomUUID());
        entry.setValues(new LinkedHashMap<>(Map.of("cf_company", targetId)));

        var targetEntry = new ContentEntry();
        targetEntry.setId(targetId);
        targetEntry.setValues(Map.of("cf_name", "Acme"));

        when(storage.findById("tenant_test", targetDef, targetId))
                .thenReturn(Optional.of(targetEntry));
        when(displayFormatResolver.resolve(targetDef, targetEntry.getValues()))
                .thenReturn("Acme Corp");

        Map<String, Object> result = resolver.resolveReferences(
                "tenant_test", sourceDef, entry, id -> targetDef);

        assertThat(result.get("cf_company"))
                .isInstanceOf(EntryReference.class)
                .satisfies(r -> assertThat(((EntryReference) r).display()).isEqualTo("Acme Corp"));
    }

    private ContentDefinition definitionWith(UUID id, String slug, FieldDefinition... fields) {
        var def = new ContentDefinition();
        def.setId(id);
        def.setSlug(slug);
        def.setFields(List.of(fields));
        return def;
    }

    private FieldDefinition referenceField(String key, UUID targetDefinitionId) {
        var f = new FieldDefinition();
        f.setFieldKey(key);
        f.setFieldType(FieldType.REFERENCE);
        f.setConfig(new ReferenceFieldConfig(targetDefinitionId, null, null));
        return f;
    }
}
```

**Run:** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.management.service.ReferenceResolverTest`

**Commit:** `feat(content): add ReferenceResolver for REFERENCE and MIRROR field resolution`

---

## Task 4 — ContentEntryService core CRUD (without onDelete)

**File:** `apps/backend/src/main/java/io/webcontify/content/management/service/ContentEntryService.java`

```java
package io.webcontify.content.management.service;

import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.storage.ContentStorage;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ContentEntryService {

    private final ContentStorage storage;
    private final ContentValidationService validationService;
    private final ReferenceResolver referenceResolver;
    private final ContentDefinitionRepository definitionRepository;
    private final AppRepository appRepository;
    private final ContentDefinitionMapper definitionMapper;

    public ContentEntryService(
            ContentStorage storage,
            ContentValidationService validationService,
            ReferenceResolver referenceResolver,
            ContentDefinitionRepository definitionRepository,
            AppRepository appRepository,
            ContentDefinitionMapper definitionMapper) {
        this.storage = storage;
        this.validationService = validationService;
        this.referenceResolver = referenceResolver;
        this.definitionRepository = definitionRepository;
        this.appRepository = appRepository;
        this.definitionMapper = definitionMapper;
    }

    public ContentEntry create(String appSlug, String defSlug, Map<String, Object> rawValues) {
        var tenantInfo = TenantContext.getTenantInformation();
        String schemaName = schemaName(tenantInfo.slug());
        ContentDefinition definition = resolveDefinition(appSlug, defSlug);

        Map<String, Object> validValues = validationService.validateAndCastValues(definition, rawValues);
        validationService.validateUniqueness(schemaName, definition, validValues, null);
        validationService.validateReferenceTargets(schemaName, definition, validValues,
                id -> loadDefinitionById(id));

        UUID userId = TenantContext.getCurrentUserId();
        ContentEntry entry = storage.save(schemaName, definition, validValues, userId);
        return withResolvedReferences(schemaName, definition, entry);
    }

    @Transactional(readOnly = true)
    public ContentEntry findById(String appSlug, String defSlug, UUID entryId) {
        var tenantInfo = TenantContext.getTenantInformation();
        String schemaName = schemaName(tenantInfo.slug());
        ContentDefinition definition = resolveDefinition(appSlug, defSlug);

        ContentEntry entry = storage.findById(schemaName, definition, entryId)
                .orElseThrow(() -> new NotFoundException("content.entry.not_found"));
        return withResolvedReferences(schemaName, definition, entry);
    }

    @Transactional(readOnly = true)
    public List<ContentEntry> findAll(String appSlug, String defSlug, int limit) {
        var tenantInfo = TenantContext.getTenantInformation();
        String schemaName = schemaName(tenantInfo.slug());
        ContentDefinition definition = resolveDefinition(appSlug, defSlug);

        return storage.findAll(schemaName, definition, limit).stream()
                .map(e -> withResolvedReferences(schemaName, definition, e))
                .toList();
    }

    public ContentEntry update(String appSlug, String defSlug, UUID entryId, Map<String, Object> rawValues) {
        var tenantInfo = TenantContext.getTenantInformation();
        String schemaName = schemaName(tenantInfo.slug());
        ContentDefinition definition = resolveDefinition(appSlug, defSlug);

        // Verify entry exists
        storage.findById(schemaName, definition, entryId)
                .orElseThrow(() -> new NotFoundException("content.entry.not_found"));

        Map<String, Object> validValues = validationService.validateAndCastValues(definition, rawValues);
        validationService.validateUniqueness(schemaName, definition, validValues, entryId);
        validationService.validateReferenceTargets(schemaName, definition, validValues,
                id -> loadDefinitionById(id));

        UUID userId = TenantContext.getCurrentUserId();
        ContentEntry entry = storage.update(schemaName, definition, entryId, validValues, userId);
        return withResolvedReferences(schemaName, definition, entry);
    }

    public void delete(String appSlug, String defSlug, UUID entryId) {
        var tenantInfo = TenantContext.getTenantInformation();
        String schemaName = schemaName(tenantInfo.slug());
        ContentDefinition definition = resolveDefinition(appSlug, defSlug);

        storage.findById(schemaName, definition, entryId)
                .orElseThrow(() -> new NotFoundException("content.entry.not_found"));

        UUID userId = TenantContext.getCurrentUserId();
        storage.softDelete(schemaName, definition, entryId, userId);
    }

    // --- helpers ---

    private ContentEntry withResolvedReferences(
            String schemaName, ContentDefinition definition, ContentEntry entry) {
        Map<String, Object> resolved = referenceResolver.resolveReferences(
                schemaName, definition, entry, this::loadDefinitionById);
        entry.setValues(resolved);
        return entry;
    }

    private ContentDefinition resolveDefinition(String appSlug, String defSlug) {
        AppEntity app = appRepository.findBySlugAndAuditInfoDeletedAtIsNull(appSlug)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        ContentDefinitionEntity entity = definitionRepository
                .findBySlugAndAppIdAndAuditInfoDeletedAtIsNull(defSlug, app.getId())
                .orElseThrow(() -> new NotFoundException("content_definition.not_found"));
        ContentDefinition def = definitionMapper.toDomain(entity);
        // Populate fields (the mapper may load them separately or they are eager)
        return def;
    }

    private ContentDefinition loadDefinitionById(UUID id) {
        return definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(id)
                .map(definitionMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("content_definition.not_found"));
    }

    private String schemaName(String tenantSlug) {
        return "tenant_" + tenantSlug.replace("-", "_");
    }
}
```

**Note on repository:** `ContentDefinitionRepository` needs a method `findBySlugAndAppIdAndAuditInfoDeletedAtIsNull`. Add to the interface:

```java
Optional<ContentDefinitionEntity> findBySlugAndAppIdAndAuditInfoDeletedAtIsNull(String slug, UUID appId);
```

### Test

**File:** `apps/backend/src/test/java/io/webcontify/content/management/service/ContentEntryServiceTest.java`

```java
package io.webcontify.content.management.service;

import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.storage.ContentStorage;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentEntryServiceTest {

    @Mock ContentStorage storage;
    @Mock ContentValidationService validationService;
    @Mock ReferenceResolver referenceResolver;
    @Mock ContentDefinitionRepository definitionRepository;
    @Mock AppRepository appRepository;
    @Mock ContentDefinitionMapper definitionMapper;

    @InjectMocks ContentEntryService service;

    @Test
    void findById_throwsNotFound_whenEntryMissing() {
        mockDefinitionChain();
        when(storage.findById(anyString(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("my-app", "person", UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAll_delegatesToStorageAndReturnsResolvedEntries() {
        mockDefinitionChain();
        var entry = new ContentEntry();
        entry.setId(UUID.randomUUID());
        entry.setValues(new HashMap<>());
        when(storage.findAll(anyString(), any(), eq(50))).thenReturn(List.of(entry));
        when(referenceResolver.resolveReferences(anyString(), any(), eq(entry), any()))
                .thenReturn(entry.getValues());

        List<ContentEntry> result = service.findAll("my-app", "person", 50);

        assertThat(result).hasSize(1);
    }

    @Test
    void delete_throwsNotFound_whenEntryMissing() {
        mockDefinitionChain();
        when(storage.findById(anyString(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("my-app", "person", UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    private void mockDefinitionChain() {
        var app = new AppEntity();
        app.setId(UUID.randomUUID());
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app"))
                .thenReturn(Optional.of(app));

        var entity = new ContentDefinitionEntity();
        when(definitionRepository.findBySlugAndAppIdAndAuditInfoDeletedAtIsNull("person", app.getId()))
                .thenReturn(Optional.of(entity));

        var def = new ContentDefinition();
        def.setId(UUID.randomUUID());
        def.setFields(List.of());
        when(definitionMapper.toDomain(entity)).thenReturn(def);
    }
}
```

**Run:** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.management.service.ContentEntryServiceTest`

**Commit:** `feat(content): add ContentEntryService core CRUD orchestration`

---

## Task 5 — onDelete enforcement in ContentEntryService.delete

Add onDelete logic before `storage.softDelete`. This requires scanning all definitions in the same app for REFERENCE fields that point to the deleted entry's definition and checking existing values.

**Replace `delete` method in `ContentEntryService`:**

```java
public void delete(String appSlug, String defSlug, UUID entryId) {
    var tenantInfo = TenantContext.getTenantInformation();
    String schemaName = schemaName(tenantInfo.slug());
    ContentDefinition definition = resolveDefinition(appSlug, defSlug);

    storage.findById(schemaName, definition, entryId)
            .orElseThrow(() -> new NotFoundException("content.entry.not_found"));

    // Enforce onDelete policies for all REFERENCE fields pointing to this definition
    enforceOnDelete(schemaName, appSlug, definition.getId(), entryId);

    UUID userId = TenantContext.getCurrentUserId();
    storage.softDelete(schemaName, definition, entryId, userId);
}

private void enforceOnDelete(
        String schemaName, String appSlug, UUID targetDefinitionId, UUID deletedEntryId) {

    AppEntity app = appRepository.findBySlugAndAuditInfoDeletedAtIsNull(appSlug)
            .orElseThrow(() -> new NotFoundException("app.not_found"));

    List<ContentDefinitionEntity> allEntities =
            definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(app.getId());

    for (ContentDefinitionEntity refEntity : allEntities) {
        ContentDefinition refDef = definitionMapper.toDomain(refEntity);
        if (refDef.getFields() == null) continue;

        for (FieldDefinition field : refDef.getFields()) {
            if (field.getFieldType() != FieldType.REFERENCE) continue;
            ReferenceFieldConfig config = (ReferenceFieldConfig) field.getConfig();
            if (!targetDefinitionId.equals(config.targetDefinitionId())) continue;

            String colName = "cf_" + field.getFieldKey();
            OnDeleteStrategy strategy = config.onDelete() != null
                    ? config.onDelete() : OnDeleteStrategy.RESTRICT;

            switch (strategy) {
                case RESTRICT -> {
                    // Check if any row references deletedEntryId
                    boolean hasRef = hasReferenceToEntry(schemaName, refDef, colName, deletedEntryId);
                    if (hasRef) {
                        throw new ConflictException("content.entry.delete_restricted",
                                List.of(new FieldError(colName, "error.delete.restricted_by_reference")));
                    }
                }
                case SET_NULL -> {
                    // Update all referencing rows to set colName = null
                    nullifyReferences(schemaName, refDef, colName, deletedEntryId);
                }
                case CASCADE -> {
                    // Find all referencing entries and soft-delete them recursively
                    List<ContentEntry> refs = storage.findAll(schemaName, refDef, Integer.MAX_VALUE)
                            .stream()
                            .filter(e -> deletedEntryId.equals(rawUuid(e.getValues().get(colName))))
                            .toList();
                    UUID userId = TenantContext.getCurrentUserId();
                    for (ContentEntry ref : refs) {
                        // Recursive: enforce onDelete for the referencing entry too
                        enforceOnDelete(schemaName, appSlug, refDef.getId(), ref.getId());
                        storage.softDelete(schemaName, refDef, ref.getId(), userId);
                    }
                }
            }
        }
    }
}
```

**Add helpers to `ContentEntryService` (use `DSLContext` injected or delegate through `ContentStorage`):**

Since `DedicatedTableStorage` already holds the `DSLContext`, add two methods to `ContentStorage` interface for this purpose, or inject `DSLContext` directly in `ContentEntryService`. Simplest approach: add to `ContentStorage`:

```java
// ContentStorage.java additions:
boolean existsByFieldValue(String schemaName, ContentDefinition definition, String columnName, UUID value);
void nullifyField(String schemaName, ContentDefinition definition, String columnName, UUID value);
```

Implement in `DedicatedTableStorage`:

```java
@Override
public boolean existsByFieldValue(String schemaName, ContentDefinition definition,
        String columnName, UUID value) {
    String tableName = tableName(definition);
    return dsl.fetchExists(
            dsl.selectOne()
                    .from(table(name(schemaName, tableName)))
                    .where(field(name(columnName)).equal(value)
                            .and(field(name("deleted_at")).isNull())));
}

@Override
public void nullifyField(String schemaName, ContentDefinition definition,
        String columnName, UUID value) {
    String tableName = tableName(definition);
    dsl.update(table(name(schemaName, tableName)))
            .set(field(name(columnName)), (Object) null)
            .where(field(name(columnName)).equal(value)
                    .and(field(name("deleted_at")).isNull()))
            .execute();
}
```

Then update `enforceOnDelete` helper methods:

```java
private boolean hasReferenceToEntry(String schemaName, ContentDefinition def,
        String colName, UUID entryId) {
    return storage.existsByFieldValue(schemaName, def, colName, entryId);
}

private void nullifyReferences(String schemaName, ContentDefinition def,
        String colName, UUID entryId) {
    storage.nullifyField(schemaName, def, colName, entryId);
}

private UUID rawUuid(Object val) {
    if (val == null) return null;
    if (val instanceof UUID u) return u;
    if (val instanceof EntryReference r) return r.id();
    try { return UUID.fromString(val.toString()); } catch (Exception e) { return null; }
}
```

Also add `import io.webcontify.content.definition.model.*` and exception imports.

### Test

**File:** `apps/backend/src/test/java/io/webcontify/content/management/service/ContentEntryServiceDeleteTest.java`

```java
package io.webcontify.content.management.service;

import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.storage.ContentStorage;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentEntryServiceDeleteTest {

    @Mock ContentStorage storage;
    @Mock ContentValidationService validationService;
    @Mock ReferenceResolver referenceResolver;
    @Mock ContentDefinitionRepository definitionRepository;
    @Mock AppRepository appRepository;
    @Mock ContentDefinitionMapper definitionMapper;

    @InjectMocks ContentEntryService service;

    @Test
    void delete_throwsConflict_whenRestrictedByReference() {
        UUID appId = UUID.randomUUID();
        UUID defId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        UUID refDefId = UUID.randomUUID();

        mockApp("my-app", appId);

        // Target definition
        var targetEntity = new ContentDefinitionEntity();
        var targetDef = new ContentDefinition();
        targetDef.setId(defId);
        targetDef.setSlug("person");
        targetDef.setFields(List.of());
        when(definitionRepository.findBySlugAndAppIdAndAuditInfoDeletedAtIsNull("person", appId))
                .thenReturn(Optional.of(targetEntity));
        when(definitionMapper.toDomain(targetEntity)).thenReturn(targetDef);

        // Entry exists
        var entry = new ContentEntry();
        entry.setId(entryId);
        entry.setValues(new HashMap<>());
        when(storage.findById(anyString(), eq(targetDef), eq(entryId)))
                .thenReturn(Optional.of(entry));

        // Referencing definition with RESTRICT field
        var refEntity = new ContentDefinitionEntity();
        var refField = new FieldDefinition();
        refField.setFieldKey("person");
        refField.setFieldType(FieldType.REFERENCE);
        refField.setConfig(new ReferenceFieldConfig(defId, null, OnDeleteStrategy.RESTRICT));
        var refDef = new ContentDefinition();
        refDef.setId(refDefId);
        refDef.setSlug("order");
        refDef.setFields(List.of(refField));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId))
                .thenReturn(List.of(targetEntity, refEntity));
        when(definitionMapper.toDomain(refEntity)).thenReturn(refDef);

        when(storage.existsByFieldValue(anyString(), eq(refDef), eq("cf_person"), eq(entryId)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.delete("my-app", "person", entryId))
                .isInstanceOf(ConflictException.class);
    }

    private void mockApp(String slug, UUID id) {
        var app = new AppEntity();
        app.setId(id);
        when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull(slug))
                .thenReturn(Optional.of(app));
    }
}
```

**Run:** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.management.service.ContentEntryServiceDeleteTest`

**Commit:** `feat(content): add onDelete enforcement (RESTRICT/SET_NULL/CASCADE) to ContentEntryService`

---

## Task 6 — OpenAPI spec update + regen

**File:** `openapi/content-api.yaml`

Add the following at the end of `tags`:

```yaml
  - name: ContentEntry
    description: Content Entry Management
```

Add these paths after the existing field paths:

```yaml
  /api/v1/content/{appSlug}/definitions/{defSlug}/entries:
    post:
      operationId: createContentEntry
      summary: Create a new content entry
      tags:
        - ContentEntry
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefSlugPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateContentEntryRequest'
      responses:
        '201':
          description: Entry created
          headers:
            Location:
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentEntryResponse'
        '400':
          $ref: '#/components/responses/ValidationError'
        '409':
          $ref: '#/components/responses/ConflictError'

    get:
      operationId: listContentEntries
      summary: List content entries
      tags:
        - ContentEntry
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefSlugPath'
        - name: limit
          in: query
          schema:
            type: integer
            default: 50
            minimum: 1
            maximum: 200
      responses:
        '200':
          description: Entry list
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentEntryListResponse'

  /api/v1/content/{appSlug}/definitions/{defSlug}/entries/{entryId}:
    get:
      operationId: getContentEntry
      summary: Get a content entry by ID
      tags:
        - ContentEntry
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefSlugPath'
        - $ref: '#/components/parameters/EntryIdPath'
      responses:
        '200':
          description: Entry detail
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentEntryResponse'
        '404':
          $ref: '#/components/responses/NotFoundError'

    put:
      operationId: updateContentEntry
      summary: Update a content entry
      tags:
        - ContentEntry
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefSlugPath'
        - $ref: '#/components/parameters/EntryIdPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateContentEntryRequest'
      responses:
        '200':
          description: Entry updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentEntryResponse'
        '400':
          $ref: '#/components/responses/ValidationError'
        '404':
          $ref: '#/components/responses/NotFoundError'

    delete:
      operationId: deleteContentEntry
      summary: Delete a content entry
      tags:
        - ContentEntry
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefSlugPath'
        - $ref: '#/components/parameters/EntryIdPath'
      responses:
        '204':
          description: Entry deleted
        '404':
          $ref: '#/components/responses/NotFoundError'
        '409':
          $ref: '#/components/responses/ConflictError'
```

Add these **parameters** to `components/parameters` (if not already present):

```yaml
    DefSlugPath:
      name: defSlug
      in: path
      required: true
      schema:
        type: string

    EntryIdPath:
      name: entryId
      in: path
      required: true
      schema:
        type: string
        format: uuid
```

Add these **schemas** to `components/schemas`:

```yaml
    CreateContentEntryRequest:
      type: object
      required:
        - values
      properties:
        values:
          type: object
          additionalProperties: true

    UpdateContentEntryRequest:
      type: object
      required:
        - values
      properties:
        values:
          type: object
          additionalProperties: true

    ContentEntryResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        definitionId:
          type: string
          format: uuid
        values:
          type: object
          additionalProperties: true
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time
        createdBy:
          type: string
        updatedBy:
          type: string

    ContentEntryListResponse:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/ContentEntryResponse'
```

Add **responses** shortcuts to `components/responses` if not already there:

```yaml
    ValidationError:
      description: Validation error
      content:
        application/json:
          schema:
            $ref: './common-schemas.yaml#/components/schemas/ValidationProblemDetail'
    ConflictError:
      description: Conflict
      content:
        application/json:
          schema:
            $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
    NotFoundError:
      description: Not found
      content:
        application/json:
          schema:
            $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
```

**Regenerate:**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
```

Verify `ContentEntryApi` interface is generated in `build/generated-sources/`.

**Commit:** `feat(content): add ContentEntry endpoints to OpenAPI spec and regenerate`

---

## Task 7 — ContentEntryMapper + ContentEntryController

### Mapper

**File:** `apps/backend/src/main/java/io/webcontify/content/management/mapper/ContentEntryMapper.java`

```java
package io.webcontify.content.management.mapper;

import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.generated.model.ContentEntryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface ContentEntryMapper {

    @Mapping(source = "createdBy", target = "createdBy", qualifiedByName = "uuidToString")
    @Mapping(source = "updatedBy", target = "updatedBy", qualifiedByName = "uuidToString")
    ContentEntryResponse toResponse(ContentEntry entry);

    List<ContentEntryResponse> toResponseList(List<ContentEntry> entries);

    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }
}
```

### Controller

**File:** `apps/backend/src/main/java/io/webcontify/content/management/api/ContentEntryController.java`

```java
package io.webcontify.content.management.api;

import io.webcontify.content.management.mapper.ContentEntryMapper;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.service.ContentEntryService;
import io.webcontify.generated.api.ContentEntryApi;
import io.webcontify.generated.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class ContentEntryController implements ContentEntryApi {

    private final ContentEntryService entryService;
    private final ContentEntryMapper entryMapper;

    public ContentEntryController(ContentEntryService entryService, ContentEntryMapper entryMapper) {
        this.entryService = entryService;
        this.entryMapper = entryMapper;
    }

    @Override
    public ResponseEntity<ContentEntryResponse> createContentEntry(
            String appSlug, String defSlug, CreateContentEntryRequest request) {
        ContentEntry entry = entryService.create(appSlug, defSlug, request.getValues());
        ContentEntryResponse response = entryMapper.toResponse(entry);
        return ResponseEntity
                .created(URI.create("/api/v1/content/" + appSlug + "/definitions/"
                        + defSlug + "/entries/" + entry.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<ContentEntryListResponse> listContentEntries(
            String appSlug, String defSlug, Integer limit) {
        int effectiveLimit = limit != null ? limit : 50;
        List<ContentEntry> entries = entryService.findAll(appSlug, defSlug, effectiveLimit);
        ContentEntryListResponse response = new ContentEntryListResponse()
                .items(entryMapper.toResponseList(entries));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ContentEntryResponse> getContentEntry(
            String appSlug, String defSlug, UUID entryId) {
        ContentEntry entry = entryService.findById(appSlug, defSlug, entryId);
        return ResponseEntity.ok(entryMapper.toResponse(entry));
    }

    @Override
    public ResponseEntity<ContentEntryResponse> updateContentEntry(
            String appSlug, String defSlug, UUID entryId, UpdateContentEntryRequest request) {
        ContentEntry entry = entryService.update(appSlug, defSlug, entryId, request.getValues());
        return ResponseEntity.ok(entryMapper.toResponse(entry));
    }

    @Override
    public ResponseEntity<Void> deleteContentEntry(
            String appSlug, String defSlug, UUID entryId) {
        entryService.delete(appSlug, defSlug, entryId);
        return ResponseEntity.noContent().build();
    }
}
```

**Permissions note:** Add `@RequiresPermission` at controller method level using SpEL on `definitionId`. Since we resolve definition by slug at service level, the simplest approach is to check at service level using the pattern already established in `ContentDefinitionService` (via `permissionService.check`). Add to `ContentEntryService.create/update/delete`:

```java
// At start of create/findById/update/delete, after resolving definition:
requireDefinitionPermission(definition.getId(), "editor"); // or "viewer" for reads
```

```java
private void requireDefinitionPermission(UUID definitionId, String action) {
    var tenantInfo = TenantContext.getTenantInformation();
    if (tenantInfo.platformAdmin()) return;
    boolean allowed = permissionService.check(new CheckPermissionRequest(
            TenantContext.getCurrentUserId(), action, "content_definition", definitionId));
    if (!allowed) throw new AccessDeniedException("Insufficient permissions");
}
```

Add `PermissionService` to `ContentEntryService` constructor.

### Test

**File:** `apps/backend/src/test/java/io/webcontify/content/management/api/ContentEntryControllerTest.java`

```java
package io.webcontify.content.management.api;

import io.webcontify.content.management.mapper.ContentEntryMapper;
import io.webcontify.content.management.model.ContentEntry;
import io.webcontify.content.management.service.ContentEntryService;
import io.webcontify.generated.model.ContentEntryResponse;
import io.webcontify.generated.model.CreateContentEntryRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentEntryControllerTest {

    @Mock ContentEntryService entryService;
    @Mock ContentEntryMapper entryMapper;

    @InjectMocks ContentEntryController controller;

    @Test
    void createContentEntry_returns201WithLocation() {
        UUID id = UUID.randomUUID();
        var entry = new ContentEntry();
        entry.setId(id);
        entry.setValues(Map.of());

        var request = new CreateContentEntryRequest().values(Map.of("cf_name", "Alice"));
        when(entryService.create("my-app", "person", Map.of("cf_name", "Alice")))
                .thenReturn(entry);
        when(entryMapper.toResponse(entry)).thenReturn(new ContentEntryResponse().id(id));

        var response = controller.createContentEntry("my-app", "person", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation().toString())
                .contains(id.toString());
    }

    @Test
    void deleteContentEntry_returns204() {
        var response = controller.deleteContentEntry("my-app", "person", UUID.randomUUID());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
```

**Run:** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.management.api.ContentEntryControllerTest`

**Compile check (all tests):** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

**Commit:** `feat(content): add ContentEntryMapper and ContentEntryController`

---

## Task 8 — Integration test skeleton

This test verifies the full stack with a live Postgres container. It can be skipped in CI without Docker.

**File:** `apps/backend/src/test/java/io/webcontify/content/management/integration/ContentEntryIntegrationTest.java`

```java
package io.webcontify.content.management.integration;

import io.webcontify.content.definition.model.*;
import io.webcontify.content.management.service.ContentEntryService;
import io.webcontify.content.management.storage.ContentStorage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test for ContentEntryService.
 * Requires Docker. Skip with @Disabled when running without Docker.
 */
@Disabled("Requires Docker")
@Testcontainers
@SpringBootTest
class ContentEntryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("webcontify_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    ContentEntryService entryService;

    @Test
    void createAndRetrieveEntry_roundTrip() {
        // Seed: create a tenant schema + content definition table manually or via service
        // Then call entryService.create and verify findById returns the same entry.
        // Implementation depends on test data setup — expand when test infrastructure is ready.
        assertThat(entryService).isNotNull();
    }
}
```

**Run (Docker required):** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.management.integration.ContentEntryIntegrationTest`

**Commit:** `test(content): add ContentEntry integration test skeleton`

---

## Summary of changes

| File | Action |
|------|--------|
| `content/management/service/ContentValidationService.java` | new |
| `content/management/service/ContentEntryService.java` | new |
| `content/management/service/ReferenceResolver.java` | new |
| `content/management/mapper/ContentEntryMapper.java` | new |
| `content/management/api/ContentEntryController.java` | new |
| `content/management/storage/ContentStorage.java` | extend: +existsByFieldValue, +nullifyField |
| `content/management/storage/DedicatedTableStorage.java` | implement new methods |
| `content/definition/repository/ContentDefinitionRepository.java` | extend: +findBySlugAndAppId |
| `openapi/content-api.yaml` | add 5 entry endpoints + 4 schemas + parameters |

## Conventions

- Error keys: `content.entry.not_found`, `content.entry.validation_failed`, `content.entry.duplicate_value`, `content.entry.delete_restricted`
- Field error keys: `error.value.required`, `error.value.not_unique`, `error.reference.target_not_found`, `error.delete.restricted_by_reference`
- Schema naming: `tenant_<slug_underscored>`, table naming: `cd_<def_slug_underscored>`
- Column prefix: `cf_<fieldKey>`
- Permission check: service-level via `PermissionService.check` + `platformAdmin` fast-path
