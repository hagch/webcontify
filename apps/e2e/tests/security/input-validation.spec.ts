/**
 * E2E Security Tests: Input Validation
 *
 * Verifies that:
 * 1. SQL injection payloads in role names are handled safely (no 500).
 * 2. XSS payloads in tenant names are handled safely (no 500).
 * 3. Oversized input is rejected with a 400 validation error.
 *
 * "Safely handled" means either:
 * - 201: the value was accepted and stored without being executed (proper parameterisation)
 * - 400: the value was rejected by validation (also acceptable)
 * The only unacceptable outcome is a 5xx server error.
 *
 * Setup:
 * - For role name tests: creates one tenant, waits for provisioning, obtains admin JWT
 * - For tenant creation tests: uses the platform admin JWT directly
 */

import { test, expect } from "@playwright/test";
import { ApiClient } from "../../helpers/api";
import {
  getKeycloakAdminToken,
  findKeycloakUserByEmail,
  setUserPassword,
  clearRequiredActions,
} from "../../helpers/keycloak-admin";
import { getTenantUserToken } from "../../helpers/auth";

const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";
const ADMIN_PASSWORD = "E2eAdmin1!";

test.describe.serial("Input Validation @security", () => {
  let tenantSlug: string;
  let tenantId: string;
  let tenantAdminToken: string;

  const sharedApiClient = new ApiClient();

  test.beforeAll(async () => {
    const ts = Date.now();
    const adminEmail = `e2e-input-admin-${ts}@e2e.test`;

    const tenant = await sharedApiClient.createTenant(`E2E Input ${ts}`, {
      adminEmail,
      adminFirstName: "Input",
      adminLastName: "Admin",
    });
    tenantId = tenant.id;
    tenantSlug = tenant.slug;

    await sharedApiClient.waitForTenantProvisioning(tenantId);

    const kcToken = await getKeycloakAdminToken();
    const tenantRealm = `tenant-${tenantSlug}`;
    const kcUser = await findKeycloakUserByEmail(kcToken, tenantRealm, adminEmail);
    if (!kcUser) {
      throw new Error(`Admin user not found in realm ${tenantRealm}`);
    }
    await setUserPassword(kcToken, tenantRealm, kcUser.id, ADMIN_PASSWORD);
    await clearRequiredActions(kcToken, tenantRealm, kcUser.id);

    tenantAdminToken = await getTenantUserToken(
      tenantRealm,
      adminEmail,
      ADMIN_PASSWORD
    );
  });

  test.afterAll(async () => {
    await sharedApiClient.cleanup();
  });

  test("should safely handle SQL injection in role name", async ({ request }) => {
    const sqlInjectionPayload = "'; DROP TABLE tenant_role;--";

    const response = await request.post(`${API_BASE}/tenant/roles`, {
      headers: {
        Authorization: `Bearer ${tenantAdminToken}`,
        "Content-Type": "application/json",
        "X-Tenant-Slug": tenantSlug,
      },
      data: JSON.stringify({
        name: sqlInjectionPayload,
        description: "SQL injection test",
      }),
    });

    // Accepted (safely stored) or rejected by validation — never a server error
    expect(response.status()).not.toBeGreaterThanOrEqual(500);
    expect([201, 400, 409, 422]).toContain(response.status());
  });

  test("should safely handle XSS payload in tenant name", async ({ request }) => {
    const xssPayload = "<script>alert(1)</script>";

    // Use the platform admin token from ApiClient
    const platformToken = await sharedApiClient.getToken();

    const response = await request.post(`${API_BASE}/admin/tenants`, {
      headers: {
        Authorization: `Bearer ${platformToken}`,
        "Content-Type": "application/json",
      },
      data: JSON.stringify({
        name: xssPayload,
        adminEmail: `e2e-xss-${Date.now()}@e2e.test`,
        adminFirstName: "XSS",
        adminLastName: "Test",
      }),
    });

    // Must not be a server error — 201 (stored safely), 400, or 422 (rejected)
    expect(response.status()).not.toBeGreaterThanOrEqual(500);
    expect([201, 400, 409, 422]).toContain(response.status());

    // If a tenant was created with the XSS name, register it for cleanup
    if (response.status() === 201) {
      const data = await response.json();
      sharedApiClient.registerForCleanup({ type: "tenant", id: data.id });
    }
  });

  test("should reject oversized tenant name with 400", async ({ request }) => {
    const oversizedName = "A".repeat(10_000);

    const platformToken = await sharedApiClient.getToken();

    const response = await request.post(`${API_BASE}/admin/tenants`, {
      headers: {
        Authorization: `Bearer ${platformToken}`,
        "Content-Type": "application/json",
      },
      data: JSON.stringify({
        name: oversizedName,
        adminEmail: `e2e-oversize-${Date.now()}@e2e.test`,
        adminFirstName: "Over",
        adminLastName: "Size",
      }),
    });

    // Oversized input must be rejected — never accepted or cause a server error
    expect(response.status()).toBe(400);
  });

  test("should reject oversized role name with 400", async ({ request }) => {
    const oversizedName = "B".repeat(10_000);

    const response = await request.post(`${API_BASE}/tenant/roles`, {
      headers: {
        Authorization: `Bearer ${tenantAdminToken}`,
        "Content-Type": "application/json",
        "X-Tenant-Slug": tenantSlug,
      },
      data: JSON.stringify({
        name: oversizedName,
        description: "Oversized name test",
      }),
    });

    expect(response.status()).toBe(400);
  });
});
