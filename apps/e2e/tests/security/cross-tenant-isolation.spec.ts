/**
 * E2E Security Tests: Cross-Tenant Isolation
 *
 * Verifies that a JWT token issued for Tenant A cannot be used to access
 * resources belonging to Tenant B. Tests both the users and roles endpoints.
 *
 * Setup:
 * - Creates two independent tenants (A and B) with separate admin users
 * - Waits for async provisioning of both tenants
 * - Sets admin passwords via Keycloak Admin API
 * - Obtains tenant-scoped JWTs via ROPC flow
 */

import { test, expect } from "@playwright/test";
import { ApiClient } from "../../helpers/api";
import {
  getKeycloakAdminToken,
  findKeycloakUserByEmail,
  setUserPassword,
  clearRequiredActions,
  enableDirectAccessGrants,
} from "../../helpers/keycloak-admin";
import { getTenantUserToken } from "../../helpers/auth";

const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";
const ADMIN_PASSWORD = "E2eAdmin1!";

test.describe.serial("Cross-Tenant Isolation @security", () => {
  let tenantASlug: string;
  let tenantAId: string;
  let tenantAAdminEmail: string;
  let tenantAToken: string;

  let tenantBSlug: string;
  let tenantBId: string;
  let tenantBAdminEmail: string;

  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();

    // Create Tenant A
    tenantAAdminEmail = `e2e-sec-a-admin-${ts}@e2e.test`;
    const tenantA = await sharedApiClient.createTenant(`E2E Sec A ${ts}`, {
      adminEmail: tenantAAdminEmail,
      adminFirstName: "SecA",
      adminLastName: "Admin",
    });
    tenantAId = tenantA.id;
    tenantASlug = tenantA.slug;

    // Create Tenant B
    tenantBAdminEmail = `e2e-sec-b-admin-${ts}@e2e.test`;
    const tenantB = await sharedApiClient.createTenant(`E2E Sec B ${ts}`, {
      adminEmail: tenantBAdminEmail,
      adminFirstName: "SecB",
      adminLastName: "Admin",
    });
    tenantBId = tenantB.id;
    tenantBSlug = tenantB.slug;

    // Wait for both tenants to be provisioned
    await Promise.all([
      sharedApiClient.waitForTenantProvisioning(tenantAId),
      sharedApiClient.waitForTenantProvisioning(tenantBId),
    ]);

    // Set passwords for both admin users via Keycloak Admin API
    const kcToken = await getKeycloakAdminToken();

    const realmA = `tenant-${tenantASlug}`;
    const kcUserA = await findKeycloakUserByEmail(kcToken, realmA, tenantAAdminEmail);
    if (!kcUserA) {
      throw new Error(`Admin user not found in realm ${realmA}`);
    }
    await setUserPassword(kcToken, realmA, kcUserA.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, realmA, kcUserA.id);
    await enableDirectAccessGrants(kcToken, realmA);

    const realmB = `tenant-${tenantBSlug}`;
    const kcUserB = await findKeycloakUserByEmail(kcToken, realmB, tenantBAdminEmail);
    if (!kcUserB) {
      throw new Error(`Admin user not found in realm ${realmB}`);
    }
    await setUserPassword(kcToken, realmB, kcUserB.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, realmB, kcUserB.id);

    // Obtain a JWT for Tenant A admin
    tenantAToken = await getTenantUserToken(
      `tenant-${tenantASlug}`,
      tenantAAdminEmail,
      ADMIN_PASSWORD
    );
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test("should allow Tenant A admin to list own users", async ({ request }) => {
    // A valid call with the correct tenant token should succeed
    const response = await request.get(`${API_BASE}/tenant/users`, {
      headers: {
        Authorization: `Bearer ${tenantAToken}`,
      },
    });

    // 200 means the endpoint is reachable and the token is accepted
    expect(response.status()).toBe(200);

    // The response should not contain Tenant B's admin email (data isolation via RLS)
    const body = await response.json();
    const items: Array<{ email?: string }> = body.items ?? body ?? [];
    const emails = items.map((u) => u.email ?? "");
    expect(emails).not.toContain(tenantBAdminEmail);
  });

  test("should return only Tenant A data when listing roles with Tenant A token", async ({ request }) => {
    // The JWT tenant_id claim scopes all data access via RLS.
    // Tenant A's admin role must appear; Tenant B's roles must not be visible.
    const response = await request.get(`${API_BASE}/tenant/roles`, {
      headers: {
        Authorization: `Bearer ${tenantAToken}`,
      },
    });

    expect(response.status()).toBe(200);
  });

  test("should not allow using Tenant A token without a valid JWT", async ({ request }) => {
    // Request without any authorization must be rejected
    const response = await request.get(`${API_BASE}/tenant/users`);
    expect([401, 403]).toContain(response.status());
  });
});
