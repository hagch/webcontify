/**
 * E2E Security Tests: Unauthorized Access
 *
 * Verifies that:
 * 1. Unauthenticated requests to protected API endpoints receive 401.
 * 2. Requests with an invalid / malformed JWT receive 401.
 * 3. Unauthenticated browser navigation to a protected page redirects to Keycloak.
 *
 * No tenant setup required — all tests use the API or browser directly without
 * a valid token.
 */

import { test, expect } from "@playwright/test";

const API_BASE = process.env.API_URL ?? "http://localhost:8080/api/v1";

test.describe("Unauthorized Access @security", () => {
  test("should return 401 for unauthenticated request to tenant users endpoint", async ({ request }) => {
    const response = await request.get(`${API_BASE}/tenant/users`);
    expect(response.status()).toBe(401);
  });

  test("should return 401 for unauthenticated request to tenant roles endpoint", async ({ request }) => {
    const response = await request.get(`${API_BASE}/tenant/roles`);
    expect(response.status()).toBe(401);
  });

  test("should return 401 for unauthenticated request to platform admin tenants endpoint", async ({ request }) => {
    const response = await request.get(`${API_BASE}/admin/tenants`);
    expect(response.status()).toBe(401);
  });

  test("should return 401 for API call with a structurally invalid JWT", async ({ request }) => {
    const response = await request.get(`${API_BASE}/tenant/users`, {
      headers: { Authorization: "Bearer invalid.jwt.token" },
    });
    expect(response.status()).toBe(401);
  });

  test("should return 401 for API call with a well-formed but self-signed JWT", async ({ request }) => {
    // A plausibly shaped JWT (three base64 segments) that was not signed by Keycloak
    const fakeHeader = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
    const fakePayload = Buffer.from(
      JSON.stringify({
        sub: "00000000-0000-0000-0000-000000000000",
        iss: "http://auth.localhost/realms/webcontify-platform",
        exp: Math.floor(Date.now() / 1000) + 3600,
      })
    ).toString("base64url");
    const fakeSignature = "fakesignature";
    const fakeToken = `${fakeHeader}.${fakePayload}.${fakeSignature}`;

    const response = await request.get(`${API_BASE}/tenant/users`, {
      headers: { Authorization: `Bearer ${fakeToken}` },
    });
    expect(response.status()).toBe(401);
  });

  test("should redirect unauthenticated page visit to Keycloak login", async ({ page }) => {
    // Navigate to a protected platform page — middleware should redirect
    await page.goto("/tenants");

    // Wait for a redirect either to the Keycloak login page or an internal signin route
    await page.waitForURL(
      (url) =>
        url.href.includes("auth.localhost") || url.href.includes("signin"),
      { timeout: 15000 }
    );

    // The resulting URL must not be the original protected page
    expect(page.url()).not.toMatch(/\/tenants$/);
  });
});
