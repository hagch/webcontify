import { test, expect } from "../../helpers/fixtures";
import {
  createKeycloakUser,
  getKeycloakAdminToken,
} from "../../helpers/keycloak-admin";
import { ApiClient, type TenantData } from "../../helpers/api";

const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";

/**
 * Polls GET /api/v1/admin/tenants/{id} until status is "ACTIVE".
 * Retries every 1 s, times out after 30 s.
 */
async function waitForTenantProvisioning(
  apiClient: ApiClient,
  tenantId: string,
  timeoutMs = 30_000
): Promise<void> {
  const token = await apiClient.getToken();
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const response = await fetch(`${API_BASE}/admin/tenants/${tenantId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!response.ok) {
      throw new Error(
        `Failed to fetch tenant status: ${response.status} ${await response.text()}`
      );
    }

    const tenant: TenantData = await response.json();

    if (tenant.status === "ACTIVE") {
      return;
    }

    // Wait 1 s before next poll
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }

  throw new Error(
    `Tenant ${tenantId} did not reach ACTIVE status within ${timeoutMs}ms`
  );
}

// Serial because beforeAll/afterAll share state across tests in this describe block
test.describe.serial("Tenant User Login @smoke", () => {
  let tenantSlug: string;
  let tenantId: string;
  const testUsername = "e2e-tenant-user";
  const testPassword = "E2ePassword1!";
  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    // Create a tenant via the admin API
    const tenant = await sharedApiClient.createTenant("E2E Tenant Login Test");
    tenantId = tenant.id;
    tenantSlug = tenant.slug;

    // Wait for async provisioning to complete (Keycloak realm created, etc.)
    await waitForTenantProvisioning(sharedApiClient, tenantId);

    // Tenant realm follows the convention "tenant-{slug}"
    const tenantRealm = `tenant-${tenantSlug}`;

    // Create a user in the tenant's Keycloak realm
    const adminToken = await getKeycloakAdminToken();
    const userId = await createKeycloakUser(adminToken, tenantRealm, {
      username: testUsername,
      email: `${testUsername}@e2e.test`,
      firstName: "E2E",
      lastName: "User",
      password: testPassword,
      tenantId,
    });

    // Register the Keycloak user for cleanup (before the tenant so it is deleted first)
    sharedApiClient.registerForCleanup({
      type: "keycloak-user",
      id: userId,
      realm: tenantRealm,
    });
  });

  test("should login as tenant user via tenant subdomain", async ({ page }) => {
    const tenantBaseUrl = (
      process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
    ).replace("{slug}", tenantSlug);

    // Navigate to the tenant subdomain — triggers Keycloak redirect to tenant realm
    await page.goto(`${tenantBaseUrl}/dashboard`);
    await page.waitForLoadState("domcontentloaded");

    // Should have been redirected to Keycloak
    await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
    await page.locator("#username").fill(testUsername);
    await page.locator("#password").fill(testPassword);
    await page.locator("#kc-login").click();

    // Wait until redirect back to tenant app completes
    await page.waitForURL((url) => !url.href.includes("auth.localhost"), {
      timeout: 20000,
    });
    await page.waitForLoadState("networkidle");

    // Should land on /dashboard of the tenant subdomain
    expect(page.url()).toContain("/dashboard");
    expect(page.url()).toContain(tenantSlug);
  });

  test.afterAll(async () => {
    // Cleanup: removes the Keycloak user first, then the tenant (LIFO stack)
    await sharedApiClient.cleanup();
  });
});
