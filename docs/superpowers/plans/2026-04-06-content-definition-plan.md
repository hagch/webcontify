# Content Definition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Content Definition CRUD within App context — schema definition with 7 field types, reference validation, OpenFGA permissions, App revision integration, and React Flow graph UI.

**Architecture:** Content is a separate Spring Modulith module (`content/definition/`) communicating with App via domain events. Contract-First (OpenAPI), JPA for static schema, MapStruct for mapping, ChangeRecordService for revisions.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, MapStruct, OpenFGA, React Flow, ELK.js, shadcn/ui, Orval, Zod, Playwright

**Design Spec:** `docs/plans/2026-04-06-content-definition-design.md`

---

## Sub-Plan Overview

| Sub-Plan | Scope | Dependencies |
|---|---|---|
| 5a | OpenFGA Model + Flyway Migration + OpenAPI Spec | None |
| 5b | Backend: Domain Models, Entities, Repositories, Mappers | 5a |
| 5c | Backend: Services (Definition CRUD + Field CRUD + Validators), TDD | 5b |
| 5d | Backend: Controllers + Integration Tests | 5c |
| 5e | Backend: App Integration (Events, Revisions, Snapshots, Permissions) | 5d |
| 5f | Frontend: Orval + i18n + Graph UI + Sidebar CRUD | 5d |
| 5g | Frontend: Permission Matrix + Revisions Tab Extension | 5e, 5f |
| 5h | E2E Tests (Playwright) | 5f, 5g |

---

## Sub-Plan 5a: Infrastructure (OpenFGA + Migration + OpenAPI)

### Task 5a.1: Update OpenFGA Model

**Files:**
- Modify: `infra/openfga/model.fga`

- [ ] **Step 1: Add `creator` to `app_management` and new `content_definition` type**

```
type app_management
  relations
    define tenant: [tenant]
    define admin: [user, role#assignee] or admin from tenant
    define editor: [user, role#assignee] or admin
    define creator: [user, role#assignee] or admin
    define viewer: [user, role#assignee] or editor or creator

type content_definition
  relations
    define parent: [app]
    define viewer: [user, role#assignee] or viewer from parent
    define editor: [user, role#assignee] or editor from parent
    define admin: [user, role#assignee] or admin from parent
    define creator: [user, role#assignee] or admin from parent
    define deleter: [user, role#assignee] or editor from parent
```

- [ ] **Step 2: Regenerate model.json**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew transformOpenFgaModel`

- [ ] **Step 3: Commit**

```bash
git add infra/openfga/model.fga infra/openfga/model.json
git commit -m "feat: add content_definition type and creator relation to OpenFGA model"
```

### Task 5a.2: Flyway Migration

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V20260406_002__create_content_definition_tables.sql`

- [ ] **Step 1: Write migration**

```sql
-- Content Definition
CREATE TABLE content_definition (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    app_id           UUID NOT NULL REFERENCES app(id),
    name             VARCHAR(255) NOT NULL,
    slug             VARCHAR(255) NOT NULL,
    description      TEXT,
    display_format   VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    deleted_by       UUID,
    UNIQUE(app_id, name),
    UNIQUE(app_id, slug)
);

CREATE INDEX idx_cd_tenant_id ON content_definition(tenant_id);
CREATE INDEX idx_cd_app_id ON content_definition(app_id);

ALTER TABLE content_definition ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_content_definition ON content_definition
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );

-- Field Definition
CREATE TABLE content_field_definition (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    definition_id    UUID NOT NULL REFERENCES content_definition(id) ON DELETE CASCADE,
    field_key        VARCHAR(255) NOT NULL,
    field_type       VARCHAR(50) NOT NULL,
    required         BOOLEAN NOT NULL DEFAULT false,
    unique_value     BOOLEAN NOT NULL DEFAULT false,
    sortable         BOOLEAN NOT NULL DEFAULT false,
    filterable       BOOLEAN NOT NULL DEFAULT false,
    config           JSONB,
    position         INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    deleted_by       UUID,
    UNIQUE(definition_id, field_key)
);

CREATE INDEX idx_cfd_definition_id ON content_field_definition(definition_id);
CREATE INDEX idx_cfd_field_type ON content_field_definition(field_type);

ALTER TABLE content_field_definition ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_content_field_definition ON content_field_definition
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
```

- [ ] **Step 2: Verify migration compiles**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.WebcontifyApplicationTest`

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/resources/db/migration/V20260406_002__create_content_definition_tables.sql
git commit -m "feat: add content_definition and content_field_definition tables with RLS"
```

### Task 5a.3: OpenAPI Spec

**Files:**
- Create: `openapi/content-api.yaml`
- Modify: `apps/backend/build.gradle.kts` (add content-api to code generation)

- [ ] **Step 1: Write content-api.yaml**

Write the full OpenAPI 3.1.0 spec with:

**Tags:** ContentDefinition, ContentField

**Paths:**
- `POST /api/v1/content/{appSlug}/definitions` — operationId: `createContentDefinition`
- `GET /api/v1/content/{appSlug}/definitions` — operationId: `listContentDefinitions`
- `GET /api/v1/content/{appSlug}/definitions/{definitionId}` — operationId: `getContentDefinition`
- `PUT /api/v1/content/{appSlug}/definitions/{definitionId}` — operationId: `updateContentDefinition`
- `DELETE /api/v1/content/{appSlug}/definitions/{definitionId}` — operationId: `deleteContentDefinition`
- `GET /api/v1/content/{appSlug}/definitions/name-available` — operationId: `checkContentDefinitionNameAvailability`
- `POST /api/v1/content/{appSlug}/definitions/{definitionId}/fields` — operationId: `addField`
- `PUT /api/v1/content/{appSlug}/definitions/{definitionId}/fields/{fieldId}` — operationId: `updateField`
- `DELETE /api/v1/content/{appSlug}/definitions/{definitionId}/fields/{fieldId}` — operationId: `deleteField`
- `PUT /api/v1/content/{appSlug}/definitions/{definitionId}/fields/reorder` — operationId: `reorderFields`

**Schemas:**
- `CreateContentDefinitionRequest`: name (required), description (optional)
- `UpdateContentDefinitionRequest`: name, description, displayFormat (all optional)
- `ContentDefinitionResponse`: id, appId, name, slug, description, displayFormat, fields[], auditInfo
- `ContentDefinitionListResponse`: items[], pageInfo
- `CreateFieldRequest`: fieldKey (required), fieldType (required, enum), required, unique, sortable, filterable, config (object)
- `UpdateFieldRequest`: fieldKey, required, unique, sortable, filterable, config (all optional)
- `FieldDefinitionResponse`: id, fieldKey, fieldType, required, unique, sortable, filterable, config, position
- `ReorderFieldsRequest`: fieldIds (uuid array)
- `FieldType` enum: TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, ENUM, REFERENCE
- `FieldConfig` oneOf: TextFieldConfig, IntegerFieldConfig, DecimalFieldConfig, BooleanFieldConfig, DateFieldConfig, EnumFieldConfig, ReferenceFieldConfig

Each FieldConfig schema:
- `TextFieldConfig`: minLength (int), maxLength (int), pattern (string), defaultValue (string)
- `IntegerFieldConfig`: min (int64), max (int64), defaultValue (int64)
- `DecimalFieldConfig`: min (number), max (number), precision (int), scale (int), defaultValue (number)
- `BooleanFieldConfig`: defaultValue (boolean)
- `DateFieldConfig`: minDate (date), maxDate (date), dateFormat (string), defaultValue (date)
- `EnumFieldConfig`: allowedValues (string array), defaultValue (string)
- `ReferenceFieldConfig`: targetDefinitionId (uuid), relationType (enum: ONE_TO_ONE, MANY_TO_ONE, MANY_TO_MANY), onDelete (enum: CASCADE, SET_NULL, RESTRICT)

Error responses reference `./common-schemas.yaml#/components/schemas/ProblemDetail` and `ValidationProblemDetail`.

Follow exact patterns from `openapi/app-api.yaml`: path params as `format: uuid`, pagination via query params (page/size defaults), Location header on 201.

- [ ] **Step 2: Add code generation config to build.gradle.kts**

Add a new `openApiGenerate` task for `content-api.yaml` following the existing pattern for `app-api.yaml`. Generated package: `io.webcontify.generated.content`.

Check existing config: `apps/backend/build.gradle.kts` — find the openApiGenerate section and replicate for content.

- [ ] **Step 3: Run code generation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate`
Expected: Generated interfaces `ContentDefinitionApi`, `ContentFieldApi` and all DTOs in `build/generated/`

- [ ] **Step 4: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add openapi/content-api.yaml apps/backend/build.gradle.kts
git commit -m "feat: add content-api.yaml OpenAPI spec with code generation"
```

---

## Sub-Plan 5b: Backend Domain Models, Entities, Repositories, Mappers

### Task 5b.1: Domain Models

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/ContentDefinition.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/FieldDefinition.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/FieldType.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/RelationType.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/OnDeleteStrategy.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/FieldConfig.java` (sealed interface)
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/TextFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/IntegerFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/DecimalFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/BooleanFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/DateFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/EnumFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/ReferenceFieldConfig.java`

- [ ] **Step 1: Create enums**

`FieldType.java`:
```java
package io.webcontify.content.definition.model;

public enum FieldType {
    TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, ENUM, REFERENCE
}
```

`RelationType.java`:
```java
package io.webcontify.content.definition.model;

public enum RelationType {
    ONE_TO_ONE, MANY_TO_ONE, MANY_TO_MANY
}
```

`OnDeleteStrategy.java`:
```java
package io.webcontify.content.definition.model;

public enum OnDeleteStrategy {
    CASCADE, SET_NULL, RESTRICT
}
```

- [ ] **Step 2: Create FieldConfig sealed interface + records**

`FieldConfig.java`:
```java
package io.webcontify.content.definition.model;

public sealed interface FieldConfig permits
        TextFieldConfig, IntegerFieldConfig, DecimalFieldConfig,
        BooleanFieldConfig, DateFieldConfig, EnumFieldConfig, ReferenceFieldConfig {}
```

`TextFieldConfig.java`:
```java
package io.webcontify.content.definition.model;

public record TextFieldConfig(
        Integer minLength, Integer maxLength, String pattern, String defaultValue
) implements FieldConfig {}
```

`IntegerFieldConfig.java`:
```java
package io.webcontify.content.definition.model;

public record IntegerFieldConfig(
        Long min, Long max, Long defaultValue
) implements FieldConfig {}
```

`DecimalFieldConfig.java`:
```java
package io.webcontify.content.definition.model;

import java.math.BigDecimal;

public record DecimalFieldConfig(
        BigDecimal min, BigDecimal max, Integer precision, Integer scale, BigDecimal defaultValue
) implements FieldConfig {}
```

`BooleanFieldConfig.java`:
```java
package io.webcontify.content.definition.model;

public record BooleanFieldConfig(Boolean defaultValue) implements FieldConfig {}
```

`DateFieldConfig.java`:
```java
package io.webcontify.content.definition.model;

import java.time.LocalDate;

public record DateFieldConfig(
        LocalDate minDate, LocalDate maxDate, String dateFormat, LocalDate defaultValue
) implements FieldConfig {}
```

`EnumFieldConfig.java`:
```java
package io.webcontify.content.definition.model;

import java.util.List;

public record EnumFieldConfig(
        List<String> allowedValues, String defaultValue
) implements FieldConfig {}
```

`ReferenceFieldConfig.java`:
```java
package io.webcontify.content.definition.model;

import java.util.UUID;

public record ReferenceFieldConfig(
        UUID targetDefinitionId, RelationType relationType, OnDeleteStrategy onDelete
) implements FieldConfig {}
```

- [ ] **Step 3: Create domain models**

`ContentDefinition.java` — POJO following App.java pattern:
```java
package io.webcontify.content.definition.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ContentDefinition {
    private UUID id;
    private UUID tenantId;
    private UUID appId;
    private String name;
    private String slug;
    private String description;
    private String displayFormat;
    private List<FieldDefinition> fields;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;
    // getters + setters for all fields
}
```

`FieldDefinition.java`:
```java
package io.webcontify.content.definition.model;

import java.util.UUID;

public class FieldDefinition {
    private UUID id;
    private UUID tenantId;
    private UUID definitionId;
    private String fieldKey;
    private FieldType fieldType;
    private boolean required;
    private boolean uniqueValue;
    private boolean sortable;
    private boolean filterable;
    private FieldConfig config;
    private int position;
    // getters + setters for all fields
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/
git commit -m "feat: add Content Definition domain models and FieldConfig records"
```

### Task 5b.2: JPA Entities

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/repository/entity/ContentDefinitionEntity.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/repository/entity/ContentFieldEntity.java`

- [ ] **Step 1: Create entities**

`ContentDefinitionEntity.java` — follows AppEntity pattern (extends TenantAwareBaseEntity):
```java
package io.webcontify.content.definition.repository.entity;

import io.webcontify.shared.persistence.TenantAwareBaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "content_definition")
public class ContentDefinitionEntity extends TenantAwareBaseEntity {

    @Column(name = "app_id", nullable = false)
    private UUID appId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "description")
    private String description;

    @Column(name = "display_format")
    private String displayFormat;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<ContentFieldEntity> fields = new ArrayList<>();

    // getters + setters
}
```

`ContentFieldEntity.java`:
```java
package io.webcontify.content.definition.repository.entity;

import io.webcontify.shared.persistence.TenantAwareBaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "content_field_definition")
public class ContentFieldEntity extends TenantAwareBaseEntity {

    @Column(name = "definition_id", nullable = false, insertable = false, updatable = false)
    private UUID definitionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private ContentDefinitionEntity definition;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "field_type", nullable = false)
    private String fieldType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "unique_value", nullable = false)
    private boolean uniqueValue;

    @Column(name = "sortable", nullable = false)
    private boolean sortable;

    @Column(name = "filterable", nullable = false)
    private boolean filterable;

    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    @Column(name = "position", nullable = false)
    private int position;

    // getters + setters
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/repository/entity/
git commit -m "feat: add ContentDefinitionEntity and ContentFieldEntity JPA entities"
```

### Task 5b.3: Repositories

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/repository/ContentDefinitionRepository.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/repository/ContentFieldRepository.java`

- [ ] **Step 1: Create repositories**

`ContentDefinitionRepository.java`:
```java
package io.webcontify.content.definition.repository;

import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentDefinitionRepository extends JpaRepository<ContentDefinitionEntity, UUID> {
    Optional<ContentDefinitionEntity> findByIdAndAuditInfoDeletedAtIsNull(UUID id);
    List<ContentDefinitionEntity> findByAppIdAndAuditInfoDeletedAtIsNull(UUID appId);
    boolean existsByNameAndAppIdAndAuditInfoDeletedAtIsNull(String name, UUID appId);
    boolean existsBySlugAndAppIdAndAuditInfoDeletedAtIsNull(String slug, UUID appId);
}
```

`ContentFieldRepository.java`:
```java
package io.webcontify.content.definition.repository;

import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContentFieldRepository extends JpaRepository<ContentFieldEntity, UUID> {
    Optional<ContentFieldEntity> findByIdAndAuditInfoDeletedAtIsNull(UUID id);
    List<ContentFieldEntity> findByDefinitionIdAndAuditInfoDeletedAtIsNullOrderByPositionAsc(UUID definitionId);
    boolean existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull(String fieldKey, UUID definitionId);

    @Query("SELECT f FROM ContentFieldEntity f WHERE f.fieldType = 'REFERENCE' AND f.config LIKE %:targetDefinitionId% AND f.auditInfo.deletedAt IS NULL")
    List<ContentFieldEntity> findReferenceFieldsByTargetDefinitionId(String targetDefinitionId);
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/repository/
git commit -m "feat: add ContentDefinition and ContentField repositories"
```

### Task 5b.4: MapStruct Mappers

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/mapper/ContentDefinitionMapper.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/mapper/ContentFieldMapper.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/mapper/FieldConfigConverter.java`

- [ ] **Step 1: Create FieldConfigConverter** (Jackson JSONB ↔ FieldConfig)

```java
package io.webcontify.content.definition.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.webcontify.content.definition.model.*;
import org.springframework.stereotype.Component;

@Component
public class FieldConfigConverter {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public FieldConfig fromJson(String json, FieldType fieldType) {
        if (json == null) return null;
        try {
            return switch (fieldType) {
                case TEXT -> objectMapper.readValue(json, TextFieldConfig.class);
                case INTEGER -> objectMapper.readValue(json, IntegerFieldConfig.class);
                case DECIMAL -> objectMapper.readValue(json, DecimalFieldConfig.class);
                case BOOLEAN -> objectMapper.readValue(json, BooleanFieldConfig.class);
                case DATE -> objectMapper.readValue(json, DateFieldConfig.class);
                case ENUM -> objectMapper.readValue(json, EnumFieldConfig.class);
                case REFERENCE -> objectMapper.readValue(json, ReferenceFieldConfig.class);
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize FieldConfig", e);
        }
    }

    public String toJson(FieldConfig config) {
        if (config == null) return null;
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize FieldConfig", e);
        }
    }

    public Object toApiConfig(FieldConfig config) {
        if (config == null) return null;
        return objectMapper.convertValue(config, JsonNode.class);
    }

    public FieldConfig fromApiConfig(Object config, FieldType fieldType) {
        if (config == null) return null;
        JsonNode node = objectMapper.valueToTree(config);
        return fromJson(node.toString(), fieldType);
    }
}
```

- [ ] **Step 2: Create ContentDefinitionMapper**

Follow AppMapper pattern — interface with `@Mapper(componentModel = "spring")`:
```java
package io.webcontify.content.definition.mapper;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.generated.content.model.*;
import java.util.List;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {ContentFieldMapper.class})
public interface ContentDefinitionMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    ContentDefinition toDomain(ContentDefinitionEntity entity);

    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "fields", ignore = true)
    ContentDefinitionEntity toEntity(ContentDefinition domain);

    ContentDefinitionResponse toResponse(ContentDefinition domain);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "appId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "displayFormat", ignore = true)
    @Mapping(target = "fields", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ContentDefinition toDomain(CreateContentDefinitionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "appId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "fields", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ContentDefinition toDomain(UpdateContentDefinitionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "appId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "fields", ignore = true)
    void updateEntity(@MappingTarget ContentDefinitionEntity entity, ContentDefinition domain);
}
```

- [ ] **Step 3: Create ContentFieldMapper**

```java
package io.webcontify.content.definition.mapper;

import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.generated.content.model.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public abstract class ContentFieldMapper {

    protected FieldConfigConverter fieldConfigConverter;

    @org.springframework.beans.factory.annotation.Autowired
    public void setFieldConfigConverter(FieldConfigConverter converter) {
        this.fieldConfigConverter = converter;
    }

    @Mapping(source = "fieldType", target = "fieldType", qualifiedByName = "stringToFieldType")
    @Mapping(target = "config", ignore = true)
    public abstract FieldDefinition toDomain(ContentFieldEntity entity);

    @AfterMapping
    protected void mapConfigToDomain(ContentFieldEntity entity, @MappingTarget FieldDefinition domain) {
        if (entity.getConfig() != null && domain.getFieldType() != null) {
            domain.setConfig(fieldConfigConverter.fromJson(entity.getConfig(), domain.getFieldType()));
        }
    }

    @Mapping(source = "fieldType", target = "fieldType", qualifiedByName = "fieldTypeToString")
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "definition", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    public abstract ContentFieldEntity toEntity(FieldDefinition domain);

    @AfterMapping
    protected void mapConfigToEntity(FieldDefinition domain, @MappingTarget ContentFieldEntity entity) {
        entity.setConfig(fieldConfigConverter.toJson(domain.getConfig()));
    }

    @Mapping(target = "config", ignore = true)
    public abstract FieldDefinitionResponse toResponse(FieldDefinition domain);

    @AfterMapping
    protected void mapConfigToResponse(FieldDefinition domain, @MappingTarget FieldDefinitionResponse response) {
        response.setConfig(fieldConfigConverter.toApiConfig(domain.getConfig()));
    }

    @Named("stringToFieldType")
    protected FieldType stringToFieldType(String type) {
        return type == null ? null : FieldType.valueOf(type);
    }

    @Named("fieldTypeToString")
    protected String fieldTypeToString(FieldType type) {
        return type == null ? null : type.name();
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/mapper/
git commit -m "feat: add ContentDefinition and ContentField MapStruct mappers"
```

### Task 5b.5: Spring Modulith Configuration

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/package-info.java`

- [ ] **Step 1: Create module annotation**

```java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared", "shared::shared-changelog", "shared::shared-security", "shared::shared-persistence", "shared::shared-util", "app"}
)
package io.webcontify.content;
```

Check existing `apps/backend/src/main/java/io/webcontify/app/package-info.java` for the exact pattern and adjust `allowedDependencies` to match.

- [ ] **Step 2: Run modulith verification**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.ModulithStructureTest`

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/
git commit -m "feat: configure content Spring Modulith module"
```

---

## Sub-Plan 5c: Backend Services (TDD)

### Task 5c.1: ContentFieldConfigValidator (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentFieldConfigValidator.java`
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentFieldConfigValidatorTest.java`

- [ ] **Step 1: Write failing tests**

Test cases:
- TEXT: valid config passes
- TEXT: minLength > maxLength throws
- TEXT: invalid regex pattern throws
- INTEGER: min > max throws
- DECIMAL: precision < scale throws
- ENUM: empty allowedValues throws
- ENUM: defaultValue not in allowedValues throws
- DATE: minDate > maxDate throws
- REFERENCE: targetDefinitionId null throws
- REFERENCE: relationType null throws
- All types: valid defaultValue passes
- All types: defaultValue outside constraints throws

Follow AppServiceTest pattern: `@ExtendWith(MockitoExtension.class)`, static test data, AssertJ assertions.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentFieldConfigValidatorTest`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement ContentFieldConfigValidator**

```java
package io.webcontify.content.definition.service;

import io.webcontify.content.definition.model.*;
import io.webcontify.shared.exception.ValidationException;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class ContentFieldConfigValidator {

    public void validate(FieldType fieldType, FieldConfig config) {
        if (config == null) return;

        switch (config) {
            case TextFieldConfig c -> validateText(c);
            case IntegerFieldConfig c -> validateInteger(c);
            case DecimalFieldConfig c -> validateDecimal(c);
            case BooleanFieldConfig c -> { /* no constraints to validate */ }
            case DateFieldConfig c -> validateDate(c);
            case EnumFieldConfig c -> validateEnum(c);
            case ReferenceFieldConfig c -> validateReference(c);
        }
    }

    // private validate methods for each type with specific constraint checks
    // Each throws ValidationException with i18n key on failure
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentFieldConfigValidatorTest`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/service/ContentFieldConfigValidator.java apps/backend/src/test/java/io/webcontify/content/definition/service/ContentFieldConfigValidatorTest.java
git commit -m "feat: add ContentFieldConfigValidator with TDD (12+ tests)"
```

### Task 5c.2: ContentReferenceValidator (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentReferenceValidator.java`
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentReferenceValidatorTest.java`

- [ ] **Step 1: Write failing tests**

Test cases:
- Same-app reference: always valid
- Cross-app reference with valid dependency: valid
- Cross-app reference without dependency: throws
- Circular reference within same app: valid
- targetDefinitionId does not exist: throws
- displayFormat with valid placeholders: valid
- displayFormat with unknown placeholder: throws

- [ ] **Step 2: Run tests, verify fail**
- [ ] **Step 3: Implement ContentReferenceValidator**

Injects: `ContentDefinitionRepository`, `AppDependencyRepository` (from app module, read-only)

- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add ContentReferenceValidator with TDD (7 tests)"
```

### Task 5c.3: ContentDefinitionService (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java`
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java`

- [ ] **Step 1: Write failing tests**

Test cases following AppServiceTest pattern:
- create: generates slug, sets tenantId, saves entity, returns domain model
- create: duplicate name throws ConflictException
- create: writes OpenFGA tuple
- create: publishes ContentDefinitionChangedEvent
- getById: returns definition with fields
- getById: not found throws NotFoundException
- list: returns all definitions for app
- update: updates name/description, creates revision
- update: name change checks uniqueness
- update: slug remains unchanged
- update: sets app to DRAFT via event
- delete: soft deletes definition
- delete: removes reference fields pointing to this definition (cascade)
- delete: removes OpenFGA tuple
- isNameAvailable: returns true/false

Setup: Mock repositories, mapper, slugGenerator, changeRecordService, permissionService. SecurityContext with TenantAwareAuthentication (same pattern as AppServiceTest).

- [ ] **Step 2: Run tests, verify fail**
- [ ] **Step 3: Implement ContentDefinitionService**

Follow AppService pattern exactly:
- `@Service @Transactional`
- Constructor injection
- `TenantContext.getTenantInformation()` for tenant scoping
- `SlugGenerator.generateUnique()` for slug (unique check against appId, not tenantId)
- `UuidGenerator.generate()` for IDs
- `ChangeRecordService.create()` for revisions with `entityType="content_definition"`
- `PermissionService.grantRelation()` / `revokeRelation()` for OpenFGA tuples
- App resolution: inject `AppRepository` (read-only) to resolve appSlug → appId
- Event publishing: `ApplicationEventPublisher.publishEvent(new ContentDefinitionChangedEvent(appId, tenantId))`

- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add ContentDefinitionService with TDD (15 tests)"
```

### Task 5c.4: ContentFieldService (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentFieldService.java`
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentFieldServiceTest.java`

- [ ] **Step 1: Write failing tests**

Test cases:
- addField: creates field with correct position, validates config
- addField: duplicate fieldKey throws
- addField: REFERENCE type validates via ContentReferenceValidator
- updateField: updates fieldKey, config, options
- updateField: fieldType change throws (immutable)
- updateField: creates revision
- deleteField: soft deletes field
- deleteField: updates displayFormat if placeholder removed
- reorder: updates positions for all fields
- All mutations: publish ContentDefinitionChangedEvent (→ app DRAFT)

- [ ] **Step 2: Run tests, verify fail**
- [ ] **Step 3: Implement ContentFieldService**
- [ ] **Step 4: Run tests, verify pass**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add ContentFieldService with TDD (10 tests)"
```

---

## Sub-Plan 5d: Backend Controllers + Integration Tests

### Task 5d.1: ContentDefinitionController

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java`

- [ ] **Step 1: Implement controller**

Follow AppController pattern:
```java
@RestController
public class ContentDefinitionController implements ContentDefinitionApi {

    private final ContentDefinitionService definitionService;
    private final ContentDefinitionMapper mapper;

    // Constructor injection

    @Override
    @RequiresPermission(action = "creator", resource = "app", resourceId = "#appSlug")
    public ResponseEntity<ContentDefinitionResponse> createContentDefinition(
            String appSlug, CreateContentDefinitionRequest request) {
        // Map request, call service, return 201 + Location
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appSlug")
    public ResponseEntity<ContentDefinitionListResponse> listContentDefinitions(String appSlug) {
        // List + map to response
    }

    // ... remaining endpoints with appropriate @RequiresPermission
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add ContentDefinitionController implementing generated interface"
```

### Task 5d.2: ContentFieldController

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/api/ContentFieldController.java`

- [ ] **Step 1: Implement controller** (same pattern, field endpoints)
- [ ] **Step 2: Verify compilation**
- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add ContentFieldController implementing generated interface"
```

### Task 5d.3: Integration Tests

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/api/ContentDefinitionControllerIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

Follow AppControllerIntegrationTest pattern:
- `@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(TestSecurityConfiguration.class)`
- `@Container @ServiceConnection` PostgreSQL 17
- `@MockitoBean PermissionService`
- Setup: create a test tenant + test app in `@BeforeEach`

Test cases:
- createAndRetrieveDefinition: POST 201 + GET 200
- createWithDuplicateName: POST 409
- updateDefinition: PUT 200
- deleteDefinition: DELETE 204
- checkNameAvailability: GET 200
- addField: POST 201
- updateField: PUT 200
- deleteField: DELETE 204
- reorderFields: PUT 200
- addReferenceField: POST 201 with REFERENCE config
- fieldConfigValidation: POST 400 with invalid config

- [ ] **Step 2: Run integration tests**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.api.ContentDefinitionControllerIntegrationTest`
Expected: ALL PASS

- [ ] **Step 3: Run full backend test suite**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git commit -m "test: add Content Definition integration tests (11 tests)"
```

---

## Sub-Plan 5e: Backend App Integration

### Task 5e.1: Domain Events

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/event/ContentDefinitionChangedEvent.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/event/ContentDefinitionDeletedEvent.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppService.java` (add event listener)

- [ ] **Step 1: Create events**

```java
public record ContentDefinitionChangedEvent(UUID appId, UUID tenantId) {}
public record ContentDefinitionDeletedEvent(UUID appId, UUID tenantId, UUID definitionId) {}
```

- [ ] **Step 2: Add listener in AppService or create AppContentListener**

```java
@Component
public class AppContentListener {
    private final AppService appService;

    @ApplicationModuleListener
    public void onContentDefinitionChanged(ContentDefinitionChangedEvent event) {
        appService.updateStatus(event.appId(), AppStatus.DRAFT);
    }
}
```

- [ ] **Step 3: Test event wiring**

Write test: publish event → verify AppService.updateStatus called.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add Content Definition domain events with App integration"
```

### Task 5e.2: AppSnapshot Extension

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/app/model/AppSnapshot.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppSnapshotService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java`

- [ ] **Step 1: Extend AppSnapshotDefinition and AppSnapshotField**

Add to AppSnapshotDefinition: `description`, `displayFormat`
Add to AppSnapshotField: `fieldKey` (rename from `name`), `required`, `uniqueValue`, `sortable`, `filterable`, `config` (String, full JSON), `position`

- [ ] **Step 2: Populate snapshot on publish**

In AppVersionService.publish(): load all ContentDefinitions for the app, serialize into AppSnapshot.contentDefinitions with full field details.

- [ ] **Step 3: Restore from snapshot on rollback**

Implement reconciliation algorithm from design doc (9 steps):
1. Load snapshot definitions (desired state)
2. Load current definitions (actual state)
3. Definitions in snapshot but not DB → restore
4. Definitions in DB but not snapshot → soft-delete
5. Both → overwrite with snapshot state
6. Dangling references → auto-remove
7. Topological sort for processing order
8. Single transaction
9. OpenFGA tuple cleanup

- [ ] **Step 4: Write tests for snapshot publish/rollback**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat: extend AppSnapshot with full Content Definition data, implement rollback reconciliation"
```

### Task 5e.3: Permission Integration

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java` (extend RESOURCE_ACTIONS)
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java` (extend effective permissions)

- [ ] **Step 1: Add content_definition to RESOURCE_ACTIONS**

In RoleService, add to the resource actions map:
```java
"content_definition", List.of("viewer", "editor", "admin", "creator", "deleter")
```

- [ ] **Step 2: Extend getEffectivePermissions**

In AuthService, add content_definition to the BatchCheck query.

- [ ] **Step 3: Run existing permission tests to ensure no regression**
- [ ] **Step 4: Commit**

```bash
git commit -m "feat: extend RoleService and AuthService with content_definition permissions"
```

---

## Sub-Plan 5f: Frontend (Graph UI + Sidebar CRUD)

### Task 5f.1: Orval Config + Code Generation

**Files:**
- Modify: `apps/platform/orval.config.ts`

- [ ] **Step 1: Add content API to Orval config**

```typescript
content: {
  input: { target: "../../openapi/content-api.yaml" },
  output: {
    target: "./src/generated/content.ts",
    client: "react-query",
    mode: "single",
    override: {
      mutator: {
        path: "./src/lib/orval-mutator.ts",
        name: "customInstance",
      },
    },
  },
},
```

- [ ] **Step 2: Generate**

Run: `cd apps/platform && pnpm generate`

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add content API to Orval config and generate hooks"
```

### Task 5f.2: i18n Keys

**Files:**
- Modify: `apps/platform/src/messages/en.json`
- Modify: `apps/platform/src/messages/de.json`

- [ ] **Step 1: Add content definition i18n keys (EN)**

```json
"content": {
  "title": "Content Definitions",
  "addDefinition": "Add Definition",
  "noDefinitions": "No content definitions yet",
  "addFirst": "Add your first definition",
  "desktopRecommended": "Desktop recommended for graph editing",
  "definition": {
    "name": "Name",
    "description": "Description",
    "displayFormat": "Display Format",
    "displayFormatHint": "Use {fieldKey} placeholders, e.g. \"{title} — {author}\"",
    "delete": "Delete Definition",
    "deleteConfirm": "This will delete the definition and remove all reference fields pointing to it.",
    "nameExists": "A definition with this name already exists"
  },
  "field": {
    "title": "Fields",
    "addField": "Add Field",
    "noFields": "No fields defined yet",
    "fieldKey": "Field Key",
    "fieldType": "Field Type",
    "required": "Required",
    "unique": "Unique",
    "sortable": "Sortable",
    "filterable": "Filterable",
    "config": "Configuration",
    "delete": "Delete Field",
    "deleteConfirm": "Are you sure you want to delete this field?",
    "typeImmutable": "Field type cannot be changed after creation",
    "keyExists": "A field with this key already exists"
  },
  "fieldType": {
    "TEXT": "Text",
    "INTEGER": "Integer",
    "DECIMAL": "Decimal",
    "BOOLEAN": "Boolean",
    "DATE": "Date",
    "ENUM": "Enum",
    "REFERENCE": "Reference"
  },
  "config": {
    "minLength": "Min Length",
    "maxLength": "Max Length",
    "pattern": "Pattern (Regex)",
    "defaultValue": "Default Value",
    "min": "Minimum",
    "max": "Maximum",
    "precision": "Precision",
    "scale": "Scale",
    "minDate": "Min Date",
    "maxDate": "Max Date",
    "dateFormat": "Date Format",
    "allowedValues": "Allowed Values",
    "targetDefinition": "Target Definition",
    "relationType": "Relation Type",
    "onDelete": "On Delete"
  },
  "relationType": {
    "ONE_TO_ONE": "One-to-One",
    "MANY_TO_ONE": "Many-to-One",
    "MANY_TO_MANY": "Many-to-Many"
  },
  "onDelete": {
    "CASCADE": "Cascade",
    "SET_NULL": "Set Null",
    "RESTRICT": "Restrict"
  },
  "graph": {
    "layoutReset": "Reset Layout",
    "ghostNodeBadge": "From: {appName}"
  }
}
```

- [ ] **Step 2: Add DE translation**

Same structure, German labels.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add content definition i18n keys (EN + DE)"
```

### Task 5f.3: Content Definition Graph Component

**Files:**
- Create: `apps/platform/src/features/content/components/content-definition-graph.tsx`
- Create: `apps/platform/src/features/content/components/definition-node.tsx`
- Create: `apps/platform/src/features/content/components/ghost-definition-node.tsx`
- Create: `apps/platform/src/features/content/components/reference-edge.tsx`

- [ ] **Step 1: Create DefinitionNode**

Custom React Flow node showing: definition name, field list (compact: icon + fieldKey), handles for edges. Follow AppNode pattern from `app-node.tsx`:
- Width: 280px
- Handle left (target) + right (source)
- Clickable (triggers sidebar open)
- Field list truncated at 5, "+N more" indicator

- [ ] **Step 2: Create GhostDefinitionNode**

Same layout as DefinitionNode but:
- Dashed border (`border-dashed`)
- Greyed out (`opacity-60`)
- Badge showing source app name
- Not clickable for editing
- No delete button

- [ ] **Step 3: Create ReferenceEdge**

Follow DependencyEdge pattern from `dependency-edge.tsx`:
- Label showing relation type (1:1, N:1, N:M)
- Color based on same-app vs cross-app
- Smooth step path with border radius

- [ ] **Step 4: Create ContentDefinitionGraph**

Main component following `app-dependency-graph.tsx` pattern:
- ReactFlow with custom nodeTypes + edgeTypes
- ELK layout via `useElkLayout` hook (direction: DOWN for ER diagram)
- Minimap component
- Background + Controls
- Toolbar with "Add Definition" button
- Empty state when no definitions
- Data transformation: definitions → nodes, reference fields → edges
- Ghost nodes for dependency app definitions

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add Content Definition React Flow graph with custom nodes and edges"
```

### Task 5f.4: Content Sidebar

**Files:**
- Create: `apps/platform/src/features/content/components/content-sidebar.tsx`
- Create: `apps/platform/src/features/content/components/definition-form.tsx`
- Create: `apps/platform/src/features/content/components/field-list.tsx`
- Create: `apps/platform/src/features/content/components/field-form.tsx`
- Create: `apps/platform/src/features/content/components/field-config-form.tsx`
- Create: `apps/platform/src/features/content/schemas/content-schemas.ts`

- [ ] **Step 1: Create Zod schemas**

```typescript
export const createDefinitionSchema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().max(2000).optional().or(z.literal("")),
});

export const updateDefinitionSchema = z.object({
  name: z.string().min(1).max(255).optional(),
  description: z.string().max(2000).optional().or(z.literal("")),
  displayFormat: z.string().max(500).optional().or(z.literal("")),
});

export const createFieldSchema = z.object({
  fieldKey: z.string().min(1).max(255).regex(/^[a-z][a-z0-9_]*$/, "Must start with lowercase letter, only a-z, 0-9, _"),
  fieldType: z.enum(["TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "ENUM", "REFERENCE"]),
  required: z.boolean().default(false),
  unique: z.boolean().default(false),
  sortable: z.boolean().default(false),
  filterable: z.boolean().default(false),
  config: z.object({}).passthrough().optional(),
});
```

- [ ] **Step 2: Create ContentSidebar**

Right panel that slides in on node click:
- Header: Definition name (editable), close button
- DefinitionForm: name, description, displayFormat
- FieldList: sortable list with drag handles (using `@dnd-kit/sortable`)
- "Add Field" button at bottom

- [ ] **Step 3: Create FieldForm**

Dialog/accordion for field editing:
- fieldKey input
- fieldType dropdown (disabled if editing — immutable)
- Toggle group: required, unique, sortable, filterable
- FieldConfigForm (dynamic based on fieldType)

- [ ] **Step 4: Create FieldConfigForm**

Dynamic form that changes based on fieldType:
- TEXT: minLength, maxLength, pattern, defaultValue inputs
- INTEGER: min, max, defaultValue inputs
- DECIMAL: min, max, precision, scale, defaultValue inputs
- BOOLEAN: defaultValue checkbox
- DATE: minDate, maxDate, dateFormat, defaultValue date pickers
- ENUM: allowedValues tag input, defaultValue dropdown
- REFERENCE: targetDefinition dropdown (grouped by app), relationType dropdown, onDelete dropdown

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add Content Definition sidebar with field management forms"
```

### Task 5f.5: Content Tab on App Detail Page

**Files:**
- Modify: `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx`

- [ ] **Step 1: Add "Content" tab**

Add new `TabsTrigger` "Content" and `TabsContent` with `ContentDefinitionGraph` + `ContentSidebar` components. Position as first tab after "Overview".

- [ ] **Step 2: Wire up state management**

Selected node state lifts from graph → sidebar. Use `useState` for selected definition ID. Pass to both graph (highlight) and sidebar (show details).

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add Content tab to App Detail Page with graph and sidebar"
```

### Task 5f.6: Definition Detail Page

**Files:**
- Create: `apps/platform/src/app/(workspace)/apps/[appId]/definitions/[definitionId]/page.tsx`

- [ ] **Step 1: Create detail page**

Full-page view for a single definition:
- AppHeader with breadcrumbs (Apps → {appName} → Definitions → {defName})
- Full DefinitionForm (name, description, displayFormat)
- Full FieldList with larger editing area
- Back link to Content tab

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: add Content Definition detail page"
```

### Task 5f.7: Frontend Tests

**Files:**
- Create: `apps/platform/src/features/content/components/__tests__/definition-node.test.tsx`
- Create: `apps/platform/src/features/content/components/__tests__/field-form.test.tsx`

- [ ] **Step 1: Write Vitest tests**

Test cases:
- DefinitionNode renders name and field list
- GhostDefinitionNode renders with dashed border and badge
- FieldForm validates fieldKey format
- FieldForm disables fieldType when editing
- FieldConfigForm renders correct inputs for each fieldType

- [ ] **Step 2: Run tests**

Run: `cd apps/platform && pnpm vitest run`

- [ ] **Step 3: Commit**

```bash
git commit -m "test: add Content Definition frontend tests"
```

---

## Sub-Plan 5g: Frontend Permission Matrix + Revisions Extension

### Task 5g.1: Permission Matrix Extension

**Files:**
- Modify: `apps/platform/src/features/roles/components/role-permissions-editor.tsx`

- [ ] **Step 1: Add content_definition to RESOURCE_ACTIONS**

Extend the existing permission matrix with hierarchical app → definition structure:
- Load apps via `useListApps()`
- For each app, load definitions via `useListContentDefinitions(appSlug)`
- Render collapsible app sections with definition toggles underneath
- Add i18n keys for new permission labels

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: extend permission matrix with hierarchical content_definition permissions"
```

### Task 5g.2: Revisions Tab Extension

**Files:**
- Modify: `apps/platform/src/features/apps/components/app-revision-list.tsx` (or equivalent)

- [ ] **Step 1: Extend revision display**

- Add type icon/badge to distinguish "app" vs "content_definition" revisions
- Content definition revisions show: definition name, field changes summary
- Revert button works for both types

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: extend Revisions tab with content_definition revision support"
```

---

## Sub-Plan 5h: E2E Tests (Playwright)

### Task 5h.1: E2E Test Helpers

**Files:**
- Create: `apps/e2e/helpers/content-api.ts`

- [ ] **Step 1: Create API helpers**

```typescript
export async function createContentDefinition(appSlug: string, data: { name: string; description?: string }) { ... }
export async function addField(appSlug: string, definitionId: string, data: { fieldKey: string; fieldType: string; config?: any }) { ... }
export async function deleteContentDefinition(appSlug: string, definitionId: string) { ... }
```

Follow existing `apps/e2e/helpers/app-api.ts` pattern.

- [ ] **Step 2: Commit**

```bash
git commit -m "test: add content definition E2E API helpers"
```

### Task 5h.2: BDU Test 1 — Content Definition CRUD

**Files:**
- Create: `apps/e2e/tests/content/content-crud.spec.ts`

- [ ] **Step 1: Write E2E test**

Steps from BDU Test 1 (design doc section 7):
1. Navigate to app detail → Content tab
2. See empty graph
3. Click "Add Definition" → sidebar opens
4. Type "Blog Post" → save
5. Node appears in graph
6. Click node → edit description → save
7. Delete definition → confirm → node disappears
8. Verify app status shows "Draft"

Use `data-testid` selectors, English text only.

- [ ] **Step 2: Commit**

```bash
git commit -m "test: add Content Definition CRUD E2E test"
```

### Task 5h.3: BDU Test 2 — Field Management

**Files:**
- Create: `apps/e2e/tests/content/content-fields.spec.ts`

Steps from BDU Test 2.

### Task 5h.4: BDU Test 3 — Reference Fields + Cross-App

**Files:**
- Create: `apps/e2e/tests/content/content-references.spec.ts`

Steps from BDU Test 3.

### Task 5h.5: BDU Test 4 — Permissions

**Files:**
- Create: `apps/e2e/tests/content/content-permissions.spec.ts`

Steps from BDU Test 4.

### Task 5h.6: BDU Test 5 — Revisions + Publish

**Files:**
- Create: `apps/e2e/tests/content/content-revisions.spec.ts`

Steps from BDU Test 5.

### Task 5h.7: BDU Test 6 — Graph UI

**Files:**
- Create: `apps/e2e/tests/content/content-graph.spec.ts`

Steps from BDU Test 6.

---

## Final Verification

- [ ] **Run full backend test suite:** `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
- [ ] **Run full frontend test suite:** `cd apps/platform && pnpm vitest run`
- [ ] **Run frontend build:** `cd apps/platform && pnpm build` (nx build platform)
- [ ] **Run lint:** `nx lint platform`
- [ ] **Update PROGRESS.md** with Phase 5 tasks
- [ ] **Update memory if needed**
