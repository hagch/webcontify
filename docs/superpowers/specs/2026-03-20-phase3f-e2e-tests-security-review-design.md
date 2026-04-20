# Phase 3f: E2E Tests + Security Review — Design Spec

## Overview

Comprehensive E2E test suite covering all critical flows from Phase 3a–3e, combined with security-focused tests for tenant isolation, permission enforcement, and unauthorized access prevention.

Key changes:
- Separate E2E project (`apps/e2e/`) in the monorepo — environment-agnostic, configurable for local/staging/production
- Migration of existing 3 E2E test files from `apps/platform/e2e/`
- `data-testid` attributes on all testable UI elements
- All test code, comments, and descriptions in English

---

## Project Structure

### New E2E Project

```
apps/e2e/
  ├── playwright.config.ts
  ├── package.json
  ├── tsconfig.json
  ├── .env.local
  ├── tests/
  │   ├── auth/
  │   │   ├── platform-login.spec.ts
  │   │   ├── tenant-login.spec.ts
  │   │   └── token-expiry.spec.ts
  │   ├── tenants/
  │   │   ├── tenant-crud.spec.ts
  │   │   └── tenant-config.spec.ts
  │   ├── users/
  │   │   ├── invite-user.spec.ts
  │   │   └── invite-with-role-clone.spec.ts
  │   ├── roles/
  │   │   ├── role-crud.spec.ts
  │   │   └── role-assignment.spec.ts
  │   ├── admins/
  │   │   └── platform-admin-crud.spec.ts
  │   └── security/
  │       ├── cross-tenant-isolation.spec.ts
  │       ├── permission-enforcement.spec.ts
  │       ├── unauthorized-access.spec.ts
  │       └── input-validation.spec.ts
  └── helpers/
      ├── auth.ts                (Keycloak login helpers)
      ├── keycloak-admin.ts      (Keycloak Admin API for test setup)
      ├── api.ts                 (Backend API client for setup/teardown)
      └── fixtures.ts            (Playwright custom test fixtures)
```

### Environment Configuration

Via environment variables (`.env.local` for local dev):

```env
BASE_URL=http://platform.localhost
TENANT_BASE_URL_TEMPLATE=http://{slug}.localhost
API_URL=http://api.localhost
KEYCLOAK_URL=http://auth.localhost
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=admin
PLATFORM_ADMIN_EMAIL=admin@webcontify.io
PLATFORM_ADMIN_PASSWORD=admin
```

`TENANT_BASE_URL_TEMPLATE` is used by `loginAsTenantUser(page, slug)` to dynamically set the base URL for tenant-specific pages.

For staging/production: override via CI environment variables or separate `.env` files.

### Migration from apps/platform/e2e/

The existing 3 test files and helpers are moved to `apps/e2e/`:
- `auth-flow.spec.ts` → `tests/auth/platform-login.spec.ts` (refactored)
- `tenant-config.spec.ts` → `tests/tenants/tenant-config.spec.ts` (refactored)
- `auth-self-service.spec.ts` → split into `tests/auth/tenant-login.spec.ts` + `tests/users/invite-user.spec.ts`
- `helpers/*` → `helpers/*` (adapted to new structure)
- `helpers/ui.ts` → `helpers/ui.ts` (createTenantViaUI and other UI interaction helpers)

**auth-self-service.spec.ts split strategy:** The serial test chain (Tenant creation → User creation → User login → Branding) is split by making each file self-contained. `invite-user.spec.ts` creates its own test tenant in `beforeAll` via `apiClient`. `tenant-login.spec.ts` creates its own tenant + user via API. No shared state between files.

Old files in `apps/platform/e2e/` and `apps/platform/playwright.config.ts` are deleted.

---

## Element Selection: data-testid Convention

All testable UI elements get `data-testid` attributes. Naming convention: **kebab-case**, descriptive.

### Naming Patterns

```
// Tables
data-testid="role-table"
data-testid="role-table-row"
data-testid="user-table"
data-testid="user-table-row"
data-testid="admin-table"
data-testid="admin-table-row"

// Dialogs
data-testid="create-role-dialog"
data-testid="invite-user-dialog"
data-testid="invite-admin-dialog"
data-testid="delete-confirm-dialog"
data-testid="add-assignees-dialog"

// Form Fields
data-testid="role-name-input"
data-testid="role-description-input"
data-testid="user-email-input"
data-testid="user-firstname-input"
data-testid="user-lastname-input"
data-testid="copy-roles-select"
data-testid="admin-email-input"
data-testid="admin-firstname-input"
data-testid="admin-lastname-input"

// Buttons (prefixed with dialog/form context)
data-testid="create-role-button"
data-testid="invite-user-button"
data-testid="invite-admin-button"
data-testid="create-role-submit"
data-testid="create-role-cancel"
data-testid="invite-user-submit"
data-testid="invite-user-cancel"
data-testid="invite-admin-submit"
data-testid="invite-admin-cancel"
data-testid="edit-role-submit"

// Row Actions
data-testid="actions-menu-{id}"
data-testid="action-edit"
data-testid="action-delete"
data-testid="action-deactivate"
data-testid="action-activate"
data-testid="action-reset-password"
data-testid="action-clone-roles"

// Status/Badges
data-testid="status-badge"
data-testid="system-role-badge"

// Navigation
data-testid="nav-user-menu"
data-testid="password-change-link"

// Role Detail
data-testid="role-assignees-tab"
data-testid="role-permissions-tab"
data-testid="add-assignee-button"
data-testid="remove-assignee-{userId}"

// Loading/Empty States
data-testid="table-skeleton"
data-testid="empty-state"
```

### Components to Add data-testid

All components from Phase 3d and 3e need `data-testid` attributes:
- `features/roles/components/role-table.tsx`
- `features/roles/components/create-role-dialog.tsx`
- `features/roles/components/edit-role-form.tsx`
- `features/roles/components/role-assignees-table.tsx`
- `features/roles/components/add-assignees-dialog.tsx`
- `features/roles/components/role-permissions-preview.tsx`
- `features/users/components/user-table.tsx`
- `features/users/components/invite-user-dialog.tsx`
- `features/users/components/user-actions-menu.tsx`
- `features/admins/components/admin-table.tsx`
- `features/admins/components/invite-admin-dialog.tsx`
- `features/admins/components/admin-actions-menu.tsx`
- `components/layout/nav-user.tsx`

Existing Phase 2e components (tenant list, tenant detail, create tenant dialog) also need `data-testid` for the migrated tests.

---

## Playwright Fixtures

Custom fixtures for authenticated pages and API cleanup:

```typescript
// helpers/fixtures.ts
import { test as base, Page } from "@playwright/test";

type TestFixtures = {
  platformAdminPage: Page;
  tenantUserPage: Page;
  apiClient: ApiClient;
};

export const test = base.extend<TestFixtures>({
  platformAdminPage: async ({ page }, use) => {
    await loginAsAdmin(page);
    await use(page);
  },
  tenantUserPage: async ({ page }, use) => {
    await loginAsTenantUser(page);
    await use(page);
  },
  apiClient: async ({}, use) => {
    const client = await createApiClient();
    await use(client);
    await client.cleanup();
  },
});
```

### Cleanup Strategy

Each test cleans up its own data via `apiClient.cleanup()` in fixture teardown. Tests are independent and can run in any order.

**Concrete mechanism:** `ApiClient` uses a stack-based undo pattern:

```typescript
class ApiClient {
  private cleanupStack: Array<{ type: string; id: string; realm?: string }> = [];

  async createTenant(name: string): Promise<string> {
    const tenantId = await api.post("/admin/tenants", { name });
    this.cleanupStack.push({ type: "tenant", id: tenantId });
    return tenantId;
  }

  async createUser(realm: string, email: string): Promise<string> {
    const userId = await keycloak.createUser(realm, { email });
    this.cleanupStack.push({ type: "keycloak-user", id: userId, realm });
    return userId;
  }

  async cleanup(): Promise<void> {
    // Reverse order: users before tenants, roles before users
    for (const item of [...this.cleanupStack].reverse()) {
      try {
        switch (item.type) {
          case "keycloak-user": await keycloak.deleteUser(item.realm!, item.id); break;
          case "tenant": await api.delete(`/admin/tenants/${item.id}`); break;
          case "role": await api.delete(`/tenant/roles/${item.id}`); break;
        }
      } catch (e) {
        // Best-effort cleanup: log but don't fail the test
        console.warn(`Cleanup failed for ${item.type}:${item.id}`, e);
      }
    }
  }
}
```

Partial failures during setup: if a test fails mid-setup, only resources that were successfully created are in the stack, so cleanup is always safe.

---

## Test Scenarios

### Priority 1 — Critical Flows

**auth/platform-login.spec.ts:**
- Platform Admin logs in via Keycloak PKCE → redirected to /tenants
- Logout removes session, redirects to signin
- Session persists across page reload
- OIDC Discovery endpoint responds

**auth/tenant-login.spec.ts:**
- Tenant User logs in via tenant-specific realm → redirected to /dashboard
- Token refresh keeps session active
- Tenant subdomain routes to correct realm

**auth/token-expiry.spec.ts:**
- Invalid/manipulated JWT rejected with 401 (direct API call with garbage token)
- Note: Real token expiry testing requires short-lived access tokens. Approach: use Keycloak Admin API to revoke the user's session (`DELETE /admin/realms/{realm}/sessions/{sessionId}`), then verify the next API call returns 401 and the frontend redirects to login. This simulates token invalidation without waiting for natural expiry.

**tenants/tenant-crud.spec.ts:**
- Create tenant via UI → provisioning completes (poll status)
- Tenant appears in list with correct name
- Edit tenant name → reflected in list
- Tenant detail page shows provisioning status

**tenants/tenant-config.spec.ts:**
- Migration of existing tenant-config tests (branding, email, domain tabs)
- Adapted to use data-testid selectors

### Priority 2 — Phase 3d/3e Features

**users/invite-user.spec.ts:**
- Invite user → appears in user list with INVITED status
- Create Keycloak password for invited user → login succeeds → status ACTIVE
- Deactivate user → cannot login
- Delete user → removed from list

**users/invite-with-role-clone.spec.ts:**
- Create custom role "Editor"
- Invite User A, assign "Editor" role
- Invite User B with "copy roles from User A"
- Verify User B has "Editor" role on user detail or role detail page

**roles/role-crud.spec.ts:**
- Create custom role → appears in list
- Edit role name and description
- Delete role (remove assignees first, then delete)
- System roles (Admin, Editor, Viewer, Inviter) visible but not editable/deletable

**roles/role-assignment.spec.ts:**
- Add user to role from role detail page (assignees tab)
- Add role to user from user roles section
- Remove user from role
- Last admin protection — cannot remove last Admin assignee (error message shown)

**admins/platform-admin-crud.spec.ts:**
- Invite new platform admin → appears in admin list
- Login as new admin → has full access to /tenants
- Deactivate admin → cannot login
- Delete admin → removed from list
- Last admin protection — cannot delete/deactivate only remaining admin
- Reset password → required action set (next login forces password change)

### Priority 3 — Security

**security/cross-tenant-isolation.spec.ts:**
- Create Tenant A and Tenant B with separate admin users
- Login as Tenant A user → API call to list users returns only Tenant A users
- Direct API call with Tenant A JWT against Tenant B subdomain → rejected
- Tenant A user cannot see Tenant B roles

**security/permission-enforcement.spec.ts:**
- User without Admin role cannot access role management page
- User without Inviter permission cannot see invite button
- Tenant user cannot call /api/v1/admin/ endpoints → 403

**security/unauthorized-access.spec.ts:**
- Unauthenticated API call → 401
- Unauthenticated page visit → redirect to signin
- Tenant user navigating to platform admin pages → redirect/forbidden

**security/input-validation.spec.ts:**
- SQL injection attempt in role name (`'; DROP TABLE tenant_role;--`) → rejected with 400 or safely stored
- XSS attempt in tenant name (`<script>alert(1)</script>`) → escaped in UI, no script execution
- Oversized input (10k character description) → rejected with 400

---

## Testing

### Running E2E Tests

```bash
# Full suite (requires running dev environment)
npx nx e2e e2e

# Specific test file
npx nx e2e e2e -- --grep "platform-login"

# Headed mode (visible browser)
npx nx e2e e2e -- --headed

# Specific project/browser
npx nx e2e e2e -- --project=chromium
```

### Prerequisites

Full dev environment must be running:
```bash
pnpm dev  # or: npx nx up infra && npx nx serve backend && npx nx serve platform
```

### CI Integration

E2E tests run after deployment in CI pipeline. Environment variables configure target URLs. Smoke test subset can be tagged with `@smoke` annotation for quick validation.

---

## Nx Project Configuration

The `apps/e2e/` project needs a `project.json` (or entry in `nx.json`) with an `e2e` target:

```json
{
  "name": "e2e",
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

Smoke tests: All Priority 1 tests are tagged with `@smoke` via Playwright's `test.describe` tag feature. Run with `npx nx e2e e2e -- --grep @smoke`.

---

## Scope Exclusion (not in Phase 3f)

- Performance/load testing
- Visual regression testing
- Mobile viewport testing (desktop Chromium only for now)
- Multi-browser testing (Chromium only, Firefox/WebKit later)
- Accessibility testing (a11y audit is a separate effort)
- Brute-force login protection testing (Keycloak default policy, verified manually)
- Security logging/monitoring audit (separate effort, Keycloak Admin Events can be enabled later)
- RLS-level isolation testing (covered by backend integration tests with Testcontainers, not E2E)
