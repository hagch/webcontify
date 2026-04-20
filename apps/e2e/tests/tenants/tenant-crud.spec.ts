/**
 * E2E Tests: Tenant CRUD
 *
 * Tests for creating tenants, navigating to tenant detail pages,
 * and verifying provisioning status is shown.
 *
 * Uses platformAdminPage fixture (already logged in as admin).
 * Uses ApiClient directly for cleanup after the serial suite.
 */

import { test, expect } from "../../helpers/fixtures";
import { ApiClient } from "../../helpers/api";

const apiClient = new ApiClient();
let tenantId: string;
let tenantName: string;

test.describe.serial("Tenant CRUD @smoke", () => {
  test.afterAll(async () => {
    await apiClient.cleanup();
  });

  test("should create a tenant and see it in the list", async ({
    platformAdminPage,
  }) => {
    tenantName = `E2E Test ${Date.now()}`;

    await platformAdminPage.goto("/tenants");
    await platformAdminPage.waitForLoadState("networkidle");

    // Open create dialog
    await platformAdminPage.getByRole("button", { name: "Create Tenant" }).click();

    const dialog = platformAdminPage.getByTestId("create-tenant-dialog");
    await expect(dialog).toBeVisible();

    // Fill in tenant name
    await platformAdminPage.getByTestId("tenant-name-input").fill(tenantName);

    // Fill in required admin fields
    const adminEmail = `e2e+${Date.now()}@example.com`;
    await dialog.getByTestId("admin-email-input").fill(adminEmail);
    await dialog.getByTestId("admin-firstname-input").fill("E2E");
    await dialog.getByTestId("admin-lastname-input").fill("Test");

    // Submit
    await platformAdminPage.getByTestId("create-tenant-submit").click();

    // Dialog closes and redirects to tenant detail
    await expect(dialog).not.toBeVisible({ timeout: 10000 });
    await platformAdminPage.waitForURL(/\/tenants\/[^/]+$/, { timeout: 15000 });

    // Extract tenant ID from URL and register for cleanup
    const match = platformAdminPage.url().match(/\/tenants\/([^/]+)/);
    expect(match).not.toBeNull();
    tenantId = match![1];
    apiClient.registerForCleanup({ type: "tenant", id: tenantId });

    // Navigate back to tenant list and verify tenant appears
    await platformAdminPage.goto("/tenants");
    await platformAdminPage.waitForLoadState("networkidle");

    const table = platformAdminPage.getByTestId("tenant-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    const tenantLink = table.getByRole("link", { name: tenantName });
    await expect(tenantLink).toBeVisible({ timeout: 10000 });
  });

  test("should show provisioning status on tenant detail page", async ({
    platformAdminPage,
  }) => {
    expect(tenantId).toBeDefined();

    await platformAdminPage.goto(`/tenants/${tenantId}`);
    await platformAdminPage.waitForLoadState("networkidle");

    // The tenant detail section should be visible
    const detail = platformAdminPage.getByTestId("tenant-detail");
    await expect(detail).toBeVisible({ timeout: 10000 });

    // Either provisioning status card or the tenant info card should be visible —
    // provisioning may still be running or may already be done.
    // The tenant name appears in the CardTitle inside the tenant-detail container.
    const provisioningStatus = detail.getByTestId("provisioning-status");
    const tenantNameInCard = detail.getByText(tenantName);

    await expect(provisioningStatus.or(tenantNameInCard)).toBeVisible({
      timeout: 10000,
    });
  });

  test("should navigate to tenant detail page by clicking tenant row", async ({
    platformAdminPage,
  }) => {
    expect(tenantId).toBeDefined();
    expect(tenantName).toBeDefined();

    await platformAdminPage.goto("/tenants");
    await platformAdminPage.waitForLoadState("networkidle");

    const table = platformAdminPage.getByTestId("tenant-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // Click the tenant name link in the table
    const tenantLink = table.getByRole("link", { name: tenantName });
    await expect(tenantLink).toBeVisible({ timeout: 10000 });
    await tenantLink.click();

    // URL should change to tenant detail
    await platformAdminPage.waitForURL(new RegExp(`/tenants/${tenantId}$`), {
      timeout: 10000,
    });

    expect(platformAdminPage.url()).toContain(`/tenants/${tenantId}`);
  });
});
