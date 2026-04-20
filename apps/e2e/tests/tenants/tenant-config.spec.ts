/**
 * E2E Tests: Tenant Config Page
 *
 * Migrated from apps/platform/e2e/tests/tenant-config.spec.ts.
 *
 * Tests for the tenant configuration page at /tenants/{id}/config.
 * This page provides branding, email, and domain configuration tabs
 * for a specific tenant from the platform admin perspective.
 *
 * Prerequisites:
 * - A tenant must exist and be ACTIVE before navigating to config
 * - Tenant is created via API in beforeAll and cleaned up in afterAll
 */

import { test, expect } from "../../helpers/fixtures";
import { ApiClient } from "../../helpers/api";

const apiClient = new ApiClient();
let tenantId: string;

/**
 * Waits until the tenant transitions to ACTIVE status (provisioning is async).
 * Polls the API every 2 seconds for up to 30 seconds.
 */
async function waitForActiveStatus(id: string): Promise<void> {
  const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";
  const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? "http://auth.localhost";

  const tokenRes = await fetch(
    `${KEYCLOAK_URL}/realms/webcontify-platform/protocol/openid-connect/token`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "password",
        client_id: "platform-ui",
        username: process.env.PLATFORM_ADMIN_EMAIL ?? "admin",
        password: process.env.PLATFORM_ADMIN_PASSWORD ?? "admin",
      }),
    }
  );
  const { access_token: token } = await tokenRes.json();

  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const res = await fetch(`${API_BASE}/admin/tenants/${id}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const data = await res.json();
    if (data.status === "ACTIVE") return;
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw new Error(`Tenant ${id} did not become ACTIVE within 30s`);
}

test.describe.serial("Tenant Config", () => {
  test.beforeAll(async () => {
    const tenant = await apiClient.createTenant(`E2E Config ${Date.now()}`);
    tenantId = tenant.id;
    await waitForActiveStatus(tenantId);
  });

  test.afterAll(async () => {
    await apiClient.cleanup();
  });

  test.describe("Tenant Config Page", () => {
    test.beforeEach(async ({ platformAdminPage }) => {
      await platformAdminPage.goto(`/tenants/${tenantId}/config`);
      await platformAdminPage.waitForLoadState("networkidle");
    });

    test("should show branding, email, and domain tabs", async ({
      platformAdminPage,
    }) => {
      await expect(
        platformAdminPage.getByRole("heading", { level: 1 })
      ).toContainText(/configuration/i);

      const tabList = platformAdminPage.getByRole("tablist");
      await expect(tabList).toBeVisible();

      await expect(
        tabList.getByRole("tab", { name: /branding/i })
      ).toBeVisible();
      await expect(
        tabList.getByRole("tab", { name: /email/i })
      ).toBeVisible();
      await expect(
        tabList.getByRole("tab", { name: /domain/i })
      ).toBeVisible();
    });

    test("should save branding configuration", async ({ platformAdminPage }) => {
      // Branding tab should be active by default
      const brandingTab = platformAdminPage.getByRole("tab", {
        name: /branding/i,
      });
      // Base UI tabs use data-active or aria-selected to indicate the active tab
      await expect(brandingTab).toHaveAttribute("aria-selected", "true");

      const primaryInput = platformAdminPage.getByLabel(/primary color/i);
      const secondaryInput = platformAdminPage.getByLabel(/secondary color/i);

      await expect(primaryInput).toBeVisible();
      await expect(secondaryInput).toBeVisible();

      await primaryInput.clear();
      await primaryInput.fill("#3b82f6");
      await secondaryInput.clear();
      await secondaryInput.fill("#64748b");

      await platformAdminPage
        .getByRole("button", { name: /save branding/i })
        .click();

      await expect(
        platformAdminPage.locator("[data-sonner-toast][data-type='success']")
      ).toBeVisible({ timeout: 10000 });
    });
  });
});
