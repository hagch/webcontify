import type { Page } from "@playwright/test";
import { expect } from "@playwright/test";

/**
 * Erstellt einen Tenant über die UI und gibt die tenantId aus der URL zurück.
 *
 * Ablauf:
 * 1. Navigiert zu /tenants
 * 2. Klickt "Mandant erstellen"
 * 3. Füllt Name + Slug aus
 * 4. Klickt Submit, wartet bis Dialog schließt
 * 5. Klickt auf den Tenant in der Tabelle
 * 6. Extrahiert tenantId aus der URL
 */
export async function createTenantViaUI(
  page: Page,
  name: string,
  slug: string
): Promise<string> {
  await page.goto("/tenants");
  await page.waitForLoadState("networkidle");

  await page.getByRole("button", { name: "Create Tenant" }).click();

  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();

  await dialog.getByLabel("Name").fill(name);
  await dialog.getByLabel("Slug").fill(slug);

  await dialog.getByRole("button", { name: "Create" }).click();

  await expect(dialog).not.toBeVisible({ timeout: 10000 });

  const tenantLink = page.getByRole("link", { name });
  await expect(tenantLink).toBeVisible({ timeout: 10000 });
  await tenantLink.click();

  await page.waitForURL(/\/tenants\/[^/]+$/, { timeout: 10000 });

  const match = page.url().match(/\/tenants\/([^/]+)/);
  if (!match) {
    throw new Error(`Konnte tenantId nicht aus URL extrahieren: ${page.url()}`);
  }

  return match[1];
}
