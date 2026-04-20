# Phase 3d: Rollen-Verwaltung — Design Spec

## Ueberblick

Rollen-CRUD + bidirektionale User-zu-Rolle-Zuweisung fuer Tenant Self-Service. OpenFGA ist Single Source of Truth fuer alle Zuweisungen und Permissions. Die lokale `tenant_role` Tabelle speichert ausschliesslich Metadaten (Name, Beschreibung, System-Rolle Flag).

Dynamische Permission-Matrix (Rolle X bekommt `editor` auf `app:Y`) wird auf eine spaetere Phase verschoben — es gibt aktuell noch keine Apps/Content-Definitionen.

Platform Admin Management (Admins einladen, Berechtigungen) ist Phase 3e.

### Voraussetzung: OpenFGA Model erweitern

Das bestehende OpenFGA Model (`model.json`) enthaelt noch keinen `tenant_config` Type. Dieser muss vor Phase 3d hinzugefuegt werden, da die System-Rollen Permissions auf `tenant_config` benoetigen:

```
type tenant_config
  relations
    define tenant: [tenant]
    define editor: [user, role#assignee] or admin from tenant
    define reader: [user, role#assignee] or editor or admin from tenant
```

Dies ist der erste Task in der Implementierung.

---

## Datenmodell

### Bestehende DB-Tabelle (unveraendert)

`tenant_role` existiert bereits (Migration `V20260315_001`):
- id, tenant_id, name, description, is_system_role, Audit-Felder, Soft Delete
- RLS mit Tenant-Isolation
- Unique Constraint auf (tenant_id, name)

Keine neue Join-Tabelle. User-zu-Rolle-Zuweisungen leben ausschliesslich in OpenFGA.

### Domain Model

```
TenantRole         (Record: id, tenantId, name, description, systemRole, createdAt, updatedAt)
RoleWithAssignees  (Record: TenantRole + List<RoleAssignee>)
RoleAssignee       (Record: userId, email, firstName, lastName)
```

### OpenFGA Relationships

User-zu-Rolle-Zuweisung:
```
role:{roleId}#assignee -> user:{userId}
```

Rolle-zu-Tenant-Zugehoerigkeit:
```
role:{roleId}#tenant -> tenant:{tenantId}
```

Singleton-Ressourcen pro Tenant (fuer Permission-Checks und Provisioning):
```
user_management:{tenantId}#tenant -> tenant:{tenantId}
role_management:{tenantId}#tenant -> tenant:{tenantId}
tenant_config:{tenantId}#tenant  -> tenant:{tenantId}
```

Diese Singleton-Tupel werden beim Provisioning einmalig erstellt. Permissions der System-Rollen referenzieren diese festen Object-IDs (z.B. `user_management:{tenantId}#inviter -> role:admin-role#assignee`).

Abfragen:
- "Rollen von User X" = OpenFGA ListObjects (`user:{userId}`, `assignee`, `role`)
- "User in Rolle X" = OpenFGA Read Tuples (`role:{roleId}`, `assignee`)

---

## System-Rollen

Vier System-Rollen werden beim Tenant-Provisioning automatisch erstellt:

| Rolle | OpenFGA Permissions |
|---|---|
| Admin | `user_management#inviter/editor/deleter/reader`, `role_management#creator/editor/deleter/reader`, `tenant_config#editor/reader` |
| Editor | `tenant_config#reader` |
| Viewer | `tenant_config#reader` |
| Inviter | `user_management#inviter/reader` |

System-Rollen:
- Koennen nicht geloescht werden (400 Bad Request)
- Koennen nicht umbenannt werden (400 Bad Request)
- Sind in der UI visuell gekennzeichnet (Badge)
- Admin bekommt volle Tenant-Verwaltungsrechte
- Editor/Viewer bekommen erst bei der dynamischen Permission-Matrix (spaeter) App/Content-Permissions

### Provisioning-Erweiterung (TenantProvisioningListener)

Aktuell wird der Admin-User als `tenant#admin` in OpenFGA geschrieben. Neu:
1. Vier System-Rollen in DB erstellen (Admin, Editor, Viewer, Inviter)
2. `role:{id}#tenant -> tenant:{tenantId}` fuer jede Rolle in OpenFGA
3. Permissions der System-Rollen in OpenFGA schreiben (Tupel aus Tabelle oben)
4. Admin-User bekommt `role:admin-role#assignee` Zuweisung

---

## API Endpoints

### Rollen-CRUD

| Method | Path | Operation | Beschreibung | Permission |
|---|---|---|---|---|
| POST | `/api/v1/tenant/roles` | createRole | Neue Custom-Rolle erstellen | `role_management#creator` |
| GET | `/api/v1/tenant/roles` | listRoles | Alle Rollen (inkl. System-Rollen) | `role_management#reader` |
| GET | `/api/v1/tenant/roles/{roleId}` | getRole | Rolle mit Assignees + Permission-Vorschau | `role_management#reader` |
| PUT | `/api/v1/tenant/roles/{roleId}` | updateRole | Name/Beschreibung aendern (nur Custom) | `role_management#editor` |
| DELETE | `/api/v1/tenant/roles/{roleId}` | deleteRole | Rolle loeschen (nur Custom) | `role_management#deleter` |

### Rollen-Zuweisung (bidirektional)

| Method | Path | Operation | Beschreibung | Permission |
|---|---|---|---|---|
| PUT | `/api/v1/tenant/roles/{roleId}/assignees` | setRoleAssignees | User-Liste einer Rolle setzen (Replace) | `role_management#editor` |
| PUT | `/api/v1/tenant/users/{userId}/roles` | setUserRoles | Rollen-Liste eines Users setzen (Replace) | `user_management#editor` |
| GET | `/api/v1/tenant/users/{userId}/roles` | getUserRoles | Rollen eines Users abfragen | `user_management#reader` |

### Design-Entscheidungen

- **Replace-Semantik (PUT):** Frontend schickt die komplette Liste, Backend berechnet Delta (aktuelle vs. gewuenschte Zuweisungen) und schreibt/loescht in OpenFGA. Einfacher fuer Frontend, weniger Roundtrips.
- `createRole`/`updateRole` nur fuer Custom-Rollen — System-Rollen geben 400 bei Update/Delete
- Alle Endpoints mit `@RequiresPermission` geschuetzt
- Platform Admins bypassen Permission-Checks automatisch (bestehender Aspect)

### Request/Response Schemas

```yaml
CreateRoleRequest:
  name: string (required)
  description: string (optional)

UpdateRoleRequest:
  name: string (required)
  description: string (optional)

RoleResponse:
  id: UUID
  name: string
  description: string
  systemRole: boolean
  assigneeCount: integer
  createdAt: datetime
  updatedAt: datetime

RoleDetailResponse:
  id: UUID
  name: string
  description: string
  systemRole: boolean
  assignees: RoleAssigneeResponse[]
  permissions: RolePermissionPreview[]
  createdAt: datetime
  updatedAt: datetime

RoleAssigneeResponse:
  userId: UUID
  email: string
  firstName: string
  lastName: string

RolePermissionPreview:
  resourceType: string (z.B. "user_management")
  actions: string[] (z.B. ["inviter", "reader"])

SetRoleAssigneesRequest:
  userIds: UUID[] (maxItems: 100)

SetUserRolesRequest:
  roleIds: UUID[] (maxItems: 50)

UserRolesResponse:
  roles: RoleResponse[]

RoleListResponse:
  roles: RoleResponse[]
```

### Invite-Erweiterung

Bestehender `inviteUser` Endpoint bekommt optionales Feld:
```yaml
InviteUserRequest (erweitert):
  email: string (required)
  firstName: string (required)
  lastName: string (required)
  copyRolesFromUserId: UUID (optional)
```

Backend liest bei `copyRolesFromUserId` die Rollen von User X und weist sie dem neuen User zu.

**Sicherheit:** Vor dem Rollen-Lookup wird `copyRolesFromUserId` gegen `tenant_user` (mit RLS) validiert. Existiert der User nicht im aktuellen Tenant → 400 Bad Request (`invite.source_user_not_found`).

---

## Backend Services

### RoleService

```
create(name, description)
  → DB Insert (TenantRoleEntity)
  → OpenFGA Write: role:{id}#tenant -> tenant:{tenantId}
  → return TenantRole

list()
  → DB Query (alle nicht-geloeschten Rollen)
  → Parallel (CompletableFuture) fuer jede Rolle: OpenFGA Read Tuples Count fuer assigneeCount
  → return List<RoleResponse>
  → Hinweis: typischerweise < 20 Rollen pro Tenant, parallele Ausfuehrung ist ausreichend

getById(roleId)
  → DB Metadaten
  → OpenFGA Read Tuples fuer Assignees
  → Assignee User IDs → tenant_user DB Lookup fuer Namen/Email
  → OpenFGA ListObjects fuer Permission-Vorschau
  → return RoleWithAssignees

update(roleId, name, description)
  → Pruefung: !systemRole (sonst 400)
  → DB Update
  → return TenantRole

delete(roleId)
  → Pruefung: !systemRole (sonst 400)
  → Pruefung: keine Assignees (sonst 409, User muss erst Zuweisungen entfernen)
  → Soft Delete in DB
  → Alle OpenFGA Relationships der Rolle entfernen (tenant, assignees, permissions)
  → Permissions: alle Tupel mit role:{roleId}#assignee als Subject loeschen
    (z.B. user_management:{tenantId}#inviter -> role:{roleId}#assignee)

setAssignees(roleId, userIds)
  → Validierung: alle userIds gegen tenant_user (RLS) pruefen, unbekannte → 400
  → Aktuelle Assignees aus OpenFGA lesen
  → Delta berechnen (toAdd, toRemove)
  → Schutzlogik: letzte Admin-Zuweisung darf nicht entfernt werden (409)
  → OpenFGA Write (toAdd) + Delete (toRemove)
  → maxItems: 100 (OpenAPI Constraint auf userIds Array)

getPermissionPreview(roleId)
  → Fuer jeden Singleton-Resource-Typ ({tenantId} als Object-ID):
    → user_management:{tenantId}, role_management:{tenantId}, tenant_config:{tenantId}
    → Fuer jede Action: OpenFGA Check ob role:{roleId}#assignee die Permission hat
  → return List<RolePermissionPreview>
```

### UserService Erweiterungen

```
setRoles(userId, roleIds)
  → Validierung: alle roleIds gegen tenant_role (RLS) pruefen, unbekannte → 400
  → Aktuelle Rollen aus OpenFGA lesen (ListObjects)
  → Delta berechnen (toAdd, toRemove)
  → Schutzlogik: letzte Admin-Zuweisung darf nicht entfernt werden (409)
  → OpenFGA Write (toAdd) + Delete (toRemove)
  → maxItems: 50 (OpenAPI Constraint auf roleIds Array)

getRoles(userId)
  → OpenFGA ListObjects (user:{userId}, assignee, role)
  → Role IDs → DB Lookup fuer Metadaten
  → return List<RoleResponse>

invite() (erweitert)
  → Bestehende Logik (Keycloak User + DB Insert)
  → Wenn copyRolesFromUserId gesetzt:
    → getRoles(sourceUserId) → setRoles(newUserId, roleIds)
```

### Schutzlogik

- System-Rollen: kein Update, kein Delete → 400 Bad Request mit i18n Key
- Letzte Admin-Zuweisung: wenn ein User die Admin-Rolle verlieren wuerde und er der letzte Assignee ist → 409 Conflict
- Rolle loeschen: nur wenn keine Assignees → 409 Conflict (User muss erst Zuweisungen entfernen)
- Input-Validierung: alle User-IDs/Rollen-IDs gegen lokale DB (mit RLS) pruefen vor OpenFGA-Write

### Race Condition: Letzte-Admin-Guard

OpenFGA hat keine Transaktions-Semantik. Zwei gleichzeitige Requests koennten beide den "letzter Admin"-Check passieren. Loesung: Compensating Transaction — nach dem OpenFGA Delete wird erneut geprueft ob noch mindestens ein Admin-Assignee existiert. Falls nicht, wird die Aenderung sofort rueckgaengig gemacht (OpenFGA Write) und 409 zurueckgegeben.

### Fehlerbehandlung bei Rollen-Klon (Invite)

Wenn nach dem Keycloak-User-Create und DB-Insert das `setRoles()` in OpenFGA fehlschlaegt, wird der User ohne Rollen angelegt. Dies wird als Best-Effort behandelt: der User existiert, Rollen muessen manuell zugewiesen werden. Ein Warning-Log wird geschrieben. Kein Frontend-Toast-Unterschied — der Invite ist erfolgreich, die Rollen-Zuweisung kann nachgeholt werden.

---

## Frontend

### Dateistruktur

```
src/features/roles/
  ├── components/
  │   ├── role-table.tsx
  │   ├── create-role-dialog.tsx
  │   ├── edit-role-form.tsx
  │   ├── role-assignees-table.tsx
  │   ├── add-assignees-dialog.tsx
  │   ├── role-permissions-preview.tsx
  │   └── __tests__/
  ├── hooks/
  │   └── use-role-actions.ts
  └── schemas/
      └── role.ts
```

### Rollen-Liste (`/roles`)

- Tabelle: Name, Beschreibung, System-Rolle Badge, Anzahl Assignees
- "Rolle erstellen" Button → Create-Dialog (React Hook Form + Zod)
- Actions pro Zeile: Bearbeiten, Loeschen (nur Custom-Rollen)
- Klick auf Zeile → Rollen-Detail-Seite
- Skeleton Loading, Empty State

### Rollen-Detail (`/roles/[roleId]`)

- Header: Name, Beschreibung (editierbar bei Custom-Rollen via Inline-Edit oder Edit-Form)
- **Tab 1 — Zugewiesene User:**
  - Tabelle mit User-Liste (Email, Name, Status)
  - "User hinzufuegen" Button → Multi-Select Dialog (Combobox mit Suche aus bestehenden Tenant-Usern)
  - User entfernen per Action-Menu in Zeile
  - Schutzlogik-Feedback: Fehlermeldung bei letztem Admin
- **Tab 2 — Permissions (read-only):**
  - Gruppiert nach Resource-Typ (User Management, Role Management, Tenant Config)
  - Zeigt welche Actions die Rolle hat (Badges/Chips)
  - Hinweis "Permissions koennen aktuell nicht bearbeitet werden"

### Create/Edit Role Dialog

- Felder: Name (required, max 255), Beschreibung (optional)
- Zod Schema Validierung
- React Hook Form + shadcn Form
- Error Handling: Duplicate Name (409) → Inline-Fehler

### Bidirektionale Zuweisung

- **Von Rolle aus:** Rollen-Detail → Tab "Zugewiesene User" → "User hinzufuegen"
- **Von User aus:** User-Detail bekommt neue Sektion "Rollen" — Liste der zugewiesenen Rollen mit "Rolle hinzufuegen" Button und Entfernen-Action

### Invite mit Rollen-Klon

- Bestehender Invite-Dialog bekommt optionales Feld "Berechtigungen uebernehmen von"
  - Combobox mit User-Suche (bestehende Tenant-User)
  - Bei Auswahl: Rollen des Quell-Users als Vorschau-Badges anzeigen
  - Badges sind nicht editierbar (komplette Uebernahme)
- User-Tabelle: neue Zeilen-Action "User mit gleichen Rollen einladen"
  - Oeffnet Invite-Dialog mit vorausgefuelltem `copyRolesFromUserId`

### Loading/Error States

- Skeleton Loading fuer Tabellen (bestehendes Pattern)
- Table-Body Overlay bei Refetch
- Button Loading bei Mutations (kein Optimistic Update — Mutation wartet auf Server-Response)
- Toast fuer Erfolg/Fehler (Sonner)
- 409 Conflict (letzte Admin-Zuweisung) → spezifische Fehlermeldung

---

## Testing

### Backend

- **Unit Tests (Mockito):** RoleService (CRUD, Delta-Berechnung, Schutzlogik)
- **Integration Tests (Testcontainers):** RoleController (alle Endpoints, Permission-Checks, System-Rollen-Schutz, letzte-Admin-Schutz)
- **Provisioning Test:** System-Rollen werden korrekt erstellt + OpenFGA Permissions gesetzt

### Frontend

- **Vitest:** use-role-actions Hook, Rollen-Klon-Logik im Invite-Dialog
- **Komponenten-Tests:** Role Table, Create Dialog, Assignees Dialog

---

## Abgrenzung (nicht in Phase 3d)

- Dynamische Permission-Matrix (Rolle X bekommt Permissions auf App/Content)
- Platform Admin User Management (Phase 3e)
- E2E Tests Auth Flow (Phase 3f)
- App/Content-Level Rollen-Zuweisung
