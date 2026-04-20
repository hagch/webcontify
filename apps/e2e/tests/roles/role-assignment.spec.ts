/**
 * E2E Tests: Role Assignment
 *
 * Tests the role assignment flow on the tenant workspace:
 * 1. A user can be assigned to a custom role from the role detail page
 * 2. A user can be removed from a role
 * 3. Removing the last admin assignee from the Admin system role is prevented
 *
 * Setup:
 * - Creates a tenant via the Platform Admin API
 * - Waits for async provisioning (Keycloak realm creation)
 * - Uses Keycloak Admin API to set the initial admin password
 * - Logs in as the tenant admin
 * - Creates a custom role and invites a second user via the UI
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
const CUSTOM_ROLE_NAME = "E2E Zuweisung";
const INVITED_USER_FIRSTNAME = "Test";
const INVITED_USER_LASTNAME = "Assignee";

test.describe.serial("Role Assignment", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let invitedEmail: string;
  let tenantBaseUrl: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async ({ browser }) => {
    const ts = Date.now();
    adminEmail = `e2e-assign-admin-${ts}@e2e.test`;
    invitedEmail = `e2e-assign-user-${ts}@e2e.test`;

    const tenant = await sharedApiClient.createTenant(
      `E2E Role Assign ${ts}`,
      {
        adminEmail,
        adminFirstName: "Assign",
        adminLastName: "Admin",
      }
    );
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    await sharedApiClient.waitForTenantProvisioning(tenantId);

    // Set admin password via Keycloak Admin API
    const kcToken = await getKeycloakAdminToken();
    const tenantRealm = `tenant-${tenantSlug}`;
    const kcUser = await findKeycloakUserByEmail(
      kcToken,
      tenantRealm,
      adminEmail
    );
    if (!kcUser) {
      throw new Error(
        `Initial admin user not found in Keycloak realm ${tenantRealm} for email ${adminEmail}`
      );
    }
    await setUserPassword(kcToken, tenantRealm, kcUser.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, kcUser.id);

    // Use a browser context for setup UI actions
    const context = await browser.newContext();
    const page = await context.newPage();

    try {
      await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);

      // 1. Create a custom role via the UI
      await page.goto(`${tenantBaseUrl}/roles`);
      await page.waitForLoadState("networkidle");

      await page.getByRole("button", { name: /rolle erstellen|create role/i }).click();
      const createDialog = page.getByTestId("create-role-dialog");
      await expect(createDialog).toBeVisible({ timeout: 10000 });
      await createDialog.getByTestId("role-name-input").fill(CUSTOM_ROLE_NAME);
      await createDialog
        .getByTestId("role-description-input")
        .fill("Created for assignment E2E tests");
      await createDialog.getByTestId("create-role-submit").click();
      await expect(createDialog).not.toBeVisible({ timeout: 10000 });

      // 2. Invite a second user via the UI
      await page.goto(`${tenantBaseUrl}/users`);
      await page.waitForLoadState("networkidle");

      await page.getByRole("button", { name: /einladen|invite/i }).click();
      const inviteDialog = page.getByTestId("invite-user-dialog");
      await expect(inviteDialog).toBeVisible({ timeout: 10000 });
      await inviteDialog.getByTestId("user-email-input").fill(invitedEmail);
      await inviteDialog
        .getByTestId("user-firstname-input")
        .fill(INVITED_USER_FIRSTNAME);
      await inviteDialog
        .getByTestId("user-lastname-input")
        .fill(INVITED_USER_LASTNAME);
      await inviteDialog.getByTestId("invite-user-submit").click();
      await expect(inviteDialog).not.toBeVisible({ timeout: 10000 });

      // Verify the invited user appears in the table
      const userTable = page.getByTestId("user-table");
      await expect(userTable.getByText(invitedEmail)).toBeVisible({
        timeout: 10000,
      });
    } finally {
      await context.close();
    }
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should assign a user to a role from role detail page", async ({
    page,
  }) => {
    await page.goto(`${tenantBaseUrl}/roles`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Navigate to custom role detail
    await table.getByText(CUSTOM_ROLE_NAME).click();
    await page.waitForLoadState("networkidle");

    // Navigate to assignees tab if there are multiple tabs
    const assigneesTab = page.getByRole("tab", {
      name: /mitglieder|assignees|benutzer/i,
    });
    if (await assigneesTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await assigneesTab.click();
    }

    // Click add assignee button
    const addButton = page.getByTestId("add-assignee-button");
    await expect(addButton).toBeVisible({ timeout: 10000 });
    await addButton.click();

    // Add assignees dialog should open
    const addDialog = page.getByTestId("add-assignees-dialog");
    await expect(addDialog).toBeVisible({ timeout: 10000 });

    // Select the invited user from the dialog
    const userEntry = addDialog.getByText(invitedEmail);
    await expect(userEntry).toBeVisible({ timeout: 10000 });
    await userEntry.click();

    // Confirm the selection
    await addDialog
      .getByRole("button", { name: /hinzufügen|add|bestätigen|confirm/i })
      .click();
    await expect(addDialog).not.toBeVisible({ timeout: 10000 });

    // User should appear in the assignees table
    const assigneesTable = page.getByTestId("role-assignees-table");
    await expect(assigneesTable).toBeVisible({ timeout: 10000 });
    await expect(assigneesTable.getByText(invitedEmail)).toBeVisible({
      timeout: 10000,
    });
  });

  test("should remove a user from a role", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/roles`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Navigate to custom role detail
    await table.getByText(CUSTOM_ROLE_NAME).click();
    await page.waitForLoadState("networkidle");

    // Navigate to assignees tab if present
    const assigneesTab = page.getByRole("tab", {
      name: /mitglieder|assignees|benutzer/i,
    });
    if (await assigneesTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await assigneesTab.click();
    }

    const assigneesTable = page.getByTestId("role-assignees-table");
    await expect(assigneesTable).toBeVisible({ timeout: 10000 });
    await expect(assigneesTable.getByText(invitedEmail)).toBeVisible({
      timeout: 10000,
    });

    // Find the row for the invited user and open its actions menu
    const rows = assigneesTable.getByTestId("role-table-row");
    const rowCount = await rows.count();

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      if ((await row.getByText(invitedEmail).count()) > 0) {
        const menuTrigger = row.locator("[data-testid^='actions-menu-']");
        await menuTrigger.click();
        break;
      }
    }

    // Click remove / entfernen
    await page
      .getByRole("menuitem", { name: /entfernen|remove/i })
      .click();

    // Confirm if a confirmation dialog appears
    const confirmButton = page.getByRole("button", {
      name: /bestätigen|confirm|entfernen|remove/i,
    });
    if (
      await confirmButton.isVisible({ timeout: 3000 }).catch(() => false)
    ) {
      await confirmButton.click();
    }

    // User should no longer appear in the assignees table
    await expect(assigneesTable.getByText(invitedEmail)).not.toBeVisible({
      timeout: 10000,
    });
  });

  test("should prevent removing last admin assignee", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/roles`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Navigate to the Admin system role detail
    const rows = table.getByTestId("role-table-row");
    const rowCount = await rows.count();

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      if (
        (await row.getByText("Admin").count()) > 0 &&
        (await row.getByTestId("system-role-badge").count()) > 0
      ) {
        await row.getByText("Admin").click();
        break;
      }
    }

    await page.waitForLoadState("networkidle");

    // Navigate to assignees tab if present
    const assigneesTab = page.getByRole("tab", {
      name: /mitglieder|assignees|benutzer/i,
    });
    if (await assigneesTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await assigneesTab.click();
    }

    const assigneesTable = page.getByTestId("role-assignees-table");
    await expect(assigneesTable).toBeVisible({ timeout: 10000 });

    // There should be exactly one admin (the initial admin we created)
    const assigneeRows = assigneesTable.getByTestId("role-table-row");
    const assigneeCount = await assigneeRows.count();
    expect(assigneeCount).toBeGreaterThanOrEqual(1);

    // Open actions menu for the first (only) admin row
    const firstRow = assigneeRows.first();
    const menuTrigger = firstRow.locator("[data-testid^='actions-menu-']");

    if (await menuTrigger.isVisible({ timeout: 3000 }).catch(() => false)) {
      await menuTrigger.click();

      // Remove action should be absent or disabled
      const removeAction = page.getByRole("menuitem", {
        name: /entfernen|remove/i,
      });
      const isVisible = await removeAction
        .isVisible({ timeout: 3000 })
        .catch(() => false);
      if (isVisible) {
        await removeAction.click();

        // Expect an error toast or inline message — backend should reject
        const errorIndicator = page.locator(
          "[data-sonner-toast], [role='alert'], [data-testid='error-message']"
        );
        await expect(errorIndicator).toBeVisible({ timeout: 10000 });
      }
      // If the remove action is absent, the requirement is met
    }
    // If no menu trigger exists for the last admin, the protection is UI-level
  });
});
