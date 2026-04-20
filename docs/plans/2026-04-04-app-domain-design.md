# App Domain — Design Document

## 1. Overview

The App Domain introduces Apps as the top-level grouping for Content Definitions, Pages, and Workflows. An App is a self-contained, versionable package within a Tenant.

**Who:** Tenant users with appropriate permissions (Viewer/Editor/Admin per App or global).

**Why:**
- Structure: No loose resources at tenant level — everything belongs to an App
- Versioning: Immutable snapshots for rollback and traceability
- Marketplace-ready: Bundled export/import of Apps as self-contained blueprints with transitive dependencies
- Access control: Granular permissions at App level

**Lifecycle:** DRAFT → ACTIVE (via Publish) → INACTIVE (Readonly). Editing an ACTIVE App automatically creates a new Draft. Publishing creates an immutable version snapshot.

---

## 2. Functional Areas

### 2.1 App CRUD + Lifecycle

- Create (Name → Auto-Slug), Read, Update, Soft Delete
- Status: DRAFT → ACTIVE (Publish) → INACTIVE (Deactivate) → ACTIVE (Reactivate)
- Published App is readonly; editing automatically creates a new Draft
- Publish: Creates version snapshot, Semver auto-suggested (overridable)
- Delete: Soft Delete, blocked if other Apps depend on this one (with info which ones)
- INACTIVE: Entries readonly, no changes, no new Draft

### 2.2 App Versioning

- Immutable snapshots on Publish (Definitions as JSON in S3)
- Version history with Semver, auto-suggested based on change type
- Rollback loads an old version as a new Draft (broken Draft allowed, Publish validates)
- Unified Diff between any two versions (Accordion inline)
- Release notes per version (optional, freetext)

### 2.3 Draft Revisions (ChangeRecord)

- Each save action creates a ChangeRecord (payload = JSON Patch, RFC 6902)
- Linear history per Draft, closed on Publish
- Rollback of individual revisions via Reverse-Patch
- Unified Diff between revisions (same Accordion UX as versions)
- ChangeRecord is generic, reusable for Content Entries + Workflows later

### 2.4 App Dependencies

- Explicit dependencies between Apps, transitive resolution (like Maven)
- Cycle detection (graph traversal, immediately rejected)
- Removing a dependency shows Impact Preview (what breaks)
- React Flow graph (direct vs. transitive visualized)
- Problems View: invisible at 0 errors, Badge + Panel when problems exist, Publish blocked

### 2.5 App Import/Export

- Export: Bundled JSON Blueprint (version snapshot + transitive dependencies)
- Import: Upload → Analyze → Preview (name conflicts, Semver compatibility) → Confirm
- Auto-rename on name conflicts with info before execution
- Semver range check on dependencies (Major must match, Minor equal or higher)

### 2.6 App Permissions (OpenFGA)

- App as resource type: Viewer (read), Editor (Draft editing, Dependencies), Admin (Publish, Rollback, Status, Delete, Import/Export)
- Inheritance: Tenant roles → App roles → Resource roles
- Global App permission + Per-App overrides (extend only, not restrict)
- Navigation hidden without permission

### 2.7 Frontend App Management UI

- App List (Table + Actions Menu + Import Blueprint)
- App Detail (Tabs: Overview, Definitions, Dependencies, Versions, Revisions, Settings)
- Accordion Diffs on Versions + Revisions Tabs
- Dependency Graph (React Flow)
- Permission Matrix (two tabs: General + Apps & Content)
- All shadcn, Whitelabel-compatible via CSS Variables

---

## 3. Architecture

### Module Structure (Spring Modulith)

```
app/
  api/
    AppController.java
    AppDependencyController.java
    AppVersionController.java
    AppImportExportController.java
  service/
    AppService.java
    AppDependencyService.java
    AppVersionService.java
    AppRevisionService.java
    AppImportExportService.java
    AppValidationService.java
  mapper/
    AppMapper.java
  model/
    App.java
    AppStatus.java
    AppDependency.java
    AppVersion.java
  repository/
    AppRepository.java
    AppDependencyRepository.java
    AppVersionRepository.java
    entity/
      AppEntity.java
      AppDependencyEntity.java
      AppVersionEntity.java
  event/
    AppPublishedEvent.java
    AppStatusChangedEvent.java

shared/
  changelog/
    model/
      ChangeRecord.java
    repository/
      ChangeRecordRepository.java
      entity/
        ChangeRecordEntity.java
    service/
      ChangeRecordService.java
```

### Key Design Decisions

- `ChangeRecord` lives in `shared` module — reusable for all domains
- `AppVersionService` coordinates Publish: build snapshot → upload to S3 → create AppVersion entry → close ChangeRecords
- `AppValidationService` checks before Publish: broken references, missing dependencies, cycles → Problems list
- `AppDependencyService` does graph traversal for cycle detection and transitive resolution
- `AppImportExportService` uses S3 for Blueprint files (same bucket as snapshots)
- Events via Spring Modulith `@ApplicationModuleListener` (same as Tenant Provisioning)

### Publish Flow

```
User clicks Publish
  → AppVersionService.publish(appId, semver, releaseNotes)
    → AppValidationService.validate(appId) → Problems? → Abort
    → Build snapshot (serialize all Definitions + Dependencies)
    → S3: upload snapshot-{appId}-{version}.json
    → Create AppVersion entry in DB (version, s3Key, releaseNotes, publishedBy, publishedAt)
    → Set App status to ACTIVE, hasDraft = false
    → Mark ChangeRecords for this Draft as closed
    → Publish AppPublishedEvent
```

### Rollback Flow

```
User clicks Rollback on v1.0.0
  → AppVersionService.rollback(appId, versionId)
    → Load snapshot from S3
    → Deserialize snapshot into current Draft definitions
    → Set App.hasDraft = true
    → Create ChangeRecord (source: "system:rollback", payload: full diff)
    → User sees Draft with old state, can review + publish
```

### i18n

- Backend: RFC 7807 + `code` field with i18n key (e.g., `error.app.name.duplicate`, `error.app.dependency.circular`, `error.app.publish.hasProblems`)
- Frontend: All labels, buttons, tooltips, empty states, error messages via `useTranslations()` (next-intl)
- New namespace sections: `apps.*`, `versions.*`, `revisions.*`, `dependencies.*`, `permissions.apps.*`
- Keys in `en.json` + `de.json`

---

## 4. Data Model

### Entities

```
App (Aggregate Root)
  id: UUID (UUIDv7)
  tenantId: UUID (FK → tenant)
  name: String (unique per Tenant)
  slug: String (unique per Tenant, auto-generated)
  description: String (optional)
  status: AppStatus (DRAFT, ACTIVE, INACTIVE)
  hasDraft: Boolean (default false)
  auditInfo: AuditInfo (created/updated/deleted at/by)

AppDependency (Value Object)
  appId: UUID (FK → app)
  dependsOnId: UUID (FK → app)
  tenantId: UUID

AppVersion (Entity)
  id: UUID (UUIDv7)
  appId: UUID (FK → app)
  tenantId: UUID
  version: String (Semver, e.g., "1.0.0")
  s3Key: String (path to snapshot in S3)
  releaseNotes: String (optional)
  publishedAt: Timestamp
  publishedBy: UUID

ChangeRecord (Shared, generic)
  id: UUID (UUIDv7)
  tenantId: UUID
  entityType: String (app, content_definition, content_entry...)
  entityId: UUID
  changeType: String (created, updated, deleted, status_changed, field_added...)
  payload: JSONB (JSON Patch or Event)
  source: String (user:{id}, workflow:{id}:step:{id}, system:import, system:rollback)
  draftVersion: String (optional — groups records to a draft, null when closed)
  createdAt: Timestamp
  createdBy: UUID
```

### Database Schema

```sql
CREATE TABLE app (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    description     TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    has_draft       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    UNIQUE(tenant_id, name),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE app_dependency (
    app_id          UUID NOT NULL REFERENCES app(id) ON DELETE CASCADE,
    depends_on_id   UUID NOT NULL REFERENCES app(id),
    tenant_id       UUID NOT NULL,
    PRIMARY KEY (app_id, depends_on_id),
    CHECK (app_id != depends_on_id)
);

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

-- Indexes
CREATE INDEX idx_change_record_lookup
    ON change_record(entity_type, entity_id, created_at);
CREATE INDEX idx_change_record_draft
    ON change_record(entity_type, entity_id, draft_version)
    WHERE draft_version IS NOT NULL;

-- RLS on all tables
ALTER TABLE app ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

ALTER TABLE app_dependency ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app_dependency
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

ALTER TABLE app_version ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app_version
    USING (tenant_id = current_setting('app.tenant_id')::UUID);

ALTER TABLE change_record ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON change_record
    USING (tenant_id = current_setting('app.tenant_id')::UUID);
```

### Snapshot Format (S3, JSON)

```json
{
  "appId": "uuid",
  "version": "1.0.0",
  "metadata": {
    "name": "Blog",
    "slug": "blog",
    "description": "Blog application"
  },
  "dependencies": [
    { "appSlug": "core", "version": "2.1.0" }
  ],
  "contentDefinitions": [
    {
      "name": "blog-post",
      "slug": "blog-post",
      "fields": [
        { "name": "title", "type": "STRING", "required": true },
        { "name": "author", "type": "REFERENCE", "target": "core.user" }
      ]
    }
  ]
}
```

---

## 5. API Surface

### App Management

```
POST   /api/v1/apps                                        Create App
GET    /api/v1/apps                                        List Apps (permission-filtered)
GET    /api/v1/apps/{appId}                                Get App Detail
PUT    /api/v1/apps/{appId}                                Update App (Draft only)
DELETE /api/v1/apps/{appId}                                Delete App (Soft Delete)
PUT    /api/v1/apps/{appId}/status                         Change status (activate, deactivate)
```

### App Versioning

```
POST   /api/v1/apps/{appId}/publish                        Publish (version + releaseNotes in body)
GET    /api/v1/apps/{appId}/versions                       Version history
GET    /api/v1/apps/{appId}/versions/{versionId}           Version detail
POST   /api/v1/apps/{appId}/versions/{versionId}/rollback  Rollback as new Draft
GET    /api/v1/apps/{appId}/versions/{versionId}/diff?compareWith={otherId}  Diff between versions
```

### Draft Revisions

```
GET    /api/v1/apps/{appId}/revisions                      List revisions of current Draft
GET    /api/v1/apps/{appId}/revisions/{revisionId}         Revision detail (patch + diff)
POST   /api/v1/apps/{appId}/revisions/{revisionId}/revert  Revert single revision
```

### App Dependencies

```
POST   /api/v1/apps/{appId}/dependencies                   Add dependency
DELETE /api/v1/apps/{appId}/dependencies/{depAppId}         Remove dependency
GET    /api/v1/apps/{appId}/dependencies                   List dependencies (direct + transitive)
GET    /api/v1/apps/{appId}/dependencies/graph             Dependency graph (nodes + edges for React Flow)
GET    /api/v1/apps/{appId}/dependencies/{depAppId}/impact  Impact preview before removal
```

### Validation / Problems

```
GET    /api/v1/apps/{appId}/problems                       Current problems (broken refs, missing deps)
```

### Import / Export

```
GET    /api/v1/apps/{appId}/versions/{versionId}/export    Download Blueprint JSON
POST   /api/v1/apps/import/analyze                         Upload Blueprint + analyze (conflicts, compatibility)
POST   /api/v1/apps/import/execute                         Execute import (after preview confirmation)
```

### Permissions (extends existing Role endpoints)

```
GET    /api/v1/roles/{roleId}/permissions                  Existing — extended with app permissions
PUT    /api/v1/roles/{roleId}/permissions                  Existing — extended with app permissions
GET    /api/v1/auth/effective-permissions                   Existing — extended with per-app permissions
```

### Response Format Conventions

- Lists: Wrapper object `{ "apps": [...] }`, `{ "versions": [...] }`
- Create: `201 + Location Header + Body`
- Errors: RFC 7807 + `code` field with i18n key
- All endpoints Tenant-scoped via JWT/RLS

---

## 6. UX Flows

### Flow 1: Create App

1. User clicks "+ Create App" on App list
2. Dialog: Name (required), Description (optional)
3. Submit → Slug auto-generated, App created with status DRAFT
4. Redirect to App detail page

### Flow 2: Edit App + Publish

1. User opens ACTIVE App → sees readonly Overview
2. User navigates to Definitions Tab, edits a definition
3. System automatically creates Draft → Header shows DRAFT badge
4. User makes more changes (each save = new revision)
5. User clicks "Publish" → Dialog with auto-suggested Semver + Release Notes textarea
6. System validates (Problems check) → on errors: Publish blocked, Problems badge
7. On success: Snapshot to S3, Version in history, Draft flag reset, status ACTIVE

### Flow 3: Version Diff

1. User opens Versions Tab → sees Draft at top, versions below
2. User clicks version row → Accordion expands
3. Dropdown to choose comparison base (default: previous version)
4. Unified Diff shows inline: added/removed/modified definitions + fields

### Flow 4: Rollback

1. User opens Versions Tab, clicks "Rollback" on an old version
2. Confirmation Dialog: "Version 1.0.0 will be loaded as new Draft. Existing Draft will be overwritten."
3. Confirm → Snapshot loaded, Draft flag set
4. User reviews Draft, fixes potential Problems, publishes as new version

### Flow 5: Manage Dependencies

1. User opens Dependencies Tab → React Flow graph
2. User clicks "+ Add Dependency" → Dialog with App selection (only ACTIVE, no cycles)
3. Dependency appears in graph as new node with edge
4. User wants to remove dependency → clicks remove → Impact Preview shows what breaks
5. Confirm → Dependency removed, Problems badge may appear

### Flow 6: Import

1. User clicks "Import Blueprint" on App list
2. File upload dialog → upload JSON Blueprint
3. System analyzes: name conflicts, dependency compatibility
4. Preview page shows: what will be created, auto-renames, warnings, errors
5. User confirms → Apps created, dependencies linked
6. Redirect to App list with new apps

### Flow 7: Export

1. User opens Versions Tab, clicks "Export" on a version
2. Bundled JSON Blueprint generated (App + transitive dependencies)
3. Download starts

### Flow 8: Fix Problems

1. User sees red badge "2 Problems" in header
2. Click → Problems panel opens
3. Each problem: description + severity (Error/Warning) + "Go to fix" link
4. User clicks link → navigates directly to affected definition/dependency
5. After fix, problem disappears, badge updates

---

## 7. BDU Test Script

### BDU Test 1: Create and Publish App

- **Entry point:** Workspace Sidebar → Apps
- **Steps:**
  1. Click "+ Create App"
  2. Type "My Blog" as name, leave description empty
  3. Click Create
  4. See App detail with status DRAFT
  5. Click "Publish" immediately (no definitions)
  6. Publish dialog: Semver suggested, click Publish
  7. App is ACTIVE, version v0.1.0 in history
- **Success:** App in list with status ACTIVE, version v0.1.0
- **Failure modes:** Double-click on Create (duplicate?), empty name (validation), Publish without changes (allowed for first version)
- **Playwright E2E:** YES
- **Verify in browser:** YES

### BDU Test 2: Edit App and See Draft

- **Entry point:** App list → click ACTIVE App
- **Steps:**
  1. See readonly detail page
  2. Navigate to Definitions Tab
  3. Edit a definition (add field)
  4. Save
  5. See DRAFT badge in header
  6. Navigate to Revisions Tab, see revision #1
  7. Expand revision, see diff
- **Success:** Draft badge visible, revision with correct diff
- **Failure modes:** Back button after save (change lost?), save twice (duplicate revision?), close browser tab without saving
- **Playwright E2E:** YES
- **Verify in browser:** YES

### BDU Test 3: Version Diff + Rollback

- **Entry point:** App detail → Versions Tab
- **Steps:**
  1. See Draft at top, two versions below
  2. Click v1.0.0 → Accordion expands, diff visible
  3. Change comparison base in dropdown to v0.1.0
  4. Diff updates
  5. Click "Rollback" on v0.1.0
  6. Confirmation dialog → confirm
  7. See Draft with old state
- **Success:** Draft contains state of v0.1.0, old Draft overwritten
- **Failure modes:** Rollback while other user edits (optimistic locking), rollback to version with deleted dependency (broken Draft + Problems badge)
- **Playwright E2E:** YES
- **Verify in browser:** YES

### BDU Test 4: Dependency + Impact Preview

- **Entry point:** App detail → Dependencies Tab
- **Steps:**
  1. See empty graph (only own App)
  2. Click "+ Add Dependency"
  3. Select "Core" from list
  4. Core appears as node in graph with edge
  5. Want to remove dependency → click Remove
  6. Impact Preview shows "blog-post.author references Core.User"
  7. Remove anyway → Problems badge appears
- **Success:** Dependency in graph, Impact Preview correct, Problems after removal
- **Failure modes:** Dependency on self (blocked), circular dependency (error), dependency on INACTIVE App (blocked)
- **Playwright E2E:** YES
- **Verify in browser:** YES

### BDU Test 5: Import Blueprint

- **Entry point:** App list → "Import Blueprint"
- **Steps:**
  1. Click "Import Blueprint"
  2. Select JSON file
  3. See Preview: 2 Apps will be created, "Core" renamed to "Core (imported)"
  4. See Semver warning: "Blog needs Core v2.1.0, importing v2.0.0"
  5. Click "Import" anyway (warning, not error)
  6. See new Apps in list
- **Success:** Apps created with correct rename, warning was visible
- **Failure modes:** Corrupt JSON (error message), huge file (size limit), cancel import after analysis (no side effects)
- **Playwright E2E:** YES
- **Verify in browser:** YES

### BDU Test 6: Permission-Gated Navigation

- **Entry point:** Login as user with restricted permissions
- **Steps:**
  1. User has only Viewer on "Blog", no access to "Shop"
  2. Opens Apps list → sees only "Blog", not "Shop"
  3. Opens Blog → sees readonly, no Publish button, no Edit
  4. Tries direct URL: /apps/{shopId} → 403 or redirect
- **Success:** Only permitted Apps visible, actions permission-gated
- **Failure modes:** Direct URL entry (must be 403), API call without permission (must be 403)
- **Playwright E2E:** YES
- **Verify in browser:** YES

---

## 8. Non-Functional Requirements

### Performance

- App list: < 200ms for 50 Apps per Tenant
- Version history: < 300ms for 100 versions
- Revisions list: < 200ms (paginated, max 50 per page)
- Publish (snapshot + S3 upload): < 2s for 50 Content Definitions
- Import analysis: < 3s for Blueprint with 10 Apps
- Dependency graph: < 500ms for graph with 20 nodes
- ChangeRecord queries: performant via composite index, partitioning option for later

### Security

- RLS on all tables (tenant isolation, no gaps)
- OpenFGA permission checks on all endpoints (@RequiresPermission)
- Import: Blueprint validation (no arbitrary JSON execution, schema validation)
- Export: Only published versions exportable (no Draft leak)
- S3: Snapshots/Blueprints per Tenant bucket, pre-signed URLs for download

### Accessibility

- All interactive elements keyboard-navigable
- Accordion diffs: aria-expanded, aria-controls
- React Flow graph: keyboard support + screen reader labels for nodes/edges
- Problems panel: aria-live for dynamic error count
- Color coding never as sole indicator (always + text/icon)

### Scaling

- ChangeRecord: Composite index sufficient for Phase 4, table partitioning by entity_type when needed
- S3 snapshots: No cleanup needed (versions are persistent), optional TTL for deleted Apps
- Pagination on all list endpoints

---

## 9. Scope Boundaries

### IN Scope (Phase 4)

- App CRUD + Lifecycle (DRAFT/ACTIVE/INACTIVE)
- App Versioning with snapshots in S3
- Draft Revisions via ChangeRecord (JSON Patch)
- App Dependencies with cycle detection + transitive resolution
- Dependency Graph (React Flow)
- Problems View (validation, broken references)
- Unified Diff (versions + revisions, accordion)
- Import/Export (bundled Blueprint with transitive dependencies)
- OpenFGA: App type + Viewer/Editor/Admin + Global + Per-App permissions
- Permission Matrix UI (General tab + Apps & Content tab)
- i18n (EN + DE) for all UI texts + error responses
- E2E Tests (Playwright) for all BDU scenarios

### OUT of Scope (later)

- Content Definitions CRUD (Content Domain, separate phase)
- Content Entries (runtime data)
- Pages + Workflows
- Marketplace (share/sell App templates)
- Auto-detection of breaking changes for Semver suggestion
- Event Sourcing for Workflows
- ChangeRecord partitioning (only needed with Content Entry volume)
- App templates (export as reusable blueprint catalog)
- Runtime data export for INACTIVE Apps
- Version constraints on dependencies (Semver ranges on import suffice for now)

### Dependencies

- OpenFGA model must be extended with `app` type
- Existing Role endpoints must be extended with App permissions
- `effective-permissions` endpoint must deliver per-App permissions
- React Flow as new frontend dependency
