/**
 * E2E Tests: App CRUD and Publish
 *
 * Tests the app lifecycle on the tenant workspace:
 * 1. Create an app via the create dialog
 * 2. Verify it appears in the apps list
 * 3. Navigate to app detail page
 * 4. Publish the app with a version
 * 5. Delete the app
 *
 * Setup:
 * - Creates a tenant via Platform Admin API
 * - Waits for async provisioning
 * - Sets admin password via Keycloak Admin API
 * - Logs in as tenant admin on the tenant subdomain
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

test.describe.serial("App CRUD and Publish", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  let appName: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-app-crud-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(`E2E App CRUD ${ts}`, {
      adminEmail,
      adminFirstName: "App",
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
  });

  test("should create an app via the create dialog", async ({ page }) => {
    appName = `Test App ${Date.now()}`;

    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Open create dialog
    await page.getByRole("button", { name: /create app|app erstellen/i }).click();

    const dialog = page.getByTestId("create-app-dialog");
    await expect(dialog).toBeVisible({ timeout: 10000 });

    // Fill in app name
    await dialog.getByTestId("app-name-input").fill(appName);
    await dialog
      .getByTestId("app-description-input")
      .fill("E2E test app description");

    // Submit
    await dialog.getByTestId("create-app-submit").click();

    // Dialog closes
    await expect(dialog).not.toBeVisible({ timeout: 10000 });

    // Should redirect to app detail or show in list
    // Wait for either a redirect to the app detail page or the app list to update
    await page.waitForLoadState("networkidle");
  });

  test("should display the created app in the apps list", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // The created app should appear in the table
    await expect(table.getByText(appName)).toBeVisible({ timeout: 10000 });

    // App should have DRAFT status badge
    const rows = table.getByTestId("app-table-row");
    const rowCount = await rows.count();
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      if ((await row.getByText(appName).count()) > 0) {
        await expect(row.getByTestId("app-status-badge")).toContainText(
          /draft/i
        );
        break;
      }
    }
  });

  test("should navigate to app detail page", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Click the app name link
    await table.getByText(appName).click();

    // Should navigate to app detail
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // App name should be visible on the detail page
    const detail = page.getByTestId("app-detail");
    await expect(detail).toBeVisible({ timeout: 10000 });
    await expect(detail.getByText(appName)).toBeVisible({ timeout: 10000 });
  });

  test("should publish the app", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Navigate to app detail
    const table = page.getByTestId("app-table");
    await table.getByText(appName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Click publish button
    await page.getByRole("button", { name: /publish|ver[oö]ffentlichen/i }).click();

    const publishDialog = page.getByTestId("publish-app-dialog");
    await expect(publishDialog).toBeVisible({ timeout: 10000 });

    // Fill in version
    await publishDialog.getByTestId("app-version-input").fill("1.0.0");
    await publishDialog
      .getByTestId("app-release-notes-input")
      .fill("Initial release");

    // Submit publish
    await publishDialog.getByTestId("publish-app-submit").click();

    // Dialog closes
    await expect(publishDialog).not.toBeVisible({ timeout: 10000 });

    // Status should change to PUBLISHED
    await expect(page.getByTestId("app-status-badge")).toContainText(
      /published|active/i,
      { timeout: 10000 }
    );

    // Version should be visible
    await expect(page.getByText("1.0.0")).toBeVisible({ timeout: 10000 });
  });

  test("should delete the app", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Find the app row and open actions menu
    const rows = table.getByTestId("app-table-row");
    const rowCount = await rows.count();
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      if ((await row.getByText(appName).count()) > 0) {
        const menuTrigger = row.getByRole("button", {
          name: /actions|aktionen/i,
        });
        await menuTrigger.click();
        break;
      }
    }

    // Click delete in the dropdown
    await page
      .getByRole("menuitem", { name: /delete|l[oö]schen|loeschen/i })
      .click();

    // Confirm deletion
    const confirmButton = page.getByRole("button", {
      name: /confirm|best[aä]tigen|bestaetigen|delete|l[oö]schen|loeschen/i,
    });
    await expect(confirmButton).toBeVisible({ timeout: 10000 });
    await confirmButton.click();

    // App should be gone from the table
    await expect(table.getByText(appName)).not.toBeVisible({
      timeout: 10000,
    });
  });
});
