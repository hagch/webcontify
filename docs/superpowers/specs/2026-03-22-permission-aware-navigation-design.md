# Permission-Aware Navigation + Konfigurierbare Rollen-Permissions

**Datum:** 2026-03-22
**Phase:** 3h
**Scope:** Option B — Navigation fixen + Permission-Konfiguration fuer Rollen

---

## Problem

1. **Navigation zeigt alles:** `isAdmin: true` ist hardcoded — jeder User sieht Users, Roles, Settings
2. **Editor/Viewer haben keine relevanten Permissions:** Bekommen beim Provisioning nur `reader` auf `tenant_config`, sehen aber trotzdem alle Menuepunkte
3. **Permissions nicht konfigurierbar:** Die `role-permissions-preview.tsx` zeigt nur read-only an, Tenant-Admins koennen nichts aendern
4. **Custom Roles sind nutzlos:** Neu erstellte Rollen haben keine Permissions und es gibt keine Moeglichkeit welche zuzuweisen
5. **Profil-Editing funktioniert:** Die Auth-Endpoints haben keine Permission-Checks (nur JWT), das Problem liegt an der Navigation/403-Darstellung
6. **`tenant_config` fehlt in `model.fga`:** Der Typ ist im Code und Provisioning referenziert, aber nicht im FGA-Modell definiert — Tuples sind ungueltig
7. **Permission-Tuples werden bei Rollen-Loeschung nicht aufgeraeumt:** `RoleService.delete()` entfernt nur tenant/assignee-Tuples, nicht Permission-Tuples

## Loesung

### 1. Backend: OpenFGA Model Fix

`model.fga` um `tenant_config` erweitern (fehlt aktuell, obwohl Provisioning bereits Tuples schreibt):

```
type tenant_config
  relations
    define tenant: [tenant]
    define editor: [user, role#assignee] or admin from tenant
    define reader: [user, role#assignee] or editor or admin from tenant
```

Bestehende Stores: Authorization Model wird beim naechsten Provisioning oder manuell redeployed. Fuer bestehende Tenants muss ein Migration-Script das Model aktualisieren (einmalig).

### 2. Backend: OpenFgaService.batchCheck()

`batchCheck()` implementieren (Record `OpenFgaBatchCheckRequest` existiert bereits, wird aber nicht genutzt). Pflicht fuer den Effective-Permissions-Endpoint und Migration der bestehenden `getPermissionPreview()`.

**Thread-Safety Fix:** `client.setStoreId()` ist globaler State und nicht thread-safe bei parallelen Requests. Store-ID muss pro Request uebergeben werden, nicht als globalen State setzen.

### 3. Backend: Effective Permissions Endpoint

Neuer Endpoint auf `auth-api.yaml`:

```
GET /api/v1/auth/effective-permissions?scope=navigation
```

**Response:**

```json
{
  "permissions": {
    "user_management": ["reader", "inviter"],
    "role_management": ["reader"],
    "tenant_config": ["reader", "editor"]
  },
  "isTenantAdmin": true
}
```

**Verhalten:**
- Liest die userId aus dem JWT
- Fuer `scope=navigation`: Prueft die 3 Singleton-Ressourcen (`user_management`, `role_management`, `tenant_config`) mit allen moeglichen Actions via **BatchCheck** (11 Checks in 1 Request: 4 + 4 + 2 Actions + 1 Admin-Check)
- `isTenantAdmin`: `true` wenn der User die `admin`-Relation auf dem Tenant hat (im selben BatchCheck)
- Platform Admins bekommen alle Permissions (Bypass wie bei `@RequiresPermission`)

**Scope-Parameter (Vorbereitung fuer Phase C):**
- `navigation` — nur Singleton-Ressourcen (jetzt)
- `app:{appId}` — App-spezifische Permissions (Phase 4, spaeter)
- Unbekannte Scopes: 400 Bad Request

Der Scope-Parameter ist optional, default ist `navigation`.

### 4. Backend: Role Permissions Management Endpoint

Neuer Endpoint auf `identity-api.yaml`:

```
PUT /api/v1/tenant/roles/{roleId}/permissions
```

**Request:**

```json
{
  "permissions": [
    {
      "resourceType": "user_management",
      "actions": ["reader", "inviter"]
    },
    {
      "resourceType": "role_management",
      "actions": ["reader"]
    },
    {
      "resourceType": "tenant_config",
      "actions": ["reader"]
    }
  ]
}
```

**Verhalten:**
- Erfordert `@RequiresPermission(action="editor", resource="role_management")`
- System-Rollen (Admin, Editor, Viewer, Inviter) koennen nicht geaendert werden (400)
- **Delta-basiert:** Aktuelle Permission-Tuples lesen, Delta berechnen, schreiben/loeschen
- **Reihenfolge:** Writes zuerst, dann Deletes (bei Fehler nach Writes hat die Rolle mehr Permissions als gewuenscht, nie weniger — sicherere Richtung)
- Validierung: Nur bekannte `resourceType`/`action`-Kombinationen erlaubt (Whitelist aus `RESOURCE_ACTIONS`)
- **Privilege Escalation Guard:** Prueft via `permissionService.check()` ob der anfragende User die Permission selbst hat (effektive Permissions, nicht direkte Tuples — wichtig weil `reader` oft durch `editor` impliziert wird)

**OpenFGA Tuples die geschrieben/geloescht werden:**

```
role:{roleId}#assignee --{action}--> {resourceType}:{tenantId}
```

Beispiel: Rolle "Content Manager" bekommt `inviter` auf `user_management`:
```
role:abc#assignee --inviter--> user_management:tenant123
```

**Kein explizites `reader`-Tuple:** Das OpenFGA-Modell leitet `reader` automatisch ab (z.B. `inviter` impliziert `reader` auf `user_management`). Es werden nur die explizit gewaehlten Actions als Tuples geschrieben. Das Frontend zeigt `reader` als automatisch aktiv an (visuelle Hilfe), schickt es aber nicht als separates Tuple.

### 5. Frontend: usePermissions Hook

Neuer React Query Hook:

```typescript
// src/hooks/use-permissions.ts
export function usePermissions() {
  const query = useGetEffectivePermissions({ scope: "navigation" });

  return {
    permissions: query.data?.permissions ?? {},
    isTenantAdmin: query.data?.isTenantAdmin ?? false,
    isLoading: query.isLoading,

    // Helper: hat der User mindestens eine der Actions?
    can: (resource: string, action: string) => boolean,

    // Helper: hat der User irgendeine Permission auf der Resource?
    canAccess: (resource: string) => boolean,
  };
}
```

**Caching-Strategie:**
- React Query mit `staleTime: 60 * 1000` (1 Minute) — kurz genug fuer zeitnahe Permission-Updates, lang genug um nicht bei jedem Navigation-Wechsel neu zu laden
- Invalidierung bei Rollen-Aenderungen im eigenen Tab (grant/revoke Mutations)
- **Tradeoff:** Wenn Admin A die Permissions von Rolle X aendert, sehen andere User mit Rolle X das erst nach max. 1 Minute. Das Backend schuetzt korrekt via `@RequiresPermission` — die Navigation ist nur UI-Convenience. Akzeptabler Tradeoff.
- Kein Zustand Store noetig — React Query cached global via QueryClient

**Warum kein Zustand Store:**
- Permissions sind Server State (kommt von OpenFGA)
- React Query bietet bereits globales Caching + Deduplication
- `usePermissions()` kann ueberall aufgerufen werden, React Query dedupliziert automatisch
- Zustand waere Redundanz und erfordert manuellen Sync

### 6. Frontend: Permission-Aware Navigation

**Workspace Layout (`layout.tsx`):**

```typescript
const { canAccess, isTenantAdmin, isLoading } = usePermissions();
const sections = getWorkspaceNavItems({
  canAccessUserManagement: canAccess("user_management"),
  canAccessRoleManagement: canAccess("role_management"),
  canAccessTenantConfig: canAccess("tenant_config"),
}, t);
```

**Nav Config aendern:**
- `isAdmin: boolean` ersetzen durch granulare Flags
- Management-Section nur wenn `canAccessUserManagement || canAccessRoleManagement`
- Settings-Section nur wenn `canAccessTenantConfig`
- Einzelne Items nur wenn die jeweilige Permission vorhanden

**Loading State:**
- Waehrend `isLoading`: Sidebar Skeleton (nur fuer die dynamischen Sections)
- Statische Items (Dashboard, Apps) sofort sichtbar

### 7. Frontend: Editable Permission Toggles

Die bestehende `role-permissions-preview.tsx` wird zu `role-permissions-editor.tsx`:

**UI-Pattern (inspiriert von Linear Toggle-Switches):**

```
┌─────────────────────────────────────────────────┐
│ Benutzerverwaltung                              │
│                                                 │
│  ☑ Lesen      ☑ Einladen    ☐ Bearbeiten      │
│  ☐ Loeschen                                    │
├─────────────────────────────────────────────────┤
│ Rollenverwaltung                                │
│                                                 │
│  ☑ Lesen      ☐ Erstellen   ☐ Bearbeiten      │
│  ☐ Loeschen                                    │
├─────────────────────────────────────────────────┤
│ Tenant-Einstellungen                            │
│                                                 │
│  ☑ Lesen      ☐ Bearbeiten                     │
└─────────────────────────────────────────────────┘
              [Speichern]  [Abbrechen]
```

**Regeln:**
- System-Rollen (Admin, Editor, Viewer, Inviter): Toggles disabled + Info-Banner "System-Rollen koennen nicht geaendert werden"
- Custom Roles: Toggles editierbar
- `reader`-Toggle wird automatisch als aktiv angezeigt wenn eine andere Action in der gleichen Ressource aktiv ist (rein visuell — kein separates Tuple, OpenFGA-Modell leitet reader ab)
- Dirty State Tracking: Speichern-Button nur wenn Aenderungen vorhanden
- Optimistic Update: UI sofort aktualisieren, bei Fehler rollback

**Privilege Escalation Schutz im UI:**
- Toggles die der aktuelle User selbst nicht hat: disabled + Tooltip "Du hast diese Berechtigung selbst nicht"
- Verhindert dass ein User mit nur `reader`-Rechten einem anderen `editor` geben kann

### 8. Frontend: 403 Fehlerbehandlung

**Seiten-Level Guard:**
- Workspace-Seiten die Permissions erfordern pruefen `usePermissions()` beim Laden
- Bei fehlender Permission: Redirect auf `/dashboard` + Toast "Kein Zugriff"
- Kein separater "Access Denied" Screen — einfacher Redirect mit Erklaerung

**API-Level:**
- 403 Responses von der API: Toast mit i18n Key `errors.forbidden` ("Keine Berechtigung fuer diese Aktion")
- Kein generischer Error Screen

### 9. System-Rollen Default-Permissions anpassen

Editor und Viewer sind fuer Singleton-Ressourcen (User/Role Management, Tenant Config) nicht relevant — sie werden erst in Phase 4 fuer Apps/Content interessant. Daher bekommen sie **keine** Singleton-Permissions.

**Admin** (unveraendert — bekommt alles durch `admin from tenant` + explizite Tuples):
- user_management: inviter, editor, deleter (+ reader implizit)
- role_management: creator, editor, deleter (+ reader implizit)
- tenant_config: editor (+ reader implizit)

**Editor** (keine Singleton-Permissions):
- Keine Tuples fuer user_management, role_management, tenant_config
- Sieht nur Dashboard + Apps in der Navigation
- Wird in Phase 4 App/Content-Permissions bekommen

**Viewer** (keine Singleton-Permissions):
- Keine Tuples fuer user_management, role_management, tenant_config
- Sieht nur Dashboard + Apps in der Navigation
- Wird in Phase 4 App/Content-Permissions bekommen

**Inviter** (unveraendert):
- user_management: inviter

**Aenderung am Provisioning:** Die bestehenden `reader`-Tuples fuer Editor/Viewer auf `tenant_config` werden entfernt.

Tenant-Admins koennen Custom Roles mit beliebigen Singleton-Permissions konfigurieren. System-Rollen bleiben fix.

### 10. Role Delete Cleanup

`RoleService.delete()` muss erweitert werden: neben tenant/assignee-Tuples muessen auch alle Permission-Tuples der Rolle aufgeraeumt werden. Ablauf:
1. Alle Permission-Tuples lesen (readTuples fuer jede Resource in `RESOURCE_ACTIONS`)
2. Batch-Delete aller gefundenen Tuples
3. Dann bisheriges Delete (tenant-Tuple, Soft Delete in DB)

---

## Architektur-Entscheidungen

### Kein Zustand Store fuer Permissions
React Query cached Effective Permissions global. `usePermissions()` ist ein duenner Wrapper. Zustand waere redundant und erfordert manuellen Cache-Sync.

### BatchCheck fuer Performance (Pflicht, nicht optional)
Der Effective-Permissions-Endpoint macht 11 Checks (4+4+2 Actions + 1 Admin-Check). Mit OpenFGA BatchCheck wird das 1 Request statt 11. Das `OpenFgaBatchCheckRequest`-Model existiert bereits. Die bestehende `getPermissionPreview()` in `RoleService` (aktuell 10 einzelne Checks) wird gleichzeitig migriert.

### Scope-Parameter statt separater Endpoints
Ein Endpoint mit `scope` statt mehrere Endpoints. `navigation` jetzt, `app:{id}` spaeter. Haelt die API-Oberflaeche klein und das Frontend kann gezielt nachladen.

### Privilege Escalation Guard
Backend UND Frontend. Backend ist die Wahrheit (prüft ob der anfragende User die Permission selbst hat bevor er sie einer Rolle geben darf). Frontend disabled nur die Toggles fuer bessere UX.

---

## Ausblick Phase C (nicht in Scope)

Fuer Content-Level Permissions in Phase 4+ wird ein dynamischeres System noetig:

- **App-Scoping:** Rollen koennen pro App unterschiedliche Permissions haben
- **Content-Level:** ABAC-artige Regeln ("darf lesen wenn Feld X Wert Y hat")
- Das ist ein eigenes Design das auf dem hier gebauten aufbaut
- Der `scope`-Parameter und die Toggle-UI sind dafuer vorbereitet (neue Resource-Typen + App-Dropdown)

---

## Betroffene Dateien

### Backend (neu)
- `openapi/auth-api.yaml` — EffectivePermissions Endpoint + Response Schema
- `openapi/identity-api.yaml` — SetRolePermissions Endpoint + Request Schema
- `AuthController` — `getEffectivePermissions()`
- `AuthService` — Permission-Aggregation mit BatchCheck
- `RoleController` — `setPermissions()`
- `RoleService` — `setPermissions()` mit Delta + Privilege Escalation Guard

### Backend (aendern)
- `model.fga` + `model.json` — `tenant_config` Typ ergaenzen
- `OpenFgaService` — `batchCheck()` implementieren + Thread-Safety Fix (Store-ID pro Request)
- `RoleService.getPermissionPreview()` — auf BatchCheck migrieren
- `RoleService.delete()` — Permission-Tuples aufraemen
- `TenantProvisioningListener` — Editor/Viewer `tenant_config` reader-Tuples entfernen

### Frontend (neu)
- `src/hooks/use-permissions.ts` — `usePermissions()` Hook
- `src/features/roles/components/role-permissions-editor.tsx` — Editierbare Toggles

### Frontend (aendern)
- `src/components/layout/nav-config.ts` — Granulare Permission-Flags statt `isAdmin`
- `src/app/(workspace)/layout.tsx` — `usePermissions()` statt hardcoded `isAdmin: true`
- `src/app/(workspace)/users/page.tsx` — Permission Guard
- `src/app/(workspace)/roles/page.tsx` — Permission Guard
- `src/app/(workspace)/roles/[roleId]/page.tsx` — Preview durch Editor ersetzen
- Settings-Seiten — Permission Guard
- i18n: `errors.forbidden` Key in `en.json` + `de.json`

### Tests
- Backend: Unit + Integration Tests fuer neue Endpoints
- Backend: OpenFGA Model Update Tests
- Frontend: Vitest fuer `usePermissions`, Permission Guards, Toggle-Komponente
