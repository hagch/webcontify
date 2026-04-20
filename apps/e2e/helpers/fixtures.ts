import { test as base, type Page } from "@playwright/test";
import { loginAsAdmin } from "./auth";
import { ApiClient } from "./api";

type TestFixtures = {
  platformAdminPage: Page;
  apiClient: ApiClient;
};

/**
 * Extended Playwright test with shared fixtures:
 *
 * - platformAdminPage: a page that is already logged in as platform admin
 * - apiClient: an ApiClient instance that cleans up all registered resources after the test
 */
export const test = base.extend<TestFixtures>({
  platformAdminPage: async ({ page }, use) => {
    await loginAsAdmin(page);
    await use(page);
  },
  apiClient: async ({}, use) => {
    const client = new ApiClient();
    await use(client);
    await client.cleanup();
  },
});

export { expect } from "@playwright/test";
