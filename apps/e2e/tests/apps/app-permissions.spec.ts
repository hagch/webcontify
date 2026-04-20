/**
 * E2E Tests: App Permissions
 *
 * Tests that users without app permissions cannot access app features:
 * 1. Create a tenant and a custom role WITHOUT app permissions
 * 2. Invite a user with that restricted role
 * 3. Log in as the restricted user
 * 4. Verify the Apps navigation item is hidden or inaccessible
 * 5. Verify direct navigation to /apps returns a forbidden state
 *
 * Setup:
 * - Creates a tenant via Platform Admin API
 * - Creates a custom role without app_management permissions
 * - Invites a user with that role
 */

import { test, expect } from "@playwright/test";
import { ApiClient } from "../../helpers/api";
import {
  getKeycloakAdminToken,
  findKeycloakUserByEmail,
  setUserPassword,
  clearRequiredActions,
} from "../../helpers/keycloak-admin";
import { getTenantUserToken, loginAsTenantUser } from "../../helpers/auth";

const ADMIN_PASSWORD = "E2eAdmin1!";
const RESTRICTED_USER_PASSWORD = "E2eUser1!";

test.describe.serial("App Permissions", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let restrictedUserEmail: string;
  let tenantBaseUrl: string;
  let tenantToken: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-app-perms-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(
      `E2E App Perms ${ts}`,
      {
        adminEmail,
        adminFirstName: "Perms",
        adminLastName: "Admin",
      }
    );
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    await sharedApiClient.waitForTenantProvisioning(tenantId);

    // Set admin password
    const kcToken = await getKeycloakAdminToken();
    const tenantRealm = `tenant-${tenantSlug}`;
    const adminKcUser = await findKeycloakUserByEmail(
      kcToken,
      tenantRealm,
      adminEmail
    );
    if (!adminKcUser) {
      throw new Error(
        `Admin user not found in Keycloak realm ${tenantRealm}`
      );
    }
    await setUserPassword(kcToken, tenantRealm, adminKcUser.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, adminKcUser.id);

    // Get admin tenant token
    tenantToken = await getTenantUserToken(
      tenantRealm,
      adminEmail,
      ADMIN_PASSWORD
    );

    // Create a custom role WITHOUT app_management permissions
    // Only grant minimal permissions (e.g. dashboard access)
    const role = await sharedApiClient.createRole(tenantToken, tenantSlug, {
      name: "No Apps Role",
      description: "Role without app management permissions",
    });

    // Set permissions on the role — intentionally no app_management
    await sharedApiClient.setRolePermissions(tenantToken, tenantSlug, role.id, {
      permissions: [
        {
          resourceType: "user_management",
          actions: ["viewer"],
        },
      ],
    });

    // Invite a restricted user with the custom role
    restrictedUserEmail = `e2e-app-perms-user-${ts}@e2e.test`;
    await sharedApiClient.inviteUser(tenantToken, tenantSlug, {
      email: restrictedUserEmail,
      firstName: "Restricted",
      lastName: "User",
      roleIds: [role.id],
    });

    // Set the restricted user's password via Keycloak Admin
    const restrictedKcUser = await findKeycloakUserByEmail(
      kcToken,
      tenantRealm,
      restrictedUserEmail
    );
    if (!restrictedKcUser) {
      throw new Error(
        `Restricted user not found in Keycloak realm ${tenantRealm}`
      );
    }
    await setUserPassword(
      kcToken,
      tenantRealm,
      restrictedKcUser.id,
      RESTRICTED_USER_PASSWORD
    );
    await clearRequiredActions(kcToken, tenantRealm, restrictedKcUser.id);
    sharedApiClient.registerForCleanup({
      type: "keycloak-user",
      id: restrictedKcUser.id,
      realm: tenantRealm,
    });
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test("admin user should see Apps in the navigation", async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);

    await page.goto(`${tenantBaseUrl}/dashboard`);
    await page.waitForLoadState("networkidle");

    // The sidebar should contain an Apps navigation item
    const sidebar = page.getByTestId("sidebar");
    await expect(sidebar).toBeVisible({ timeout: 10000 });

    const appsNavItem = sidebar.getByRole("link", { name: /apps/i });
    await expect(appsNavItem).toBeVisible({ timeout: 10000 });
  });

  test("restricted user should NOT see Apps in the navigation", async ({
    page,
  }) => {
    await loginAsTenantUser(
      page,
      tenantSlug,
      restrictedUserEmail,
      RESTRICTED_USER_PASSWORD
    );

    await page.goto(`${tenantBaseUrl}/dashboard`);
    await page.waitForLoadState("networkidle");

    // The sidebar should NOT contain an Apps navigation item
    const sidebar = page.getByTestId("sidebar");
    await expect(sidebar).toBeVisible({ timeout: 10000 });

    const appsNavItem = sidebar.getByRole("link", { name: /apps/i });
    await expect(appsNavItem).not.toBeVisible({ timeout: 5000 });
  });

  test("restricted user should be denied access when navigating directly to /apps", async ({
    page,
  }) => {
    await loginAsTenantUser(
      page,
      tenantSlug,
      restrictedUserEmail,
      RESTRICTED_USER_PASSWORD
    );

    // Navigate directly to the apps page
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Should see a forbidden/unauthorized message or be redirected
    const forbidden = page.getByTestId("forbidden-message");
    const redirectedToDashboard = page.url().includes("/dashboard");

    const isForbiddenVisible = await forbidden
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    // Either we see a forbidden message or we got redirected away from /apps
    expect(isForbiddenVisible || redirectedToDashboard).toBeTruthy();
  });
});
