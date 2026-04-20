/**
 * E2E Test: Auth + Self-Service Branding Flow
 *
 * Testet den vollstaendigen Ablauf:
 * 1. Platform Admin loggt sich via Keycloak ein
 * 2. Platform Admin erstellt einen neuen Tenant
 * 3. Ein Tenant User wird via Keycloak Admin API erstellt (kein User-Management-UI)
 * 4. Tenant User loggt sich ein
 * 5. Tenant User bearbeitet das Branding in den Self-Service Settings
 *
 * Voraussetzungen:
 * - Keycloak laeuft auf auth.localhost (via Traefik)
 * - Backend API laeuft auf api.localhost
 * - Platform UI laeuft auf platform.localhost
 * - Platform Admin: admin / admin (Rolle PLATFORM_ADMIN im Realm webcontify-platform)
 */

import { test, expect } from "@playwright/test";
import { deleteTenant } from "../helpers/api";
import { createTenantViaUI } from "../helpers/ui";
import { loginAsAdmin, loginAsTenantUser, KEYCLOAK_REALM } from "../helpers/auth";
import {
  getKeycloakAdminToken,
  createKeycloakUser,
  deleteKeycloakUser,
  findKeycloakUser,
} from "../helpers/keycloak-admin";

// Geteilter Zustand ueber alle seriellen Tests
let tenantId: string;
let tenantSlug: string;
let tenantUsername: string;
let keycloakUserId: string;

test.describe.serial("Auth + Self-Service Branding Flow", () => {
  // Cleanup nach allen Tests
  test.afterAll(async () => {
    // Keycloak User loeschen
    if (keycloakUserId) {
      try {
        const token = await getKeycloakAdminToken();
        await deleteKeycloakUser(token, KEYCLOAK_REALM, keycloakUserId);
        console.log(`Keycloak User geloescht: ${keycloakUserId}`);
      } catch (error) {
        console.warn(`Keycloak User konnte nicht geloescht werden: ${error}`);
      }
    }

    // Tenant loeschen
    if (tenantId) {
      try {
        await deleteTenant(tenantId);
        console.log(`Tenant geloescht: ${tenantId}`);
      } catch (error) {
        console.warn(`Tenant konnte nicht geloescht werden: ${error}`);
      }
    }
  });

  test("Platform Admin kann sich via Keycloak einloggen", async ({ page }) => {
    // Login als Platform Admin (admin/admin)
    await loginAsAdmin(page);

    // Verifiziere: /tenants Seite ist geladen
    await expect(page).toHaveURL(/\/tenants/);
    await expect(
      page.getByRole("heading", { name: /Tenants/i })
    ).toBeVisible({ timeout: 10000 });
  });

  test("Platform Admin erstellt neuen Tenant", async ({ page }) => {
    // Eindeutiger Slug mit Timestamp um Konflikte zu vermeiden
    const timestamp = Date.now();
    tenantSlug = `e2e-auth-${timestamp}`;
    const tenantName = `E2E Auth Test ${timestamp}`;

    // Als Admin einloggen
    await loginAsAdmin(page);

    // Tenant via UI erstellen (nutzt bestehenden Helper)
    tenantId = await createTenantViaUI(page, tenantName, tenantSlug);

    // tenantId muss eine gueltige UUID sein
    expect(tenantId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
    console.log(`Tenant erstellt: ${tenantId} (slug: ${tenantSlug})`);
  });

  test("Tenant User wird via Keycloak Admin API erstellt", async () => {
    // Kein page-Objekt noetig — rein API-basiert
    expect(tenantId).toBeDefined();

    const timestamp = Date.now();
    tenantUsername = `e2e-tenant-user-${timestamp}`;
    const tenantPassword = "Test1234!";

    // Admin Token holen
    const token = await getKeycloakAdminToken();

    // User erstellen mit tenant_id Attribut
    // tenant_id wird als Attribut gesetzt, damit der Keycloak Protocol Mapper
    // den tenant_id Claim im JWT Access Token setzt
    keycloakUserId = await createKeycloakUser(token, KEYCLOAK_REALM, {
      username: tenantUsername,
      email: `${tenantUsername}@e2e-test.local`,
      firstName: "E2E",
      lastName: "Tenant User",
      password: tenantPassword,
      tenantId: tenantId,
    });

    expect(keycloakUserId).toBeDefined();
    expect(keycloakUserId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );

    // Verifiziere dass der User gefunden werden kann
    const createdUser = await findKeycloakUser(token, KEYCLOAK_REALM, tenantUsername);
    expect(createdUser).not.toBeNull();
    expect(createdUser?.id).toBe(keycloakUserId);

    console.log(`Keycloak User erstellt: ${keycloakUserId} (${tenantUsername})`);
  });

  test("Tenant User kann sich via Keycloak einloggen", async ({ page }) => {
    expect(tenantUsername).toBeDefined();

    const tenantPassword = "Test1234!";

    // Login als Tenant User
    // Zielseite ist / (Home), da /tenants nur fuer Platform Admins ist
    await loginAsTenantUser(page, tenantUsername, tenantPassword);

    // Verifiziere: App ist geladen (keine Error-Seite, kein Keycloak-Formular)
    await expect(page).not.toHaveURL(/auth\.localhost/);
    await page.waitForLoadState("networkidle");

    // Navigiere zu Settings — hier landet der Tenant User
    await page.goto("/settings");
    await page.waitForLoadState("networkidle");

    // Settings-Seite muss sichtbar sein
    await expect(
      page.getByRole("heading", { name: /Settings/i })
    ).toBeVisible({ timeout: 10000 });
  });

  test("Tenant User kann Branding bearbeiten und speichern", async ({ page }) => {
    expect(tenantUsername).toBeDefined();
    expect(tenantId).toBeDefined();

    const tenantPassword = "Test1234!";

    // Als Tenant User einloggen
    await loginAsTenantUser(page, tenantUsername, tenantPassword);

    // Navigiere direkt zu den Settings
    await page.goto("/settings");
    await page.waitForLoadState("networkidle");

    // Sicherstellen dass die Branding-Section sichtbar ist
    // settings/page.tsx rendert h2 fuer Branding (t("settings.branding.title") = "Branding")
    await expect(
      page.getByRole("heading", { name: /Branding/i })
    ).toBeVisible({ timeout: 10000 });

    // Primary Color und Secondary Color ausfullen
    // Labels kommen aus en.json: config.branding.primaryColor = "Primary Color"
    // config.branding.secondaryColor = "Secondary Color"
    const primaryInput = page.getByLabel(/Primary Color/i);
    const secondaryInput = page.getByLabel(/Secondary Color/i);

    await expect(primaryInput).toBeVisible({ timeout: 5000 });
    await primaryInput.clear();
    await primaryInput.fill("#1e40af");

    await expect(secondaryInput).toBeVisible({ timeout: 5000 });
    await secondaryInput.clear();
    await secondaryInput.fill("#64748b");

    // Save Branding klicken (i18n Key: config.branding.save = "Save Branding")
    await page.getByRole("button", { name: /Save Branding/i }).click();

    // Verifiziere Success Toast (Sonner)
    // Toast erscheint mit data-sonner-toast und data-type="success"
    await expect(
      page.locator("[data-sonner-toast][data-type='success']")
    ).toBeVisible({ timeout: 10000 });
  });

  test("Branding-Speicherung zeigt Fehler bei ungueltigem Hex-Code", async ({ page }) => {
    expect(tenantUsername).toBeDefined();

    const tenantPassword = "Test1234!";

    // Als Tenant User einloggen
    await loginAsTenantUser(page, tenantUsername, tenantPassword);

    await page.goto("/settings");
    await page.waitForLoadState("networkidle");

    const primaryInput = page.getByLabel(/Primary Color/i);
    await expect(primaryInput).toBeVisible({ timeout: 5000 });

    // Ungueltige Farbe eingeben
    await primaryInput.clear();
    await primaryInput.fill("not-a-color");

    // Speichern versuchen
    await page.getByRole("button", { name: /Save Branding/i }).click();

    // Formular-Validierung schlaegt fehl — kein Success-Toast
    // Zod-Validierung verhindert Submit
    await expect(
      page.locator("[data-sonner-toast][data-type='success']")
    ).not.toBeVisible({ timeout: 2000 });
  });

  test("Settings-Seite zeigt den Tenant User als eingeloggt", async ({ page }) => {
    expect(tenantUsername).toBeDefined();

    const tenantPassword = "Test1234!";

    // Als Tenant User einloggen
    await loginAsTenantUser(page, tenantUsername, tenantPassword);

    await page.goto("/settings");
    await page.waitForLoadState("networkidle");

    // User-Email ist im Header sichtbar
    // Header zeigt session.user.email (aus JWT)
    const tenantEmail = `${tenantUsername}@e2e-test.local`;
    await expect(page.getByText(tenantEmail)).toBeVisible({ timeout: 5000 });
  });
});
