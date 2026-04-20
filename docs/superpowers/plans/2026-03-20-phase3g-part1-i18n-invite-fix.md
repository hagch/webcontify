# Phase 3g Part 1: i18n Audit + Invite Flow Fix — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Internationalize all frontend strings (DE + EN) and fix the invite flow to require role assignment with privilege escalation prevention.

**Architecture:** next-intl with cookie-based locale switching (no URL routing). Backend returns i18n keys in RFC 7807 `code` field. Invite flow requires `roleIds` as mandatory field, backend validates privilege escalation.

**Tech Stack:** next-intl, React Hook Form, Zod, Spring Boot, OpenFGA

**Design Spec:** `docs/superpowers/specs/2026-03-20-phase3g-part1-i18n-invite-fix-design.md`

---

## File Structure

### i18n — Modified Files
- `apps/platform/src/i18n/request.ts` — dynamic locale from cookie
- `apps/platform/messages/en.json` — complete English translations
- `apps/platform/messages/de.json` — complete German translations
- All components in `features/roles/`, `features/users/`, `features/admins/`, `components/layout/` — useTranslations()

### Invite Fix — Modified Files
- `openapi/identity-api.yaml` — InviteUserRequest with roleIds
- `apps/backend/src/main/java/io/webcontify/identity/service/UserService.java` — invite() with roleIds + privilege escalation check
- `apps/backend/src/test/java/io/webcontify/identity/service/UserServiceTest.java` — updated tests
- `apps/platform/src/features/users/components/invite-user-dialog.tsx` — role multi-select
- `apps/platform/src/features/users/schemas/invite-user.ts` — roleIds in schema

---

## Task 1: i18n — Locale Switching + Complete Message Files

**Files:**
- Modify: `apps/platform/src/i18n/request.ts`
- Modify: `apps/platform/messages/en.json`
- Modify: `apps/platform/messages/de.json`

- [ ] **Step 1: Enable dynamic locale from cookie**

`src/i18n/request.ts`:
```typescript
import { getRequestConfig } from "next-intl/server";
import { cookies } from "next/headers";

export default getRequestConfig(async () => {
  const cookieStore = await cookies();
  const locale = cookieStore.get("NEXT_LOCALE")?.value ?? "en";

  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
  };
});
```

- [ ] **Step 2: Complete en.json with all missing namespaces**

Add namespaces for roles, users, admins, common, errors, profile, auth. All strings that are currently hardcoded in components must have a key here. Read each component to extract the exact strings.

Minimum structure:
```json
{
  "common": {
    "cancel": "Cancel",
    "save": "Save",
    "delete": "Delete",
    "edit": "Edit",
    "create": "Create",
    "search": "Search",
    "loading": "Loading...",
    "noResults": "No results found",
    "confirm": "Confirm",
    "actions": "Actions",
    "name": "Name",
    "description": "Description",
    "email": "Email",
    "status": "Status",
    "createdAt": "Created",
    "type": "Type"
  },
  "errors": {
    "generic": "An error occurred. Please try again.",
    "role.not_found": "Role not found",
    "role.name_already_exists": "A role with this name already exists",
    "role.system_role_cannot_be_modified": "System roles cannot be modified",
    "role.system_role_cannot_be_deleted": "System roles cannot be deleted",
    "role.has_assignees": "Remove all assignees before deleting this role",
    "role.last_admin_cannot_be_removed": "The last admin cannot be removed",
    "role.assignee_not_found": "One or more users not found",
    "user.not_found": "User not found",
    "user.email_already_exists": "This email address is already in use",
    "user.cannot_deactivate_self": "You cannot deactivate yourself",
    "user.cannot_delete_self": "You cannot delete yourself",
    "user.last_admin_cannot_be_removed": "The last admin cannot be removed",
    "user.invalid_status_transition": "Invalid status transition",
    "user.not_invited": "User is not in invited status",
    "invite.source_user_not_found": "Source user not found in this tenant",
    "invite.cannot_assign_admin_role": "You do not have permission to assign the admin role",
    "admin.email_already_exists": "This email address is already in use",
    "admin.not_found": "Admin not found",
    "admin.cannot_modify_self": "You cannot modify your own account",
    "admin.cannot_delete_self": "You cannot delete your own account",
    "admin.last_admin_cannot_be_deactivated": "The last admin cannot be deactivated",
    "admin.last_admin_cannot_be_deleted": "The last admin cannot be deleted",
    "admin.use_account_console_for_own_password": "Use the account settings to change your own password",
    "access.denied": "Access denied",
    "tenant.not_found": "Tenant not found",
    "tenant.name_already_exists": "A tenant with this name already exists"
  },
  "roles": {
    "title": "Roles",
    "create": "Create Role",
    "createDialog": { "title": "Create Role", "description": "Create a new custom role.", "nameLabel": "Name", "namePlaceholder": "e.g. Editor", "descriptionLabel": "Description", "descriptionPlaceholder": "Optional description", "submit": "Create", "cancel": "Cancel" },
    "table": { "name": "Name", "description": "Description", "type": "Type", "members": "Members", "actions": "Actions", "system": "System", "custom": "Custom", "empty": "No roles created yet", "deleteTitle": "Delete Role", "deleteDescription": "Are you sure you want to delete the role \"{name}\"?", "deleteConfirm": "Delete", "deleteCancel": "Cancel" },
    "detail": { "assignees": "Assigned Users", "permissions": "Permissions", "addAssignee": "Add User", "removeAssignee": "Remove", "noAssignees": "No users assigned", "permissionsReadonly": "Permissions cannot be edited at this time.", "editName": "Edit name", "editDescription": "Edit description" },
    "permissions": { "user_management": "User Management", "role_management": "Role Management", "tenant_config": "Tenant Configuration", "reader": "Read", "editor": "Edit", "creator": "Create", "deleter": "Delete", "inviter": "Invite" },
    "toast": { "created": "Role created successfully", "updated": "Role updated successfully", "deleted": "Role deleted successfully", "assigneesUpdated": "Assignees updated" }
  },
  "users": {
    "title": "Users",
    "invite": "Invite User",
    "inviteDialog": { "title": "Invite User", "description": "Invite a new user to your workspace.", "emailLabel": "Email", "emailPlaceholder": "max@example.com", "firstNameLabel": "First Name", "firstNamePlaceholder": "Max", "lastNameLabel": "Last Name", "lastNamePlaceholder": "Mustermann", "rolesLabel": "Roles", "rolesPlaceholder": "Select at least one role", "copyRolesLabel": "Copy permissions from", "copyRolesPlaceholder": "Select user (optional)", "submit": "Invite", "cancel": "Cancel" },
    "table": { "name": "Name", "email": "Email", "status": "Status", "invitedAt": "Invited", "actions": "Actions", "empty": "No users yet", "deleteTitle": "Delete User", "deleteDescription": "Are you sure you want to delete the user \"{name}\"?", "cloneRoles": "Invite with same roles" },
    "status": { "INVITED": "Invited", "ACTIVE": "Active", "DEACTIVATED": "Deactivated" },
    "actions": { "activate": "Activate", "deactivate": "Deactivate", "resendInvite": "Resend invite", "resetPassword": "Reset password", "delete": "Delete" },
    "toast": { "invited": "{name} has been invited", "activated": "User activated", "deactivated": "User deactivated", "deleted": "User deleted", "inviteResent": "Invite resent" }
  },
  "admins": {
    "title": "Administrators",
    "invite": "Invite Admin",
    "inviteDialog": { "title": "Invite Administrator", "description": "Invite a new platform administrator.", "emailLabel": "Email", "firstNameLabel": "First Name", "lastNameLabel": "Last Name", "submit": "Invite", "cancel": "Cancel" },
    "table": { "name": "Name", "email": "Email", "status": "Status", "createdAt": "Created", "actions": "Actions", "empty": "No administrators yet", "deleteTitle": "Delete Administrator", "deleteDescription": "Are you sure you want to delete the administrator \"{name}\"?" },
    "status": { "active": "Active", "deactivated": "Deactivated" },
    "actions": { "activate": "Activate", "deactivate": "Deactivate", "resetPassword": "Reset password", "delete": "Delete" },
    "toast": { "invited": "{name} has been invited", "activated": "Administrator activated", "deactivated": "Administrator deactivated", "deleted": "Administrator deleted", "passwordReset": "Password reset initiated" }
  },
  "nav": {
    "tenants": "Tenants",
    "admins": "Administrators",
    "settings": "Settings",
    "dashboard": "Dashboard",
    "apps": "Apps",
    "users": "Users",
    "roles": "Roles",
    "profile": "Profile",
    "changePassword": "Change Password",
    "signOut": "Sign out",
    "theme": "Theme",
    "themeLight": "Light",
    "themeDark": "Dark",
    "themeSystem": "System"
  }
}
```

- [ ] **Step 3: Create matching de.json**

Copy en.json structure, translate all values to German.

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/i18n/request.ts apps/platform/messages/
git commit -m "feat: enable locale switching and complete i18n message files (DE + EN)"
```

---

## Task 2: i18n — Migrate All Components to useTranslations()

**Files:**
- All components listed in the spec

- [ ] **Step 1: Migrate role components**

Read and update each file in `features/roles/components/`:
- `role-table.tsx` — `useTranslations("roles")`
- `create-role-dialog.tsx` — `useTranslations("roles")`
- `edit-role-form.tsx` — `useTranslations("roles")`
- `role-assignees-table.tsx` — `useTranslations("roles")`
- `add-assignees-dialog.tsx` — `useTranslations("roles")`
- `role-permissions-preview.tsx` — `useTranslations("roles")`

Also page files: `roles/page.tsx`, `roles/[roleId]/page.tsx`

- [ ] **Step 2: Migrate user components**

- `user-table.tsx` — `useTranslations("users")`
- `invite-user-dialog.tsx` — `useTranslations("users")`
- `user-actions-menu.tsx` — `useTranslations("users")`
- `user-status-badge.tsx` — `useTranslations("users")`
- `users/page.tsx`

- [ ] **Step 3: Migrate admin components**

- `admin-table.tsx` — `useTranslations("admins")`
- `invite-admin-dialog.tsx` — `useTranslations("admins")`
- `admin-actions-menu.tsx` — `useTranslations("admins")`
- `admins/page.tsx`

- [ ] **Step 4: Migrate nav-user.tsx**

- `nav-user.tsx` — `useTranslations("nav")`

- [ ] **Step 5: Migrate error handlers**

In all dialog components (create-role, invite-user, invite-admin), change error handling to use i18n:
```tsx
const te = useTranslations("errors");
// In catch block:
const errorCode = err.code ?? "generic";
toast.error(te(errorCode));
```

- [ ] **Step 6: Verify build + tests**

```bash
npx nx build platform && npx nx test platform
```

- [ ] **Step 7: Commit**

```bash
git add apps/platform/src/
git commit -m "feat: migrate all components to useTranslations() for i18n"
```

---

## Task 3: Invite Flow — Backend (OpenAPI + Service + Tests)

**Files:**
- Modify: `openapi/identity-api.yaml`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/UserService.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/service/UserServiceTest.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/api/UserControllerIntegrationTest.java`

- [ ] **Step 1: Update OpenAPI spec**

In `identity-api.yaml`, change `InviteUserRequest`:
- Add `roleIds` as required field (array of UUID, minItems: 1, maxItems: 50)
- `copyRolesFromUserId` stays optional but note it's frontend-only convenience

Regenerate: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate`

- [ ] **Step 2: Update UserService.invite()**

- Change signature: add `List<UUID> roleIds` parameter
- Remove `permissionService.grant(userId, "admin", "tenant", tenantId)`
- Add privilege escalation check:
  ```java
  // Check if non-admin tries to assign admin role
  var adminRole = roleRepository.findActiveByName("Admin");
  if (adminRole.isPresent() && roleIds.contains(adminRole.get().getId())) {
      var tenantInfo = TenantContext.getTenantInformation();
      var isAdmin = permissionService.check(new CheckPermissionRequest(
          resolveCurrentUserId(), "admin", "tenant", tenantInfo.tenantId()));
      if (!isAdmin) {
          throw new ForbiddenException("invite.cannot_assign_admin_role");
      }
  }
  ```
- After successful user creation: `roleService.setRolesForUser(keycloakUserId, roleIds)`
- On role assignment failure: rollback (delete Keycloak user + soft-delete DB entry)
- `copyRolesFromUserId` logic: if set AND roleIds is empty, read roles from source user. If roleIds is set, ignore copyRolesFromUserId.

- [ ] **Step 3: Update tests**

- All existing `userService.invite(email, firstName, lastName, null)` calls need roleIds parameter
- Change `verify(permissionService).grant(any())` to `verify(roleService).setRolesForUser(any(), any())`
- Add: `shouldRejectInviteWhenNonAdminAssignsAdminRole`
- Add: `shouldRollbackWhenRoleAssignmentFails`

- [ ] **Step 4: Update UserController**

Pass `request.getRoleIds()` to service.

- [ ] **Step 5: Run tests**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

- [ ] **Step 6: Commit**

```bash
git add openapi/identity-api.yaml apps/backend/
git commit -m "feat: require roleIds in invite flow with privilege escalation prevention"
```

---

## Task 4: Invite Flow — Frontend (Role Multi-Select in Dialog)

**Files:**
- Modify: `apps/platform/src/features/users/components/invite-user-dialog.tsx`
- Modify: `apps/platform/src/features/users/schemas/invite-user.ts`

- [ ] **Step 1: Update Zod schema**

```typescript
export const inviteUserSchema = z.object({
  email: z.string().email(/* i18n key */).max(255),
  firstName: z.string().min(1).max(255),
  lastName: z.string().min(1).max(255),
  roleIds: z.array(z.string().uuid()).min(1, /* i18n: at least one role */),
  copyRolesFromUserId: z.string().uuid().optional(),
});
```

- [ ] **Step 2: Add role multi-select to invite dialog**

Use shadcn Checkbox group or multi-select pattern:
- Load roles via `useListRoles()`
- Render each role as a Checkbox with label
- When `copyRolesFromUserId` is selected, pre-fill the checkboxes with source user's roles
- roleIds is required — form cannot submit without at least one role checked

- [ ] **Step 3: Regenerate Orval**

```bash
npx nx generate platform
```

- [ ] **Step 4: Verify**

```bash
npx nx build platform && npx nx test platform
```

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/
git commit -m "feat: add role multi-select to invite dialog (required field)"
```

---

## Task 5: E2E Tests Update + Build Verification

- [ ] **Step 1: Update E2E invite tests**

The invite tests in `apps/e2e/tests/users/` need to include roleIds in the invite flow.

- [ ] **Step 2: Run full E2E suite**

```bash
cd apps/e2e && npx playwright test --reporter=list
```

- [ ] **Step 3: Run full backend + frontend tests**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
npx nx test platform && npx nx build platform
```

- [ ] **Step 4: Update PROGRESS.md**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: update PROGRESS.md — Phase 3g Part 1 complete"
```
