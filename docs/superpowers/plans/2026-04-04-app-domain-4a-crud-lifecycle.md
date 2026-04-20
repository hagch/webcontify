# App Domain 4a: CRUD + Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement App CRUD with Draft/Active/Inactive lifecycle, including backend (OpenAPI, JPA, Service, Controller) and frontend (list page, detail page, create dialog).

**Architecture:** New Spring Modulith module `app/` following existing Tenant domain patterns. Contract-first (OpenAPI → generated interfaces). SlugGenerator moved to shared for reuse. TDD throughout.

**Tech Stack:** Spring Boot 4, JPA, MapStruct, Flyway, OpenAPI Generator, Next.js 16, React Query (Orval), shadcn/ui, Vitest

**Design Doc:** `docs/plans/2026-04-04-app-domain-design.md`

**Sub-Plan Dependencies:** None (this is the foundation)

---

## File Structure

### Backend — New Files
- `apps/backend/src/main/resources/db/migration/V20260404_001__create_app_table.sql`
- `openapi/app-api.yaml`
- `apps/backend/src/main/java/io/webcontify/app/model/App.java`
- `apps/backend/src/main/java/io/webcontify/app/model/AppStatus.java`
- `apps/backend/src/main/java/io/webcontify/app/repository/entity/AppEntity.java`
- `apps/backend/src/main/java/io/webcontify/app/repository/AppRepository.java`
- `apps/backend/src/main/java/io/webcontify/app/mapper/AppMapper.java`
- `apps/backend/src/main/java/io/webcontify/app/service/AppService.java`
- `apps/backend/src/main/java/io/webcontify/app/api/AppController.java`
- `apps/backend/src/test/java/io/webcontify/app/service/AppServiceTest.java`
- `apps/backend/src/test/java/io/webcontify/app/api/AppControllerIntegrationTest.java`

### Backend — Modified Files
- `apps/backend/src/main/java/io/webcontify/shared/util/SlugGenerator.java` (moved from tenant)
- `apps/backend/src/main/java/io/webcontify/tenant/service/TenantService.java` (import update)
- `apps/backend/build.gradle.kts` (new OpenAPI generation task)

### Frontend — New Files
- `apps/platform/src/features/apps/schemas/create-app.ts`
- `apps/platform/src/features/apps/components/app-table.tsx`
- `apps/platform/src/features/apps/components/app-table-skeleton.tsx`
- `apps/platform/src/features/apps/components/create-app-dialog.tsx`
- `apps/platform/src/app/(workspace)/apps/page.tsx` (replace placeholder)
- `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx`
- `apps/platform/src/features/apps/components/__tests__/app-table.test.tsx`

### Frontend — Modified Files
- `apps/platform/orval.config.ts` (add app API)
- `apps/platform/src/components/layout/nav-config.ts` (add Apps nav item with permission)
- `apps/platform/messages/en.json` (add apps namespace)
- `apps/platform/messages/de.json` (add apps namespace)

---

## Task 1: Move SlugGenerator to Shared Module

The `SlugGenerator` currently lives in `io.webcontify.tenant.service`. The App domain needs it too. Move to `shared.util` to avoid cross-module coupling.

**Files:**
- Move: `apps/backend/src/main/java/io/webcontify/tenant/service/SlugGenerator.java` → `apps/backend/src/main/java/io/webcontify/shared/util/SlugGenerator.java`
- Modify: `apps/backend/src/main/java/io/webcontify/tenant/service/TenantService.java` (import update)
- Modify: `apps/backend/src/test/java/io/webcontify/tenant/service/TenantServiceTest.java` (import update)
- Test: `apps/backend/src/test/java/io/webcontify/shared/util/SlugGeneratorTest.java` (move existing tests)

- [ ] **Step 1: Move SlugGenerator to shared.util**

Move the file and update the package declaration:

```java
package io.webcontify.shared.util;

import java.text.Normalizer;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class SlugGenerator {
    // Exact same implementation, only package changes
}
```

- [ ] **Step 2: Update imports in TenantService**

In `TenantService.java`, change:
```java
// OLD
import io.webcontify.tenant.service.SlugGenerator;
// NEW
import io.webcontify.shared.util.SlugGenerator;
```

- [ ] **Step 3: Update imports in TenantServiceTest**

Same import change in `TenantServiceTest.java`.

- [ ] **Step 4: Move SlugGeneratorTest to shared package**

Move existing test (if any) to `apps/backend/src/test/java/io/webcontify/shared/util/SlugGeneratorTest.java` and update package.

- [ ] **Step 5: Run all tests to verify nothing broke**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: All existing tests pass

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: move SlugGenerator to shared.util for cross-module reuse"
```

---

## Task 2: Flyway Migration — App Table

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V20260404_001__create_app_table.sql`

- [ ] **Step 1: Create migration file**

```sql
-- App table
CREATE TABLE app (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    description     TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    has_draft       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    UNIQUE(tenant_id, name),
    UNIQUE(tenant_id, slug)
);

-- RLS (same pattern as tenant_user/tenant_role: combined policy with NULLIF for empty string safety)
ALTER TABLE app ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_app ON app
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
```

- [ ] **Step 2: Verify migration runs**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: Flyway migration applies successfully, tests pass

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/resources/db/migration/V20260404_001__create_app_table.sql
git commit -m "feat: add Flyway migration for app table with RLS"
```

---

## Task 3: OpenAPI Spec — App API

**Files:**
- Create: `openapi/app-api.yaml`
- Modify: `apps/backend/build.gradle.kts` (add generation task)

- [ ] **Step 1: Create app-api.yaml**

```yaml
openapi: 3.1.0
info:
  title: App API
  version: 1.0.0
  description: App management endpoints for tenant workspace
servers:
  - url: http://localhost:8080

paths:
  /api/v1/apps:
    post:
      operationId: createApp
      tags: [App]
      summary: Create a new app
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateAppRequest'
      responses:
        '201':
          description: App created
          headers:
            Location:
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppResponse'
        '400':
          $ref: './common-schemas.yaml#/components/responses/ValidationError'
        '409':
          $ref: './common-schemas.yaml#/components/responses/ConflictError'
    get:
      operationId: listApps
      tags: [App]
      summary: List all apps in the tenant
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
        - name: status
          in: query
          schema:
            $ref: '#/components/schemas/AppStatus'
      responses:
        '200':
          description: List of apps
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppListResponse'

  /api/v1/apps/{appId}:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: getApp
      tags: [App]
      summary: Get app details
      responses:
        '200':
          description: App details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppResponse'
        '404':
          $ref: './common-schemas.yaml#/components/responses/NotFoundError'
    put:
      operationId: updateApp
      tags: [App]
      summary: Update app metadata
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateAppRequest'
      responses:
        '200':
          description: App updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppResponse'
        '400':
          $ref: './common-schemas.yaml#/components/responses/ValidationError'
        '404':
          $ref: './common-schemas.yaml#/components/responses/NotFoundError'
        '409':
          $ref: './common-schemas.yaml#/components/responses/ConflictError'
    delete:
      operationId: deleteApp
      tags: [App]
      summary: Soft-delete an app
      responses:
        '204':
          description: App deleted
        '404':
          $ref: './common-schemas.yaml#/components/responses/NotFoundError'
        '409':
          description: App has dependants
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/apps/{appId}/status:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    put:
      operationId: updateAppStatus
      tags: [App]
      summary: Change app status (activate, deactivate)
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateAppStatusRequest'
      responses:
        '200':
          description: Status updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppResponse'
        '400':
          $ref: './common-schemas.yaml#/components/responses/ValidationError'
        '404':
          $ref: './common-schemas.yaml#/components/responses/NotFoundError'

  /api/v1/apps/{appId}/name-available:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: checkAppNameAvailability
      tags: [App]
      summary: Check if app name is available
      parameters:
        - name: name
          in: query
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Availability result
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/NameAvailabilityResponse'

  /api/v1/apps/name-available:
    get:
      operationId: checkNewAppNameAvailability
      tags: [App]
      summary: Check if app name is available (for new apps)
      parameters:
        - name: name
          in: query
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Availability result
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/NameAvailabilityResponse'

components:
  schemas:
    AppStatus:
      type: string
      enum: [DRAFT, ACTIVE, INACTIVE]

    CreateAppRequest:
      type: object
      required: [name]
      properties:
        name:
          type: string
          minLength: 1
          maxLength: 255
        description:
          type: string
          maxLength: 2000

    UpdateAppRequest:
      type: object
      required: [name]
      properties:
        name:
          type: string
          minLength: 1
          maxLength: 255
        description:
          type: string
          maxLength: 2000

    UpdateAppStatusRequest:
      type: object
      required: [status]
      properties:
        status:
          $ref: '#/components/schemas/AppStatus'

    NameAvailabilityResponse:
      $ref: './common-schemas.yaml#/components/schemas/NameAvailabilityResponse'
      # NOTE: Move NameAvailabilityResponse from tenant-api.yaml to common-schemas.yaml first,
      # then reference it here. Both tenant-api and app-api share the same schema.

    AppResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        slug:
          type: string
        description:
          type: string
        status:
          $ref: '#/components/schemas/AppStatus'
        hasDraft:
          type: boolean
        createdAt:
          type: string
          format: date-time
        createdBy:
          type: string
          format: uuid
        updatedAt:
          type: string
          format: date-time
        updatedBy:
          type: string
          format: uuid

    AppListResponse:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/AppResponse'
        pageInfo:
          $ref: './common-schemas.yaml#/components/schemas/PageInfo'
```

- [ ] **Step 2: Add OpenAPI generation task to build.gradle.kts**

Add a new `openApiGenerate` task for app-api.yaml following the same pattern as the existing tenant-api task. The task should:
- Input: `$rootProject/openapi/app-api.yaml`
- Output package: `io.webcontify.generated.api` and `io.webcontify.generated.model`
- Same config: `interfaceOnly=true`, `useSpringBoot3=true`, `useTags=true`

- [ ] **Step 3: Run code generation and verify**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate`
Expected: Generated files in `build/generated/openapi/` including `AppApi.java`, `AppResponse.java`, `CreateAppRequest.java`, etc.

- [ ] **Step 4: Commit**

```bash
git add openapi/app-api.yaml apps/backend/build.gradle.kts
git commit -m "feat: add OpenAPI spec for App API with CRUD + status endpoints"
```

---

## Task 4: Domain Model + JPA Entity

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/model/App.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/model/AppStatus.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/repository/entity/AppEntity.java`

- [ ] **Step 1: Create AppStatus enum**

```java
package io.webcontify.app.model;

public enum AppStatus {
    DRAFT,
    ACTIVE,
    INACTIVE
}
```

- [ ] **Step 2: Create App domain model**

```java
package io.webcontify.app.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class App {

    private UUID id;
    private UUID tenantId;
    private String name;
    private String slug;
    private String description;
    private AppStatus status;
    private boolean hasDraft;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;

    // All getters and setters
}
```

- [ ] **Step 3: Create AppEntity**

```java
package io.webcontify.app.repository.entity;

import io.webcontify.shared.persistence.TenantAwareBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "app")
public class AppEntity extends TenantAwareBaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "has_draft", nullable = false)
    private boolean hasDraft;

    // All getters and setters
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: Compiles successfully

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/app/model/ apps/backend/src/main/java/io/webcontify/app/repository/entity/
git commit -m "feat: add App domain model, AppStatus enum, and AppEntity"
```

---

## Task 5: Repository + Mapper

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/repository/AppRepository.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/mapper/AppMapper.java`

- [ ] **Step 1: Create AppRepository**

```java
package io.webcontify.app.repository;

import io.webcontify.app.repository.entity.AppEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppRepository extends JpaRepository<AppEntity, UUID> {

    Optional<AppEntity> findByIdAndAuditInfoDeletedAtIsNull(UUID id);

    Optional<AppEntity> findBySlugAndAuditInfoDeletedAtIsNull(String slug);

    boolean existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull(String name, UUID tenantId);

    boolean existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull(String slug, UUID tenantId);

    Page<AppEntity> findByAuditInfoDeletedAtIsNull(Pageable pageable);

    Page<AppEntity> findByStatusAndAuditInfoDeletedAtIsNull(String status, Pageable pageable);
}
```

- [ ] **Step 2: Create AppMapper**

```java
package io.webcontify.app.mapper;

import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.generated.model.AppResponse;
import io.webcontify.generated.model.CreateAppRequest;
import io.webcontify.generated.model.UpdateAppRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface AppMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    @Mapping(source = "tenantId", target = "tenantId")
    @Mapping(source = "status", target = "status", qualifiedByName = "stringToAppStatus")
    App toDomain(AppEntity entity);

    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(source = "status", target = "status", qualifiedByName = "appStatusToString")
    AppEntity toEntity(App app);

    @Mapping(source = "status", target = "status", qualifiedByName = "domainStatusToApiStatus")
    AppResponse toResponse(App app);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "hasDraft", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    App toDomain(CreateAppRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "hasDraft", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    App toDomain(UpdateAppRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "hasDraft", ignore = true)
    void updateEntity(@MappingTarget AppEntity entity, App app);

    @Named("stringToAppStatus")
    default AppStatus stringToAppStatus(String status) {
        return status == null ? null : AppStatus.valueOf(status);
    }

    @Named("appStatusToString")
    default String appStatusToString(AppStatus status) {
        return status == null ? null : status.name();
    }

    @Named("domainStatusToApiStatus")
    default io.webcontify.generated.model.AppStatus domainStatusToApiStatus(AppStatus status) {
        return status == null ? null : io.webcontify.generated.model.AppStatus.fromValue(status.name());
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: Compiles, MapStruct generates `AppMapperImpl`

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/app/repository/AppRepository.java apps/backend/src/main/java/io/webcontify/app/mapper/AppMapper.java
git commit -m "feat: add AppRepository and AppMapper (MapStruct)"
```

---

## Task 6: AppService — TDD

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/app/service/AppServiceTest.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/service/AppService.java`

- [ ] **Step 1: Write failing tests for create**

```java
package io.webcontify.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.mapper.AppMapperImpl;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.util.SlugGenerator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppServiceTest {

    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    @Mock
    private AppRepository appRepository;

    private AppMapper appMapper = new AppMapperImpl();
    private SlugGenerator slugGenerator = new SlugGenerator();

    private AppService appService;

    @BeforeEach
    void setUp() {
        // Set TenantContext via thread-local (same pattern as existing services)
        TenantContext.setTenantInformation(new TenantInformation(TEST_TENANT_ID, false, "test-realm"));
        appService = new AppService(appRepository, appMapper, slugGenerator);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateAppWithDraftStatusAndGeneratedSlug() {
        App input = new App();
        input.setName("My Blog");

        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull(anyString(), any()))
                .thenReturn(false);
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        App result = appService.create(input);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("My Blog");
        assertThat(result.getSlug()).isEqualTo("my-blog");
        assertThat(result.getStatus()).isEqualTo(AppStatus.DRAFT);
        assertThat(result.isHasDraft()).isFalse();
    }

    @Test
    void shouldThrowConflictOnDuplicateName() {
        App input = new App();
        input.setName("Existing App");

        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull("Existing App", TEST_TENANT_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> appService.create(input))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("app.name_already_exists");
    }

    @Test
    void shouldReturnAppById() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = new AppEntity();
        entity.setId(appId);
        entity.setName("Test");
        entity.setSlug("test");
        entity.setStatus("DRAFT");

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId))
                .thenReturn(Optional.of(entity));

        App result = appService.getById(appId);

        assertThat(result.getName()).isEqualTo("Test");
    }

    @Test
    void shouldThrowNotFoundForMissingApp() {
        UUID appId = UUID.randomUUID();
        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appService.getById(appId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("app.not_found");
    }

    @Test
    void shouldUpdateAppNameAndDescription() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = new AppEntity();
        entity.setId(appId);
        entity.setName("Old Name");
        entity.setSlug("old-name");
        entity.setStatus("DRAFT");

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId))
                .thenReturn(Optional.of(entity));
        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull(anyString(), any()))
                .thenReturn(false);
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        App update = new App();
        update.setName("New Name");
        update.setDescription("New description");

        App result = appService.update(appId, update);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getDescription()).isEqualTo("New description");
    }

    @Test
    void shouldActivateAppFromDraft() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = new AppEntity();
        entity.setId(appId);
        entity.setStatus("DRAFT");

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId))
                .thenReturn(Optional.of(entity));
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        App result = appService.updateStatus(appId, AppStatus.ACTIVE);

        assertThat(result.getStatus()).isEqualTo(AppStatus.ACTIVE);
    }

    @Test
    void shouldDeactivateActiveApp() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = new AppEntity();
        entity.setId(appId);
        entity.setStatus("ACTIVE");

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId))
                .thenReturn(Optional.of(entity));
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        App result = appService.updateStatus(appId, AppStatus.INACTIVE);

        assertThat(result.getStatus()).isEqualTo(AppStatus.INACTIVE);
    }

    @Test
    void shouldSoftDeleteApp() {
        UUID appId = UUID.randomUUID();
        AppEntity entity = new AppEntity();
        entity.setId(appId);
        entity.setStatus("DRAFT");

        when(appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId))
                .thenReturn(Optional.of(entity));
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        appService.delete(appId);

        verify(appRepository).save(any(AppEntity.class));
    }

    @Test
    void shouldCheckNameAvailability() {
        when(appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull("Available", TEST_TENANT_ID))
                .thenReturn(false);

        assertThat(appService.isNameAvailable("Available")).isTrue();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.app.service.AppServiceTest`
Expected: Compilation error — `AppService` does not exist yet

- [ ] **Step 3: Implement AppService**

```java
package io.webcontify.app.service;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.entity.AppEntity;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.util.SlugGenerator;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppService {

    private final AppRepository appRepository;
    private final AppMapper appMapper;
    private final SlugGenerator slugGenerator;

    public AppService(
            AppRepository appRepository,
            AppMapper appMapper,
            SlugGenerator slugGenerator) {
        this.appRepository = appRepository;
        this.appMapper = appMapper;
        this.slugGenerator = slugGenerator;
    }

    public App create(App app) {
        var tenantInfo = TenantContext.getTenantInformation();
        UUID tenantId = tenantInfo.tenantId();
        checkNameUniqueness(app.getName(), tenantId);

        String slug = slugGenerator.generateUnique(
                app.getName(),
                s -> appRepository.existsBySlugAndTenantIdAndAuditInfoDeletedAtIsNull(s, tenantId));

        app.setId(UuidGenerator.generate());
        app.setTenantId(tenantId);
        app.setSlug(slug);
        app.setStatus(AppStatus.DRAFT);
        app.setHasDraft(false);

        AppEntity entity = appMapper.toEntity(app);
        entity.setTenantId(tenantId);
        entity = appRepository.save(entity);
        return appMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public App getById(UUID id) {
        AppEntity entity = appRepository
                .findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        return appMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public Page<App> list(Pageable pageable, AppStatus status) {
        Page<AppEntity> page;
        if (status != null) {
            page = appRepository.findByStatusAndAuditInfoDeletedAtIsNull(status.name(), pageable);
        } else {
            page = appRepository.findByAuditInfoDeletedAtIsNull(pageable);
        }
        return page.map(appMapper::toDomain);
    }

    public App update(UUID id, App update) {
        AppEntity entity = appRepository
                .findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("app.not_found"));

        if (!entity.getName().equals(update.getName())) {
            checkNameUniqueness(update.getName(), TenantContext.getTenantInformation().tenantId());
        }

        appMapper.updateEntity(entity, update);
        entity = appRepository.save(entity);
        return appMapper.toDomain(entity);
    }

    public App updateStatus(UUID id, AppStatus status) {
        AppEntity entity = appRepository
                .findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("app.not_found"));

        entity.setStatus(status.name());
        entity = appRepository.save(entity);
        return appMapper.toDomain(entity);
    }

    public void delete(UUID id) {
        AppEntity entity = appRepository
                .findByIdAndAuditInfoDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("app.not_found"));

        entity.getAuditInfo().setDeletedAt(OffsetDateTime.now());
        appRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public boolean isNameAvailable(String name) {
        return !appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull(
                name, TenantContext.getTenantInformation().tenantId());
    }

    private void checkNameUniqueness(String name, UUID tenantId) {
        if (appRepository.existsByNameAndTenantIdAndAuditInfoDeletedAtIsNull(name, tenantId)) {
            throw new ConflictException("app.name_already_exists");
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.app.service.AppServiceTest`
Expected: All 9 tests pass

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/app/service/AppServiceTest.java apps/backend/src/main/java/io/webcontify/app/service/AppService.java
git commit -m "feat: add AppService with CRUD + lifecycle (TDD, 9 tests)"
```

---

## Task 7: AppController

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/api/AppController.java`

- [ ] **Step 1: Implement AppController**

```java
package io.webcontify.app.api;

import io.webcontify.app.mapper.AppMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.service.AppService;
import io.webcontify.generated.api.AppApi;
import io.webcontify.generated.model.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

// TODO: Add @RequiresPermission annotations when app type is added to OpenFGA (Sub-Plan 4e)
@RestController
public class AppController implements AppApi {

    private final AppService appService;
    private final AppMapper appMapper;

    public AppController(AppService appService, AppMapper appMapper) {
        this.appService = appService;
        this.appMapper = appMapper;
    }

    @Override
    public ResponseEntity<AppResponse> createApp(CreateAppRequest request) {
        App app = appMapper.toDomain(request);
        App created = appService.create(app);
        AppResponse response = appMapper.toResponse(created);
        return ResponseEntity.created(URI.create("/api/v1/apps/" + created.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<AppListResponse> listApps(
            Integer page,
            Integer size,
            io.webcontify.generated.model.AppStatus status) {
        AppStatus domainStatus = status != null ? AppStatus.valueOf(status.getValue()) : null;
        Page<App> result = appService.list(PageRequest.of(page, size), domainStatus);

        AppListResponse response = new AppListResponse();
        response.setItems(result.getContent().stream().map(appMapper::toResponse).toList());

        PageInfo pageInfo = new PageInfo();
        pageInfo.setPage(result.getNumber());
        pageInfo.setSize(result.getSize());
        pageInfo.setTotalElements(result.getTotalElements());
        pageInfo.setTotalPages(result.getTotalPages());
        response.setPageInfo(pageInfo);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AppResponse> getApp(UUID appId) {
        App app = appService.getById(appId);
        return ResponseEntity.ok(appMapper.toResponse(app));
    }

    @Override
    public ResponseEntity<AppResponse> updateApp(UUID appId, UpdateAppRequest request) {
        App update = appMapper.toDomain(request);
        App updated = appService.update(appId, update);
        return ResponseEntity.ok(appMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<AppResponse> updateAppStatus(
            UUID appId, UpdateAppStatusRequest request) {
        AppStatus status = AppStatus.valueOf(request.getStatus().getValue());
        App updated = appService.updateStatus(appId, status);
        return ResponseEntity.ok(appMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deleteApp(UUID appId) {
        appService.delete(appId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<NameAvailabilityResponse> checkNewAppNameAvailability(String name) {
        NameAvailabilityResponse response = new NameAvailabilityResponse();
        response.setAvailable(appService.isNameAvailable(name));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<NameAvailabilityResponse> checkAppNameAvailability(UUID appId, String name) {
        NameAvailabilityResponse response = new NameAvailabilityResponse();
        response.setAvailable(appService.isNameAvailable(name));
        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 2: Run full backend test suite**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: All tests pass including existing tests + new AppServiceTest

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/app/api/AppController.java
git commit -m "feat: add AppController implementing generated AppApi interface"
```

---

## Task 8: Backend Integration Test

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/app/api/AppControllerIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

Follow the existing integration test pattern with `@SpringBootTest`, `@AutoConfigureMockMvc`, and test security configuration. Test the full HTTP round-trip:

```java
package io.webcontify.app.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.generated.model.CreateAppRequest;
import io.webcontify.generated.model.UpdateAppRequest;
import io.webcontify.generated.model.UpdateAppStatusRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AppControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndRetrieveApp() throws Exception {
        CreateAppRequest request = new CreateAppRequest();
        request.setName("Integration Blog");
        request.setDescription("A test blog app");

        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Blog"))
                .andExpect(jsonPath("$.slug").value("integration-blog"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.hasDraft").value(false))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/apps/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Integration Blog"));
    }

    @Test
    void shouldListApps() throws Exception {
        mockMvc.perform(get("/api/v1/apps?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.pageInfo").exists());
    }

    @Test
    void shouldUpdateApp() throws Exception {
        // Create first
        CreateAppRequest createReq = new CreateAppRequest();
        createReq.setName("Update Test App");
        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        // Update
        UpdateAppRequest updateReq = new UpdateAppRequest();
        updateReq.setName("Updated Name");
        updateReq.setDescription("Updated description");

        mockMvc.perform(put("/api/v1/apps/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void shouldChangeAppStatus() throws Exception {
        CreateAppRequest createReq = new CreateAppRequest();
        createReq.setName("Status Test App");
        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        UpdateAppStatusRequest statusReq = new UpdateAppStatusRequest();
        statusReq.setStatus(io.webcontify.generated.model.AppStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/apps/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldSoftDeleteApp() throws Exception {
        CreateAppRequest createReq = new CreateAppRequest();
        createReq.setName("Delete Test App");
        MvcResult createResult = mockMvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(delete("/api/v1/apps/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/apps/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn409OnDuplicateName() throws Exception {
        CreateAppRequest request = new CreateAppRequest();
        request.setName("Duplicate Name App");

        mockMvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldCheckNameAvailability() throws Exception {
        mockMvc.perform(get("/api/v1/apps/name-available?name=UniqueAppName"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
}
```

- [ ] **Step 2: Run integration tests**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.app.api.AppControllerIntegrationTest`
Expected: All 7 tests pass

- [ ] **Step 3: Run full test suite**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: All tests pass (existing + new)

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/app/api/AppControllerIntegrationTest.java
git commit -m "test: add AppController integration tests (7 tests)"
```

---

## Task 9: Frontend — Orval Config + Generation + i18n

**Files:**
- Modify: `apps/platform/orval.config.ts`
- Modify: `apps/platform/messages/en.json`
- Modify: `apps/platform/messages/de.json`

- [ ] **Step 1: Add app API to Orval config**

Add a new `app` entry in `orval.config.ts` following the existing pattern:

```typescript
app: {
  input: {
    target: "../../../openapi/app-api.yaml",
  },
  output: {
    target: "./src/generated/app.ts",
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

- [ ] **Step 2: Add i18n keys to en.json**

Add under the root:

```json
"apps": {
  "title": "Apps",
  "description": "Manage your applications",
  "create": "Create App",
  "name": "Name",
  "slug": "Slug",
  "description_label": "Description",
  "status": "Status",
  "hasDraft": "Draft",
  "actions": "Actions",
  "statusValues": {
    "DRAFT": "Draft",
    "ACTIVE": "Active",
    "INACTIVE": "Inactive"
  },
  "emptyState": {
    "title": "No apps yet",
    "description": "Create your first app to get started."
  },
  "createDialog": {
    "title": "Create App",
    "description": "Create a new application in your workspace.",
    "nameLabel": "Name",
    "namePlaceholder": "My App",
    "descriptionLabel": "Description",
    "descriptionPlaceholder": "Optional description for your app",
    "nameAlreadyExists": "An app with this name already exists",
    "submit": "Create",
    "cancel": "Cancel",
    "successToast": "App created successfully"
  },
  "detail": {
    "slugLabel": "Slug",
    "descriptionLabel": "Description",
    "statusLabel": "Status",
    "createdAtLabel": "Created",
    "updatedAtLabel": "Last updated",
    "activate": "Activate",
    "deactivate": "Deactivate",
    "delete": "Delete",
    "deleteConfirm": {
      "title": "Delete App",
      "description": "Are you sure you want to delete this app? This action cannot be undone.",
      "confirm": "Delete",
      "cancel": "Cancel"
    },
    "statusChangeConfirm": {
      "activateTitle": "Activate App",
      "activateDescription": "This will make the app active and usable.",
      "deactivateTitle": "Deactivate App",
      "deactivateDescription": "This will make the app read-only. Existing entries remain accessible.",
      "confirm": "Confirm",
      "cancel": "Cancel"
    }
  },
  "viewDetails": "View Details",
  "edit": "Edit"
}
```

- [ ] **Step 3: Add i18n keys to de.json**

Same structure with German translations:

```json
"apps": {
  "title": "Apps",
  "description": "Anwendungen verwalten",
  "create": "App erstellen",
  "name": "Name",
  "slug": "Slug",
  "description_label": "Beschreibung",
  "status": "Status",
  "hasDraft": "Entwurf",
  "actions": "Aktionen",
  "statusValues": {
    "DRAFT": "Entwurf",
    "ACTIVE": "Aktiv",
    "INACTIVE": "Inaktiv"
  },
  "emptyState": {
    "title": "Noch keine Apps",
    "description": "Erstelle deine erste App um loszulegen."
  },
  "createDialog": {
    "title": "App erstellen",
    "description": "Erstelle eine neue Anwendung in deinem Workspace.",
    "nameLabel": "Name",
    "namePlaceholder": "Meine App",
    "descriptionLabel": "Beschreibung",
    "descriptionPlaceholder": "Optionale Beschreibung fuer deine App",
    "nameAlreadyExists": "Eine App mit diesem Namen existiert bereits",
    "submit": "Erstellen",
    "cancel": "Abbrechen",
    "successToast": "App erfolgreich erstellt"
  },
  "detail": {
    "slugLabel": "Slug",
    "descriptionLabel": "Beschreibung",
    "statusLabel": "Status",
    "createdAtLabel": "Erstellt",
    "updatedAtLabel": "Zuletzt aktualisiert",
    "activate": "Aktivieren",
    "deactivate": "Deaktivieren",
    "delete": "Loeschen",
    "deleteConfirm": {
      "title": "App loeschen",
      "description": "Bist du sicher, dass du diese App loeschen moechtest? Diese Aktion kann nicht rueckgaengig gemacht werden.",
      "confirm": "Loeschen",
      "cancel": "Abbrechen"
    },
    "statusChangeConfirm": {
      "activateTitle": "App aktivieren",
      "activateDescription": "Die App wird aktiv und nutzbar.",
      "deactivateTitle": "App deaktivieren",
      "deactivateDescription": "Die App wird schreibgeschuetzt. Bestehende Eintraege bleiben lesbar.",
      "confirm": "Bestaetigen",
      "cancel": "Abbrechen"
    }
  },
  "viewDetails": "Details anzeigen",
  "edit": "Bearbeiten"
}
```

- [ ] **Step 4: Generate Orval hooks**

Run: `cd apps/platform && pnpm generate`
Expected: `src/generated/app.ts` created with `useListApps`, `useGetApp`, `useCreateApp`, `useUpdateApp`, `useDeleteApp`, `useUpdateAppStatus`, `useCheckNewAppNameAvailability` hooks

- [ ] **Step 5: Commit**

```bash
git add apps/platform/orval.config.ts apps/platform/src/generated/app.ts apps/platform/messages/en.json apps/platform/messages/de.json
git commit -m "feat: add Orval app API config, generate hooks, add i18n keys (EN+DE)"
```

---

## Task 10: Frontend — Create App Schema + Dialog

**Files:**
- Create: `apps/platform/src/features/apps/schemas/create-app.ts`
- Create: `apps/platform/src/features/apps/components/create-app-dialog.tsx`

- [ ] **Step 1: Create Zod schema**

```typescript
import { z } from "zod"

export const createAppSchema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().max(2000).optional().or(z.literal("")),
})

export type CreateAppFormValues = z.infer<typeof createAppSchema>
```

- [ ] **Step 2: Create CreateAppDialog component**

Follow the exact pattern from `CreateTenantDialog`:
- `useForm<CreateAppFormValues>` with `zodResolver(createAppSchema)`
- `defaultValues: { name: "", description: "" }`
- Name availability check before submit using `checkNewAppNameAvailability`
- On success: toast, close dialog, reset form, router.push to `/apps/${id}`
- Error handling: 400 validation, 409 conflict via `form.setError`
- All text via `useTranslations("apps")`
- `data-testid="create-app-dialog"`

- [ ] **Step 3: Verify it compiles**

Run: `cd apps/platform && pnpm build`
Expected: No compilation errors (component not yet mounted in a page)

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/features/apps/
git commit -m "feat: add CreateAppDialog with Zod schema and form validation"
```

---

## Task 11: Frontend — App Table + Skeleton

**Files:**
- Create: `apps/platform/src/features/apps/components/app-table.tsx`
- Create: `apps/platform/src/features/apps/components/app-table-skeleton.tsx`

- [ ] **Step 1: Create AppTableSkeleton**

Follow `TenantTableSkeleton` pattern: 5 rows of `Skeleton` elements matching table columns (Name, Status, Draft, Description, Actions).

- [ ] **Step 2: Create AppTable component**

Follow `TenantTable` pattern exactly:
- Props: `apps: AppResponse[]`, `isLoading: boolean`, `isRefetching: boolean`
- Loading → `AppTableSkeleton`
- Empty → dashed border empty state with CTA text
- Refetching → overlay with spinner
- Table columns: Name (link to `/apps/${id}`), Status (Badge), Draft indicator, Description (truncated), Actions (DropdownMenu with View Details)
- Status badge variants: DRAFT → `outline`, ACTIVE → `default` (green), INACTIVE → `secondary`
- Draft column: show amber "DRAFT" badge when `hasDraft === true`
- All text via `useTranslations("apps")`
- `data-testid="app-table"`

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/features/apps/components/app-table.tsx apps/platform/src/features/apps/components/app-table-skeleton.tsx
git commit -m "feat: add AppTable and AppTableSkeleton components"
```

---

## Task 12: Frontend — App List Page + Navigation

**Files:**
- Modify: `apps/platform/src/app/(workspace)/apps/page.tsx` (replace placeholder)
- Modify: `apps/platform/src/components/layout/nav-config.ts`

- [ ] **Step 1: Replace apps page placeholder**

Follow the `TenantsPage` pattern exactly:

```typescript
"use client"

import { Plus } from "lucide-react"
import { useTranslations } from "next-intl"

import { Button } from "@/components/ui/button"
import { AppHeader } from "@/components/layout/app-header"
import { AppTable } from "@/features/apps/components/app-table"
import { CreateAppDialog } from "@/features/apps/components/create-app-dialog"
import { useListApps } from "@/generated/app"

export default function AppsPage() {
  const t = useTranslations("apps")
  const { data, isLoading, isRefetching } = useListApps()
  const apps = data?.data?.items ?? []

  return (
    <>
      <AppHeader
        mode="list"
        title={t("title")}
        description={t("description")}
        actions={
          <CreateAppDialog>
            <Button>
              <Plus className="mr-2 size-4" />
              {t("create")}
            </Button>
          </CreateAppDialog>
        }
      />
      <div data-testid="apps-page" className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <AppTable apps={apps} isLoading={isLoading} isRefetching={isRefetching} />
      </div>
    </>
  )
}
```

- [ ] **Step 2: Update nav-config.ts**

Add `Apps` to the workspace navigation. Add a `Boxes` (or `LayoutGrid`) icon import from lucide-react. Add the item to the first section of workspace nav. **No permission gate in 4a** — the OpenFGA `app` type doesn't exist yet. The nav item is always visible for workspace users. Permission gating will be added in Sub-Plan 4e.

```typescript
// In getWorkspaceNavItems, add to the first section:
{
  label: t("sidebar.apps"),
  href: "/apps",
  icon: Boxes,
  testId: "nav-apps",
}
```

Note: `canAccessApps` option will be added to `WorkspaceNavOptions` in Sub-Plan 4e when OpenFGA app type is available.

- [ ] **Step 3: Verify build**

Run: `cd apps/platform && pnpm build`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/app/(workspace)/apps/page.tsx apps/platform/src/components/layout/nav-config.ts
git commit -m "feat: add App list page with table, create dialog, and navigation"
```

---

## Task 13: Frontend — App Detail Page

**Files:**
- Create: `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx`

- [ ] **Step 1: Create App detail page**

Follow the `TenantDetailPage` pattern:
- `use(params)` to get `appId`
- `useGetApp(appId)` for data fetching
- `useTranslations("apps")`
- `AppHeader` with `mode="detail"`, breadcrumbs: `[{ label: t("title"), href: "/apps" }, { label: app.name, suffix: statusBadge }]`
- Show draft badge next to status badge when `hasDraft === true`
- Overview section with: Description, Slug, Status, Created, Last Updated
- Actions section (permission-gated): Activate/Deactivate button, Delete button with confirmation dialog
- Status change and delete via generated mutation hooks (`useUpdateAppStatus`, `useDeleteApp`)
- Loading state with Skeleton
- Not found state via `notFound()`
- `data-testid="app-detail-page"`

- [ ] **Step 2: Verify build**

Run: `cd apps/platform && pnpm build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/app/(workspace)/apps/[appId]/
git commit -m "feat: add App detail page with status management and delete"
```

---

## Task 14: Frontend — Vitest Tests

**Files:**
- Create: `apps/platform/src/features/apps/components/__tests__/app-table.test.tsx`

- [ ] **Step 1: Write AppTable tests**

```typescript
import { describe, it, expect } from "vitest"
import { render, screen } from "@testing-library/react"
import { AppTable } from "../app-table"
import type { AppResponse } from "@/generated/app"

// Mock useTranslations
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}))

// Mock next/link
vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}))

const mockApp: AppResponse = {
  id: "test-id",
  name: "Blog",
  slug: "blog",
  description: "A blog app",
  status: "ACTIVE",
  hasDraft: false,
  createdAt: "2026-04-04T00:00:00Z",
  createdBy: "user-id",
  updatedAt: "2026-04-04T00:00:00Z",
  updatedBy: "user-id",
}

describe("AppTable", () => {
  it("renders loading skeleton when isLoading", () => {
    render(<AppTable apps={[]} isLoading={true} isRefetching={false} />)
    // Skeleton should be visible
  })

  it("renders empty state when no apps", () => {
    render(<AppTable apps={[]} isLoading={false} isRefetching={false} />)
    expect(screen.getByText("emptyState.title")).toBeInTheDocument()
  })

  it("renders app rows", () => {
    render(<AppTable apps={[mockApp]} isLoading={false} isRefetching={false} />)
    expect(screen.getByText("Blog")).toBeInTheDocument()
  })

  it("shows draft badge when hasDraft is true", () => {
    const draftApp = { ...mockApp, hasDraft: true }
    render(<AppTable apps={[draftApp]} isLoading={false} isRefetching={false} />)
    expect(screen.getByText("hasDraft")).toBeInTheDocument()
  })

  it("links app name to detail page", () => {
    render(<AppTable apps={[mockApp]} isLoading={false} isRefetching={false} />)
    const link = screen.getByText("Blog").closest("a")
    expect(link).toHaveAttribute("href", "/apps/test-id")
  })
})
```

- [ ] **Step 2: Run tests**

Run: `cd apps/platform && pnpm vitest run src/features/apps`
Expected: All 5 tests pass

- [ ] **Step 3: Run full frontend test suite**

Run: `cd apps/platform && pnpm vitest run`
Expected: All existing + new tests pass

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/features/apps/components/__tests__/
git commit -m "test: add AppTable vitest tests (5 tests)"
```

---

## Task 15: Final Verification

- [ ] **Step 1: Run full backend test suite**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: All tests pass

- [ ] **Step 2: Run full frontend test suite**

Run: `cd apps/platform && pnpm vitest run`
Expected: All tests pass

- [ ] **Step 3: Run frontend build**

Run: `cd apps/platform && pnpm build`
Expected: Build succeeds

- [ ] **Step 4: Run frontend lint**

Run: `cd apps/platform && pnpm lint`
Expected: No lint errors

- [ ] **Step 5: Update PROGRESS.md**

Add Phase 4a section to `docs/PROGRESS.md` with all completed tasks.

- [ ] **Step 6: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md with Phase 4a App CRUD + Lifecycle"
```

---

## Known Trade-offs (from Plan Challenge)

- **No @RequiresPermission on AppController** — OpenFGA `app` type doesn't exist until Sub-Plan 4e. TODO comment added as reminder.
- **No permission gate on Apps nav item** — Same reason. Always visible in 4a, gated in 4e.
- **E2E Tests deferred to Sub-Plan 4g** — All BDU scenarios collected there to test full integrated flow.
- **OpenAPI build.gradle task registration** — Single point of failure. If task name or dependency chain is wrong, all code generation breaks. Agent must carefully follow existing pattern in build.gradle.kts (explicit `tasks.register` + add to `compileJava.dependsOn`).
