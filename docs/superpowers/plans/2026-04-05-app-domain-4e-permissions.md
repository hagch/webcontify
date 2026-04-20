# App Domain 4e: Permissions (OpenFGA) + Permission Matrix UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add App as a permission-controlled resource in OpenFGA with global + per-app permissions, @RequiresPermission on all App controllers, and a Permission Matrix UI with two tabs (General + Apps & Content).

**Architecture:** OpenFGA model gets `app_management` type (global, singleton per tenant like user_management) and `app` type (per-app overrides inheriting from app_management). @RequiresPermission annotations on all App endpoints. RoleService extended for app permissions. Permission Matrix UI split into General tab (existing) + Apps & Content tab (new).

**Tech Stack:** OpenFGA, Spring AOP, MapStruct, OpenAPI Generator, Next.js 16, shadcn/ui

**Design Doc:** `docs/plans/2026-04-04-app-domain-design.md` (Section 2.6)

**Sub-Plan Dependencies:** 4a (App CRUD) must be complete

---

## File Structure

### Backend — Modified Files
- `infra/openfga/model.fga` (add app_management + app types)
- `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java` (extend RESOURCE_ACTIONS for app)
- `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java` (extend getEffectivePermissions for app)
- `apps/backend/src/main/java/io/webcontify/app/api/AppController.java` (add @RequiresPermission)
- `apps/backend/src/main/java/io/webcontify/app/api/AppVersionController.java` (add @RequiresPermission)
- `apps/backend/src/main/java/io/webcontify/app/api/AppRevisionController.java` (add @RequiresPermission)
- `apps/backend/src/main/java/io/webcontify/app/api/AppDependencyController.java` (add @RequiresPermission)
- `apps/backend/src/main/java/io/webcontify/app/service/AppService.java` (create OpenFGA tuples on app create)

### Frontend — New Files
- `apps/platform/src/features/roles/components/app-permissions-editor.tsx`

### Frontend — Modified Files
- `apps/platform/src/features/roles/components/role-permissions-editor.tsx` (add Apps tab or integrate)
- `apps/platform/src/hooks/use-permissions.ts` (extend for app permissions)
- `apps/platform/src/components/layout/nav-config.ts` (add canAccessApps gate)
- `apps/platform/src/app/(workspace)/apps/page.tsx` (permission-filter visible apps)
- `apps/platform/messages/en.json` + `de.json`

---

## Task 1: Extend OpenFGA Model

**Files:**
- Modify: `infra/openfga/model.fga`

Add two new types after `tenant_config`:

```
type app_management
  relations
    define tenant: [tenant]
    define viewer: [user, role#assignee] or editor or admin from tenant
    define editor: [user, role#assignee] or viewer or admin from tenant
    define admin: [user, role#assignee] or editor or admin from tenant

type app
  relations
    define management: [app_management]
    define viewer: [user, role#assignee] or viewer from management
    define editor: [user, role#assignee] or editor from management
    define admin: [user, role#assignee] or admin from management
```

This gives us:
- `app_management` = global "all apps" permissions (singleton per tenant, like user_management)
- `app` = per-app overrides inheriting from app_management via the `management` relation
- Tenant admins automatically get admin on all apps (via `admin from tenant`)

The model.fga sync task in build.gradle copies from `infra/openfga/model.fga` to `src/main/resources/openfga/model.fga`.

IMPORTANT: The implementer must also update `infra/openfga/model.json` if it exists (some setups use JSON). Check if `infra/openfga/model.json` exists and keep it in sync.

Verify: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew processResources` to sync the model
Then: `./gradlew test` to ensure nothing breaks

Commit: `"feat: add app_management and app types to OpenFGA model"`

---

## Task 2: Create OpenFGA Tuples on App Create + @RequiresPermission

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppService.java`
- Modify all 4 controllers to add @RequiresPermission

### 2a. AppService — Create OpenFGA relation on app creation

When a new app is created, link it to the tenant's app_management:
```java
// After saving the app entity:
// Create tuple: app:<appId>#management -> app_management:<tenantId>
permissionService.grant(new GrantPermissionRequest(
    "app", appId.toString(), "management",
    "app_management", tenantId.toString(), ""
));
```

Add PermissionService as dependency to AppService.

Also on delete: clean up the tuple.

### 2b. @RequiresPermission on AppController

```java
@RequiresPermission(action = "viewer", resource = "app_management")
public ResponseEntity<AppListResponse> listApps(...) // global check for listing

@RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
public ResponseEntity<AppResponse> getApp(UUID appId) // per-app check

@RequiresPermission(action = "editor", resource = "app", resourceId = "#appId")
public ResponseEntity<AppResponse> updateApp(UUID appId, ...) // per-app check

@RequiresPermission(action = "admin", resource = "app", resourceId = "#appId")
public ResponseEntity<Void> deleteApp(UUID appId) // per-app check

// createApp: check app_management editor (global, since app doesn't exist yet)
@RequiresPermission(action = "editor", resource = "app_management")
public ResponseEntity<AppResponse> createApp(...)
```

### 2c. @RequiresPermission on AppVersionController

```java
@RequiresPermission(action = "admin", resource = "app", resourceId = "#appId")
publishApp, rollbackAppVersion

@RequiresPermission(action = "viewer", resource = "app", resourceId = "#appId")
listAppVersions, getAppVersion, suggestNextVersion
```

### 2d. @RequiresPermission on AppRevisionController + AppDependencyController

Same pattern: viewer for reads, editor for mutations, admin for destructive operations.

IMPORTANT: Check how the PermissionCheckAspect resolves `#appId` — it uses SpEL to extract from method parameters. The parameter name must match.

Verify: `./gradlew test`
Commit: `"feat: add @RequiresPermission on all App controllers, create OpenFGA tuples on app create"`

---

## Task 3: Extend RoleService for App Permissions

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java`

### 3a. Extend RESOURCE_ACTIONS map

Currently has `user_management` and `role_management`. Add:
```java
"app_management", List.of("viewer", "editor", "admin")
```

This enables the existing setPermissions/getPermissions flow to handle app permissions. When a role is assigned "editor" on "app_management", that grants editor on ALL apps.

### 3b. Per-App permissions

Per-app overrides need a different mechanism since they're on `app:<appId>` not `app_management:<tenantId>`.

Add new methods to RoleService:
```java
public void setAppPermissions(UUID roleId, UUID appId, List<String> actions)
public Map<String, List<String>> getAppPermissions(UUID roleId)
```

These write/read tuples on the `app` resource type instead of `app_management`.

### 3c. OpenAPI — extend role permission endpoints

Add app permission fields to the existing SetRolePermissionsRequest and EffectivePermissionsResponse. Or add new dedicated endpoints:
```
PUT /api/v1/roles/{roleId}/app-permissions/{appId}
GET /api/v1/roles/{roleId}/app-permissions
```

IMPORTANT: Read the existing OpenAPI spec for roles (`openapi/identity-api.yaml`) to understand the current permission endpoint schema before extending.

Tests: Extend existing RoleServiceTest.
Commit: `"feat: extend RoleService with app_management permissions"`

---

## Task 4: Extend AuthService.getEffectivePermissions

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java`

Extend `getEffectivePermissions()` to include `app_management` in the batch check:

Currently checks: user_management, role_management, tenant_config.
Add: app_management (with viewer, editor, admin actions).

The response should include `app_management` permissions so the frontend knows what the user can do globally with apps.

For per-app permissions: the frontend will check lazily when accessing a specific app. The navigation just needs the global check.

Tests: Extend existing AuthServiceTest.
Commit: `"feat: extend effective-permissions with app_management"`

---

## Task 5: Frontend — Permission-aware Navigation + App List

**Files:**
- Modify: `apps/platform/src/hooks/use-permissions.ts`
- Modify: `apps/platform/src/components/layout/nav-config.ts`
- Modify: `apps/platform/src/app/(workspace)/apps/page.tsx`

### 5a. usePermissions — extend for app

The `can("app_management", "viewer")` call should work out of the box if AuthService returns app_management permissions. Verify.

### 5b. nav-config — add canAccessApps gate

Add to `WorkspaceNavOptions`:
```typescript
canAccessApps: boolean
```

Gate the Apps nav item:
```typescript
if (options.canAccessApps) {
  items.push({ label: t("sidebar.apps"), href: "/apps", icon: Boxes, testId: "nav-apps" })
}
```

Update the sidebar component that calls `getWorkspaceNavItems` to pass `canAccessApps: can("app_management", "viewer")`.

### 5c. Update app-sidebar to pass canAccessApps

Read the sidebar component (likely `app-sidebar.tsx`) and add the canAccessApps option.

Verify: `pnpm build && pnpm vitest run`
Commit: `"feat: permission-aware navigation for Apps"`

---

## Task 6: Frontend — Permission Matrix UI (Apps & Content Tab)

**Files:**
- Create: `apps/platform/src/features/roles/components/app-permissions-editor.tsx`
- Modify: Role detail page to add tabs (General + Apps & Content)
- i18n keys

### 6a. i18n keys

EN:
```json
"permissions": {
  "tabs": {
    "general": "General",
    "appsAndContent": "Apps & Content"
  },
  "appManagement": {
    "title": "App Management",
    "global": "Global — All Apps",
    "globalDescription": "Applies to all existing and future apps",
    "perApp": "Per-App Overrides",
    "perAppDescription": "Extend global permissions for specific apps",
    "addOverride": "Add Override",
    "removeOverride": "Remove",
    "fromGlobal": "from global",
    "viewer": "Viewer",
    "editor": "Editor",
    "admin": "Admin"
  }
}
```

DE: German translations.

### 6b. AppPermissionsEditor component

New component that renders:
1. **Global section** — checkboxes for viewer/editor/admin on app_management (same as existing pattern)
2. **Per-App Overrides section** — list of apps with individual permission levels
   - "Add Override" button → dialog with app selector
   - Each override shows: app name + status badge + checkboxes (global permissions disabled/implied)
   - "Remove" button per override

Follow the same state management pattern as RolePermissionsEditor (delta tracking, save/cancel).

### 6c. Modify Role Detail Page

The existing permissions tab should be split into two sub-tabs:
- **General** — existing RolePermissionsEditor (user_management, role_management, tenant_config)
- **Apps & Content** — new AppPermissionsEditor

Use shadcn Tabs within the Permissions tab content area.

Verify: `pnpm build && pnpm vitest run`
Commit: `"feat: add Permission Matrix with General + Apps & Content tabs"`

---

## Task 7: Integration Tests

**Files:**
- Create or extend integration tests

Tests:
1. App endpoints return 403 when user has no app permission
2. App endpoints work when user has viewer/editor/admin permission
3. Per-app override grants higher access on specific app
4. Navigation hides Apps when no permission

NOTE: Integration tests with OpenFGA require the test to set up proper tuples. Check how existing permission tests handle this (likely via TestSecurityConfiguration which bypasses for platform admins).

Commit: `"test: add permission integration tests for App endpoints"`

---

## Task 8: Final Verification

- Full backend + frontend tests
- Build
- Update PROGRESS.md
- Commit

---

## Known Trade-offs

- **Per-app permissions UI complexity** — the Add Override dialog needs to fetch all apps and filter. This is fine for <100 apps.
- **OpenFGA model deployment** — model changes require redeploying the OpenFGA store. In dev, this happens on app startup.
- **No lazy per-app permission check in navigation** — navigation uses global app_management check. Individual app access is checked on the detail page.
- **Existing tests may need TestSecurityConfig updates** — platform admin bypass still works, but tenant-scoped tests need proper permission tuples.
