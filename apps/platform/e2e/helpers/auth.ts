import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";

/**
 * Keycloak URL fuer den Admin-Zugriff (via Traefik, Port 80).
 * Lokal: auth.localhost (kein Port, Traefik routet durch).
 */
export const KEYCLOAK_URL =
  process.env.KEYCLOAK_URL ?? "http://auth.localhost";

/**
 * Realm der Platform.
 */
export const KEYCLOAK_REALM =
  process.env.KEYCLOAK_REALM ?? "webcontify-platform";

/**
 * Loggt einen User via Keycloak-Login-Formular ein.
 *
 * Flow:
 * 1. Navigiert zur geschuetzten Seite targetPath (Default: /tenants)
 * 2. Middleware leitet direkt zu /api/auth/signin/keycloak weiter
 * 3. NextAuth startet PKCE-Flow und leitet zu Keycloak Login weiter
 * 4. Fuellt Username + Password aus
 * 5. Wartet auf Redirect zurueck zur App
 *
 * Bereits eingeloggt: Falls der User bereits authentifiziert ist,
 * wird kein Keycloak-Redirect ausgeloest und die Funktion kehrt sofort zurueck.
 */
export async function loginAsKeycloak(
  page: Page,
  username: string,
  password: string,
  targetPath: string = "/tenants"
): Promise<void> {
  // Navigiere zur geschuetzten Seite — loest Auth-Redirect aus
  await page.goto(targetPath);

  // Warte kurz auf potentielle Redirects
  await page.waitForLoadState("domcontentloaded");

  const currentUrl = page.url();

  // Falls bereits eingeloggt: kein Keycloak-Redirect
  if (!currentUrl.includes("auth.localhost")) {
    await page.waitForLoadState("networkidle");
    return;
  }

  // Keycloak Login-Formular ausfullen
  // Middleware leitet direkt zu Keycloak weiter (kein NextAuth-Zwischen-Schritt mehr).
  // Selektoren: Standard Keycloak Theme verwendet #username und #password
  await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);

  // Login-Button: Keycloak Standard Theme verwendet #kc-login
  await page.locator("#kc-login").click();

  // Warte bis Redirect zurueck zur App passiert
  await page.waitForURL(
    (url) => !url.href.includes("auth.localhost"),
    { timeout: 20000 }
  );

  // Verifiziere dass wir auf der App-Seite sind
  await page.waitForLoadState("networkidle");
}

/**
 * Loggt den Platform Admin ein (admin/admin).
 */
export async function loginAsAdmin(page: Page): Promise<void> {
  await loginAsKeycloak(page, "admin", "admin", "/tenants");
}

/**
 * Loggt einen Tenant User ein.
 * Zielseite ist /settings (da /tenants fuer Tenant User nicht zugaenglich waere).
 */
export async function loginAsTenantUser(
  page: Page,
  username: string,
  password: string
): Promise<void> {
  // /settings ist geschuetzt und fuer Tenant User zugaenglich
  await loginAsKeycloak(page, username, password, "/settings");
}
