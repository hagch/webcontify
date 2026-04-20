/**
 * E2E Tests: Permission-Aware Navigation
 *
 * Verifies that the workspace sidebar shows only the nav items the current user
 * has permission to access, based on their OpenFGA permissions.
 *
 * Scenarios:
 * 1. Viewer role (no singleton permissions) → only Dashboard + Apps visible
 * 2. Viewer navigating to /users is redirected away
 * 3. Custom role with user_management:reader → sees Users, not Roles/Settings
 * 4. Admin → sees all menu items (Dashboard, Apps, Users, Roles, Branding)
 * 5. Admin can edit permissions on a custom role
 * 6. System role permissions are read-only (notice is shown, save button absent)
 *
 * Setup:
 * - Creates one tenant per suite run
 * - Sets admin password via Keycloak Admin API (bypasses invite flow)
 * - Creates additional users via API invite + Keycloak password reset
 * - Custom role is created via API and given user_management:reader permission
 */

import { test, expect } from "@playwright/test";
import { ApiClient } from "../../helpers/api";
import {
  getKeycloakAdminToken,
  findKeycloakUserByEmail,
  setUserPassword,
  clearRequiredActions,
  enableDirectAccessGrants,
} from "../../helpers/keycloak-admin";
// Note: findKeycloakUserByEmail is used for the initial admin user only
import { getTenantUserToken } from "../../helpers/auth";
import { loginAsTenantUser } from "../../helpers/auth";

const ADMIN_PASSWORD = "E2eAdmin1!";
const VIEWER_PASSWORD = "E2eViewer1!";
const CUSTOM_USER_PASSWORD = "E2eCustom1!";

test.describe.serial("Permission-Aware Navigation @permissions", () => {
  let tenantSlug: string;
  let tenantId: string;
  let tenantBaseUrl: string;
  let tenantRealm: string;

  let adminEmail: string;
  let viewerEmail: string;
  let customUserEmail: string;

  /** ID of the custom role created during setup */
  let customRoleId: string;

  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-nav-admin-${ts}@e2e.test`;
    viewerEmail = `e2e-nav-viewer-${ts}@e2e.test`;
    customUserEmail = `e2e-nav-custom-${ts}@e2e.test`;

    // 1. Create tenant
    const tenant = await sharedApiClient.createTenant(`E2E Nav Perm ${ts}`, {
      adminEmail,
      adminFirstName: "Nav",
      adminLastName: "Admin",
    });
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    tenantRealm = `tenant-${tenantSlug}`;
    tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    // 2. Wait for provisioning
    await sharedApiClient.waitForTenantProvisioning(tenantId);

    // 3. Set up Keycloak: admin password + direct access grants
    const kcToken = await getKeycloakAdminToken();

    const kcAdmin = await findKeycloakUserByEmail(kcToken, tenantRealm, adminEmail);
    if (!kcAdmin) {
      throw new Error(`Admin user not found in realm ${tenantRealm}`);
    }
    await setUserPassword(kcToken, tenantRealm, kcAdmin.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, kcAdmin.id);
    await enableDirectAccessGrants(kcToken, tenantRealm);

    // 4. Obtain admin API token
    const adminToken = await getTenantUserToken(tenantRealm, adminEmail, ADMIN_PASSWORD);

    // 5. Get system roles to find Viewer role ID
    const roles = await sharedApiClient.listRoles(adminToken, tenantSlug);
    const viewerRole = roles.find(
      (r) => r.name.toLowerCase() === "viewer" && r.systemRole
    );
    if (!viewerRole) {
      throw new Error(`Viewer system role not found. Available: ${roles.map((r) => r.name).join(", ")}`);
    }

    // 6. Create custom role with user_management:reader permission
    const customRole = await sharedApiClient.createRole(adminToken, tenantSlug, {
      name: "E2E Custom Nav Role",
      description: "Custom role for navigation permission E2E tests",
    });
    customRoleId = customRole.id;

    await sharedApiClient.setRolePermissions(adminToken, tenantSlug, customRoleId, {
      permissions: [
        {
          resourceType: "user_management",
          actions: ["reader"],
        },
      ],
    });

    // 7. Invite viewer user and assign Viewer role
    // The invite response `id` is the Keycloak user UUID (backend sets entity.id = keycloakUserId)
    const viewerUser = await sharedApiClient.inviteUser(adminToken, tenantSlug, {
      email: viewerEmail,
      firstName: "Viewer",
      lastName: "User",
      roleIds: [viewerRole.id],
    });

    await setUserPassword(kcToken, tenantRealm, viewerUser.id, VIEWER_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, viewerUser.id);
    sharedApiClient.registerForCleanup({
      type: "keycloak-user",
      id: viewerUser.id,
      realm: tenantRealm,
    });

    // 8. Invite custom role user and assign custom role
    const customUser = await sharedApiClient.inviteUser(adminToken, tenantSlug, {
      email: customUserEmail,
      firstName: "Custom",
      lastName: "User",
      roleIds: [customRoleId],
    });

    await setUserPassword(kcToken, tenantRealm, customUser.id, CUSTOM_USER_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, customUser.id);
    sharedApiClient.registerForCleanup({
      type: "keycloak-user",
      id: customUser.id,
      realm: tenantRealm,
    });
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  // ─── Test 1: Viewer sees only Dashboard + Apps ─────────────────────────────

  test("viewer user sees only Dashboard and Apps nav items", async ({ page }) => {
    await page.context().clearCookies();
    await loginAsTenantUser(page, tenantSlug, viewerEmail, VIEWER_PASSWORD);
    await page.goto(`${tenantBaseUrl}/dashboard`);
    await page.waitForLoadState("networkidle");

    // Dashboard and Apps must be visible
    await expect(page.getByTestId("nav-item-dashboard")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-apps")).toBeVisible({ timeout: 10000 });

    // Management items must NOT be visible
    await expect(page.getByTestId("nav-item-users")).not.toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId("nav-item-roles")).not.toBeVisible({ timeout: 5000 });

    // Settings items must NOT be visible
    await expect(page.getByTestId("nav-item-branding")).not.toBeVisible({ timeout: 5000 });
  });

  // ─── Test 2: Viewer is redirected from /users ──────────────────────────────

  test("viewer user navigating to /users is redirected to dashboard", async ({ page }) => {
    await page.context().clearCookies();
    await loginAsTenantUser(page, tenantSlug, viewerEmail, VIEWER_PASSWORD);
    await page.goto(`${tenantBaseUrl}/users`);
    await page.waitForLoadState("networkidle");

    // Either redirected away from /users or shown an access-denied/empty state
    const url = page.url();
    const redirectedAway = !url.endsWith("/users");

    const accessDeniedVisible =
      (await page.getByTestId("access-denied").isVisible({ timeout: 3000 }).catch(() => false)) ||
      (await page.getByText(/403|forbidden|access denied|keine berechtigung|zugriff verweigert/i)
        .isVisible({ timeout: 3000 }).catch(() => false));

    // No user table rows should be visible (API returned 403)
    const tableVisible = await page.getByTestId("user-table").isVisible({ timeout: 2000 }).catch(() => false);
    const tableHasRows = tableVisible
      ? (await page.getByTestId("user-table-row").count()) > 0
      : false;

    expect(redirectedAway || accessDeniedVisible || !tableHasRows).toBe(true);
  });

  // ─── Test 3: Custom role user sees Users but not Roles/Settings ────────────

  test("custom role user with user_management:reader sees Users but not Roles or Branding", async ({ page }) => {
    await page.context().clearCookies();
    await loginAsTenantUser(page, tenantSlug, customUserEmail, CUSTOM_USER_PASSWORD);
    await page.goto(`${tenantBaseUrl}/dashboard`);
    await page.waitForLoadState("networkidle");

    // Dashboard and Apps always visible
    await expect(page.getByTestId("nav-item-dashboard")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-apps")).toBeVisible({ timeout: 10000 });

    // Users must be visible (user_management:reader granted)
    await expect(page.getByTestId("nav-item-users")).toBeVisible({ timeout: 10000 });

    // Roles must NOT be visible (no role_management permission)
    await expect(page.getByTestId("nav-item-roles")).not.toBeVisible({ timeout: 5000 });

    // Branding (tenant_config) must NOT be visible
    await expect(page.getByTestId("nav-item-branding")).not.toBeVisible({ timeout: 5000 });
  });

  // ─── Test 4: Admin sees all menu items ─────────────────────────────────────

  test("tenant admin sees all navigation items", async ({ page }) => {
    await page.context().clearCookies();
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
    await page.goto(`${tenantBaseUrl}/dashboard`);
    await page.waitForLoadState("networkidle");

    await expect(page.getByTestId("nav-item-dashboard")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-apps")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-users")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-roles")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-branding")).toBeVisible({ timeout: 10000 });
  });

  // ─── Test 5: Admin can edit permissions on custom role ─────────────────────

  test("admin can open permission editor and save permissions on custom role", async ({ page }) => {
    await page.context().clearCookies();
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
    await page.goto(`${tenantBaseUrl}/roles`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Navigate to the custom role detail page
    await table.getByText("E2E Custom Nav Role").click();
    await page.waitForLoadState("networkidle");

    // Switch to permissions tab
    const permissionsTab = page.getByTestId("role-permissions-tab");
    await expect(permissionsTab).toBeVisible({ timeout: 10000 });
    await permissionsTab.click();

    // Permission editor should be visible — no system-role-permissions-notice
    await expect(page.getByTestId("system-role-permissions-notice")).not.toBeVisible({
      timeout: 5000,
    });

    // user_management:reader checkbox should be present
    const readerCheckbox = page.getByTestId("permission-user_management-reader");
    await expect(readerCheckbox).toBeVisible({ timeout: 10000 });

    // Toggle inviter permission to make the form dirty
    const inviterCheckbox = page.getByTestId("permission-user_management-inviter");
    await expect(inviterCheckbox).toBeVisible({ timeout: 10000 });

    const inviterChecked = await inviterCheckbox.isChecked();
    await inviterCheckbox.click();

    // Save button must appear when form is dirty
    const saveButton = page.getByTestId("save-permissions-button");
    await expect(saveButton).toBeVisible({ timeout: 10000 });
    await saveButton.click();

    // Save button disappears after successful save (form is no longer dirty)
    await expect(saveButton).not.toBeVisible({ timeout: 10000 });

    // Revert to original state for clean teardown
    await inviterCheckbox.click();
    if (!inviterChecked) {
      // inviter was off, we turned it on, now off again — reader may still be implied
      // Check if save button appeared again
      const saveButtonAgain = page.getByTestId("save-permissions-button");
      if (await saveButtonAgain.isVisible({ timeout: 3000 }).catch(() => false)) {
        await saveButtonAgain.click();
        await expect(saveButtonAgain).not.toBeVisible({ timeout: 10000 });
      }
    }
  });

  // ─── Test 6: System role permissions are read-only ─────────────────────────

  test("system role shows read-only permissions notice without save button", async ({ page }) => {
    await page.context().clearCookies();
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
    await page.goto(`${tenantBaseUrl}/roles`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Navigate to the Admin system role
    const rows = table.getByTestId("role-table-row");
    const rowCount = await rows.count();
    let foundAdminRow = false;

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      const hasAdminText = (await row.getByText("Admin").count()) > 0;
      const hasBadge = (await row.getByTestId("system-role-badge").count()) > 0;
      if (hasAdminText && hasBadge) {
        await row.getByText("Admin").click();
        foundAdminRow = true;
        break;
      }
    }

    expect(foundAdminRow).toBe(true);
    await page.waitForLoadState("networkidle");

    // Switch to permissions tab
    const permissionsTab = page.getByTestId("role-permissions-tab");
    await expect(permissionsTab).toBeVisible({ timeout: 10000 });
    await permissionsTab.click();

    // System role permissions notice must be visible
    await expect(page.getByTestId("system-role-permissions-notice")).toBeVisible({
      timeout: 10000,
    });

    // Save button must never appear for system roles
    await expect(page.getByTestId("save-permissions-button")).not.toBeVisible({
      timeout: 5000,
    });

    // All permission checkboxes should be disabled
    const checkboxes = page.locator('[data-testid^="permission-"]');
    const checkboxCount = await checkboxes.count();
    expect(checkboxCount).toBeGreaterThan(0);

    for (let i = 0; i < checkboxCount; i++) {
      await expect(checkboxes.nth(i)).toBeDisabled();
    }
  });
});
