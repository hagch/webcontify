/**
 * E2E Tests: Content Definition Cascade / Override Permissions
 *
 * Verifies role-scoped cascade vs override semantics on content definitions.
 * The backend stores this via an OpenFGA `restricted` marker on the app; the
 * frontend exposes a per-app toggle "Include all definitions automatically".
 *
 * Scenarios (each a separate test):
 *
 * 1. Cascade role (toggle ON)
 *    - Admin creates role "App A Editor" with editor:app on App A,
 *      toggle "include all definitions automatically" stays ON.
 *    - Assigns the role to a test user.
 *    - User logs in.
 *      - Sidebar shows "Apps".
 *      - /apps lists App A.
 *      - Navigating into App A shows every content definition, each
 *        editable (rename button visible, add field button visible).
 *      - "+" button (create definition) is visible.
 *      - Undo / redo buttons visible.
 *      - Spotlight (Ctrl/Cmd+K) exposes "Create new definition".
 *
 * 2. Override role (toggle OFF, single definition granted)
 *    - Admin edits the role: turns toggle OFF for App A, unchecks all
 *      definitions except "Article:viewer".
 *    - Saves role.
 *    - User reloads.
 *      - /apps still lists App A (app-level viewer via editor tuple).
 *      - Navigating into App A shows only the Article node, read-only.
 *      - Other definitions (Author, Category) are NOT present.
 *      - For each REFERENCE field on Article pointing to Author, a
 *        restricted-definition-node placeholder is rendered with the
 *        "Restricted access" label — NO name leak.
 *      - "+" button is hidden.
 *      - Spotlight does NOT show "Create new definition".
 *      - Drag & drop disabled (nodesDraggable=false).
 *
 * 3. Restricted-to-cascade round-trip
 *    - Admin toggles "include all definitions automatically" back ON.
 *    - Saves role.
 *    - User reloads → sees all definitions again, editor rights restored.
 *
 * 4. Dependency tab ghost app
 *    - Admin on "App B" adds App A as a dependency.
 *    - Revokes the test user's access to App A entirely.
 *    - User navigates to App B → Dependencies tab.
 *      - The App A node renders as ghost (lock icon, generic label).
 *      - App A name is NOT visible anywhere in the DOM.
 *
 * Setup uses the same API helpers as navigation-permissions.spec.ts:
 *   - ApiClient for REST (create tenant, users, roles, app, definitions).
 *   - Keycloak helpers to set passwords / clear required actions.
 *   - loginAsTenantUser for browser session.
 *
 * Implementation note: the concrete seed + assertion code is TODO — this
 * file captures the scenarios to pin down reviewer expectations before
 * flaky test plumbing starts. Pattern the implementation after
 * navigation-permissions.spec.ts.
 */

import { test } from "@playwright/test";

test.describe("Content Definition Cascade/Override @permissions", () => {
  test.skip(
    "cascade role: user sees all definitions of the granted app",
    async ({ page: _page }) => {
      // 1. API: createTenant → setAdminPassword → createApp "App A" →
      //    createDefinitions [Article, Author, Category] → createRole
      //    "App A Editor" with app-permissions {appId, actions:["editor"],
      //    restricted:false, definitionPermissions:[]} → inviteUser +
      //    assignRole.
      // 2. loginAsTenantUser.
      // 3. expect(page.getByTestId("sidebar-apps")).toBeVisible().
      // 4. page.goto apps/app-a/content.
      // 5. expect(page.getByTestId("graph-node-article")).toBeVisible().
      // 6. expect(page.getByText("+")).toBeVisible() (create button).
    }
  );

  test.skip(
    "override role: user sees only the explicitly-granted definition and a ghost for siblings",
    async ({ page: _page }) => {
      // Update role: restricted=true, definitionPermissions=[{
      //   definitionId: articleId, actions: ["viewer"] }].
      // Reload page.
      // expect(page.getByTestId(`graph-node-${articleId}`)).toBeVisible().
      // expect(page.getByTestId(`graph-node-${authorId}`)).not.toBeVisible().
      // expect(page.getByTestId("restricted-definition-node")).toBeVisible().
      // expect(page.getByText("Author")).not.toBeVisible(); // no name leak.
      // expect(page.getByTestId("confirm-delete-definition")).not.toBeVisible();
      // expect(page.getByText("+")).not.toBeVisible();
    }
  );

  test.skip(
    "role toggled from restricted back to cascade: user regains full access",
    async ({ page: _page }) => {
      // Update role with restricted=false again.
      // Reload, expect all definitions visible and editable.
    }
  );

  test.skip(
    "dependency tab: inaccessible dependency renders as ghost app node",
    async ({ page: _page }) => {
      // Revoke all app:A tuples for the user. Navigate to app-b/dependencies.
      // expect(page.getByTestId("restricted-app-node")).toBeVisible().
      // expect(page.getByText("App A")).not.toBeVisible();
    }
  );
});
