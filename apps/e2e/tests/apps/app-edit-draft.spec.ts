/**
 * E2E Tests: App Edit Draft
 *
 * Tests that editing an app name via API creates a draft revision:
 * 1. Create and publish an app via API
 * 2. Edit the app name via API
 * 3. Verify the DRAFT badge and pending revision in the UI
 * 4. Re-publish and verify the name update is live
 *
 * Setup:
 * - Creates a tenant via Platform Admin API
 * - Waits for provisioning, sets admin password
 * - Creates an app via API, publishes it
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

test.describe.serial("App Edit Draft", () => {
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
    adminEmail = `e2e-app-draft-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(
      `E2E App Draft ${ts}`,
      {
        adminEmail,
        adminFirstName: "Draft",
        adminLastName: "Admin",
      }
    );
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    await sharedApiClient.waitForTenantProvisioning(tenantId);

    // Set admin password in Keycloak
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

    // Get tenant token for API calls
    tenantToken = await getTenantUserToken(
      tenantRealm,
      adminEmail,
      ADMIN_PASSWORD
    );

    // Create and publish an app via API
    appName = `Draft Test App ${ts}`;
    const app = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      appName,
      "App for draft testing"
    );
    appId = app.id;

    await sharedApiClient.publishApp(
      tenantToken,
      tenantSlug,
      appId,
      "1.0.0",
      "Initial version"
    );
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should show published app without draft badge", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });
    await expect(table.getByText(appName)).toBeVisible({ timeout: 10000 });

    // Navigate to detail
    await table.getByText(appName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Should show published status, not draft
    const statusBadge = page.getByTestId("app-status-badge");
    await expect(statusBadge).toBeVisible({ timeout: 10000 });
    await expect(statusBadge).toContainText(/published|active/i);
  });

  test("should show draft badge after editing the app name via API", async ({
    page,
  }) => {
    // Edit the app name via API — creates a draft revision
    const updatedName = `${appName} Updated`;
    await sharedApiClient.updateApp(tenantToken, tenantSlug, appId, {
      name: updatedName,
    });

    // Navigate to app detail
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // The updated name should appear
    await expect(table.getByText(updatedName)).toBeVisible({ timeout: 10000 });

    // Navigate to detail
    await table.getByText(updatedName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Should show a DRAFT badge or pending changes indicator
    const draftBadge = page.getByTestId("app-draft-badge");
    const statusBadge = page.getByTestId("app-status-badge");

    // Either a dedicated draft badge or the status badge showing "draft"
    const hasDraftBadge = await draftBadge
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (hasDraftBadge) {
      await expect(draftBadge).toBeVisible();
    } else {
      await expect(statusBadge).toContainText(/draft|pending/i);
    }

    // Update the name for subsequent tests
    appName = updatedName;
  });

  test("should show the updated name after re-publishing", async ({
    page,
  }) => {
    // Re-publish the app
    await sharedApiClient.publishApp(
      tenantToken,
      tenantSlug,
      appId,
      "1.1.0",
      "Name update"
    );

    // Navigate to app detail
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Click into app detail
    await table.getByText(appName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Should show the updated name and published status
    await expect(page.getByText(appName)).toBeVisible({ timeout: 10000 });

    const statusBadge = page.getByTestId("app-status-badge");
    await expect(statusBadge).toContainText(/published|active/i);

    // Version 1.1.0 should be visible
    await expect(page.getByText("1.1.0")).toBeVisible({ timeout: 10000 });
  });
});
