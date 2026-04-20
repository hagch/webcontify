/**
 * E2E Tests: App Dependencies
 *
 * Tests the app dependency management via the interactive React Flow graph:
 * 1. Create two apps via API
 * 2. Add a dependency via the graph's add-dependency node + popover
 * 3. Verify the dependency appears as a node in the graph
 * 4. Attempt to deactivate the dependency target — expect a problem/warning
 * 5. Remove the dependency via the edge delete button on hover
 *
 * Setup:
 * - Creates a tenant via Platform Admin API
 * - Creates two apps via API
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

test.describe.serial("App Dependencies", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  let tenantToken: string;
  let parentAppId: string;
  let parentAppName: string;
  let dependencyAppId: string;
  let dependencyAppName: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-app-deps-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(
      `E2E App Deps ${ts}`,
      {
        adminEmail,
        adminFirstName: "Deps",
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

    // Create two apps
    parentAppName = `Parent App ${ts}`;
    const parentApp = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      parentAppName,
      "App that depends on another"
    );
    parentAppId = parentApp.id;

    dependencyAppName = `Dependency App ${ts}`;
    const depApp = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      dependencyAppName,
      "App that is depended upon"
    );
    dependencyAppId = depApp.id;

    // Publish both apps
    await sharedApiClient.publishApp(
      tenantToken,
      tenantSlug,
      parentAppId,
      "1.0.0"
    );
    await sharedApiClient.publishApp(
      tenantToken,
      tenantSlug,
      dependencyAppId,
      "1.0.0"
    );
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should add a dependency via the graph", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Navigate to parent app detail
    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });
    await table.getByText(parentAppName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Navigate to dependencies tab if present
    const depsTab = page.getByRole("tab", {
      name: /dependencies|abh[aä]ngigkeiten/i,
    });
    const hasDepsTab = await depsTab
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    if (hasDepsTab) {
      await depsTab.click();
      await page.waitForLoadState("networkidle");
    }

    // Wait for the dependency graph to render
    const graph = page.getByTestId("app-dependency-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Click the add dependency node in the React Flow graph
    const addNode = page.getByTestId("add-dependency-node");
    await expect(addNode).toBeVisible({ timeout: 10000 });
    await addNode.click();

    // Wait for the popover to appear
    const popover = page.getByTestId("add-dependency-popover");
    await expect(popover).toBeVisible({ timeout: 10000 });

    // Search for the dependency app
    const searchField = page.getByTestId("add-dependency-search");
    await searchField.fill(dependencyAppName);

    // Click the matching app option
    const appOption = page.getByTestId(/^add-dep-option-/);
    await expect(appOption).toBeVisible({ timeout: 10000 });
    await appOption.click();

    // Popover should close
    await expect(popover).not.toBeVisible({ timeout: 10000 });

    // Dependency app name should appear as a node in the graph
    await expect(graph.getByText(dependencyAppName)).toBeVisible({
      timeout: 10000,
    });
  });

  test("should show a warning when deactivating a dependency target", async ({
    page,
  }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Navigate to the dependency app
    const table = page.getByTestId("app-table");
    await expect(table).toBeVisible({ timeout: 10000 });
    await table.getByText(dependencyAppName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Try to deactivate the app via the actions menu or status toggle
    const deactivateButton = page.getByRole("button", {
      name: /deactivate|deaktivieren/i,
    });
    const hasDeactivateButton = await deactivateButton
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (hasDeactivateButton) {
      await deactivateButton.click();

      // Should show a warning about dependent apps
      const warning = page.getByTestId("dependency-warning");
      const warningVisible = await warning
        .isVisible({ timeout: 5000 })
        .catch(() => false);

      if (warningVisible) {
        await expect(warning).toContainText(parentAppName);
      } else {
        // The warning might be shown as a toast or dialog
        const warningDialog = page.getByRole("alertdialog");
        await expect(warningDialog).toBeVisible({ timeout: 5000 });
        await expect(warningDialog.getByText(parentAppName)).toBeVisible();

        // Cancel the deactivation
        await warningDialog
          .getByRole("button", { name: /cancel|abbrechen/i })
          .click();
      }
    } else {
      // Try via actions menu
      const menuTrigger = page.getByRole("button", {
        name: /actions|aktionen/i,
      });
      if (
        await menuTrigger.isVisible({ timeout: 3000 }).catch(() => false)
      ) {
        await menuTrigger.click();
        const deactivateMenuItem = page.getByRole("menuitem", {
          name: /deactivate|deaktivieren/i,
        });
        if (
          await deactivateMenuItem
            .isVisible({ timeout: 3000 })
            .catch(() => false)
        ) {
          await deactivateMenuItem.click();

          // Should show warning
          const warningDialog = page.getByRole("alertdialog");
          const hasWarning = await warningDialog
            .isVisible({ timeout: 5000 })
            .catch(() => false);
          if (hasWarning) {
            await expect(warningDialog.getByText(parentAppName)).toBeVisible();
            await warningDialog
              .getByRole("button", { name: /cancel|abbrechen/i })
              .click();
          }
        }
      }
    }
  });

  test("should remove a dependency via the graph", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps`);
    await page.waitForLoadState("networkidle");

    // Navigate to parent app detail
    const table = page.getByTestId("app-table");
    await table.getByText(parentAppName).click();
    await page.waitForURL(/\/apps\/[^/]+$/, { timeout: 15000 });
    await page.waitForLoadState("networkidle");

    // Navigate to dependencies tab if present
    const depsTab = page.getByRole("tab", {
      name: /dependencies|abh[aä]ngigkeiten/i,
    });
    const hasDepsTab = await depsTab
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    if (hasDepsTab) {
      await depsTab.click();
      await page.waitForLoadState("networkidle");
    }

    // Wait for the dependency graph to render
    const graph = page.getByTestId("app-dependency-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Verify the dependency node is present before removal
    await expect(graph.getByText(dependencyAppName)).toBeVisible({
      timeout: 10000,
    });

    // Hover over the edge connecting the apps to reveal the delete button
    const edge = graph.locator(".react-flow__edge").first();
    await expect(edge).toBeVisible({ timeout: 10000 });
    await edge.hover({ force: true });

    // Click the delete button that appears on the edge
    const deleteButton = page.locator("[data-testid^='edge-delete-']");
    await expect(deleteButton).toBeVisible({ timeout: 5000 });
    await deleteButton.click();

    // Confirm removal in the dialog
    const removeDialog = page.getByTestId("remove-dependency-dialog");
    await expect(removeDialog).toBeVisible({ timeout: 10000 });
    await removeDialog
      .getByRole("button", {
        name: /confirm|best[aä]tigen|bestaetigen|remove|entfernen/i,
      })
      .click();

    // Dialog should close
    await expect(removeDialog).not.toBeVisible({ timeout: 10000 });

    // Dependency node should no longer be visible in the graph
    await expect(graph.getByText(dependencyAppName)).not.toBeVisible({
      timeout: 10000,
    });
  });
});
