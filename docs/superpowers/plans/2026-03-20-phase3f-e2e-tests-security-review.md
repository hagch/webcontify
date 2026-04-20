# Phase 3f: E2E Tests + Security Review — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate E2E test project with comprehensive test coverage for all Phase 3a–3e features, using `data-testid` selectors, Playwright fixtures, and stack-based cleanup. All code in English.

**Architecture:** New `apps/e2e/` project in monorepo. Existing tests migrate from `apps/platform/e2e/`. Tests run against full dev environment (Docker Compose + Backend + Frontend). Helpers abstract Keycloak Admin API and Backend API calls.

**Tech Stack:** Playwright, TypeScript, Nx

**Design Spec:** `docs/superpowers/specs/2026-03-20-phase3f-e2e-tests-security-review-design.md`

---

## File Structure

### New Project: apps/e2e/
- `apps/e2e/package.json` — Playwright + dependencies
- `apps/e2e/tsconfig.json` — TypeScript config
- `apps/e2e/playwright.config.ts` — Playwright config with env vars
- `apps/e2e/project.json` — Nx project config
- `apps/e2e/.env.local` — Default local environment
- `apps/e2e/helpers/auth.ts` — Keycloak login helpers (migrated + refactored to English)
- `apps/e2e/helpers/keycloak-admin.ts` — Keycloak Admin API (migrated + English)
- `apps/e2e/helpers/api.ts` — Backend API client with cleanup stack (migrated + English)
- `apps/e2e/helpers/ui.ts` — UI interaction helpers (migrated + English)
- `apps/e2e/helpers/fixtures.ts` — Playwright custom test fixtures
- `apps/e2e/tests/auth/platform-login.spec.ts`
- `apps/e2e/tests/auth/tenant-login.spec.ts`
- `apps/e2e/tests/auth/token-expiry.spec.ts`
- `apps/e2e/tests/tenants/tenant-crud.spec.ts`
- `apps/e2e/tests/tenants/tenant-config.spec.ts`
- `apps/e2e/tests/users/invite-user.spec.ts`
- `apps/e2e/tests/users/invite-with-role-clone.spec.ts`
- `apps/e2e/tests/roles/role-crud.spec.ts`
- `apps/e2e/tests/roles/role-assignment.spec.ts`
- `apps/e2e/tests/admins/platform-admin-crud.spec.ts`
- `apps/e2e/tests/security/cross-tenant-isolation.spec.ts`
- `apps/e2e/tests/security/permission-enforcement.spec.ts`
- `apps/e2e/tests/security/unauthorized-access.spec.ts`
- `apps/e2e/tests/security/input-validation.spec.ts`

### Frontend Modified (data-testid attributes)
- `apps/platform/src/features/roles/components/role-table.tsx`
- `apps/platform/src/features/roles/components/create-role-dialog.tsx`
- `apps/platform/src/features/roles/components/role-assignees-table.tsx`
- `apps/platform/src/features/roles/components/add-assignees-dialog.tsx`
- `apps/platform/src/features/users/components/user-table.tsx`
- `apps/platform/src/features/users/components/invite-user-dialog.tsx`
- `apps/platform/src/features/users/components/user-actions-menu.tsx`
- `apps/platform/src/features/admins/components/admin-table.tsx`
- `apps/platform/src/features/admins/components/invite-admin-dialog.tsx`
- `apps/platform/src/features/admins/components/admin-actions-menu.tsx`
- `apps/platform/src/components/layout/nav-user.tsx`

### Deleted (after migration)
- `apps/platform/e2e/` (entire directory)
- `apps/platform/playwright.config.ts`

---

## Task 1: E2E Project Scaffold

**Files:**
- Create: `apps/e2e/package.json`
- Create: `apps/e2e/tsconfig.json`
- Create: `apps/e2e/playwright.config.ts`
- Create: `apps/e2e/project.json`
- Create: `apps/e2e/.env.local`

- [ ] **Step 1: Create package.json**

```json
{
  "name": "@webcontify/e2e",
  "private": true,
  "scripts": {
    "e2e": "playwright test",
    "e2e:headed": "playwright test --headed",
    "e2e:ui": "playwright test --ui"
  },
  "devDependencies": {
    "@playwright/test": "^1.50.0"
  }
}
```

- [ ] **Step 2: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "baseUrl": ".",
    "paths": {
      "@helpers/*": ["./helpers/*"]
    }
  },
  "include": ["tests/**/*.ts", "helpers/**/*.ts"]
}
```

- [ ] **Step 3: Create playwright.config.ts**

```typescript
import { defineConfig, devices } from "@playwright/test";
import dotenv from "dotenv";
import path from "path";

dotenv.config({ path: path.resolve(__dirname, ".env.local") });

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "html",
  use: {
    baseURL: process.env.BASE_URL ?? "http://platform.localhost",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
```

- [ ] **Step 4: Create project.json**

```json
{
  "name": "e2e",
  "$schema": "../../node_modules/nx/schemas/project-schema.json",
  "sourceRoot": "apps/e2e",
  "projectType": "application",
  "targets": {
    "e2e": {
      "executor": "nx:run-commands",
      "options": {
        "command": "npx playwright test",
        "cwd": "apps/e2e"
      }
    }
  }
}
```

- [ ] **Step 5: Create .env.local**

```env
BASE_URL=http://platform.localhost
TENANT_BASE_URL_TEMPLATE=http://{slug}.localhost
API_URL=http://localhost:8080/api/v1
KEYCLOAK_URL=http://auth.localhost
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=admin
PLATFORM_ADMIN_EMAIL=admin@webcontify.io
PLATFORM_ADMIN_PASSWORD=admin
```

- [ ] **Step 6: Install dependencies**

```bash
cd apps/e2e && pnpm install && npx playwright install chromium
```

- [ ] **Step 7: Commit**

```bash
git add apps/e2e/package.json apps/e2e/tsconfig.json apps/e2e/playwright.config.ts \
       apps/e2e/project.json apps/e2e/.env.local pnpm-lock.yaml
git commit -m "chore: scaffold E2E test project

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Migrate + Refactor Helpers (English)

**Files:**
- Create: `apps/e2e/helpers/auth.ts`
- Create: `apps/e2e/helpers/keycloak-admin.ts`
- Create: `apps/e2e/helpers/api.ts`
- Create: `apps/e2e/helpers/ui.ts`
- Create: `apps/e2e/helpers/fixtures.ts`

- [ ] **Step 1: Migrate and refactor all helpers**

Migrate existing helpers from `apps/platform/e2e/helpers/` to `apps/e2e/helpers/`. Key changes:
- **All comments and error messages in English**
- **Environment variables from .env.local** (not hardcoded)
- **api.ts:** Add stack-based `ApiClient` class with `cleanup()` method
- **auth.ts:** Add `loginAsTenantUser(page, slug, username, password)` that navigates to `TENANT_BASE_URL_TEMPLATE.replace("{slug}", slug)` before login
- **fixtures.ts:** Playwright custom test fixtures with `platformAdminPage`, `apiClient`

The `ApiClient` class pattern:
```typescript
export class ApiClient {
  private cleanupStack: Array<{ type: string; id: string; realm?: string }> = [];
  private token: string | null = null;

  async getToken(): Promise<string> { /* ... */ }
  async createTenant(name: string): Promise<TenantData> { /* ... push to stack ... */ }
  async createKeycloakUser(realm: string, data: KeycloakUserData): Promise<string> { /* ... push to stack ... */ }
  async cleanup(): Promise<void> { /* reverse order, best-effort */ }
}
```

- [ ] **Step 2: Verify helpers compile**

```bash
cd apps/e2e && npx tsc --noEmit
```

- [ ] **Step 3: Commit**

```bash
git add apps/e2e/helpers/
git commit -m "feat: migrate and refactor E2E helpers to English with ApiClient cleanup

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Add data-testid to Frontend Components

**Files:**
- Modify: all frontend components listed in File Structure

- [ ] **Step 1: Add data-testid attributes to role components**

Read each file, add `data-testid` attributes to key elements following the naming convention from the spec. Examples:

```tsx
// role-table.tsx
<Table data-testid="role-table">
<TableRow data-testid="role-table-row">

// create-role-dialog.tsx
<DialogContent data-testid="create-role-dialog">
<Input data-testid="role-name-input" ... />
<Button data-testid="create-role-submit" type="submit">
<Button data-testid="create-role-cancel" ...>
```

- [ ] **Step 2: Add data-testid to user components**

```tsx
// user-table.tsx
<Table data-testid="user-table">
// invite-user-dialog.tsx
<DialogContent data-testid="invite-user-dialog">
<Input data-testid="user-email-input" ... />
<Select data-testid="copy-roles-select" ... />
<Button data-testid="invite-user-submit" ...>
```

- [ ] **Step 3: Add data-testid to admin components**

```tsx
// admin-table.tsx
<Table data-testid="admin-table">
// invite-admin-dialog.tsx
<DialogContent data-testid="invite-admin-dialog">
<Button data-testid="invite-admin-submit" ...>
```

- [ ] **Step 4: Add data-testid to nav-user.tsx**

```tsx
<DropdownMenu data-testid="nav-user-menu">
<DropdownMenuItem data-testid="password-change-link" ...>
```

- [ ] **Step 5: Verify frontend still builds**

```bash
npx nx build platform
```

- [ ] **Step 6: Verify frontend tests still pass**

```bash
npx nx test platform
```

- [ ] **Step 7: Commit**

```bash
git add apps/platform/src/
git commit -m "feat: add data-testid attributes to all testable UI components

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Priority 1 Tests — Auth Flows

**Files:**
- Create: `apps/e2e/tests/auth/platform-login.spec.ts`
- Create: `apps/e2e/tests/auth/tenant-login.spec.ts`
- Create: `apps/e2e/tests/auth/token-expiry.spec.ts`

- [ ] **Step 1: Write platform-login.spec.ts**

Tests (migrated from auth-flow.spec.ts, refactored to English + data-testid):
- `should login as platform admin and see tenants page`
- `should persist session across page reload`
- `should logout and redirect to signin`
- `should redirect unauthenticated user to keycloak`

- [ ] **Step 2: Write tenant-login.spec.ts**

Tests (self-contained — creates own tenant + user in beforeAll via apiClient):
- `should login as tenant user via tenant subdomain`
- `should redirect to dashboard after login`

- [ ] **Step 3: Write token-expiry.spec.ts**

Tests:
- `should reject API call with invalid JWT`
- `should redirect to login after session revocation`

For session revocation: use Keycloak Admin API to delete user sessions (`DELETE /admin/realms/{realm}/sessions/{sessionId}`).

- [ ] **Step 4: Run auth tests**

```bash
cd apps/e2e && npx playwright test tests/auth/
```

- [ ] **Step 5: Commit**

```bash
git add apps/e2e/tests/auth/
git commit -m "test: add E2E auth flow tests (platform login, tenant login, token expiry)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Priority 1 Tests — Tenant CRUD + Config

**Files:**
- Create: `apps/e2e/tests/tenants/tenant-crud.spec.ts`
- Create: `apps/e2e/tests/tenants/tenant-config.spec.ts`

- [ ] **Step 1: Write tenant-crud.spec.ts**

Tests:
- `should create a tenant and see it in the list`
- `should show provisioning status`
- `should navigate to tenant detail page`

- [ ] **Step 2: Write tenant-config.spec.ts** (migrated from existing)

Tests (migrated from existing tenant-config.spec.ts, adapted to data-testid + English):
- `should show branding, email, and domain tabs`
- `should save branding configuration`
- `should validate hex color format`

- [ ] **Step 3: Run tenant tests**

```bash
cd apps/e2e && npx playwright test tests/tenants/
```

- [ ] **Step 4: Commit**

```bash
git add apps/e2e/tests/tenants/
git commit -m "test: add E2E tenant CRUD and config tests

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Priority 2 Tests — Users + Invite

**Files:**
- Create: `apps/e2e/tests/users/invite-user.spec.ts`
- Create: `apps/e2e/tests/users/invite-with-role-clone.spec.ts`

- [ ] **Step 1: Write invite-user.spec.ts**

Setup: Create tenant + Keycloak user via apiClient in beforeAll. Login as tenant admin.

Tests:
- `should invite a user and see them in the list with INVITED status`
- `should activate invited user after keycloak password setup`
- `should deactivate a user`
- `should delete a user`

- [ ] **Step 2: Write invite-with-role-clone.spec.ts**

Setup: Create tenant + admin user + custom role via API.

Tests:
- `should create a custom role`
- `should invite user A and assign the custom role`
- `should invite user B with copied roles from user A`
- `should verify user B has the same role as user A`

- [ ] **Step 3: Run user tests**

```bash
cd apps/e2e && npx playwright test tests/users/
```

- [ ] **Step 4: Commit**

```bash
git add apps/e2e/tests/users/
git commit -m "test: add E2E user invite and role clone tests

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Priority 2 Tests — Roles

**Files:**
- Create: `apps/e2e/tests/roles/role-crud.spec.ts`
- Create: `apps/e2e/tests/roles/role-assignment.spec.ts`

- [ ] **Step 1: Write role-crud.spec.ts**

Setup: Login as tenant admin (tenant created via apiClient).

Tests:
- `should display system roles (Admin, Editor, Viewer, Inviter)`
- `should create a custom role`
- `should edit a custom role name and description`
- `should not allow editing system roles`
- `should not allow deleting system roles`
- `should delete a custom role after removing assignees`

- [ ] **Step 2: Write role-assignment.spec.ts**

Tests:
- `should assign a user to a role from role detail page`
- `should remove a user from a role`
- `should prevent removing last admin assignee`

- [ ] **Step 3: Run role tests**

```bash
cd apps/e2e && npx playwright test tests/roles/
```

- [ ] **Step 4: Commit**

```bash
git add apps/e2e/tests/roles/
git commit -m "test: add E2E role CRUD and assignment tests

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Priority 2 Tests — Platform Admins

**Files:**
- Create: `apps/e2e/tests/admins/platform-admin-crud.spec.ts`

- [ ] **Step 1: Write platform-admin-crud.spec.ts**

Setup: Login as existing platform admin.

Tests:
- `should list platform admins`
- `should invite a new platform admin`
- `should login as newly invited admin`
- `should deactivate an admin`
- `should not allow deactivating the last admin`
- `should delete an admin`
- `should trigger password reset for another admin`

- [ ] **Step 2: Run admin tests**

```bash
cd apps/e2e && npx playwright test tests/admins/
```

- [ ] **Step 3: Commit**

```bash
git add apps/e2e/tests/admins/
git commit -m "test: add E2E platform admin CRUD tests

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Priority 3 Tests — Security

**Files:**
- Create: `apps/e2e/tests/security/cross-tenant-isolation.spec.ts`
- Create: `apps/e2e/tests/security/permission-enforcement.spec.ts`
- Create: `apps/e2e/tests/security/unauthorized-access.spec.ts`
- Create: `apps/e2e/tests/security/input-validation.spec.ts`

- [ ] **Step 1: Write cross-tenant-isolation.spec.ts**

Setup: Create Tenant A + Tenant B via apiClient, each with their own admin user.

Tests:
- `should only see own tenant users when logged in as Tenant A`
- `should reject API call from Tenant A token against Tenant B subdomain`
- `should not see Tenant B roles when logged in as Tenant A`

- [ ] **Step 2: Write permission-enforcement.spec.ts**

Setup: Create tenant with admin + non-admin user (viewer role only).

Tests:
- `should not show role management to user without admin role`
- `should not show invite button to user without inviter permission`
- `should reject tenant user calling admin API endpoints`

- [ ] **Step 3: Write unauthorized-access.spec.ts**

Tests:
- `should return 401 for unauthenticated API call`
- `should redirect unauthenticated page visit to signin`
- `should deny tenant user access to platform admin pages`

- [ ] **Step 4: Write input-validation.spec.ts**

Tests:
- `should safely handle SQL injection attempt in role name`
- `should escape XSS attempt in tenant name`
- `should reject oversized input`

- [ ] **Step 5: Run security tests**

```bash
cd apps/e2e && npx playwright test tests/security/
```

- [ ] **Step 6: Commit**

```bash
git add apps/e2e/tests/security/
git commit -m "test: add E2E security tests (isolation, permissions, input validation)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Delete Old E2E Files + Full Suite Run

**Files:**
- Delete: `apps/platform/e2e/` (entire directory)
- Delete: `apps/platform/playwright.config.ts`
- Modify: `apps/platform/package.json` (remove playwright devDependency if present)

- [ ] **Step 1: Delete old E2E files**

```bash
rm -rf apps/platform/e2e/
rm -f apps/platform/playwright.config.ts
```

- [ ] **Step 2: Run full E2E suite**

```bash
cd apps/e2e && npx playwright test
```

- [ ] **Step 3: Verify frontend build + tests still pass**

```bash
npx nx test platform && npx nx build platform
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: remove old E2E files from platform app, migrate complete

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: PROGRESS.md Update

- [ ] **Step 1: Update PROGRESS.md**

Add Phase 3f section with all completed tasks.

- [ ] **Step 2: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md — Phase 3f complete

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```
