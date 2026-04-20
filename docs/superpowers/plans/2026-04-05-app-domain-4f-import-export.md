# App Domain 4f: Import/Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement bundled App export (version snapshot + transitive dependencies as JSON Blueprint) and import with preview (auto-rename, Semver compatibility check) and execution.

**Architecture:** AppExportService bundles a version snapshot with all transitive dependency snapshots into a single JSON Blueprint. AppImportService analyzes blueprints for conflicts (slug collisions, Semver mismatches), generates a preview, and executes the import (creating apps + dependencies). Synchronous execution (no async job scheduler needed for this phase).

**Tech Stack:** Spring Boot 4, Jackson, S3 (MinIO), OpenAPI Generator, Next.js 16, shadcn/ui

**Design Doc:** `docs/plans/2026-04-04-app-domain-design.md` (Section 2.5)

**Sub-Plan Dependencies:** 4b (Versioning/Snapshots) + 4d (Dependencies)

---

## File Structure

### Backend — New Files
- `apps/backend/src/main/java/io/webcontify/app/model/AppBlueprint.java`
- `apps/backend/src/main/java/io/webcontify/app/service/AppExportService.java`
- `apps/backend/src/main/java/io/webcontify/app/service/AppImportService.java`
- `apps/backend/src/main/java/io/webcontify/app/api/AppImportExportController.java`
- `apps/backend/src/test/java/io/webcontify/app/service/AppExportServiceTest.java`
- `apps/backend/src/test/java/io/webcontify/app/service/AppImportServiceTest.java`
- `apps/backend/src/test/java/io/webcontify/app/api/AppImportExportControllerIntegrationTest.java`

### Backend — Modified Files
- `openapi/app-api.yaml` (add import/export endpoints)
- `apps/backend/src/main/java/io/webcontify/app/service/AppSnapshotService.java` (populate dependencies in snapshot)

### Frontend — New Files
- `apps/platform/src/features/apps/components/import-blueprint-dialog.tsx`
- `apps/platform/src/features/apps/components/import-preview.tsx`

### Frontend — Modified Files
- `apps/platform/src/app/(workspace)/apps/page.tsx` (Import Blueprint button)
- `apps/platform/src/features/apps/components/app-version-list.tsx` (Export button wiring)
- `apps/platform/messages/en.json` + `de.json`

---

## Task 1: OpenAPI Spec — Import/Export Endpoints

**Files:**
- Modify: `openapi/app-api.yaml`

Add 3 paths with tag `AppImportExport`:

```yaml
  /api/v1/apps/{appId}/versions/{versionId}/export:
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
      operationId: exportAppBlueprint
      tags: [AppImportExport]
      summary: Export app version as bundled Blueprint JSON
      responses:
        '200':
          description: Blueprint JSON
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppBlueprintResponse'
        '404':
          description: App or version not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/apps/import/analyze:
    post:
      operationId: analyzeImport
      tags: [AppImportExport]
      summary: Upload Blueprint and analyze for conflicts
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AppBlueprintResponse'
      responses:
        '200':
          description: Import preview with conflicts and suggestions
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ImportPreviewResponse'

  /api/v1/apps/import/execute:
    post:
      operationId: executeImport
      tags: [AppImportExport]
      summary: Execute import after preview confirmation
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ExecuteImportRequest'
      responses:
        '201':
          description: Import executed, apps created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ImportResultResponse'
        '400':
          description: Invalid blueprint or unresolved conflicts
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
```

Schemas:

```yaml
    AppBlueprintResponse:
      type: object
      properties:
        exportedAt:
          type: string
          format: date-time
        exportedBy:
          type: string
          format: uuid
        apps:
          type: array
          items:
            $ref: '#/components/schemas/BlueprintApp'

    BlueprintApp:
      type: object
      properties:
        slug:
          type: string
        name:
          type: string
        description:
          type: string
        version:
          type: string
        dependencies:
          type: array
          items:
            type: string
            description: slug of dependency app
        snapshot:
          type: object
          description: Full app snapshot (definitions, fields)

    ImportPreviewResponse:
      type: object
      properties:
        apps:
          type: array
          items:
            $ref: '#/components/schemas/ImportPreviewApp'
        warnings:
          type: array
          items:
            type: string
        canImport:
          type: boolean

    ImportPreviewApp:
      type: object
      properties:
        originalSlug:
          type: string
        originalName:
          type: string
        hasSlugCollision:
          type: boolean
        suggestedSlug:
          type: string
        suggestedName:
          type: string
        versionCompatible:
          type: boolean

    ExecuteImportRequest:
      type: object
      required: [blueprint]
      properties:
        blueprint:
          $ref: '#/components/schemas/AppBlueprintResponse'
        slugOverrides:
          type: object
          additionalProperties:
            type: string
          description: Map of originalSlug to overrideSlug

    ImportResultResponse:
      type: object
      properties:
        importedApps:
          type: array
          items:
            $ref: '#/components/schemas/AppResponse'
```

Verify: `./gradlew openApiGenerateApp compileJava test`
Commit: `"feat: add OpenAPI import/export endpoints"`

---

## Task 2: AppBlueprint Model + Enhance Snapshots

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/model/AppBlueprint.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppSnapshotService.java`

### 2a. AppBlueprint — the bundled export format

```java
package io.webcontify.app.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AppBlueprint(
    OffsetDateTime exportedAt,
    UUID exportedBy,
    List<BlueprintApp> apps
) {
    public record BlueprintApp(
        String slug,
        String name,
        String description,
        String version,
        List<String> dependencies,  // slugs of dependency apps
        AppSnapshot snapshot
    ) {}
}
```

### 2b. Enhance AppSnapshotService.buildSnapshot()

Populate dependencies from AppDependencyService:

```java
// Add AppDependencyService as dependency
// In buildSnapshot():
var deps = dependencyService.listDependencies(UUID.fromString(app.getId().toString()));
var snapshotDeps = deps.stream()
    .filter(AppDependency::isDirect)
    .map(d -> new AppSnapshot.AppSnapshotDependency(d.getDependsOnAppSlug(), ""))
    .toList();
// Pass snapshotDeps instead of List.of()
```

Commit: `"feat: add AppBlueprint model, populate dependencies in snapshots"`

---

## Task 3: AppExportService — TDD

**Files:**
- Create: `AppExportService.java` + `AppExportServiceTest.java`

Methods:

### exportBlueprint(UUID appId, UUID versionId) → AppBlueprint
1. Get the version via AppVersionService
2. Download the snapshot from S3 via AppSnapshotService
3. Resolve transitive dependencies via AppDependencyService
4. For each dependency: find latest published version, download its snapshot
5. Bundle everything into AppBlueprint (main app + all dependency apps)
6. Set exportedAt + exportedBy

Unit Tests (4):
1. shouldExportBlueprintWithMainApp
2. shouldIncludeTransitiveDependencies — A→B→C exports all 3
3. shouldThrowWhenVersionNotFound
4. shouldThrowWhenNoDependencyVersionPublished

Commit: `"feat: add AppExportService for bundled Blueprint export (TDD, 4 tests)"`

---

## Task 4: AppImportService — TDD

**Files:**
- Create: `AppImportService.java` + `AppImportServiceTest.java`

Methods:

### analyzeBlueprint(AppBlueprint blueprint) → ImportPreview
1. For each app in blueprint: check if slug exists in tenant → slug collision
2. If collision: generate suggested slug (append "-imported" or "-2")
3. For dependencies: check if they exist in tenant and version is compatible (Semver major match)
4. Generate warnings for incompatible versions
5. Set canImport = true if no blocking errors

### executeImport(AppBlueprint blueprint, Map<String, String> slugOverrides) → List<App>
1. For each app in blueprint (topological order based on dependencies):
   - Apply slug override if present, otherwise auto-rename if collision
   - Create app via AppService.create()
   - Set status to ACTIVE via AppService.updateStatus()
   - Create version via AppVersionService.publish()
2. Wire dependencies between imported apps
3. Return list of created apps

IMPORTANT: Import order matters — dependency apps must be created before apps that depend on them. Use topological sort on the dependency graph.

Unit Tests (5):
1. shouldDetectSlugCollision
2. shouldSuggestAlternativeSlug
3. shouldDetectIncompatibleVersion — major mismatch
4. shouldImportSingleApp
5. shouldImportWithDependenciesInCorrectOrder

Commit: `"feat: add AppImportService with analyze and execute (TDD, 5 tests)"`

---

## Task 5: AppImportExportController + Integration Tests

**Files:**
- Create: Controller + Integration Tests

Controller implements the generated import/export API interface.

Endpoints:
- exportBlueprint → 200 + JSON body
- analyzeImport → 200 + preview
- executeImport → 201 + created apps

Integration Tests (5):
1. shouldExportBlueprintForPublishedVersion
2. shouldAnalyzeBlueprintAndDetectCollisions
3. shouldImportNewApp
4. shouldAutoRenameOnSlugCollision
5. shouldImportWithDependencies

Commits: controller + tests separately.

---

## Task 6: Frontend — Import/Export UI

**Files:**
- Create: ImportBlueprintDialog, ImportPreview
- Modify: App list page (Import button), Version list (Export button wiring)
- i18n keys

### 6a. i18n keys

EN:
```json
"importExport": {
  "export": "Export Blueprint",
  "exportSuccess": "Blueprint exported",
  "import": "Import Blueprint",
  "importDialog": {
    "title": "Import Blueprint",
    "description": "Upload a Blueprint JSON file to import apps into your workspace.",
    "upload": "Upload File",
    "analyze": "Analyze",
    "analyzing": "Analyzing...",
    "execute": "Import",
    "cancel": "Cancel",
    "successToast": "Apps imported successfully"
  },
  "preview": {
    "title": "Import Preview",
    "appsToImport": "Apps to import",
    "slugCollision": "Slug collision — will be renamed to {suggestedSlug}",
    "versionWarning": "Version mismatch: requires {required}, found {found}",
    "canImport": "Ready to import",
    "cannotImport": "Cannot import — resolve issues first"
  }
}
```

DE: German translations.

### 6b. ImportBlueprintDialog

- File upload (accept .json)
- Parse JSON, call analyzeImport
- Show ImportPreview with results
- "Import" button calls executeImport
- On success: toast, close, invalidate app list query

### 6c. Wire Export button on Version List

The AppVersionList already has an "Export" button per version. Wire it to call exportAppBlueprint and trigger a JSON file download.

### 6d. Import Blueprint button on App List

The apps list page already has an "Import Blueprint" button. Wire it to open ImportBlueprintDialog.

Verify: `pnpm build && pnpm vitest run`
Commit: `"feat: add Import/Export UI with preview and Blueprint download"`

---

## Task 7: Final Verification

- Full backend + frontend tests
- Build
- Update PROGRESS.md
- Commit

---

## Known Trade-offs

- **Synchronous import** — no async job scheduler. Fine for blueprints with <10 apps. Async can be added later.
- **Snapshot dependencies currently empty** — Content Definitions don't exist yet. The export bundles app metadata + dependency structure. Full content export comes with Content Domain.
- **Semver check is major-only** — minor/patch differences generate warnings, not errors.
- **No file size limit enforced** — can be added at controller level if needed.
- **Import creates new versions** — imported apps get a published version immediately.
