# Content Definition Permissions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a role-scoped permission override for content definitions: roles grant app-level access by default (cascade), but can restrict to a subset of specific definitions (override). Frontend resolves the binary `canEdit` into granular actions and renders ghost nodes for inaccessible references.

**Architecture:** OpenFGA `restricted` marker relation on `app` combined with `but not restricted from parent` in `content_definition` relations provides the override semantics declaratively. Role save logic maintains the `restricted` tuple synchronously with per-definition grants. List endpoints use `listObjects` fallback to include per-definition-only users. Inaccessible entries are returned as a discriminated-union schema variant with only `{id, accessible:false}` — no name/app/description leakage.

**Tech Stack:** Spring Boot 4 / Java 21 / OpenFGA SDK, Next.js 16 / React 19 / Orval-generated hooks / React Query, shadcn/ui, Playwright E2E.

**Spec:** `docs/superpowers/specs/2026-04-13-content-definition-permissions-design.md`

**Prerequisites already in place (uncommitted):**
- `AppService.list` filters via tenant-wide check + `listObjects("viewer", "app")`.
- `ContentDefinitionService.listByApp` manual per-app `viewer:app` check with `AccessDeniedException`.
- `AppController.listApps`, `ContentDefinitionController.listContentDefinitions` — `@RequiresPermission` removed.
- `AuthService.getEffectivePermissions` injects `viewer` into `app_management` when user has any `viewer:app`.
- Frontend `ContentSpotlight` gated on `canCreate`.

---

## Task 0: Commit prior bug fixes

**Files:**
- Modify (already on disk): `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/repository/AppRepository.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/api/AppController.java`
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java`
- Modify: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java`
- Modify: `apps/platform/src/features/content/components/content-spotlight.tsx`
- Modify: `apps/platform/src/features/content/components/content-definition-graph.tsx`

- [ ] **Step 1: Run backend tests to confirm prior fixes compile and pass**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) nx test backend
```
Expected: all tests pass.

- [ ] **Step 2: Run frontend tests**

```bash
cd apps/platform && pnpm vitest run
```
Expected: all tests pass.

- [ ] **Step 3: Commit the backend bug fix (per-app list filtering)**

```bash
git add apps/backend/src/main/java/io/webcontify/app/repository/AppRepository.java \
        apps/backend/src/main/java/io/webcontify/app/service/AppService.java \
        apps/backend/src/main/java/io/webcontify/app/api/AppController.java \
        apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java \
        apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java \
        apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java
git commit -m "feat(apps): filter app list by OpenFGA access, per-app check for content definitions"
```

- [ ] **Step 4: Commit navigation permission fix**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java
git commit -m "feat(auth): expose app_management.viewer when user has any per-app viewer"
```

- [ ] **Step 5: Commit frontend spotlight fix**

```bash
git add apps/platform/src/features/content/components/content-spotlight.tsx \
        apps/platform/src/features/content/components/content-definition-graph.tsx
git commit -m "fix(content): gate spotlight \"create definition\" on canEdit"
```

---

## Task 1: OpenFGA model — add `restricted` relation and `but not` cascade

**Files:**
- Modify: `infra/openfga/model.fga`
- Test: `infra/openfga/tests/test-content-definition-override.yaml` (new)

- [ ] **Step 1: Update `model.fga`**

Replace the `app` and `content_definition` type blocks:

```fga
type app
  relations
    define management: [app_management]
    define restricted: [user, role#assignee]
    define viewer: [user, role#assignee] or viewer from management
    define editor: [user, role#assignee] or editor from management
    define admin: [user, role#assignee] or admin from management

type content_definition
  relations
    define parent: [app]
    define viewer: [user, role#assignee] or (viewer from parent but not restricted from parent)
    define editor: [user, role#assignee] or (editor from parent but not restricted from parent)
    define admin:  [user, role#assignee] or (admin  from parent but not restricted from parent)
    define creator: [user, role#assignee] or (admin  from parent but not restricted from parent)
    define deleter: [user, role#assignee] or (editor from parent but not restricted from parent)
```

- [ ] **Step 2: Write OpenFGA test file**

Create `infra/openfga/tests/test-content-definition-override.yaml`:

```yaml
name: content definition override semantics
model_file: ../model.fga
tuples:
  - user: app_management:t1
    relation: tenant
    object: tenant:t1
  - user: user:u_cascade
    relation: viewer
    object: app_management:t1
  - user: app_management:t1
    relation: management
    object: app:a1
  - user: app:a1
    relation: parent
    object: content_definition:d1
  - user: app:a1
    relation: parent
    object: content_definition:d2
  - user: role:r_override#assignee
    relation: viewer
    object: app:a1
  - user: role:r_override#assignee
    relation: restricted
    object: app:a1
  - user: role:r_override#assignee
    relation: viewer
    object: content_definition:d1
  - user: user:u_override
    relation: assignee
    object: role:r_override

tests:
  - name: cascade user sees both definitions
    check:
      - user: user:u_cascade
        object: content_definition:d1
        assertions:
          viewer: true
      - user: user:u_cascade
        object: content_definition:d2
        assertions:
          viewer: true

  - name: override user sees only granted definition
    check:
      - user: user:u_override
        object: content_definition:d1
        assertions:
          viewer: true
      - user: user:u_override
        object: content_definition:d2
        assertions:
          viewer: false
```

- [ ] **Step 3: Regenerate `model.json`**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew transformOpenFgaModel
```
Expected: `apps/backend/src/main/resources/openfga/model.json` is rewritten.

- [ ] **Step 4: Run OpenFGA CLI test**

```bash
fga model test --tests infra/openfga/tests/test-content-definition-override.yaml
```
Expected: both tests pass. If `fga` CLI is not installed locally, document in commit that tests must be verified in CI.

- [ ] **Step 5: Commit**

```bash
git add infra/openfga/model.fga \
        infra/openfga/tests/test-content-definition-override.yaml \
        apps/backend/src/main/resources/openfga/model.json
git commit -m "feat(openfga): add restricted relation for per-app content definition override"
```

---

## Task 2: Move create / positions / undo / redo / name-availability from tenant-wide to per-app

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java:45,94,105,123,130`
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java` — add per-app permission checks to methods that lack `@RequiresPermission` on the controller path
- Test: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java` — extend

Reason: previously all non-ID-scoped mutating endpoints required tenant-wide `editor:app_management`. Under the new model, users can be editor on a specific app only.

- [ ] **Step 1: Write failing test — create requires editor on app**

Append to `ContentDefinitionServiceTest.java`:

```java
@Test
void shouldThrowAccessDeniedOnCreateWhenUserLacksEditorOnApp() {
    AppEntity appEntity = createAppEntity();
    when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
    when(permissionService.check(argThat(r ->
            r != null && "editor".equals(r.action()) && "app".equals(r.resourceType())
                    && TEST_APP_ID.equals(r.resourceId()))))
            .thenReturn(false);

    ContentDefinition input = new ContentDefinition();
    input.setName("Article");
    assertThatThrownBy(() -> service.create("my-app", input))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verify(definitionRepository, never()).save(any());
}
```

- [ ] **Step 2: Run test, expect failure**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew test --tests io.webcontify.content.definition.service.ContentDefinitionServiceTest.shouldThrowAccessDeniedOnCreateWhenUserLacksEditorOnApp
```
Expected: FAIL (currently no check).

- [ ] **Step 3: Add guard helper and apply to mutating service methods**

In `ContentDefinitionService.java`, add a private helper and call it from `create`, `updatePositions`, `undo`, `redo`:

```java
private void requireAppEditor(UUID appId) {
    var tenantInfo = TenantContext.getTenantInformation();
    if (tenantInfo.platformAdmin()) return;
    boolean allowed = permissionService.check(new CheckPermissionRequest(
            TenantContext.getCurrentUserId(), "editor", "app", appId));
    if (!allowed) {
        throw new AccessDeniedException("Insufficient permissions");
    }
}
```

In `create(String appSlug, ContentDefinition definition)`, after `AppEntity app = resolveApp(appSlug);`:

```java
requireAppEditor(app.getId());
```

Same guard at start of `updatePositions`, `undo`, `redo` (each resolves an app first — keep the pattern: resolve app → requireAppEditor → proceed).

- [ ] **Step 4: Remove tenant-wide annotations in controller**

In `ContentDefinitionController.java`:

- Line 45: remove `@RequiresPermission(action = "editor", resource = "app_management")` from `createContentDefinition`. Add comment: `// Per-app permission check in service`.
- Line 94: remove annotation from `checkContentDefinitionNameAvailability`. Replace with manual check via new service method `isNameAvailableWithViewerCheck(appSlug, name)`; or inline the check in controller by calling `permissionService.check(...)` after resolving the app.
- Line 105: remove annotation from `batchUpdatePositions`.
- Line 123: remove annotation from `undoContentChange`.
- Line 130: remove annotation from `redoContentChange`.

For `checkContentDefinitionNameAvailability`, add a one-liner in the controller:

```java
@Override
public ResponseEntity<CheckContentDefinitionNameAvailability200Response>
        checkContentDefinitionNameAvailability(
                String appSlug, String name, @Nullable UUID excludeId) {
    boolean available = definitionService.isNameAvailable(appSlug, name);
    return ResponseEntity.ok(new CheckContentDefinitionNameAvailability200Response(available));
}
```
and in `ContentDefinitionService.isNameAvailable`, resolve the app first and call the new `requireAppViewer(appId)` (mirror of `requireAppEditor` but with action `"viewer"`).

- [ ] **Step 5: Run the failing test again, expect pass**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew test --tests io.webcontify.content.definition.service.ContentDefinitionServiceTest
```
Expected: all passing. Fix existing `create` tests by stubbing `permissionService.check(...)` with the editor matcher to return true.

- [ ] **Step 6: Update existing create tests to stub permission**

In each passing create-path test (e.g. `shouldCreateDefinitionWithGeneratedSlugAndIds`, `shouldFailOnDuplicateName`, etc.), add at the top of arrange block:

```java
when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(true);
```

- [ ] **Step 7: Run all backend tests**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```
Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java \
        apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java \
        apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java
git commit -m "feat(content): move mutating content-definition endpoints to per-app permission"
```

---

## Task 3: ListContentDefinitions fallback — per-definition-only users

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java` — `listByApp`
- Test: `apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java`

- [ ] **Step 1: Write failing test — user without `viewer:app` but with specific `viewer:content_definition:Y` sees Y only**

```java
@Test
void shouldReturnOnlyAccessibleDefinitionsWhenUserLacksAppViewerButHasSpecific() {
    AppEntity appEntity = createAppEntity();
    UUID def1 = UUID.randomUUID();
    UUID def2 = UUID.randomUUID();
    when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
    when(permissionService.check(argThat(r ->
            r != null && "viewer".equals(r.action()) && "app".equals(r.resourceType()))))
            .thenReturn(false);
    when(permissionService.listObjects(argThat(r ->
            r != null && "viewer".equals(r.action()) && "content_definition".equals(r.resourceType()))))
            .thenReturn(List.of(def1));

    ContentDefinitionEntity entity1 = createDefinitionEntity("Article", "article");
    entity1.setId(def1);
    entity1.setAppId(appEntity.getId());
    when(definitionRepository.findByIdInAndAppIdAndAuditInfoDeletedAtIsNull(
            List.of(def1), appEntity.getId()))
            .thenReturn(List.of(entity1));

    ContentDefinition d1 = new ContentDefinition();
    d1.setName("Article");
    when(definitionMapper.toDomainList(List.of(entity1))).thenReturn(List.of(d1));

    List<ContentDefinition> result = service.listByApp("my-app");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("Article");
}

@Test
void shouldReturnEmptyWhenUserHasNoAppAccessAndNoSpecificAccess() {
    AppEntity appEntity = createAppEntity();
    when(appRepository.findBySlugAndAuditInfoDeletedAtIsNull("my-app")).thenReturn(Optional.of(appEntity));
    when(permissionService.check(any(CheckPermissionRequest.class))).thenReturn(false);
    when(permissionService.listObjects(any(ListPermittedObjectsRequest.class)))
            .thenReturn(List.of());

    List<ContentDefinition> result = service.listByApp("my-app");

    assertThat(result).isEmpty();
    verify(definitionRepository, never()).findByAppIdAndAuditInfoDeletedAtIsNull(any());
}
```

Import added: `import io.webcontify.identity.model.ListPermittedObjectsRequest;` at top of test file.

- [ ] **Step 2: Run tests, expect failure**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew test --tests io.webcontify.content.definition.service.ContentDefinitionServiceTest
```
Expected: two new tests fail.

- [ ] **Step 3: Add new repository method**

In `apps/backend/src/main/java/io/webcontify/content/definition/repository/ContentDefinitionRepository.java`, add:

```java
List<ContentDefinitionEntity> findByIdInAndAppIdAndAuditInfoDeletedAtIsNull(
        Collection<UUID> ids, UUID appId);
```
(Add `import java.util.Collection;` if not present.)

- [ ] **Step 4: Update `ContentDefinitionService.listByApp`**

Replace the current method body (previously throws AccessDenied when app check fails) with:

```java
@Transactional(readOnly = true)
public List<ContentDefinition> listByApp(String appSlug) {
    AppEntity app = resolveApp(appSlug);
    var tenantInfo = TenantContext.getTenantInformation();
    UUID userId = TenantContext.getCurrentUserId();

    boolean appViewer = tenantInfo.platformAdmin()
            || permissionService.check(new CheckPermissionRequest(
                    userId, "viewer", "app", app.getId()));

    List<ContentDefinitionEntity> entities;
    if (appViewer) {
        entities = definitionRepository.findByAppIdAndAuditInfoDeletedAtIsNull(app.getId());
    } else {
        List<UUID> accessibleIds = permissionService.listObjects(
                new ListPermittedObjectsRequest(userId, "viewer", "content_definition"));
        if (accessibleIds.isEmpty()) {
            return List.of();
        }
        entities = definitionRepository.findByIdInAndAppIdAndAuditInfoDeletedAtIsNull(
                accessibleIds, app.getId());
    }

    return definitionMapper.toDomainList(entities);
}
```

Add import: `import io.webcontify.identity.model.ListPermittedObjectsRequest;`

- [ ] **Step 5: Run tests, expect pass**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew test --tests io.webcontify.content.definition.service.ContentDefinitionServiceTest
```
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/repository/ContentDefinitionRepository.java \
        apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java \
        apps/backend/src/test/java/io/webcontify/content/definition/service/ContentDefinitionServiceTest.java
git commit -m "feat(content): list definitions fallback via OpenFGA listObjects for per-definition access"
```

---

## Task 4: OpenAPI — extend role app-permissions and list-definitions schemas

**Files:**
- Modify: `openapi/identity-api.yaml`
- Modify: `openapi/content-api.yaml` (or equivalent)

- [ ] **Step 1: Extend `AppPermissionEntry` and `SetRoleAppPermissionsRequest`**

In `openapi/identity-api.yaml`, replace the `AppPermissionEntry` schema:

```yaml
AppPermissionEntry:
  type: object
  required:
    - appId
    - appName
    - actions
    - isRestricted
    - definitionPermissions
  properties:
    appId:
      type: string
      format: uuid
    appName:
      type: string
    appSlug:
      type: string
    actions:
      type: array
      items:
        type: string
        enum: [viewer, editor, admin]
    isRestricted:
      type: boolean
      description: When true, content definition access is restricted to the explicit definitionPermissions list; cascade from app is disabled for this role on this app.
    definitionPermissions:
      type: array
      items:
        $ref: '#/components/schemas/RoleDefinitionPermissionEntry'

RoleDefinitionPermissionEntry:
  type: object
  required:
    - definitionId
    - definitionName
    - actions
  properties:
    definitionId:
      type: string
      format: uuid
    definitionName:
      type: string
    actions:
      type: array
      items:
        type: string
        enum: [viewer, editor, admin, creator, deleter]
```

Extend `SetRoleAppPermissionsRequest` to match:

```yaml
SetRoleAppPermissionsRequest:
  type: object
  required:
    - appPermissions
  properties:
    appPermissions:
      type: array
      items:
        type: object
        required:
          - appId
          - actions
          - restricted
          - definitionPermissions
        properties:
          appId:
            type: string
            format: uuid
          actions:
            type: array
            items:
              type: string
              enum: [viewer, editor, admin]
          restricted:
            type: boolean
          definitionPermissions:
            type: array
            description: Authoritative when restricted=true. Ignored when restricted=false (all per-definition tuples for this role+app are cleared on save).
            items:
              type: object
              required:
                - definitionId
                - actions
              properties:
                definitionId:
                  type: string
                  format: uuid
                actions:
                  type: array
                  items:
                    type: string
                    enum: [viewer, editor, admin, creator, deleter]
```

- [ ] **Step 2: Extend content definition list and dependency graph schemas**

In `openapi/content-api.yaml` (locate the `ContentDefinitionListResponse` schema), add:

```yaml
ContentDefinitionListResponse:
  type: object
  required: [items, referencedDefinitions]
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/ContentDefinitionResponse'
    referencedDefinitions:
      type: array
      description: Metadata for REFERENCE targets not present in items (cross-definition or cross-app). Two variants; strictly disjoint — inaccessible entries contain only id+accessible.
      items:
        oneOf:
          - $ref: '#/components/schemas/ReferencedDefinitionAccessible'
          - $ref: '#/components/schemas/ReferencedDefinitionInaccessible'
        discriminator:
          propertyName: accessible
          mapping:
            "true":  '#/components/schemas/ReferencedDefinitionAccessible'
            "false": '#/components/schemas/ReferencedDefinitionInaccessible'

ReferencedDefinitionAccessible:
  type: object
  required: [id, accessible, name, appId, appName]
  properties:
    id:
      type: string
      format: uuid
    accessible:
      type: boolean
      enum: [true]
    name:
      type: string
    appId:
      type: string
      format: uuid
    appName:
      type: string

ReferencedDefinitionInaccessible:
  type: object
  required: [id, accessible]
  properties:
    id:
      type: string
      format: uuid
    accessible:
      type: boolean
      enum: [false]
```

Extend the dependency graph endpoint schema (`AppDependencyGraphResponse` or equivalent in `openapi/app-api.yaml`) similarly: each node becomes a `oneOf` of `AppGraphNodeAccessible` (id + accessible=true + name + slug + status + isCurrent) and `AppGraphNodeInaccessible` (id + accessible=false only).

- [ ] **Step 3: Run backend codegen**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
```
Expected: generated DTOs compile (may produce warnings; fix by running full build).

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```
Expected: green. Any compile errors in controllers/services due to new required fields — fix by setting defaults (`isRestricted=false`, empty `definitionPermissions`) until Task 5/6 fills them properly.

- [ ] **Step 4: Commit**

```bash
git add openapi/identity-api.yaml openapi/content-api.yaml openapi/app-api.yaml
git commit -m "feat(openapi): extend schemas for content definition override and ghost references"
```

---

## Task 5: Backend — role preview and save logic

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java`
- Test: `apps/backend/src/test/java/io/webcontify/identity/service/RoleServiceTest.java` (new tests)

The existing `RoleService.getRoleAppPermissions(roleId)` returns app entries with `actions`. We extend it to populate `isRestricted` and `definitionPermissions` per app. The existing `setRoleAppPermissions(roleId, request)` is extended to process `restricted` and `definitionPermissions`.

- [ ] **Step 1: Write failing preview test — restricted app returns isRestricted=true and per-def entries**

```java
@Test
void previewIncludesIsRestrictedAndDefinitionPermissions() {
    UUID roleId = UUID.randomUUID();
    UUID appId = UUID.randomUUID();
    UUID defId = UUID.randomUUID();
    // Stub: openFgaService.listObjects for role#assignee -> viewer -> app -> returns [appId]
    //       openFgaService.read for restricted tuples -> returns appId
    //       openFgaService.listObjects for role#assignee -> viewer -> content_definition -> returns [defId]
    //       repository lookup for app + definition metadata

    // Arrange stubs (see RoleService for exact shape; align with existing patterns at lines ~440/470)
    // ...

    RoleAppPermissionsResponse response = roleService.getRoleAppPermissions(roleId);

    AppPermissionEntry entry = response.getItems().get(0);
    assertThat(entry.getAppId()).isEqualTo(appId);
    assertThat(entry.getIsRestricted()).isTrue();
    assertThat(entry.getDefinitionPermissions()).hasSize(1);
    assertThat(entry.getDefinitionPermissions().get(0).getDefinitionId()).isEqualTo(defId);
}
```

(Concrete stub wiring uses the same `openFgaService` / `appRepository` / `definitionRepository` pattern already present in `RoleService`. Inspect lines around 440/470 for existing `listObjects` usage and model test stubs accordingly.)

- [ ] **Step 2: Run test, expect failure**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew test --tests io.webcontify.identity.service.RoleServiceTest.previewIncludesIsRestrictedAndDefinitionPermissions
```

- [ ] **Step 3: Implement preview extension**

In `RoleService.getRoleAppPermissions(UUID roleId)`, after collecting the base app entries:

```java
// For each app entry, read the `restricted` tuple and list direct content_definition tuples.
String roleUser = "role:" + roleId + "#assignee";
var storeId = permissionService.resolveStoreId(); // expose via new public helper or reuse existing path

for (AppPermissionEntry entry : entries) {
    // isRestricted: does role#assignee -> restricted -> app:X tuple exist?
    boolean restricted = openFgaService.check(new OpenFgaCheckRequest(
            storeId, /* userId= */ null, "restricted", "app", entry.getAppId()))
        // Note: check API expects a user subject. Use read-tuples against
        // (user=roleUser, relation="restricted", object="app:X") instead:
        ;
    var readResult = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
            storeId, roleUser, "restricted", "app:" + entry.getAppId()));
    entry.setIsRestricted(!readResult.tuples().isEmpty());

    // Per-definition entries: list all content_definition tuples for this role on
    // definitions whose parent is this app.
    List<String> defObjects = openFgaService.listObjects(new OpenFgaListObjectsRequest(
            storeId, /* userId */ roleId, "viewer", "content_definition"));
    // For each defObject, fetch appId via repository, filter to this app, and
    // collect actions via batchCheck on viewer/editor/admin/creator/deleter.
    // ...
    entry.setDefinitionPermissions(defPermEntries);
}
```

(Exact helper signatures: introduce `readTuples` on `OpenFgaService` if not present. Follow existing OpenFGA SDK usage in `OpenFgaService.java:162` for listObjects pattern.)

- [ ] **Step 4: Run test, expect pass; run full test class**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew test --tests io.webcontify.identity.service.RoleServiceTest
```

- [ ] **Step 5: Write failing save tests**

Three cases:
1. Save with `restricted=false` clears any existing per-definition tuples and the `restricted` marker.
2. Save with `restricted=true` sets the `restricted` marker and writes per-definition tuples matching the request.
3. Transition `restricted:true → false` clears per-def tuples.

```java
@Test
void saveRestrictedTrueWritesRestrictedMarkerAndPerDefinitionTuples() { /* ... */ }
@Test
void saveRestrictedFalseClearsPerDefinitionTuplesAndMarker() { /* ... */ }
@Test
void saveTransitionFromRestrictedToCascadeRemovesDefinitionTuples() { /* ... */ }
```

- [ ] **Step 6: Run tests, expect failure**

- [ ] **Step 7: Implement save extension**

In `RoleService.setRoleAppPermissions(UUID roleId, SetRoleAppPermissionsRequest request)`, for each `AppPermissionInput app`:

```java
String roleUser = "role:" + roleId + "#assignee";
String appObject = "app:" + app.getAppId();

// 1. Diff base app actions (existing logic: viewer/editor/admin at app level)
// ... existing diff & write/delete ...

// 2. Restricted marker
boolean currentlyRestricted = /* read from OpenFGA */;
if (app.getRestricted() && !currentlyRestricted) {
    permissionService.grantRelation(new RelationshipTuple(roleUser, "restricted", appObject));
} else if (!app.getRestricted() && currentlyRestricted) {
    permissionService.revokeRelation(new RelationshipTuple(roleUser, "restricted", appObject));
}

// 3. Per-definition tuples
if (app.getRestricted()) {
    // desired = app.getDefinitionPermissions() expanded per action
    // current = listObjects(role, viewer|editor|..., content_definition) filtered to this app
    // diff: write missing, delete extras
} else {
    // clear all per-definition tuples for this role on definitions whose parent is this app
}
```

(Expand with concrete loop code when implementing; follow existing grant/revoke patterns in `RoleService.java:440-500`.)

- [ ] **Step 8: Run all tests**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

- [ ] **Step 9: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java \
        apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java \
        apps/backend/src/test/java/io/webcontify/identity/service/RoleServiceTest.java
git commit -m "feat(roles): support restricted marker and per-definition permissions in role save/preview"
```

---

## Task 6: Backend — extended list-definitions response with referenced definitions

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/api/ContentDefinitionController.java` — `listContentDefinitions`
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/service/ContentDefinitionService.java` — `listByApp` signature or new method
- Modify: `apps/backend/src/main/java/io/webcontify/content/definition/mapper/ContentDefinitionMapper.java` — map to new response shape
- Test: `ContentDefinitionServiceTest.java` — new test

- [ ] **Step 1: Write failing test — response contains ghost entries for inaccessible REFERENCE targets**

```java
@Test
void listByAppReturnsInaccessibleReferencedDefinitionsWithMinimalMetadata() {
    // Arrange: app a1 with def1 visible (editor), def1 has REFERENCE field to def2 (not visible).
    // Stub: permission check on def2 viewer → false.
    // Expect: service result contains referencedDefinitions: [{id: def2, accessible: false}]
    //         with no name/appId/appName.
    // ...
    var result = service.listByAppWithReferences("my-app");
    assertThat(result.referencedDefinitions())
        .singleElement()
        .satisfies(r -> {
            assertThat(r.id()).isEqualTo(def2Id);
            assertThat(r.accessible()).isFalse();
            assertThat(r.name()).isNull();
            assertThat(r.appId()).isNull();
            assertThat(r.appName()).isNull();
        });
}
```

- [ ] **Step 2: Run test, expect failure**

- [ ] **Step 3: Introduce domain type and service method**

Create `apps/backend/src/main/java/io/webcontify/content/definition/model/ContentDefinitionListResult.java`:

```java
package io.webcontify.content.definition.model;

import java.util.List;

public record ContentDefinitionListResult(
        List<ContentDefinition> items,
        List<ReferencedDefinition> referencedDefinitions
) {
    public record ReferencedDefinition(
            java.util.UUID id,
            boolean accessible,
            String name,       // null when !accessible
            java.util.UUID appId,  // null when !accessible
            String appName     // null when !accessible
    ) {}
}
```

Rename/extend `listByApp` to `listByAppWithReferences` (keep old method for internal callers or mark deprecated). Impl:

```java
public ContentDefinitionListResult listByAppWithReferences(String appSlug) {
    // existing viewer/listObjects logic → items
    // collect all REFERENCE field targets from items
    // for each targetId not in items:
    //   boolean accessible = permissionService.check(viewer, content_definition, targetId)
    //   if accessible: repository lookup name + parent app + app name
    //   else: emit {id, accessible=false, name=null, ...}
}
```

- [ ] **Step 4: Update controller + mapper**

In `ContentDefinitionController.listContentDefinitions`:

```java
ContentDefinitionListResult result = definitionService.listByAppWithReferences(appSlug);
ContentDefinitionListResponse response = new ContentDefinitionListResponse();
response.setItems(result.items().stream().map(this::toResponseWithPermissions).toList());
response.setReferencedDefinitions(result.referencedDefinitions().stream()
        .map(this::toReferencedDefinitionResponse).toList());
return ResponseEntity.ok(response);
```

Add mapper helper that produces the correct `oneOf` variant. For `accessible=false`, construct the inaccessible variant with only id+accessible; for `accessible=true`, full variant.

- [ ] **Step 5: Run tests**

- [ ] **Step 6: Write mapper unit test for leakage prevention**

```java
@Test
void inaccessibleReferencedDefinitionSerializesWithExactlyTwoFields() {
    var ghost = new ContentDefinitionListResult.ReferencedDefinition(
            UUID.randomUUID(), false, null, null, null);
    var mapper = new ContentDefinitionMapper();
    var dto = mapper.toReferencedResponse(ghost);
    var json = new ObjectMapper().writeValueAsString(dto);
    var tree = new ObjectMapper().readTree(json);
    assertThat(tree.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "accessible");
    assertThat(tree.get("accessible").asBoolean()).isFalse();
}
```

- [ ] **Step 7: Run all backend tests**

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/content/definition/...
git commit -m "feat(content): return ghost metadata for inaccessible REFERENCE targets"
```

---

## Task 7: Backend — dependency graph endpoint accessibility annotation

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/app/api/AppController.java` — `getAppDependencyGraph` (or wherever it lives)
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppDependencyService.java` (or equivalent)
- Test: `apps/backend/src/test/java/io/webcontify/app/service/AppDependencyServiceTest.java`

- [ ] **Step 1: Locate existing dependency graph service**

```bash
grep -r "getAppDependencyGraph\|dependency-graph" apps/backend/src/main/java
```
Note the file paths in your scratch notes.

- [ ] **Step 2: Write failing test — inaccessible dependency app node has only id + accessible=false**

```java
@Test
void dependencyGraphRendersInaccessibleAppNodesWithMinimalMetadata() {
    // Arrange: root app a1, depends on a2. User has viewer on a1 only.
    // Stub permissionService.check(viewer, app, a2) → false.
    // ...
    var graph = service.getGraph(a1Id);
    var a2Node = graph.nodes().stream().filter(n -> n.id().equals(a2Id)).findFirst().orElseThrow();
    assertThat(a2Node.accessible()).isFalse();
    assertThat(a2Node.name()).isNull();
    assertThat(a2Node.slug()).isNull();
}
```

- [ ] **Step 3: Run test, expect failure**

- [ ] **Step 4: Implement annotation**

In the dependency service, after collecting all apps in the graph:

```java
for (var node : nodes) {
    if (tenantInfo.platformAdmin() || node.id().equals(rootAppId)) {
        node.accessible = true;
    } else {
        node.accessible = permissionService.check(new CheckPermissionRequest(
                userId, "viewer", "app", node.id()));
    }
    if (!node.accessible) {
        node.name = null;
        node.slug = null;
        node.status = null;
    }
}
```

Mapper emits `AppGraphNodeAccessible` or `AppGraphNodeInaccessible` based on flag. Same mapper leakage test as Task 6 Step 6.

- [ ] **Step 5: Run all backend tests**

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/app/... apps/backend/src/test/...
git commit -m "feat(apps): annotate dependency graph nodes with accessibility flag"
```

---

## Task 8: Frontend — regenerate Orval hooks and confirm compile

**Files:**
- Modify (generated): `apps/platform/src/generated/identity`, `apps/platform/src/generated/content`, `apps/platform/src/generated/app`

- [ ] **Step 1: Regenerate**

```bash
cd apps/platform && pnpm generate
```
Expected: new generated files reflect the OpenAPI changes, including `useDeleteContentDefinition` hook if missing.

- [ ] **Step 2: TypeScript compile check**

```bash
cd apps/platform && pnpm tsc --noEmit
```
Expected: errors in consumers due to required `isRestricted` / `definitionPermissions` fields. These are fixed in Task 9.

- [ ] **Step 3: Commit generated files**

```bash
git add apps/platform/src/generated/
git commit -m "chore(platform): regenerate API hooks and types"
```

---

## Task 9: Frontend — role editor toggle and editable definition checkboxes

**Files:**
- Modify: `apps/platform/src/features/roles/components/app-permissions-editor.tsx`
- Modify: `apps/platform/src/messages/en.json`
- Modify: `apps/platform/src/messages/de.json`
- Test: `apps/platform/src/features/roles/components/__tests__/app-permissions-editor.test.tsx`

- [ ] **Step 1: Add i18n keys**

In `en.json` (under `roles.permissions` or nearest logical section):
```json
{
  "cascadeToggleLabel": "Include all definitions automatically",
  "cascadeToggleHelp": "When on, this role has access to every current and future content definition of this app. When off, select specific definitions below.",
  "definitionActionsHeader": "Definition permissions"
}
```

In `de.json`:
```json
{
  "cascadeToggleLabel": "Alle Definitionen automatisch einbeziehen",
  "cascadeToggleHelp": "Wenn aktiviert, hat diese Rolle Zugriff auf alle aktuellen und zukünftigen Content Definitions dieser App. Wenn deaktiviert, wähle unten spezifische Definitionen aus.",
  "definitionActionsHeader": "Definition-Berechtigungen"
}
```

- [ ] **Step 2: Write failing Vitest — toggle off reveals editable checkboxes, save payload includes restricted=true**

```tsx
// apps/platform/src/features/roles/components/__tests__/app-permissions-editor.test.tsx
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { AppPermissionsEditor } from "../app-permissions-editor";

describe("AppPermissionsEditor — cascade/override toggle", () => {
  it("enables per-definition checkboxes when cascade toggle is turned off", async () => {
    // Arrange mock hooks: app A with definitions [def1, def2], toggle initially on.
    // Render, assert definition checkboxes disabled.
    // Click toggle off, assert definition checkboxes enabled.
    // Click def1 viewer, click save, assert mutate called with
    //   { appPermissions: [{ appId, actions: ["viewer"], restricted: true,
    //     definitionPermissions: [{ definitionId: def1, actions: ["viewer"] }] }] }
  });
});
```

- [ ] **Step 3: Extend component state**

In `app-permissions-editor.tsx`, extend `PerAppPermissionState`:

```tsx
type PerAppPermissionState = Record<
  string,
  {
    actions: Record<string, boolean>;
    isRestricted: boolean;
    definitions: Record<string, Record<string, boolean>>; // defId → action → bool
  }
>;
```

Build initial state from `AppPermissionEntry.isRestricted` and `definitionPermissions`. Dirty-check compares both maps.

- [ ] **Step 4: Render toggle + editable definition grid**

Inside the existing per-app row (or a new collapsible block), add:

```tsx
<div className="flex items-center gap-2">
  <Switch
    id={`cascade-${app.appId}`}
    checked={!perApp[app.appId].isRestricted}
    onCheckedChange={(checked) => setIsRestricted(app.appId, !checked)}
    disabled={isSystemRole}
  />
  <Label htmlFor={`cascade-${app.appId}`}>{t("cascadeToggleLabel")}</Label>
  <HelpTooltip text={t("cascadeToggleHelp")} />
</div>
```

Keep the existing `ContentDefinitionRows` collapsible block, but render its checkboxes as **enabled when `!isRestricted`** (i.e. toggle is OFF) and **disabled + all-checked visually when toggle is ON**.

- [ ] **Step 5: Build save payload**

```tsx
const payload: SetRoleAppPermissionsRequest = {
  appPermissions: Object.entries(perApp).map(([appId, state]) => ({
    appId,
    actions: PER_APP_ACTIONS.filter((a) => state.actions[a]),
    restricted: state.isRestricted,
    definitionPermissions: state.isRestricted
      ? Object.entries(state.definitions)
          .map(([definitionId, actions]) => ({
            definitionId,
            actions: Object.entries(actions).filter(([, v]) => v).map(([k]) => k),
          }))
          .filter((d) => d.actions.length > 0)
      : [],
  })),
};
```

- [ ] **Step 6: Run test, expect pass**

```bash
cd apps/platform && pnpm vitest run src/features/roles
```

- [ ] **Step 7: Commit**

```bash
git add apps/platform/src/features/roles/ apps/platform/src/messages/
git commit -m "feat(roles): cascade/override toggle and editable per-definition checkboxes"
```

---

## Task 10: Frontend — content definition graph granular permissions

**Files:**
- Modify: `apps/platform/src/features/content/components/content-definition-graph.tsx`
- Modify: `apps/platform/src/features/content/components/definition-node.tsx`
- Modify: `apps/platform/src/app/(workspace)/apps/[appSlug]/content/page.tsx` (pass `appEffectiveActions` prop)

- [ ] **Step 1: Derive flags per definition and per app**

In `content-definition-graph.tsx`, replace the `canEdit` prop with flag derivation inside the map callback:

```tsx
interface ContentDefinitionGraphProps {
  appSlug: string;
  appEffectiveActions: string[]; // from getApp response
}

const canCreateInApp = appEffectiveActions.includes("editor");
const canLayoutApp = appEffectiveActions.includes("editor");

// inside definitions.map((def) => {
const defActions = def.effectiveActions ?? [];
const canView = defActions.includes("viewer");
const canEdit = defActions.includes("editor");
const canDelete = defActions.includes("deleter");
// ...
return {
  id: def.id,
  type: "definition",
  data: {
    definition: def,
    onEditDefinition: canEdit ? (id) => /* ... */ : undefined,
    onEditField: canEdit ? /* ... */ : undefined,
    onAddField: canEdit ? /* ... */ : undefined,
    onDeleteDefinition: canDelete ? /* ... */ : undefined, // Task 11 wires this
  },
};
```

Replace remaining app-level guards:
- `onConnect={canLayoutApp ? onConnect : undefined}` — line 528
- `nodesDraggable={canLayoutApp}` — line 536
- `nodesConnectable={canLayoutApp}` — line 537
- `{canCreateInApp && ( ...+ button... )}` — line 500, 553
- Undo/Redo buttons — gate on `canLayoutApp`
- Spotlight `canCreate={canCreateInApp}` — replace the existing `canEdit` pass-through

- [ ] **Step 2: Update page to pass appEffectiveActions**

In `apps/platform/src/app/(workspace)/apps/[appSlug]/content/page.tsx`, load the app via `useGetAppBySlug` (or existing hook) and pass `effectiveActions` to `ContentDefinitionGraph`.

- [ ] **Step 3: Run frontend tests**

```bash
cd apps/platform && pnpm vitest run
```

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/features/content/ apps/platform/src/app/
git commit -m "feat(content): derive granular graph permissions from effectiveActions"
```

---

## Task 11: Frontend — wire up delete definition

**Files:**
- Modify: `apps/platform/src/features/content/components/content-definition-graph.tsx`
- Modify: `apps/platform/src/features/content/components/definition-node.tsx` (existing button stays)
- Create: `apps/platform/src/features/content/components/delete-definition-dialog.tsx`
- Modify: `apps/platform/src/messages/en.json`, `de.json`
- Test: `apps/platform/src/features/content/components/__tests__/delete-definition-dialog.test.tsx`

- [ ] **Step 1: Add i18n keys**

`en.json` under `content.delete`:
```json
{
  "title": "Delete definition",
  "body": "This will soft-delete \"{name}\". Definitions referencing it will keep their edges but show a restricted placeholder.",
  "confirmButton": "Delete",
  "cancelButton": "Cancel",
  "success": "Definition deleted",
  "error": "Failed to delete definition"
}
```

`de.json` mirrored with German translations.

- [ ] **Step 2: Write failing Vitest**

```tsx
// delete-definition-dialog.test.tsx
it("calls mutate with appSlug and definitionId on confirm", async () => {
  const mutate = vi.fn();
  // render dialog open, click confirm, assert mutate called with correct args
});
```

- [ ] **Step 3: Implement dialog component**

```tsx
// apps/platform/src/features/content/components/delete-definition-dialog.tsx
"use client";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { AlertDialog, /* ... */ } from "@/components/ui/alert-dialog";
import { useDeleteContentDefinition, getListContentDefinitionsQueryKey } from "@/generated/content";
import { useQueryClient } from "@tanstack/react-query";

interface Props {
  appSlug: string;
  definitionId: string;
  definitionName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function DeleteDefinitionDialog({ appSlug, definitionId, definitionName, open, onOpenChange }: Props) {
  const t = useTranslations("content.delete");
  const qc = useQueryClient();
  const { mutateAsync, isPending } = useDeleteContentDefinition({
    mutation: {
      onSuccess: () => {
        toast.success(t("success"));
        qc.invalidateQueries({ queryKey: getListContentDefinitionsQueryKey(appSlug) });
        onOpenChange(false);
      },
      onError: () => toast.error(t("error")),
    },
  });

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      {/* title, description with name interpolation, cancel + confirm buttons */}
    </AlertDialog>
  );
}
```

- [ ] **Step 4: Wire callback in graph**

In `content-definition-graph.tsx`, add state for `deletingDefinition: { id, name } | null` and pass:

```tsx
onDeleteDefinition: canDelete
  ? (id: string) => setDeletingDefinition({ id, name: def.name })
  : undefined,
```

Render `<DeleteDefinitionDialog />` if state is set.

- [ ] **Step 5: Run tests, expect pass**

- [ ] **Step 6: Commit**

```bash
git add apps/platform/src/features/content/ apps/platform/src/messages/
git commit -m "feat(content): wire up delete definition with confirm dialog"
```

---

## Task 12: Frontend — ghost node for inaccessible references

**Files:**
- Create: `apps/platform/src/features/content/components/restricted-definition-node.tsx`
- Modify: `apps/platform/src/features/content/components/content-definition-graph.tsx`
- Modify: `apps/platform/src/messages/en.json`, `de.json`

- [ ] **Step 1: Add i18n key**

`en.json` → `content.graph.restrictedDefinition`: `"Restricted access"`
`de.json` → `content.graph.restrictedDefinition`: `"Eingeschränkter Zugriff"`
Also `content.graph.restrictedTooltip`: `"You don't have access to this definition"` / `"Du hast keinen Zugriff auf diese Definition"`.

- [ ] **Step 2: Create ghost node component**

```tsx
// restricted-definition-node.tsx
"use client";
import { useTranslations } from "next-intl";
import { Lock } from "lucide-react";
import { Handle, Position } from "@xyflow/react";

export function RestrictedDefinitionNode() {
  const t = useTranslations("content.graph");
  return (
    <div
      className="flex items-center gap-2 rounded-md border border-dashed border-muted-foreground/40 bg-muted/30 px-4 py-3 text-sm text-muted-foreground"
      title={t("restrictedTooltip")}
    >
      <Lock className="size-4" />
      <span>{t("restrictedDefinition")}</span>
      <Handle type="target" position={Position.Left} isConnectable={false} />
      <Handle type="source" position={Position.Right} isConnectable={false} />
    </div>
  );
}
```

- [ ] **Step 3: Register node type and render ghosts**

In `content-definition-graph.tsx`:

```tsx
import { RestrictedDefinitionNode } from "./restricted-definition-node";

const nodeTypes = {
  definition: DefinitionNode,
  restrictedDefinition: RestrictedDefinitionNode,
};
```

In the `useMemo` that builds `flowNodes`, after building regular nodes, append ghost nodes from `data?.data?.referencedDefinitions`:

```tsx
const refs = data?.data?.referencedDefinitions ?? [];
for (const ref of refs) {
  if (!ref.accessible) {
    defNodes.push({
      id: ref.id,
      type: "restrictedDefinition",
      position: { x: 0, y: 0 }, // layout engine places it
      data: {},
    });
  } else {
    // cross-app accessible ghost — existing onAddGhostNode pattern or dedicated type
  }
}
```

In the edge builder (which resolves REFERENCE fields), ghost target IDs already have a node now so edges render naturally. Add a style: edges whose target is a ghost render with `strokeDasharray` and reduced opacity — check target node type in the edge renderer.

- [ ] **Step 4: Vitest — ghost node renders when referencedDefinitions contains inaccessible entry**

```tsx
it("renders a restricted placeholder node for inaccessible references", async () => {
  // mock useListContentDefinitions to return items: [art], referencedDefinitions: [{id:auth, accessible:false}]
  // render graph
  // assert screen has lock icon + "Restricted access" label
  // assert no "Author" name text appears anywhere
});
```

- [ ] **Step 5: Run tests, expect pass**

- [ ] **Step 6: Commit**

```bash
git add apps/platform/src/features/content/ apps/platform/src/messages/
git commit -m "feat(content): ghost node for inaccessible REFERENCE targets"
```

---

## Task 13: Frontend — dependency graph ghost app nodes

**Files:**
- Modify: `apps/platform/src/features/apps/components/app-dependency-graph.tsx`
- Create: `apps/platform/src/features/apps/components/restricted-app-node.tsx`
- Modify: messages

- [ ] **Step 1: i18n**

`en.json` under `apps.dependencies`:
```json
{
  "restrictedApp": "Restricted access",
  "restrictedAppTooltip": "You don't have access to this app"
}
```
`de.json` mirrored.

- [ ] **Step 2: Create ghost app node component**

```tsx
// restricted-app-node.tsx
// Mirror of RestrictedDefinitionNode but with app styling from AppNode.
```

- [ ] **Step 3: Wire into dependency graph**

In `app-dependency-graph.tsx`:

```tsx
const nodeTypes = {
  appNode: AppNode,
  restrictedAppNode: RestrictedAppNode,
};

// In the node builder:
const appNodes = graphNodes.map((node) => {
  if (!node.accessible) {
    return {
      id: node.id,
      type: "restrictedAppNode",
      position: { x: 0, y: 0 },
      data: { id: node.id },
    };
  }
  return {
    id: node.id,
    type: "appNode",
    position: { x: 0, y: 0 },
    data: { ...node, isTransitive: !node.isCurrent && !directNodeIds.has(node.id) },
  };
});
```

Edge styling: dashed for edges with inaccessible endpoints — check in the edge renderer.

- [ ] **Step 4: Vitest**

```tsx
it("renders ghost node for inaccessible dependency", async () => {
  // mock response: nodes [{id: a1, accessible: true, name, ...}, {id: a2, accessible: false}]
  // assert lock icon appears, no name leak for a2
});
```

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/features/apps/ apps/platform/src/messages/
git commit -m "feat(apps): ghost nodes for inaccessible dependencies"
```

---

## Task 14: E2E tests — Playwright

**Files:**
- Create: `apps/e2e/tests/content-definition-permissions.spec.ts`

- [ ] **Step 1: Draft scenarios**

Scenarios to cover (each a separate `test(...)` block):

1. Admin creates role "App-A Editor" with cascade toggle ON → save → reload → toggle still ON, no per-def entries stored.
2. Admin assigns "App-A Editor" to user → user logs in → sees App A in sidebar, navigates in, sees all definitions editable.
3. Admin edits role → toggles OFF for App A, checks only `Article: viewer` → save.
4. User reloads → sidebar still shows App A, inside sees only Article (read-only), ghost node labeled "Restricted access" for Author reference, no name leak.
5. User cannot delete, cannot open "+" action, cannot drag nodes.

- [ ] **Step 2: Write test skeleton using existing helpers**

```ts
// apps/e2e/tests/content-definition-permissions.spec.ts
import { test, expect } from "@playwright/test";
import { createRoleViaApi, assignRoleToUser, loginAs } from "./helpers/setup";

test.describe("content definition permissions — cascade and override", () => {
  test.beforeEach(async () => {
    // Seed: tenant, admin user, secondary user, app A with defs [Article, Author]
  });

  test("override mode restricts visible definitions", async ({ page }) => {
    // ... sequence above ...
    await expect(page.getByTestId("graph-node-article")).toBeVisible();
    await expect(page.getByTestId("graph-node-author")).not.toBeVisible();
    await expect(page.getByText("Restricted access")).toBeVisible();
    await expect(page.getByText("Author")).not.toBeVisible();
  });
});
```

(Use `data-testid` selectors. Test text in English only per E2E conventions.)

- [ ] **Step 3: Run E2E tests**

```bash
cd apps/e2e && npx playwright test tests/content-definition-permissions.spec.ts
```
Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add apps/e2e/tests/content-definition-permissions.spec.ts
git commit -m "test(e2e): content definition permission cascade and override"
```

---

## Self-Review Checklist (run after all tasks)

- [ ] OpenFGA test file covers cascade and override
- [ ] Mapper unit test asserts inaccessible ghost serializes with exactly two JSON keys
- [ ] Role save round-trip tested (cascade → override → cascade)
- [ ] Frontend Vitest covers toggle + definition checkbox behavior
- [ ] E2E Playwright covers full user flow: role → assign → login → graph display
- [ ] All new UI strings in both `en.json` and `de.json`
- [ ] No references to `canEdit` as a generic graph-wide prop remaining (replaced by derived flags)
- [ ] `listContentDefinitions` works for: tenant-admin, per-app viewer, per-app editor, per-definition-only viewer, no access
- [ ] Delete flow works for user with `deleter:content_definition` and is hidden otherwise
- [ ] Ghost nodes in graph and dependency tab hide names (visual inspection)
- [ ] `pnpm dev` full stack smoke test: admin UI → create override role → login as user → verify restricted view
- [ ] `docs/PROGRESS.md` updated with "Content Definition Permissions — done"

## Notes on order dependencies

- Task 0 must run first (clean state).
- Tasks 1–7 are backend-only and can be implemented in order with tests between.
- Task 8 (codegen) depends on Tasks 4–7.
- Task 9–13 depend on Task 8.
- Task 14 (E2E) runs last, against the full stack.

Subagent-driven execution recommended — each task has clear file boundaries and commits independently.
