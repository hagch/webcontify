# Phase 3b: User Management + Invite — Design Spec

## Scope

User Management und Invite-Flow fuer Tenant Admins. Aufbauend auf Phase 3a Infrastruktur (KeycloakAdminService, OpenFgaService, PermissionService, @RequiresPermission).

Kein Self-Service (Name/Email editieren), keine Rollen-Verwaltung, kein Bulk-Invite — nur CRUD + Einzel-Invite + Resend + Status-Management.

---

## Entscheidungen

| # | Entscheidung | Begruendung |
|---|---|---|
| 1 | Lokale DB als Primary, Keycloak fuer Auth-Operationen | Einfache Queries, RLS gratis, Keycloak ist Implementierungsdetail |
| 2 | `tenant_user` + `tenant_role` Tabellen, Rollen-Zuordnung nur in OpenFGA | `tenant_role` speichert Metadaten (Name, Beschreibung) die OpenFGA nicht kann. Zuordnung User↔Rolle laeuft ueber OpenFGA `role#assignee` — keine lokale Join-Table noetig |
| 3 | Nur "Admin"-Rolle in Phase 3b | Rollen-CRUD kommt in Phase 3d, aktuell wird jeder User als `tenant#admin` in OpenFGA gesetzt |
| 4 | Einzelner Invite (kein Bulk) | Einfach, Bulk kommt bei Bedarf spaeter |
| 5 | Status-Wechsel INVITED → ACTIVE beim ersten Login | Backend erkennt JWT-User mit Status INVITED und setzt auf ACTIVE |
| 6 | Nach Passwort-Setup Redirect zur Tenant Login Page | Einfach, kein Custom Keycloak Code, User loggt sich normal ein |
| 7 | Initialer Admin bei Tenant-Erstellung durchlaeuft gleichen Invite-Flow | TenantProvisioningListener Step 4 erstellt auch `tenant_user` Eintrag |
| 8 | Read-only Detail-Seite | Name/Email editieren ist Self-Service (Phase 3g) |
| 9 | Aktionen in Tabelle UND auf Detail-Seite | Schneller Zugriff auf haeufige Aktionen ohne Navigation |
| 10 | Deaktivieren statt Loeschen als Default-Aktion | Loeschen ist destruktiv, Deaktivieren ist reversibel. Loeschen als separate Aktion verfuegbar |
| 11 | Keycloak User bei Deaktivierung disablen, bei Loeschung loeschen | Konsistenz zwischen lokaler DB und Keycloak |
| 12 | OpenFGA Relationships bei Loeschung entfernen | Keine verwaisten Permissions |
| 13 | Profilbild nicht in Phase 3b | Kommt spaeter (S3 Upload, eigener Flow) |
| 14 | Letzter Admin kann nicht geloescht/deaktiviert werden | Tenant ohne aktiven Admin ist nicht behebbar (nur Platform Admin Eingriff) |
| 15 | Delete ist Hard-Delete in Keycloak + Soft-Delete in lokaler DB | Soft Delete lokal dient als Audit-Trail, User kann nicht reaktiviert werden — das ist bewusst, Reaktivierung ist kein Use Case |
| 16 | `inviteUser` als OperationId (statt `createUser`) | Fachliche Ausnahme vom `createXxx` Pattern, Invite beschreibt den Vorgang praeziser |
| 17 | Keine `tenant_user_role` Tabelle — Zuordnung User↔Rolle nur in OpenFGA | OpenFGA `role#assignee` Relationships sind die Single Source of Truth. Lokale Sync-Tabelle waere redundant und Konsistenzrisiko |
| 18 | OpenAPI Spec in `identity-api.yaml` | User/Rollen/Permissions gehoeren zur Identity Domain, eine Datei pro Domain laut `docs/09-openapi-spec-regeln.md` |
| 19 | Keycloak vor DB-Insert im Invite-Flow, Cleanup im Catch-Block | Keycloak-UUID wird als PK in tenant_user verwendet, daher Keycloak zuerst. Bei DB-Fehler wird Keycloak-User im Catch-Block geloescht + geloggt |
| 20 | System-Rolle "Admin" wird bei Tenant-Provisioning angelegt | `tenant_role` Eintrag (is_system_role=true), User↔Rolle Zuordnung nur in OpenFGA (`role#assignee`) |

---

## DB-Schema

### Flyway Migration

```sql
-- V20260315_001__add_user_management_tables.sql

CREATE TABLE tenant_user (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    email           VARCHAR(255) NOT NULL,
    first_name      VARCHAR(255) NOT NULL,
    last_name       VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'INVITED',
    invited_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    CONSTRAINT uq_tenant_user_email UNIQUE (tenant_id, email)
);

CREATE TABLE tenant_role (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    is_system_role  BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    CONSTRAINT uq_tenant_role_name UNIQUE (tenant_id, name)
);

-- Keine tenant_user_role Tabelle — Zuordnung User↔Rolle laeuft ueber OpenFGA (role#assignee)

-- Indizes fuer RLS-Performance und Foreign Keys
CREATE INDEX idx_tenant_user_tenant_id ON tenant_user(tenant_id);
CREATE INDEX idx_tenant_role_tenant_id ON tenant_role(tenant_id);

-- RLS Policies (Pattern aus docs/08-db-schema-regeln.md)
ALTER TABLE tenant_user ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenant_user ON tenant_user
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );

ALTER TABLE tenant_role ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenant_role ON tenant_role
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );

```

### User-ID Strategie

Die `tenant_user.id` ist die **gleiche UUID wie die Keycloak User-ID**. Kein separates Mapping noetig. `KeycloakAdminService.createUser()` gibt die Keycloak-User-ID zurueck, diese wird als PK in `tenant_user` verwendet.

### UserStatus Enum

```java
public enum UserStatus {
    INVITED,      // Eingeladen, Passwort noch nicht gesetzt
    ACTIVE,       // Hat sich eingeloggt, aktiv
    DEACTIVATED   // Von Admin deaktiviert
}
```

### Status State Machine

```
INVITED ──→ ACTIVE           (automatisch: UserActivationFilter beim ersten Login)
INVITED ──→ DEACTIVATED      (Admin-Aktion: Keycloak User disablen)
ACTIVE  ──→ DEACTIVATED      (Admin-Aktion: Keycloak User disablen)
DEACTIVATED → ACTIVE         (Admin-Aktion: Keycloak User enablen)
```

Unzulaessige Wechsel:
- `DEACTIVATED → INVITED` — User war bereits eingeladen, Status kann nicht zurueckgesetzt werden
- `ACTIVE → INVITED` — dito

**Wichtig:** Der `UserActivationFilter` prueft den lokalen DB-Status. Ein DEACTIVATED User wird **nicht** auf ACTIVE gesetzt, auch wenn er ein gueltiges JWT hat. Der Filter aktiviert nur INVITED → ACTIVE.

### Soft Delete

`tenant_user` und `tenant_role` nutzen das bestehende Soft-Delete Pattern (deleted_at/deleted_by). Alle Queries filtern `deleted_at IS NULL`.

---

## OpenAPI Spec

### Datei: `openapi/identity-api.yaml`

Laut `docs/09-openapi-spec-regeln.md`: eine Spec-Datei pro Domain-Modul. User/Rollen/Permissions gehoeren zur Identity Domain.

### Endpoints

```
# Tenant User Management (Tenant-Kontext, Subdomain-basiert)
POST   /api/v1/tenant/users                    → inviteUser
GET    /api/v1/tenant/users                    → listUsers
GET    /api/v1/tenant/users/{userId}           → getUser
PUT    /api/v1/tenant/users/{userId}/status    → updateUserStatus
POST   /api/v1/tenant/users/{userId}/resend-invite → resendInvite
DELETE /api/v1/tenant/users/{userId}           → deleteUser
```

### Request/Response Schemas

```yaml
InviteUserRequest:
  type: object
  required: [email, firstName, lastName]
  properties:
    email:
      type: string
      format: email
    firstName:
      type: string
      minLength: 1
      maxLength: 255
    lastName:
      type: string
      minLength: 1
      maxLength: 255

UpdateUserStatusRequest:
  type: object
  required: [status]
  properties:
    status:
      $ref: '#/components/schemas/UserStatusUpdate'

UserStatusUpdate:
  type: string
  enum:
    - ACTIVE
    - DEACTIVATED

UserStatus:
  type: string
  enum:
    - INVITED
    - ACTIVE
    - DEACTIVATED

UserResponse:
  type: object
  required: [id, email, firstName, lastName, status, createdAt, updatedAt]
  properties:
    id:
      type: string
      format: uuid
    email:
      type: string
    firstName:
      type: string
    lastName:
      type: string
    status:
      $ref: '#/components/schemas/UserStatus'
    invitedBy:
      type: string
      format: uuid
      nullable: true
    createdAt:
      type: string
      format: date-time
    updatedAt:
      type: string
      format: date-time

UserListResponse:
  type: object
  required: [items, page, size, totalElements, totalPages]
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/UserResponse'
    page:
      type: integer
    size:
      type: integer
    totalElements:
      type: integer
    totalPages:
      type: integer
```

### HTTP Response Codes

Alle Endpoints haben zusaetzlich `401 Unauthorized` und `403 Forbidden` als Standard-Responses (siehe `docs/09-openapi-spec-regeln.md`).

| Endpoint | Erfolg | Fachliche Fehler |
|---|---|---|
| inviteUser | 201 + Location + Body | 400 (Validation), 409 (Email existiert) |
| listUsers | 200 | — |
| getUser | 200 | 404 |
| updateUserStatus | 200 | 400 (ungueltiger Wechsel), 404 |
| resendInvite | 204 | 400 (nicht INVITED), 404 |
| deleteUser | 204 | 404 |

---

## Backend

### Package-Struktur

```
identity/
  api/
    TenantProvisioningController.java    (besteht, retry endpoint)
    UserController.java                  (NEU)
  service/
    OpenFgaService.java                  (besteht)
    KeycloakAdminService.java            (besteht)
    PermissionService.java               (besteht)
    TenantProvisioningListener.java      (ANPASSEN: Step 4 erstellt tenant_user)
    UserService.java                     (NEU)
    UserActivationService.java           (NEU: INVITED → ACTIVE beim Login)
  mapper/
    UserMapper.java                      (NEU, MapStruct)
  model/
    TenantUser.java                      (NEU, Domain Model)
    UserStatus.java                      (NEU, Enum)
  repository/
    TenantUserRepository.java            (NEU, Spring Data JPA)
    TenantRoleRepository.java            (NEU, Spring Data JPA, vorbereitet fuer Phase 3d)
    entity/
      TenantUserEntity.java              (NEU, JPA Entity)
      TenantRoleEntity.java              (NEU, JPA Entity, vorbereitet fuer Phase 3d)
      (keine TenantUserRoleEntity — Zuordnung laeuft ueber OpenFGA)
```

### Domain Model

```java
// TenantUser.java (Domain Model, kein JPA)
public class TenantUser {
    private UUID id;
    private UUID tenantId;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private UUID invitedBy;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;
}
```

### JPA Entity

```java
// TenantUserEntity.java
@Entity
@Table(name = "tenant_user")
@EntityListeners(AuditListener.class)
public class TenantUserEntity extends TenantAwareBaseEntity {
    @Column(nullable = false)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    @Convert(converter = UserStatusConverter.class)
    private UserStatus status;

    @Column(name = "invited_by")
    private UUID invitedBy;
}

// UserStatusConverter.java (JPA AttributeConverter)
@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {
    @Override public String convertToDatabaseColumn(UserStatus status) { return status.name(); }
    @Override public UserStatus convertToEntityAttribute(String value) { return UserStatus.valueOf(value); }
}

// TenantRoleEntity.java (vorbereitet, noch kein Service-Code)
@Entity
@Table(name = "tenant_role")
@EntityListeners(AuditListener.class)
public class TenantRoleEntity extends TenantAwareBaseEntity {
    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_system_role", nullable = false)
    private boolean systemRole;
}

// Keine TenantUserRoleEntity — User↔Rolle Zuordnung laeuft ueber OpenFGA (role#assignee)
```

### UserService

```java
public class UserService {

    // Invite: DB-Insert zuerst → Keycloak → OpenFGA → Email
    // DB-first verhindert Zombie-User in Keycloak bei DB-Fehler (Entscheidung 19)
    @Transactional
    public TenantUser invite(InviteUserRequest request) {
        // 1. Pruefen ob Email schon existiert (aktiver oder eingeladener User)
        //    → ConflictException("user.email_already_exists") wenn ja
        // 2. UUIDv7 generieren fuer User-ID (wird in Keycloak als externe ID gesetzt)
        //    Hinweis: Keycloak generiert eigene UUID — wir nutzen unsere als PK,
        //    muessen aber die Keycloak-UUID zurueck-mappen. Alternative: Keycloak-UUID
        //    als PK verwenden (wie in User-ID Strategie beschrieben). Da createUser()
        //    die Keycloak-UUID zurueckgibt, verwenden wir diese als PK.
        //    Reihenfolge daher: Keycloak first fuer ID, dann DB.
        //
        //    KORREKTUR: Reihenfolge ist DB-Insert NACH Keycloak-Call, weil wir die
        //    Keycloak-UUID als PK brauchen. Aber wir wrappen alles in try/catch:
        //    - Keycloak-User anlegen → keycloakUserId
        //    - DB-Insert (in laufender Transaktion)
        //    - Bei DB-Fehler: Keycloak-User im catch loeschen + loggen
        // 3. tenant_user Eintrag: id = keycloakUserId, status = INVITED
        //    invitedBy = aktueller User aus TenantContext
        // 4. OpenFGA: grant user:{id} admin tenant:{tenantId}
        // 5. KeycloakAdminService.sendInviteEmail()
        // 6. Return TenantUser
    }

    // Liste aller User im Tenant (paginiert)
    public Page<TenantUser> list(Pageable pageable) {
        // Query auf tenant_user WHERE deleted_at IS NULL
        // RLS filtert automatisch auf aktuellen Tenant
    }

    // Einzelnen User laden
    public TenantUser getById(UUID userId) {
        // NotFoundException wenn nicht gefunden oder soft-deleted
    }

    // Status aendern (gemaess State Machine)
    public TenantUser updateStatus(UUID userId, UserStatus newStatus) {
        // 1. User laden, pruefen ob Transition erlaubt (siehe State Machine)
        // 2. Self-Check: user.cannot_deactivate_self wenn userId == currentUserId
        // 3. Last-Admin-Guard: bei DEACTIVATED pruefen ob noch min. 1 aktiver Admin verbleibt
        //    → BadRequestException("user.last_admin_cannot_be_removed")
        // 4. Keycloak: User enablen/disablen je nach Status
        //    ACTIVE/INVITED → DEACTIVATED: keycloakAdminService.disableUser()
        //    DEACTIVATED → ACTIVE: keycloakAdminService.enableUser()
        // 5. Status in lokaler DB aktualisieren
    }

    // Invite erneut senden
    public void resendInvite(UUID userId) {
        // Validierung: nur fuer Status INVITED
        //   → BadRequestException("user.not_invited") wenn nicht INVITED
        // KeycloakAdminService.sendInviteEmail()
    }

    // User loeschen (Soft Delete lokal + Hard Delete Keycloak + OpenFGA Cleanup)
    public void delete(UUID userId) {
        // 1. Self-Check: user.cannot_delete_self wenn userId == currentUserId
        // 2. Last-Admin-Guard: pruefen ob noch min. 1 aktiver Admin verbleibt
        //    → BadRequestException("user.last_admin_cannot_be_removed")
        // 3. Soft Delete in lokaler DB (deleted_at/deleted_by setzen)
        // 4. KeycloakAdminService.deleteUser() (hard delete in Keycloak)
        // 5. OpenFGA: revoke user:{id} admin tenant:{tenantId}
        // Hinweis: User kann nach Delete nicht reaktiviert werden (Entscheidung 15)
    }
}
```

### UserActivationService — INVITED → ACTIVE beim Login

```java
@Component
public class UserActivationService {

    // Wird aus einem Filter oder AOP aufgerufen nach erfolgreicher JWT-Validierung
    public void activateOnFirstLogin(UUID userId, UUID tenantId) {
        // 1. tenant_user laden
        // 2. Wenn status == INVITED → auf ACTIVE setzen + updatedAt/updatedBy
        // 3. Keycloak User ist bereits enabled (wurde bei Invite enabled angelegt)
    }
}
```

**Integration:** `UserActivationFilter` als `OncePerRequestFilter`, registriert in der Security Filter Chain via `addFilterAfter(userActivationFilter, BearerTokenAuthenticationFilter.class)`. Laeuft nach JWT-Validierung, hat Zugriff auf `SecurityContext`.

**Logik:**
1. Platform Admin → Skip (kein tenant_user Eintrag)
2. User-ID aus JWT extrahieren
3. `tenant_user` PK-Lookup (via `TenantUserRepository.findById()`)
4. Wenn nicht gefunden → Skip (User existiert nicht in lokaler DB, z.B. Legacy-User)
5. Wenn Status `INVITED` → auf `ACTIVE` setzen, `updatedBy` = userId
6. Wenn Status `DEACTIVATED` → **nicht** auf ACTIVE setzen. Request laeuft weiter, aber `@RequiresPermission` wird den Zugriff regeln. Alternativ koennte der Filter hier direkt 403 werfen — aber das waere eine Dopplung mit dem Permission-Check. Keycloak disabled Users koennen kein gueltiges JWT bekommen, also ist dieser Fall nur bei Cache/Timing-Edge-Cases relevant.
7. Wenn Status `ACTIVE` → No-Op

**Performance:** PK-Lookup pro Request (Index, schnell). Kein Cache — Vermeidet Stale-State-Probleme bei Deaktivierung. Optimierung spaeter bei Bedarf.

### Anpassung TenantProvisioningListener

Step 4 (INITIAL_ADMIN) wird erweitert:

```java
// Bestehend:
// 1. KeycloakAdminService.createUser()
// 2. OpenFGA: grant user:{id} admin tenant:{tenantId}
// 3. KeycloakAdminService.sendInviteEmail()

// NEU dazu:
// 1.5. TenantUserRepository.save() → tenant_user Eintrag mit:
//       - id = keycloakUserId
//       - tenantId = event.tenantId()
//       - email = event.adminEmail()
//       - firstName = event.adminFirstName()
//       - lastName = event.adminLastName()
//       - status = INVITED
//       - invitedBy = null (System-Erstellung)
//       - createdBy = SystemUser.ID
```

**RLS-Kontext im Provisioning Listener:**

Der Listener hat keinen HTTP-Request-Kontext. Die `TenantAwareDataSource` (DataSource Proxy) setzt `app.tenant_id` basierend auf `TenantContext`. Fuer den Listener muss der Tenant-Kontext manuell gesetzt werden:

```java
// Im TenantProvisioningListener, vor DB-Operationen:
TenantContext.setTenantInformation(new TenantInformation(event.tenantId(), false));
try {
    // DB-Inserts (tenant_role, tenant_user)
} finally {
    TenantContext.clear();
}
```

Alternativ, falls `TenantContext` nur mit SecurityContext funktioniert: Direkter JDBC-Call `SET LOCAL app.tenant_id = '{tenantId}'` innerhalb der `@Transactional` Methode. Der bestehende Provisioning Listener setzt bereits Felder auf der `tenant` Tabelle (die kein `tenant_id` hat) — der neue Insert auf `tenant_user` braucht aber den RLS-Kontext.

**Zusaetzlich in Step 4:**

Vor dem `tenant_user` Eintrag muss die System-Rolle "Admin" angelegt werden:

```java
// 1. KeycloakAdminService.createUser() → keycloakUserId
// 1.5 TenantRoleRepository.save() → "Admin" Rolle mit is_system_role=true
// 1.6 TenantUserRepository.save() → tenant_user Eintrag (status = INVITED)
// 2. OpenFGA: grant user:{id} admin tenant:{tenantId} (= Rollenzuordnung)
// 3. KeycloakAdminService.sendInviteEmail()
```

`invitedBy` = null (System-Erstellung). `createdBy` / `updatedBy` = `SystemUser.ID` (existiert bereits aus Phase 1a).

### Anpassung KeycloakAdminService

Neue Methoden fuer Enable/Disable:

```java
// Neuer Request Record
record DisableKeycloakUserRequest(String realmName, UUID userId) {}
record EnableKeycloakUserRequest(String realmName, UUID userId) {}

// Methoden
void disableUser(DisableKeycloakUserRequest request);
void enableUser(EnableKeycloakUserRequest request);
```

Implementierung: `UserRepresentation.setEnabled(false/true)` + `realmResource.users().get(id).update(rep)`

### Permission Checks auf Endpoints

```java
@RestController
@RequestMapping("/api/v1/tenant/users")
public class UserController {

    @PostMapping
    @RequiresPermission(action = "inviter", resource = "user_management")
    public ResponseEntity<UserResponse> inviteUser(@RequestBody InviteUserRequest request) { ... }

    @GetMapping
    @RequiresPermission(action = "reader", resource = "user_management")
    public ResponseEntity<UserListResponse> listUsers(Pageable pageable) { ... }

    @GetMapping("/{userId}")
    @RequiresPermission(action = "reader", resource = "user_management")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) { ... }

    @PutMapping("/{userId}/status")
    @RequiresPermission(action = "editor", resource = "user_management")
    public ResponseEntity<UserResponse> updateUserStatus(...) { ... }

    @PostMapping("/{userId}/resend-invite")
    @RequiresPermission(action = "inviter", resource = "user_management")
    public ResponseEntity<Void> resendInvite(@PathVariable UUID userId) { ... }

    @DeleteMapping("/{userId}")
    @RequiresPermission(action = "deleter", resource = "user_management")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) { ... }
}
```

Die `user_management` Resource ist Type-Level (kein resourceId) — alle User eines Tenants werden gleich berechtigt. Granulare Per-User Permissions sind nicht vorgesehen.

**Platform Admin Zugriff:** Platform Admins koennen diese Endpoints auf Tenant-Subdomains aufrufen. Der `PermissionCheckAspect` bypassed alle Checks fuer `platformAdmin == true`. Der Tenant-Kontext wird ueber die Subdomain aufgeloest (bestehender `TenantResolutionFilter`). Platform Admin JWT wird auf allen Subdomains akzeptiert (Phase 3a: Cross-Subdomain Session).

### Error Codes (i18n Keys)

| Code | HTTP | Wann |
|---|---|---|
| `user.not_found` | 404 | User existiert nicht oder soft-deleted |
| `user.email_already_exists` | 409 | Email im Tenant bereits vergeben |
| `user.invalid_status_transition` | 400 | Ungueltiger Status-Wechsel |
| `user.not_invited` | 400 | Resend Invite fuer nicht-INVITED User |
| `user.cannot_delete_self` | 400 | User versucht sich selbst zu loeschen |
| `user.cannot_deactivate_self` | 400 | User versucht sich selbst zu deaktivieren |
| `user.last_admin_cannot_be_removed` | 400 | Letzter aktiver Admin kann nicht geloescht/deaktiviert werden |

---

## Frontend

### Dateistruktur

```
src/features/users/
  components/
    user-table.tsx
    invite-user-dialog.tsx
    user-status-badge.tsx
    user-actions-menu.tsx
    __tests__/
      user-table.test.tsx
      invite-user-dialog.test.tsx
  schemas/
    invite-user.ts            (Zod Schema)
  hooks/
    use-user-actions.ts       (Resend, Status, Delete Mutations + Invalidierung)

src/app/(workspace)/users/
  page.tsx                    (User List Page)
  [userId]/
    page.tsx                  (User Detail Page)
```

### User List Page

Tabelle mit Spalten:
- **Name** (Vorname + Nachname, Link zur Detail-Seite)
- **Email**
- **Status** (Badge: Eingeladen/Aktiv/Deaktiviert)
- **Eingeladen am** (createdAt, formatiert)
- **Aktionen** (Dropdown-Menu)

Aktionen im Dropdown-Menu (kontextabhaengig):
- Status INVITED: "Einladung erneut senden", "Deaktivieren", Separator, "Loeschen" (destructive/rot)
- Status ACTIVE: "Deaktivieren", Separator, "Loeschen" (destructive/rot)
- Status DEACTIVATED: "Aktivieren", Separator, "Loeschen" (destructive/rot)

Button oben: "User einladen" → oeffnet Invite Dialog

Empty State: "Noch keine User. Laden Sie den ersten User ein." mit CTA-Button "Ersten User einladen".

Skeleton Loading: Tabelle mit Skeleton-Rows waehrend Laden.

Responsive Verhalten:
- Desktop (>=1024px): Alle Spalten sichtbar
- Tablet (768-1023px): Name, Email, Status, Aktionen (Datum hidden)
- Mobile (<768px): Name + Status in kompakter Row, Email als Secondary-Text unter Name, Aktionen-Menu bleibt

Accessibility:
- Tabelle mit `aria-label="User-Liste"`
- Actions-Menu Trigger: `aria-label="Aktionen fuer {Vorname} {Nachname}"`
- Tabelle ist keyboard-navigierbar (Tab durch Rows, Enter oeffnet Link/Menu)

### Invite User Dialog

- shadcn Dialog mit React Hook Form + Zod
- Felder: Email (type="email", autocomplete="email"), Vorname (autocomplete="given-name"), Nachname (autocomplete="family-name")
- Alle Felder mit sichtbarem Label (kein Placeholder-only)
- Validierung: Email-Format, Pflichtfelder, min/max Length
- Submit: `useInviteUser()` Mutation, Button zeigt Loading-Spinner waehrend Request
- Fehlerbehandlung:
  - 409 (Email existiert): Inline-Fehler unter Email-Feld, Auto-Focus auf Email-Feld
  - 400 (Validation): Field-Errors via `form.setError()`, Auto-Focus auf erstes fehlerhaftes Feld
  - 500/Netzwerk: Sonner Toast (aria-live="polite")
- Erfolg: Dialog schliesst, Focus kehrt zum "User einladen" Button zurueck, Toast "Einladung gesendet", Tabelle invalidiert

### User Detail Page

- Breadcrumb: `Users > {Vorname} {Nachname}` (Link zurueck zur Liste)
- Heading-Hierarchie: h1 = Seitenname, h2 = Sektionen (Profil, Aktionen, Rollen)
- Profil-Sektion (read-only): Name, Email, Status Badge, Eingeladen am, Eingeladen von
  - Read-only Felder visuell klar als nicht-editierbar erkennbar (kein Input-Styling, stattdessen Label/Value Pairs)
- Aktionen-Sektion: Buttons fuer Resend Invite, Deaktivieren/Aktivieren, Loeschen
  - Alle Aktions-Buttons zeigen Loading-Spinner waehrend Mutation laeuft
  - Loeschen-Button: `variant="destructive"` (rot), visuell von anderen Aktionen getrennt
  - Loeschen: Confirmation Dialog ("User wird unwiderruflich geloescht. Fortfahren?") mit Danger-Button
  - Deaktivieren: Direkt ohne Confirmation (reversibel)
  - Erfolg/Fehler: Toast-Nachricht nach jeder Aktion
- Rollen-Sektion: Platzhalter "Admin" Badge, Hinweis "Rollen-Verwaltung kommt in einer spaeteren Version"
- Skeleton Loading waehrend Daten geladen werden
- Zurueck-Navigation zur Liste erhaelt Scroll-Position und Pagination-State (Next.js Router Cache)

### User Status Badge

- INVITED: Orange/Amber Badge "Eingeladen"
- ACTIVE: Gruen/Emerald Badge "Aktiv"
- DEACTIVATED: Grau/Stone Badge "Deaktiviert"

### Hooks / Mutations

```typescript
// use-user-actions.ts
// Kapselt Mutations + Query-Invalidierung

function useUserActions() {
  const queryClient = useQueryClient()

  const resendInvite = useMutation(...)    // POST /users/{id}/resend-invite
  const updateStatus = useMutation(...)    // PUT /users/{id}/status
  const deleteUser = useMutation(...)      // DELETE /users/{id}

  // Alle invalidieren useListUsers Query nach Erfolg
  return { resendInvite, updateStatus, deleteUser }
}
```

### Navigation

Sidebar: "Users" Link unter Workspace-Sektion (besteht bereits als Placeholder).

---

## Testing-Strategie

### Backend Unit Tests (Mockito)

- `UserServiceTest` — Invite (happy path + Email-Duplikat), List, GetById, UpdateStatus (alle Kombinationen), ResendInvite (nur INVITED), Delete (Keycloak + OpenFGA Cleanup), Cannot-Delete/Deactivate-Self
- `UserMapperTest` — Entity ↔ Domain Mapping
- `UserActivationServiceTest` — INVITED → ACTIVE, Already ACTIVE (no-op), DEACTIVATED (no-op)
- `UserControllerTest` — Permission Annotations vorhanden, Request Validation

### Backend Integration Tests (Testcontainers)

- `UserManagementIntegrationTest` — Kompletter Invite → Login → Active Flow mit echtem PostgreSQL + gemocktem Keycloak/OpenFGA
- `UserActivationFilterIntegrationTest` — Filter setzt INVITED → ACTIVE bei JWT-Request
- `TenantProvisioningIntegrationTest` — Erweitert: prueft dass tenant_user Eintrag erstellt wird

### Frontend Tests (Vitest)

- `invite-user-dialog.test.tsx` — Formular-Validierung, Submit, Fehlerbehandlung
- `user-table.test.tsx` — Rendering, Aktionen, Empty State
- `use-user-actions.test.ts` — Mutations + Invalidierung

### Kein E2E in Phase 3b

Kommt in Phase 3f zusammen mit Auth Flow E2E.

---

## Abgrenzung: Was kommt spaeter

| Feature | Phase |
|---|---|
| Name/Email editieren (Self-Service) | 3g |
| Rollen-CRUD + Permission-Matrix | 3d |
| Platform Admin User Management (Cross-Tenant) | 3e |
| Profilbild Upload | spaeter |
| Bulk-Invite | spaeter |
| E2E Tests Auth + User Flow | 3f |
| Keycloak Custom Theme mit Tenant-Branding | spaeter |
