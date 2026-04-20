/**
 * E2E Tests: Invite User with Role Clone
 *
 * Tests the full role clone flow:
 * 1. Create a custom role "Redakteur" on the tenant workspace
 * 2. Invite User A and assign the custom role to them
 * 3. Invite User B with roles copied from User A
 *    — the role preview badges should appear in the invite dialog
 *
 * Setup:
 * - Creates a tenant via the Platform Admin API
 * - Waits for async provisioning (Keycloak realm creation)
 * - Uses Keycloak Admin API to set the initial admin password directly
 * - Logs in as the tenant admin on the tenant subdomain before each test
 */

import { test, expect } from "@playwright/test";
import { ApiClient } from "../../helpers/api";
import {
  getKeycloakAdminToken,
  findKeycloakUserByEmail,
  setUserPassword,
  clearRequiredActions,
} from "../../helpers/keycloak-admin";
import { loginAsTenantUser } from "../../helpers/auth";

const ADMIN_PASSWORD = "E2eAdmin1!";
const ROLE_NAME = "Redakteur";

test.describe.serial("Invite with Role Clone", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  // Captured after role creation, used in subsequent tests
  let createdRoleId: string;
  // Captured after User A invite, used in role clone test
  let userAEmail: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    // 1. Create tenant
    const ts = Date.now();
    adminEmail = `e2e-roleclone-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(`E2E RoleClone ${ts}`, {
      adminEmail,
      adminFirstName: "Clone",
      adminLastName: "Admin",
    });
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    // 2. Wait for provisioning
    await sharedApiClient.waitForTenantProvisioning(tenantId);

    // 3. Set password for the initial admin
    const kcToken = await getKeycloakAdminToken();
    const tenantRealm = `tenant-${tenantSlug}`;
    const kcUser = await findKeycloakUserByEmail(kcToken, tenantRealm, adminEmail);
    if (!kcUser) {
      throw new Error(
        `Initial admin user not found in Keycloak realm ${tenantRealm} for email ${adminEmail}`
      );
    }
    await setUserPassword(kcToken, tenantRealm, kcUser.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, kcUser.id);
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should create a custom role", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/roles`);
    await page.waitForLoadState("networkidle");

    // Open create role dialog
    await page.getByRole("button", { name: "Rolle erstellen" }).click();

    const dialog = page.getByTestId("create-role-dialog");
    await expect(dialog).toBeVisible({ timeout: 10000 });

    // Fill role name
    await dialog.getByTestId("role-name-input").fill(ROLE_NAME);

    // Submit
    await dialog.getByTestId("create-role-submit").click();

    // Dialog should close
    await expect(dialog).not.toBeVisible({ timeout: 10000 });

    // Role should appear in the table
    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    const roleLink = table.getByRole("link", { name: ROLE_NAME });
    await expect(roleLink).toBeVisible({ timeout: 10000 });

    // Capture the role ID from the href for later tests
    const href = await roleLink.getAttribute("href");
    expect(href).toBeTruthy();
    // href is like /roles/{roleId}
    const match = href!.match(/\/roles\/([^/]+)/);
    expect(match).not.toBeNull();
    createdRoleId = match![1];
  });

  test("should invite User A and assign the custom role", async ({ page }) => {
    const ts = Date.now();
    userAEmail = `e2e-user-a-${ts}@e2e.test`;

    // Invite User A
    await page.goto(`${tenantBaseUrl}/users`);
    await page.waitForLoadState("networkidle");

    await page.getByRole("button", { name: "Einladen" }).click();

    const inviteDialog = page.getByTestId("invite-user-dialog");
    await expect(inviteDialog).toBeVisible({ timeout: 10000 });

    await inviteDialog.getByTestId("user-email-input").fill(userAEmail);
    await inviteDialog.getByTestId("user-firstname-input").fill("User");
    await inviteDialog.getByTestId("user-lastname-input").fill("A");
    await inviteDialog.getByTestId("invite-user-submit").click();

    await expect(inviteDialog).not.toBeVisible({ timeout: 10000 });

    // User A should appear in the table
    const userTable = page.getByTestId("user-table");
    await expect(userTable).toBeVisible({ timeout: 10000 });
    await expect(userTable.getByText(userAEmail)).toBeVisible({ timeout: 10000 });

    // Navigate to role detail and add User A as an assignee
    await page.goto(`${tenantBaseUrl}/roles/${createdRoleId}`);
    await page.waitForLoadState("networkidle");

    // Click "Benutzer hinzufuegen"
    await page.getByTestId("add-assignee-button").click();

    const addDialog = page.getByTestId("add-assignees-dialog");
    await expect(addDialog).toBeVisible({ timeout: 10000 });

    // Select User A via their checkbox (aria-label includes name)
    const userACheckbox = addDialog.getByRole("checkbox", {
      name: /User A/,
    });
    await expect(userACheckbox).toBeVisible({ timeout: 10000 });
    await userACheckbox.check();

    // Confirm add (button text shows count)
    await addDialog.getByRole("button", { name: /1 Benutzer hinzufuegen/ }).click();

    await expect(addDialog).not.toBeVisible({ timeout: 10000 });

    // User A should appear in the assignees table
    const assigneesTable = page.getByTestId("role-assignees-table");
    await expect(assigneesTable).toBeVisible({ timeout: 10000 });
    await expect(assigneesTable.getByText(userAEmail)).toBeVisible({ timeout: 10000 });
  });

  test("should invite User B with copied roles from User A", async ({ page }) => {
    const userBEmail = `e2e-user-b-${Date.now()}@e2e.test`;

    await page.goto(`${tenantBaseUrl}/users`);
    await page.waitForLoadState("networkidle");

    await page.getByRole("button", { name: "Einladen" }).click();

    const inviteDialog = page.getByTestId("invite-user-dialog");
    await expect(inviteDialog).toBeVisible({ timeout: 10000 });

    // Fill User B's data
    await inviteDialog.getByTestId("user-email-input").fill(userBEmail);
    await inviteDialog.getByTestId("user-firstname-input").fill("User");
    await inviteDialog.getByTestId("user-lastname-input").fill("B");

    // Open the "copy roles from" select and choose User A
    await inviteDialog.getByTestId("copy-roles-select").click();

    // SelectContent renders in a portal — wait for User A option
    // Option text: "User A (e2e-user-a-...@e2e.test)"
    const userAOption = page.getByRole("option", { name: /User A/ });
    await expect(userAOption).toBeVisible({ timeout: 10000 });
    await userAOption.click();

    // Role preview badges should appear (the "Redakteur" role assigned to User A)
    const roleBadge = inviteDialog.getByText(ROLE_NAME);
    await expect(roleBadge).toBeVisible({ timeout: 10000 });

    // Submit
    await inviteDialog.getByTestId("invite-user-submit").click();

    await expect(inviteDialog).not.toBeVisible({ timeout: 10000 });

    // User B should appear in the user table
    const userTable = page.getByTestId("user-table");
    await expect(userTable).toBeVisible({ timeout: 10000 });
    await expect(userTable.getByText(userBEmail)).toBeVisible({ timeout: 10000 });
  });
});
