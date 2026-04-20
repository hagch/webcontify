# App Domain 4g: E2E Tests (Playwright) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write Playwright E2E tests covering all 6 BDU scenarios from the design doc, plus data-testid additions where missing, and manual browser verification.

**Architecture:** Tests use the existing E2E infrastructure (fixtures, ApiClient, auth helpers). Each BDU scenario maps to a test.describe.serial block. Tests run against the full stack (Docker + Backend + Frontend) via `pnpm dev`. Cleanup via ApiClient.registerForCleanup.

**Tech Stack:** Playwright, Nx, pnpm dev (Docker + Backend + Frontend)

**Design Doc:** `docs/plans/2026-04-04-app-domain-design.md` (Section 7 — BDU Test Script)

**Sub-Plan Dependencies:** All previous sub-plans (4a-4f)

---

## Prerequisites

Before running E2E tests, the full stack must be running:
```bash
pnpm dev  # Starts Docker (PostgreSQL, Keycloak, OpenFGA, MinIO, Traefik) + Backend + Frontend
```

Wait until:
- `platform.localhost` serves the UI
- `api.localhost` responds to health checks
- `auth.localhost` (Keycloak) is accessible

## File Structure

### New Files
- `apps/e2e/tests/apps/app-crud.spec.ts` — BDU Test 1: Create and Publish
- `apps/e2e/tests/apps/app-edit-draft.spec.ts` — BDU Test 2: Edit + Draft Badge + Revisions
- `apps/e2e/tests/apps/app-versioning.spec.ts` — BDU Test 3: Version History + Rollback
- `apps/e2e/tests/apps/app-dependencies.spec.ts` — BDU Test 4: Dependencies + Problems
- `apps/e2e/tests/apps/app-import-export.spec.ts` — BDU Test 5: Import Blueprint
- `apps/e2e/tests/apps/app-permissions.spec.ts` — BDU Test 6: Permission-Gated Navigation

### Modified Files
- Various app components may need additional data-testid attributes

### Helpers
- `apps/e2e/helpers/api.ts` — may need app-specific API helper methods (createApp, publishApp, etc.)

---

## Task 1: Add App API Helpers to E2E

**Files:**
- Modify: `apps/e2e/helpers/api.ts`

Add methods to the ApiClient class following the existing pattern (createTenant, deleteTenant):

```typescript
async createApp(token: string, name: string, description?: string): Promise<{ id: string; slug: string }>
async deleteApp(token: string, appId: string): Promise<void>
async publishApp(token: string, appId: string, version: string, releaseNotes?: string): Promise<void>
async updateApp(token: string, appId: string, data: { name?: string; description?: string }): Promise<void>
async updateAppStatus(token: string, appId: string, status: string): Promise<void>
async addAppDependency(token: string, appId: string, dependsOnAppId: string): Promise<void>
async exportAppBlueprint(token: string, appId: string, versionId: string): Promise<object>
```

Each method:
- Uses `fetch()` against `process.env.API_URL`
- Includes Authorization header with token
- Includes X-Tenant-Id header (from tenant context)
- Registers created resources for cleanup

IMPORTANT: Check how existing methods like `createTenant` handle the API URL and headers. The App API endpoints are tenant-scoped, so they need the tenant's JWT (not platform admin JWT).

For creating apps in E2E, the flow is:
1. Create a tenant via platform admin API (already exists)
2. Wait for provisioning
3. Get a tenant user token
4. Call app APIs with that token

Commit: `"test: add App API helpers to E2E test infrastructure"`

---

## Task 2: BDU Test 1 — Create and Publish App

**File:** `apps/e2e/tests/apps/app-crud.spec.ts`

```typescript
test.describe.serial("App CRUD — BDU Test 1", () => {
  // Setup: create tenant, wait for provisioning, login as tenant admin

  test("should create an app via dialog", async ({ page }) => {
    // Navigate to Apps page
    // Click "Create App" button
    // Fill name: "My Blog"
    // Click submit
    // Verify redirect to detail page
    // Verify status DRAFT badge visible
  })

  test("should show app in list", async ({ page }) => {
    // Navigate to Apps list
    // Verify "My Blog" row exists in table
    // Verify status badge shows DRAFT
  })

  test("should publish app", async ({ page }) => {
    // Navigate to app detail
    // Click Publish button
    // Verify version input pre-filled (e.g., "0.1.0")
    // Click submit
    // Verify status changes to ACTIVE
    // Verify version shown in Versions tab
  })

  // Cleanup: delete app, delete tenant
})
```

Use `data-testid` selectors: `create-app-dialog`, `app-name-input`, `create-app-submit`, `app-table`, `publish-app-dialog`, `publish-version-input`, `publish-app-submit`.

Commit: `"test: add E2E test for App CRUD + Publish (BDU Test 1)"`

---

## Task 3: BDU Test 2 — Edit App and See Draft

**File:** `apps/e2e/tests/apps/app-edit-draft.spec.ts`

```typescript
test.describe.serial("App Edit + Draft — BDU Test 2", () => {
  // Setup: create tenant, create + publish app via API

  test("should show readonly detail for active app", async ({ page }) => {
    // Navigate to app detail
    // Verify ACTIVE status
    // Verify overview content visible
  })

  test("should edit app name and see draft badge", async ({ page }) => {
    // Navigate to app detail
    // Edit app name (via API or UI edit action)
    // Verify DRAFT badge appears next to ACTIVE
  })

  test("should see revision after edit", async ({ page }) => {
    // Click Revisions tab
    // Verify at least 1 revision listed
    // Expand revision to see diff
  })
})
```

NOTE: Since there's no "edit" button on the detail page UI (updates go through API), the edit step may need to use the API directly. Or check if there's an edit form/button.

Commit: `"test: add E2E test for App Edit + Draft + Revisions (BDU Test 2)"`

---

## Task 4: BDU Test 3 — Version History + Rollback

**File:** `apps/e2e/tests/apps/app-versioning.spec.ts`

```typescript
test.describe.serial("App Versioning — BDU Test 3", () => {
  // Setup: create tenant, create app, publish v0.1.0, edit, publish v0.2.0

  test("should show version history", async ({ page }) => {
    // Click Versions tab
    // Verify 2 versions listed (v0.2.0, v0.1.0 in desc order)
  })

  test("should expand version to see details", async ({ page }) => {
    // Click on a version row
    // Verify collapsible expands
    // Verify release notes or version info visible
  })

  test("should rollback to previous version", async ({ page }) => {
    // Click Rollback on v0.1.0
    // Confirm dialog
    // Verify app now has hasDraft = true
    // Verify DRAFT badge appears
  })
})
```

Commit: `"test: add E2E test for Version History + Rollback (BDU Test 3)"`

---

## Task 5: BDU Test 4 — Dependencies + Problems

**File:** `apps/e2e/tests/apps/app-dependencies.spec.ts`

```typescript
test.describe.serial("App Dependencies — BDU Test 4", () => {
  // Setup: create tenant, create 2 apps (Core + Blog), activate both via API

  test("should add dependency", async ({ page }) => {
    // Navigate to Blog app detail → Dependencies tab
    // Click "Add Dependency"
    // Select "Core" from dropdown
    // Verify Core appears in dependency list/graph
  })

  test("should show problems when dependency deactivated", async ({ page }) => {
    // Deactivate Core via API
    // Navigate to Blog app detail
    // Verify problems badge/panel visible
  })

  test("should remove dependency", async ({ page }) => {
    // Navigate to Dependencies tab
    // Remove the dependency
    // Verify dependency removed from list
  })
})
```

Commit: `"test: add E2E test for Dependencies + Problems (BDU Test 4)"`

---

## Task 6: BDU Test 5 — Import Blueprint

**File:** `apps/e2e/tests/apps/app-import-export.spec.ts`

```typescript
test.describe.serial("App Import/Export — BDU Test 5", () => {
  // Setup: create tenant, create + publish app via API, export blueprint via API

  test("should import blueprint via dialog", async ({ page }) => {
    // Navigate to Apps list
    // Click "Import Blueprint"
    // Paste exported JSON into textarea
    // Click "Analyze"
    // Verify preview shows app(s) to import
    // Click "Import"
    // Verify success, new app in list
  })

  test("should auto-rename on slug collision", async ({ page }) => {
    // Import same blueprint again
    // Verify preview shows slug collision
    // Verify suggested rename
    // Import with renamed slug
    // Verify both apps in list
  })
})
```

Commit: `"test: add E2E test for Import Blueprint (BDU Test 5)"`

---

## Task 7: BDU Test 6 — Permission-Gated Navigation

**File:** `apps/e2e/tests/apps/app-permissions.spec.ts`

This is the most complex test — requires creating a tenant user with restricted permissions.

```typescript
test.describe.serial("App Permissions — BDU Test 6", () => {
  // Setup:
  // 1. Create tenant, wait for provisioning
  // 2. Create a role "App Viewer" with app_management:viewer only
  // 3. Invite a user with that role
  // 4. Create 2 apps (Blog, Shop) via admin
  // 5. Set per-app permission: user gets viewer on Blog only (via API)

  test("should only see permitted apps in list", async ({ page }) => {
    // Login as restricted user
    // Navigate to Apps
    // Verify "Blog" visible
    // Verify "Shop" NOT visible (or entire Apps nav hidden if no permission)
  })

  test("should see readonly view for viewer", async ({ page }) => {
    // Navigate to Blog detail
    // Verify no Publish button
    // Verify no Edit actions
  })

  test("should block direct URL access to forbidden app", async ({ page }) => {
    // Navigate directly to /apps/{shopId}
    // Verify 403 or redirect
  })
})
```

NOTE: This test requires per-app permissions which may not be fully implemented (we have global app_management but per-app was noted as future). Check what's available and test what we can.

If per-app is not implemented, simplify to:
- User with NO app_management permission → Apps nav hidden
- User with app_management:viewer → Apps visible, readonly

Commit: `"test: add E2E test for Permission-Gated Navigation (BDU Test 6)"`

---

## Task 8: Add Missing data-testid Attributes

Check all app components for missing data-testid attributes that E2E tests need. Common gaps:
- Action buttons (activate, deactivate, delete on detail page)
- Tab triggers (overview, dependencies, versions, revisions)
- Status badges
- Problems panel items
- Version list rows
- Revision list rows

Read each component file and add data-testid where the E2E tests need to find elements.

Commit: `"test: add missing data-testid attributes for E2E tests"`

---

## Task 9: Run All E2E Tests + Manual BDU Verification

### 9a. Start the stack

```bash
pnpm dev
```

Wait for all services to be healthy.

### 9b. Run Playwright tests

```bash
cd apps/e2e && npx playwright test tests/apps/
```

All tests must pass.

### 9c. Manual BDU verification (verify-e2e)

After all Playwright tests pass, manually walk through each BDU scenario in the browser:
1. Open `platform.localhost` in Chrome
2. Walk through each of the 6 scenarios visually
3. Check for: visual glitches, confusing flows, mobile responsiveness
4. Document any issues found

### 9d. Commit + PROGRESS.md

```bash
git commit -m "test: E2E tests for App Domain (6 BDU scenarios)"
```

Update PROGRESS.md with Phase 4g completion.

---

## Known Trade-offs

- **BDU Test 2 limited** — Content Definitions don't exist yet, so we test name/description edit only (not definition field editing)
- **BDU Test 6 simplified** — per-app permissions may use global app_management check, not individual app checks
- **No Content Definition editing** — the "Definitions Tab" referenced in BDU Test 2 doesn't have content yet
- **Manual BDU verification** — should be done after Playwright tests pass, using verify-e2e skill
- **Stack startup** — `pnpm dev` needs Docker running and may take 2-3 minutes
