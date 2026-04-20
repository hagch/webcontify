# Content Definition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Content Definition CRUD within App context -- canvas-only graph editor with drag-and-connect references, MIRROR fields, spotlight search, popovers, position persistence, undo/redo, and OpenFGA permissions. 8 field types (TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, ENUM, REFERENCE, MIRROR), strict config validation, App revision integration, and dead code cleanup.

**Architecture:** Content is a separate Spring Modulith module (`content/definition/`) communicating with App via domain events. Contract-First (OpenAPI `content-api.yaml`), JPA for static schema, MapStruct for mapping, ChangeRecordService for revisions. Frontend is a canvas-only React Flow graph with popovers (no sidebar, no detail page).

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, MapStruct, OpenFGA, React Flow 12, ELK.js, shadcn/ui, @base-ui/react/popover, Orval, Zod, Playwright

**Design Spec:** `docs/plans/2026-04-06-content-definition-design.md`

---

## Sub-Plan Overview

| Sub-Plan | Scope | Dependencies |
|---|---|---|
| 5a | OpenFGA Model + Flyway Migration + OpenAPI Spec (`content-api.yaml`) | None |
| 5b | Backend: Domain Models, Entities, Repositories, Mappers (incl. MIRROR, FieldConfig) | 5a |
| 5c | Backend: Services TDD (ConfigValidator, ReferenceValidator, DefinitionService, FieldService, PositionService) | 5b |
| 5d | Backend: Controllers + Integration Tests | 5c |
| 5e | Backend: App Integration (Events, Revisions, Snapshots, Permissions, Undo/Redo) | 5d |
| 5f | Frontend: Orval + i18n + Graph Canvas (nodes, edges, spotlight, popovers, drag-connect) | 5d |
| 5g | Frontend: Permission Matrix + Revisions Tab + Dependencies Tab Read-Only + Dead Code Cleanup | 5e, 5f |
| 5h | E2E Tests (Playwright) | 5f, 5g |

---

## Sub-Plan 5a: Infrastructure (OpenFGA + Migration + OpenAPI)

### Task 5a.1: Update OpenFGA Model

**Files:**
- Modify: `infra/openfga/model.fga`

- [ ] **Step 1: Add `creator` to `app_management` and new `content_definition` type**

Open `infra/openfga/model.fga` and apply two changes:

1. In `type app_management`, add `creator` relation and update `viewer` to include `creator`:

```
type app_management
  relations
    define tenant: [tenant]
    define admin: [user, role#assignee] or admin from tenant
    define editor: [user, role#assignee] or admin
    define creator: [user, role#assignee] or admin
    define viewer: [user, role#assignee] or editor or creator
```

2. At the end of the file, add the new `content_definition` type:

```
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

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew transformOpenFgaModel
```

Expected: `infra/openfga/model.json` updated with new types.

- [ ] **Step 3: Commit**

```bash
git add infra/openfga/model.fga infra/openfga/model.json
git commit -m "feat: add content_definition type and creator relation to OpenFGA model"
```

---

### Task 5a.2: Flyway Migration

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V20260407_001__create_content_definition_tables.sql`

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
    position_x       DOUBLE PRECISION,
    position_y       DOUBLE PRECISION,
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

-- Content Reference join table (populated during Content Management phase, schema needed now)
CREATE TABLE content_reference (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    source_field_id UUID NOT NULL REFERENCES content_field_definition(id),
    source_entry_id UUID NOT NULL,
    target_entry_id UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    UNIQUE(source_field_id, source_entry_id, target_entry_id)
);

CREATE INDEX idx_cr_source_field ON content_reference(source_field_id);
CREATE INDEX idx_cr_source_entry ON content_reference(source_entry_id);
CREATE INDEX idx_cr_target_entry ON content_reference(target_entry_id);

ALTER TABLE content_reference ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_content_reference ON content_reference
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
```

- [ ] **Step 2: Verify migration compiles**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.WebcontifyApplicationTest
```

Expected: BUILD SUCCESSFUL (Flyway migration applies cleanly)

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/resources/db/migration/V20260407_001__create_content_definition_tables.sql
git commit -m "feat: add content_definition, content_field_definition, and content_reference tables with RLS"
```

---

### Task 5a.3: OpenAPI Spec

**Files:**
- Create: `openapi/content-api.yaml`
- Modify: `apps/backend/build.gradle.kts`

- [ ] **Step 1: Write content-api.yaml**

```yaml
openapi: 3.1.0
info:
  title: Webcontify Content API
  version: 1.0.0
  description: Tenant-scoped endpoints for content definition management

servers:
  - url: http://localhost:8080
    description: Local development server

tags:
  - name: ContentDefinition
    description: Content Definition Management
  - name: ContentField
    description: Content Field Management

paths:
  /api/v1/content/{appSlug}/definitions:
    post:
      operationId: createContentDefinition
      summary: Create a new content definition
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateContentDefinitionRequest'
      responses:
        '201':
          description: Content definition created
          headers:
            Location:
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentDefinitionResponse'
        '400':
          description: Validation error
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ValidationProblemDetail'
        '409':
          description: Name already exists
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

    get:
      operationId: listContentDefinitions
      summary: List all content definitions for an app
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
      responses:
        '200':
          description: Content definition list
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentDefinitionListResponse'

  /api/v1/content/{appSlug}/definitions/{definitionId}:
    get:
      operationId: getContentDefinition
      summary: Get a content definition by ID
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefinitionIdPath'
      responses:
        '200':
          description: Content definition details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentDefinitionResponse'
        '404':
          description: Content definition not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

    put:
      operationId: updateContentDefinition
      summary: Update a content definition
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefinitionIdPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateContentDefinitionRequest'
      responses:
        '200':
          description: Content definition updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentDefinitionResponse'
        '400':
          description: Validation error
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
        '409':
          description: Name already exists
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

    delete:
      operationId: deleteContentDefinition
      summary: Soft delete a content definition
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefinitionIdPath'
      responses:
        '204':
          description: Content definition deleted
        '404':
          description: Content definition not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/content/{appSlug}/definitions/name-available:
    get:
      operationId: checkContentDefinitionNameAvailability
      summary: Check if a content definition name is available
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - name: name
          in: query
          required: true
          schema:
            type: string
        - name: excludeId
          in: query
          required: false
          schema:
            type: string
            format: uuid
          description: Exclude this definition from the check (for updates)
      responses:
        '200':
          description: Name availability result
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/NameAvailabilityResponse'

  /api/v1/content/{appSlug}/definitions/positions:
    put:
      operationId: batchUpdatePositions
      summary: Batch update definition positions on graph canvas
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdatePositionsRequest'
      responses:
        '204':
          description: Positions updated
        '400':
          description: Validation error
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ValidationProblemDetail'

  /api/v1/content/{appSlug}/definitions/undo:
    post:
      operationId: undoContentChange
      summary: Undo the last content definition change
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
      responses:
        '200':
          description: Change undone
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UndoRedoResponse'
        '404':
          description: Nothing to undo
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/content/{appSlug}/definitions/redo:
    post:
      operationId: redoContentChange
      summary: Redo the last undone content definition change
      tags:
        - ContentDefinition
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
      responses:
        '200':
          description: Change redone
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UndoRedoResponse'
        '404':
          description: Nothing to redo
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/content/{appSlug}/definitions/{definitionId}/fields:
    post:
      operationId: addField
      summary: Add a field to a content definition
      tags:
        - ContentField
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefinitionIdPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateFieldRequest'
      responses:
        '201':
          description: Field added
          headers:
            Location:
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FieldDefinitionResponse'
        '400':
          description: Validation error
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ValidationProblemDetail'
        '409':
          description: Field key already exists
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/content/{appSlug}/definitions/{definitionId}/fields/{fieldId}:
    put:
      operationId: updateField
      summary: Update a field on a content definition
      tags:
        - ContentField
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefinitionIdPath'
        - $ref: '#/components/parameters/FieldIdPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateFieldRequest'
      responses:
        '200':
          description: Field updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FieldDefinitionResponse'
        '400':
          description: Validation error
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ValidationProblemDetail'
        '404':
          description: Field not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '409':
          description: Field key already exists
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

    delete:
      operationId: deleteField
      summary: Delete a field from a content definition
      tags:
        - ContentField
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefinitionIdPath'
        - $ref: '#/components/parameters/FieldIdPath'
      responses:
        '204':
          description: Field deleted
        '404':
          description: Field not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/content/{appSlug}/definitions/{definitionId}/fields/reorder:
    put:
      operationId: reorderFields
      summary: Reorder fields on a content definition
      tags:
        - ContentField
      parameters:
        - $ref: '#/components/parameters/AppSlugPath'
        - $ref: '#/components/parameters/DefinitionIdPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ReorderFieldsRequest'
      responses:
        '200':
          description: Fields reordered
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ContentDefinitionResponse'
        '400':
          description: Validation error
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ValidationProblemDetail'

components:
  parameters:
    AppSlugPath:
      name: appSlug
      in: path
      required: true
      schema:
        type: string

    DefinitionIdPath:
      name: definitionId
      in: path
      required: true
      schema:
        type: string
        format: uuid

    FieldIdPath:
      name: fieldId
      in: path
      required: true
      schema:
        type: string
        format: uuid

  schemas:
    FieldType:
      type: string
      enum:
        - TEXT
        - INTEGER
        - DECIMAL
        - BOOLEAN
        - DATE
        - ENUM
        - REFERENCE
        - MIRROR

    RelationType:
      type: string
      enum:
        - ONE_TO_ONE
        - MANY_TO_ONE
        - MANY_TO_MANY

    OnDeleteStrategy:
      type: string
      enum:
        - CASCADE
        - SET_NULL
        - RESTRICT

    CreateContentDefinitionRequest:
      type: object
      required:
        - name
      properties:
        name:
          type: string
          minLength: 1
          maxLength: 255
        description:
          type: string
          maxLength: 2000

    UpdateContentDefinitionRequest:
      type: object
      properties:
        name:
          type: string
          minLength: 1
          maxLength: 255
        description:
          type: string
          maxLength: 2000
        displayFormat:
          type: string
          maxLength: 500
        positionX:
          type: number
          format: double
        positionY:
          type: number
          format: double

    ContentDefinitionResponse:
      type: object
      required:
        - id
        - appId
        - name
        - slug
        - fields
        - createdAt
        - updatedAt
      properties:
        id:
          type: string
          format: uuid
        appId:
          type: string
          format: uuid
        name:
          type: string
        slug:
          type: string
        description:
          type: string
        displayFormat:
          type: string
        positionX:
          type: number
          format: double
        positionY:
          type: number
          format: double
        fields:
          type: array
          items:
            $ref: '#/components/schemas/FieldDefinitionResponse'
        createdAt:
          type: string
          format: date-time
        createdBy:
          type: string
        updatedAt:
          type: string
          format: date-time
        updatedBy:
          type: string
        effectiveActions:
          type: array
          items:
            type: string
            enum:
              - viewer
              - editor
              - admin
              - creator
              - deleter
          description: Actions the current user is allowed to perform on this definition

    ContentDefinitionListResponse:
      type: object
      required:
        - items
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/ContentDefinitionResponse'

    CreateFieldRequest:
      type: object
      required:
        - fieldKey
        - fieldType
      properties:
        fieldKey:
          type: string
          minLength: 1
          maxLength: 255
        fieldType:
          $ref: '#/components/schemas/FieldType'
        required:
          type: boolean
          default: false
        unique:
          type: boolean
          default: false
        sortable:
          type: boolean
          default: false
        filterable:
          type: boolean
          default: false
        config:
          $ref: '#/components/schemas/FieldConfig'

    UpdateFieldRequest:
      type: object
      properties:
        fieldKey:
          type: string
          minLength: 1
          maxLength: 255
        required:
          type: boolean
        unique:
          type: boolean
        sortable:
          type: boolean
        filterable:
          type: boolean
        config:
          $ref: '#/components/schemas/FieldConfig'

    FieldDefinitionResponse:
      type: object
      required:
        - id
        - fieldKey
        - fieldType
        - required
        - unique
        - sortable
        - filterable
        - position
      properties:
        id:
          type: string
          format: uuid
        fieldKey:
          type: string
        fieldType:
          $ref: '#/components/schemas/FieldType'
        required:
          type: boolean
        unique:
          type: boolean
        sortable:
          type: boolean
        filterable:
          type: boolean
        config:
          $ref: '#/components/schemas/FieldConfig'
        position:
          type: integer

    FieldConfig:
      oneOf:
        - $ref: '#/components/schemas/TextFieldConfig'
        - $ref: '#/components/schemas/IntegerFieldConfig'
        - $ref: '#/components/schemas/DecimalFieldConfig'
        - $ref: '#/components/schemas/BooleanFieldConfig'
        - $ref: '#/components/schemas/DateFieldConfig'
        - $ref: '#/components/schemas/EnumFieldConfig'
        - $ref: '#/components/schemas/ReferenceFieldConfig'
        - $ref: '#/components/schemas/MirrorFieldConfig'

    TextFieldConfig:
      type: object
      properties:
        minLength:
          type: integer
        maxLength:
          type: integer
        pattern:
          type: string
        defaultValue:
          type: string

    IntegerFieldConfig:
      type: object
      properties:
        min:
          type: integer
          format: int64
        max:
          type: integer
          format: int64
        defaultValue:
          type: integer
          format: int64

    DecimalFieldConfig:
      type: object
      properties:
        min:
          type: number
        max:
          type: number
        precision:
          type: integer
        scale:
          type: integer
        defaultValue:
          type: number

    BooleanFieldConfig:
      type: object
      properties:
        defaultValue:
          type: boolean

    DateFieldConfig:
      type: object
      properties:
        minDate:
          type: string
          format: date
        maxDate:
          type: string
          format: date
        dateFormat:
          type: string
        defaultValue:
          type: string
          format: date

    EnumFieldConfig:
      type: object
      required:
        - allowedValues
      properties:
        allowedValues:
          type: array
          items:
            type: string
          minItems: 1
        defaultValue:
          type: string

    ReferenceFieldConfig:
      type: object
      required:
        - targetDefinitionId
        - relationType
        - onDelete
      properties:
        targetDefinitionId:
          type: string
          format: uuid
        relationType:
          $ref: '#/components/schemas/RelationType'
        onDelete:
          $ref: '#/components/schemas/OnDeleteStrategy'

    MirrorFieldConfig:
      type: object
      required:
        - sourceDefinitionId
        - sourceFieldId
        - ownerAppId
      properties:
        sourceDefinitionId:
          type: string
          format: uuid
        sourceFieldId:
          type: string
          format: uuid
        ownerAppId:
          type: string
          format: uuid

    ReorderFieldsRequest:
      type: object
      required:
        - fieldIds
      properties:
        fieldIds:
          type: array
          items:
            type: string
            format: uuid

    UpdatePositionsRequest:
      type: object
      required:
        - positions
      properties:
        positions:
          type: array
          items:
            $ref: '#/components/schemas/PositionUpdate'

    PositionUpdate:
      type: object
      required:
        - definitionId
        - positionX
        - positionY
      properties:
        definitionId:
          type: string
          format: uuid
        positionX:
          type: number
          format: double
        positionY:
          type: number
          format: double

    UndoRedoResponse:
      type: object
      required:
        - success
      properties:
        success:
          type: boolean
        entityType:
          type: string
        entityId:
          type: string
          format: uuid
        changeType:
          type: string
        canUndo:
          type: boolean
        canRedo:
          type: boolean
```

- [ ] **Step 2: Add code generation config to build.gradle.kts**

In `apps/backend/build.gradle.kts`, add a new `GenerateTask` after the `openApiGenerateApp` task:

```kotlin
tasks.register<GenerateTask>("openApiGenerateContent") {
    generatorName.set("spring")
    inputSpec.set("$openApiSpecDir/content-api.yaml")
    outputDir.set(openApiOutputDir)
    apiPackage.set("io.webcontify.generated.api")
    modelPackage.set("io.webcontify.generated.model")
    configOptions.set(openApiConfigOptions)
    typeMappings.set(openApiTypeMappings)
}
```

Update the `compileJava` dependency:

```kotlin
tasks.named("compileJava") {
    dependsOn("openApiGenerate", "openApiGenerateIdentity", "openApiGeneratePlatformAdmin", "openApiGenerateAuth", "openApiGenerateApp", "openApiGenerateContent")
}
```

- [ ] **Step 3: Run code generation**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerateContent
```

Expected: Generated interfaces `ContentDefinitionApi`, `ContentFieldApi` and all DTOs in `build/generated/`

- [ ] **Step 4: Verify compilation**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

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
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/FieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/TextFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/IntegerFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/DecimalFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/BooleanFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/DateFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/EnumFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/ReferenceFieldConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/model/MirrorFieldConfig.java`

- [ ] **Step 1: Create enums**

`FieldType.java`:
```java
package io.webcontify.content.definition.model;

public enum FieldType {
    TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, ENUM, REFERENCE, MIRROR
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
        BooleanFieldConfig, DateFieldConfig, EnumFieldConfig,
        ReferenceFieldConfig, MirrorFieldConfig {}
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

`MirrorFieldConfig.java`:
```java
package io.webcontify.content.definition.model;

import java.util.UUID;

public record MirrorFieldConfig(
        UUID sourceDefinitionId, UUID sourceFieldId, UUID ownerAppId
) implements FieldConfig {}
```

- [ ] **Step 3: Create domain models**

`ContentDefinition.java` -- POJO following existing App.java pattern:
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
    private Double positionX;
    private Double positionY;
    private List<FieldDefinition> fields;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;

    // Constructor
    public ContentDefinition() {}

    // Getters and setters for all fields
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getAppId() { return appId; }
    public void setAppId(UUID appId) { this.appId = appId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDisplayFormat() { return displayFormat; }
    public void setDisplayFormat(String displayFormat) { this.displayFormat = displayFormat; }
    public Double getPositionX() { return positionX; }
    public void setPositionX(Double positionX) { this.positionX = positionX; }
    public Double getPositionY() { return positionY; }
    public void setPositionY(Double positionY) { this.positionY = positionY; }
    public List<FieldDefinition> getFields() { return fields; }
    public void setFields(List<FieldDefinition> fields) { this.fields = fields; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
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

    public FieldDefinition() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public FieldType getFieldType() { return fieldType; }
    public void setFieldType(FieldType fieldType) { this.fieldType = fieldType; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isUniqueValue() { return uniqueValue; }
    public void setUniqueValue(boolean uniqueValue) { this.uniqueValue = uniqueValue; }
    public boolean isSortable() { return sortable; }
    public void setSortable(boolean sortable) { this.sortable = sortable; }
    public boolean isFilterable() { return filterable; }
    public void setFilterable(boolean filterable) { this.filterable = filterable; }
    public FieldConfig getConfig() { return config; }
    public void setConfig(FieldConfig config) { this.config = config; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/
git commit -m "feat: add Content Definition domain models with MIRROR field type and FieldConfig records"
```

---

### Task 5b.2: JPA Entities

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/repository/entity/ContentDefinitionEntity.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/repository/entity/ContentFieldEntity.java`

- [ ] **Step 1: Create ContentDefinitionEntity**

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

    @Column(name = "position_x")
    private Double positionX;

    @Column(name = "position_y")
    private Double positionY;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<ContentFieldEntity> fields = new ArrayList<>();

    public UUID getAppId() { return appId; }
    public void setAppId(UUID appId) { this.appId = appId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDisplayFormat() { return displayFormat; }
    public void setDisplayFormat(String displayFormat) { this.displayFormat = displayFormat; }
    public Double getPositionX() { return positionX; }
    public void setPositionX(Double positionX) { this.positionX = positionX; }
    public Double getPositionY() { return positionY; }
    public void setPositionY(Double positionY) { this.positionY = positionY; }
    public List<ContentFieldEntity> getFields() { return fields; }
    public void setFields(List<ContentFieldEntity> fields) { this.fields = fields; }
}
```

- [ ] **Step 2: Create ContentFieldEntity**

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

    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }
    public ContentDefinitionEntity getDefinition() { return definition; }
    public void setDefinition(ContentDefinitionEntity definition) { this.definition = definition; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isUniqueValue() { return uniqueValue; }
    public void setUniqueValue(boolean uniqueValue) { this.uniqueValue = uniqueValue; }
    public boolean isSortable() { return sortable; }
    public void setSortable(boolean sortable) { this.sortable = sortable; }
    public boolean isFilterable() { return filterable; }
    public void setFilterable(boolean filterable) { this.filterable = filterable; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/repository/entity/
git commit -m "feat: add ContentDefinitionEntity and ContentFieldEntity JPA entities"
```

---

### Task 5b.3: Repositories

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/repository/ContentDefinitionRepository.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/repository/ContentFieldRepository.java`

- [ ] **Step 1: Create ContentDefinitionRepository**

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
    Optional<ContentDefinitionEntity> findByNameAndAppIdAndAuditInfoDeletedAtIsNull(String name, UUID appId);
    boolean existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull(String name, UUID appId, UUID id);
}
```

- [ ] **Step 2: Create ContentFieldRepository**

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
    boolean existsByFieldKeyAndDefinitionIdAndIdNotAndAuditInfoDeletedAtIsNull(String fieldKey, UUID definitionId, UUID id);
    int countByDefinitionIdAndAuditInfoDeletedAtIsNull(UUID definitionId);

    @Query("SELECT f FROM ContentFieldEntity f WHERE f.fieldType = 'REFERENCE' AND f.config LIKE %:targetDefinitionId% AND f.auditInfo.deletedAt IS NULL")
    List<ContentFieldEntity> findReferenceFieldsByTargetDefinitionId(String targetDefinitionId);

    @Query("SELECT f FROM ContentFieldEntity f WHERE f.fieldType = 'MIRROR' AND f.config LIKE %:sourceFieldId% AND f.auditInfo.deletedAt IS NULL")
    List<ContentFieldEntity> findMirrorFieldsBySourceFieldId(String sourceFieldId);
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/repository/ContentDefinitionRepository.java apps/backend/src/main/java/io/webcontify/content/definition/repository/ContentFieldRepository.java
git commit -m "feat: add ContentDefinition and ContentField repositories"
```

---

### Task 5b.4: MapStruct Mappers

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/mapper/FieldConfigConverter.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/mapper/ContentDefinitionMapper.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/mapper/ContentFieldMapper.java`

- [ ] **Step 1: Create FieldConfigConverter**

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
                case MIRROR -> objectMapper.readValue(json, MirrorFieldConfig.class);
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

```java
package io.webcontify.content.definition.mapper;

import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.generated.model.*;
import java.util.List;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {ContentFieldMapper.class})
public interface ContentDefinitionMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    ContentDefinition toDomain(ContentDefinitionEntity entity);

    List<ContentDefinition> toDomainList(List<ContentDefinitionEntity> entities);

    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "fields", ignore = true)
    ContentDefinitionEntity toEntity(ContentDefinition domain);

    ContentDefinitionResponse toResponse(ContentDefinition domain);

    List<ContentDefinitionResponse> toResponseList(List<ContentDefinition> domains);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "appId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "displayFormat", ignore = true)
    @Mapping(target = "positionX", ignore = true)
    @Mapping(target = "positionY", ignore = true)
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
import io.webcontify.generated.model.*;
import java.util.List;
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

    public abstract List<FieldDefinition> toDomainList(List<ContentFieldEntity> entities);

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

    public abstract List<FieldDefinitionResponse> toResponseList(List<FieldDefinition> domains);

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

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/mapper/
git commit -m "feat: add ContentDefinition and ContentField MapStruct mappers with FieldConfigConverter"
```

---

### Task 5b.5: Spring Modulith Configuration

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/package-info.java`

- [ ] **Step 1: Create module annotation**

Check existing `apps/backend/src/main/java/io/webcontify/app/package-info.java` for the exact `allowedDependencies` pattern, then create:

```java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"shared", "shared::shared-changelog", "shared::shared-security", "shared::shared-persistence", "shared::shared-util", "app"}
)
package io.webcontify.content;
```

- [ ] **Step 2: Run modulith verification**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.ModulithStructureTest
```

Expected: PASS. If it fails, adjust `allowedDependencies` to match the actual module structure.

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/package-info.java
git commit -m "feat: configure content Spring Modulith module"
```

---

## Sub-Plan 5c: Backend Services (TDD)

### Task 5c.1: ContentFieldConfigValidator (TDD)

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentFieldConfigValidatorTest.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentFieldConfigValidator.java`

- [ ] **Step 1: Write failing tests**

```java
package io.webcontify.content.definition.service;

import io.webcontify.content.definition.model.*;
import io.webcontify.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ContentFieldConfigValidatorTest {

    private ContentFieldConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ContentFieldConfigValidator();
    }

    @Test
    void validate_textValidConfig_passes() {
        var config = new TextFieldConfig(1, 100, null, "hello");
        assertThatNoException().isThrownBy(() -> validator.validate(FieldType.TEXT, config));
    }

    @Test
    void validate_textMinLengthGreaterThanMaxLength_throws() {
        var config = new TextFieldConfig(100, 10, null, null);
        assertThatThrownBy(() -> validator.validate(FieldType.TEXT, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_textInvalidRegexPattern_throws() {
        var config = new TextFieldConfig(null, null, "[invalid", null);
        assertThatThrownBy(() -> validator.validate(FieldType.TEXT, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_textDefaultValueExceedsMaxLength_throws() {
        var config = new TextFieldConfig(null, 5, null, "toolongvalue");
        assertThatThrownBy(() -> validator.validate(FieldType.TEXT, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_integerValidConfig_passes() {
        var config = new IntegerFieldConfig(0L, 100L, 50L);
        assertThatNoException().isThrownBy(() -> validator.validate(FieldType.INTEGER, config));
    }

    @Test
    void validate_integerMinGreaterThanMax_throws() {
        var config = new IntegerFieldConfig(100L, 10L, null);
        assertThatThrownBy(() -> validator.validate(FieldType.INTEGER, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_integerDefaultOutOfRange_throws() {
        var config = new IntegerFieldConfig(0L, 10L, 50L);
        assertThatThrownBy(() -> validator.validate(FieldType.INTEGER, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_decimalPrecisionLessThanScale_throws() {
        var config = new DecimalFieldConfig(null, null, 3, 5, null);
        assertThatThrownBy(() -> validator.validate(FieldType.DECIMAL, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_decimalMinGreaterThanMax_throws() {
        var config = new DecimalFieldConfig(BigDecimal.TEN, BigDecimal.ONE, null, null, null);
        assertThatThrownBy(() -> validator.validate(FieldType.DECIMAL, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_enumEmptyAllowedValues_throws() {
        var config = new EnumFieldConfig(List.of(), null);
        assertThatThrownBy(() -> validator.validate(FieldType.ENUM, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_enumDefaultNotInAllowed_throws() {
        var config = new EnumFieldConfig(List.of("A", "B"), "C");
        assertThatThrownBy(() -> validator.validate(FieldType.ENUM, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_dateMinAfterMax_throws() {
        var config = new DateFieldConfig(
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), null, null);
        assertThatThrownBy(() -> validator.validate(FieldType.DATE, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_referenceNullTargetDefinitionId_throws() {
        var config = new ReferenceFieldConfig(null, RelationType.MANY_TO_ONE, OnDeleteStrategy.CASCADE);
        assertThatThrownBy(() -> validator.validate(FieldType.REFERENCE, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_referenceNullRelationType_throws() {
        var config = new ReferenceFieldConfig(java.util.UUID.randomUUID(), null, OnDeleteStrategy.CASCADE);
        assertThatThrownBy(() -> validator.validate(FieldType.REFERENCE, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_mirrorNullSourceDefinitionId_throws() {
        var config = new MirrorFieldConfig(null, java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        assertThatThrownBy(() -> validator.validate(FieldType.MIRROR, config))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validate_nullConfig_passes() {
        assertThatNoException().isThrownBy(() -> validator.validate(FieldType.TEXT, null));
    }

    @Test
    void validate_booleanConfig_passes() {
        var config = new BooleanFieldConfig(true);
        assertThatNoException().isThrownBy(() -> validator.validate(FieldType.BOOLEAN, config));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentFieldConfigValidatorTest
```

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
            case MirrorFieldConfig c -> validateMirror(c);
        }
    }

    private void validateText(TextFieldConfig config) {
        if (config.minLength() != null && config.maxLength() != null && config.minLength() > config.maxLength()) {
            throw new ValidationException("config.minLength", "error.config.minLength.greaterThanMaxLength");
        }
        if (config.pattern() != null) {
            try {
                Pattern.compile(config.pattern());
            } catch (PatternSyntaxException e) {
                throw new ValidationException("config.pattern", "error.config.pattern.invalidRegex");
            }
        }
        if (config.defaultValue() != null) {
            if (config.minLength() != null && config.defaultValue().length() < config.minLength()) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.belowMinLength");
            }
            if (config.maxLength() != null && config.defaultValue().length() > config.maxLength()) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.exceedsMaxLength");
            }
            if (config.pattern() != null && !config.defaultValue().matches(config.pattern())) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.doesNotMatchPattern");
            }
        }
    }

    private void validateInteger(IntegerFieldConfig config) {
        if (config.min() != null && config.max() != null && config.min() > config.max()) {
            throw new ValidationException("config.min", "error.config.min.greaterThanMax");
        }
        if (config.defaultValue() != null) {
            if (config.min() != null && config.defaultValue() < config.min()) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.belowMin");
            }
            if (config.max() != null && config.defaultValue() > config.max()) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.exceedsMax");
            }
        }
    }

    private void validateDecimal(DecimalFieldConfig config) {
        if (config.min() != null && config.max() != null && config.min().compareTo(config.max()) > 0) {
            throw new ValidationException("config.min", "error.config.min.greaterThanMax");
        }
        if (config.precision() != null && config.scale() != null && config.precision() < config.scale()) {
            throw new ValidationException("config.precision", "error.config.precision.lessThanScale");
        }
        if (config.defaultValue() != null) {
            if (config.min() != null && config.defaultValue().compareTo(config.min()) < 0) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.belowMin");
            }
            if (config.max() != null && config.defaultValue().compareTo(config.max()) > 0) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.exceedsMax");
            }
        }
    }

    private void validateDate(DateFieldConfig config) {
        if (config.minDate() != null && config.maxDate() != null && config.minDate().isAfter(config.maxDate())) {
            throw new ValidationException("config.minDate", "error.config.minDate.afterMaxDate");
        }
        if (config.defaultValue() != null) {
            if (config.minDate() != null && config.defaultValue().isBefore(config.minDate())) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.beforeMinDate");
            }
            if (config.maxDate() != null && config.defaultValue().isAfter(config.maxDate())) {
                throw new ValidationException("config.defaultValue", "error.config.defaultValue.afterMaxDate");
            }
        }
    }

    private void validateEnum(EnumFieldConfig config) {
        if (config.allowedValues() == null || config.allowedValues().isEmpty()) {
            throw new ValidationException("config.allowedValues", "error.config.allowedValues.empty");
        }
        if (config.defaultValue() != null && !config.allowedValues().contains(config.defaultValue())) {
            throw new ValidationException("config.defaultValue", "error.config.defaultValue.notInAllowedValues");
        }
    }

    private void validateReference(ReferenceFieldConfig config) {
        if (config.targetDefinitionId() == null) {
            throw new ValidationException("config.targetDefinitionId", "error.config.targetDefinitionId.required");
        }
        if (config.relationType() == null) {
            throw new ValidationException("config.relationType", "error.config.relationType.required");
        }
        if (config.onDelete() == null) {
            throw new ValidationException("config.onDelete", "error.config.onDelete.required");
        }
    }

    private void validateMirror(MirrorFieldConfig config) {
        if (config.sourceDefinitionId() == null) {
            throw new ValidationException("config.sourceDefinitionId", "error.config.sourceDefinitionId.required");
        }
        if (config.sourceFieldId() == null) {
            throw new ValidationException("config.sourceFieldId", "error.config.sourceFieldId.required");
        }
        if (config.ownerAppId() == null) {
            throw new ValidationException("config.ownerAppId", "error.config.ownerAppId.required");
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentFieldConfigValidatorTest
```

Expected: ALL PASS (17 tests)

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/service/ContentFieldConfigValidator.java apps/backend/src/test/java/io/webcontify/content/definition/service/ContentFieldConfigValidatorTest.java
git commit -m "feat: add ContentFieldConfigValidator with TDD (17 tests)"
```

---

### Task 5c.2: ContentReferenceValidator (TDD)

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentReferenceValidatorTest.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentReferenceValidator.java`

- [ ] **Step 1: Write failing tests**

Test cases:
- Same-app reference: always valid
- Cross-app reference with valid dependency: valid
- Cross-app reference without dependency: throws
- Circular reference within same app: valid
- targetDefinitionId does not exist: throws
- displayFormat with valid placeholders: valid
- displayFormat with unknown placeholder: throws

Follow existing test patterns: `@ExtendWith(MockitoExtension.class)`, `@Mock` for repositories, AssertJ assertions.

Dependencies to mock: `ContentDefinitionRepository`, `AppDependencyRepository` (from app module, read-only).

- [ ] **Step 2: Run tests, verify fail**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentReferenceValidatorTest
```

- [ ] **Step 3: Implement ContentReferenceValidator**

```java
package io.webcontify.content.definition.service;

import io.webcontify.app.repository.AppDependencyRepository;
import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ContentReferenceValidator {

    private static final Pattern DISPLAY_FORMAT_PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final ContentDefinitionRepository definitionRepository;
    private final AppDependencyRepository dependencyRepository;

    public ContentReferenceValidator(
            ContentDefinitionRepository definitionRepository,
            AppDependencyRepository dependencyRepository) {
        this.definitionRepository = definitionRepository;
        this.dependencyRepository = dependencyRepository;
    }

    public void validateReference(UUID sourceAppId, ReferenceFieldConfig config) {
        var targetDefinition = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(config.targetDefinitionId())
                .orElseThrow(() -> new NotFoundException("error.contentDefinition.targetNotFound"));

        UUID targetAppId = targetDefinition.getAppId();

        if (!sourceAppId.equals(targetAppId)) {
            boolean hasDependency = dependencyRepository.existsByAppIdAndDependsOnAppIdAndAuditInfoDeletedAtIsNull(
                    sourceAppId, targetAppId);
            if (!hasDependency) {
                throw new ValidationException("config.targetDefinitionId", "error.contentDefinition.crossAppNoDependency");
            }
        }
    }

    public void validateDisplayFormat(String displayFormat, List<String> existingFieldKeys) {
        if (displayFormat == null || displayFormat.isBlank()) return;

        Matcher matcher = DISPLAY_FORMAT_PLACEHOLDER.matcher(displayFormat);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!existingFieldKeys.contains(placeholder)) {
                throw new ValidationException("displayFormat", "error.contentDefinition.displayFormat.unknownPlaceholder");
            }
        }
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentReferenceValidatorTest
```

Expected: ALL PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/service/ContentReferenceValidator.java apps/backend/src/test/java/io/webcontify/content/definition/service/ContentReferenceValidatorTest.java
git commit -m "feat: add ContentReferenceValidator with TDD (7 tests)"
```

---

### Task 5c.3: ContentDefinitionService (TDD)

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java`

- [ ] **Step 1: Write failing tests**

Test cases (follow AppServiceTest pattern):
- create: generates slug, sets tenantId, saves entity, returns domain model
- create: duplicate name throws ConflictException
- create: writes OpenFGA tuple `content_definition:{id}#parent@app:{appId}`
- create: publishes ContentDefinitionChangedEvent
- create: creates revision via ChangeRecordService
- getById: returns definition with fields
- getById: not found throws NotFoundException
- listByApp: returns all definitions for app
- update: updates name/description/displayFormat, creates revision
- update: name change checks uniqueness
- update: slug remains unchanged
- update: sets app to DRAFT via event
- delete: soft deletes definition
- delete: removes reference fields pointing to this definition (cascade)
- delete: removes MIRROR fields associated with this definition's reference fields
- delete: removes OpenFGA tuple
- delete: publishes ContentDefinitionDeletedEvent
- isNameAvailable: returns true when name is free
- isNameAvailable: returns false when name is taken
- updatePositions: batch updates positionX/positionY

Setup: `@ExtendWith(MockitoExtension.class)`, `@Mock` for all dependencies, static test data, AssertJ assertions.

- [ ] **Step 2: Run tests, verify fail**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentDefinitionServiceTest
```

- [ ] **Step 3: Implement ContentDefinitionService**

```java
package io.webcontify.content.definition.service;

import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.content.definition.event.ContentDefinitionChangedEvent;
import io.webcontify.content.definition.event.ContentDefinitionDeletedEvent;
import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.mapper.ContentFieldMapper;
import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.model.ReferenceFieldConfig;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.generated.model.PositionUpdate;
import io.webcontify.shared.changelog.ChangeRecordService;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.security.TenantInformation;
import io.webcontify.shared.security.permission.PermissionService;
import io.webcontify.shared.util.SlugGenerator;
import io.webcontify.shared.util.UuidGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ContentDefinitionService {

    private final ContentDefinitionRepository definitionRepository;
    private final ContentFieldRepository fieldRepository;
    private final ContentDefinitionMapper definitionMapper;
    private final ContentFieldMapper fieldMapper;
    private final FieldConfigConverter fieldConfigConverter;
    private final SlugGenerator slugGenerator;
    private final UuidGenerator uuidGenerator;
    private final ChangeRecordService changeRecordService;
    private final PermissionService permissionService;
    private final ApplicationEventPublisher eventPublisher;
    private final AppRepository appRepository;

    public ContentDefinitionService(
            ContentDefinitionRepository definitionRepository,
            ContentFieldRepository fieldRepository,
            ContentDefinitionMapper definitionMapper,
            ContentFieldMapper fieldMapper,
            FieldConfigConverter fieldConfigConverter,
            SlugGenerator slugGenerator,
            UuidGenerator uuidGenerator,
            ChangeRecordService changeRecordService,
            PermissionService permissionService,
            ApplicationEventPublisher eventPublisher,
            AppRepository appRepository) {
        this.definitionRepository = definitionRepository;
        this.fieldRepository = fieldRepository;
        this.definitionMapper = definitionMapper;
        this.fieldMapper = fieldMapper;
        this.fieldConfigConverter = fieldConfigConverter;
        this.slugGenerator = slugGenerator;
        this.uuidGenerator = uuidGenerator;
        this.changeRecordService = changeRecordService;
        this.permissionService = permissionService;
        this.eventPublisher = eventPublisher;
        this.appRepository = appRepository;
    }

    public ContentDefinition create(String appSlug, ContentDefinition definition) {
        TenantInformation tenantInfo = TenantContext.getTenantInformation();
        AppEntity app = resolveApp(appSlug);

        if (definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull(definition.getName(), app.getId())) {
            throw new ConflictException("error.contentDefinition.nameAlreadyExists");
        }

        definition.setId(uuidGenerator.generate());
        definition.setTenantId(tenantInfo.tenantId());
        definition.setAppId(app.getId());
        definition.setSlug(slugGenerator.generateUnique(
                definition.getName(),
                slug -> definitionRepository.existsBySlugAndAppIdAndAuditInfoDeletedAtIsNull(slug, app.getId())
        ));

        ContentDefinitionEntity entity = definitionMapper.toEntity(definition);
        entity = definitionRepository.save(entity);

        permissionService.grantRelation("content_definition", entity.getId().toString(), "parent", "app", app.getId().toString());

        ContentDefinition result = definitionMapper.toDomain(entity);
        changeRecordService.create("content_definition", entity.getId(), app.getId(), result);
        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(app.getId(), tenantInfo.tenantId()));

        return result;
    }

    @Transactional(readOnly = true)
    public ContentDefinition getById(String appSlug, UUID definitionId) {
        resolveApp(appSlug);
        ContentDefinitionEntity entity = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(definitionId)
                .orElseThrow(() -> new NotFoundException("error.contentDefinition.notFound"));
        return definitionMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public List<ContentDefinition> listByApp(String appSlug) {
        AppEntity app = resolveApp(appSlug);
        List<ContentDefinitionEntity> entities = definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(app.getId());
        return definitionMapper.toDomainList(entities);
    }

    public ContentDefinition update(String appSlug, UUID definitionId, ContentDefinition updates) {
        TenantInformation tenantInfo = TenantContext.getTenantInformation();
        AppEntity app = resolveApp(appSlug);

        ContentDefinitionEntity entity = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(definitionId)
                .orElseThrow(() -> new NotFoundException("error.contentDefinition.notFound"));

        if (updates.getName() != null && !updates.getName().equals(entity.getName())) {
            if (definitionRepository.existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull(updates.getName(), app.getId(), definitionId)) {
                throw new ConflictException("error.contentDefinition.nameAlreadyExists");
            }
        }

        definitionMapper.updateEntity(entity, updates);
        entity = definitionRepository.save(entity);

        ContentDefinition result = definitionMapper.toDomain(entity);
        changeRecordService.create("content_definition", entity.getId(), app.getId(), result);
        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(app.getId(), tenantInfo.tenantId()));

        return result;
    }

    public void delete(String appSlug, UUID definitionId) {
        TenantInformation tenantInfo = TenantContext.getTenantInformation();
        AppEntity app = resolveApp(appSlug);

        ContentDefinitionEntity entity = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(definitionId)
                .orElseThrow(() -> new NotFoundException("error.contentDefinition.notFound"));

        // Cascade: remove reference fields pointing to this definition
        var referencingFields = fieldRepository.findReferenceFieldsByTargetDefinitionId(definitionId.toString());
        for (var refField : referencingFields) {
            // Also remove paired MIRROR fields
            var mirrorFields = fieldRepository.findMirrorFieldsBySourceFieldId(refField.getId().toString());
            for (var mirrorField : mirrorFields) {
                mirrorField.getAuditInfo().softDelete(tenantInfo.userId());
                fieldRepository.save(mirrorField);
            }
            refField.getAuditInfo().softDelete(tenantInfo.userId());
            fieldRepository.save(refField);
        }

        // Also remove MIRROR fields that reference fields from this definition
        for (var field : entity.getFields()) {
            if ("REFERENCE".equals(field.getFieldType())) {
                var mirrorFields = fieldRepository.findMirrorFieldsBySourceFieldId(field.getId().toString());
                for (var mirrorField : mirrorFields) {
                    mirrorField.getAuditInfo().softDelete(tenantInfo.userId());
                    fieldRepository.save(mirrorField);
                }
            }
        }

        entity.getAuditInfo().softDelete(tenantInfo.userId());
        definitionRepository.save(entity);

        permissionService.revokeRelation("content_definition", entity.getId().toString(), "parent", "app", app.getId().toString());

        changeRecordService.create("content_definition", entity.getId(), app.getId(), null);
        eventPublisher.publishEvent(new ContentDefinitionDeletedEvent(app.getId(), tenantInfo.tenantId(), definitionId));
        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(app.getId(), tenantInfo.tenantId()));
    }

    @Transactional(readOnly = true)
    public boolean isNameAvailable(String appSlug, String name, UUID excludeId) {
        AppEntity app = resolveApp(appSlug);
        if (excludeId != null) {
            return !definitionRepository.existsByNameAndAppIdAndIdNotAndAuditInfoDeletedAtIsNull(name, app.getId(), excludeId);
        }
        return !definitionRepository.existsByNameAndAppIdAndAuditInfoDeletedAtIsNull(name, app.getId());
    }

    public void updatePositions(String appSlug, List<PositionUpdate> positions) {
        resolveApp(appSlug);
        for (PositionUpdate pos : positions) {
            definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(UUID.fromString(pos.getDefinitionId().toString()))
                    .ifPresent(entity -> {
                        entity.setPositionX(pos.getPositionX());
                        entity.setPositionY(pos.getPositionY());
                        definitionRepository.save(entity);
                    });
        }
    }

    private AppEntity resolveApp(String appSlug) {
        return appRepository.findBySlugAndAuditInfoDeletedAtIsNull(appSlug)
                .orElseThrow(() -> new NotFoundException("error.app.notFound"));
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentDefinitionServiceTest
```

Expected: ALL PASS (20 tests)

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java
git commit -m "feat: add ContentDefinitionService with TDD (20 tests)"
```

---

### Task 5c.4: ContentFieldService (TDD)

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentFieldServiceTest.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentFieldService.java`

- [ ] **Step 1: Write failing tests**

Test cases:
- addField: creates field with correct position (next after existing), validates config
- addField: duplicate fieldKey throws ConflictException
- addField: REFERENCE type validates via ContentReferenceValidator + auto-creates MIRROR field on target
- addField: MIRROR type validates via ContentFieldConfigValidator
- updateField: updates fieldKey, config, options
- updateField: fieldType change throws (immutable after creation)
- updateField: creates revision via ChangeRecordService
- updateField: publishes ContentDefinitionChangedEvent
- deleteField: soft deletes field
- deleteField: deleting REFERENCE field also soft-deletes paired MIRROR field
- deleteField: deleting MIRROR field also soft-deletes paired REFERENCE field
- reorder: updates positions for all fields in the given order
- All mutations: publish ContentDefinitionChangedEvent

- [ ] **Step 2: Run tests, verify fail**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentFieldServiceTest
```

- [ ] **Step 3: Implement ContentFieldService**

```java
package io.webcontify.content.definition.service;

import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.content.definition.event.ContentDefinitionChangedEvent;
import io.webcontify.content.definition.mapper.ContentFieldMapper;
import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.*;
import io.webcontify.content.definition.repository.ContentDefinitionRepository;
import io.webcontify.content.definition.repository.ContentFieldRepository;
import io.webcontify.content.definition.repository.entity.ContentDefinitionEntity;
import io.webcontify.content.definition.repository.entity.ContentFieldEntity;
import io.webcontify.shared.changelog.ChangeRecordService;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.exception.ValidationException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.security.TenantInformation;
import io.webcontify.shared.util.UuidGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ContentFieldService {

    private final ContentFieldRepository fieldRepository;
    private final ContentDefinitionRepository definitionRepository;
    private final ContentFieldMapper fieldMapper;
    private final FieldConfigConverter fieldConfigConverter;
    private final ContentFieldConfigValidator configValidator;
    private final ContentReferenceValidator referenceValidator;
    private final UuidGenerator uuidGenerator;
    private final ChangeRecordService changeRecordService;
    private final ApplicationEventPublisher eventPublisher;
    private final AppRepository appRepository;

    public ContentFieldService(
            ContentFieldRepository fieldRepository,
            ContentDefinitionRepository definitionRepository,
            ContentFieldMapper fieldMapper,
            FieldConfigConverter fieldConfigConverter,
            ContentFieldConfigValidator configValidator,
            ContentReferenceValidator referenceValidator,
            UuidGenerator uuidGenerator,
            ChangeRecordService changeRecordService,
            ApplicationEventPublisher eventPublisher,
            AppRepository appRepository) {
        this.fieldRepository = fieldRepository;
        this.definitionRepository = definitionRepository;
        this.fieldMapper = fieldMapper;
        this.fieldConfigConverter = fieldConfigConverter;
        this.configValidator = configValidator;
        this.referenceValidator = referenceValidator;
        this.uuidGenerator = uuidGenerator;
        this.changeRecordService = changeRecordService;
        this.eventPublisher = eventPublisher;
        this.appRepository = appRepository;
    }

    public FieldDefinition addField(String appSlug, UUID definitionId, FieldDefinition field) {
        TenantInformation tenantInfo = TenantContext.getTenantInformation();
        AppEntity app = resolveApp(appSlug);
        ContentDefinitionEntity definition = resolveDefinition(definitionId);

        if (fieldRepository.existsByFieldKeyAndDefinitionIdAndAuditInfoDeletedAtIsNull(field.getFieldKey(), definitionId)) {
            throw new ConflictException("error.field.keyAlreadyExists");
        }

        configValidator.validate(field.getFieldType(), field.getConfig());

        if (field.getFieldType() == FieldType.REFERENCE && field.getConfig() instanceof ReferenceFieldConfig refConfig) {
            referenceValidator.validateReference(app.getId(), refConfig);
        }

        int nextPosition = fieldRepository.countByDefinitionIdAndAuditInfoDeletedAtIsNull(definitionId);

        field.setId(uuidGenerator.generate());
        field.setTenantId(tenantInfo.tenantId());
        field.setDefinitionId(definitionId);
        field.setPosition(nextPosition);

        ContentFieldEntity entity = fieldMapper.toEntity(field);
        entity.setDefinition(definition);
        entity = fieldRepository.save(entity);

        // If REFERENCE: auto-create MIRROR on target definition
        if (field.getFieldType() == FieldType.REFERENCE && field.getConfig() instanceof ReferenceFieldConfig refConfig) {
            createMirrorField(tenantInfo, app, definition, entity, refConfig);
        }

        FieldDefinition result = fieldMapper.toDomain(entity);
        changeRecordService.create("content_definition", definitionId, app.getId(), result);
        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(app.getId(), tenantInfo.tenantId()));

        return result;
    }

    public FieldDefinition updateField(String appSlug, UUID definitionId, UUID fieldId, FieldDefinition updates) {
        TenantInformation tenantInfo = TenantContext.getTenantInformation();
        AppEntity app = resolveApp(appSlug);
        resolveDefinition(definitionId);

        ContentFieldEntity entity = fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId)
                .orElseThrow(() -> new NotFoundException("error.field.notFound"));

        FieldType currentType = FieldType.valueOf(entity.getFieldType());

        if (updates.getFieldType() != null && updates.getFieldType() != currentType) {
            throw new ValidationException("fieldType", "error.field.typeImmutable");
        }

        if (updates.getFieldKey() != null && !updates.getFieldKey().equals(entity.getFieldKey())) {
            if (fieldRepository.existsByFieldKeyAndDefinitionIdAndIdNotAndAuditInfoDeletedAtIsNull(
                    updates.getFieldKey(), definitionId, fieldId)) {
                throw new ConflictException("error.field.keyAlreadyExists");
            }
            entity.setFieldKey(updates.getFieldKey());
        }

        if (updates.getConfig() != null) {
            configValidator.validate(currentType, updates.getConfig());
            entity.setConfig(fieldConfigConverter.toJson(updates.getConfig()));
        }

        entity.setRequired(updates.isRequired());
        entity.setUniqueValue(updates.isUniqueValue());
        entity.setSortable(updates.isSortable());
        entity.setFilterable(updates.isFilterable());

        entity = fieldRepository.save(entity);

        FieldDefinition result = fieldMapper.toDomain(entity);
        changeRecordService.create("content_definition", definitionId, app.getId(), result);
        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(app.getId(), tenantInfo.tenantId()));

        return result;
    }

    public void deleteField(String appSlug, UUID definitionId, UUID fieldId) {
        TenantInformation tenantInfo = TenantContext.getTenantInformation();
        AppEntity app = resolveApp(appSlug);
        resolveDefinition(definitionId);

        ContentFieldEntity entity = fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId)
                .orElseThrow(() -> new NotFoundException("error.field.notFound"));

        // If REFERENCE: also delete paired MIRROR field
        if ("REFERENCE".equals(entity.getFieldType())) {
            var mirrorFields = fieldRepository.findMirrorFieldsBySourceFieldId(fieldId.toString());
            for (var mirrorField : mirrorFields) {
                mirrorField.getAuditInfo().softDelete(tenantInfo.userId());
                fieldRepository.save(mirrorField);
            }
        }

        // If MIRROR: also delete paired REFERENCE field
        if ("MIRROR".equals(entity.getFieldType())) {
            MirrorFieldConfig mirrorConfig = (MirrorFieldConfig) fieldConfigConverter.fromJson(entity.getConfig(), FieldType.MIRROR);
            if (mirrorConfig != null) {
                fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(mirrorConfig.sourceFieldId())
                        .ifPresent(refField -> {
                            // Also delete any other MIRROR fields that reference the same REFERENCE field
                            var otherMirrors = fieldRepository.findMirrorFieldsBySourceFieldId(refField.getId().toString());
                            for (var otherMirror : otherMirrors) {
                                if (!otherMirror.getId().equals(fieldId)) {
                                    otherMirror.getAuditInfo().softDelete(tenantInfo.userId());
                                    fieldRepository.save(otherMirror);
                                }
                            }
                            refField.getAuditInfo().softDelete(tenantInfo.userId());
                            fieldRepository.save(refField);
                        });
            }
        }

        entity.getAuditInfo().softDelete(tenantInfo.userId());
        fieldRepository.save(entity);

        changeRecordService.create("content_definition", definitionId, app.getId(), null);
        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(app.getId(), tenantInfo.tenantId()));
    }

    public List<FieldDefinition> reorderFields(String appSlug, UUID definitionId, List<UUID> fieldIds) {
        TenantInformation tenantInfo = TenantContext.getTenantInformation();
        AppEntity app = resolveApp(appSlug);
        resolveDefinition(definitionId);

        for (int i = 0; i < fieldIds.size(); i++) {
            UUID fieldId = fieldIds.get(i);
            ContentFieldEntity entity = fieldRepository.findByIdAndAuditInfoDeletedAtIsNull(fieldId)
                    .orElseThrow(() -> new NotFoundException("error.field.notFound"));
            entity.setPosition(i);
            fieldRepository.save(entity);
        }

        var fields = fieldRepository.findByDefinitionIdAndAuditInfoDeletedAtIsNullOrderByPositionAsc(definitionId);
        var result = fieldMapper.toDomainList(fields);

        changeRecordService.create("content_definition", definitionId, app.getId(), result);
        eventPublisher.publishEvent(new ContentDefinitionChangedEvent(app.getId(), tenantInfo.tenantId()));

        return result;
    }

    private void createMirrorField(TenantInformation tenantInfo, AppEntity sourceApp,
                                    ContentDefinitionEntity sourceDefinition, ContentFieldEntity referenceField,
                                    ReferenceFieldConfig refConfig) {
        ContentDefinitionEntity targetDefinition = definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(refConfig.targetDefinitionId())
                .orElseThrow(() -> new NotFoundException("error.contentDefinition.targetNotFound"));

        MirrorFieldConfig mirrorConfig = new MirrorFieldConfig(
                sourceDefinition.getId(),
                referenceField.getId(),
                sourceApp.getId()
        );

        int nextMirrorPosition = fieldRepository.countByDefinitionIdAndAuditInfoDeletedAtIsNull(targetDefinition.getId());

        // Default mirrorKey: plural of source definition name (lowercase + "s")
        String mirrorKey = sourceDefinition.getName().toLowerCase().replaceAll("\\s+", "_") + "s";

        ContentFieldEntity mirrorEntity = new ContentFieldEntity();
        mirrorEntity.setId(uuidGenerator.generate());
        mirrorEntity.setTenantId(tenantInfo.tenantId());
        mirrorEntity.setDefinition(targetDefinition);
        mirrorEntity.setFieldKey(mirrorKey);
        mirrorEntity.setFieldType("MIRROR");
        mirrorEntity.setRequired(false);
        mirrorEntity.setUniqueValue(false);
        mirrorEntity.setSortable(false);
        mirrorEntity.setFilterable(false);
        mirrorEntity.setConfig(fieldConfigConverter.toJson(mirrorConfig));
        mirrorEntity.setPosition(nextMirrorPosition);

        fieldRepository.save(mirrorEntity);
    }

    private AppEntity resolveApp(String appSlug) {
        return appRepository.findBySlugAndAuditInfoDeletedAtIsNull(appSlug)
                .orElseThrow(() -> new NotFoundException("error.app.notFound"));
    }

    private ContentDefinitionEntity resolveDefinition(UUID definitionId) {
        return definitionRepository.findByIdAndAuditInfoDeletedAtIsNull(definitionId)
                .orElseThrow(() -> new NotFoundException("error.contentDefinition.notFound"));
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.service.ContentFieldServiceTest
```

Expected: ALL PASS (13 tests)

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/service/ContentFieldService.java apps/backend/src/test/java/io/webcontify/content/definition/service/ContentFieldServiceTest.java
git commit -m "feat: add ContentFieldService with MIRROR auto-creation, TDD (13 tests)"
```

---

## Sub-Plan 5d: Backend Controllers + Integration Tests

### Task 5d.1: ContentDefinitionController

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java`

- [ ] **Step 1: Implement controller**

```java
package io.webcontify.content.definition.api;

import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.model.ContentDefinition;
import io.webcontify.content.definition.service.ContentDefinitionService;
import io.webcontify.generated.api.ContentDefinitionApi;
import io.webcontify.generated.model.*;
import io.webcontify.shared.security.permission.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class ContentDefinitionController implements ContentDefinitionApi {

    private final ContentDefinitionService definitionService;
    private final ContentDefinitionMapper mapper;

    public ContentDefinitionController(ContentDefinitionService definitionService, ContentDefinitionMapper mapper) {
        this.definitionService = definitionService;
        this.mapper = mapper;
    }

    @Override
    @RequiresPermission(action = "creator", resource = "app", resourceId = "#appSlug")
    public ResponseEntity<ContentDefinitionResponse> createContentDefinition(
            String appSlug, CreateContentDefinitionRequest request) {
        ContentDefinition domain = mapper.toDomain(request);
        ContentDefinition created = definitionService.create(appSlug, domain);
        ContentDefinitionResponse response = mapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appSlug")
    public ResponseEntity<ContentDefinitionListResponse> listContentDefinitions(String appSlug) {
        List<ContentDefinition> definitions = definitionService.listByApp(appSlug);
        ContentDefinitionListResponse response = new ContentDefinitionListResponse();
        response.setItems(mapper.toResponseList(definitions));
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<ContentDefinitionResponse> getContentDefinition(String appSlug, UUID definitionId) {
        ContentDefinition definition = definitionService.getById(appSlug, definitionId);
        return ResponseEntity.ok(mapper.toResponse(definition));
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<ContentDefinitionResponse> updateContentDefinition(
            String appSlug, UUID definitionId, UpdateContentDefinitionRequest request) {
        ContentDefinition updates = mapper.toDomain(request);
        ContentDefinition updated = definitionService.update(appSlug, definitionId, updates);
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @Override
    @RequiresPermission(action = "deleter", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<Void> deleteContentDefinition(String appSlug, UUID definitionId) {
        definitionService.delete(appSlug, definitionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "viewer", resource = "app", resourceId = "#appSlug")
    public ResponseEntity<NameAvailabilityResponse> checkContentDefinitionNameAvailability(
            String appSlug, String name, UUID excludeId) {
        boolean available = definitionService.isNameAvailable(appSlug, name, excludeId);
        NameAvailabilityResponse response = new NameAvailabilityResponse();
        response.setAvailable(available);
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "app", resourceId = "#appSlug")
    public ResponseEntity<Void> batchUpdatePositions(String appSlug, UpdatePositionsRequest request) {
        definitionService.updatePositions(appSlug, request.getPositions());
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "editor", resource = "app", resourceId = "#appSlug")
    public ResponseEntity<UndoRedoResponse> undoContentChange(String appSlug) {
        // Delegates to ChangeRecordService undo (implemented in 5e)
        // Placeholder: will be connected in Task 5e
        return ResponseEntity.notFound().build();
    }

    @Override
    @RequiresPermission(action = "editor", resource = "app", resourceId = "#appSlug")
    public ResponseEntity<UndoRedoResponse> redoContentChange(String appSlug) {
        // Delegates to ChangeRecordService redo (implemented in 5e)
        // Placeholder: will be connected in Task 5e
        return ResponseEntity.notFound().build();
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java
git commit -m "feat: add ContentDefinitionController implementing generated interface"
```

---

### Task 5d.2: ContentFieldController

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/api/ContentFieldController.java`

- [ ] **Step 1: Implement controller**

```java
package io.webcontify.content.definition.api;

import io.webcontify.content.definition.mapper.ContentDefinitionMapper;
import io.webcontify.content.definition.mapper.ContentFieldMapper;
import io.webcontify.content.definition.mapper.FieldConfigConverter;
import io.webcontify.content.definition.model.FieldDefinition;
import io.webcontify.content.definition.model.FieldType;
import io.webcontify.content.definition.service.ContentDefinitionService;
import io.webcontify.content.definition.service.ContentFieldService;
import io.webcontify.generated.api.ContentFieldApi;
import io.webcontify.generated.model.*;
import io.webcontify.shared.security.permission.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class ContentFieldController implements ContentFieldApi {

    private final ContentFieldService fieldService;
    private final ContentFieldMapper fieldMapper;
    private final ContentDefinitionService definitionService;
    private final ContentDefinitionMapper definitionMapper;
    private final FieldConfigConverter fieldConfigConverter;

    public ContentFieldController(
            ContentFieldService fieldService,
            ContentFieldMapper fieldMapper,
            ContentDefinitionService definitionService,
            ContentDefinitionMapper definitionMapper,
            FieldConfigConverter fieldConfigConverter) {
        this.fieldService = fieldService;
        this.fieldMapper = fieldMapper;
        this.definitionService = definitionService;
        this.definitionMapper = definitionMapper;
        this.fieldConfigConverter = fieldConfigConverter;
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<FieldDefinitionResponse> addField(
            String appSlug, UUID definitionId, CreateFieldRequest request) {
        FieldDefinition field = new FieldDefinition();
        field.setFieldKey(request.getFieldKey());
        field.setFieldType(FieldType.valueOf(request.getFieldType().name()));
        field.setRequired(Boolean.TRUE.equals(request.getRequired()));
        field.setUniqueValue(Boolean.TRUE.equals(request.getUnique()));
        field.setSortable(Boolean.TRUE.equals(request.getSortable()));
        field.setFilterable(Boolean.TRUE.equals(request.getFilterable()));
        if (request.getConfig() != null) {
            field.setConfig(fieldConfigConverter.fromApiConfig(request.getConfig(), field.getFieldType()));
        }

        FieldDefinition created = fieldService.addField(appSlug, definitionId, field);
        FieldDefinitionResponse response = fieldMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<FieldDefinitionResponse> updateField(
            String appSlug, UUID definitionId, UUID fieldId, UpdateFieldRequest request) {
        FieldDefinition updates = new FieldDefinition();
        updates.setFieldKey(request.getFieldKey());
        updates.setRequired(Boolean.TRUE.equals(request.getRequired()));
        updates.setUniqueValue(Boolean.TRUE.equals(request.getUnique()));
        updates.setSortable(Boolean.TRUE.equals(request.getSortable()));
        updates.setFilterable(Boolean.TRUE.equals(request.getFilterable()));
        // Note: fieldType is not settable on update (immutable)

        FieldDefinition updated = fieldService.updateField(appSlug, definitionId, fieldId, updates);
        return ResponseEntity.ok(fieldMapper.toResponse(updated));
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<Void> deleteField(String appSlug, UUID definitionId, UUID fieldId) {
        fieldService.deleteField(appSlug, definitionId, fieldId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "editor", resource = "content_definition", resourceId = "#definitionId")
    public ResponseEntity<ContentDefinitionResponse> reorderFields(
            String appSlug, UUID definitionId, ReorderFieldsRequest request) {
        fieldService.reorderFields(appSlug, definitionId, request.getFieldIds());
        var definition = definitionService.getById(appSlug, definitionId);
        return ResponseEntity.ok(definitionMapper.toResponse(definition));
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/api/ContentFieldController.java
git commit -m "feat: add ContentFieldController implementing generated interface"
```

---

### Task 5d.3: Integration Tests

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/content/definition/api/ContentDefinitionControllerIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

Follow AppControllerIntegrationTest pattern:
- `@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(TestSecurityConfiguration.class)`
- `@Container @ServiceConnection PostgreSQLContainer<>("postgres:17-alpine")`
- `@MockitoBean PermissionService`
- Setup: create a test tenant + test app in `@BeforeEach`

Test cases:
- createAndRetrieveDefinition: POST 201 + GET 200 (verify slug generation, fields empty)
- createWithDuplicateName: POST 409
- updateDefinition: PUT 200 (verify name change, slug unchanged)
- deleteDefinition: DELETE 204 + GET 404
- checkNameAvailability: GET 200 (available: true/false)
- addTextField: POST 201 with TEXT config
- addReferenceField: POST 201 with REFERENCE config (verify MIRROR auto-created)
- updateField: PUT 200 (change fieldKey, verify fieldType unchanged)
- deleteField: DELETE 204
- reorderFields: PUT 200 (verify new order)
- fieldConfigValidation: POST 400 with invalid config (minLength > maxLength)
- batchUpdatePositions: PUT 204

- [ ] **Step 2: Run integration tests**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.content.definition.api.ContentDefinitionControllerIntegrationTest
```

Expected: ALL PASS (12 tests)

- [ ] **Step 3: Run full backend test suite**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/content/definition/api/ContentDefinitionControllerIntegrationTest.java
git commit -m "test: add Content Definition integration tests (12 tests)"
```

---

## Sub-Plan 5e: Backend App Integration

### Task 5e.1: Domain Events

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/event/ContentDefinitionChangedEvent.java`
- Create: `apps/backend/src/main/java/io/webcontify/content/definition/event/ContentDefinitionDeletedEvent.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/` (add event listener)

- [ ] **Step 1: Create events**

```java
package io.webcontify.content.definition.event;

import java.util.UUID;

public record ContentDefinitionChangedEvent(UUID appId, UUID tenantId) {}
```

```java
package io.webcontify.content.definition.event;

import java.util.UUID;

public record ContentDefinitionDeletedEvent(UUID appId, UUID tenantId, UUID definitionId) {}
```

- [ ] **Step 2: Add listener in App module**

Create `apps/backend/src/main/java/io/webcontify/app/listener/AppContentListener.java`:

```java
package io.webcontify.app.listener;

import io.webcontify.app.service.AppService;
import io.webcontify.content.definition.event.ContentDefinitionChangedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AppContentListener {

    private final AppService appService;

    public AppContentListener(AppService appService) {
        this.appService = appService;
    }

    @ApplicationModuleListener
    public void onContentDefinitionChanged(ContentDefinitionChangedEvent event) {
        appService.setDraftStatus(event.appId());
    }
}
```

Note: Verify `AppService` has a `setDraftStatus(UUID appId)` method or equivalent. If not, add it following the existing pattern for status updates.

- [ ] **Step 3: Test event wiring**

Write a test that publishes `ContentDefinitionChangedEvent` and verifies the App status is set to DRAFT.

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/event/ apps/backend/src/main/java/io/webcontify/app/listener/AppContentListener.java
git commit -m "feat: add Content Definition domain events with App integration"
```

---

### Task 5e.2: AppSnapshot Extension

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/app/model/AppSnapshot.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java`

- [ ] **Step 1: Extend AppSnapshotDefinition with full field details**

In `AppSnapshot.java`, extend the existing `AppSnapshotDefinition` record (or inner class) to include:
- `description` (String)
- `displayFormat` (String)

Extend `AppSnapshotField` (or create it) to include:
- `fieldKey` (String)
- `fieldType` (String)
- `required` (boolean)
- `uniqueValue` (boolean)
- `sortable` (boolean)
- `filterable` (boolean)
- `config` (String, full JSON)
- `position` (int)

Check existing `AppSnapshot.java` for the current structure and extend it. Do NOT break existing serialization (additive changes only).

- [ ] **Step 2: Populate snapshot on publish**

In `AppVersionService.publish()`, after existing logic:
1. Load all ContentDefinitions for the app via `ContentDefinitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(appId)`
2. Map each definition + its fields to `AppSnapshotDefinition` with full `AppSnapshotField` list
3. Set on the `AppSnapshot` object before serialization

- [ ] **Step 3: Implement rollback reconciliation**

In `AppVersionService.rollback()`, implement the 9-step reconciliation algorithm from the design doc:
1. Load snapshot's `contentDefinitions` list (desired state)
2. Load current definitions from DB (actual state)
3. Definitions in snapshot but not in DB: Re-create (restore soft-deleted or insert)
4. Definitions in DB but not in snapshot: Soft-delete
5. Definitions in both: Overwrite current with snapshot (name, description, displayFormat, all fields + configs)
6. Dangling reference fields: Auto-remove
7. Topological sort for processing order (definitions without references first)
8. Single transaction
9. OpenFGA tuple cleanup (remove for deleted, add for restored)

- [ ] **Step 4: Write tests for snapshot publish/rollback**

Test cases:
- publish: snapshot includes all definitions with full fields
- rollback: definitions restored from snapshot
- rollback: new definitions soft-deleted
- rollback: missing definitions re-created
- rollback: dangling references auto-removed

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/app/model/AppSnapshot.java apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java
git commit -m "feat: extend AppSnapshot with full Content Definition data, implement rollback reconciliation"
```

---

### Task 5e.3: Permission Integration

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java`

- [ ] **Step 1: Add content_definition to RESOURCE_ACTIONS**

In `RoleService.java`, find the `RESOURCE_ACTIONS` map and add:
```java
"content_definition", List.of("viewer", "editor", "admin", "creator", "deleter")
```

- [ ] **Step 2: Extend getEffectivePermissions**

In `AuthService.java`, find the BatchCheck query and add `content_definition` permissions to the batch check (same pattern as existing `app` permissions).

- [ ] **Step 3: Run existing permission tests to ensure no regression**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.identity.service.RoleServiceTest
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.identity.service.AuthServiceTest
```

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java
git commit -m "feat: extend RoleService and AuthService with content_definition permissions"
```

---

### Task 5e.4: Undo/Redo Implementation

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java`

- [ ] **Step 1: Implement undo/redo in service**

Add methods to `ContentDefinitionService`:
```java
public UndoRedoResult undo(String appSlug) {
    AppEntity app = resolveApp(appSlug);
    TenantInformation tenantInfo = TenantContext.getTenantInformation();
    // Use ChangeRecordService to find the last applied change for this app with entityType="content_definition"
    // Apply inverse patch
    // Return result with canUndo/canRedo flags
}

public UndoRedoResult redo(String appSlug) {
    AppEntity app = resolveApp(appSlug);
    TenantInformation tenantInfo = TenantContext.getTenantInformation();
    // Use ChangeRecordService to find the last undone change for this app with entityType="content_definition"
    // Re-apply forward patch
    // Return result with canUndo/canRedo flags
}
```

Note: This depends on the existing `ChangeRecordService` having undo/redo support. If not, extend it following `AppRevisionService.computeReversePatch()` pattern.

- [ ] **Step 2: Wire undo/redo in controller**

Update the placeholder methods in `ContentDefinitionController.java` to delegate to the service methods.

- [ ] **Step 3: Test undo/redo**

Test cases:
- undo: reverts last change
- redo: re-applies last undone change
- undo with nothing to undo: returns 404
- redo with nothing to redo: returns 404

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java
git commit -m "feat: implement undo/redo for content definition changes via ChangeRecordService"
```

---

## Sub-Plan 5f: Frontend (Graph UI)

### Task 5f.1: Orval Config + Code Generation

**Files:**
- Modify: `apps/platform/orval.config.ts`

- [ ] **Step 1: Add content API to Orval config**

```typescript
content: {
  input: {
    target: "../../openapi/content-api.yaml",
  },
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

```bash
cd apps/platform && pnpm generate
```

Expected: `src/generated/content.ts` created with hooks for all content endpoints.

- [ ] **Step 3: Commit**

```bash
git add apps/platform/orval.config.ts apps/platform/src/generated/content.ts
git commit -m "feat: add content API to Orval config and generate hooks"
```

---

### Task 5f.2: i18n Keys

**Files:**
- Modify: `apps/platform/src/messages/en.json`
- Modify: `apps/platform/src/messages/de.json`

- [ ] **Step 1: Add content definition i18n keys (EN)**

Add to `en.json` under a top-level `"content"` key:

```json
"content": {
  "title": "Content Definitions",
  "tab": "Content",
  "addDefinition": "Create new definition",
  "noDefinitions": "No content definitions yet",
  "noDefinitionsDescription": "Create your first definition to start building your content structure.",
  "createFirst": "Create your first definition",
  "desktopRecommended": "Desktop recommended for graph editing",
  "definition": {
    "name": "Name",
    "description": "Description",
    "displayFormat": "Display Format",
    "displayFormatHint": "Use {fieldKey} placeholders, e.g. \"{title} — {author}\"",
    "delete": "Delete Definition",
    "deleteConfirm": "This will delete the definition \"{name}\" and remove all reference fields pointing to it.",
    "deleteWithReferences": "{count} reference fields will be removed",
    "nameExists": "A definition with this name already exists",
    "created": "Definition created",
    "updated": "Definition updated",
    "deleted": "Definition deleted"
  },
  "field": {
    "title": "Fields",
    "addField": "Add Field",
    "noFields": "No fields defined",
    "fieldKey": "Field Key",
    "fieldKeyHint": "Lowercase, a-z, 0-9, underscore",
    "fieldType": "Field Type",
    "required": "Required",
    "unique": "Unique",
    "sortable": "Sortable",
    "filterable": "Filterable",
    "config": "Configuration",
    "delete": "Delete Field",
    "deleteConfirm": "Are you sure you want to delete the field \"{fieldKey}\"?",
    "typeImmutable": "Field type cannot be changed after creation",
    "keyExists": "A field with this key already exists",
    "created": "Field added",
    "updated": "Field updated",
    "deleted": "Field deleted",
    "reordered": "Fields reordered"
  },
  "fieldType": {
    "TEXT": "Text",
    "INTEGER": "Integer",
    "DECIMAL": "Decimal",
    "BOOLEAN": "Boolean",
    "DATE": "Date",
    "ENUM": "Enum",
    "REFERENCE": "Reference",
    "MIRROR": "Mirror"
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
    "onDelete": "On Delete",
    "sourceDefinition": "Source Definition",
    "sourceField": "Source Field",
    "ownerApp": "Owner App"
  },
  "relationType": {
    "ONE_TO_ONE": "One-to-One (1:1)",
    "MANY_TO_ONE": "Many-to-One (N:1)",
    "MANY_TO_MANY": "Many-to-Many (N:M)"
  },
  "onDelete": {
    "CASCADE": "Cascade",
    "SET_NULL": "Set Null",
    "RESTRICT": "Restrict"
  },
  "graph": {
    "resetLayout": "Reset Layout",
    "zoomIn": "Zoom In",
    "zoomOut": "Zoom Out",
    "fitView": "Fit View",
    "minimap": "Toggle Minimap",
    "ghostNodeBadge": "From: {appName}",
    "removeGhost": "Remove from canvas",
    "removeGhostWithDependency": "Removing this will also delete the dependency on {appName}. Continue?",
    "removeGhostBlocked": "Remove reference fields pointing to this definition first",
    "spotlight": "Search definitions...",
    "spotlightCreate": "Create new definition",
    "spotlightOwnApp": "This App",
    "spotlightOtherApps": "Other Apps"
  },
  "connect": {
    "title": "Create Reference",
    "referenceFieldKey": "Reference Field Key",
    "mirrorFieldKey": "Mirror Field Key",
    "relationType": "Relation Type",
    "onDelete": "On Delete Strategy",
    "create": "Create Reference",
    "cancel": "Cancel"
  },
  "undoRedo": {
    "undo": "Undo",
    "redo": "Redo",
    "undone": "Change undone",
    "redone": "Change redone",
    "nothingToUndo": "Nothing to undo",
    "nothingToRedo": "Nothing to redo"
  }
}
```

- [ ] **Step 2: Add DE translation**

Add to `de.json` the same structure with German translations:

```json
"content": {
  "title": "Content Definitionen",
  "tab": "Content",
  "addDefinition": "Neue Definition erstellen",
  "noDefinitions": "Noch keine Content Definitionen",
  "noDefinitionsDescription": "Erstelle deine erste Definition, um deine Content-Struktur aufzubauen.",
  "createFirst": "Erste Definition erstellen",
  "desktopRecommended": "Desktop empfohlen fuer die Graph-Bearbeitung",
  "definition": {
    "name": "Name",
    "description": "Beschreibung",
    "displayFormat": "Anzeigeformat",
    "displayFormatHint": "Verwende {fieldKey} Platzhalter, z.B. \"{title} — {author}\"",
    "delete": "Definition loeschen",
    "deleteConfirm": "Die Definition \"{name}\" wird geloescht und alle Referenzfelder, die darauf zeigen, werden entfernt.",
    "deleteWithReferences": "{count} Referenzfelder werden entfernt",
    "nameExists": "Eine Definition mit diesem Namen existiert bereits",
    "created": "Definition erstellt",
    "updated": "Definition aktualisiert",
    "deleted": "Definition geloescht"
  },
  "field": {
    "title": "Felder",
    "addField": "Feld hinzufuegen",
    "noFields": "Keine Felder definiert",
    "fieldKey": "Feld-Schluessel",
    "fieldKeyHint": "Kleinbuchstaben, a-z, 0-9, Unterstrich",
    "fieldType": "Feldtyp",
    "required": "Pflichtfeld",
    "unique": "Eindeutig",
    "sortable": "Sortierbar",
    "filterable": "Filterbar",
    "config": "Konfiguration",
    "delete": "Feld loeschen",
    "deleteConfirm": "Moechtest du das Feld \"{fieldKey}\" wirklich loeschen?",
    "typeImmutable": "Der Feldtyp kann nach der Erstellung nicht mehr geaendert werden",
    "keyExists": "Ein Feld mit diesem Schluessel existiert bereits",
    "created": "Feld hinzugefuegt",
    "updated": "Feld aktualisiert",
    "deleted": "Feld geloescht",
    "reordered": "Felder umsortiert"
  },
  "fieldType": {
    "TEXT": "Text",
    "INTEGER": "Ganzzahl",
    "DECIMAL": "Dezimalzahl",
    "BOOLEAN": "Wahrheitswert",
    "DATE": "Datum",
    "ENUM": "Aufzaehlung",
    "REFERENCE": "Referenz",
    "MIRROR": "Spiegel"
  },
  "config": {
    "minLength": "Mindestlaenge",
    "maxLength": "Maximallaenge",
    "pattern": "Muster (Regex)",
    "defaultValue": "Standardwert",
    "min": "Minimum",
    "max": "Maximum",
    "precision": "Praezision",
    "scale": "Nachkommastellen",
    "minDate": "Fruehestes Datum",
    "maxDate": "Spaetestes Datum",
    "dateFormat": "Datumsformat",
    "allowedValues": "Erlaubte Werte",
    "targetDefinition": "Ziel-Definition",
    "relationType": "Relationstyp",
    "onDelete": "Bei Loeschung",
    "sourceDefinition": "Quell-Definition",
    "sourceField": "Quell-Feld",
    "ownerApp": "Besitzer-App"
  },
  "relationType": {
    "ONE_TO_ONE": "Eins-zu-Eins (1:1)",
    "MANY_TO_ONE": "Viele-zu-Eins (N:1)",
    "MANY_TO_MANY": "Viele-zu-Viele (N:M)"
  },
  "onDelete": {
    "CASCADE": "Kaskadieren",
    "SET_NULL": "Auf Null setzen",
    "RESTRICT": "Verhindern"
  },
  "graph": {
    "resetLayout": "Layout zuruecksetzen",
    "zoomIn": "Vergroessern",
    "zoomOut": "Verkleinern",
    "fitView": "Ansicht anpassen",
    "minimap": "Minimap umschalten",
    "ghostNodeBadge": "Von: {appName}",
    "removeGhost": "Von Canvas entfernen",
    "removeGhostWithDependency": "Das Entfernen loescht auch die Abhaengigkeit zu {appName}. Fortfahren?",
    "removeGhostBlocked": "Entferne zuerst die Referenzfelder, die auf diese Definition zeigen",
    "spotlight": "Definitionen suchen...",
    "spotlightCreate": "Neue Definition erstellen",
    "spotlightOwnApp": "Diese App",
    "spotlightOtherApps": "Andere Apps"
  },
  "connect": {
    "title": "Referenz erstellen",
    "referenceFieldKey": "Referenz-Feldschluessel",
    "mirrorFieldKey": "Spiegel-Feldschluessel",
    "relationType": "Relationstyp",
    "onDelete": "Loeschstrategie",
    "create": "Referenz erstellen",
    "cancel": "Abbrechen"
  },
  "undoRedo": {
    "undo": "Rueckgaengig",
    "redo": "Wiederherstellen",
    "undone": "Aenderung rueckgaengig gemacht",
    "redone": "Aenderung wiederhergestellt",
    "nothingToUndo": "Nichts zum Rueckgaengig machen",
    "nothingToRedo": "Nichts zum Wiederherstellen"
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/messages/en.json apps/platform/src/messages/de.json
git commit -m "feat: add content definition i18n keys (EN + DE)"
```

---

### Task 5f.3: Zod Schemas

**Files:**
- Create: `apps/platform/src/features/content/schemas/content-schemas.ts`

- [ ] **Step 1: Create Zod schemas**

```typescript
import { z } from "zod";

export const createDefinitionSchema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().max(2000).optional().or(z.literal("")),
});

export const updateDefinitionSchema = z.object({
  name: z.string().min(1).max(255).optional(),
  description: z.string().max(2000).optional().or(z.literal("")),
  displayFormat: z.string().max(500).optional().or(z.literal("")),
});

export const fieldKeySchema = z
  .string()
  .min(1)
  .max(255)
  .regex(/^[a-z][a-z0-9_]*$/, "Must start with lowercase letter, only a-z, 0-9, _");

export const createFieldSchema = z.object({
  fieldKey: fieldKeySchema,
  fieldType: z.enum(["TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "ENUM"]),
  required: z.boolean().default(false),
  unique: z.boolean().default(false),
  sortable: z.boolean().default(false),
  filterable: z.boolean().default(false),
  config: z.record(z.unknown()).optional(),
});

export const connectReferenceSchema = z.object({
  referenceFieldKey: fieldKeySchema,
  mirrorFieldKey: fieldKeySchema,
  relationType: z.enum(["ONE_TO_ONE", "MANY_TO_ONE", "MANY_TO_MANY"]),
  onDelete: z.enum(["CASCADE", "SET_NULL", "RESTRICT"]),
});

export type CreateDefinitionForm = z.infer<typeof createDefinitionSchema>;
export type UpdateDefinitionForm = z.infer<typeof updateDefinitionSchema>;
export type CreateFieldForm = z.infer<typeof createFieldSchema>;
export type ConnectReferenceForm = z.infer<typeof connectReferenceSchema>;
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/content/schemas/content-schemas.ts
git commit -m "feat: add content definition Zod schemas"
```

---

### Task 5f.4: Definition Node Component

**Files:**
- Create: `apps/platform/src/features/content/components/definition-node.tsx`

- [ ] **Step 1: Create DefinitionNode**

Custom React Flow node (DatabaseSchemaNode pattern) showing:
- Header: definition name, edit icon on hover, delete icon on hover
- Compact field list: fieldKey + type icon per row, max 5 visible, "+N more" indicator
- Each field row: source handle (right side) + target handle (left side) for field-level connections
- "+" button at bottom for adding fields
- Hover on header: pencil icon appears
- Hover on field: edit + delete icons appear
- Double-click header: fires `onEditDefinition` callback
- Double-click field: fires `onEditField` callback
- Width: 280px
- MIRROR fields: italic, reverse-arrow icon, "virtual" badge
- REFERENCE fields: arrow-right icon

Use `@xyflow/react` Handle component with unique IDs per field (e.g., `field-{fieldId}-source`, `field-{fieldId}-target`).

Use `useTranslations("content")` for all text. Use `cn()` for conditional classes.

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/content/components/definition-node.tsx
git commit -m "feat: add DefinitionNode React Flow component with field-level handles"
```

---

### Task 5f.5: Ghost Definition Node Component

**Files:**
- Create: `apps/platform/src/features/content/components/ghost-definition-node.tsx`

- [ ] **Step 1: Create GhostDefinitionNode**

Same layout as DefinitionNode but:
- Dashed border (`border-dashed border-stone-300 dark:border-stone-600`)
- Reduced opacity (`opacity-60`)
- Badge showing source app name (e.g. "From: Core") using `useTranslations("content.graph")`
- No hover edit/delete icons on fields -- read-only
- No "+" button for adding fields
- "x" (remove) button in header for removing ghost from canvas
- Target handles on left side only (ghost nodes are always targets of references, never sources)

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/content/components/ghost-definition-node.tsx
git commit -m "feat: add GhostDefinitionNode React Flow component"
```

---

### Task 5f.6: Reference Edge Component

**Files:**
- Create: `apps/platform/src/features/content/components/reference-edge.tsx`

- [ ] **Step 1: Create ReferenceEdge**

Custom React Flow edge:
- Label showing relation type abbreviation (1:1, N:1, N:M)
- Same-app edges: solid line, indigo color
- Cross-app edges: dashed line, emerald color
- Click on edge label: fires `onEditReference` callback (opens reference popover)
- SmoothStep edge path with border-radius
- Edge label positioned at midpoint

Follow existing `dependency-edge.tsx` pattern from `apps/platform/src/features/apps/components/dependency-edge.tsx`.

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/content/components/reference-edge.tsx
git commit -m "feat: add ReferenceEdge React Flow component with relation type labels"
```

---

### Task 5f.7: Spotlight Search Component

**Files:**
- Create: `apps/platform/src/features/content/components/content-spotlight.tsx`

- [ ] **Step 1: Create ContentSpotlight**

Command-palette style component:
- Triggered by "+" button click in toolbar
- Search input with autofocus
- Results grouped:
  - **"Create new definition"** -- always at top
  - **Own App definitions** -- existing definitions (clicking centers viewport on node)
  - **Other Apps** -- grouped by app name, showing their definitions
- Keyboard navigation (arrow keys + Enter + Escape)
- Escape or click outside: dismiss
- On "Create new definition": fires `onCreateDefinition` callback
- On other-app definition: fires `onAddGhostNode` callback with definition data

Data: Uses `useListApps()` for app list, `useListContentDefinitions(appSlug)` for each app's definitions.

Uses `@base-ui/react/popover` for overlay, following existing popover patterns in the codebase.

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/content/components/content-spotlight.tsx
git commit -m "feat: add ContentSpotlight search component for definitions and ghost nodes"
```

---

### Task 5f.8: Connect Dialog (Reference Creation Popover)

**Files:**
- Create: `apps/platform/src/features/content/components/connect-dialog.tsx`

- [ ] **Step 1: Create ConnectDialog**

Popover that appears at the drop point after a drag-connect:
- Two-column form:
  - **Source side:** referenceFieldKey input (e.g. "author")
  - **Target side:** mirrorFieldKey input (default: plural of source definition name)
- Relation type dropdown: ONE_TO_ONE / MANY_TO_ONE / MANY_TO_MANY
- onDelete strategy dropdown: CASCADE / SET_NULL / RESTRICT
- Create + Cancel buttons
- Uses `connectReferenceSchema` for validation
- On Create: calls `addField` mutation with REFERENCE config, which auto-creates MIRROR via backend

Uses React Hook Form + zodResolver + `connectReferenceSchema`.

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/content/components/connect-dialog.tsx
git commit -m "feat: add ConnectDialog popover for drag-and-connect reference creation"
```

---

### Task 5f.9: Definition Popover + Field Popover

**Files:**
- Create: `apps/platform/src/features/content/components/definition-popover.tsx`
- Create: `apps/platform/src/features/content/components/field-popover.tsx`
- Create: `apps/platform/src/features/content/components/field-config-form.tsx`

- [ ] **Step 1: Create DefinitionPopover**

Anchored to node header on double-click:
- Name input (with inline name-availability check via `useCheckContentDefinitionNameAvailability`)
- Description textarea
- DisplayFormat input with hint text
- Save + Cancel buttons (or auto-save on blur)
- Uses `updateDefinitionSchema` + React Hook Form

- [ ] **Step 2: Create FieldConfigForm**

Dynamic form that renders different inputs based on fieldType:
- TEXT: minLength, maxLength, pattern, defaultValue
- INTEGER: min, max, defaultValue
- DECIMAL: min, max, precision, scale, defaultValue
- BOOLEAN: defaultValue toggle
- DATE: minDate, maxDate, dateFormat, defaultValue date pickers
- ENUM: allowedValues tag input (comma-separated or tag UI), defaultValue dropdown
- REFERENCE: relationType dropdown, onDelete dropdown (target is read-only text)
- MIRROR: read-only display of sourceDefinition, sourceField, ownerApp

- [ ] **Step 3: Create FieldPopover**

Anchored to field row on double-click:
- fieldKey input (editable)
- fieldType display (read-only, immutable after creation)
- Toggle switches: required, unique, sortable, filterable
- FieldConfigForm (dynamic based on type)
- Delete field button at bottom
- Save + Cancel buttons

- [ ] **Step 4: Create AddFieldPopover**

Triggered by "+" button on node:
- Field type selector (6 types: TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, ENUM -- no REFERENCE, no MIRROR)
- fieldKey input
- Type-specific config section (via FieldConfigForm)
- Toggle switches: required, unique, sortable, filterable
- Save → fires `addField` mutation

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/features/content/components/definition-popover.tsx apps/platform/src/features/content/components/field-popover.tsx apps/platform/src/features/content/components/field-config-form.tsx
git commit -m "feat: add definition and field popover components with type-specific config forms"
```

---

### Task 5f.10: Content Definition Graph (Main Component)

**Files:**
- Create: `apps/platform/src/features/content/components/content-definition-graph.tsx`

- [ ] **Step 1: Create ContentDefinitionGraph**

Main React Flow canvas component:
- Custom nodeTypes: `{ definition: DefinitionNode, ghost: GhostDefinitionNode }`
- Custom edgeTypes: `{ reference: ReferenceEdge }`
- `useNodesState` + `useEdgesState` from `@xyflow/react`
- Data transformation: `listContentDefinitions` response -> nodes + edges
  - Own definitions -> `type: "definition"` nodes
  - Ghost nodes from dependency apps -> `type: "ghost"` nodes (definitions from dependent apps that have REFERENCE fields pointing to them)
  - REFERENCE fields -> edges with `type: "reference"` from source field handle to target definition
- ELK layout via `computeElkLayout()` from existing `use-elk-layout.ts` (direction: "DOWN" for ER diagram)
- Position persistence: use stored `positionX`/`positionY` from backend. Null = use ELK auto-layout.
- **Debounced position save:** `onNodesChange` -> debounce 500ms -> `batchUpdatePositions` mutation
- **Snap-to-grid:** `snapToGrid={true} snapGrid={[20, 20]}`
- **Drag-and-connect:** `onConnect` handler -> open ConnectDialog at drop point
- **Connection validation:** `isValidConnection` -> no self-references, target must be definition node
- **Undo/Redo:** Keyboard listener for Ctrl+Z (undo) and Ctrl+Y/Ctrl+Shift+Z (redo) -> call `undoContentChange`/`redoContentChange` mutations
- Toolbar: "+" (spotlight), zoom controls, "Reset Layout" (ELK), minimap toggle
- Empty state: centered illustration + "No content definitions yet" + CTA button
- Background: `<Background variant="dots" />`
- Controls: `<Controls />`
- Minimap: `<MiniMap />` (toggleable)
- Ghost node removal: "x" button on ghost -> check if references exist. If last ghost from that app and no references, show confirmation dialog, then call `removeAppDependency` mutation.
- Auto-dependency creation: when ghost node is added via spotlight, call `addAppDependency` mutation (with cycle check error handling).

- [ ] **Step 2: Wire popover state**

Manage popover state with `useState`:
- `editingDefinition: { id: UUID, anchorEl: Element } | null`
- `editingField: { definitionId: UUID, fieldId: UUID, anchorEl: Element } | null`
- `addingField: { definitionId: UUID, anchorEl: Element } | null`
- `connectDialog: { sourceId: UUID, targetId: UUID, position: {x,y} } | null`
- `spotlightOpen: boolean`

Render popover components conditionally based on state.

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/features/content/components/content-definition-graph.tsx
git commit -m "feat: add ContentDefinitionGraph canvas with drag-connect, spotlight, popovers, undo/redo"
```

---

### Task 5f.11: Content Tab on App Detail Page

**Files:**
- Modify: `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx`

- [ ] **Step 1: Add "Content" tab**

In the existing App Detail Page tabs:
1. Add `TabsTrigger` with value `"content"` and label from `t("content.tab")`
2. Add `TabsContent` with `<ContentDefinitionGraph appSlug={app.slug} appId={app.id} />`
3. Position after "Overview" tab (or as the second tab)
4. Import `ContentDefinitionGraph` from `@/features/content/components/content-definition-graph`

The Content tab should take the full available height (flex-1, h-full) for the canvas.

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/app/(workspace)/apps/[appId]/page.tsx
git commit -m "feat: add Content tab to App Detail Page with graph canvas"
```

---

### Task 5f.12: Frontend Tests

**Files:**
- Create: `apps/platform/src/features/content/components/__tests__/definition-node.test.tsx`
- Create: `apps/platform/src/features/content/components/__tests__/field-config-form.test.tsx`
- Create: `apps/platform/src/features/content/schemas/__tests__/content-schemas.test.ts`

- [ ] **Step 1: Write Vitest tests**

Test cases:
- DefinitionNode renders definition name and field list
- DefinitionNode shows "+N more" when fields exceed 5
- DefinitionNode shows MIRROR fields with italic styling
- GhostDefinitionNode renders with dashed border and app badge
- FieldConfigForm renders correct inputs for TEXT type (minLength, maxLength, pattern, defaultValue)
- FieldConfigForm renders correct inputs for ENUM type (allowedValues, defaultValue)
- FieldConfigForm renders REFERENCE type as read-only for MIRROR fields
- Zod schemas: createDefinitionSchema validates correctly
- Zod schemas: connectReferenceSchema validates correctly
- Zod schemas: fieldKeySchema rejects invalid keys

- [ ] **Step 2: Run tests**

```bash
cd apps/platform && pnpm vitest run
```

Expected: ALL PASS

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/features/content/components/__tests__/ apps/platform/src/features/content/schemas/__tests__/
git commit -m "test: add Content Definition frontend tests (10 tests)"
```

---

## Sub-Plan 5g: Frontend Permissions + Revisions + Dependencies Cleanup

### Task 5g.1: Permission Matrix Extension

**Files:**
- Modify: `apps/platform/src/features/roles/components/role-permissions-editor.tsx` (or equivalent)

- [ ] **Step 1: Extend permission matrix with content_definition**

Add hierarchical structure:
1. Load apps via existing `useListApps()` hook
2. For each app, load definitions via `useListContentDefinitions(appSlug)`
3. Render collapsible app sections with definition toggles underneath
4. Permission actions for content_definition: viewer, editor, admin, creator, deleter
5. Use `useTranslations("content")` for labels

The UI should show:
```
App: Blog App
  ├── Content Definitions
  │   ├── Blog Post: [ ] viewer [x] editor [ ] admin
  │   └── Category: [ ] viewer [x] editor [ ] admin
  └── ...
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/roles/components/
git commit -m "feat: extend permission matrix with hierarchical content_definition permissions"
```

---

### Task 5g.2: Revisions Tab Extension

**Files:**
- Modify: `apps/platform/src/features/apps/components/app-revision-list.tsx` (or equivalent revision component)

- [ ] **Step 1: Extend revision display**

In the existing App Revisions tab:
1. Add type icon/badge to distinguish "app" vs "content_definition" revisions
2. Content definition revisions show: definition name, change summary (field added/removed/modified)
3. Revert button works for both types (calls same endpoint)
4. Use different icons: app icon for app revisions, document/schema icon for content definition revisions

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/apps/components/
git commit -m "feat: extend Revisions tab with content_definition revision type"
```

---

### Task 5g.3: Dependencies Tab Read-Only + Dead Code Cleanup

**Files:**
- Modify: `apps/platform/src/features/apps/components/app-dependency-graph.tsx`
- Delete: `apps/platform/src/features/apps/components/add-dependency-node.tsx`
- Delete: `apps/platform/src/features/apps/components/add-dependency-popover.tsx`
- Delete: `apps/platform/src/features/apps/components/remove-dependency-dialog.tsx`

- [ ] **Step 1: Make Dependencies tab read-only**

In `app-dependency-graph.tsx`:
1. Remove the "+" (AddDependencyNode) from the graph
2. Remove edge delete buttons (DependencyEdge delete functionality)
3. Remove `onConnect` handler
4. Keep the graph visualization intact (nodes + edges)
5. Add a subtle info banner: "Dependencies are managed via the Content tab"

- [ ] **Step 2: Delete dead code**

```bash
rm apps/platform/src/features/apps/components/add-dependency-node.tsx
rm apps/platform/src/features/apps/components/add-dependency-popover.tsx
rm apps/platform/src/features/apps/components/remove-dependency-dialog.tsx
```

Remove any imports of these files from `app-dependency-graph.tsx` or other files.

- [ ] **Step 3: Verify build**

```bash
cd apps/platform && pnpm build
```

Expected: BUILD SUCCESSFUL (no import errors)

- [ ] **Step 4: Commit**

```bash
git add -A apps/platform/src/features/apps/components/
git commit -m "refactor: make Dependencies tab read-only and remove dead code (add/remove dependency components)"
```

---

## Sub-Plan 5h: E2E Tests (Playwright)

### Task 5h.1: E2E Test Helpers

**Files:**
- Create: `apps/e2e/helpers/content-api.ts`

- [ ] **Step 1: Create API helpers**

```typescript
import { Page } from "@playwright/test";

export async function createContentDefinitionViaApi(
  page: Page,
  appSlug: string,
  data: { name: string; description?: string }
) {
  const response = await page.request.post(
    `http://api.localhost/api/v1/content/${appSlug}/definitions`,
    { data }
  );
  return response.json();
}

export async function addFieldViaApi(
  page: Page,
  appSlug: string,
  definitionId: string,
  data: { fieldKey: string; fieldType: string; config?: Record<string, unknown> }
) {
  const response = await page.request.post(
    `http://api.localhost/api/v1/content/${appSlug}/definitions/${definitionId}/fields`,
    { data }
  );
  return response.json();
}

export async function deleteContentDefinitionViaApi(
  page: Page,
  appSlug: string,
  definitionId: string
) {
  await page.request.delete(
    `http://api.localhost/api/v1/content/${appSlug}/definitions/${definitionId}`
  );
}
```

Follow existing `apps/e2e/helpers/app-api.ts` pattern for auth headers and base URL.

- [ ] **Step 2: Commit**

```bash
git add apps/e2e/helpers/content-api.ts
git commit -m "test: add content definition E2E API helpers"
```

---

### Task 5h.2: BDU Test 1 -- Content Definition CRUD

**Files:**
- Create: `apps/e2e/tests/content/content-crud.spec.ts`

- [ ] **Step 1: Write E2E test**

```typescript
import { test, expect } from "@playwright/test";

test.describe("Content Definition CRUD", () => {
  test("create, edit, and delete a content definition via graph", async ({ page }) => {
    // 1. Navigate to app detail page -> Content tab
    await page.goto("http://platform.localhost/apps/{testAppId}");
    await page.getByRole("tab", { name: "Content" }).click();

    // 2. See empty state
    await expect(page.getByTestId("content-empty-state")).toBeVisible();

    // 3. Click "+" button -> Spotlight opens
    await page.getByTestId("content-spotlight-trigger").click();
    await expect(page.getByTestId("content-spotlight")).toBeVisible();

    // 4. Select "Create new definition"
    await page.getByTestId("content-spotlight-create").click();

    // 5. Name popover opens, type name
    await page.getByTestId("definition-name-input").fill("Blog Post");
    await page.getByTestId("definition-save-button").click();

    // 6. Node appears in graph
    await expect(page.getByTestId("definition-node-Blog Post")).toBeVisible();

    // 7. Double-click header -> edit popover
    await page.getByTestId("definition-node-Blog Post").dblclick();
    await page.getByTestId("definition-description-input").fill("Blog post content type");
    await page.getByTestId("definition-save-button").click();

    // 8. Delete definition
    await page.getByTestId("definition-node-Blog Post").hover();
    await page.getByTestId("definition-delete-button").click();
    await page.getByTestId("confirm-delete-button").click();

    // 9. Node disappears
    await expect(page.getByTestId("definition-node-Blog Post")).not.toBeVisible();
    await expect(page.getByTestId("content-empty-state")).toBeVisible();
  });
});
```

Use `data-testid` selectors, English text only. Adapt the test app ID and navigation to match the existing E2E test setup (check `apps/e2e/tests/apps/` for patterns).

- [ ] **Step 2: Commit**

```bash
git add apps/e2e/tests/content/content-crud.spec.ts
git commit -m "test: add Content Definition CRUD E2E test"
```

---

### Task 5h.3: BDU Test 2 -- Field Management

**Files:**
- Create: `apps/e2e/tests/content/content-fields.spec.ts`

- [ ] **Step 1: Write E2E test**

Steps from BDU Test 2 (design doc section 7):
1. Create a definition via API (setup)
2. Click "+" on definition node -> Add Field popover
3. Select TEXT, type "title" as fieldKey
4. Set Required toggle ON, maxLength to 200
5. Save -> field appears on node
6. Add another field "content" (TEXT)
7. Double-click "title" field -> field popover opens
8. Change maxLength to 255 -> save
9. Delete "content" field via popover delete button
10. Verify field list shows only "title"

Use `data-testid` selectors.

- [ ] **Step 2: Commit**

```bash
git add apps/e2e/tests/content/content-fields.spec.ts
git commit -m "test: add Content Definition field management E2E test"
```

---

### Task 5h.4: BDU Test 3 -- Drag-and-Connect References

**Files:**
- Create: `apps/e2e/tests/content/content-references.spec.ts`

- [ ] **Step 1: Write E2E test**

Steps from BDU Test 3 (design doc section 7):
1. Setup: App "Blog" with definition "Post" (via API). App "Core" exists with definition "User".
2. Click "+" -> Spotlight -> select "User" from Core
3. Ghost node appears, verify dashed border
4. Drag connection from "Post" field handle to ghost node "User"
5. Connect Dialog: type "author", select MANY_TO_ONE, SET_NULL
6. Save -> edge appears with "N:1" label
7. Check Dependencies tab -> "Core" listed
8. Remove reference field "author" from "Post"
9. Remove ghost node "User" -> confirm dependency deletion
10. Dependencies tab -> "Core" no longer listed

Note: Drag-and-connect in Playwright requires `page.mouse.move()` sequences. Use React Flow node/handle positions from DOM.

- [ ] **Step 2: Commit**

```bash
git add apps/e2e/tests/content/content-references.spec.ts
git commit -m "test: add Content Definition drag-and-connect reference E2E test"
```

---

### Task 5h.5: BDU Test 4 -- Permissions

**Files:**
- Create: `apps/e2e/tests/content/content-permissions.spec.ts`

- [ ] **Step 1: Write E2E test**

Steps from BDU Test 4:
1. Login as tenant admin, navigate to Roles -> "Editor" role
2. See Permission Matrix with Content section
3. Set "Editor" permission on a content definition
4. Save
5. Login as user with Editor role
6. Can edit definition and fields (popovers work, "+" button visible)
7. Other definitions: view-only (no edit icons on hover)

- [ ] **Step 2: Commit**

```bash
git add apps/e2e/tests/content/content-permissions.spec.ts
git commit -m "test: add Content Definition permissions E2E test"
```

---

### Task 5h.6: BDU Test 5 -- Revisions + Publish

**Files:**
- Create: `apps/e2e/tests/content/content-revisions.spec.ts`

- [ ] **Step 1: Write E2E test**

Steps from BDU Test 5:
1. Make changes to a definition (add field, rename)
2. Go to Revisions tab -> see definition revisions with type label
3. Revert one revision -> change undone
4. Publish app -> revisions cleared
5. Make new change -> new revision appears
6. Rollback to previous version -> definitions restored from snapshot

- [ ] **Step 2: Commit**

```bash
git add apps/e2e/tests/content/content-revisions.spec.ts
git commit -m "test: add Content Definition revisions and publish E2E test"
```

---

### Task 5h.7: BDU Test 6 -- Graph Canvas Interactions

**Files:**
- Create: `apps/e2e/tests/content/content-graph.spec.ts`

- [ ] **Step 1: Write E2E test**

Steps from BDU Test 6:
1. Create multiple definitions (via API for setup)
2. Verify graph renders nodes and edges
3. Ghost nodes have dashed border and app badge
4. Hover own definition -> edit/delete icons appear
5. Hover ghost node -> only "x" remove, no edit icons
6. Drag node to new position -> verify snap-to-grid
7. Reload page -> positions preserved
8. Use minimap
9. Zoom in/out
10. Click "Reset Layout" -> nodes rearrange (ELK)
11. Open spotlight via "+" -> keyboard navigate, Escape dismisses

- [ ] **Step 2: Commit**

```bash
git add apps/e2e/tests/content/content-graph.spec.ts
git commit -m "test: add Content Definition graph canvas interaction E2E test"
```

---

## Final Verification

- [ ] **Run full backend test suite:**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

- [ ] **Run full frontend test suite:**

```bash
cd apps/platform && pnpm vitest run
```

- [ ] **Run frontend build:**

```bash
cd apps/platform && pnpm build
```

- [ ] **Run lint:**

```bash
nx lint platform
```

- [ ] **Run E2E tests:**

```bash
cd apps/e2e && npx playwright test tests/content/
```

- [ ] **Update PROGRESS.md** with Phase 5 tasks (completed/open/next steps)
- [ ] **Update MEMORY.md** if new architectural insights were discovered
