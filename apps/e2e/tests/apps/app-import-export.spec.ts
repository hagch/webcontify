/**
 * E2E Tests: App Import and Export
 *
 * Tests the app blueprint import/export functionality:
 * 1. Create and publish an app via API
 * 2. Export the app blueprint via UI button
 * 3. Import the blueprint via a dialog to create a new app
 * 4. Verify the imported app appears in the list
 *
 * Setup:
 * - Creates a tenant via Platform Admin API
 * - Creates and publishes an app via API
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

test.describe.serial("App Import and Export", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  let tenantToken: string;
  let appId: string;
  let appName: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-app-export-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(
      `E2E App Export ${ts}`,
      {
        adminEmail,
        adminFirstName: "Export",
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
    const kcUser = await findKeycloakUserByEmail(
      kcToken,
      tenantRealm,
      adminEmail
    );
    if (!kcUser) {
      throw new Error(
        `Admin user not found in Keycloak realm ${tenantRealm}`
      );
    }
    await setUserPassword(kcToken, tenantRealm, kcUser.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, kcUser.id);

    // Get tenant token
    tenantToken = await getTenantUserToken(
      tenantRealm,
      adminEmail,
      ADMIN_PASSWORD
    );

    // Create and publish an app
    appName = `Export App ${ts}`;
    const app = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      appName,
      "App for export/import testing"
    );
    appId = app.id;

    await sharedApiClient.publishApp(
      tenantToken,
      tenantSlug,
      appId,
      "1.0.0",
      "Release for export"
    );
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should export the app blueprint via UI", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Navigate to app detail
    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });
    await table.getByText(appName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Set up download listener before clicking export
    const downloadPromise = page.waitForEvent("download", { timeout: 15000 });

    // Click the export button
    const exportButton = page.getByRole("button", {
      name: /export|exportieren/i,
    });
    await expect(exportButton).toBeVisible({ timeout: 10000 });
    await exportButton.click();

    // If there is a version selection dialog, select the version
    const exportDialog = page.getByTestId("export-app-dialog");
    const hasExportDialog = await exportDialog
      .isVisible({ timeout: 3000 })
      .catch(() => false);

    if (hasExportDialog) {
      // Select version 1.0.0 if prompted
      const versionSelect = exportDialog.getByTestId("export-version-select");
      const hasVersionSelect = await versionSelect
        .isVisible({ timeout: 3000 })
        .catch(() => false);

      if (hasVersionSelect) {
        await versionSelect.click();
        await page.getByRole("option", { name: "1.0.0" }).click();
      }

      await exportDialog.getByTestId("export-app-submit").click();
    }

    // Wait for the download to complete
    const download = await downloadPromise;
    expect(download).toBeTruthy();

    // Verify the downloaded file has a reasonable name
    const suggestedFilename = download.suggestedFilename();
    expect(suggestedFilename).toMatch(/\.json$/i);
  });

  test("should import an app blueprint via dialog", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Click the import button
    const importButton = page.getByRole("button", {
      name: /import|importieren/i,
    });
    await expect(importButton).toBeVisible({ timeout: 10000 });
    await importButton.click();

    const importDialog = page.getByTestId("import-app-dialog");
    await expect(importDialog).toBeVisible({ timeout: 10000 });

    // Create a mock blueprint JSON file for upload
    // The actual content would be an exported blueprint, we use a minimal one
    const blueprintContent = JSON.stringify({
      name: `Imported App ${Date.now()}`,
      description: "Imported via E2E test",
      version: "1.0.0",
    });

    // Upload the file via the file input
    const fileInput = importDialog.getByTestId("import-file-input");
    await fileInput.setInputFiles({
      name: "blueprint.json",
      mimeType: "application/json",
      buffer: Buffer.from(blueprintContent),
    });

    // Submit the import
    await importDialog.getByTestId("import-app-submit").click();

    // Dialog closes
    await expect(importDialog).not.toBeVisible({ timeout: 10000 });

    // Verify the imported app appears in the list
    await page.waitForLoadState("networkidle");
    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // The imported app should appear (with "Imported App" prefix)
    await expect(table.getByText(/Imported App/)).toBeVisible({
      timeout: 10000,
    });
  });
});
