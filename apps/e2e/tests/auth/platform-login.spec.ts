import { test, expect } from "../../helpers/fixtures";

test.describe("Platform Admin Login @smoke", () => {
  test("should login as platform admin and see tenants page", async ({
    page,
  }) => {
    // Navigate to protected page — middleware triggers Keycloak redirect
    await page.goto("/tenants");
    await page.waitForLoadState("domcontentloaded");

    // Should have been redirected to Keycloak
    await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
    await page.locator("#username").fill("admin");
    await page.locator("#password").fill("admin");
    await page.locator("#kc-login").click();

    // Wait until redirect back to the app completes
    await page.waitForURL((url) => !url.href.includes("auth.localhost"), {
      timeout: 20000,
    });
    await page.waitForLoadState("networkidle");

    // Should land on /tenants
    expect(page.url()).toContain("/tenants");

    // Tenant management content should be visible
    await expect(page.getByTestId("tenants-page")).toBeVisible({
      timeout: 10000,
    });
  });

  test("should persist session across page reload", async ({
    platformAdminPage,
  }) => {
    // platformAdminPage fixture is already logged in as admin
    const urlBeforeReload = platformAdminPage.url();
    expect(urlBeforeReload).toContain("/tenants");

    await platformAdminPage.reload();
    await platformAdminPage.waitForLoadState("networkidle");

    // Should still be on /tenants — no Keycloak redirect
    expect(platformAdminPage.url()).toContain("/tenants");
    expect(platformAdminPage.url()).not.toContain("auth.localhost");

    await expect(platformAdminPage.getByTestId("tenants-page")).toBeVisible({
      timeout: 10000,
    });
  });

  test("should logout and redirect to signin", async ({
    platformAdminPage,
  }) => {
    // Open the user menu in the nav
    await platformAdminPage.getByTestId("nav-user-menu").click();

    // Click "Sign out"
    await platformAdminPage.getByTestId("nav-sign-out").click();

    // Should redirect to Keycloak login or internal signin page
    await platformAdminPage.waitForURL(
      (url) =>
        url.href.includes("auth.localhost") ||
        url.href.includes("/api/auth/signin"),
      { timeout: 15000 }
    );
  });

  test("should redirect unauthenticated user to keycloak", async ({ page }) => {
    // Navigate directly to a protected page without any session
    await page.goto("/tenants");
    await page.waitForLoadState("domcontentloaded");

    // Middleware should have redirected to Keycloak
    await page.waitForURL((url) => url.href.includes("auth.localhost"), {
      timeout: 10000,
    });

    await expect(page.locator("#username")).toBeVisible({ timeout: 15000 });
  });
});
