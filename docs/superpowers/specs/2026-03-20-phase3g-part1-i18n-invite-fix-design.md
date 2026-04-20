# Phase 3g Part 1: i18n Audit + Invite Flow Fix — Design Spec

## Ueberblick

Zwei zusammenhaengende Themen:
1. **i18n Audit:** Alle Frontend-Komponenten muessen `useTranslations()` nutzen statt hardcoded Strings. Deutsch (de) + Englisch (en). Backend i18n Keys verifizieren.
2. **Invite Flow Fix:** Rollen-Zuweisung beim Invite als Pflichtfeld, `tenant#admin` Grant entfernen, Privilege Escalation Prevention (Inviter darf keine Admin-Rolle vergeben).

---

## i18n Audit

### Aktueller Stand

- `next-intl` ist installiert und konfiguriert (Plugin in `next.config.ts`, Provider in Layout)
- Locale ist **hardcoded auf `"en"`** in `src/i18n/request.ts` — kein dynamisches Switching
- `messages/en.json` und `messages/de.json` existieren, decken aber nur `tenants`, `settings`, `config` ab
- **Alle Phase 3d/3e Komponenten haben hardcoded deutsche Strings** (roles, users, admins)
- Backend gibt korrekt i18n Keys im `code`-Feld zurueck (RFC 7807)
- Frontend uebersetzt Backend-Fehler NICHT — zeigt hardcoded deutsche Strings

### Was zu tun ist

**1. Locale-Switching aktivieren:**
- `src/i18n/request.ts` — Locale aus Cookie lesen (Default: `en` bis alle Uebersetzungen in `de.json` vollstaendig sind, dann auf `de` wechseln)
- Cookie: `NEXT_LOCALE` (Standard next-intl Cookie-Name), `SameSite=Lax`, nicht HttpOnly (JS muss lesen koennen)
- Kein URL-basiertes Routing (kein `/de/tenants` vs `/en/tenants`) — Locale wird per Cookie gesteuert

**2. Message-Dateien erweitern:**

Fehlende Namespaces in `messages/de.json` und `messages/en.json`:

```
roles.*        — Rollen-Verwaltung (Table, Dialog, Detail, Permissions)
users.*        — User-Verwaltung (Table, Invite Dialog, Actions)
admins.*       — Platform Admin Verwaltung
common.*       — Gemeinsame Strings (Abbrechen, Speichern, Loeschen, Bearbeiten, etc.)
errors.*       — Backend i18n Keys (role.not_found, user.email_already_exists, etc.)
nav.*          — Navigation (existiert teilweise)
auth.*         — Login, Logout, Session
```

**3. Alle Komponenten migrieren:**

Jede Komponente die hardcoded Strings enthaelt muss auf `useTranslations()` umgestellt werden:

```tsx
// Vorher:
<Button>Rolle erstellen</Button>

// Nachher:
const t = useTranslations("roles");
<Button>{t("create")}</Button>
```

Betroffene Komponenten (alle aus Phase 3d/3e + nav-user):
- `features/roles/components/*` (6 Dateien)
- `features/users/components/*` (4 Dateien)
- `features/admins/components/*` (4 Dateien)
- `components/layout/nav-user.tsx`
- `app/(workspace)/roles/page.tsx`, `roles/[roleId]/page.tsx`
- `app/(workspace)/users/page.tsx`
- `app/(platform)/admins/page.tsx`

**4. Backend-Fehler uebersetzen:**

Frontend Error Handler muessen das `code`-Feld aus der Backend-Response lesen und via `t(`errors.${code}`)` uebersetzen. Fallback: den Code selbst anzeigen.

---

## Invite Flow Fix

### Aktueller Stand

- `UserService.invite()` macht `permissionService.grant(userId, "admin", "tenant", tenantId)` — jeder eingeladene User wird automatisch `tenant#admin`
- `InviteUserRequest` hat optionales `copyRolesFromUserId` aber kein `roleIds` Feld
- Invite-Dialog hat kein Rollen-Auswahlfeld (nur "Berechtigungen uebernehmen von")

### Was zu aendern ist

**1. Backend:**

`UserService.invite()`:
- `tenant#admin` Grant entfernen
- Neues Pflichtfeld `roleIds: List<UUID>` (mindestens eine Rolle)
- Rollen-Zuweisung via `roleService.setRolesForUser(userId, roleIds)`
- **Privilege Escalation Check:** Konkreter Mechanismus:
  ```
  1. Admin System-Rolle per roleRepository.findActiveByName("Admin") laden
  2. Pruefen ob roleIds die Admin-Rolle enthaelt
  3. Wenn ja: permissionService.check(currentUserId, "admin", "tenant", tenantId)
  4. Wenn check false → ForbiddenException("invite.cannot_assign_admin_role")
  ```
  Der Check ist serverseitig — das Frontend filtert die Admin-Rolle als UX-Enhancement aus der Liste (kein Security-Gate, nur Komfort). Der eigentliche Security-Check ist im Backend.
- **Fehlerbehandlung Rollen-Zuweisung:** Wenn `roleService.setRolesForUser()` nach erfolgreichem Keycloak-Create und DB-Insert fehlschlaegt, wird ein vollstaendiger Rollback durchgefuehrt: Keycloak-User loeschen + DB Soft-Delete. Der Invite ist dann komplett fehlgeschlagen (kein best-effort fuer Pflicht-Rollen).

`OpenAPI Spec` (`identity-api.yaml`):
- `InviteUserRequest` aendern:
  ```yaml
  InviteUserRequest:
    type: object
    required: [email, firstName, lastName, roleIds]
    properties:
      email: { type: string, format: email, maxLength: 255 }
      firstName: { type: string, minLength: 1, maxLength: 255 }
      lastName: { type: string, minLength: 1, maxLength: 255 }
      roleIds:
        type: array
        items: { type: string, format: uuid }
        minItems: 1
        maxItems: 50
      copyRolesFromUserId:
        type: string
        format: uuid
        description: Optional — if set, roleIds are pre-filled from this user (frontend only, backend uses roleIds)
  ```
- **Interaktion `copyRolesFromUserId` + `roleIds`:** `copyRolesFromUserId` ist ein reines Frontend-Feld das die `roleIds`-Liste vorbefuellt. Das Backend kennt nur `roleIds` und ignoriert `copyRolesFromUserId` wenn `roleIds` explizit gesetzt ist. `copyRolesFromUserId` bleibt als Convenience fuer den "Rollen klonen"-Flow, aber die eigentliche Zuweisung laeuft immer ueber `roleIds`.

**2. Frontend:**

Invite-Dialog:
- Neues Pflichtfeld: Rollen-Multi-Select (Checkboxen oder Multi-Select Dropdown)
- Verfuegbare Rollen via `useListRoles()` laden
- **Filter:** Admin-Rolle wird immer in der Liste angezeigt. Der Backend-Check verhindert Privilege Escalation (403). Frontend-seitige Filterung ist optional als UX-Enhancement — dafuer kann `useGetUserRoles(currentUserId)` genutzt werden um zu pruefen ob der aktuelle User Admin ist
- Wenn `copyRolesFromUserId` gewaehlt: Rollen werden vorausgefuellt (aber editierbar)
- Validierung: mindestens eine Rolle muss gewaehlt sein

**3. Provisioning unberuehrt:**
- `TenantProvisioningListener` weist dem initialen Admin-User weiterhin die Admin-Rolle zu — das bleibt.

---

## Testing

- Backend Unit Tests: UserService invite mit roleIds, Privilege Escalation Check, Rollback bei Rollen-Fehler
- Backend: Bestehende UserServiceTest Tests anpassen (neue invite-Signatur mit roleIds)
- Frontend Vitest: Invite Dialog mit Rollen-Select
- E2E: Bestehende invite-user Tests anpassen (roleIds Pflicht)

### Betroffene bestehende Tests
- `UserServiceTest.Invite.shouldInviteUserSuccessfully` — `verify(permissionService).grant(any())` wird zu `verify(roleService).setRolesForUser(any(), any())`
- `UserServiceTest.InviteWithRoleClone.*` — Signatur-Aenderung
- `UserControllerIntegrationTest.shouldInviteAndListUsers` — Request Body braucht `roleIds`
- Neuer Test: `shouldRejectInviteWhenNonAdminAssignsAdminRole`

---

## Abgrenzung

- Profil-Seite (Part 2)
- Profilbild-Upload (Part 2)
- Theme/Sprache Praeferenzen (Part 2)
