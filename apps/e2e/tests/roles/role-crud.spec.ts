/**
 * E2E Tests: Role CRUD
 *
 * Tests the role management flow on the tenant workspace:
 * 1. System roles are displayed with the system-role-badge
 * 2. Custom roles can be created, edited, and deleted
 * 3. System roles cannot be deleted
 *
 * Setup:
 * - Creates a tenant via the Platform Admin API
 * - Waits for async provisioning (Keycloak realm creation)
 * - Uses Keycloak Admin API to set the initial admin password directly
 * - Logs in as the tenant admin on the tenant subdomain
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

test.describe.serial("Role CRUD", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-role-crud-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(`E2E Role CRUD ${ts}`, {
      adminEmail,
      adminFirstName: "Role",
      adminLastName: "Admin",
    });
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    await sharedApiClient.waitForTenantProvisioning(tenantId);

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
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
    await page.goto(`${tenantBaseUrl}/roles`);
    await page.waitForLoadState("networkidle");
  });

  test("should display system roles", async ({ page }) => {
    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    const systemRoleNames = ["Admin", "Editor", "Viewer", "Inviter"];

    for (const roleName of systemRoleNames) {
      await expect(table.getByText(roleName)).toBeVisible({ timeout: 10000 });
    }

    // All system roles should carry the system-role-badge
    const badges = table.getByTestId("system-role-badge");
    const badgeCount = await badges.count();
    expect(badgeCount).toBeGreaterThanOrEqual(4);
  });

  test("should create a custom role", async ({ page }) => {
    // Open create dialog
    await page.getByRole("button", { name: /rolle erstellen|create role/i }).click();

    const dialog = page.getByTestId("create-role-dialog");
    await expect(dialog).toBeVisible({ timeout: 10000 });

    await dialog.getByTestId("role-name-input").fill("Redakteur");
    await dialog
      .getByTestId("role-description-input")
      .fill("Can edit content");

    await dialog.getByTestId("create-role-submit").click();

    // Dialog should close
    await expect(dialog).not.toBeVisible({ timeout: 10000 });

    // New role should appear in the table
    const table = page.getByTestId("role-table");
    await expect(table.getByText("Redakteur")).toBeVisible({ timeout: 10000 });

    // Custom role should NOT have a system-role-badge
    const rows = table.getByTestId("role-table-row");
    const rowCount = await rows.count();
    let redakteurRow: ReturnType<typeof table.getByTestId> | null = null;
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      if ((await row.getByText("Redakteur").count()) > 0) {
        redakteurRow = row as unknown as ReturnType<typeof table.getByTestId>;
        // Custom role row must not contain a system badge
        await expect(row.getByTestId("system-role-badge")).toHaveCount(0);
        break;
      }
    }
    expect(redakteurRow).not.toBeNull();
  });

  test("should edit a custom role", async ({ page }) => {
    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Navigate to custom role detail by clicking the row / role name
    await table.getByText("Redakteur").click();

    // Wait for detail page to load
    await page.waitForLoadState("networkidle");

    // Look for an edit button or editable name field
    const editButton = page.getByRole("button", { name: /bearbeiten|edit/i });
    if (await editButton.isVisible({ timeout: 3000 }).catch(() => false)) {
      await editButton.click();
    }

    const nameInput = page.getByTestId("role-name-input");
    await expect(nameInput).toBeVisible({ timeout: 10000 });
    await nameInput.clear();
    await nameInput.fill("Senior Redakteur");

    await page.getByRole("button", { name: /speichern|save/i }).click();

    // Verify the updated name is visible on the page
    await expect(page.getByText("Senior Redakteur")).toBeVisible({
      timeout: 10000,
    });
  });

  test("should not allow deleting system roles", async ({ page }) => {
    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Find the Admin system role row and open its actions menu
    const rows = table.getByTestId("role-table-row");
    const rowCount = await rows.count();

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      if (
        (await row.getByText("Admin").count()) > 0 &&
        (await row.getByTestId("system-role-badge").count()) > 0
      ) {
        // Find the actions menu for this row and open it
        const actionsMenu = row.getByRole("button", { name: /aktionen|actions/i });
        if (await actionsMenu.isVisible({ timeout: 2000 }).catch(() => false)) {
          await actionsMenu.click();
        } else {
          // Try data-testid pattern — id may vary, look for any menu trigger in the row
          const menuTrigger = row.locator("[data-testid^='actions-menu-']");
          await menuTrigger.click();
        }

        // Delete action should not be present or should be disabled
        const deleteAction = page.getByRole("menuitem", {
          name: /löschen|delete/i,
        });
        const isVisible = await deleteAction
          .isVisible({ timeout: 3000 })
          .catch(() => false);
        if (isVisible) {
          await expect(deleteAction).toBeDisabled();
        }
        // else: delete action is simply absent — that satisfies the requirement
        break;
      }
    }
  });

  test("should delete a custom role", async ({ page }) => {
    const table = page.getByTestId("role-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    const rows = table.getByTestId("role-table-row");
    const rowCount = await rows.count();

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      if ((await row.getByText("Senior Redakteur").count()) > 0) {
        // Open actions menu via data-testid or role
        const menuTrigger = row.locator("[data-testid^='actions-menu-']");
        await menuTrigger.click();
        break;
      }
    }

    // Click delete in the dropdown
    await page
      .getByRole("menuitem", { name: /löschen|delete/i })
      .click();

    // Confirm in the confirmation dialog
    const confirmButton = page.getByRole("button", {
      name: /bestätigen|confirm|löschen|delete/i,
    });
    await expect(confirmButton).toBeVisible({ timeout: 10000 });
    await confirmButton.click();

    // Role should be gone from the table
    await expect(table.getByText("Senior Redakteur")).not.toBeVisible({
      timeout: 10000,
    });
  });
});
