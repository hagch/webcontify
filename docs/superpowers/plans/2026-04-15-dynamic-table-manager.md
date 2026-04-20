# Dynamic Table Manager + DDL Diff Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox syntax.

**Goal:** Create, alter, and drop dedicated PostgreSQL tables per content definition using jOOQ. Execute DDL at App Publish time based on `pending_action` flags. Update flags after successful DDL application.

**Architecture:** `DynamicTableManager` uses jOOQ's `DSL.table`/`DSL.field` for dynamic identifiers. `DdlDiffEngine` reads `pending_action` flags from `content_field_definition` and produces `List<DdlChange>`. `ContentDdlListener` listens to `AppPublishedEvent`, invokes engine + manager, updates flags, logs changes. All tables in `tenant_{slug}` schema via search_path (already configured in Phase 5b). Composite indexes `(cf_{key}, id)` created for sortable fields.

**Tech Stack:** Java 21, Spring Boot 4, jOOQ 3.x, PostgreSQL 17, Flyway, Testcontainers

**Package:** `io.webcontify.content.definition.storage` — sits inside the `content` module which already allows `app` as a dependency (see `package-info.java`). Listener reacts to `AppPublishedEvent` from the `app` module.

**Key model facts from codebase reading:**
- `FieldDefinition` does NOT have `published` or `pendingAction` fields — those live only on `ContentFieldEntity`. The listener must work directly with the entity layer or a dedicated DTO that carries these flags.
- `ContentDefinitionEntity.getFields()` uses `@SQLRestriction("deleted_at IS NULL")` — so soft-deleted fields (REMOVED candidates) do NOT appear on the entity's field collection. We need a separate repository query to fetch fields with `pending_action = 'REMOVED'` (including soft-deleted rows).
- `ContentFieldRepository` already has `findByDefinitionIdAndAuditInfoDeletedAtIsNullOrderByPositionAsc` — we need an additional query for pending-REMOVED fields.
- `TenantSlugResolver` is in `shared.security` package — already accessible from `content` module.
- `FieldDdlProvider.columnType` currently returns `String` — must change to `org.jooq.DataType<?>`.
- `FieldTypeHandlerContractTest` tests the `generateDdl` default method — keep that test. We also keep `generateDdl` default on `FieldDdlProvider` as it may be used later (or remove if no usages — verify during Task 4).

---

## Tasks (9 total, sequential, all TDD)

---

### Task 1: Flyway migration — `content_field_change_log` table + JPA entity

- [ ] Create migration file `apps/backend/src/main/resources/db/migration/V20260415_002__create_content_field_change_log.sql`
- [ ] Create JPA entity `ContentFieldChangeLogEntity.java` in `io.webcontify.content.definition.repository.entity`
- [ ] Create Spring Data repository `ContentFieldChangeLogRepository.java` in `io.webcontify.content.definition.repository`
- [ ] Write `@DataJpaTest` smoke test verifying save + read round-trip

**Migration SQL:**

```sql
CREATE TABLE content_field_change_log (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    definition_id   UUID NOT NULL REFERENCES content_definition(id),
    field_key       VARCHAR(255) NOT NULL,
    action          VARCHAR(20) NOT NULL,
    old_field_type  VARCHAR(50),
    old_config      JSONB,
    new_field_type  VARCHAR(50),
    new_config      JSONB,
    data_loss       BOOLEAN NOT NULL DEFAULT false,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_by      UUID
);

CREATE INDEX idx_cfcl_definition_id ON content_field_change_log(definition_id);
CREATE INDEX idx_cfcl_applied_at ON content_field_change_log(applied_at DESC);

ALTER TABLE content_field_change_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON content_field_change_log
    USING (
        CASE
            WHEN current_setting('app.tenant_id', true) IS NULL OR current_setting('app.tenant_id', true) = ''
                THEN false
            ELSE tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID
        END
    );
```

**Entity `ContentFieldChangeLogEntity.java`:**

```java
package io.webcontify.content.definition.repository.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "content_field_change_log")
public class ContentFieldChangeLogEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "old_field_type")
    private String oldFieldType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_config", columnDefinition = "jsonb")
    private String oldConfig;

    @Column(name = "new_field_type")
    private String newFieldType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_config", columnDefinition = "jsonb")
    private String newConfig;

    @Column(name = "data_loss", nullable = false)
    private boolean dataLoss;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt;

    @Column(name = "applied_by")
    private UUID appliedBy;

    // getters + setters (no Lombok)
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOldFieldType() { return oldFieldType; }
    public void setOldFieldType(String oldFieldType) { this.oldFieldType = oldFieldType; }
    public String getOldConfig() { return oldConfig; }
    public void setOldConfig(String oldConfig) { this.oldConfig = oldConfig; }
    public String getNewFieldType() { return newFieldType; }
    public void setNewFieldType(String newFieldType) { this.newFieldType = newFieldType; }
    public String getNewConfig() { return newConfig; }
    public void setNewConfig(String newConfig) { this.newConfig = newConfig; }
    public boolean isDataLoss() { return dataLoss; }
    public void setDataLoss(boolean dataLoss) { this.dataLoss = dataLoss; }
    public OffsetDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(OffsetDateTime appliedAt) { this.appliedAt = appliedAt; }
    public UUID getAppliedBy() { return appliedBy; }
    public void setAppliedBy(UUID appliedBy) { this.appliedBy = appliedBy; }
}
```

**Repository `ContentFieldChangeLogRepository.java`:**

```java
package io.webcontify.content.definition.repository;

import io.webcontify.content.definition.repository.entity.ContentFieldChangeLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentFieldChangeLogRepository
        extends JpaRepository<ContentFieldChangeLogEntity, UUID> {

    List<ContentFieldChangeLogEntity> findByDefinitionIdOrderByAppliedAtDesc(UUID definitionId);
}
```

**Smoke test (uses inline Testcontainers, matching `ContentDefinitionControllerIntegrationTest` pattern):**

```java
package io.webcontify.content.definition.repository;

import io.webcontify.content.definition.repository.entity.ContentFieldChangeLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ContentFieldChangeLogRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ContentFieldChangeLogRepository repository;

    @Test
    void save_andFindByDefinitionId_works() {
        UUID definitionId = UUID.randomUUID();
        ContentFieldChangeLogEntity entry = new ContentFieldChangeLogEntity();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(UUID.randomUUID());
        entry.setDefinitionId(definitionId);
        entry.setFieldKey("title");
        entry.setAction("ADDED");
        entry.setNewFieldType("TEXT");
        entry.setDataLoss(false);
        entry.setAppliedAt(OffsetDateTime.now());

        repository.save(entry);

        var results = repository.findByDefinitionIdOrderByAppliedAtDesc(definitionId);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFieldKey()).isEqualTo("title");
    }
}
```

**Commit:** `feat(content): add content_field_change_log for DDL audit trail`

---

### Task 2: Add query for REMOVED (soft-deleted) fields to `ContentFieldRepository`

> This task is a prerequisite for DdlDiffEngine. Fields with `pending_action = 'REMOVED'` are soft-deleted (have `deleted_at` set), so `@SQLRestriction` on the entity hides them. We need a native query.

- [ ] Add `findPendingRemovedByDefinitionId` native query to `ContentFieldRepository`
- [ ] Unit test: verify query annotation is syntactically correct (integration test in Task 8 will cover functional behavior)

**Add to `ContentFieldRepository`:**

```java
@Query(
    value = "SELECT * FROM content_field_definition f "
          + "WHERE f.definition_id = :definitionId "
          + "AND f.pending_action = 'REMOVED' "
          + "AND f.deleted_at IS NOT NULL",
    nativeQuery = true)
List<ContentFieldEntity> findPendingRemovedByDefinitionId(@Param("definitionId") UUID definitionId);
```

Add import: `import org.springframework.data.repository.query.Param;`

**Commit:** `feat(content): add findPendingRemovedByDefinitionId query to ContentFieldRepository`

---

### Task 3: Verify jOOQ DSLContext auto-configuration

- [ ] Create integration test `JooqAutoConfigTest.java` in `io.webcontify.content.definition.storage` (new test package, matching source package under `content`)
- [ ] Test injects `DSLContext` and runs `SELECT 1`
- [ ] Run test: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.storage.JooqAutoConfigTest`

**File:** `apps/backend/src/test/java/io/webcontify/content/definition/storage/JooqAutoConfigTest.java`

```java
package io.webcontify.content.definition.storage;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.inline;

@SpringBootTest
@Testcontainers
class JooqAutoConfigTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DSLContext dslContext;

    @Test
    void dslContextIsAutoConfigured() {
        assertThat(dslContext).isNotNull();
        Integer one = dslContext.select(inline(1)).fetchOneInto(Integer.class);
        assertThat(one).isEqualTo(1);
    }
}
```

**Commit:** `test(content): verify jOOQ DSLContext auto-configuration`

---

### Task 4: Change `FieldDdlProvider.columnType` return type + implement in all handlers

> **Breaking change on `FieldDdlProvider`:** `columnType` currently returns `String`. Change to `org.jooq.DataType<?>`. `generateDdl` is no longer needed now that we use jOOQ builders — remove it from the interface (check zero usages first: `grep -r "generateDdl" apps/backend/src`). Update `FieldTypeHandlerContractTest` accordingly.

- [ ] Verify `generateDdl` has no usages outside of `FieldDdlProvider` and `FieldTypeHandlerContractTest` — if so, remove from interface
- [ ] Update `FieldDdlProvider`: change `columnType` return type to `DataType<?>`, remove `generateDdl`
- [ ] Update `FieldTypeHandlerContractTest`: remove the `generateDdl` assertion; update `columnType` assertion to verify it still throws `UnsupportedOperationException` (anonymous impl does not override)
- [ ] Implement `columnType` in each non-virtual handler
- [ ] Add per-handler unit tests for `columnType`

**Updated `FieldDdlProvider.java`:**

```java
package io.webcontify.content.fieldtype;

import io.webcontify.content.definition.model.FieldConfig;
import org.jooq.DataType;

public interface FieldDdlProvider {

    default DataType<?> columnType(FieldConfig config) {
        throw new UnsupportedOperationException("DDL not implemented for this field type");
    }
}
```

**Handler implementations:**

`TextFieldTypeHandler`:
```java
@Override
public DataType<?> columnType(FieldConfig config) {
    if (config instanceof TextFieldConfig c && c.maxLength() != null) {
        return SQLDataType.VARCHAR(c.maxLength());
    }
    return SQLDataType.VARCHAR(4000);
}
```

`IntegerFieldTypeHandler`:
```java
@Override
public DataType<?> columnType(FieldConfig config) {
    return SQLDataType.BIGINT;
}
```

`DecimalFieldTypeHandler`:
```java
@Override
public DataType<?> columnType(FieldConfig config) {
    if (config instanceof DecimalFieldConfig c) {
        int precision = c.precision() != null ? c.precision() : 19;
        int scale = c.scale() != null ? c.scale() : 4;
        return SQLDataType.NUMERIC(precision, scale);
    }
    return SQLDataType.NUMERIC(19, 4);
}
```

`BooleanFieldTypeHandler`:
```java
@Override
public DataType<?> columnType(FieldConfig config) {
    return SQLDataType.BOOLEAN;
}
```

`DateFieldTypeHandler`:
```java
@Override
public DataType<?> columnType(FieldConfig config) {
    return SQLDataType.TIMESTAMPWITHTIMEZONE;
}
```

`EnumFieldTypeHandler`:
```java
@Override
public DataType<?> columnType(FieldConfig config) {
    return SQLDataType.VARCHAR(255);
}
```

`ReferenceFieldTypeHandler`:
```java
@Override
public DataType<?> columnType(FieldConfig config) {
    return SQLDataType.UUID;
}
```

`MirrorFieldTypeHandler`: inherits the default (throws `UnsupportedOperationException`) — no override needed since `isVirtual()` returns `true` and callers skip virtual handlers.

**Required imports in each handler:**
```java
import static org.jooq.impl.SQLDataType.*;
import org.jooq.DataType;
```

**Updated contract test:**
```java
@Test
void ddlProvider_columnType_defaultThrowsUnsupported() {
    FieldDdlProvider provider = new FieldDdlProvider() {};
    assertThatThrownBy(() -> provider.columnType(null))
            .isInstanceOf(UnsupportedOperationException.class);
}

@Test
void fieldTypeHandler_extendsAllConcerns() {
    assertThat(FieldTypeHandler.class.getInterfaces())
            .contains(FieldDdlProvider.class, FieldValueHandler.class, FieldQueryBuilder.class);
}
```

**Per-handler unit test example (`TextFieldTypeHandlerTest` addition):**
```java
@Test
void columnType_withMaxLength_returnsVarcharWithLimit() {
    TextFieldConfig config = new TextFieldConfig(null, 200, null, null);
    DataType<?> type = handler.columnType(config);
    assertThat(type).isEqualTo(SQLDataType.VARCHAR(200));
}

@Test
void columnType_withoutMaxLength_returnsVarchar4000() {
    DataType<?> type = handler.columnType(null);
    assertThat(type).isEqualTo(SQLDataType.VARCHAR(4000));
}
```

Add equivalent focused tests in each handler's test class.

**Commit:** `feat(content): add columnType(DataType) to FieldTypeHandlers for DDL generation`

---

### Task 5: Define `DdlAction` + `DdlChange` + `DdlDiffEngine`

- [ ] Create `DdlAction.java` enum
- [ ] Create `DdlChange.java` record
- [ ] Create `DdlDiffEngine.java` Spring component
- [ ] Unit tests for `DdlDiffEngine`

All files in package `io.webcontify.content.definition.storage`.

**`DdlAction.java`:**

```java
package io.webcontify.content.definition.storage;

public enum DdlAction {
    CREATE_TABLE,
    DROP_TABLE,
    ADD_COLUMN,
    DROP_COLUMN
}
```

**`DdlChange.java`:**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.content.definition.repository.entity.ContentFieldEntity;

public record DdlChange(DdlAction action, ContentFieldEntity field) {}
```

> Note: Uses `ContentFieldEntity` directly (not domain model) because `pending_action` and `published` flags are entity-level concerns. The `field` is `null` for `CREATE_TABLE` and `DROP_TABLE`.

**`DdlDiffEngine.java`:**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DdlDiffEngine {

    private final ContentFieldRepository fieldRepository;

    public DdlDiffEngine(ContentFieldRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    /**
     * Computes the DDL changes needed for the given definition.
     *
     * @param definition  the content definition entity
     * @param tableExists whether the physical table already exists in the tenant schema
     * @return ordered list of DDL changes to apply
     */
    public List<DdlChange> computeDiff(ContentDefinitionEntity definition, boolean tableExists) {
        if (!tableExists) {
            // Full table creation — no per-field entries needed here,
            // DynamicTableManager.createTable reads definition.getFields() directly
            return List.of(new DdlChange(DdlAction.CREATE_TABLE, null));
        }

        List<DdlChange> changes = new ArrayList<>();

        // ADDED: live fields with pending_action = 'ADDED'
        for (ContentFieldEntity field : definition.getFields()) {
            if ("ADDED".equals(field.getPendingAction())) {
                changes.add(new DdlChange(DdlAction.ADD_COLUMN, field));
            }
        }

        // REMOVED: soft-deleted fields with pending_action = 'REMOVED'
        List<ContentFieldEntity> removedFields =
                fieldRepository.findPendingRemovedByDefinitionId(definition.getId());
        for (ContentFieldEntity field : removedFields) {
            changes.add(new DdlChange(DdlAction.DROP_COLUMN, field));
        }

        return changes;
    }
}
```

**Unit tests `DdlDiffEngineTest.java`:**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DdlDiffEngineTest {

    @Mock
    ContentFieldRepository fieldRepository;

    @InjectMocks
    DdlDiffEngine engine;

    private ContentDefinitionEntity definition;

    @BeforeEach
    void setUp() {
        definition = new ContentDefinitionEntity();
        definition.setId(UUID.randomUUID());
    }

    @Test
    void computeDiff_tableNotExists_returnsCreateTable() {
        when(fieldRepository.findPendingRemovedByDefinitionId(any())).thenReturn(List.of());

        List<DdlChange> changes = engine.computeDiff(definition, false);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).action()).isEqualTo(DdlAction.CREATE_TABLE);
        assertThat(changes.get(0).field()).isNull();
    }

    @Test
    void computeDiff_tableExists_noChanges_returnsEmpty() {
        definition.setFields(List.of());
        when(fieldRepository.findPendingRemovedByDefinitionId(definition.getId()))
                .thenReturn(List.of());

        List<DdlChange> changes = engine.computeDiff(definition, true);

        assertThat(changes).isEmpty();
    }

    @Test
    void computeDiff_tableExists_pendingAdded_returnsAddColumn() {
        ContentFieldEntity addedField = fieldWithPendingAction("ADDED");
        definition.setFields(List.of(addedField));
        when(fieldRepository.findPendingRemovedByDefinitionId(definition.getId()))
                .thenReturn(List.of());

        List<DdlChange> changes = engine.computeDiff(definition, true);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).action()).isEqualTo(DdlAction.ADD_COLUMN);
        assertThat(changes.get(0).field()).isEqualTo(addedField);
    }

    @Test
    void computeDiff_tableExists_pendingRemoved_returnsDropColumn() {
        definition.setFields(List.of());
        ContentFieldEntity removedField = fieldWithPendingAction("REMOVED");
        when(fieldRepository.findPendingRemovedByDefinitionId(definition.getId()))
                .thenReturn(List.of(removedField));

        List<DdlChange> changes = engine.computeDiff(definition, true);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).action()).isEqualTo(DdlAction.DROP_COLUMN);
        assertThat(changes.get(0).field()).isEqualTo(removedField);
    }

    @Test
    void computeDiff_tableExists_mixedChanges_returnsAll() {
        ContentFieldEntity addedField = fieldWithPendingAction("ADDED");
        ContentFieldEntity publishedField = fieldWithPendingAction(null);
        definition.setFields(List.of(addedField, publishedField));
        ContentFieldEntity removedField = fieldWithPendingAction("REMOVED");
        when(fieldRepository.findPendingRemovedByDefinitionId(definition.getId()))
                .thenReturn(List.of(removedField));

        List<DdlChange> changes = engine.computeDiff(definition, true);

        assertThat(changes).hasSize(2);
        assertThat(changes).extracting(DdlChange::action)
                .containsExactlyInAnyOrder(DdlAction.ADD_COLUMN, DdlAction.DROP_COLUMN);
    }

    private ContentFieldEntity fieldWithPendingAction(String pendingAction) {
        ContentFieldEntity field = new ContentFieldEntity();
        field.setId(UUID.randomUUID());
        field.setFieldKey("test_field_" + UUID.randomUUID().toString().substring(0, 8));
        field.setFieldType("TEXT");
        field.setPendingAction(pendingAction);
        return field;
    }
}
```

**Commit:** `feat(content): add DdlDiffEngine for field change detection`

---

### Task 6: `DynamicTableManager.createTable`

- [ ] Create `DynamicTableManager.java` in `io.webcontify.content.definition.storage`
- [ ] Implement `createTable(String schemaName, ContentDefinitionEntity definition)`
- [ ] Integration test with Testcontainers: create schema, call `createTable`, verify via `information_schema.columns`

**`DynamicTableManager.java` (createTable portion):**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.content.fieldtype.FieldTypeHandler;
import io.webcontify.content.fieldtype.FieldTypeRegistry;
import io.webcontify.content.definition.model.FieldType;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static org.jooq.impl.DSL.*;
import static org.jooq.impl.SQLDataType.*;

@Component
public class DynamicTableManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicTableManager.class);

    private final DSLContext dsl;
    private final FieldTypeRegistry registry;

    public DynamicTableManager(DSLContext dsl, FieldTypeRegistry registry) {
        this.dsl = dsl;
        this.registry = registry;
    }

    @Transactional
    public void createTable(String schemaName, ContentDefinitionEntity definition) {
        String tableName = "cd_" + definition.getSlug().replace("-", "_");
        log.info("Creating table {}.{} for definition id={}", schemaName, tableName, definition.getId());

        dsl.execute("SET LOCAL lock_timeout = '5s'");

        var step = dsl.createTable(name(schemaName, tableName))
                .column(name("id"), UUID.nullable(false))
                .column(name("created_at"), TIMESTAMPWITHTIMEZONE.nullable(false).defaultValue(currentTimestamp()))
                .column(name("updated_at"), TIMESTAMPWITHTIMEZONE.nullable(false).defaultValue(currentTimestamp()))
                .column(name("created_by"), UUID)
                .column(name("updated_by"), UUID)
                .column(name("deleted_at"), TIMESTAMPWITHTIMEZONE)
                .column(name("deleted_by"), UUID);

        for (ContentFieldEntity field : definition.getFields()) {
            FieldTypeHandler handler = registry.get(FieldType.valueOf(field.getFieldType()));
            if (handler.isVirtual()) continue;

            String columnName = "cf_" + field.getFieldKey();
            DataType<?> type = handler.columnType(
                    // config is stored as JSON string on entity; no domain model needed here
                    // pass null — handlers use config only for optional constraints (e.g. TEXT maxLength)
                    // For createTable, fields are ADDED so config is available via field.getConfig()
                    // We need a FieldConfigConverter to parse it — inject and use below
                    null // placeholder; see note below
            );
            if (field.isRequired()) {
                type = type.nullable(false);
            }
            step = step.column(name(columnName), type);
        }

        step.constraints(
                constraint("pk_" + tableName).primaryKey(name("id"))
        ).execute();

        createHistoryTable(schemaName, tableName);

        for (ContentFieldEntity field : definition.getFields()) {
            FieldTypeHandler handler = registry.get(FieldType.valueOf(field.getFieldType()));
            if (!handler.isVirtual() && field.isSortable()) {
                createSortableIndex(schemaName, tableName, "cf_" + field.getFieldKey());
            }
        }

        log.info("Created table {}.{} successfully", schemaName, tableName);
    }
    // ... additional methods below
}
```

> **Implementation Note on `columnType(null)` placeholder above:** The `columnType` method on handlers takes a `FieldConfig` (domain object) but `ContentFieldEntity` stores config as a raw JSON string. We need `FieldConfigConverter` (already exists at `io.webcontify.content.definition.mapper.FieldConfigConverter`) injected into `DynamicTableManager`. Call `fieldConfigConverter.fromJson(entity.getConfig(), FieldType.valueOf(entity.getFieldType()))` to parse the config before passing to `columnType`. The actual `createTable` implementation must:

```java
// Inject FieldConfigConverter
private final FieldConfigConverter configConverter;

// In createTable loop:
FieldConfig config = configConverter.fromJson(field.getConfig(), FieldType.valueOf(field.getFieldType()));
DataType<?> type = handler.columnType(config);
```

**Full `createTable` with correct config parsing:**

```java
@Transactional
public void createTable(String schemaName, ContentDefinitionEntity definition) {
    String tableName = "cd_" + definition.getSlug().replace("-", "_");
    log.info("Creating table {}.{} for definition id={}", schemaName, tableName, definition.getId());

    dsl.execute("SET LOCAL lock_timeout = '5s'");

    var step = dsl.createTable(name(schemaName, tableName))
            .column(name("id"), UUID.nullable(false))
            .column(name("created_at"), TIMESTAMPWITHTIMEZONE.nullable(false).defaultValue(currentTimestamp()))
            .column(name("updated_at"), TIMESTAMPWITHTIMEZONE.nullable(false).defaultValue(currentTimestamp()))
            .column(name("created_by"), UUID)
            .column(name("updated_by"), UUID)
            .column(name("deleted_at"), TIMESTAMPWITHTIMEZONE)
            .column(name("deleted_by"), UUID);

    for (ContentFieldEntity field : definition.getFields()) {
        FieldTypeHandler handler = registry.get(FieldType.valueOf(field.getFieldType()));
        if (handler.isVirtual()) continue;
        String columnName = "cf_" + field.getFieldKey();
        FieldConfig config = configConverter.fromJson(field.getConfig(), FieldType.valueOf(field.getFieldType()));
        DataType<?> type = handler.columnType(config);
        if (field.isRequired()) {
            type = type.nullable(false);
        }
        step = step.column(name(columnName), type);
    }

    step.constraints(constraint("pk_" + tableName).primaryKey(name("id"))).execute();

    createHistoryTable(schemaName, tableName);

    for (ContentFieldEntity field : definition.getFields()) {
        FieldTypeHandler handler = registry.get(FieldType.valueOf(field.getFieldType()));
        if (!handler.isVirtual() && field.isSortable()) {
            createSortableIndex(schemaName, tableName, "cf_" + field.getFieldKey());
        }
    }

    log.info("Created table {}.{} successfully", schemaName, tableName);
}
```

**Private helpers:**

```java
private void createHistoryTable(String schemaName, String tableName) {
    String historyName = tableName + "_history";
    dsl.createTable(name(schemaName, historyName))
            .column(name("id"), UUID.nullable(false))
            .column(name("entry_id"), UUID.nullable(false))
            .column(name("action"), VARCHAR(20).nullable(false))
            .column(name("changed_fields"), JSONB)
            .column(name("changed_at"), TIMESTAMPWITHTIMEZONE.nullable(false).defaultValue(currentTimestamp()))
            .column(name("changed_by"), UUID)
            .constraints(constraint("pk_" + historyName).primaryKey(name("id")))
            .execute();

    dsl.createIndex(name("idx_" + historyName + "_entry"))
            .on(table(name(schemaName, historyName)), name("entry_id"), name("changed_at"))
            .execute();
}

private void createSortableIndex(String schemaName, String tableName, String columnName) {
    String indexName = "idx_" + tableName + "_" + columnName;
    dsl.createIndex(name(indexName))
            .on(table(name(schemaName, tableName)), name(columnName), name("id"))
            .execute();
}
```

**Helper: `tableExists`:**

```java
public boolean tableExists(String schemaName, ContentDefinitionEntity definition) {
    String tableName = "cd_" + definition.getSlug().replace("-", "_");
    int count = dsl.fetchCount(
            dsl.selectOne()
               .from("information_schema.tables")
               .where(field("table_schema").eq(schemaName))
               .and(field("table_name").eq(tableName))
    );
    return count > 0;
}
```

**Integration test `DynamicTableManagerCreateTableTest.java`:**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.*;

@SpringBootTest
@Testcontainers
class DynamicTableManagerCreateTableTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DynamicTableManager tableManager;

    @Autowired
    private DSLContext dsl;

    @Test
    void createTable_createsTableWithSystemAndFieldColumns() {
        // Arrange: create schema
        String schemaName = "tenant_test_create";
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

        ContentDefinitionEntity definition = new ContentDefinitionEntity();
        definition.setId(UUID.randomUUID());
        definition.setSlug("blog-post");

        ContentFieldEntity titleField = new ContentFieldEntity();
        titleField.setId(UUID.randomUUID());
        titleField.setFieldKey("title");
        titleField.setFieldType("TEXT");
        titleField.setRequired(false);
        titleField.setSortable(true);
        titleField.setConfig(null);
        definition.setFields(List.of(titleField));

        // Act
        tableManager.createTable(schemaName, definition);

        // Assert: main table columns
        List<String> columns = dsl
                .select(field("column_name", String.class))
                .from("information_schema.columns")
                .where(field("table_schema").eq(schemaName))
                .and(field("table_name").eq("cd_blog_post"))
                .fetchInto(String.class);

        assertThat(columns).contains("id", "created_at", "updated_at", "deleted_at", "cf_title");

        // Assert: history table exists
        int historyCount = dsl.fetchCount(
                dsl.selectOne()
                   .from("information_schema.tables")
                   .where(field("table_schema").eq(schemaName))
                   .and(field("table_name").eq("cd_blog_post_history"))
        );
        assertThat(historyCount).isEqualTo(1);

        // Assert: sortable index exists for cf_title
        int indexCount = dsl.fetchCount(
                dsl.selectOne()
                   .from("pg_indexes")
                   .where(field("schemaname").eq(schemaName))
                   .and(field("tablename").eq("cd_blog_post"))
                   .and(field("indexname").eq("idx_cd_blog_post_cf_title"))
        );
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    void tableExists_returnsTrueAfterCreate() {
        String schemaName = "tenant_test_exists";
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

        ContentDefinitionEntity definition = new ContentDefinitionEntity();
        definition.setId(UUID.randomUUID());
        definition.setSlug("articles");
        definition.setFields(List.of());

        assertThat(tableManager.tableExists(schemaName, definition)).isFalse();
        tableManager.createTable(schemaName, definition);
        assertThat(tableManager.tableExists(schemaName, definition)).isTrue();
    }
}
```

**Commit:** `feat(content): add DynamicTableManager.createTable via jOOQ`

---

### Task 7: `DynamicTableManager.applyChanges` + `dropTable`

- [ ] Add `applyChanges` method to `DynamicTableManager`
- [ ] Add `dropTable` method to `DynamicTableManager`
- [ ] Integration tests for ADD COLUMN and DROP COLUMN scenarios

**`applyChanges`:**

```java
@Transactional
public void applyChanges(String schemaName, ContentDefinitionEntity definition, List<DdlChange> changes) {
    String tableName = "cd_" + definition.getSlug().replace("-", "_");
    log.info("Applying {} DDL changes to {}.{}", changes.size(), schemaName, tableName);

    dsl.execute("SET LOCAL lock_timeout = '5s'");

    for (DdlChange change : changes) {
        ContentFieldEntity field = change.field();
        FieldTypeHandler handler = registry.get(FieldType.valueOf(field.getFieldType()));
        if (handler.isVirtual()) continue;

        String columnName = "cf_" + field.getFieldKey();

        switch (change.action()) {
            case ADD_COLUMN -> {
                FieldConfig config = configConverter.fromJson(field.getConfig(), FieldType.valueOf(field.getFieldType()));
                DataType<?> type = handler.columnType(config);
                if (field.isRequired()) {
                    type = type.nullable(false);
                }
                dsl.alterTable(name(schemaName, tableName))
                        .addColumn(name(columnName), type)
                        .execute();
                log.info("Added column {}.{}.{}", schemaName, tableName, columnName);
                if (field.isSortable()) {
                    createSortableIndex(schemaName, tableName, columnName);
                }
            }
            case DROP_COLUMN -> {
                if (field.isSortable()) {
                    String indexName = "idx_" + tableName + "_" + columnName;
                    dsl.dropIndexIfExists(name(schemaName, indexName)).execute();
                }
                dsl.alterTable(name(schemaName, tableName))
                        .dropColumn(name(columnName))
                        .execute();
                log.info("Dropped column {}.{}.{}", schemaName, tableName, columnName);
            }
            default -> log.warn("Unexpected action {} in applyChanges — skipped", change.action());
        }
    }
}
```

**`dropTable`:**

```java
@Transactional
public void dropTable(String schemaName, ContentDefinitionEntity definition) {
    String tableName = "cd_" + definition.getSlug().replace("-", "_");
    log.info("Dropping table {}.{} and history", schemaName, tableName);

    dsl.execute("SET LOCAL lock_timeout = '5s'");
    dsl.dropTableIfExists(name(schemaName, tableName + "_history")).execute();
    dsl.dropTableIfExists(name(schemaName, tableName)).execute();

    log.info("Dropped table {}.{}", schemaName, tableName);
}
```

**Integration test `DynamicTableManagerAlterTableTest.java`:**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;

@SpringBootTest
@Testcontainers
class DynamicTableManagerAlterTableTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired DynamicTableManager tableManager;
    @Autowired DSLContext dsl;

    private static final String SCHEMA = "tenant_test_alter";
    private ContentDefinitionEntity definition;

    @BeforeEach
    void setUp() {
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        definition = new ContentDefinitionEntity();
        definition.setId(UUID.randomUUID());
        definition.setSlug("alter-test-" + UUID.randomUUID().toString().substring(0, 8));
        definition.setFields(List.of());
        tableManager.createTable(SCHEMA, definition);
    }

    @Test
    void applyChanges_addColumn_columnAppearsInSchema() {
        ContentFieldEntity newField = fieldEntity("new_title", "TEXT", false, false);
        DdlChange addChange = new DdlChange(DdlAction.ADD_COLUMN, newField);

        tableManager.applyChanges(SCHEMA, definition, List.of(addChange));

        List<String> columns = columnsOf(SCHEMA, "cd_" + definition.getSlug().replace("-", "_"));
        assertThat(columns).contains("cf_new_title");
    }

    @Test
    void applyChanges_dropColumn_columnRemovedFromSchema() {
        // First add a column
        ContentFieldEntity field = fieldEntity("to_remove", "TEXT", false, false);
        tableManager.applyChanges(SCHEMA, definition, List.of(new DdlChange(DdlAction.ADD_COLUMN, field)));

        // Then drop it
        tableManager.applyChanges(SCHEMA, definition, List.of(new DdlChange(DdlAction.DROP_COLUMN, field)));

        List<String> columns = columnsOf(SCHEMA, "cd_" + definition.getSlug().replace("-", "_"));
        assertThat(columns).doesNotContain("cf_to_remove");
    }

    @Test
    void dropTable_removesTableAndHistory() {
        tableManager.dropTable(SCHEMA, definition);
        assertThat(tableManager.tableExists(SCHEMA, definition)).isFalse();
    }

    private List<String> columnsOf(String schema, String table) {
        return dsl.select(field("column_name", String.class))
                  .from("information_schema.columns")
                  .where(field("table_schema").eq(schema))
                  .and(field("table_name").eq(table))
                  .fetchInto(String.class);
    }

    private ContentFieldEntity fieldEntity(String key, String type, boolean required, boolean sortable) {
        ContentFieldEntity f = new ContentFieldEntity();
        f.setId(UUID.randomUUID());
        f.setFieldKey(key);
        f.setFieldType(type);
        f.setRequired(required);
        f.setSortable(sortable);
        f.setConfig(null);
        return f;
    }
}
```

**Commit:** `feat(content): add DynamicTableManager.applyChanges and dropTable`

---

### Task 8: `ContentDdlListener` — listens to `AppPublishedEvent`

- [ ] Create `ContentDdlListener.java` in `io.webcontify.content.definition.storage`
- [ ] Unit tests with mocks for all branches
- [ ] Ensure package-info.java module dependency allows `app` events (already permitted — `content` package-info allows `app`)

**`ContentDdlListener.java`:**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldChangeLogRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldChangeLogEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.shared.security.TenantSlugResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class ContentDdlListener {

    private static final Logger log = LoggerFactory.getLogger(ContentDdlListener.class);

    private final ContentDefinitionRepository definitionRepository;
    private final ContentFieldRepository fieldRepository;
    private final ContentFieldChangeLogRepository changeLogRepository;
    private final DynamicTableManager tableManager;
    private final DdlDiffEngine diffEngine;
    private final TenantSlugResolver slugResolver;

    public ContentDdlListener(
            ContentDefinitionRepository definitionRepository,
            ContentFieldRepository fieldRepository,
            ContentFieldChangeLogRepository changeLogRepository,
            DynamicTableManager tableManager,
            DdlDiffEngine diffEngine,
            TenantSlugResolver slugResolver) {
        this.definitionRepository = definitionRepository;
        this.fieldRepository = fieldRepository;
        this.changeLogRepository = changeLogRepository;
        this.tableManager = tableManager;
        this.diffEngine = diffEngine;
        this.slugResolver = slugResolver;
    }

    @ApplicationModuleListener
    @Transactional
    public void onAppPublished(AppPublishedEvent event) {
        log.info("AppPublishedEvent received for app={}, tenant={}", event.appId(), event.tenantId());

        String slug = slugResolver.resolveSlugByTenantId(event.tenantId())
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant slug not found for tenantId=" + event.tenantId()));

        String schemaName = "tenant_" + slug.replace("-", "_");

        List<ContentDefinitionEntity> definitions =
                definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(event.appId());

        for (ContentDefinitionEntity definition : definitions) {
            processDefinition(schemaName, definition, event.tenantId());
        }
    }

    private void processDefinition(String schemaName, ContentDefinitionEntity definition, UUID tenantId) {
        boolean tableExists = tableManager.tableExists(schemaName, definition);
        List<DdlChange> changes = diffEngine.computeDiff(definition, tableExists);

        if (changes.isEmpty()) {
            log.debug("No DDL changes for definition id={}", definition.getId());
            return;
        }

        log.info("Applying {} DDL changes for definition id={}", changes.size(), definition.getId());

        if (!tableExists) {
            tableManager.createTable(schemaName, definition);
            // After createTable, mark all ADDED fields as published
            for (ContentFieldEntity field : definition.getFields()) {
                if ("ADDED".equals(field.getPendingAction())) {
                    markFieldPublished(field);
                    logChange(tenantId, definition.getId(), field, "ADDED");
                }
            }
        } else {
            tableManager.applyChanges(schemaName, definition, changes);
            for (DdlChange change : changes) {
                ContentFieldEntity field = change.field();
                switch (change.action()) {
                    case ADD_COLUMN -> {
                        markFieldPublished(field);
                        logChange(tenantId, definition.getId(), field, "ADDED");
                    }
                    case DROP_COLUMN -> {
                        fieldRepository.delete(field);
                        logChange(tenantId, definition.getId(), field, "REMOVED");
                    }
                    default -> log.warn("Unexpected action {} skipped in flag update", change.action());
                }
            }
        }
    }

    private void markFieldPublished(ContentFieldEntity field) {
        field.setPublished(true);
        field.setPendingAction(null);
        fieldRepository.save(field);
    }

    private void logChange(UUID tenantId, UUID definitionId, ContentFieldEntity field, String action) {
        ContentFieldChangeLogEntity entry = new ContentFieldChangeLogEntity();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(tenantId);
        entry.setDefinitionId(definitionId);
        entry.setFieldKey(field.getFieldKey());
        entry.setAction(action);
        entry.setNewFieldType("ADDED".equals(action) ? field.getFieldType() : null);
        entry.setOldFieldType("REMOVED".equals(action) ? field.getFieldType() : null);
        entry.setNewConfig("ADDED".equals(action) ? field.getConfig() : null);
        entry.setOldConfig("REMOVED".equals(action) ? field.getConfig() : null);
        entry.setDataLoss("REMOVED".equals(action));
        entry.setAppliedAt(OffsetDateTime.now());
        changeLogRepository.save(entry);
    }
}
```

**Unit test `ContentDdlListenerTest.java`:**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldChangeLogRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.shared.security.TenantSlugResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentDdlListenerTest {

    @Mock ContentDefinitionRepository definitionRepository;
    @Mock ContentFieldRepository fieldRepository;
    @Mock ContentFieldChangeLogRepository changeLogRepository;
    @Mock DynamicTableManager tableManager;
    @Mock DdlDiffEngine diffEngine;
    @Mock TenantSlugResolver slugResolver;

    @InjectMocks ContentDdlListener listener;

    @Test
    void onAppPublished_noDefinitions_doesNothing() {
        UUID appId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(slugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("acme"));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(List.of());

        listener.onAppPublished(new AppPublishedEvent(appId, tenantId, "1.0.0"));

        verifyNoInteractions(tableManager, diffEngine);
    }

    @Test
    void onAppPublished_tableNotExists_callsCreateTable() {
        UUID appId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ContentDefinitionEntity def = definitionEntity(appId);

        when(slugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("acme"));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(List.of(def));
        when(tableManager.tableExists(eq("tenant_acme"), any())).thenReturn(false);
        when(diffEngine.computeDiff(def, false))
                .thenReturn(List.of(new DdlChange(DdlAction.CREATE_TABLE, null)));

        listener.onAppPublished(new AppPublishedEvent(appId, tenantId, "1.0.0"));

        verify(tableManager).createTable("tenant_acme", def);
        verify(tableManager, never()).applyChanges(any(), any(), any());
    }

    @Test
    void onAppPublished_tableExists_noChanges_skipsAlter() {
        UUID appId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ContentDefinitionEntity def = definitionEntity(appId);

        when(slugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("acme"));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(List.of(def));
        when(tableManager.tableExists(any(), any())).thenReturn(true);
        when(diffEngine.computeDiff(def, true)).thenReturn(List.of());

        listener.onAppPublished(new AppPublishedEvent(appId, tenantId, "1.0.0"));

        verify(tableManager, never()).applyChanges(any(), any(), any());
    }

    @Test
    void onAppPublished_slugNotFound_throwsIllegalState() {
        UUID appId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(slugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> listener.onAppPublished(new AppPublishedEvent(appId, tenantId, "1.0.0")));
    }

    @Test
    void onAppPublished_slugWithHyphens_replacedWithUnderscores() {
        UUID appId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ContentDefinitionEntity def = definitionEntity(appId);

        when(slugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("my-tenant-slug"));
        when(definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId)).thenReturn(List.of(def));
        when(tableManager.tableExists(eq("tenant_my_tenant_slug"), any())).thenReturn(false);
        when(diffEngine.computeDiff(def, false))
                .thenReturn(List.of(new DdlChange(DdlAction.CREATE_TABLE, null)));

        listener.onAppPublished(new AppPublishedEvent(appId, tenantId, "1.0.0"));

        verify(tableManager).createTable("tenant_my_tenant_slug", def);
    }

    private ContentDefinitionEntity definitionEntity(UUID appId) {
        ContentDefinitionEntity def = new ContentDefinitionEntity();
        def.setId(UUID.randomUUID());
        def.setAppId(appId);
        def.setSlug("blog-post");
        def.setFields(List.of());
        return def;
    }
}
```

**Commit:** `feat(content): add ContentDdlListener for App Publish DDL execution`

---

### Task 9: Full publish flow integration test

- [ ] Create `DdlPublishFlowIntegrationTest.java` in `io.webcontify.content.definition.storage`
- [ ] Test complete flow: create tenant → create app → create definition → add field → publish → assert table exists
- [ ] Test incremental column add: add another field → publish again → assert new column
- [ ] Run full test suite: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
- [ ] Document any pre-existing failures vs new failures

> This test requires the full Spring context (not MockMvc). It does NOT test via HTTP — it invokes services directly to avoid auth complexity.

**`DdlPublishFlowIntegrationTest.java`:**

```java
package io.webcontify.content.definition.storage;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.shared.model.AuditInfo;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.field;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class DdlPublishFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    // Mock TenantSlugResolver so we control slug resolution without a real tenant row
    @MockitoBean
    io.webcontify.shared.security.TenantSlugResolver slugResolver;

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired ContentDefinitionRepository definitionRepository;
    @Autowired ContentFieldRepository fieldRepository;
    @Autowired DSLContext dsl;

    private UUID tenantId;
    private UUID appId;
    private String schemaName;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        appId = UUID.randomUUID();
        schemaName = "tenant_integration_test";
        when(slugResolver.resolveSlugByTenantId(tenantId))
                .thenReturn(Optional.of("integration-test"));
        dsl.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
    }

    @Test
    void fullPublishFlow_createsTableOnFirstPublish() {
        // Arrange: save definition + field with pending_action = 'ADDED'
        ContentDefinitionEntity def = savedDefinition("articles");
        ContentFieldEntity titleField = savedField(def, "title", "TEXT", "ADDED");

        // Act: publish app
        eventPublisher.publishEvent(new AppPublishedEvent(appId, tenantId, "1.0.0"));

        // Assert: table exists with cf_title column
        List<String> columns = columnsOf(schemaName, "cd_articles");
        assertThat(columns).contains("id", "created_at", "cf_title");

        // Assert: field marked as published
        ContentFieldEntity updated = fieldRepository.findById(titleField.getId()).orElseThrow();
        assertThat(updated.isPublished()).isTrue();
        assertThat(updated.getPendingAction()).isNull();
    }

    @Test
    void incrementalPublish_addsColumnOnSecondPublish() {
        // First publish
        ContentDefinitionEntity def = savedDefinition("posts");
        savedField(def, "title", "TEXT", "ADDED");
        eventPublisher.publishEvent(new AppPublishedEvent(appId, tenantId, "1.0.0"));

        // Second publish: add a new field
        // Reload def to get updated fields list
        ContentDefinitionEntity reloadedDef = definitionRepository
                .findByIdAndAuditInfoDeletedAtIsNull(def.getId()).orElseThrow();
        ContentFieldEntity bodyField = savedField(reloadedDef, "body", "TEXT", "ADDED");

        eventPublisher.publishEvent(new AppPublishedEvent(appId, tenantId, "1.0.1"));

        List<String> columns = columnsOf(schemaName, "cd_posts");
        assertThat(columns).contains("cf_title", "cf_body");

        ContentFieldEntity updatedBody = fieldRepository.findById(bodyField.getId()).orElseThrow();
        assertThat(updatedBody.isPublished()).isTrue();
        assertThat(updatedBody.getPendingAction()).isNull();
    }

    private ContentDefinitionEntity savedDefinition(String slug) {
        ContentDefinitionEntity def = new ContentDefinitionEntity();
        def.setId(UUID.randomUUID());
        def.setAppId(appId);
        def.setName("Definition " + slug);
        def.setSlug(slug);
        AuditInfo audit = new AuditInfo();
        audit.setCreatedAt(OffsetDateTime.now());
        audit.setUpdatedAt(OffsetDateTime.now());
        def.setAuditInfo(audit);
        def.setTenantId(tenantId);
        return definitionRepository.save(def);
    }

    private ContentFieldEntity savedField(ContentDefinitionEntity def, String key, String type, String pendingAction) {
        ContentFieldEntity field = new ContentFieldEntity();
        field.setId(UUID.randomUUID());
        field.setTenantId(tenantId);
        field.setDefinition(def);
        field.setFieldKey(key);
        field.setFieldType(type);
        field.setRequired(false);
        field.setSortable(false);
        field.setFilterable(false);
        field.setUniqueValue(false);
        field.setPosition(0);
        field.setPublished(false);
        field.setPendingAction(pendingAction);
        AuditInfo audit = new AuditInfo();
        audit.setCreatedAt(OffsetDateTime.now());
        audit.setUpdatedAt(OffsetDateTime.now());
        field.setAuditInfo(audit);
        return fieldRepository.save(field);
    }

    private List<String> columnsOf(String schema, String table) {
        return dsl.select(field("column_name", String.class))
                  .from("information_schema.columns")
                  .where(field("table_schema").eq(schema))
                  .and(field("table_name").eq(table))
                  .fetchInto(String.class);
    }
}
```

**Run full suite:**
```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

Document any failures:
- If failures are in unrelated tests (pre-existing): note and skip
- If new failures from this phase: fix before committing

**Commit:** `test(content): integration test for App Publish DDL execution`

**Final commit:** `docs: update PROGRESS.md for Phase 5c completion`

---

## Key Constraints (Reminder)

- `import static org.jooq.impl.DSL.*` and `import static org.jooq.impl.SQLDataType.*` in all jOOQ files
- Always `name(schema, table)` — never string interpolation for DDL identifiers
- `SET LOCAL lock_timeout = '5s'` before every ALTER TABLE
- `@Transactional` on all methods that modify schema
- Skip `isVirtual() == true` handlers in all DDL operations
- Slug hyphens `->` underscores in schema + table names
- `CREATE INDEX CONCURRENTLY` is NOT used — we are inside a transaction
- New source package: `io.webcontify.content.definition.storage`
- `FieldDefinition` domain model does NOT carry `published`/`pendingAction` — work with `ContentFieldEntity` directly in the storage layer

## File List

| File | Action |
|------|--------|
| `src/main/resources/db/migration/V20260415_002__create_content_field_change_log.sql` | Create |
| `src/main/java/io/webcontify/content/definition/repository/entity/ContentFieldChangeLogEntity.java` | Create |
| `src/main/java/io/webcontify/content/definition/repository/ContentFieldChangeLogRepository.java` | Create |
| `src/main/java/io/webcontify/content/fieldtype/FieldDdlProvider.java` | Modify (return type change, remove `generateDdl`) |
| `src/main/java/io/webcontify/content/fieldtype/handler/TextFieldTypeHandler.java` | Modify |
| `src/main/java/io/webcontify/content/fieldtype/handler/IntegerFieldTypeHandler.java` | Modify |
| `src/main/java/io/webcontify/content/fieldtype/handler/DecimalFieldTypeHandler.java` | Modify |
| `src/main/java/io/webcontify/content/fieldtype/handler/BooleanFieldTypeHandler.java` | Modify |
| `src/main/java/io/webcontify/content/fieldtype/handler/DateFieldTypeHandler.java` | Modify |
| `src/main/java/io/webcontify/content/fieldtype/handler/EnumFieldTypeHandler.java` | Modify |
| `src/main/java/io/webcontify/content/fieldtype/handler/ReferenceFieldTypeHandler.java` | Modify |
| `src/main/java/io/webcontify/content/definition/repository/ContentFieldRepository.java` | Modify (add query) |
| `src/main/java/io/webcontify/content/definition/storage/DdlAction.java` | Create |
| `src/main/java/io/webcontify/content/definition/storage/DdlChange.java` | Create |
| `src/main/java/io/webcontify/content/definition/storage/DdlDiffEngine.java` | Create |
| `src/main/java/io/webcontify/content/definition/storage/DynamicTableManager.java` | Create |
| `src/main/java/io/webcontify/content/definition/storage/ContentDdlListener.java` | Create |
| `src/test/java/io/webcontify/content/definition/storage/JooqAutoConfigTest.java` | Create |
| `src/test/java/io/webcontify/content/definition/storage/DdlDiffEngineTest.java` | Create |
| `src/test/java/io/webcontify/content/definition/storage/DynamicTableManagerCreateTableTest.java` | Create |
| `src/test/java/io/webcontify/content/definition/storage/DynamicTableManagerAlterTableTest.java` | Create |
| `src/test/java/io/webcontify/content/definition/storage/ContentDdlListenerTest.java` | Create |
| `src/test/java/io/webcontify/content/definition/storage/DdlPublishFlowIntegrationTest.java` | Create |
| `src/test/java/io/webcontify/content/fieldtype/FieldTypeHandlerContractTest.java` | Modify |
| `src/test/java/io/webcontify/content/definition/repository/ContentFieldChangeLogRepositoryTest.java` | Create |
