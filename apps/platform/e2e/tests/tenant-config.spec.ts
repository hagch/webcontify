import { test, expect } from "@playwright/test";
import { deleteTenant } from "../helpers/api";
import { createTenantViaUI } from "../helpers/ui";

let tenantId: string;

test.describe.serial("Tenant Config", () => {
  test.afterAll(async () => {
    if (tenantId) {
      await deleteTenant(tenantId);
    }
  });

  test("create tenant via UI", async ({ page }) => {
    const slug = `e2e-cfg-${Date.now()}`;
    tenantId = await createTenantViaUI(page, `Config Test ${slug}`, slug);
    expect(tenantId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
  });

  test.describe("Tenant Config Page", () => {
    test.beforeEach(async ({ page }) => {
      await page.goto(`/tenants/${tenantId}/config`);
      await page.waitForLoadState("networkidle");
    });

    test("shows config page with three tabs", async ({ page }) => {
      await expect(page.getByRole("heading", { level: 1 })).toContainText(
        /Konfiguration|Configuration/
      );

      const tabList = page.getByRole("tablist");
      await expect(tabList).toBeVisible();

      await expect(
        tabList.getByRole("tab", { name: /Branding/i })
      ).toBeVisible();
      await expect(
        tabList.getByRole("tab", { name: /E-Mail|Email/i })
      ).toBeVisible();
      await expect(
        tabList.getByRole("tab", { name: /Domain/i })
      ).toBeVisible();
    });

    test("branding tab is active by default", async ({ page }) => {
      const brandingTab = page.getByRole("tab", { name: /Branding/i });
      await expect(brandingTab).toHaveAttribute("data-state", "active");

      await expect(
        page.getByLabel(/Primaerfarbe|Primary Color/i)
      ).toBeVisible();
      await expect(
        page.getByLabel(/Sekundaerfarbe|Secondary Color/i)
      ).toBeVisible();
    });

    test("saves branding with valid hex color code", async ({ page }) => {
      const primaryInput = page.getByLabel(/Primaerfarbe|Primary Color/i);
      const secondaryInput = page.getByLabel(/Sekundaerfarbe|Secondary Color/i);

      await primaryInput.clear();
      await primaryInput.fill("#3b82f6");
      await secondaryInput.clear();
      await secondaryInput.fill("#64748b");

      await page
        .getByRole("button", { name: /Branding speichern|Save Branding/i })
        .click();

      await expect(
        page.locator("[data-sonner-toast][data-type='success']")
      ).toBeVisible({ timeout: 10000 });
    });

    test("shows validation error for invalid hex code", async ({ page }) => {
      const primaryInput = page.getByLabel(/Primaerfarbe|Primary Color/i);

      await primaryInput.clear();
      await primaryInput.fill("invalid-color");

      await page
        .getByRole("button", { name: /Branding speichern|Save Branding/i })
        .click();

      // Formular soll nicht abschicken — kein Toast sichtbar
      await expect(
        page.locator("[data-sonner-toast]")
      ).not.toBeVisible({ timeout: 2000 });
    });

    test("switches to email tab and shows form fields", async ({ page }) => {
      await page.getByRole("tab", { name: /E-Mail|Email/i }).click();

      await expect(page.getByLabel(/SMTP Host/i)).toBeVisible();
      await expect(page.getByLabel(/SMTP Port/i)).toBeVisible();
      await expect(page.getByLabel(/SMTP Benutzer|SMTP User/i)).toBeVisible();
      await expect(page.getByLabel(/SMTP Passwort|SMTP Password/i)).toBeVisible();
      await expect(
        page.getByLabel(/Absender-Adresse|From Address/i)
      ).toBeVisible();
      await expect(page.getByLabel(/Absender-Name|From Name/i)).toBeVisible();
    });

    test("saves email configuration", async ({ page }) => {
      await page.getByRole("tab", { name: /E-Mail|Email/i }).click();

      await page.getByLabel(/SMTP Host/i).fill("smtp.test.com");
      await page.getByLabel(/SMTP Port/i).fill("587");
      await page
        .getByLabel(/Absender-Adresse|From Address/i)
        .fill("test@test.com");
      await page.getByLabel(/Absender-Name|From Name/i).fill("Test Sender");

      await page
        .getByRole("button", { name: /E-Mail speichern|Save Email/i })
        .click();

      await expect(
        page.locator("[data-sonner-toast][data-type='success']")
      ).toBeVisible({ timeout: 10000 });
    });

    test("switches to domain tab and shows form fields", async ({ page }) => {
      await page.getByRole("tab", { name: /Domain/i }).click();

      await expect(
        page.getByLabel(/Eigene Domain|Custom Domain/i)
      ).toBeVisible();
    });

    test("saves domain configuration", async ({ page }) => {
      await page.getByRole("tab", { name: /Domain/i }).click();

      await page
        .getByLabel(/Eigene Domain|Custom Domain/i)
        .fill("app.example.com");

      await page
        .getByRole("button", { name: /Domain speichern|Save Domain/i })
        .click();

      await expect(
        page.locator("[data-sonner-toast][data-type='success']")
      ).toBeVisible({ timeout: 10000 });
    });

    test("has back link to tenant detail page", async ({ page }) => {
      const backLink = page.getByRole("link", {
        name: /Mandanten-Details|Tenant Details/i,
      });
      await expect(backLink).toBeVisible();

      await backLink.click();
      await expect(page).toHaveURL(new RegExp(`/tenants/${tenantId}$`));
    });
  });

  test.describe("Tenant Detail -> Config Navigation", () => {
    test("config link navigates to config page", async ({ page }) => {
      await page.goto(`/tenants/${tenantId}`);
      await page.waitForLoadState("networkidle");

      const configLink = page.getByRole("link", {
        name: /Konfiguration|Configuration/i,
      });
      await expect(configLink).toBeVisible();

      await configLink.click();
      await expect(page).toHaveURL(
        new RegExp(`/tenants/${tenantId}/config`)
      );
      await expect(page.getByRole("heading", { level: 1 })).toContainText(
        /Konfiguration|Configuration/
      );
    });
  });
});
