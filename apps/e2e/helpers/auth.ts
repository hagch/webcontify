import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";

/**
 * Keycloak URL for admin access (via Traefik, port 80).
 * Locally: auth.localhost (no port, Traefik routes through).
 */
export const KEYCLOAK_URL =
  process.env.KEYCLOAK_URL ?? "http://auth.localhost";

/**
 * Obtains a JWT access token for a tenant user via the Keycloak Resource Owner
 * Password Credentials flow. The token is issued by the tenant-specific realm.
 *
 * @param realm   - Keycloak realm name, e.g. "tenant-my-company"
 * @param username - Username of the tenant user
 * @param password - Password of the tenant user
 * @returns JWT access_token string
 */
export async function getTenantUserToken(
  realm: string,
  username: string,
  password: string
): Promise<string> {
  const response = await fetch(
    `${KEYCLOAK_URL}/realms/${realm}/protocol/openid-connect/token`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "password",
        client_id: "tenant-ui",
        username,
        password,
      }),
    }
  );
  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      `Failed to get tenant user token (realm: ${realm}, user: ${username}): ${response.status} ${body}`
    );
  }
  const data = await response.json();
  return data.access_token as string;
}

/**
 * Platform realm name.
 */
export const KEYCLOAK_REALM =
  process.env.KEYCLOAK_REALM ?? "webcontify-platform";

/**
 * Logs in a user via the Keycloak login form.
 *
 * Flow:
 * 1. Navigates to the protected page at targetPath (default: /tenants)
 * 2. Middleware redirects directly to /api/auth/signin/keycloak
 * 3. NextAuth starts PKCE flow and redirects to Keycloak login
 * 4. Fills in username + password
 * 5. Waits for redirect back to the app
 *
 * Already logged in: If the user is already authenticated,
 * no Keycloak redirect is triggered and the function returns immediately.
 */
export async function loginAsKeycloak(
  page: Page,
  username: string,
  password: string,
  targetPath: string = "/tenants"
): Promise<void> {
  // Navigate to the protected page — triggers auth redirect
  await page.goto(targetPath);

  // Wait briefly for potential redirects
  await page.waitForLoadState("domcontentloaded");

  const currentUrl = page.url();

  // Already logged in: no Keycloak redirect
  if (!currentUrl.includes("auth.localhost")) {
    await page.waitForLoadState("networkidle");
    return;
  }

  // Fill in the Keycloak login form.
  // Middleware redirects directly to Keycloak (no NextAuth intermediate step).
  // Selectors: standard Keycloak theme uses #username and #password
  await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);

  // Login button: Keycloak standard theme uses #kc-login
  await page.locator("#kc-login").click();

  // Wait until redirect back to the app completes
  await page.waitForURL(
    (url) => !url.href.includes("auth.localhost"),
    { timeout: 20000 }
  );

  // Verify we are on the app page
  await page.waitForLoadState("networkidle");
}

/**
 * Logs in the platform admin (admin/admin).
 */
export async function loginAsAdmin(page: Page): Promise<void> {
  await loginAsKeycloak(page, "admin", "admin", "/tenants");
}

/**
 * Logs in a tenant user by navigating to the tenant subdomain.
 *
 * Uses TENANT_BASE_URL_TEMPLATE to construct the subdomain URL.
 * Template placeholder: {slug} is replaced with the actual tenant slug.
 */
export async function loginAsTenantUser(
  page: Page,
  slug: string,
  username: string,
  password: string
): Promise<void> {
  const tenantBaseUrl = (
    process.env.TENANT_BASE_URL_TEMPLATE ?? "http://{slug}.localhost"
  ).replace("{slug}", slug);

  // Navigate to the tenant subdomain — triggers auth redirect
  await page.goto(tenantBaseUrl + "/dashboard");

  // Wait briefly for potential redirects
  await page.waitForLoadState("domcontentloaded");

  const currentUrl = page.url();

  // Already logged in: no Keycloak redirect
  if (!currentUrl.includes("auth.localhost")) {
    await page.waitForLoadState("networkidle");
    return;
  }

  // Fill in Keycloak login form
  await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);

  await page.locator("#kc-login").click();

  // Wait until redirect back to the tenant app completes
  await page.waitForURL(
    (url) => !url.href.includes("auth.localhost"),
    { timeout: 20000 }
  );

  await page.waitForLoadState("networkidle");
}
