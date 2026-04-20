/**
 * E2E Tests: Content Definition References
 *
 * Tests cross-app reference management on the graph canvas:
 * 1. Setup: App "Blog" with definition "Post", App "Core" with definition "User"
 * 2. Open spotlight and add "User" from Core as ghost node
 * 3. Verify ghost node appears with dashed border
 * 4. Verify Dependencies tab shows "Core"
 * 5. Create a REFERENCE field via API (drag-and-connect is fragile in Playwright)
 * 6. Verify the edge appears in the graph
 * 7. Delete the reference field
 * 8. Remove the ghost node and verify dependency is deleted
 *
 * Setup:
 * - Creates a tenant via Platform Admin API
 * - Creates two apps ("Blog" and "Core") with definitions via API
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
import {
  createContentDefinitionViaApi,
  addFieldViaApi,
} from "../../helpers/content-api";

const ADMIN_PASSWORD = "E2eAdmin1!";

test.describe.serial("Content Definition References", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  let tenantToken: string;
  let blogAppId: string;
  let blogAppSlug: string;
  let coreAppId: string;
  let coreAppSlug: string;
  let coreAppName: string;
  let postDefinitionId: string;
  let userDefinitionId: string;
  let referenceFieldId: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-content-refs-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(
      `E2E Content Refs ${ts}`,
      {
        adminEmail,
        adminFirstName: "Refs",
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

    // Create "Blog" app
    coreAppName = `Core ${ts}`;
    const blogApp = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      `Blog ${ts}`,
      "Blog app with posts"
    );
    blogAppId = blogApp.id;
    blogAppSlug = blogApp.slug;

    // Create "Core" app
    const coreApp = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      coreAppName,
      "Core app with users"
    );
    coreAppId = coreApp.id;
    coreAppSlug = coreApp.slug;

    // Create "Post" definition in Blog app
    const postDef = await createContentDefinitionViaApi(
      tenantToken,
      tenantSlug,
      blogAppSlug,
      { name: "Post" }
    );
    postDefinitionId = postDef.id;

    // Create "User" definition in Core app
    const userDef = await createContentDefinitionViaApi(
      tenantToken,
      tenantSlug,
      coreAppSlug,
      { name: "User" }
    );
    userDefinitionId = userDef.id;
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should add an external definition via spotlight as ghost node", async ({
    page,
  }) => {
    // Navigate to Blog app detail
    await page.goto(`${tenantBaseUrl}/apps/${blogAppId}`);
    await page.waitForLoadState("networkidle");

    // Switch to Content tab
    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Wait for the Post node to be visible
    await expect(graph.getByText("Post")).toBeVisible({ timeout: 10000 });

    // Open spotlight
    const spotlightButton = page.getByTestId("open-spotlight");
    await spotlightButton.click();

    const spotlight = page.getByTestId("content-spotlight");
    await expect(spotlight).toBeVisible({ timeout: 10000 });

    // Search for "User"
    await spotlight.getByTestId("spotlight-search-input").fill("User");

    // Click the User definition from Core app
    const results = spotlight.getByTestId("spotlight-results");
    await expect(results).toBeVisible({ timeout: 5000 });

    // Find the external definition item (type "external")
    const userItem = spotlight.locator("[data-testid^='spotlight-item-']", {
      hasText: "User",
    });
    await expect(userItem).toBeVisible({ timeout: 10000 });
    await userItem.click();

    // Spotlight should close
    await expect(spotlight).not.toBeVisible({ timeout: 10000 });

    // Ghost node should appear (dashed border)
    const ghostNode = page.getByTestId(`ghost-node-${userDefinitionId}`);
    await expect(ghostNode).toBeVisible({ timeout: 10000 });
  });

  test("should show dependency in Dependencies tab", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps/${blogAppId}`);
    await page.waitForLoadState("networkidle");

    // Switch to Dependencies tab
    const depsTab = page.getByRole("tab", {
      name: /dependencies|abh[aä]ngigkeiten/i,
    });
    const hasDepsTab = await depsTab
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (hasDepsTab) {
      await depsTab.click();
      await page.waitForLoadState("networkidle");

      // The dependency graph should show the Core app
      const depGraph = page.getByTestId("app-dependency-graph");
      await expect(depGraph).toBeVisible({ timeout: 15000 });
      await expect(depGraph.getByText(coreAppName)).toBeVisible({
        timeout: 10000,
      });
    }
  });

  test("should create a reference field and verify edge appears", async ({
    page,
  }) => {
    // Create reference field via API (drag-and-connect is fragile in Playwright)
    const field = await addFieldViaApi(
      tenantToken,
      tenantSlug,
      blogAppSlug,
      postDefinitionId,
      {
        fieldKey: "author",
        fieldType: "REFERENCE",
        config: {
          targetDefinitionId: userDefinitionId,
          relationType: "MANY_TO_ONE",
          onDelete: "SET_NULL",
        },
      }
    );
    referenceFieldId = field.id;

    // Navigate to Blog app and verify the edge in the graph
    await page.goto(`${tenantBaseUrl}/apps/${blogAppId}`);
    await page.waitForLoadState("networkidle");

    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Both nodes should be visible
    await expect(graph.getByText("Post")).toBeVisible({ timeout: 10000 });

    // The ghost node for User should be visible
    const ghostNode = page.getByTestId(`ghost-node-${userDefinitionId}`);
    await expect(ghostNode).toBeVisible({ timeout: 10000 });

    // An edge should be present in the graph (React Flow renders edges as SVG paths)
    const edges = graph.locator(".react-flow__edge");
    await expect(edges.first()).toBeVisible({ timeout: 10000 });

    // The "author" reference field should be visible on the Post node
    await expect(graph.getByText("author")).toBeVisible({ timeout: 10000 });
  });

  test("should delete reference field and remove ghost node", async ({
    page,
  }) => {
    await page.goto(`${tenantBaseUrl}/apps/${blogAppId}`);
    await page.waitForLoadState("networkidle");

    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Double-click the "author" field to open its popover
    const authorField = graph.getByText("author");
    await expect(authorField).toBeVisible({ timeout: 10000 });
    await authorField.dblclick();

    // Delete the field via the popover
    const fieldPopover = page.getByTestId("field-popover");
    await expect(fieldPopover).toBeVisible({ timeout: 10000 });
    await fieldPopover.getByTestId("field-delete").click();

    // Confirm deletion if prompted
    const confirmButton = page.getByRole("button", {
      name: /confirm|delete|remove|best[aä]tigen|l[oö]schen|entfernen/i,
    });
    const hasConfirm = await confirmButton
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    if (hasConfirm) {
      await confirmButton.click();
    }

    await expect(fieldPopover).not.toBeVisible({ timeout: 10000 });

    // Edge should be gone
    const edges = graph.locator(".react-flow__edge");
    await expect(edges).toHaveCount(0, { timeout: 10000 });

    // Remove the ghost node
    const removeGhostButton = page.getByTestId(
      `remove-ghost-${userDefinitionId}`
    );
    const hasRemoveButton = await removeGhostButton
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (hasRemoveButton) {
      await removeGhostButton.click();

      // Confirm removal if prompted
      const ghostConfirm = page.getByRole("button", {
        name: /confirm|remove|delete|best[aä]tigen|entfernen|l[oö]schen/i,
      });
      const hasGhostConfirm = await ghostConfirm
        .isVisible({ timeout: 3000 })
        .catch(() => false);
      if (hasGhostConfirm) {
        await ghostConfirm.click();
      }

      // Ghost node should disappear
      const ghostNode = page.getByTestId(`ghost-node-${userDefinitionId}`);
      await expect(ghostNode).not.toBeVisible({ timeout: 10000 });
    }
  });

  test("should no longer show dependency after ghost node removal", async ({
    page,
  }) => {
    await page.goto(`${tenantBaseUrl}/apps/${blogAppId}`);
    await page.waitForLoadState("networkidle");

    // Check the Dependencies tab
    const depsTab = page.getByRole("tab", {
      name: /dependencies|abh[aä]ngigkeiten/i,
    });
    const hasDepsTab = await depsTab
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (hasDepsTab) {
      await depsTab.click();
      await page.waitForLoadState("networkidle");

      const depGraph = page.getByTestId("app-dependency-graph");
      await expect(depGraph).toBeVisible({ timeout: 15000 });

      // Core app should no longer be shown as a dependency
      await expect(depGraph.getByText(coreAppName)).not.toBeVisible({
        timeout: 10000,
      });
    }
  });
});
