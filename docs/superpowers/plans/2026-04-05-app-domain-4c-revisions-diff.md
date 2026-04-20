# App Domain 4c: Draft Revisions + Unified Diff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track every app edit as a JSON Patch revision, show unified diffs, and allow reverting individual revisions.

**Architecture:** AppService.update() creates ChangeRecords with JSON Patch payloads (computed via zjsonpatch). AppRevisionService reads/reverts revisions. Unified Diff UI renders patches as added/removed/modified lines in Accordion rows.

**Tech Stack:** zjsonpatch (JSON Patch RFC 6902), Spring Boot 4, JPA, OpenAPI Generator, Next.js 16, React Query, shadcn/ui

**Design Doc:** `docs/plans/2026-04-04-app-domain-design.md` (Sections 2.3, 6 Flow 3)

**Sub-Plan Dependencies:** 4b (ChangeRecord + Versioning) must be complete

---

## File Structure

### Backend — New Files
- `apps/backend/src/main/java/io/webcontify/app/service/AppRevisionService.java`
- `apps/backend/src/main/java/io/webcontify/app/api/AppRevisionController.java`
- `apps/backend/src/test/java/io/webcontify/app/service/AppRevisionServiceTest.java`
- `apps/backend/src/test/java/io/webcontify/app/api/AppRevisionControllerIntegrationTest.java`

### Backend — Modified Files
- `apps/backend/build.gradle.kts` (add zjsonpatch dependency)
- `openapi/app-api.yaml` (add revision endpoints)
- `apps/backend/src/main/java/io/webcontify/app/service/AppService.java` (create ChangeRecord on update)
- `apps/backend/src/main/java/io/webcontify/shared/changelog/service/ChangeRecordService.java` (add getById method)

### Frontend — New Files
- `apps/platform/src/features/apps/components/app-revision-list.tsx`
- `apps/platform/src/features/apps/components/revision-diff-view.tsx`

### Frontend — Modified Files
- `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx` (add Revisions tab)
- `apps/platform/messages/en.json` (add revision keys)
- `apps/platform/messages/de.json` (add revision keys)

---

## Task 1: Add zjsonpatch Dependency

**Files:**
- Modify: `apps/backend/build.gradle.kts`

- [ ] **Step 1: Add zjsonpatch to dependencies**

Add to the `dependencies` block:
```kotlin
implementation("com.flipkart.zjsonpatch:zjsonpatch:0.4.16")
```

- [ ] **Step 2: Verify compilation**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 3: Commit**

```bash
git add apps/backend/build.gradle.kts
git commit -m "build: add zjsonpatch library for JSON Patch (RFC 6902) support"
```

---

## Task 2: OpenAPI Spec — Revision Endpoints

**Files:**
- Modify: `openapi/app-api.yaml`

- [ ] **Step 1: Add revision endpoints and schemas**

Add these paths:

```yaml
  /api/v1/apps/{appId}/revisions:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: listAppRevisions
      tags: [AppRevision]
      summary: List revisions of current draft
      responses:
        '200':
          description: Revision list
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppRevisionListResponse'

  /api/v1/apps/{appId}/revisions/{revisionId}:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
      - name: revisionId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    get:
      operationId: getAppRevision
      tags: [AppRevision]
      summary: Get revision detail with patch
      responses:
        '200':
          description: Revision detail
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppRevisionResponse'
        '404':
          description: Revision not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/apps/{appId}/revisions/{revisionId}/revert:
    parameters:
      - name: appId
        in: path
        required: true
        schema:
          type: string
          format: uuid
      - name: revisionId
        in: path
        required: true
        schema:
          type: string
          format: uuid
    post:
      operationId: revertAppRevision
      tags: [AppRevision]
      summary: Revert a specific revision
      responses:
        '200':
          description: Revision reverted, app updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AppResponse'
        '404':
          description: Revision not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '409':
          description: Revert conflict — patch cannot be applied
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
```

Add these schemas:

```yaml
    AppRevisionResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        changeType:
          type: string
        patch:
          type: object
          description: JSON Patch (RFC 6902)
        source:
          type: string
        summary:
          type: string
          description: Human-readable summary of changes
        createdAt:
          type: string
          format: date-time
        createdBy:
          type: string
          format: uuid

    AppRevisionListResponse:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/AppRevisionResponse'
```

- [ ] **Step 2: Regenerate and verify**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerateApp compileJava`

- [ ] **Step 3: Commit**

```bash
git add openapi/app-api.yaml
git commit -m "feat: add OpenAPI revision endpoints (list, detail, revert)"
```

---

## Task 3: Modify AppService to Create Revisions on Update

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/shared/changelog/service/ChangeRecordService.java`

- [ ] **Step 1: Add getById to ChangeRecordService**

Add method:
```java
@Transactional(readOnly = true)
public ChangeRecord getById(UUID id) {
    return repository.findById(id)
            .map(this::toModel)
            .orElseThrow(() -> new NotFoundException("change_record.not_found"));
}
```

- [ ] **Step 2: Modify AppService to compute and store JSON Patch on update**

In AppService, add dependencies: ChangeRecordService, ObjectMapper (created with `new ObjectMapper()`).

In `update()`, BEFORE applying the changes:
1. Serialize current state to JSON (name, description)
2. Apply changes
3. Serialize new state to JSON
4. Compute JSON Patch using zjsonpatch
5. If patch is non-empty, create a ChangeRecord

```java
// In AppService constructor, add:
private final ChangeRecordService changeRecordService;
private final ObjectMapper objectMapper = new ObjectMapper();

// In update(), before appMapper.updateEntity():
String beforeJson = serializeAppState(entity);

// After appMapper.updateEntity() and save:
String afterJson = serializeAppState(entity);
createRevisionIfChanged(entity.getId(), beforeJson, afterJson);

// Helper methods:
private String serializeAppState(AppEntity entity) {
    var node = objectMapper.createObjectNode();
    node.put("name", entity.getName());
    node.put("description", entity.getDescription());
    return node.toString();
}

private void createRevisionIfChanged(UUID appId, String beforeJson, String afterJson) {
    try {
        var before = objectMapper.readTree(beforeJson);
        var after = objectMapper.readTree(afterJson);
        var patch = com.flipkart.zjsonpatch.JsonDiff.asJson(before, after);
        if (patch.size() > 0) {
            changeRecordService.create("app", appId, "updated",
                    patch.toString(), "user:" + TenantContext.getTenantInformation().tenantId(),
                    "draft");
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to compute revision diff", e);
    }
}
```

- [ ] **Step 3: Add test for revision creation on update**

In `AppServiceTest.java`, add:
```java
@Test
void shouldCreateRevisionWhenUpdatingApp() {
    // Setup: app exists, update name
    // Verify: changeRecordService.create() called with JSON patch payload
}
```

- [ ] **Step 4: Run tests**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: create JSON Patch revision on app update via zjsonpatch"
```

---

## Task 4: AppRevisionService — TDD

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/service/AppRevisionService.java`
- Create: `apps/backend/src/test/java/io/webcontify/app/service/AppRevisionServiceTest.java`

AppRevisionService methods:

1. `listRevisions(UUID appId)` → List of revision responses
   - Get draft ChangeRecords via changeRecordService.getDraftRecords("app", appId)
   - Map to response objects with human-readable summary

2. `getRevision(UUID appId, UUID revisionId)` → revision response
   - Get ChangeRecord by id, verify it belongs to the app

3. `revertRevision(UUID appId, UUID revisionId)` → App
   - Get the ChangeRecord
   - Compute reverse patch
   - Apply reverse patch to current app state
   - Save app
   - Create new ChangeRecord for the revert action

4. `generateSummary(String jsonPatch)` → String
   - Parse JSON Patch operations
   - Generate human-readable summary: "Changed name from 'Blog' to 'My Blog'"

Unit Tests (5):
1. shouldListDraftRevisions
2. shouldGetRevisionById
3. shouldRevertRevision — verify reverse patch applied
4. shouldThrowNotFoundForMissingRevision
5. shouldGenerateHumanReadableSummary

- [ ] **Commit**

```bash
git commit -m "feat: add AppRevisionService with list, get, revert, summary (TDD, 5 tests)"
```

---

## Task 5: AppRevisionController + Integration Tests

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/app/api/AppRevisionController.java`
- Create: `apps/backend/src/test/java/io/webcontify/app/api/AppRevisionControllerIntegrationTest.java`

Controller implements generated interface (check exact name — likely `AppRevisionApi`).

Integration Tests (5):
1. shouldListRevisionsAfterUpdate — create app, update twice, list revisions → 2 items
2. shouldGetRevisionDetail — verify patch content
3. shouldRevertRevision — update name, revert, verify original name restored
4. shouldReturnEmptyListForNewApp — no revisions on fresh app
5. shouldClearRevisionsAfterPublish — create revisions, publish, list → 0 items (closed)

- [ ] **Commit** (2 commits: controller + tests)

---

## Task 6: Frontend — Revisions Tab + Diff View

**Files:**
- Create: `apps/platform/src/features/apps/components/app-revision-list.tsx`
- Create: `apps/platform/src/features/apps/components/revision-diff-view.tsx`
- Modify: `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx`
- Modify: `apps/platform/messages/en.json`
- Modify: `apps/platform/messages/de.json`

### 6a. i18n keys

EN:
```json
"revisions": {
  "title": "Revisions",
  "noRevisions": "No changes since last publish",
  "revert": "Revert",
  "revertDialog": {
    "title": "Revert Revision",
    "description": "This will undo this specific change. A new revision will be created for the revert.",
    "confirm": "Revert",
    "cancel": "Cancel",
    "successToast": "Revision reverted successfully"
  },
  "diffLabels": {
    "added": "Added",
    "removed": "Removed",
    "replaced": "Changed"
  }
}
```

DE: Same structure with German translations.

### 6b. RevisionDiffView component

Renders a JSON Patch as a unified diff:
- Parse patch operations (add, remove, replace)
- Show each operation as a colored line: green for add, red for remove, yellow for replace
- Show path + old value → new value
- Use monospace font, similar to the version diff mockup from brainstorming

### 6c. AppRevisionList component

- Props: `appId: string`
- Uses generated `useListAppRevisions(appId)` hook
- Collapsible rows (same pattern as AppVersionList)
- Each row: revision number, timestamp, summary, revert button
- Expanded: RevisionDiffView showing the patch
- Empty state when no revisions

### 6d. Add Revisions tab to detail page

Add third tab "Revisions" with count badge when revisions exist.

### 6e. Regenerate Orval + verify

Run: `cd apps/platform && pnpm generate && pnpm build && pnpm vitest run`

- [ ] **Commit**

```bash
git commit -m "feat: add Revisions tab with diff view and revert on App detail page"
```

---

## Task 7: Final Verification

- [ ] Full backend tests
- [ ] Full frontend tests + build
- [ ] Update PROGRESS.md
- [ ] Commit

---

## Known Trade-offs

- **Revision tracking only covers app metadata** (name, description) — Content Definitions don't exist yet. When they do, the same mechanism extends naturally.
- **JSON Patch summary is basic** — just describes field changes. More sophisticated summaries come with Content Definitions.
- **No diff between two arbitrary versions yet** — the version diff endpoint from the design doc is deferred. Revisions show individual changes, not cumulative diffs.
- **Revert may fail if state has diverged** — 409 Conflict returned, user must resolve manually.
