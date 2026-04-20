/**
 * E2E Tests: Content Definition CRUD
 *
 * Tests the content definition lifecycle on the graph canvas:
 * 1. Navigate to an app's Content tab
 * 2. See empty state
 * 3. Open spotlight and create a new definition
 * 4. Verify the node appears in the graph
 * 5. Edit the definition description via popover
 * 6. Delete the definition
 *
 * Setup:
 * - Creates a tenant via Platform Admin API
 * - Creates an app via API
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
import { getTenantUserToken, loginAsTenantUser } from "../../helpers/auth";

const ADMIN_PASSWORD = "E2eAdmin1!";

test.describe.serial("Content Definition CRUD", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  let tenantToken: string;
  let appId: string;
  let appSlug: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-content-crud-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(
      `E2E Content CRUD ${ts}`,
      {
        adminEmail,
        adminFirstName: "Content",
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

    // Get tenant token for API calls
    tenantToken = await getTenantUserToken(
      tenantRealm,
      adminEmail,
      ADMIN_PASSWORD
    );

    // Create an app
    const app = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      `Content Test App ${ts}`,
      "App for content definition E2E tests"
    );
    appId = app.id;
    appSlug = app.slug;
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should show empty state on Content tab", async ({ page }) => {
    // Navigate to app detail page
    await page.goto(`${tenantBaseUrl}/apps/${appId}`);
    await page.waitForLoadState("networkidle");

    // Click the Content tab
    const contentTab = page.getByRole("tab", {
      name: /content/i,
    });
    await expect(contentTab).toBeVisible({ timeout: 10000 });
    await contentTab.click();

    // Wait for the graph to render — empty state shows "Create first definition" button
    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    const createFirstButton = page.getByTestId("create-first-definition");
    await expect(createFirstButton).toBeVisible({ timeout: 10000 });
  });

  test("should create a definition via spotlight", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps/${appId}`);
    await page.waitForLoadState("networkidle");

    // Switch to Content tab
    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Click "Create first definition" or open spotlight via "+" button
    const createFirstButton = page.getByTestId("create-first-definition");
    const hasCreateFirst = await createFirstButton
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (hasCreateFirst) {
      await createFirstButton.click();
    } else {
      // Fallback: use the spotlight button in the toolbar
      const spotlightButton = page.getByTestId("open-spotlight");
      await spotlightButton.click();
    }

    // The spotlight or the definition popover should appear
    // If spotlight opened, select "Create new definition"
    const spotlight = page.getByTestId("content-spotlight");
    const spotlightVisible = await spotlight
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (spotlightVisible) {
      // The first item in spotlight is typically "Create new definition"
      const createItem = spotlight.locator("[data-testid^='spotlight-item-']").first();
      await expect(createItem).toBeVisible({ timeout: 5000 });
      await createItem.click();
    }

    // The definition popover should appear for naming the definition
    const popover = page.getByTestId("definition-popover");
    await expect(popover).toBeVisible({ timeout: 10000 });

    // Fill in the name
    await popover.getByTestId("definition-name-input").fill("Blog Post");

    // Save
    await popover.getByTestId("definition-save").click();

    // Popover should close
    await expect(popover).not.toBeVisible({ timeout: 10000 });

    // The node should appear in the graph
    await expect(graph.getByText("Blog Post")).toBeVisible({ timeout: 10000 });
  });

  test("should edit definition description via popover", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps/${appId}`);
    await page.waitForLoadState("networkidle");

    // Switch to Content tab
    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Find the "Blog Post" node and click edit
    const nodeText = graph.getByText("Blog Post");
    await expect(nodeText).toBeVisible({ timeout: 10000 });

    // Double-click the node header to open the edit popover
    await nodeText.dblclick();

    // The definition popover should open
    const popover = page.getByTestId("definition-popover");
    await expect(popover).toBeVisible({ timeout: 10000 });

    // Edit the description
    await popover
      .getByTestId("definition-description-input")
      .fill("A blog post with title, content and author");

    // Save
    await popover.getByTestId("definition-save").click();

    // Popover should close
    await expect(popover).not.toBeVisible({ timeout: 10000 });
  });

  test("should delete a definition", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps/${appId}`);
    await page.waitForLoadState("networkidle");

    // Switch to Content tab
    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Find the node and click the delete button
    const nodeText = graph.getByText("Blog Post");
    await expect(nodeText).toBeVisible({ timeout: 10000 });

    // Click the delete button on the node
    // The delete button has data-testid="delete-definition-{id}"
    const deleteButton = graph.locator("[data-testid^='delete-definition-']");
    await expect(deleteButton).toBeVisible({ timeout: 10000 });
    await deleteButton.click();

    // Confirm deletion in the dialog
    const confirmButton = page.getByRole("button", {
      name: /confirm|delete|remove|best[aä]tigen|l[oö]schen|entfernen/i,
    });
    await expect(confirmButton).toBeVisible({ timeout: 10000 });
    await confirmButton.click();

    // Node should disappear
    await expect(graph.getByText("Blog Post")).not.toBeVisible({
      timeout: 10000,
    });

    // Empty state should reappear
    const createFirstButton = page.getByTestId("create-first-definition");
    await expect(createFirstButton).toBeVisible({ timeout: 10000 });
  });
});
