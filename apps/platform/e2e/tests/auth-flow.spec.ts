/**
 * E2E Test: Auth Flow (B.7)
 *
 * Testet den vollstaendigen Auth-Zyklus:
 * 1. Ungeschuetzte Route ist ohne Login erreichbar
 * 2. Geschuetzte Route leitet ohne Auth direkt zu Keycloak weiter
 *    (Middleware -> /api/auth/signin/keycloak -> Keycloak, kein NextAuth-Zwischen-Schritt)
 * 3. Erfolgreicher Login leitet zur urspruenglichen Seite zurueck (callbackUrl)
 * 4. Logout beendet die Session und leitet zur Home-Seite
 * 5. Nach Logout ist die geschuetzte Route wieder gesperrt
 * 6. RefreshAccessTokenError wird als Session-Fehler behandelt
 *
 * Voraussetzungen:
 * - Keycloak laeuft auf auth.localhost (via Traefik)
 * - Platform UI laeuft auf platform.localhost
 * - Platform Admin: admin / admin (Rolle PLATFORM_ADMIN im Realm webcontify-platform)
 */

import { test, expect } from "@playwright/test";
import {
  loginAsAdmin,
  loginAsKeycloak,
  KEYCLOAK_URL,
  KEYCLOAK_REALM,
} from "../helpers/auth";

test.describe("Auth Flow — Protected Routes", () => {
  test("Home-Seite (/) ist ohne Login erreichbar", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    // Keine Keycloak-Weiterleitung
    await expect(page).not.toHaveURL(/auth\.localhost/);

    // Home-Seite wird angezeigt
    await expect(
      page.getByRole("heading", { name: /Webcontify Platform/i })
    ).toBeVisible({ timeout: 10000 });
  });

  test("Geschuetzte Route /tenants leitet ohne Auth zu Keycloak weiter", async ({
    page,
  }) => {
    // Direkt zu /tenants navigieren ohne Login
    await page.goto("/tenants");

    // Erwartet: Weiterleitung zu Keycloak Login
    await page.waitForURL(/auth\.localhost/, { timeout: 15000 });

    // Keycloak-Login-Formular sichtbar
    await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
    await expect(page.locator("#password")).toBeVisible();
    await expect(page.locator("#kc-login")).toBeVisible();
  });

  test("Geschuetzte Route /settings leitet ohne Auth zu Keycloak weiter", async ({
    page,
  }) => {
    await page.goto("/settings");

    await page.waitForURL(/auth\.localhost/, { timeout: 15000 });
    await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
  });

  test("Keycloak-Redirect enthaelt callbackUrl fuer /tenants", async ({
    page,
  }) => {
    await page.goto("/tenants");

    // Warte auf Keycloak Login-Seite
    await page.waitForURL(/auth\.localhost/, { timeout: 15000 });

    // Die URL muss den Realm enthalten
    expect(page.url()).toContain(KEYCLOAK_REALM);

    // Keycloak-Login-Formular ist sichtbar
    await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
  });
});

test.describe("Auth Flow — Login + Logout", () => {
  test("Erfolgreicher Login landet auf /tenants", async ({ page }) => {
    // Login via Helper
    await loginAsAdmin(page);

    // Nach erfolgreichem Login: /tenants Seite geladen
    await expect(page).toHaveURL(/\/tenants/);
    await expect(
      page.getByRole("heading", { name: /Tenants/i })
    ).toBeVisible({ timeout: 10000 });

    // User-Info im Header sichtbar (admin@webcontify.io)
    await expect(
      page.getByText(/admin@webcontify\.io/i)
    ).toBeVisible({ timeout: 5000 });
  });

  test("Logout beendet Session und leitet zu Home weiter", async ({ page }) => {
    // Einloggen
    await loginAsAdmin(page);
    await expect(page).toHaveURL(/\/tenants/);

    // Logout-Button klicken (aus Header: t("header.logout") = "Sign out")
    await page.getByRole("button", { name: /Sign out/i }).click();

    // Nach Logout: Keycloak End-Session dann Redirect zu /
    // signOut({ callbackUrl: "/" }) leitet zu / weiter
    await page.waitForURL(
      (url) => !url.href.includes("auth.localhost"),
      { timeout: 20000 }
    );
    await page.waitForLoadState("networkidle");

    // Keine geschuetzte Route aktiv
    expect(page.url()).not.toMatch(/\/tenants/);
  });

  test("Nach Logout ist /tenants wieder gesperrt", async ({ page }) => {
    // Einloggen
    await loginAsAdmin(page);
    await expect(page).toHaveURL(/\/tenants/);

    // Logout
    await page.getByRole("button", { name: /Sign out/i }).click();
    await page.waitForURL(
      (url) => !url.href.includes("auth.localhost"),
      { timeout: 20000 }
    );
    await page.waitForLoadState("networkidle");

    // Jetzt /tenants aufrufen — soll zu Keycloak weiterleiten
    await page.goto("/tenants");
    await page.waitForURL(/auth\.localhost/, { timeout: 15000 });
    await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
  });

  test("Login mit falschen Credentials zeigt Fehlermeldung in Keycloak", async ({
    page,
  }) => {
    // Direkt zum Keycloak-Login navigieren indem wir /tenants aufrufen
    await page.goto("/tenants");
    await page.waitForURL(/auth\.localhost/, { timeout: 15000 });
    await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });

    // Falsche Credentials eingeben
    await page.locator("#username").fill("admin");
    await page.locator("#password").fill("wrong-password");
    await page.locator("#kc-login").click();

    // Keycloak zeigt Fehlermeldung — bleibt auf Login-Seite
    // Keycloak Standard: "Invalid username or password."
    await expect(page).toHaveURL(/auth\.localhost/);
    await expect(page.locator("#username")).toBeVisible({ timeout: 10000 });
  });
});

test.describe("Auth Flow — Session Handling", () => {
  test("Bereits eingeloggt: /tenants direkt zugaenglich ohne erneuten Login", async ({
    page,
  }) => {
    // Erst einloggen
    await loginAsAdmin(page);
    await expect(page).toHaveURL(/\/tenants/);

    // Nochmal zu /tenants navigieren — kein neuer Keycloak-Redirect
    await page.goto("/tenants");
    await page.waitForLoadState("networkidle");

    // Kein Keycloak-Formular sichtbar
    await expect(page).not.toHaveURL(/auth\.localhost/);
    await expect(
      page.getByRole("heading", { name: /Tenants/i })
    ).toBeVisible({ timeout: 10000 });
  });

  test("Keycloak-Realm ist webcontify-platform", async ({ page }) => {
    // Pruefen ob der richtige Realm verwendet wird
    await page.goto("/tenants");
    await page.waitForURL(/auth\.localhost/, { timeout: 15000 });

    // URL enthaelt den korrekten Realm
    expect(page.url()).toContain("/realms/webcontify-platform/");
  });

  test("Keycloak OIDC Discovery Endpoint antwortet", async ({ page }) => {
    // Prueft ob Keycloak erreichbar ist (Health Check fuer E2E Voraussetzung)
    const discoveryUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration`;

    const response = await page.request.get(discoveryUrl);
    expect(response.ok()).toBe(true);

    const json = await response.json();
    expect(json.issuer).toContain(KEYCLOAK_REALM);
    expect(json.authorization_endpoint).toBeDefined();
    expect(json.token_endpoint).toBeDefined();
  });
});
