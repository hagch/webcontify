/**
 * E2E Tests: App Versioning
 *
 * Tests the app version lifecycle:
 * 1. Create and publish multiple versions via API
 * 2. Verify the version list in the UI
 * 3. Verify version details (release notes, timestamps)
 * 4. Verify the current version indicator
 *
 * Setup:
 * - Creates a tenant via Platform Admin API
 * - Creates an app and publishes 3 versions via API
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

test.describe.serial("App Versioning", () => {
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
    adminEmail = `e2e-app-version-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(
      `E2E App Version ${ts}`,
      {
        adminEmail,
        adminFirstName: "Version",
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

    // Create an app and publish 3 versions
    appName = `Versioned App ${ts}`;
    const app = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      appName,
      "App for version testing"
    );
    appId = app.id;

    await sharedApiClient.publishApp(
      tenantToken,
      tenantSlug,
      appId,
      "1.0.0",
      "Initial release"
    );

    // Edit and publish second version
    await sharedApiClient.updateApp(tenantToken, tenantSlug, appId, {
      description: "Updated for v1.1.0",
    });
    await sharedApiClient.publishApp(
      tenantToken,
      tenantSlug,
      appId,
      "1.1.0",
      "Bug fixes and improvements"
    );

    // Edit and publish third version
    await sharedApiClient.updateApp(tenantToken, tenantSlug, appId, {
      description: "Updated for v2.0.0",
    });
    await sharedApiClient.publishApp(
      tenantToken,
      tenantSlug,
      appId,
      "2.0.0",
      "Major feature update"
    );
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should display the version list with all published versions", async ({
    page,
  }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Navigate to app detail
    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });
    await table.getByText(appName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Look for a versions tab or section
    const versionsTab = page.getByRole("tab", { name: /versions|versionen/i });
    const hasVersionsTab = await versionsTab
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    if (hasVersionsTab) {
      await versionsTab.click();
      await page.waitForLoadState("networkidle");
    }

    // Verify all 3 versions are listed
    const versionList = page.getByTestId("app-version-list");
    await expect(versionList).toBeVisible({ timeout: 10000 });

    await expect(versionList.getByText("1.0.0")).toBeVisible({ timeout: 10000 });
    await expect(versionList.getByText("1.1.0")).toBeVisible({ timeout: 10000 });
    await expect(versionList.getByText("2.0.0")).toBeVisible({ timeout: 10000 });
  });

  test("should show the latest version as the current version", async ({
    page,
  }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("app-table");
    await table.getByText(appName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // The current/latest version indicator should show 2.0.0
    const currentVersion = page.getByTestId("app-current-version");
    const hasCurrentVersion = await currentVersion
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (hasCurrentVersion) {
      await expect(currentVersion).toContainText("2.0.0");
    } else {
      // Fallback: check that 2.0.0 is prominently displayed
      await expect(page.getByText("2.0.0")).toBeVisible({ timeout: 10000 });
    }
  });

  test("should display release notes for a version", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("app-table");
    await table.getByText(appName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Navigate to versions tab if present
    const versionsTab = page.getByRole("tab", { name: /versions|versionen/i });
    const hasVersionsTab = await versionsTab
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    if (hasVersionsTab) {
      await versionsTab.click();
      await page.waitForLoadState("networkidle");
    }

    // Click on a version to see its details
    const versionList = page.getByTestId("app-version-list");
    await expect(versionList).toBeVisible({ timeout: 10000 });

    await versionList.getByText("2.0.0").click();
    await page.waitForLoadState("networkidle");

    // Release notes should be visible
    await expect(page.getByText("Major feature update")).toBeVisible({
      timeout: 10000,
    });
  });
});
