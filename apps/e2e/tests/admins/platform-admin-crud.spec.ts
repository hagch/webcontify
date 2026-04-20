/**
 * E2E Tests: Platform Admin CRUD
 *
 * Tests for listing, inviting, deactivating, activating, and deleting
 * platform admins via the /admins page.
 *
 * Uses platformAdminPage fixture (already logged in as admin).
 * Cleans up the created Keycloak user in afterAll.
 *
 * Constraints verified:
 * - Cannot deactivate yourself (self-deactivation → 400)
 * - Cannot delete the last remaining active admin (→ 409)
 */

import { test, expect } from "../../helpers/fixtures";
import {
  getKeycloakAdminToken,
  deleteKeycloakUser,
  findKeycloakUserByEmail,
} from "../../helpers/keycloak-admin";

const PLATFORM_REALM = "webcontify-platform";

test.describe.serial("Platform Admin CRUD @smoke", () => {
  const testAdminEmail = `e2e-admin-${Date.now()}@webcontify.io`;
  const testAdminFirstName = "E2E";
  const testAdminLastName = "TestAdmin";
  let testAdminKeycloakId: string | undefined;
  let testAdminId: string | undefined;

  test.afterAll(async () => {
    if (testAdminKeycloakId) {
      const token = await getKeycloakAdminToken();
      await deleteKeycloakUser(token, PLATFORM_REALM, testAdminKeycloakId);
    }
  });

  test("should list platform admins", async ({ platformAdminPage }) => {
    await platformAdminPage.goto("/admins");
    await platformAdminPage.waitForLoadState("networkidle");

    await expect(platformAdminPage.getByTestId("admin-table")).toBeVisible();
    // At least the default admin must be present
    await expect(
      platformAdminPage.getByTestId("admin-table-row").first()
    ).toBeVisible();
  });

  test("should invite a new platform admin", async ({ platformAdminPage }) => {
    await platformAdminPage.goto("/admins");
    await platformAdminPage.waitForLoadState("networkidle");

    // Open invite dialog via the page-level button
    await platformAdminPage.getByTestId("invite-admin-button").click();
    const dialog = platformAdminPage.getByTestId("invite-admin-dialog");
    await expect(dialog).toBeVisible();

    // Fill the form
    await platformAdminPage
      .getByTestId("admin-email-input")
      .fill(testAdminEmail);
    await platformAdminPage
      .getByTestId("admin-firstname-input")
      .fill(testAdminFirstName);
    await platformAdminPage
      .getByTestId("admin-lastname-input")
      .fill(testAdminLastName);

    await platformAdminPage.getByTestId("invite-admin-submit").click();

    // Dialog closes and the new admin appears in the table
    await expect(dialog).not.toBeVisible({ timeout: 10000 });
    await expect(platformAdminPage.getByText(testAdminEmail)).toBeVisible({
      timeout: 10000,
    });

    // Store the Keycloak ID for cleanup
    const token = await getKeycloakAdminToken();
    const user = await findKeycloakUserByEmail(
      token,
      PLATFORM_REALM,
      testAdminEmail
    );
    testAdminKeycloakId = user?.id;

    // Capture the row's actions-menu testid to derive the admin ID
    const rows = platformAdminPage.getByTestId("admin-table-row");
    const allRows = await rows.all();
    for (const row of allRows) {
      const emailCell = row.getByText(testAdminEmail);
      const isVisible = await emailCell.isVisible();
      if (isVisible) {
        // The actions menu button carries data-testid="actions-menu-{id}"
        const actionsBtn = row.locator("[data-testid^='actions-menu-']");
        const testId = await actionsBtn.getAttribute("data-testid");
        if (testId) {
          testAdminId = testId.replace("actions-menu-", "");
        }
        break;
      }
    }

    expect(testAdminId).toBeDefined();
  });

  test("should deactivate a platform admin", async ({ platformAdminPage }) => {
    expect(testAdminId).toBeDefined();

    await platformAdminPage.goto("/admins");
    await platformAdminPage.waitForLoadState("networkidle");

    // Open the actions menu for the test admin
    await platformAdminPage
      .getByTestId(`actions-menu-${testAdminId}`)
      .click();

    // Click Deactivate
    await platformAdminPage
      .getByTestId(`deactivate-admin-${testAdminId}`)
      .click();

    // Toast confirms success
    await expect(
      platformAdminPage.getByText("Administrator wurde deaktiviert.")
    ).toBeVisible({ timeout: 10000 });

    // After the list refetches the row should show the "Deaktiviert" badge
    await platformAdminPage.waitForLoadState("networkidle");
    const row = platformAdminPage
      .getByTestId("admin-table-row")
      .filter({ hasText: testAdminEmail });
    await expect(row.getByText("Deaktiviert")).toBeVisible({ timeout: 10000 });
  });

  test("should not allow deactivating yourself", async ({
    platformAdminPage,
  }) => {
    await platformAdminPage.goto("/admins");
    await platformAdminPage.waitForLoadState("networkidle");

    // Locate the row that belongs to the logged-in admin (admin@webcontify.io)
    const adminRow = platformAdminPage
      .getByTestId("admin-table-row")
      .filter({ hasText: "admin@webcontify.io" });
    await expect(adminRow).toBeVisible();

    const actionsBtn = adminRow.locator("[data-testid^='actions-menu-']");
    await actionsBtn.click();

    const testId = await actionsBtn.getAttribute("data-testid");
    const ownId = testId?.replace("actions-menu-", "");
    expect(ownId).toBeDefined();

    await platformAdminPage.getByTestId(`deactivate-admin-${ownId}`).click();

    // Backend returns 400 → frontend shows generic error toast
    await expect(
      platformAdminPage.getByText(
        "Fehler beim Aktualisieren des Status. Bitte versuche es erneut."
      )
    ).toBeVisible({ timeout: 10000 });
  });

  test("should activate a deactivated platform admin", async ({
    platformAdminPage,
  }) => {
    expect(testAdminId).toBeDefined();

    await platformAdminPage.goto("/admins");
    await platformAdminPage.waitForLoadState("networkidle");

    // Open actions menu for the (still deactivated) test admin
    await platformAdminPage
      .getByTestId(`actions-menu-${testAdminId}`)
      .click();

    // When the admin is deactivated the menu shows "Aktivieren"
    await platformAdminPage
      .getByTestId(`activate-admin-${testAdminId}`)
      .click();

    await expect(
      platformAdminPage.getByText("Administrator wurde aktiviert.")
    ).toBeVisible({ timeout: 10000 });

    await platformAdminPage.waitForLoadState("networkidle");
    const row = platformAdminPage
      .getByTestId("admin-table-row")
      .filter({ hasText: testAdminEmail });
    await expect(row.getByText("Aktiv")).toBeVisible({ timeout: 10000 });
  });

  test("should not allow deleting the last admin", async ({
    platformAdminPage,
  }) => {
    // There are currently 2 admins: the original + the test admin.
    // Deleting the original admin is blocked only when it is the *last* one.
    // Instead we verify the "last admin" guard by trying to delete the original
    // admin after first deactivating the test admin — leaving only one active admin,
    // and then deleting the test admin (deactivated admins still count as admins
    // in Keycloak). The real guard is on the *last* Keycloak user with the role.
    //
    // Simplified: just verify the error toast appears when backend rejects the request.
    // We trigger it by attempting to delete the seeded admin@webcontify.io directly
    // while the test admin is still active (so there ARE 2 admins) — this will
    // NOT trigger the last-admin guard. Instead we deactivate + delete the test admin
    // first, THEN try to delete the original admin to trigger the guard.

    // Step 1: deactivate test admin so only original admin remains active
    await platformAdminPage.goto("/admins");
    await platformAdminPage.waitForLoadState("networkidle");

    await platformAdminPage
      .getByTestId(`actions-menu-${testAdminId}`)
      .click();
    await platformAdminPage
      .getByTestId(`deactivate-admin-${testAdminId}`)
      .click();
    await expect(
      platformAdminPage.getByText("Administrator wurde deaktiviert.")
    ).toBeVisible({ timeout: 10000 });

    // Step 2: delete the test admin (deactivated, so it should succeed)
    await platformAdminPage.waitForLoadState("networkidle");
    await platformAdminPage
      .getByTestId(`actions-menu-${testAdminId}`)
      .click();
    await platformAdminPage
      .getByTestId(`delete-admin-${testAdminId}`)
      .click();

    const deleteDialog = platformAdminPage.getByTestId("delete-admin-dialog");
    await expect(deleteDialog).toBeVisible();
    await platformAdminPage.getByTestId("delete-admin-confirm").click();

    await expect(
      platformAdminPage.getByText("Administrator wurde entfernt.")
    ).toBeVisible({ timeout: 10000 });

    // testAdminKeycloakId is no longer needed — already deleted via UI
    testAdminKeycloakId = undefined;
    testAdminId = undefined;

    // Step 3: now only the original admin remains — trying to delete should fail
    await platformAdminPage.waitForLoadState("networkidle");
    const adminRow = platformAdminPage
      .getByTestId("admin-table-row")
      .filter({ hasText: "admin@webcontify.io" });
    await expect(adminRow).toBeVisible();

    const actionsBtn = adminRow.locator("[data-testid^='actions-menu-']");
    await actionsBtn.click();

    const testId = await actionsBtn.getAttribute("data-testid");
    const ownId = testId?.replace("actions-menu-", "");
    expect(ownId).toBeDefined();

    await platformAdminPage.getByTestId(`delete-admin-${ownId}`).click();

    const confirmDialog = platformAdminPage.getByTestId("delete-admin-dialog");
    await expect(confirmDialog).toBeVisible();
    await platformAdminPage.getByTestId("delete-admin-confirm").click();

    // Backend returns 409 → frontend shows error toast
    await expect(
      platformAdminPage.getByText(
        "Fehler beim Entfernen des Administrators. Bitte versuche es erneut."
      )
    ).toBeVisible({ timeout: 10000 });

    // Close the dialog
    await platformAdminPage.getByTestId("delete-admin-cancel").click();
  });
});
