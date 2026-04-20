/**
 * E2E Tests: User Invitation Flow
 *
 * Tests the invite user flow on the tenant workspace:
 * 1. Tenant admin invites a new user via the dialog
 * 2. The invited user appears in the user table with INVITED status
 *
 * Setup:
 * - Creates a tenant via the Platform Admin API
 * - Waits for async provisioning (Keycloak realm creation)
 * - Uses Keycloak Admin API to set the initial admin password directly
 *   (bypassing the UPDATE_PASSWORD required action from the invite email)
 * - Logs in as the tenant admin on the tenant subdomain
 */

import { test, expect } from "@playwright/test";
import { ApiClient } from "../../helpers/api";
import {
  getKeycloakAdminToken,
  findKeycloakUserByEmail,
  setUserPassword,
  clearRequiredActions,
} from "../../helpers/keycloak-admin";
import { loginAsTenantUser } from "../../helpers/auth";

// Tenant admin credentials set up during beforeAll
const ADMIN_PASSWORD = "E2eAdmin1!";

// Serial: beforeAll/afterAll share state across tests
test.describe.serial("User Invitation Flow @smoke", () => {
  let tenantSlug: string;
  let tenantId: string;
  let adminEmail: string;
  let tenantBaseUrl: string;
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    // 1. Create tenant with a deterministic admin email
    const ts = Date.now();
    adminEmail = `e2e-invite-admin-${ts}@e2e.test`;
    const tenant = await sharedApiClient.createTenant(`E2E Invite ${ts}`, {
      adminEmail,
      adminFirstName: "Invite",
      adminLastName: "Admin",
    });
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    // 2. Wait for provisioning
    await sharedApiClient.waitForTenantProvisioning(tenantId);

    // 3. Set password for the initial admin user directly via Keycloak Admin API
    const kcToken = await getKeycloakAdminToken();
    const tenantRealm = `tenant-${tenantSlug}`;
    const kcUser = await findKeycloakUserByEmail(kcToken, tenantRealm, adminEmail);
    if (!kcUser) {
      throw new Error(
        `Initial admin user not found in Keycloak realm ${tenantRealm} for email ${adminEmail}`
      );
    }
    await setUserPassword(kcToken, tenantRealm, kcUser.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, kcUser.id);
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test.beforeEach(async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, adminEmail, ADMIN_PASSWORD);
  });

  test("should invite a user and see them in the user list", async ({ page }) => {
    const invitedEmail = `e2e-invited-user-${Date.now()}@e2e.test`;

    await page.goto(`${tenantBaseUrl}/users`);
    await page.waitForLoadState("networkidle");

    // Open invite dialog via "Einladen" button
    await page.getByRole("button", { name: "Einladen" }).click();

    const dialog = page.getByTestId("invite-user-dialog");
    await expect(dialog).toBeVisible({ timeout: 10000 });

    // Fill invite form
    await dialog.getByTestId("user-email-input").fill(invitedEmail);
    await dialog.getByTestId("user-firstname-input").fill("Test");
    await dialog.getByTestId("user-lastname-input").fill("User");

    // Select at least one role (roleIds is mandatory)
    const firstRoleCheckbox = dialog.locator('[data-testid^="role-checkbox-"]').first();
    await expect(firstRoleCheckbox).toBeVisible({ timeout: 10000 });
    await firstRoleCheckbox.check();

    // Submit
    await dialog.getByTestId("invite-user-submit").click();

    // Dialog should close
    await expect(dialog).not.toBeVisible({ timeout: 10000 });

    // Invited user should appear in the table
    const table = page.getByTestId("user-table");
    await expect(table).toBeVisible({ timeout: 10000 });
    await expect(table.getByText(invitedEmail)).toBeVisible({ timeout: 10000 });
  });

  test("should show invited user with INVITED status badge", async ({ page }) => {
    await page.goto(`${tenantBaseUrl}/users`);
    await page.waitForLoadState("networkidle");

    const table = page.getByTestId("user-table");
    await expect(table).toBeVisible({ timeout: 10000 });

    // At least one row should show an INVITED badge
    const rows = table.getByTestId("user-table-row");
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThan(0);

    // Find at least one row with an INVITED badge
    let foundInvited = false;
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      if ((await row.getByText(/invited/i).count()) > 0) {
        foundInvited = true;
        break;
      }
    }
    expect(foundInvited).toBe(true);
  });
});
