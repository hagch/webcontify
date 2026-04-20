# App Domain 4d: Dependencies + Problems View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement App Dependencies with cycle detection, transitive resolution, React Flow graph visualization, impact preview, and a Problems View that blocks publish when issues exist.

**Architecture:** AppDependencyService manages the dependency graph (add/remove/validate). Graph-traversal for cycle detection and transitive resolution. AppValidationService checks for broken references and blocks publish. React Flow renders the dependency graph with direct vs. transitive edges. Problems View shows as a badge + panel when validation fails.

**Tech Stack:** Spring Boot 4, JPA, @xyflow/react (React Flow), OpenAPI Generator, Next.js 16, shadcn/ui

**Design Doc:** `docs/plans/2026-04-04-app-domain-design.md` (Section 2.4, 2.7)

**Sub-Plan Dependencies:** 4a (App CRUD) must be complete

---

## File Structure

### Backend — New Files
- `apps/backend/src/main/resources/db/migration/V20260405_002__create_app_dependency_table.sql`
- `apps/backend/src/main/java/io/webcontify/app/model/AppDependency.java`
- `apps/backend/src/main/java/io/webcontify/app/repository/entity/AppDependencyEntity.java`
- `apps/backend/src/main/java/io/webcontify/app/repository/AppDependencyRepository.java`
- `apps/backend/src/main/java/io/webcontify/app/service/AppDependencyService.java`
- `apps/backend/src/main/java/io/webcontify/app/service/AppValidationService.java`
- `apps/backend/src/main/java/io/webcontify/app/api/AppDependencyController.java`
- `apps/backend/src/test/java/io/webcontify/app/service/AppDependencyServiceTest.java`
- `apps/backend/src/test/java/io/webcontify/app/service/AppValidationServiceTest.java`
- `apps/backend/src/test/java/io/webcontify/app/api/AppDependencyControllerIntegrationTest.java`

### Backend — Modified Files
- `openapi/app-api.yaml` (add dependency + problems endpoints)
- `apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java` (validate before publish)
- `apps/backend/src/main/java/io/webcontify/app/service/AppService.java` (block delete if dependants exist)

### Frontend — New Files
- `apps/platform/src/features/apps/components/app-dependency-graph.tsx`
- `apps/platform/src/features/apps/components/add-dependency-dialog.tsx`
- `apps/platform/src/features/apps/components/remove-dependency-dialog.tsx`
- `apps/platform/src/features/apps/components/app-problems-panel.tsx`

### Frontend — Modified Files
- `apps/platform/package.json` (add @xyflow/react)
- `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx` (add Dependencies tab, Problems badge)
- `apps/platform/messages/en.json` + `de.json`

---

## Task 1: Flyway Migration — app_dependency Table

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V20260405_002__create_app_dependency_table.sql`

```sql
CREATE TABLE app_dependency (
    app_id          UUID NOT NULL REFERENCES app(id) ON DELETE CASCADE,
    depends_on_id   UUID NOT NULL REFERENCES app(id),
    tenant_id       UUID NOT NULL,
    PRIMARY KEY (app_id, depends_on_id),
    CHECK (app_id != depends_on_id)
);

ALTER TABLE app_dependency ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_app_dependency ON app_dependency
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
```

Verify: `./gradlew test`
Commit: `"feat: add Flyway migration for app_dependency table with RLS"`

---

## Task 2: OpenAPI Spec — Dependency + Problems Endpoints

**Files:**
- Modify: `openapi/app-api.yaml`

Add 6 new paths with tag `AppDependency`:

```yaml
  /api/v1/apps/{appId}/dependencies:
    # GET listAppDependencies → AppDependencyListResponse
    # POST addAppDependency (body: AddDependencyRequest with dependsOnAppId) → AppDependencyResponse (201)

  /api/v1/apps/{appId}/dependencies/{depAppId}:
    # DELETE removeAppDependency → 204 (or 409 if references exist)

  /api/v1/apps/{appId}/dependencies/graph:
    # GET getAppDependencyGraph → DependencyGraphResponse (nodes + edges for React Flow)

  /api/v1/apps/{appId}/dependencies/{depAppId}/impact:
    # GET getAppDependencyImpact → DependencyImpactResponse (list of affected references)

  /api/v1/apps/{appId}/problems:
    # GET listAppProblems → AppProblemsResponse (list of validation problems)
```

Schemas to add:

```yaml
    AddDependencyRequest:
      type: object
      required: [dependsOnAppId]
      properties:
        dependsOnAppId:
          type: string
          format: uuid

    AppDependencyResponse:
      type: object
      properties:
        appId:
          type: string
          format: uuid
        dependsOnAppId:
          type: string
          format: uuid
        dependsOnAppName:
          type: string
        dependsOnAppSlug:
          type: string
        direct:
          type: boolean

    AppDependencyListResponse:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/AppDependencyResponse'

    DependencyGraphResponse:
      type: object
      properties:
        nodes:
          type: array
          items:
            $ref: '#/components/schemas/DependencyGraphNode'
        edges:
          type: array
          items:
            $ref: '#/components/schemas/DependencyGraphEdge'

    DependencyGraphNode:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        slug:
          type: string
        status:
          $ref: '#/components/schemas/AppStatus'
        definitionCount:
          type: integer
        isCurrent:
          type: boolean

    DependencyGraphEdge:
      type: object
      properties:
        source:
          type: string
          format: uuid
        target:
          type: string
          format: uuid
        direct:
          type: boolean

    DependencyImpactResponse:
      type: object
      properties:
        affectedReferences:
          type: array
          items:
            type: string
        canRemove:
          type: boolean

    AppProblem:
      type: object
      properties:
        severity:
          type: string
          enum: [ERROR, WARNING]
        message:
          type: string
        resource:
          type: string
        fixAction:
          type: string

    AppProblemsResponse:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/AppProblem'
        errorCount:
          type: integer
        warningCount:
          type: integer
```

Verify: `./gradlew openApiGenerateApp compileJava test`
Commit: `"feat: add OpenAPI dependency and problems endpoints"`

---

## Task 3: AppDependency Model + Entity + Repository

**Files:**
- Create: Model, Entity, Repository (follow AppVersion pattern — no AuditableBaseEntity for join table)

AppDependencyEntity: composite primary key via @IdClass or @EmbeddedId. Fields: appId, dependsOnId, tenantId.

AppDependencyRepository queries:
- `findByAppId(UUID appId)` → List (direct dependencies)
- `findByDependsOnId(UUID dependsOnId)` → List (reverse — who depends on me)
- `existsByAppIdAndDependsOnId(UUID appId, UUID dependsOnId)` → boolean
- `deleteByAppIdAndDependsOnId(UUID appId, UUID dependsOnId)`

Verify: `./gradlew compileJava`
Commit: `"feat: add AppDependency model, entity, repository"`

---

## Task 4: AppDependencyService — TDD

**Files:**
- Create: `AppDependencyService.java` + `AppDependencyServiceTest.java`

Methods:

### addDependency(UUID appId, UUID dependsOnAppId)
- Verify both apps exist and are in same tenant
- Verify dependsOnApp is ACTIVE (not DRAFT — can't depend on unreleased app)
- Check for self-reference (appId == dependsOnAppId → error)
- Check for cycles via BFS/DFS graph traversal
- Save AppDependencyEntity

### removeDependency(UUID appId, UUID dependsOnAppId)
- Verify dependency exists
- Check impact (currently no content definitions, so always safe to remove)
- Delete entity

### listDependencies(UUID appId) → List<AppDependencyResponse>
- Get direct dependencies
- Resolve transitive dependencies via recursive traversal
- Mark each as direct or transitive

### getDependencyGraph(UUID appId) → nodes + edges
- Build full graph from appId outward (BFS)
- Return nodes (apps) and edges (dependencies) for React Flow
- Mark current app as `isCurrent: true`
- Mark edges as direct or transitive

### getImpact(UUID appId, UUID dependsOnAppId)
- Check if any references from appId point to content in dependsOnAppId
- Currently no content definitions exist — return empty list, canRemove=true
- Structure ready for Content Domain extension

### Cycle Detection Algorithm
```java
private boolean wouldCreateCycle(UUID fromAppId, UUID toAppId) {
    // BFS from toAppId — if we can reach fromAppId, adding this edge creates a cycle
    Set<UUID> visited = new HashSet<>();
    Queue<UUID> queue = new LinkedList<>();
    queue.add(toAppId);
    while (!queue.isEmpty()) {
        UUID current = queue.poll();
        if (current.equals(fromAppId)) return true;
        if (visited.add(current)) {
            dependencyRepository.findByAppId(current)
                .forEach(dep -> queue.add(dep.getDependsOnId()));
        }
    }
    return false;
}
```

Unit Tests (8):
1. shouldAddDependency
2. shouldRejectSelfReference
3. shouldRejectCyclicDependency (A→B→A)
4. shouldRejectTransitiveCycle (A→B→C→A)
5. shouldRemoveDependency
6. shouldListDirectAndTransitiveDependencies
7. shouldBuildDependencyGraph
8. shouldRejectDependencyOnDraftApp

Commit: `"feat: add AppDependencyService with cycle detection and transitive resolution (TDD, 8 tests)"`

---

## Task 5: AppValidationService — TDD

**Files:**
- Create: `AppValidationService.java` + `AppValidationServiceTest.java`

Methods:

### validate(UUID appId) → List<AppProblem>
- Check for broken dependencies (dependency app deleted or inactive)
- Check for unused dependencies (warning, not error)
- Currently no content definitions — just dependency checks
- Returns list of AppProblem (severity, message, resource, fixAction)

### hasErrors(UUID appId) → boolean
- Convenience method — any ERROR-severity problems?

Unit Tests (3):
1. shouldReturnEmptyWhenNoProblem
2. shouldReturnErrorWhenDependencyInactive
3. shouldReturnWarningForUnusedDependency (no references — when content defs exist)

Commit: `"feat: add AppValidationService for problems detection (TDD, 3 tests)"`

---

## Task 6: Integrate Validation with Publish + Delete Guard

**Files:**
- Modify: `AppVersionService.java` — call AppValidationService.validate() before publish, block if errors
- Modify: `AppService.java` — in delete(), check if other apps depend on this one, block with error listing dependants

Modify publish():
```java
var problems = validationService.validate(appId);
var errors = problems.stream().filter(p -> "ERROR".equals(p.severity())).toList();
if (!errors.isEmpty()) {
    throw new BadRequestException("app.publish.has_problems");
}
```

Modify delete():
```java
var dependants = dependencyRepository.findByDependsOnId(id);
if (!dependants.isEmpty()) {
    throw new ConflictException("app.delete.has_dependants");
}
```

Tests: Add to existing AppVersionServiceTest + AppServiceTest.
Commit: `"feat: validate before publish, block delete when dependants exist"`

---

## Task 7: AppDependencyController + Integration Tests

**Files:**
- Create: Controller implementing generated interface
- Create: Integration tests (7 tests)

Controller endpoints:
- POST addDependency → 201
- DELETE removeDependency → 204
- GET listDependencies → 200
- GET getDependencyGraph → 200
- GET getImpact → 200
- GET listProblems → 200

Integration Tests:
1. shouldAddDependency — create 2 apps, add dependency
2. shouldRejectCyclicDependency — A→B, B→A → 409
3. shouldRemoveDependency — add then remove
4. shouldListDirectAndTransitiveDependencies — A→B→C, list from A
5. shouldGetDependencyGraph — verify nodes + edges
6. shouldBlockDeleteWhenDependantsExist — A→B, try delete B → 409
7. shouldListProblemsForInactiveDependency — A→B, deactivate B, get problems

Commits: controller + tests separately.

---

## Task 8: Frontend — Install React Flow + Dependencies Tab

**Files:**
- Modify: `apps/platform/package.json` (add @xyflow/react)
- Create: dependency graph, add/remove dialogs, problems panel
- Modify: detail page (add Dependencies tab + Problems badge)
- i18n keys

### 8a. Install React Flow
```bash
cd apps/platform && pnpm add @xyflow/react
```

### 8b. i18n Keys (EN)
```json
"dependencies": {
  "title": "Dependencies",
  "addDependency": "Add Dependency",
  "removeDependency": "Remove",
  "noDependencies": "No dependencies",
  "direct": "Direct",
  "transitive": "Transitive",
  "addDialog": {
    "title": "Add Dependency",
    "description": "Select an app to depend on.",
    "selectApp": "Select App",
    "submit": "Add",
    "cancel": "Cancel",
    "successToast": "Dependency added",
    "cyclicError": "Adding this dependency would create a cycle",
    "draftError": "Cannot depend on a draft app"
  },
  "removeDialog": {
    "title": "Remove Dependency",
    "description": "Are you sure you want to remove this dependency?",
    "confirm": "Remove",
    "cancel": "Cancel",
    "successToast": "Dependency removed"
  },
  "graph": {
    "currentApp": "Current App"
  }
},
"problems": {
  "title": "Problems",
  "noProblems": "No problems found",
  "errorCount": "{count} errors",
  "warningCount": "{count} warnings",
  "publishBlocked": "Fix errors before publishing"
}
```

### 8c. AppDependencyGraph Component
- Uses @xyflow/react (ReactFlow, Background, Controls)
- Props: `appId: string`
- Fetches graph data via `useGetAppDependencyGraph(appId)`
- Renders nodes as custom cards (app name, status badge, definition count)
- Direct edges as solid lines, transitive as dashed
- Current app node highlighted
- Add Dependency button opens AddDependencyDialog

### 8d. AddDependencyDialog
- Fetches available apps via `useListApps()`, filters out already-dependent + self
- Select dropdown + Add button
- Error handling: 409 (cycle), 400 (draft app)

### 8e. RemoveDependencyDialog
- Confirmation dialog
- Calls removeAppDependency mutation

### 8f. AppProblemsPanel
- Uses `useListAppProblems(appId)` hook
- If no problems: invisible (returns null)
- If problems: Badge in AppHeader + expandable panel
- Each problem: severity icon (red error, amber warning), message, fix action link

### 8g. Modify Detail Page
- Add Dependencies tab (4th tab)
- Add Problems badge in header (next to Publish button)
- Publish button disabled when errors exist (tooltip: "Fix errors before publishing")

Verify: `pnpm build && pnpm vitest run`
Commit: `"feat: add Dependencies tab with React Flow graph and Problems panel"`

---

## Task 9: Final Verification

- Full backend tests
- Full frontend tests + build
- Update PROGRESS.md
- Commit

---

## Known Trade-offs

- **Impact preview always returns canRemove=true** — no Content Definitions exist yet to create actual references. When Content Domain is built, impact checks will be meaningful.
- **Validation only checks dependency status** — broken content references come with Content Domain.
- **React Flow graph is basic** — no drag-to-create edges or inline editing. Add/remove via dialogs.
- **No @RequiresPermission** — added in Sub-Plan 4e.
- **Transitive resolution is eager** — loads full graph. Fine for <100 apps per tenant, revisit if scale demands.
