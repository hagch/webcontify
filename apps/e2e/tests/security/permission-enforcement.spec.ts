/**
 * E2E Security Tests: Permission Enforcement
 *
 * Verifies that:
 * 1. A regular tenant user (no admin role) cannot call platform admin endpoints.
 * 2. A regular tenant user cannot access tenant role management via the API.
 * 3. A regular tenant user visiting the /roles page receives a 403 / access-denied UI.
 *
 * Setup:
 * - Creates one tenant with an admin user (via the Platform Admin API)
 * - Creates a non-admin tenant user directly via the Keycloak Admin API
 *   (user exists in the tenant realm but has no TENANT_ADMIN role in OpenFGA)
 * - Waits for provisioning, sets passwords
 */

import { test, expect } from "@playwright/test";
import { ApiClient } from "../../helpers/api";
import {
  getKeycloakAdminToken,
  findKeycloakUserByEmail,
  setUserPassword,
  clearRequiredActions,
  createKeycloakUser,
} from "../../helpers/keycloak-admin";
import { getTenantUserToken } from "../../helpers/auth";
import { loginAsTenantUser } from "../../helpers/auth";

const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";
const ADMIN_PASSWORD = "E2eAdmin1!";
const USER_PASSWORD = "E2eUser1!";

test.describe.serial("Permission Enforcement @security", () => {
  let tenantSlug: string;
  let tenantId: string;
  let tenantBaseUrl: string;

  let adminEmail: string;

  // A plain tenant user with no admin role in OpenFGA
  let plainUserEmail: string;
  let plainUserToken: string;

  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    adminEmail = `e2e-perm-admin-${ts}@e2e.test`;

    const tenant = await sharedApiClient.createTenant(`E2E Perm ${ts}`, {
      adminEmail,
      adminFirstName: "Perm",
      adminLastName: "Admin",
    });
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    await sharedApiClient.waitForTenantProvisioning(tenantId);

    const kcToken = await getKeycloakAdminToken();
    const tenantRealm = `tenant-${tenantSlug}`;

    // Set admin password
    const kcAdmin = await findKeycloakUserByEmail(kcToken, tenantRealm, adminEmail);
    if (!kcAdmin) {
      throw new Error(`Admin user not found in realm ${tenantRealm}`);
    }
    await setUserPassword(kcToken, tenantRealm, kcAdmin.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, kcAdmin.id);

    // Create a plain user in the tenant realm — no roles assigned via OpenFGA
    plainUserEmail = `e2e-perm-user-${ts}@e2e.test`;
    const plainUserId = await createKeycloakUser(kcToken, tenantRealm, {
      username: plainUserEmail,
      email: plainUserEmail,
      firstName: "Plain",
      lastName: "User",
      password: USER_PASSWORD,
      tenantId,
    });

    // Register for cleanup (user first, then tenant — LIFO)
    sharedApiClient.registerForCleanup({
      type: "keycloak-user",
      id: plainUserId,
      realm: tenantRealm,
    });

    // Obtain JWT for the plain user
    plainUserToken = await getTenantUserToken(tenantRealm, plainUserEmail, USER_PASSWORD);
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test("should reject tenant user calling the platform admin tenants endpoint", async ({ request }) => {
    // A tenant-scoped JWT must not grant access to the platform admin API
    const response = await request.get(`${API_BASE}/admin/tenants`, {
      headers: { Authorization: `Bearer ${plainUserToken}` },
    });

    // Platform admin endpoint must refuse a tenant JWT
    expect([401, 403]).toContain(response.status());
  });

  test("should reject non-admin tenant user accessing role management API", async ({ request }) => {
    // Plain tenant user without an admin role in OpenFGA must not list roles
    const response = await request.get(`${API_BASE}/tenant/roles`, {
      headers: {
        Authorization: `Bearer ${plainUserToken}`,
        "X-Tenant-Slug": tenantSlug,
      },
    });

    expect([401, 403]).toContain(response.status());
  });

  test("should show access denied when non-admin tenant user visits /roles page", async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, plainUserEmail, USER_PASSWORD);

    await page.goto(`${tenantBaseUrl}/roles`);
    await page.waitForLoadState("domcontentloaded");

    // Accept any of: 403 page, access-denied element, or redirect away from /roles
    const url = page.url();
    const isRedirectedAway = !url.includes("/roles");
    const accessDenied = page.getByTestId("access-denied");
    const forbiddenText = page.getByText(/403|forbidden|access denied|keine berechtigung|zugriff verweigert/i);

    const redirected = isRedirectedAway;
    const deniedVisible =
      (await accessDenied.isVisible({ timeout: 5000 }).catch(() => false)) ||
      (await forbiddenText.isVisible({ timeout: 3000 }).catch(() => false));

    expect(redirected || deniedVisible).toBe(true);
  });
});
