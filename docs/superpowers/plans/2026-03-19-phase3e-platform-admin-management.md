# Phase 3e: Platform Admin Management — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Platform Admin CRUD (Einladen, Auflisten, Deaktivieren, Entfernen, Passwort-Reset) + generischer "Passwort aendern" Link fuer alle User via Keycloak Account Console.

**Architecture:** Keycloak ist einzige Source of Truth fuer Platform Admins. Kein DB-Table, kein OpenFGA. `PLATFORM_ADMIN` Keycloak-Rolle = darf alles (Bypass bleibt). PlatformAdminService delegiert an KeycloakAdminService.

**Tech Stack:** Java 21, Spring Boot 4.0.3, Keycloak Admin Client 26, Next.js 16, React 19, React Query (Orval), shadcn/ui

**Design Spec:** `docs/superpowers/specs/2026-03-19-phase3e-platform-admin-management-design.md`

---

## File Structure

### Backend — New Files
- `apps/backend/src/main/java/io/webcontify/identity/service/PlatformAdminService.java` — CRUD + Schutzlogik
- `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java` — getMe + getAccountUrl (realm-agnostisch)
- `apps/backend/src/main/java/io/webcontify/identity/api/PlatformAdminController.java` — implements PlatformAdminsApi
- `apps/backend/src/main/java/io/webcontify/identity/api/AuthController.java` — implements AuthApi
- `apps/backend/src/test/java/io/webcontify/identity/service/PlatformAdminServiceTest.java` — Unit Tests
- `apps/backend/src/test/java/io/webcontify/identity/api/PlatformAdminControllerIntegrationTest.java` — Integration Tests
- `openapi/platform-admin-api.yaml` — OpenAPI Spec fuer Platform Admin Endpoints (neue Datei)
- `openapi/auth-api.yaml` — OpenAPI Spec fuer Auth Endpoints (neue Datei)

### Backend — Modified Files
- `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java` — neue Methoden
- `apps/backend/src/main/java/io/webcontify/shared/security/SecurityConfiguration.java` — `/api/v1/auth/**` Route
- `apps/backend/src/main/resources/openfga/model.json` — `type platform` entfernen
- `apps/backend/build.gradle` — openApiGenerate Task fuer neue Specs

### Frontend — New Files
- `apps/platform/src/features/admins/components/admin-table.tsx`
- `apps/platform/src/features/admins/components/invite-admin-dialog.tsx`
- `apps/platform/src/features/admins/components/admin-actions-menu.tsx`
- `apps/platform/src/features/admins/hooks/use-admin-actions.ts`
- `apps/platform/src/features/admins/schemas/invite-admin.ts`
- `apps/platform/src/features/admins/components/__tests__/admin-table.test.tsx`

### Frontend — Modified Files
- `apps/platform/src/app/(platform)/admins/page.tsx` — Placeholder ersetzen
- `apps/platform/src/components/layout/nav-user.tsx` — "Passwort aendern" Link

---

## Task 1: OpenFGA Cleanup — `type platform` entfernen

**Files:**
- Modify: `apps/backend/src/main/resources/openfga/model.json`

- [ ] **Step 1: `type platform` Block entfernen**

In `model.json` den gesamten `type platform` Block (aktuell Zeile 7-22) entfernen. Der Block sieht so aus:
```json
{
  "type": "platform",
  "relations": { "admin": { "this": {} } },
  "metadata": { ... }
}
```

- [ ] **Step 2: Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.OpenFgaServiceIntegrationTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/resources/openfga/model.json
git commit -m "chore: remove unused platform type from OpenFGA model

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: OpenAPI Specs — Platform Admin + Auth Endpoints

**Files:**
- Create: `openapi/platform-admin-api.yaml`
- Create: `openapi/auth-api.yaml`
- Modify: `apps/backend/build.gradle` — neue openApiGenerate Tasks

- [ ] **Step 1: platform-admin-api.yaml erstellen**

Neue OpenAPI Spec fuer Platform Admin Endpoints. Pattern wie `tenant-api.yaml` (BITTE LESEN fuer Stil).

Tags: `PlatformAdmins`

Paths:
- `POST /api/v1/admin/admins` (invitePlatformAdmin) — 201 + Location + PlatformAdminResponse
- `GET /api/v1/admin/admins` (listPlatformAdmins) — 200 PlatformAdminListResponse
- `GET /api/v1/admin/admins/{adminId}` (getPlatformAdmin) — 200 PlatformAdminResponse
- `PUT /api/v1/admin/admins/{adminId}/status` (updatePlatformAdminStatus) — 200 PlatformAdminResponse
- `DELETE /api/v1/admin/admins/{adminId}` (deletePlatformAdmin) — 204
- `POST /api/v1/admin/admins/{adminId}/reset-password` (resetPlatformAdminPassword) — 204

Schemas:
```yaml
InvitePlatformAdminRequest:
  type: object
  required: [email, firstName, lastName]
  properties:
    email: { type: string, format: email, maxLength: 255 }
    firstName: { type: string, minLength: 1, maxLength: 255 }
    lastName: { type: string, minLength: 1, maxLength: 255 }

UpdatePlatformAdminStatusRequest:
  type: object
  required: [enabled]
  properties:
    enabled: { type: boolean }

PlatformAdminResponse:
  type: object
  required: [id, email, firstName, lastName, enabled, createdAt]
  properties:
    id: { type: string, format: uuid }
    email: { type: string }
    firstName: { type: string }
    lastName: { type: string }
    enabled: { type: boolean }
    createdAt: { type: string, format: date-time }

PlatformAdminListResponse:
  type: object
  required: [admins]
  properties:
    admins: { type: array, items: { $ref: '#/components/schemas/PlatformAdminResponse' } }
```

- [ ] **Step 2: auth-api.yaml erstellen**

Tags: `Auth`

Paths:
- `GET /api/v1/auth/me` (getMyProfile) — 200 MyProfileResponse
- `GET /api/v1/auth/account-url` (getAccountUrl) — 200 AccountUrlResponse

Schemas:
```yaml
MyProfileResponse:
  type: object
  required: [id, email, firstName, lastName]
  properties:
    id: { type: string, format: uuid }
    email: { type: string }
    firstName: { type: string }
    lastName: { type: string }

AccountUrlResponse:
  type: object
  required: [url]
  properties:
    url: { type: string }
```

- [ ] **Step 3: build.gradle erweitern**

Lies `apps/backend/build.gradle` um das bestehende openApiGenerate Pattern zu verstehen. Fuege Tasks fuer die neuen Specs hinzu (Pattern wie bestehende `openApiGenerateIdentity` Task).

- [ ] **Step 4: Code Generation ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate`
Expected: `PlatformAdminsApi` und `AuthApi` Interfaces generiert

- [ ] **Step 5: Commit**

```bash
git add openapi/platform-admin-api.yaml openapi/auth-api.yaml \
       apps/backend/build.gradle apps/backend/src/main/java/io/webcontify/generated/
git commit -m "feat: add OpenAPI specs for platform admin and auth endpoints

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: KeycloakAdminService Erweiterungen

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java`
- Neue Request/Response Records falls noetig

- [ ] **Step 1: Neue Methoden hinzufuegen**

Folgende Methoden in `KeycloakAdminService.java` ergaenzen (Pattern der bestehenden Methoden folgen):

```java
public List<UserRepresentation> listUsersWithRole(String realmName, String roleName) {
    return keycloak.realm(realmName)
            .roles()
            .get(roleName)
            .getUserMembers();
}

public void assignRealmRole(String realmName, UUID userId, String roleName) {
    var roleRepresentation = keycloak.realm(realmName)
            .roles()
            .get(roleName)
            .toRepresentation();
    keycloak.realm(realmName)
            .users()
            .get(userId.toString())
            .roles()
            .realmLevel()
            .add(List.of(roleRepresentation));
}

public UserRepresentation getUserById(String realmName, UUID userId) {
    return keycloak.realm(realmName)
            .users()
            .get(userId.toString())
            .toRepresentation();
}

public List<org.keycloak.representations.idm.RoleRepresentation> getUserRealmRoles(String realmName, UUID userId) {
    return keycloak.realm(realmName)
            .users()
            .get(userId.toString())
            .roles()
            .realmLevel()
            .listEffective();
}

public void addRequiredAction(String realmName, UUID userId, String action) {
    var userResource = keycloak.realm(realmName)
            .users()
            .get(userId.toString());
    var user = userResource.toRepresentation();
    var actions = user.getRequiredActions() != null
            ? new java.util.ArrayList<>(user.getRequiredActions())
            : new java.util.ArrayList<String>();
    if (!actions.contains(action)) {
        actions.add(action);
    }
    user.setRequiredActions(actions);
    userResource.update(user);
}
```

- [ ] **Step 2: Compile-Check**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java
git commit -m "feat: extend KeycloakAdminService with role and user management methods

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: SecurityConfiguration — `/api/v1/auth/**` Route

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/shared/security/SecurityConfiguration.java`

- [ ] **Step 1: Auth-Route hinzufuegen**

In `securityFilterChain()`, zwischen den bestehenden `requestMatchers`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
    .requestMatchers("/api/v1/auth/**").authenticated()  // NEU
    .requestMatchers("/api/v1/tenant/**").authenticated()
    .anyRequest().denyAll()
)
```

- [ ] **Step 2: Bestehende Tests**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.shared.security.*"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/shared/security/SecurityConfiguration.java
git commit -m "feat: add /api/v1/auth/** route as authenticated in SecurityConfiguration

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: PlatformAdminService + AuthService (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/PlatformAdminService.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/PlatformAdminServiceTest.java`

- [ ] **Step 1: Unit Tests schreiben**

PlatformAdminServiceTest mit Mockito:
- `List`: shouldListAllPlatformAdmins
- `Invite`: shouldInvitePlatformAdmin, shouldThrowConflictWhenEmailExists
- `GetById`: shouldGetAdminById, shouldThrowNotFoundWhenUserLacksPlatformAdminRole
- `UpdateStatus`: shouldDeactivateAdmin, shouldThrowWhenDeactivatingSelf, shouldThrowWhenDeactivatingLastAdmin
- `Delete`: shouldDeleteAdmin, shouldThrowWhenDeletingSelf, shouldThrowWhenDeletingLastAdmin
- `ResetPassword`: shouldResetPassword, shouldThrowWhenResettingOwnPassword

Mock: KeycloakAdminService (die Keycloak Admin Client Aufrufe)

- [ ] **Step 2: Tests ausfuehren — FAIL**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.PlatformAdminServiceTest"`

- [ ] **Step 3: PlatformAdminService implementieren**

```java
@Service
public class PlatformAdminService {
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private final KeycloakAdminService keycloakAdminService;
    @Value("${keycloak.platform-realm:webcontify-platform}")
    private String platformRealmName;

    // list() → keycloakAdminService.listUsersWithRole(platformRealmName, PLATFORM_ADMIN_ROLE)
    //          → Map UserRepresentation → PlatformAdmin domain model
    //
    // invite(email, firstName, lastName)
    //   → createUser (Keycloak 409 → ConflictException("admin.email_already_exists"))
    //   → assignRealmRole
    //   → sendInviteEmail
    //
    // getById(adminId)
    //   → getUserById (Keycloak 404 → NotFoundException("admin.not_found"))
    //   → Pruefe PLATFORM_ADMIN Rolle via getUserRealmRoles → sonst 404
    //
    // updateStatus(adminId, enabled)
    //   → Self-Check (resolveCurrentUserId)
    //   → Last-Admin-Guard (listUsersWithRole.size() <= 1 && !enabled)
    //   → enableUser/disableUser
    //
    // delete(adminId)
    //   → Self-Check
    //   → Last-Admin-Guard
    //   → deleteUser (Hard Delete)
    //
    // resetPassword(adminId)
    //   → Self-Check (nicht sich selbst → 400)
    //   → addRequiredAction("UPDATE_PASSWORD")
}
```

- [ ] **Step 4: AuthService implementieren**

```java
@Service
public class AuthService {
    private final KeycloakAdminService keycloakAdminService;
    @Value("${keycloak.admin.server-url}")
    private String keycloakBaseUrl;

    // getMe() → Realm aus JWT Issuer → getUserById(realm, currentUserId)
    // getAccountUrl() → Realm aus JWT Issuer → keycloakBaseUrl + "/realms/" + realm + "/account/#/security/signingin"
}
```

Realm-Extraktion aus JWT: `SecurityContextHolder` → `JwtAuthenticationToken` → `jwt.getIssuer()` → parse realm name.

- [ ] **Step 5: Tests ausfuehren — PASS**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.PlatformAdminServiceTest"`

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/PlatformAdminService.java \
       apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java \
       apps/backend/src/test/java/io/webcontify/identity/service/PlatformAdminServiceTest.java
git commit -m "feat: add PlatformAdminService and AuthService with TDD

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: PlatformAdminController + AuthController + Integration Tests

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/api/PlatformAdminController.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/api/AuthController.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/api/PlatformAdminControllerIntegrationTest.java`

- [ ] **Step 1: Integration Tests schreiben**

Pattern wie `UserControllerIntegrationTest` aber fuer `/api/v1/admin/admins`:
- shouldInviteAndListAdmins
- shouldRejectSelfDeactivation
- shouldRejectDeletingLastAdmin
- shouldResetPassword

MockBeans: KeycloakAdminService (alle Keycloak-Aufrufe gemockt)

Header: `X-Test-Tenant-Id` ist fuer Platform Admins nicht relevant — stattdessen muss der Test-User als Platform Admin authentifiziert sein. Lies `TestSecurityConfiguration` um zu sehen wie Platform Admin Tests funktionieren.

- [ ] **Step 2: Tests ausfuehren — FAIL**

- [ ] **Step 3: Controller implementieren**

PlatformAdminController implements PlatformAdminsApi — delegiert an PlatformAdminService.
AuthController implements AuthApi — delegiert an AuthService.

- [ ] **Step 4: Tests ausfuehren — PASS**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.api.PlatformAdminControllerIntegrationTest"`

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/api/PlatformAdminController.java \
       apps/backend/src/main/java/io/webcontify/identity/api/AuthController.java \
       apps/backend/src/test/java/io/webcontify/identity/api/PlatformAdminControllerIntegrationTest.java
git commit -m "feat: add PlatformAdminController and AuthController with integration tests

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Full Backend Test Suite

- [ ] **Step 1: Alle Backend Tests**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: ALL PASS

- [ ] **Step 2: Fixes wenn noetig + Commit**

---

## Task 8: Frontend — Orval Regeneration + Schemas

**Files:**
- Create: `apps/platform/src/features/admins/schemas/invite-admin.ts`

- [ ] **Step 1: Orval regenerieren**

Run: `cd /Users/christophhagelkruys/Documents/private/projects/claudetest && npx nx generate platform`
Erwartete neue Hooks: `useListPlatformAdmins`, `useInvitePlatformAdmin`, `useGetPlatformAdmin`, `useUpdatePlatformAdminStatus`, `useDeletePlatformAdmin`, `useResetPlatformAdminPassword`, `useGetMyProfile`, `useGetAccountUrl`

- [ ] **Step 2: Zod Schema**

```typescript
// apps/platform/src/features/admins/schemas/invite-admin.ts
import { z } from "zod";

export const inviteAdminSchema = z.object({
  email: z.string().email("Ungueltige E-Mail-Adresse").max(255),
  firstName: z.string().min(1, "Vorname ist erforderlich").max(255),
  lastName: z.string().min(1, "Nachname ist erforderlich").max(255),
});

export type InviteAdminFormValues = z.infer<typeof inviteAdminSchema>;
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/generated/ apps/platform/src/features/admins/schemas/
git commit -m "feat: regenerate Orval hooks and add admin invite schema

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Frontend — Admins-Liste

**Files:**
- Modify: `apps/platform/src/app/(platform)/admins/page.tsx` — Placeholder ersetzen
- Create: `apps/platform/src/features/admins/components/admin-table.tsx`
- Create: `apps/platform/src/features/admins/components/invite-admin-dialog.tsx`
- Create: `apps/platform/src/features/admins/components/admin-actions-menu.tsx`
- Create: `apps/platform/src/features/admins/hooks/use-admin-actions.ts`

- [ ] **Step 1: Komponenten erstellen**

Pattern exakt wie die Rollen-/User-Komponenten. Lies:
- `apps/platform/src/features/roles/components/role-table.tsx` — Table Pattern
- `apps/platform/src/features/roles/components/create-role-dialog.tsx` — Dialog Pattern
- `apps/platform/src/features/users/components/user-actions-menu.tsx` — Actions Pattern
- `apps/platform/src/features/roles/hooks/use-role-actions.ts` — Hook Pattern

Admin-Tabelle Spalten: Name, E-Mail, Status Badge (Aktiv/Deaktiviert), Erstellt am
Actions: Deaktivieren/Aktivieren, Passwort-Reset, Entfernen (mit Dialog)
Kein Klick auf Zeile (keine Detail-Seite).

- [ ] **Step 2: Admins Page ersetzen**

```typescript
"use client";
import { Plus } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { useListPlatformAdmins } from "@/generated/platform-admin";
import { AdminTable } from "@/features/admins/components/admin-table";
import { InviteAdminDialog } from "@/features/admins/components/invite-admin-dialog";

export default function PlatformAdminsPage() {
  const { data, isLoading, isRefetching } = useListPlatformAdmins();
  const admins = (data?.status === 200 ? data.data.admins : undefined) ?? [];
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Administratoren" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Administratoren</h1>
          <InviteAdminDialog>
            <Button><Plus className="mr-2 size-4" />Admin einladen</Button>
          </InviteAdminDialog>
        </div>
        <AdminTable admins={admins} isLoading={isLoading} isRefetching={isRefetching} />
      </div>
    </>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/app/\(platform\)/admins/page.tsx \
       apps/platform/src/features/admins/
git commit -m "feat: add platform admin list page with invite dialog

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Frontend — "Passwort aendern" im NavUser

**Files:**
- Modify: `apps/platform/src/components/layout/nav-user.tsx`

- [ ] **Step 1: "Passwort aendern" Link hinzufuegen**

In `nav-user.tsx`, vor dem "Sign out" MenuItem:
- `useGetAccountUrl()` Hook aufrufen (aus generiertem Auth-Client)
- Neuer DropdownMenuItem "Passwort aendern" mit KeyRound Icon
- onClick: `window.open(accountUrl, '_blank')` (neuer Tab)

```tsx
import { KeyRound } from "lucide-react";
import { useGetAccountUrl } from "@/generated/auth";

// Im Component:
const { data: accountUrlData } = useGetAccountUrl();
const accountUrl = accountUrlData?.status === 200 ? accountUrlData.data.url : undefined;

// Im JSX, vor dem Sign out MenuItem:
{accountUrl && (
  <DropdownMenuItem onClick={() => window.open(accountUrl, '_blank')}>
    <KeyRound className="mr-2 size-4" />
    Passwort aendern
  </DropdownMenuItem>
)}
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/components/layout/nav-user.tsx
git commit -m "feat: add password change link to NavUser dropdown for all users

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Frontend Tests

**Files:**
- Create: `apps/platform/src/features/admins/components/__tests__/admin-table.test.tsx`

- [ ] **Step 1: Tests erstellen**

Pattern wie `apps/platform/src/features/roles/components/__tests__/role-table.test.tsx`.

Tests:
- should render admin names
- should show active/deactivated badge
- should show empty state
- should show skeleton when loading

- [ ] **Step 2: Tests ausfuehren**

Run: `cd /Users/christophhagelkruys/Documents/private/projects/claudetest && npx nx test platform`
Expected: ALL PASS

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/features/admins/components/__tests__/
git commit -m "test: add frontend tests for admin table

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 12: Build Verification + PROGRESS.md

- [ ] **Step 1: Full Backend Test Suite**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

- [ ] **Step 2: Frontend Build + Tests**

Run: `npx nx test platform && npx nx build platform`

- [ ] **Step 3: PROGRESS.md aktualisieren**

Phase 3e Tasks eintragen mit Status Done.

- [ ] **Step 4: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md — Phase 3e complete

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```
