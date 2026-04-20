# App Domain 4b: Versioning + Snapshots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement App Versioning with S3 snapshots, publish/rollback flow, version history, and the shared ChangeRecord infrastructure.

**Architecture:** AppVersionService orchestrates publish (snapshot → S3 → DB entry → close ChangeRecords). Snapshots stored as JSON in S3 per-tenant bucket. ChangeRecord is a generic shared module for all domains. Rollback loads old snapshot as new Draft.

**Tech Stack:** Spring Boot 4, JPA, MapStruct, Flyway, S3 (MinIO), Jackson, OpenAPI Generator, Next.js 16, React Query (Orval), shadcn/ui

**Design Doc:** `docs/plans/2026-04-04-app-domain-design.md` (Sections 2.2, 2.3, 3, 4, 5)

**Sub-Plan Dependencies:** 4a (App CRUD + Lifecycle) must be complete

---

## File Structure

### Backend — New Files
- `apps/backend/src/main/resources/db/migration/V20260405_001__create_app_version_and_change_record_tables.sql`
- `apps/backend/src/main/java/io/webcontify/shared/changelog/model/ChangeRecord.java`
- `apps/backend/src/main/java/io/webcontify/shared/changelog/repository/entity/ChangeRecordEntity.java`
- `apps/backend/src/main/java/io/webcontify/shared/changelog/repository/ChangeRecordRepository.java`
- `apps/backend/src/main/java/io/webcontify/shared/changelog/service/ChangeRecordService.java`
- `apps/backend/src/main/java/io/webcontify/shared/changelog/package-info.java`
- `apps/backend/src/main/java/io/webcontify/app/model/AppVersion.java`
- `apps/backend/src/main/java/io/webcontify/app/model/AppSnapshot.java`
- `apps/backend/src/main/java/io/webcontify/app/repository/entity/AppVersionEntity.java`
- `apps/backend/src/main/java/io/webcontify/app/repository/AppVersionRepository.java`
- `apps/backend/src/main/java/io/webcontify/app/mapper/AppVersionMapper.java`
- `apps/backend/src/main/java/io/webcontify/app/service/AppSnapshotService.java`
- `apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java`
- `apps/backend/src/main/java/io/webcontify/app/event/AppPublishedEvent.java`
- `apps/backend/src/main/java/io/webcontify/app/api/AppVersionController.java`
- `apps/backend/src/test/java/io/webcontify/shared/changelog/service/ChangeRecordServiceTest.java`
- `apps/backend/src/test/java/io/webcontify/app/service/AppVersionServiceTest.java`
- `apps/backend/src/test/java/io/webcontify/app/api/AppVersionControllerIntegrationTest.java`

### Backend — Modified Files
- `openapi/app-api.yaml` (add versioning endpoints + schemas)
- `apps/backend/src/main/java/io/webcontify/app/service/AppService.java` (hasDraft auto-set on update)

### Frontend — New Files
- `apps/platform/src/features/apps/components/publish-app-dialog.tsx`
- `apps/platform/src/features/apps/components/app-version-list.tsx`
- `apps/platform/src/features/apps/components/rollback-confirm-dialog.tsx`

### Frontend — Modified Files
- `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx` (add Versions tab, Publish button)
- `apps/platform/orval.config.ts` (regenerate for new endpoints)
- `apps/platform/messages/en.json` (add version keys)
- `apps/platform/messages/de.json` (add version keys)

---

## Task 1: Flyway Migration — app_version + change_record

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V20260405_001__create_app_version_and_change_record_tables.sql`

- [ ] **Step 1: Create migration file**

```sql
-- App Versions (immutable snapshots)
CREATE TABLE app_version (
    id              UUID PRIMARY KEY,
    app_id          UUID NOT NULL REFERENCES app(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    version         VARCHAR(50) NOT NULL,
    s3_key          VARCHAR(500) NOT NULL,
    release_notes   TEXT,
    published_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by    UUID,
    UNIQUE(app_id, version)
);

ALTER TABLE app_version ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_app_version ON app_version
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );

-- Change Records (shared, generic audit/revision tracking)
CREATE TABLE change_record (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID NOT NULL,
    change_type     VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    source          VARCHAR(255) NOT NULL,
    draft_version   VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID
);

CREATE INDEX idx_change_record_lookup
    ON change_record(entity_type, entity_id, created_at);
CREATE INDEX idx_change_record_draft
    ON change_record(entity_type, entity_id, draft_version)
    WHERE draft_version IS NOT NULL;

ALTER TABLE change_record ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_change_record ON change_record
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
```

- [ ] **Step 2: Run tests to verify migration applies**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: All tests pass, migration applies cleanly

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/resources/db/migration/V20260405_001__create_app_version_and_change_record_tables.sql
git commit -m "feat: add Flyway migration for app_version and change_record tables with RLS"
```

---

## Task 2: OpenAPI Spec — Version Endpoints

**Files:**
- Modify: `openapi/app-api.yaml`

- [ ] **Step 1: Add version endpoints and schemas to app-api.yaml**

Add these paths after the existing endpoints:

```yaml
  /api/v1/apps/{appId}/publish:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    post:
      operationId: publishApp
      tags: [AppVersion]
      summary: Publish app as a new version
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PublishAppRequest'
      responses:
        '201':
          description: Version published
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppVersionResponse'
        '400':
          description: Validation error or no changes to publish
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '404':
          description: App not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/apps/{appId}/versions:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: listAppVersions
      tags: [AppVersion]
      summary: List version history
      responses:
        '200':
          description: Version list
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppVersionListResponse'

  /api/v1/apps/{appId}/versions/{versionId}:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
      - name: versionId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: getAppVersion
      tags: [AppVersion]
      summary: Get version detail
      responses:
        '200':
          description: Version detail
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppVersionResponse'
        '404':
          description: Version not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/apps/{appId}/versions/{versionId}/rollback:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
      - name: versionId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    post:
      operationId: rollbackAppVersion
      tags: [AppVersion]
      summary: Rollback to a previous version (creates new draft)
      responses:
        '200':
          description: Rollback successful, draft created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppResponse'
        '404':
          description: Version not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/apps/{appId}/versions/suggest:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: suggestNextVersion
      tags: [AppVersion]
      summary: Suggest next semver version based on changes
      responses:
        '200':
          description: Suggested version
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/VersionSuggestionResponse'
```

Add these schemas:

```yaml
    PublishAppRequest:
      type: object
      required: [version]
      properties:
        version:
          type: string
          pattern: '^\d+\.\d+\.\d+$'
          description: Semver version string
        releaseNotes:
          type: string
          maxLength: 5000

    AppVersionResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        appId:
          type: string
          format: uuid
        version:
          type: string
        s3Key:
          type: string
        releaseNotes:
          type: string
        publishedAt:
          type: string
          format: date-time
        publishedBy:
          type: string
          format: uuid

    AppVersionListResponse:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/AppVersionResponse'

    VersionSuggestionResponse:
      type: object
      properties:
        suggestedVersion:
          type: string
        currentVersion:
          type: string
        hasUnpublishedChanges:
          type: boolean
```

- [ ] **Step 2: Regenerate OpenAPI code**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerateApp`
Expected: New generated interfaces: `AppVersionApi.java` with publish, listVersions, getVersion, rollback, suggestNextVersion

- [ ] **Step 3: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: Compiles (controller not yet implemented, so generated default methods return 501)

- [ ] **Step 4: Commit**

```bash
git add openapi/app-api.yaml
git commit -m "feat: add OpenAPI version endpoints (publish, list, detail, rollback, suggest)"
```

---

## Task 3: ChangeRecord Shared Infrastructure

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/shared/changelog/package-info.java`
- Create: `apps/backend/src/main/java/io/webcontify/shared/changelog/model/ChangeRecord.java`
- Create: `apps/backend/src/main/java/io/webcontify/shared/changelog/repository/entity/ChangeRecordEntity.java`
- Create: `apps/backend/src/main/java/io/webcontify/shared/changelog/repository/ChangeRecordRepository.java`
- Create: `apps/backend/src/main/java/io/webcontify/shared/changelog/service/ChangeRecordService.java`
- Create: `apps/backend/src/test/java/io/webcontify/shared/changelog/service/ChangeRecordServiceTest.java`

### 3a. Package-info

```java
@org.springframework.modulith.NamedInterface("changelog")
package io.webcontify.shared.changelog;
```

This exposes the changelog subpackage as a named interface from the shared module, allowing other modules (like app) to depend on it.

### 3b. ChangeRecord domain model

```java
package io.webcontify.shared.changelog.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ChangeRecord {
    private UUID id;
    private UUID tenantId;
    private String entityType;
    private UUID entityId;
    private String changeType;
    private String payload;      // JSONB as String
    private String source;
    private String draftVersion;  // null = closed
    private OffsetDateTime createdAt;
    private UUID createdBy;

    // All getters and setters
}
```

### 3c. ChangeRecordEntity

```java
package io.webcontify.shared.changelog.repository.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "change_record")
public class ChangeRecordEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "change_type", nullable = false)
    private String changeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "draft_version")
    private String draftVersion;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    // All getters and setters
}
```

NOTE: This entity does NOT extend AuditableBaseEntity because it has its own simpler schema (no updatedAt/updatedBy/deletedAt — change records are immutable).

### 3d. ChangeRecordRepository

```java
package io.webcontify.shared.changelog.repository;

import io.webcontify.shared.changelog.repository.entity.ChangeRecordEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChangeRecordRepository extends JpaRepository<ChangeRecordEntity, UUID> {

    List<ChangeRecordEntity> findByEntityTypeAndEntityIdAndDraftVersionIsNotNullOrderByCreatedAtAsc(
            String entityType, UUID entityId);

    List<ChangeRecordEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);

    @Modifying
    @Query("UPDATE ChangeRecordEntity c SET c.draftVersion = null WHERE c.entityType = :entityType AND c.entityId = :entityId AND c.draftVersion IS NOT NULL")
    int closeDraftRecords(String entityType, UUID entityId);
}
```

### 3e. ChangeRecordService

```java
package io.webcontify.shared.changelog.service;

import io.webcontify.shared.changelog.model.ChangeRecord;
import io.webcontify.shared.changelog.repository.ChangeRecordRepository;
import io.webcontify.shared.changelog.repository.entity.ChangeRecordEntity;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChangeRecordService {

    private final ChangeRecordRepository repository;

    public ChangeRecordService(ChangeRecordRepository repository) {
        this.repository = repository;
    }

    public ChangeRecord create(String entityType, UUID entityId, String changeType,
                                String payload, String source, String draftVersion) {
        var tenantInfo = TenantContext.getTenantInformation();
        ChangeRecordEntity entity = new ChangeRecordEntity();
        entity.setId(UuidGenerator.generate());
        entity.setTenantId(tenantInfo.tenantId());
        entity.setEntityType(entityType);
        entity.setEntityId(entityId);
        entity.setChangeType(changeType);
        entity.setPayload(payload);
        entity.setSource(source);
        entity.setDraftVersion(draftVersion);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setCreatedBy(tenantInfo.tenantId()); // TODO: get actual user ID from auth context
        entity = repository.save(entity);
        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public List<ChangeRecord> getDraftRecords(String entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdAndDraftVersionIsNotNullOrderByCreatedAtAsc(
                entityType, entityId).stream().map(this::toModel).toList();
    }

    public int closeDraftRecords(String entityType, UUID entityId) {
        return repository.closeDraftRecords(entityType, entityId);
    }

    private ChangeRecord toModel(ChangeRecordEntity entity) {
        ChangeRecord record = new ChangeRecord();
        record.setId(entity.getId());
        record.setTenantId(entity.getTenantId());
        record.setEntityType(entity.getEntityType());
        record.setEntityId(entity.getEntityId());
        record.setChangeType(entity.getChangeType());
        record.setPayload(entity.getPayload());
        record.setSource(entity.getSource());
        record.setDraftVersion(entity.getDraftVersion());
        record.setCreatedAt(entity.getCreatedAt());
        record.setCreatedBy(entity.getCreatedBy());
        return record;
    }
}
```

### 3f. Unit Tests

Write tests for ChangeRecordService:
1. `shouldCreateChangeRecord` — verify all fields set, UUIDv7 generated
2. `shouldCloseDraftRecords` — verify draftVersion set to null
3. `shouldReturnDraftRecords` — verify filtering by entityType + entityId + draftVersion not null

Use Mockito with SecurityContext setup (same pattern as AppServiceTest).

### 3g. Verification + Commit

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: All tests pass

```bash
git add apps/backend/src/main/java/io/webcontify/shared/changelog/ apps/backend/src/test/java/io/webcontify/shared/changelog/
git commit -m "feat: add shared ChangeRecord infrastructure (model, entity, repo, service, TDD)"
```

---

## Task 4: AppVersion Model + Entity + Repository + Mapper

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/model/AppVersion.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/model/AppSnapshot.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/repository/entity/AppVersionEntity.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/repository/AppVersionRepository.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/mapper/AppVersionMapper.java`

### 4a. AppVersion domain model

```java
package io.webcontify.app.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AppVersion {
    private UUID id;
    private UUID appId;
    private UUID tenantId;
    private String version;
    private String s3Key;
    private String releaseNotes;
    private OffsetDateTime publishedAt;
    private UUID publishedBy;

    // All getters and setters
}
```

### 4b. AppSnapshot — the JSON structure stored in S3

```java
package io.webcontify.app.model;

import java.util.List;

public record AppSnapshot(
    String appId,
    String version,
    AppSnapshotMetadata metadata,
    List<AppSnapshotDependency> dependencies,
    List<AppSnapshotDefinition> contentDefinitions
) {
    public record AppSnapshotMetadata(String name, String slug, String description) {}
    public record AppSnapshotDependency(String appSlug, String version) {}
    public record AppSnapshotDefinition(String name, String slug, List<AppSnapshotField> fields) {}
    public record AppSnapshotField(String name, String type, boolean required, String target) {}
}
```

### 4c. AppVersionEntity

```java
package io.webcontify.app.repository.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_version")
public class AppVersionEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "app_id", nullable = false)
    private UUID appId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "release_notes")
    private String releaseNotes;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    // All getters and setters
}
```

NOTE: Does NOT extend AuditableBaseEntity. Versions are immutable — no update/delete tracking needed.

### 4d. AppVersionRepository

```java
package io.webcontify.app.repository;

import io.webcontify.app.repository.entity.AppVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionRepository extends JpaRepository<AppVersionEntity, UUID> {

    List<AppVersionEntity> findByAppIdOrderByPublishedAtDesc(UUID appId);

    Optional<AppVersionEntity> findByIdAndAppId(UUID id, UUID appId);

    Optional<AppVersionEntity> findTopByAppIdOrderByPublishedAtDesc(UUID appId);

    boolean existsByAppIdAndVersion(UUID appId, String version);
}
```

### 4e. AppVersionMapper

```java
package io.webcontify.app.mapper;

import io.webcontify.app.model.AppVersion;
import io.webcontify.app.repository.entity.AppVersionEntity;
import io.webcontify.generated.model.AppVersionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AppVersionMapper {

    AppVersion toDomain(AppVersionEntity entity);

    AppVersionResponse toResponse(AppVersion version);
}
```

### 4f. Verification + Commit

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: Compiles successfully

```bash
git add apps/backend/src/main/java/io/webcontify/app/model/AppVersion.java apps/backend/src/main/java/io/webcontify/app/model/AppSnapshot.java apps/backend/src/main/java/io/webcontify/app/repository/ apps/backend/src/main/java/io/webcontify/app/mapper/AppVersionMapper.java
git commit -m "feat: add AppVersion model, entity, repository, mapper, and AppSnapshot record"
```

---

## Task 5: AppSnapshotService — S3 Storage

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/service/AppSnapshotService.java`
- Create: `apps/backend/src/test/java/io/webcontify/app/service/AppSnapshotServiceTest.java`

### 5a. AppSnapshotService

Handles serialization/deserialization of snapshots to/from S3.

```java
package io.webcontify.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.shared.filestorage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AppSnapshotService {

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public AppSnapshotService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
        this.objectMapper = new ObjectMapper();  // NOT injected — matches codebase pattern (OpenFgaService, TenantResolutionFilter)
    }

    public AppSnapshot buildSnapshot(App app, String version) {
        // Currently no content definitions or dependencies exist yet.
        // This will be extended when Content Domain is implemented.
        return new AppSnapshot(
                app.getId().toString(),
                version,
                new AppSnapshot.AppSnapshotMetadata(app.getName(), app.getSlug(), app.getDescription()),
                List.of(),  // dependencies — added in Sub-Plan 4d
                List.of()   // contentDefinitions — added in Content Domain phase
        );
    }

    public String uploadSnapshot(String bucketName, AppSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            String key = "snapshots/app-" + snapshot.appId() + "-v" + snapshot.version() + ".json";
            InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            return fileStorageService.uploadPrivateAsset(bucketName, key, stream, "application/json");
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload snapshot", e);
        }
    }

    public AppSnapshot downloadSnapshot(String bucketName, String s3Key) {
        try {
            String url = fileStorageService.getPresignedUrl(bucketName, s3Key, java.time.Duration.ofMinutes(5));
            // For now, use a simple HTTP fetch. In production, use S3 GetObject directly.
            // TODO: Add getObject method to FileStorageService for direct download
            throw new UnsupportedOperationException("Direct S3 download not yet implemented — use presigned URL");
        } catch (UnsupportedOperationException e) {
            throw e;
        }
    }
}
```

IMPORTANT: The `downloadSnapshot` method needs a `getObject` method on FileStorageService that doesn't exist yet. The implementer should:
1. Add `InputStream getObject(String bucketName, String key)` to `FileStorageService` interface
2. Implement it in `S3FileStorageService` using the S3Client
3. Use it in `downloadSnapshot` to fetch and deserialize the JSON

### 5b. Tests

Write tests for AppSnapshotService:
1. `shouldBuildSnapshotWithAppMetadata` — verify snapshot has correct name, slug, description, empty lists
2. `shouldSerializeSnapshotToJson` — verify ObjectMapper serialization produces valid JSON
3. Tests for upload/download require integration test setup (covered in Task 8)

### 5c. Verification + Commit

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

```bash
git add apps/backend/src/main/java/io/webcontify/app/service/AppSnapshotService.java apps/backend/src/test/java/io/webcontify/app/service/AppSnapshotServiceTest.java apps/backend/src/main/java/io/webcontify/shared/filestorage/
git commit -m "feat: add AppSnapshotService for S3 snapshot serialization"
```

---

## Task 6: AppVersionService + AppPublishedEvent — TDD

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/shared/security/TenantSlugResolver.java` (add resolveSlugByTenantId)
- Modify: `apps/backend/src/main/java/io/webcontify/tenant/service/TenantSlugResolverImpl.java` (implement resolveSlugByTenantId)
- Create: `apps/backend/src/main/java/io/webcontify/app/event/AppPublishedEvent.java`
- Create: `apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java`
- Create: `apps/backend/src/test/java/io/webcontify/app/service/AppVersionServiceTest.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppService.java`

### 6-prereq. Extend TenantSlugResolver with id→slug lookup

The S3 bucket naming uses `"tenant-{slug}"` (not tenant-id). TenantSlugResolver currently only has `slug→id`. Add the reverse:

In `TenantSlugResolver.java`, add:
```java
Optional<String> resolveSlugByTenantId(UUID tenantId);
```

In `TenantSlugResolverImpl.java`, implement:
```java
@Override
public Optional<String> resolveSlugByTenantId(UUID tenantId) {
    return tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId)
            .map(entity -> entity.getSlug());
}
```

### 6a. AppPublishedEvent

```java
package io.webcontify.app.event;

import java.util.UUID;

public record AppPublishedEvent(UUID appId, UUID tenantId, String version) {}
```

### 6b. Modify AppService — auto-set hasDraft on update

In `AppService.update()`, after updating the entity, set `hasDraft = true` if the app is ACTIVE:

```java
public App update(UUID id, App update) {
    AppEntity entity = appRepository
            .findByIdAndAuditInfoDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("app.not_found"));

    if (!entity.getName().equals(update.getName())) {
        checkNameUniqueness(update.getName(), TenantContext.getTenantInformation().tenantId());
    }

    // Auto-create draft when editing an ACTIVE app
    if ("ACTIVE".equals(entity.getStatus())) {
        entity.setHasDraft(true);
    }

    appMapper.updateEntity(entity, update);
    entity = appRepository.save(entity);
    return appMapper.toDomain(entity);
}
```

### 6c. AppVersionService

Core orchestration service:

```java
package io.webcontify.app.service;

import io.webcontify.app.event.AppPublishedEvent;
import io.webcontify.app.mapper.AppVersionMapper;
import io.webcontify.app.model.App;
import io.webcontify.app.model.AppSnapshot;
import io.webcontify.app.model.AppStatus;
import io.webcontify.app.model.AppVersion;
import io.webcontify.app.repository.AppRepository;
import io.webcontify.app.repository.AppVersionRepository;
import io.webcontify.app.repository.entity.AppVersionEntity;
import io.webcontify.shared.changelog.service.ChangeRecordService;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.shared.util.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppVersionService {

    private final AppRepository appRepository;
    private final AppVersionRepository versionRepository;
    private final AppVersionMapper versionMapper;
    private final AppSnapshotService snapshotService;
    private final AppService appService;
    private final ChangeRecordService changeRecordService;
    private final TenantSlugResolver tenantSlugResolver;  // for bucket name lookup (slug, not id)
    private final ApplicationEventPublisher eventPublisher;

    // Constructor with all dependencies (including TenantSlugResolver)

    public AppVersion publish(UUID appId, String version, String releaseNotes) {
        App app = appService.getById(appId);

        // Validate version doesn't exist
        if (versionRepository.existsByAppIdAndVersion(appId, version)) {
            throw new ConflictException("app.version.already_exists");
        }

        // Build and upload snapshot
        var tenantInfo = TenantContext.getTenantInformation();
        // IMPORTANT: Bucket naming uses tenant SLUG, not ID (see TenantProvisioningListener).
        // TenantSlugResolver currently only has slug→id. We need to add resolveSlugByTenantId(UUID)
        // to the interface + implementation. See Task 6 prerequisite step.
        String tenantSlug = tenantSlugResolver.resolveSlugByTenantId(tenantInfo.tenantId())
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));
        String bucketName = "tenant-" + tenantSlug;
        AppSnapshot snapshot = snapshotService.buildSnapshot(app, version);
        String s3Key = snapshotService.uploadSnapshot(bucketName, snapshot);

        // Create version entry
        AppVersionEntity entity = new AppVersionEntity();
        entity.setId(UuidGenerator.generate());
        entity.setAppId(appId);
        entity.setTenantId(tenantInfo.tenantId());
        entity.setVersion(version);
        entity.setS3Key(s3Key);
        entity.setReleaseNotes(releaseNotes);
        entity.setPublishedAt(OffsetDateTime.now());
        entity.setPublishedBy(tenantInfo.tenantId()); // TODO: get user ID
        entity = versionRepository.save(entity);

        // Update app: set ACTIVE, clear draft
        appService.updateStatus(appId, AppStatus.ACTIVE);
        // Clear hasDraft via direct repository access
        var appEntity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        appEntity.setHasDraft(false);
        appRepository.save(appEntity);

        // Close draft change records
        changeRecordService.closeDraftRecords("app", appId);

        // Publish event
        eventPublisher.publishEvent(new AppPublishedEvent(appId, tenantInfo.tenantId(), version));

        return versionMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public List<AppVersion> listVersions(UUID appId) {
        // Verify app exists
        appService.getById(appId);
        return versionRepository.findByAppIdOrderByPublishedAtDesc(appId)
                .stream().map(versionMapper::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public AppVersion getVersion(UUID appId, UUID versionId) {
        return versionRepository.findByIdAndAppId(versionId, appId)
                .map(versionMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("app.version.not_found"));
    }

    public App rollback(UUID appId, UUID versionId) {
        AppVersion version = getVersion(appId, versionId);

        // Set hasDraft on app
        var appEntity = appRepository.findByIdAndAuditInfoDeletedAtIsNull(appId)
                .orElseThrow(() -> new NotFoundException("app.not_found"));
        appEntity.setHasDraft(true);
        appRepository.save(appEntity);

        // Create rollback change record
        changeRecordService.create("app", appId, "rollback",
                "{\"rolledBackTo\": \"" + version.getVersion() + "\"}",
                "system:rollback", null);

        return appService.getById(appId);
    }

    @Transactional(readOnly = true)
    public String suggestNextVersion(UUID appId) {
        var latestVersion = versionRepository.findTopByAppIdOrderByPublishedAtDesc(appId);
        if (latestVersion.isEmpty()) {
            return "0.1.0";
        }
        String current = latestVersion.get().getVersion();
        // Simple: increment patch version
        String[] parts = current.split("\\.");
        int patch = Integer.parseInt(parts[2]) + 1;
        return parts[0] + "." + parts[1] + "." + patch;
    }
}
```

### 6d. Unit Tests

Write tests for AppVersionService:
1. `shouldPublishFirstVersion` — verify version 0.1.0 created, app set to ACTIVE, hasDraft cleared
2. `shouldRejectDuplicateVersion` — ConflictException
3. `shouldListVersionsInDescendingOrder`
4. `shouldThrowNotFoundForMissingVersion`
5. `shouldRollbackAndSetHasDraft` — verify hasDraft = true, change record created
6. `shouldSuggestFirstVersion` — returns "0.1.0" when no versions exist
7. `shouldSuggestIncrementedPatchVersion` — "1.0.0" → "1.0.1"
8. `shouldAutoSetHasDraftOnUpdateOfActiveApp` — update active app → hasDraft = true (tests the AppService.update change)

Use Mockito, SecurityContext setup. Mock AppService, AppSnapshotService, ChangeRecordService, TenantSlugResolver, repos, mapper.

### 6e. Verification + Commit

Run all tests.

```bash
git add apps/backend/src/main/java/io/webcontify/app/event/ apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java apps/backend/src/main/java/io/webcontify/app/service/AppService.java apps/backend/src/test/java/io/webcontify/app/service/AppVersionServiceTest.java
git commit -m "feat: add AppVersionService with publish, rollback, version suggest (TDD, 7 tests)"
```

---

## Task 7: AppVersionController

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/api/AppVersionController.java`

Implements the generated `AppVersionApi` interface. IMPORTANT: First read the generated interface to see exact method signatures.

```java
package io.webcontify.app.api;

// Implements generated AppVersionApi
// TODO: Add @RequiresPermission when app type is added to OpenFGA (Sub-Plan 4e)
@RestController
public class AppVersionController implements AppVersionApi {

    private final AppVersionService appVersionService;
    private final AppVersionMapper appVersionMapper;

    // publish → 201 + body
    // listVersions → 200 + AppVersionListResponse wrapper
    // getVersion → 200 + body
    // rollback → 200 + AppResponse (rolled back app state)
    // suggestNextVersion → 200 + VersionSuggestionResponse
}
```

Verification: `./gradlew test` — all tests pass.

```bash
git commit -m "feat: add AppVersionController implementing generated AppVersionApi"
```

---

## Task 8: Integration Tests

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/app/api/AppVersionControllerIntegrationTest.java`

Tests (using MockMvc, same pattern as AppControllerIntegrationTest):
1. `shouldPublishAppAndCreateVersion` — create app → publish → verify 201 + version in response
2. `shouldListVersions` — publish twice → list → verify 2 versions, desc order
3. `shouldGetVersionDetail` — publish → get by ID → verify fields
4. `shouldRejectDuplicateVersion` — publish same version twice → 409
5. `shouldRollbackToVersion` — publish → rollback → verify hasDraft = true
6. `shouldSuggestNextVersion` — publish 0.1.0 → suggest → "0.1.1"
7. `shouldSetHasDraftOnAppUpdate` — create → activate → update → verify hasDraft = true

All tests need `X-Test-Tenant-Id` header.

Verification: all tests pass.

```bash
git commit -m "test: add AppVersionController integration tests (7 tests)"
```

---

## Task 9: Frontend — Versioning UI

**Files:**
- Modify: `apps/platform/messages/en.json` (add version keys)
- Modify: `apps/platform/messages/de.json` (add version keys)
- Regenerate: `apps/platform/src/generated/app.ts`
- Create: `apps/platform/src/features/apps/components/publish-app-dialog.tsx`
- Create: `apps/platform/src/features/apps/components/app-version-list.tsx`
- Create: `apps/platform/src/features/apps/components/rollback-confirm-dialog.tsx`
- Modify: `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx` (add Tabs, Publish button, Versions tab)

### 9a. i18n keys (EN)

Add to `apps` namespace:
```json
"versions": {
  "title": "Versions",
  "publish": "Publish",
  "rollback": "Rollback",
  "noVersions": "No versions published yet",
  "publishDialog": {
    "title": "Publish Version",
    "description": "Create an immutable snapshot of the current app state.",
    "versionLabel": "Version",
    "versionPlaceholder": "e.g. 1.0.0",
    "releaseNotesLabel": "Release Notes",
    "releaseNotesPlaceholder": "What changed in this version?",
    "submit": "Publish",
    "cancel": "Cancel",
    "successToast": "Version published successfully",
    "versionExists": "This version already exists"
  },
  "rollbackDialog": {
    "title": "Rollback to Version",
    "description": "This will load version {version} as a new draft. Any existing unpublished changes will be overwritten.",
    "confirm": "Rollback",
    "cancel": "Cancel",
    "successToast": "Rolled back to version {version}"
  },
  "publishedAt": "Published",
  "publishedBy": "By",
  "releaseNotes": "Release Notes",
  "export": "Export",
  "unpublishedChanges": "Unpublished changes",
  "revisions": "{count} revisions"
}
```

### 9b. i18n keys (DE) — same structure, German translations

### 9c. Regenerate Orval hooks

Run: `cd apps/platform && pnpm generate`

### 9d. PublishAppDialog

- Form: version (string, semver pattern), releaseNotes (optional textarea)
- On open: call `suggestNextVersion` to prefill version field
- On submit: call `publishApp` mutation
- Error handling: 409 (version exists)

### 9e. AppVersionList

- Collapsible rows (shadcn Collapsible) for each version
- Version badge, published date, published by, release notes
- Rollback button per version
- Export button per version (placeholder for now — just links to a future endpoint)
- Draft section at top (amber) when `hasDraft === true` on the app

### 9f. RollbackConfirmDialog

- Confirmation dialog with version number
- Calls `rollbackAppVersion` mutation
- On success: invalidate queries, toast

### 9g. Modify App Detail Page

Add Tabs (shadcn Tabs):
- **Overview** tab (existing content)
- **Versions** tab (new — shows AppVersionList)

Add **Publish** button in AppHeader actions (visible when app has draft or status is DRAFT).

### 9h. Verification

Run: `cd apps/platform && pnpm build && pnpm vitest run`

```bash
git commit -m "feat: add Publish dialog, Version list, Rollback, Tabs on App detail page"
```

---

## Task 10: Final Verification

- [ ] **Step 1: Run full backend tests**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

- [ ] **Step 2: Run full frontend tests + build**

Run: `cd apps/platform && pnpm vitest run && pnpm build`

- [ ] **Step 3: Update PROGRESS.md**

Add Phase 4b section.

- [ ] **Step 4: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md with Phase 4b App Versioning"
```

---

## Known Trade-offs

- **Snapshot currently contains only app metadata** — Content Definitions and Dependencies are empty arrays. They will be populated when those domains are implemented.
- **downloadSnapshot needs getObject on FileStorageService** — The implementer must add `InputStream getObject(String bucket, String key)` to FileStorageService interface and S3FileStorageService. S3Client already supports this.
- **No Unified Diff yet** — Diff between versions is in Sub-Plan 4c (Draft Revisions + Diff UI).
- **Version suggestion is simple patch increment** — Smart semver suggestion (based on breaking changes) is deferred per design doc.
- **No @RequiresPermission on AppVersionController** — Added in Sub-Plan 4e.
- **publishedBy uses tenantId as placeholder** — Proper user ID extraction from SecurityContext is a TODO.
- **Generated interface is `AppVersionsApi` (plural)** — OpenAPI generator adds 's' from the tag name. Controller must implement `AppVersionsApi`, not `AppVersionApi`.
- **ChangeRecord package-info with @NamedInterface is optional** — shared module is OPEN type. Implementer can skip if not needed.
- **ObjectMapper created with `new ObjectMapper()`** — Matches codebase pattern (OpenFgaService, TenantResolutionFilter). NOT injected via Spring.
