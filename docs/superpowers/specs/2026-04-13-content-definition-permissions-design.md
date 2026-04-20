# Content Definition Permissions — Design

**Date:** 2026-04-13
**Status:** Draft
**Scope:** Content Definitions and Apps only. Field-level permissions out of scope.

## Problem

Current permission handling for content definitions is inconsistent:

1. **List endpoints ignore per-app permissions.** `GET /apps` and `GET /apps/{slug}/content-definitions` check tenant-wide `app_management:viewer`, so users with only per-app access get 403. A partial fix in an earlier increment filters `GET /apps` via `listObjects("viewer", "app")` and enforces `viewer:app` on the definition list.
2. **Frontend collapses all actions into a single `canEdit` flag.** The graph UI cannot distinguish viewer/editor/creator/deleter/admin on a specific content definition.
3. **Role editor shows per-definition rows as read-only.** There is no way for an administrator to grant access to specific definitions only.
4. **No override semantics.** A role with `editor:app` always cascades to all content definitions of that app. There is no way to grant an app-level role for navigation but restrict definition access to a subset.

## Goal

Allow roles to express two modes per app:

- **Cascade mode (default):** The role's `viewer`/`editor`/`admin` on the app automatically applies to all content definitions of that app, including future ones.
- **Override mode:** The role still has the app-level role (for navigation and app-level actions), but content definition access is restricted to an explicitly selected subset. Future definitions are not included.

The UI exposes this as a toggle per app row: "Include all definitions automatically". Default ON when any app-level action is granted. Turning it OFF reveals editable checkboxes for each definition.

Semantics:

- **Within a single role:** Cascade vs. override is role-scoped and exclusive per app — a role cannot grant "app-wide + additionally specific" for the same app. Either all or the selected subset.
- **Across roles:** Roles remain additive (union). A user with Role A (`editor:app:X` cascade) and Role B (specific `viewer:content_definition:Y` in App X, override) has editor on everything in X via Role A. Role B does not subtract.

## Permission Model

### OpenFGA Schema Changes

```diff
 type app
   relations
     define management: [app_management]
+    define restricted: [user, role#assignee]
     define viewer: [user, role#assignee] or viewer from management
     define editor: [user, role#assignee] or editor from management
     define admin: [user, role#assignee] or admin from management

 type content_definition
   relations
     define parent: [app]
-    define viewer: [user, role#assignee] or viewer from parent
-    define editor: [user, role#assignee] or editor from parent
-    define admin: [user, role#assignee] or admin from parent
-    define creator: [user, role#assignee] or admin from parent
-    define deleter: [user, role#assignee] or editor from parent
+    define viewer: [user, role#assignee] or (viewer from parent but not restricted from parent)
+    define editor: [user, role#assignee] or (editor from parent but not restricted from parent)
+    define admin:  [user, role#assignee] or (admin  from parent but not restricted from parent)
+    define creator: [user, role#assignee] or (admin  from parent but not restricted from parent)
+    define deleter: [user, role#assignee] or (editor from parent but not restricted from parent)
```

`restricted` is a marker relation. If a `role#assignee → restricted → app:X` tuple exists, the cascade from that app to its content definitions is disabled for that role's assignees. Direct content_definition tuples still apply, so the role can still grant access to specific definitions.

### Truth and Storage

OpenFGA is the single source of truth. The toggle state is derived from the presence of the `restricted` tuple — no separate DB column or flag. Role editor reads OpenFGA tuples to render the current state. Save operation diffs the desired state against current OpenFGA tuples and issues writes/deletes.

## Backend Changes

### 1. OpenFGA model generation

- Edit `infra/openfga/model.fga` as shown above.
- `model.json` regenerates via the existing Gradle `transformOpenFgaModel` task at build time.

### 2. Role grant/revoke logic

In `RoleService` (or equivalent app-permission-save service), when saving role app permissions:

For each app in the payload:

- **Cascade mode requested** (toggle ON, no per-definition entries):
  - Ensure `role#assignee → editor/viewer/admin → app:X` tuples match requested actions.
  - Delete `role#assignee → restricted → app:X` tuple if present.
  - Delete all `role#assignee → * → content_definition:Y` tuples where `Y.parent = X`.
- **Override mode requested** (toggle OFF, per-definition actions supplied):
  - Ensure `role#assignee → editor/viewer/admin → app:X` tuples match requested actions.
  - Ensure `role#assignee → restricted → app:X` tuple exists.
  - Diff requested per-definition actions vs. existing tuples, write/delete accordingly.

The save operation is a single transaction at the application level — OpenFGA writes are batched where possible.

### 3. List endpoint filtering

Both already addressed in the prior increment but need to be consistent with the new model:

- `GET /apps` (`AppController.listApps` → `AppService.list`): fast-path via `check("viewer:app_management")`; fall back to `listObjects("viewer", "app")` + `findByIdIn`.
- `GET /apps/{slug}/content-definitions` (`ContentDefinitionController.listContentDefinitions` → `ContentDefinitionService.listByApp`): fast-path `check("viewer:app:{appId}")`; fall back to `listObjects("viewer", "content_definition")` filtered to definitions of this app.

The fallback for `listContentDefinitions` is new: currently it throws `AccessDenied` when `viewer:app` fails. After this change, a user without `viewer:app:X` but with specific `viewer:content_definition:Y` where `Y.parent=X` must see just `Y`. Implementation: if `check("viewer:app:{appId}")` is false, call `listObjects("viewer", "content_definition")`, filter to definitions whose `appId` matches, return filtered list. If the filtered list is empty, return an empty list (HTTP 200) — consistent with `listApps`. The 403 path is only taken when the app itself cannot be resolved from the slug for this user (out of scope here; handled by existing `getApp` permission).

### 4. Per-definition action endpoints

`getContentDefinition`, `updateContentDefinition`, `deleteContentDefinition`, `ContentFieldController` — all already use `@RequiresPermission` with per-`content_definition` checks. These work unchanged under the new model (OpenFGA resolves `but not restricted` automatically).

### 5. Create / layout / undo / redo

`createContentDefinition`, `batchUpdatePositions`, `undoContentChange`, `redoContentChange` currently require tenant-wide `editor:app_management`. Move these to `editor:app:{appId}` (resolved from `appSlug`). Users with per-app editor can create, reorder, undo.

`checkContentDefinitionNameAvailability` — move to `viewer:app:{appId}`.

### 6. Role permission preview endpoint

The existing preview endpoint that feeds `app-permissions-editor.tsx` returns `AppPermissionEntry[]` and nested content definitions as read-only. Extend the response to include:

- Per app: `isRestricted: boolean` — derived from presence of `restricted` tuple for this role on this app.
- Per app → per definition: `actions: string[]` — direct tuples on `content_definition` for this role.

This lets the UI reconstruct the toggle and checkbox state on load.

### 7. Save endpoint payload

`PUT /api/v1/tenant/roles/{roleId}/app-permissions` payload extends:

```json
{
  "appPermissions": [
    {
      "appId": "uuid",
      "actions": ["viewer", "editor"],
      "restricted": false,
      "definitionPermissions": []
    },
    {
      "appId": "uuid",
      "actions": ["viewer"],
      "restricted": true,
      "definitionPermissions": [
        { "definitionId": "uuid", "actions": ["viewer"] },
        { "definitionId": "uuid", "actions": ["editor"] }
      ]
    }
  ]
}
```

When `restricted: true`, `definitionPermissions` is authoritative. When `restricted: false`, `definitionPermissions` is ignored (all existing per-definition tuples for this role+app are deleted on save).

## Frontend Changes

### 1. Role editor (`app-permissions-editor.tsx`)

- Per-app row gets a toggle "Alle Definitions automatisch inkludieren" (translated key). Default ON for new apps.
- When toggle ON: nested definition rows render disabled with all actions visually checked (greyed). No edit.
- When toggle OFF: nested definition rows become editable — checkboxes per action per definition.
- Toggle state is initialized from `isRestricted` in the preview response (inverted).
- Save payload builder emits `restricted` flag and `definitionPermissions` accordingly.

### 2. Content definition graph UI

Replace the binary `canEdit` flag with granular per-definition flags derived from `effectiveActions` returned in `ContentDefinitionResponse`:

- `canView` = `effectiveActions.includes("viewer")` — render the node
- `canEdit` = `effectiveActions.includes("editor")` — allow rename, field add/edit, connect (edges)
- `canDelete` = `effectiveActions.includes("deleter")` — show delete button
- `canCreateInApp` = `effectiveActions` on the app itself (from `AppResponse`) includes `editor` — allow "+" button and spotlight "Create new definition" entry
- `canLayoutApp` = `effectiveActions` on the app itself includes `editor` — allow drag & drop positioning, undo/redo, batch position saves

The spotlight "create new definition" entry (currently the only create entry point globally, confirmed) gates on `canCreateInApp`. Any future command palette additions must follow the same gate.

#### Graph display scenarios

**Viewer-only on all visible definitions (e.g. app-viewer role, cascade):**
- All nodes render, all field cells render, no edit controls (no rename, no field add/edit, no connect handles).
- No drag & drop, no undo/redo, no "+" button, no delete.
- Optional subtle lock indicator in node header (i18n label).

**Override mode, specific definitions only (e.g. role grants `viewer:content_definition:Y` where Y belongs to App X, restricted):**
- Only Y's node is rendered. Other definitions of App X are not returned by `listContentDefinitions`.
- REFERENCE fields on Y pointing to siblings (e.g. `Author`) still resolve — but their target is not in the visible items list. We render a **ghost placeholder node** with a lock icon and generic label "Restricted access" (i18n key `content.graph.restrictedDefinition`).
- **Privacy rule:** ghost nodes do NOT expose the hidden definition's name, description, or field list. Only the edge topology is visible. Otherwise override mode would leak sibling names.
- Edges to ghost nodes render dashed with reduced opacity. Clicks on ghost nodes show a tooltip "You don't have access to this definition" — no drill-down.

**Mixed access (e.g. editor on Article, viewer on Author, override):**
- Both nodes render with their individual permission set.
- Article has full edit controls; Author is read-only.
- **Edge editing rule:** A REFERENCE field is owned by its source definition. Editing / deleting an edge (equivalent to modifying the REFERENCE field) requires `editor:content_definition:{sourceId}` only. The target definition has no veto — it does not own the field. Connect handles are thus driven by the source node's `canEdit`.

**Positioning rule:** Drag & drop moves a node's saved position. This is an app-level layout concern (`canLayoutApp`). A user with `editor` on a single definition but not on the app cannot move that node. Reason: positions are stored per-node but the saving endpoint is `PUT /apps/{slug}/content-definitions/positions` (bulk, app-scoped). Keeping it app-scoped avoids per-node permission checks on every drag.

### 3. Delete functionality wiring

Backend `DELETE /apps/{slug}/content-definitions/{id}` already exists and works (`ContentDefinitionController:88-91`, service + event publication). The frontend button template exists in `definition-node.tsx:165-176` but `onDeleteDefinition` is hardcoded to `undefined` in `content-definition-graph.tsx:142`. Wiring required:

- Regenerate Orval hooks to include `useDeleteContentDefinition`.
- Pass a real `onDeleteDefinition` callback when `canDelete` is true; otherwise `undefined` (button stays hidden).
- Confirm dialog before delete (shadcn `<AlertDialog>`), i18n keys for title/body/buttons.
- On success: invalidate the `listContentDefinitions` query and close the popover.

### 4. Dependency tab display

`app-dependency-graph.tsx` (feature: `features/apps/components/`, rendered in `/apps/[appId]/page.tsx` under Tab "dependencies") uses `useGetAppDependencyGraph(appId)` → `GET /api/v1/apps/{appId}/dependency-graph`. The response is `{ nodes: AppGraphNode[], edges: AppGraphEdge[] }`.

Permission rules for this view:

- **Accessible dependency** (user has `viewer:app:{depAppId}`): app node renders with name, description, drill-down link to that app's view.
- **Inaccessible dependency**: node renders as a **ghost app node** — lock icon, generic label "Restricted access", no name, no drill-down. Edge is dashed with reduced opacity.
- **Partially-accessible dependency** (user has override in that app, sees only some definitions): at the app-node level still shown as accessible, because the user does have `viewer:app:{depAppId}`. Restriction only kicks in when they navigate into that app.

Backend: `GET /api/v1/apps/{appId}/dependency-graph` must filter/annotate nodes with accessibility. Two options:

- **A (annotate):** Return all nodes, add `accessible: boolean` per node; frontend renders ghost when false. Preserves topology and shows the user "dependency exists but hidden".
- **B (omit):** Filter out inaccessible nodes entirely; edges pointing to removed nodes are also removed or redirected to a synthetic "hidden" node.

**Choose A.** Preserving topology matches the graph-view ghost rule and does not leak names (only existence, which is implied by the edge topology anyway — and topology alone without names is not sensitive).

Current endpoint must also filter: the caller needs at least `viewer:app:{appId}` on the **root app** to see any graph at all. This is already enforced by `GET /apps/{appId}` style annotations on the controller. Verify during implementation.

### 5. Extended list response shape

`ContentDefinitionListResponse` grows a `referencedDefinitions` array to feed ghost nodes for cross-definition REFERENCEs:

```json
{
  "items": [
    {
      "id": "def-article",
      "name": "Article",
      "effectiveActions": ["viewer", "editor"],
      "fields": [
        { "id": "f1", "type": "REFERENCE", "referencedDefinitionId": "def-author" }
      ]
    }
  ],
  "referencedDefinitions": [
    { "id": "def-author", "accessible": false }
  ]
}
```

For each REFERENCE target ID referenced by visible items but not present in `items`, backend adds a `referencedDefinitions` entry. The shape is **strictly two-variant** — no field may leak across:

- `accessible: false` → response contains **exactly these two fields**: `id`, `accessible`. No `name`, no `description`, no `slug`, no `appId`, no `appName`, no `status`, no `fields`, no timestamps. The ID is acceptable to return because it is already present in the source definition's REFERENCE field that the user can read.
- `accessible: true` → response contains `id`, `accessible`, plus the minimum display metadata the frontend needs: `name`, `appId`, `appName`. Nothing more (no fields, no description).

OpenAPI schema must model these as two distinct variants (discriminated union on `accessible`) so the contract prevents accidental leaks. Backend mapper must not populate any other fields when `accessible: false`; a unit test asserts the serialized JSON for inaccessible entries contains exactly two keys.

The same rule applies to the dependency graph endpoint (`/apps/{appId}/dependency-graph`): inaccessible app nodes carry only `id` and `accessible: false` — no name, no slug, no status.

### 6. Permission fetch for app-level flags

Frontend uses `AppResponse.effectiveActions` (already returned) to compute `canCreateInApp` and `canLayoutApp`. The existing `useGetApp(appId)` call in the app detail page provides this; the graph component receives it via prop rather than making its own request.

## Out of Scope

- **Field-level permissions.** No `content_field` type, no checkboxes per field. Follow-up spec.
- **Other resource domains.** `user_management`, `role_management`, `tenant_config` unchanged.
- **Existing role migration.** Database is wiped.

## Risks and Open Questions

- **Override consistency across multiple apps in one role save.** The save handler must process each app's toggle state independently; a mistake could leak restriction markers across apps. Tests must cover multi-app role saves.
- **`but not` performance in OpenFGA.** Difference operations are well-supported in OpenFGA 1.1 but add one extra resolution step per check. Acceptable for our scale; monitor if content definitions per tenant exceed low thousands.
- **UI ambiguity when toggle is ON but some definitions are unchecked.** Prevented by the UX: checkbox grid is disabled when toggle is ON. Save payload does not send definition entries in cascade mode.
- **Granular frontend actions require backend to emit `effectiveActions` everywhere.** Already done for `ContentDefinitionResponse` and `AppResponse`. No extra work.
- **Ghost node info leak.** Ghost nodes expose edge topology only (not names) to prevent leaking the existence-with-name of restricted definitions. Topology leak (i.e. "a reference exists to something hidden") is accepted as unavoidable — the referenced ID is part of the visible source definition's schema and cannot be hidden without breaking the graph.
- **Delete confirmation.** Delete is destructive and backend uses soft-delete. Confirm dialog is required in UI even though recovery is technically possible; we do not currently surface soft-deleted items for restore.

## Testing

- Backend: unit tests for `RoleService.setRoleAppPermissions` covering cascade→override, override→cascade, cross-app isolation. Integration tests for `listApps` and `listContentDefinitions` covering tenant-wide reader, per-app viewer, per-definition-only viewer (with app restricted), no access.
- Frontend: Vitest for `app-permissions-editor` toggle behavior and payload shape. Storybook stories for cascade and override states.
- E2E (Playwright): role with per-definition override sees only the selected definition in the graph.
