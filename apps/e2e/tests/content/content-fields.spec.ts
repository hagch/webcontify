/**
 * E2E Tests: Content Definition Field Management
 *
 * Tests adding, editing, and deleting fields on a content definition:
 * 1. Add a TEXT field "title" with Required + maxLength 200
 * 2. Verify the field appears on the node
 * 3. Add another TEXT field "content"
 * 4. Edit "title" field — change maxLength to 255
 * 5. Delete "content" field
 * 6. Verify only "title" remains
 *
 * Setup:
 * - Creates a tenant, app, and content definition via API
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
import { createContentDefinitionViaApi } from "../../helpers/content-api";

const ADMIN_PASSWORD = "E2eAdmin1!";

test.describe.serial("Content Definition Field Management", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  let tenantToken: string;
  let appId: string;
  let appSlug: string;
  let definitionId: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-content-fields-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(
      `E2E Fields ${ts}`,
      {
        adminEmail,
        adminFirstName: "Fields",
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

    // Create an app
    const app = await sharedApiClient.createApp(
      tenantToken,
      tenantSlug,
      `Fields Test App ${ts}`,
      "App for field management E2E tests"
    );
    appId = app.id;
    appSlug = app.slug;

    // Create a content definition via API
    const def = await createContentDefinitionViaApi(
      tenantToken,
      tenantSlug,
      appSlug,
      { name: "Article" }
    );
    definitionId = def.id;
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should add a TEXT field with Required and maxLength", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps/${appId}`);
    await page.waitForLoadState("networkidle");

    // Switch to Content tab
    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Wait for the definition node to appear
    await expect(graph.getByText("Article")).toBeVisible({ timeout: 10000 });

    // Click the "+" (add field) button on the node
    const addFieldButton = page.getByTestId(`add-field-${definitionId}`);
    await expect(addFieldButton).toBeVisible({ timeout: 10000 });
    await addFieldButton.click();

    // Add field popover should appear
    const popover = page.getByTestId("add-field-popover");
    await expect(popover).toBeVisible({ timeout: 10000 });

    // Select TEXT type (should be default, but explicitly set it)
    await popover.getByTestId("add-field-type-select").click();
    await page.getByRole("option", { name: /text/i }).click();

    // Type field key
    await popover.getByTestId("add-field-key-input").fill("title");

    // Toggle Required on
    const requiredCheckbox = popover.getByLabel(/required/i);
    const isChecked = await requiredCheckbox.isChecked();
    if (!isChecked) {
      await requiredCheckbox.click();
    }

    // Set maxLength in the config section
    const maxLengthInput = popover.getByLabel(/max.?length/i);
    const hasMaxLength = await maxLengthInput
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    if (hasMaxLength) {
      await maxLengthInput.fill("200");
    }

    // Submit
    await popover.getByTestId("add-field-submit").click();

    // Popover should close
    await expect(popover).not.toBeVisible({ timeout: 10000 });

    // Field should be visible on the node
    await expect(graph.getByText("title")).toBeVisible({ timeout: 10000 });
  });

  test("should add a second field", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps/${appId}`);
    await page.waitForLoadState("networkidle");

    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });
    await expect(graph.getByText("Article")).toBeVisible({ timeout: 10000 });

    // Click add field
    const addFieldButton = page.getByTestId(`add-field-${definitionId}`);
    await addFieldButton.click();

    const popover = page.getByTestId("add-field-popover");
    await expect(popover).toBeVisible({ timeout: 10000 });

    // Select TEXT type
    await popover.getByTestId("add-field-type-select").click();
    await page.getByRole("option", { name: /text/i }).click();

    // Type field key
    await popover.getByTestId("add-field-key-input").fill("content");

    // Submit
    await popover.getByTestId("add-field-submit").click();
    await expect(popover).not.toBeVisible({ timeout: 10000 });

    // Both fields should be visible
    await expect(graph.getByText("title")).toBeVisible({ timeout: 10000 });
    await expect(graph.getByText("content")).toBeVisible({ timeout: 10000 });
  });

  test("should edit a field via the field popover", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps/${appId}`);
    await page.waitForLoadState("networkidle");

    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Double-click the "title" field row to open the edit popover
    const titleField = graph.getByText("title");
    await expect(titleField).toBeVisible({ timeout: 10000 });
    await titleField.dblclick();

    // Edit field popover should appear
    const popover = page.getByTestId("field-popover");
    await expect(popover).toBeVisible({ timeout: 10000 });

    // Change maxLength to 255
    const maxLengthInput = popover.getByLabel(/max.?length/i);
    const hasMaxLength = await maxLengthInput
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    if (hasMaxLength) {
      await maxLengthInput.clear();
      await maxLengthInput.fill("255");
    }

    // Save
    await popover.getByTestId("field-save").click();
    await expect(popover).not.toBeVisible({ timeout: 10000 });
  });

  test("should delete a field", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/apps/${appId}`);
    await page.waitForLoadState("networkidle");

    const contentTab = page.getByRole("tab", { name: /content/i });
    await contentTab.click();

    const graph = page.getByTestId("content-definition-graph");
    await expect(graph).toBeVisible({ timeout: 15000 });

    // Double-click the "content" field to open its popover
    const contentField = graph.getByText("content");
    await expect(contentField).toBeVisible({ timeout: 10000 });
    await contentField.dblclick();

    const popover = page.getByTestId("field-popover");
    await expect(popover).toBeVisible({ timeout: 10000 });

    // Click the delete button in the field popover
    await popover.getByTestId("field-delete").click();

    // Confirm deletion if a confirmation dialog appears
    const confirmButton = page.getByRole("button", {
      name: /confirm|delete|remove|best[aä]tigen|l[oö]schen|entfernen/i,
    });
    const hasConfirm = await confirmButton
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    if (hasConfirm) {
      await confirmButton.click();
    }

    // Popover should close
    await expect(popover).not.toBeVisible({ timeout: 10000 });

    // "content" field should be gone, "title" should remain
    await expect(graph.getByText("title")).toBeVisible({ timeout: 10000 });
    // Use a more specific locator to avoid matching "content" in other parts of the page
    const contentFieldRows = graph.locator("[data-testid^='field-row-']", {
      hasText: "content",
    });
    await expect(contentFieldRows).toHaveCount(0, { timeout: 10000 });
  });
});
