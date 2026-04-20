import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";

/**
 * Creates a tenant via the platform UI and returns the tenant ID extracted from the URL.
 *
 * Flow:
 * 1. Navigates to /tenants
 * 2. Clicks the "Create Tenant" button
 * 3. Fills in Name, admin email, first name, last name
 * 4. Clicks Submit — dialog closes and app redirects to /tenants/{id}
 * 5. Returns the tenant ID extracted from the URL
 */
export async function createTenantViaUI(
  page: Page,
  name: string,
  adminEmail?: string
): Promise<string> {
  await page.goto("/tenants");
  await page.waitForLoadState("networkidle");

  await page.getByRole("button", { name: "Create Tenant" }).click();

  const dialog = page.getByTestId("create-tenant-dialog");
  await expect(dialog).toBeVisible();

  await page.getByTestId("tenant-name-input").fill(name);

  // Admin fields are required
  const email = adminEmail ?? `e2e+${Date.now()}@example.com`;
  await dialog.getByLabel("E-Mail").fill(email);
  await dialog.getByLabel("Vorname").fill("E2E");
  await dialog.getByLabel("Nachname").fill("Test");

  await page.getByTestId("create-tenant-submit").click();

  // Dialog closes and app redirects to tenant detail page
  await expect(dialog).not.toBeVisible({ timeout: 10000 });
  await page.waitForURL(/\/tenants\/[^/]+$/, { timeout: 15000 });

  const match = page.url().match(/\/tenants\/([^/]+)/);
  if (!match) {
    throw new Error(
      `Failed to extract tenant ID from URL: ${page.url()}`
    );
  }

  return match[1];
}
