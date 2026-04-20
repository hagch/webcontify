# Phase 3e: Platform Admin Management — Design Spec

## Ueberblick

Platform Admin Verwaltung: Einladen, Auflisten, Deaktivieren, Entfernen, Passwort-Reset. Keycloak ist einzige Source of Truth — kein DB-Table, kein OpenFGA. Die `PLATFORM_ADMIN` Keycloak-Rolle bleibt der einzige Schalter: wer sie hat, darf alles.

Zusaetzlich: generischer "Passwort aendern" Link fuer alle User (Platform + Tenant) via Keycloak Account Console.

---

## Architektur

### Keycloak als Source of Truth

Platform Admins leben ausschliesslich im Keycloak Platform Realm (`webcontify-platform`). Keine lokale DB-Tabelle, keine OpenFGA-Integration fuer Platform-Ebene.

Gruende:
- Typischerweise < 20 Platform Admins — keine Query-Performance-Probleme
- Kein Sync-Problem zwischen DB und Keycloak
- Keycloak hat alle noetigeren Daten (Name, Email, Status, Erstellungsdatum)

### OpenFGA Cleanup

`type platform` wird aus `model.json` entfernt — aktuell ungenutzt und in Phase 3e nicht benoetigt. Falls spaeter granulare Platform-Permissions noetig werden, kann der Type dann hinzugefuegt werden.

### Permission-Modell

Keine Aenderung am bestehenden Modell:
- `PLATFORM_ADMIN` Keycloak-Rolle = darf alles
- `isPlatformAdmin` Bypass im `PermissionCheckAspect` bleibt
- Alle Platform Admin Endpoints pruefen nur `isPlatformAdmin` (kein granulares Permission-System)

---

## API Endpoints

### Platform Admin CRUD

| Method | Path | Operation | Beschreibung |
|---|---|---|---|
| GET | `/api/v1/admin/admins` | listPlatformAdmins | Alle Platform Admins auflisten |
| POST | `/api/v1/admin/admins` | invitePlatformAdmin | Neuen Admin einladen |
| GET | `/api/v1/admin/admins/{adminId}` | getPlatformAdmin | Admin Detail |
| PUT | `/api/v1/admin/admins/{adminId}/status` | updatePlatformAdminStatus | Aktivieren/Deaktivieren |
| DELETE | `/api/v1/admin/admins/{adminId}` | deletePlatformAdmin | Admin entfernen (Hard Delete in Keycloak) |
| POST | `/api/v1/admin/admins/{adminId}/reset-password` | resetPlatformAdminPassword | Passwort-Reset erzwingen |

### Auth Endpoints (alle authentifizierten User)

| Method | Path | Operation | Beschreibung |
|---|---|---|---|
| GET | `/api/v1/auth/me` | getMyProfile | Eigenes Profil (Name, Email) — funktioniert fuer Platform + Tenant User |
| GET | `/api/v1/auth/account-url` | getAccountUrl | Keycloak Account Console URL fuer aktuellen User |

Beide Endpoints funktionieren fuer Platform Admins und Tenant User gleichermassen. Die Daten/URL werden aus dem aktuellen JWT/Realm gebaut.

**SecurityConfiguration:** `/api/v1/auth/**` muss als `.authenticated()` eingetragen werden (aktuell nicht abgedeckt — faellt unter `denyAll`).

### Request/Response Schemas

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
    createdAt: { type: string, format: date-time }  # Keycloak-Timestamp wird zu ISO-8601 konvertiert

PlatformAdminListResponse:
  type: object
  required: [admins]
  properties:
    admins:
      type: array
      items: { $ref: '#/components/schemas/PlatformAdminResponse' }
  # Keine Pagination — erfahrungsgemaess < 20 Platform Admins. Bewusste Ausnahme.

AccountUrlResponse:
  type: object
  required: [url]
  properties:
    url: { type: string }
```

### Schutzlogik

- Alle Endpoints nur fuer `isPlatformAdmin` (bestehender Mechanismus)
- Letzten Admin nicht deaktivieren/loeschen — Keycloak Admin API abfragen: "wie viele User haben PLATFORM_ADMIN Rolle?" → wenn <= 1 und Ziel ist dieser User → 409 Conflict
- Sich selbst nicht deaktivieren/loeschen → 400 Bad Request
- Passwort-Reset nicht fuer sich selbst (dafuer Account Console) → 400 Bad Request
- Email-Duplikat beim Einladen: Keycloak gibt 409 zurueck → wird als `ConflictException("admin.email_already_exists")` behandelt (kein internes Keycloak-Detail nach aussen)
- Keycloak-User nicht gefunden (getById, updateStatus, delete): Keycloak 404 → `NotFoundException("admin.not_found")`
- Hard Delete in Keycloak — kein Audit Trail in dieser Phase. Keycloak Admin Events koennen spaeter fuer Audit aktiviert werden.
- Letzter-Admin-Check ist nicht atomar (Keycloak hat keine Transaktionen). Akzeptables Risiko bei < 20 Admins — wird im Code kommentiert.

---

## Backend Services

### PlatformAdminService

Duenne Schicht ueber `KeycloakAdminService`. Nutzt den Platform Realm Name aus der Konfiguration.

```
list()
  → Keycloak: listUsersWithRole(platformRealmName, "PLATFORM_ADMIN")
  → Map zu PlatformAdminResponse

invite(email, firstName, lastName)
  → Keycloak: createUser(platformRealmName, email, firstName, lastName)
  → Keycloak: assignRealmRole(platformRealmName, userId, "PLATFORM_ADMIN")
  → Keycloak: sendInviteEmail(platformRealmName, userId)

getById(adminId)
  → Keycloak: getUserById(platformRealmName, adminId)
  → Pruefung: hat PLATFORM_ADMIN Rolle → sonst 404

updateStatus(adminId, enabled)
  → Schutzlogik: letzter Admin + Self-Check
  → Keycloak: enableUser/disableUser

delete(adminId)
  → Schutzlogik: letzter Admin + Self-Check
  → Keycloak: deleteUser (Hard Delete)

resetPassword(adminId)
  → Schutzlogik: nicht sich selbst
  → Keycloak: addRequiredAction(platformRealmName, adminId, "UPDATE_PASSWORD")

```

### AuthService (neuer Service)

Realm-agnostischer Service fuer Endpoints die sowohl fuer Platform als auch Tenant User funktionieren.

```
getMe()
  → Realm aus JWT Issuer extrahieren
  → User-ID aus SecurityContext
  → Keycloak: getUserById(realmFromJwt, currentUserId)

getAccountUrl()
  → Realm aus JWT Issuer extrahieren (Issuer wurde bereits durch MultiRealmJwtDecoder validiert)
  → Return: keycloakBaseUrl + "/realms/" + realmName + "/account/#/security/signingin"
```

### KeycloakAdminService Erweiterungen

Neue Methoden (zusaetzlich zu den bestehenden):

```
listUsersWithRole(realmName, roleName) → List<KeycloakUserRepresentation>
  → GET /admin/realms/{realm}/roles/{role}/users

assignRealmRole(realmName, userId, roleName) → void
  → POST /admin/realms/{realm}/users/{userId}/role-mappings/realm
  → Body: [{id, name}] der Rolle

addRequiredAction(realmName, userId, action) → void
  → GET /admin/realms/{realm}/users/{userId} (bestehende requiredActions lesen)
  → PUT /admin/realms/{realm}/users/{userId}
  → Body: {requiredActions: [...bestehende, "UPDATE_PASSWORD"]} (additiv, nicht destruktiv)

getUserById(realmName, userId) → KeycloakUserRepresentation
  → GET /admin/realms/{realm}/users/{userId}

getUserRealmRoles(realmName, userId) → List<RoleRepresentation>
  → GET /admin/realms/{realm}/users/{userId}/role-mappings/realm
```

Bestehende Methoden die wiederverwendet werden:
- `createUser(CreateKeycloakUserRequest)` — User im Realm erstellen
- `deleteUser(DeleteKeycloakUserRequest)` — User loeschen
- `enableUser(EnableKeycloakUserRequest)` / `disableUser(DisableKeycloakUserRequest)`
- `sendInviteEmail(SendInviteEmailRequest)` — Einladungs-Email

### PlatformAdminController

```java
@RestController
@RequestMapping("/api/v1/admin/admins")
public class PlatformAdminController implements PlatformAdminsApi {
    // Alle Methoden pruefen isPlatformAdmin via Spring Security Config
    // (nicht via @RequiresPermission, da das OpenFGA ist)
}
```

### AuthController (neuer Controller)

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApi {
    // getAccountUrl() — fuer alle authentifizierten User
}
```

---

## Frontend

### Dateistruktur

```
src/features/admins/
  ├── components/
  │   ├── admin-table.tsx
  │   ├── invite-admin-dialog.tsx
  │   ├── admin-actions-menu.tsx
  │   └── __tests__/
  ├── hooks/
  │   └── use-admin-actions.ts
  └── schemas/
      └── invite-admin.ts
```

### Admins-Liste (`/admins`)

- Tabelle: Name (Vorname + Nachname), E-Mail, Status Badge (Aktiv/Deaktiviert), Erstellt am
- "Admin einladen" Button → Invite Dialog
- Actions pro Zeile:
  - Deaktivieren/Aktivieren (Toggle je nach aktuellem Status)
  - Passwort-Reset erzwingen
  - Entfernen (mit Bestaetigungsdialog)
- Kein Klick auf Zeile (kein Detail-Seite — Admins haben kein Profil mit Rollen/Permissions)
- Skeleton Loading, Empty State

### Invite Admin Dialog

- Felder: E-Mail (required), Vorname (required), Nachname (required)
- React Hook Form + Zod
- Error Handling: 409 (Email existiert) → Inline-Fehler
- Success: Toast + Dialog schliessen + Query invalidieren

### Passwort aendern (alle User)

- Im `NavUser` Dropdown (Sidebar unten) neuen Menuepunkt "Passwort aendern" hinzufuegen
- Klick → `GET /api/v1/auth/account-url` → Redirect in neuem Tab
- Funktioniert fuer Platform Admins und Tenant User gleichermassen

### Loading/Error States

- Skeleton Loading fuer Tabelle
- Button Loading bei Mutations (kein Optimistic Update)
- Toast fuer Erfolg/Fehler (Sonner)
- 409 Conflict (letzter Admin) → spezifische Fehlermeldung

---

## Testing

### Backend

- **Unit Tests (Mockito):** PlatformAdminService (CRUD, Schutzlogik)
- **Integration Tests (MockMvc):** PlatformAdminController (alle Endpoints, Schutzlogik, Keycloak Mocks)

### Frontend

- **Vitest:** admin-table, invite-admin-dialog

---

## Abgrenzung (nicht in Phase 3e)

- Granulare Platform-Permissions (alle Admins sind gleich)
- Profilbild-Upload (Initialen-Avatare reichen)
- Profil-Settings-Seite (kommt spaeter)
- OpenFGA fuer Platform-Ebene (entfernt, nicht gebraucht)
