# Identity & Access Domain - Detailplanung

## Ueberblick

Die Identity & Access Domain verwaltet Authentifizierung und Autorisierung der Plattform.

**Bounded Context:** Identity & Access
**Storage:** Spring Data JPA (statisches Schema)

## Kernprinzipien

### 1. Platform UI als Single Point of Management

Keycloak und OpenFGA sind **interne Infrastruktur-Services**. Kein Admin (weder Platform
noch Tenant) greift jemals direkt auf Keycloak Admin Console oder OpenFGA zu.

**Alle Verwaltung passiert ueber die Platform UI und die Platform API:**

- User anlegen/einladen -> Platform API -> Keycloak Admin API (im Hintergrund)
- Rollen erstellen/bearbeiten -> Platform API -> OpenFGA API (im Hintergrund)
- Permissions vergeben -> Platform API -> OpenFGA API (im Hintergrund)
- Tenant erstellen -> Platform API -> Keycloak Realm + OpenFGA Store (im Hintergrund)

Die Platform API ist die **einzige Schnittstelle** fuer alle Identity & Access Operationen.
Keycloak und OpenFGA sind Implementierungsdetails, die austauschbar bleiben.

### 2. Klare Verantwortungstrennung

| Verantwortung | Service | Beschreibung |
|---|---|---|
| **Authentifizierung** (Wer bist du?) | Keycloak | Login, Passwort, MFA, JWT Token-Ausstellung |
| **User-Verwaltung** (User Lifecycle) | Keycloak | User anlegen, einladen, deaktivieren, Passwort Reset |
| **Autorisierung** (Was darfst du?) | OpenFGA | Rollen, Permissions, ReBAC - alles |

**Keycloak kennt keine Rollen und keine Permissions.** Keycloak liefert ein JWT mit
`user_id` und `tenant_realm`. Alles weitere kommt aus OpenFGA.

Eine Single Source of Truth fuer Permissions (OpenFGA). Kein Sync-Problem,
keine Redundanz, kein Auseinanderlaufen.

### 3. Explizite Konfiguration

Keine impliziten Defaults bei Berechtigungen. Alles muss explizit definiert werden.
Im UI koennen Defaults vorgeschlagen werden, aber das Backend erzwingt explizite Angaben.

## Keycloak Architektur

### Realm-Struktur

```
Platform Realm
  -> Platform Admin Users
  -> Service Accounts (Backend -> Keycloak Admin API)

Tenant Realm (pro Tenant, automatisch erstellt)
  -> Tenant Admin Users
  -> Tenant Users
  -> Eigenes Theme/Branding (Logo, Farben aus Tenant Config)
  -> Keine Cross-Tenant Visibility
```

### Was Keycloak verwaltet

- User Credentials (Passwort, MFA)
- User Profile (Name, Email)
- Login Page (Tenant-spezifisches Theme)
- JWT Token Ausstellung
- Password Reset Flow
- Email Verification

### Was Keycloak NICHT verwaltet

- Rollen (leben in OpenFGA)
- Permissions (leben in OpenFGA)
- Gruppen (leben in OpenFGA als Rollen)

### JWT Token Inhalt

```json
{
  "sub": "user-uuid",
  "email": "alice@example.com",
  "realm": "tenant-acme",
  "iss": "keycloak-url/realms/tenant-acme"
}
```

Keine Rollen oder Permissions im Token. Diese werden bei jedem Request
aus OpenFGA abgefragt (mit Caching).

## OpenFGA Authorization Model

Konsolidiertes Modell inkl. App-Ebene (siehe auch `04-app-domain.md`).
Vollstaendiges `.fga` File: `openfga/model.fga`

### Vererbungsketten

```
tenant admin -> app admin -> app editor -> app viewer
                          -> app creator
app editor   -> content_definition editor/deleter -> content_entry editor/deleter
app viewer   -> content_definition viewer         -> content_entry viewer
app creator  -> content_definition creator        -> content_entry creator
tenant admin -> tenant_config / user_management / role_management (alles)
Hoehere Berechtigung impliziert immer Leserecht.
```

### Typen und Relationships

```
model
  schema 1.1

type user

type platform
  relations
    define admin: [user]

type tenant
  relations
    define admin: [user]
    define member: [user] or admin

type role
  relations
    define tenant: [tenant]
    define assignee: [user]

type app
  relations
    define tenant: [tenant]
    define admin: [user, role#assignee] or admin from tenant
    define creator: [user, role#assignee] or admin from tenant
    define editor: [user, role#assignee] or admin
    define viewer: [user, role#assignee] or editor

type content_definition
  relations
    define parent_app: [app]
    define creator: [user, role#assignee] or creator from parent_app
    define editor: [user, role#assignee] or editor from parent_app
    define deleter: [user, role#assignee] or editor from parent_app
    define viewer: [user, role#assignee] or editor or viewer from parent_app

type content_entry
  relations
    define parent_definition: [content_definition]
    define owner: [user]
    define creator: [user, role#assignee] or creator from parent_definition
    define editor: [user, role#assignee] or owner or editor from parent_definition
    define deleter: [user, role#assignee] or deleter from parent_definition
    define viewer: [user, role#assignee] or editor or viewer from parent_definition

type content_field
  relations
    define parent_definition: [content_definition]
    define writer: [user, role#assignee] or editor from parent_definition
    define reader: [user, role#assignee] or writer or viewer from parent_definition

type tenant_config
  relations
    define tenant: [tenant]
    define editor: [user, role#assignee] or admin from tenant
    define reader: [user, role#assignee] or editor or admin from tenant

type user_management
  relations
    define tenant: [tenant]
    define inviter: [user, role#assignee] or admin from tenant
    define editor: [user, role#assignee] or admin from tenant
    define deleter: [user, role#assignee] or admin from tenant
    define reader: [user, role#assignee] or editor or inviter or deleter or admin from tenant

type role_management
  relations
    define tenant: [tenant]
    define creator: [user, role#assignee] or admin from tenant
    define editor: [user, role#assignee] or admin from tenant
    define deleter: [user, role#assignee] or admin from tenant
    define reader: [user, role#assignee] or editor or creator or deleter or admin from tenant
```

### ReBAC: Permission Propagation ueber References

Berechtigungen fliessen **nicht** automatisch ueber Content References.
Jedes Reference-Feld muss explizit konfigurieren was durchfliessen soll:

```
ReferenceFieldConfig:
  targetDefinitionId: UUID
  onDelete: CASCADE | SET_NULL | RESTRICT
  permissionPropagation: READ | READ_WRITE | NONE   (Pflichtfeld)
```

| Wert | Bedeutung |
|---|---|
| `NONE` | Keine Berechtigungen fliessen. Explizite Berechtigung auf Ziel-Definition noetig |
| `READ` | Wer den Quell-Entry lesen darf, darf auch den referenzierten Entry lesen |
| `READ_WRITE` | Wer den Quell-Entry bearbeiten darf, darf auch den referenzierten Entry bearbeiten |

**Delete fliesst nie durch** - immer explizit ueber die Ziel-Definition berechtigt.

Bei `READ` oder `READ_WRITE` werden die entsprechenden OpenFGA Relationships
automatisch geschrieben wenn ein Reference-Wert gesetzt wird, und entfernt wenn
er geaendert/geloescht wird.

## Request Flow

```
HTTP Request mit JWT
  1. JWT validieren (Keycloak Public Key)
  2. user_id + tenant_realm extrahieren
  3. Tenant Resolution (Realm -> Tenant ID)
  4. TenantContext setzen (RequestScope)
  5. Permission Check: OpenFGA
     "Darf user:alice action:read auf resource:content-entry:123?"
  6. TenantAwareDataSource (SET LOCAL fuer RLS)
  7. Business Logic
```

### Permission Check im Code

```java
@Component
public class PermissionService {
    private final OpenFgaClient openFgaClient;

    public boolean check(UUID userId, String action, String resourceType, UUID resourceId) {
        // OpenFGA Check: user:{userId} {action} {resourceType}:{resourceId}
        return openFgaClient.check(
            TupleKey.of("user:" + userId, action, resourceType + ":" + resourceId)
        ).getAllowed();
    }

    public void grant(UUID userId, String relation, String resourceType, UUID resourceId) {
        // OpenFGA Write: user:{userId} is {relation} of {resourceType}:{resourceId}
        openFgaClient.write(
            TupleKey.of("user:" + userId, relation, resourceType + ":" + resourceId)
        );
    }

    public void revoke(UUID userId, String relation, String resourceType, UUID resourceId) {
        openFgaClient.delete(
            TupleKey.of("user:" + userId, relation, resourceType + ":" + resourceId)
        );
    }
}
```

### Permission Caching

OpenFGA Checks passieren bei jedem Request. Um Performance zu gewaehrleisten:
- Short-lived Cache (z.B. 30-60 Sekunden) fuer wiederholte Checks im selben Request-Kontext
- Cache Invalidierung bei Permission-Aenderungen
- Bulk-Checks wo moeglich (OpenFGA BatchCheck API)

## API Endpunkte

### User Management (Tenant Admin)

```
POST   /api/v1/tenant/users/invite                  User einladen
         -> Keycloak: User anlegen im Tenant Realm
         -> OpenFGA: Default-Relationships schreiben
         -> Email: Einladungslink versenden
GET    /api/v1/tenant/users                          Alle Users im Tenant
GET    /api/v1/tenant/users/{id}                     User Detail (inkl. Rollen)
PUT    /api/v1/tenant/users/{id}                     User aktualisieren
         -> Keycloak: User Profile updaten
DELETE /api/v1/tenant/users/{id}                     User aus Tenant entfernen
         -> Keycloak: User deaktivieren/loeschen im Realm
         -> OpenFGA: Alle Relationships des Users entfernen
PUT    /api/v1/tenant/users/{id}/roles               Rollen zuweisen
         -> OpenFGA: role#assignee Relationships schreiben
```

### Rollen (Tenant Admin)

```
POST   /api/v1/tenant/roles                          Rolle erstellen
         -> OpenFGA: Role-Typ + initiale Relationships
GET    /api/v1/tenant/roles                          Alle Rollen
GET    /api/v1/tenant/roles/{id}                     Rolle Detail (inkl. Permissions)
PUT    /api/v1/tenant/roles/{id}                     Rolle aktualisieren
DELETE /api/v1/tenant/roles/{id}                     Rolle loeschen
         -> OpenFGA: Alle Relationships der Rolle entfernen
PUT    /api/v1/tenant/roles/{id}/permissions          Permissions der Rolle setzen
         -> OpenFGA: Relationships schreiben/entfernen
```

### Permissions (Tenant Admin)

```
GET    /api/v1/tenant/permissions/resources           Verfuegbare Ressourcen
         (Content Definitions, Fields, Tenant Config, User Mgmt, Role Mgmt)
GET    /api/v1/tenant/permissions/check               Permission pruefen
         -> OpenFGA: Check
PUT    /api/v1/tenant/permissions                     Permissions setzen (Bulk)
         -> OpenFGA: Batch Write
GET    /api/v1/tenant/permissions/user/{userId}       Alle Permissions eines Users
         -> OpenFGA: ListObjects
```

### Platform Admin

```
GET    /api/v1/admin/users                           Alle Users (plattformweit)
POST   /api/v1/admin/users/{id}/tenants              User zu Tenant hinzufuegen
         -> Keycloak: User im Tenant Realm anlegen/verknuepfen
         -> OpenFGA: tenant#member Relationship
DELETE /api/v1/admin/users/{id}/tenants/{tenantId}   User aus Tenant entfernen
```

## Tenant Provisioning (erweitert)

Beim Erstellen eines neuen Tenants passiert automatisch:

```
1. Tenant in DB erstellen (Tenant Domain)
2. S3 Bucket erstellen - tenant-{slug} (Shared Kernel)
3. Keycloak Realm erstellen + Theme konfigurieren
4. OpenFGA Store erstellen + Authorization Model anlegen
5. Einladungs-Email an Tenant Admin senden
6. Tenant Admin akzeptiert -> User in Keycloak Realm erstellt
7. OpenFGA: tenant:X#admin -> user:tenant-admin-id
```

## Datenbank-Schema

Minimales lokales Schema - Keycloak und OpenFGA sind die Primary Stores.
Lokale Tabelle nur fuer Query-Performance und um nicht fuer jede User-Liste
Keycloak abfragen zu muessen.

```sql
-- Lokale User-Referenz (Sync aus Keycloak)
CREATE TABLE tenant_users (
    id              UUID PRIMARY KEY,           -- gleiche ID wie in Keycloak
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    status          VARCHAR(50) NOT NULL DEFAULT 'INVITED',  -- INVITED, ACTIVE, DEACTIVATED
    invited_by      UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, email)
);

-- Lokale Rollen-Referenz (Sync aus OpenFGA)
CREATE TABLE tenant_roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, name)
);

-- User-Role Zuordnung (Sync aus OpenFGA)
CREATE TABLE tenant_user_roles (
    user_id         UUID NOT NULL REFERENCES tenant_users(id),
    role_id         UUID NOT NULL REFERENCES tenant_roles(id),
    tenant_id       UUID NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- RLS auf allen Tabellen
ALTER TABLE tenant_users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_users
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

ALTER TABLE tenant_roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_roles
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

ALTER TABLE tenant_user_roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_user_roles
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

## Libraries & Technologie-Entscheidungen

| Bereich | Technologie | Begruendung |
|---|---|---|
| DB Layer | Spring Data JPA | Statisches Schema (lokale User/Rollen Referenz) |
| Auth Provider | Keycloak | Authentifizierung, User Lifecycle, JWT |
| Authorization | OpenFGA | ReBAC, Rollen, Permissions - Single Source of Truth |
| Keycloak Integration | Keycloak Admin Client (Java) | Realm/User Verwaltung via API |
| OpenFGA Integration | OpenFGA Java SDK | Permission Checks + Management |
| OpenAPI | openapi-generator-maven-plugin | Contract-First |
| Mapping | MapStruct | DTO <-> Domain <-> JPA Entity |
| Testing | JUnit 5 + Testcontainers + AssertJ | Testcontainers fuer Keycloak + OpenFGA + PostgreSQL |

## Package-Struktur (Spring Modulith)

```
identity/
  api/
    UserController.java                   (Tenant Admin: User Management)
    RoleController.java                   (Tenant Admin: Rollen)
    PermissionController.java             (Tenant Admin: Permissions)
    PlatformUserController.java           (Platform Admin)
  service/
    UserService.java                      (User CRUD, orchestriert Keycloak + OpenFGA)
    RoleService.java                      (Rollen CRUD, orchestriert DB + OpenFGA)
    PermissionService.java                (Permission Checks + Management via OpenFGA)
    KeycloakService.java                  (Keycloak Admin API Wrapper)
    OpenFgaService.java                   (OpenFGA API Wrapper)
    TenantRealmProvisioningService.java   (Realm + Store erstellen bei Tenant-Setup)
  mapper/
    UserMapper.java                       (MapStruct)
    RoleMapper.java                       (MapStruct)
  model/
    TenantUser.java                       (Domain Model)
    TenantRole.java                       (Domain Model)
    Permission.java                       (Value Object)
    UserStatus.java                       (Enum: INVITED, ACTIVE, DEACTIVATED)
  repository/
    TenantUserRepository.java             (Spring Data JPA)
    TenantRoleRepository.java             (Spring Data JPA)
    entity/
      TenantUserEntity.java               (JPA Entity)
      TenantRoleEntity.java               (JPA Entity)
      TenantUserRoleEntity.java           (JPA Entity)
  security/
    JwtAuthenticationFilter.java          (JWT Validierung)
    PermissionCheckInterceptor.java       (Pre-Request Permission Check)
    SecurityConfiguration.java            (Spring Security Config)
```

## Implementierungs-Tasks

### Phase 1: Grundlagen

- [ ] **Task 1.1:** Datenbank-Schema erstellen (Flyway Migration)
- [ ] **Task 1.2:** OpenAPI Spec schreiben (User, Rollen, Permissions Endpunkte)
- [ ] **Task 1.3:** Keycloak Dev-Setup (Docker Compose)
- [ ] **Task 1.4:** OpenFGA Dev-Setup (Docker Compose)

### Phase 2: Keycloak Integration

- [ ] **Task 2.1:** KeycloakService (Realm erstellen, User anlegen/einladen, Theme Setup)
- [ ] **Task 2.2:** JwtAuthenticationFilter (JWT Validierung, User ID + Realm Extraktion)
- [ ] **Task 2.3:** SecurityConfiguration (Spring Security + Keycloak)
- [ ] **Task 2.4:** TenantRealmProvisioningService (automatisch bei Tenant-Erstellung)

### Phase 3: OpenFGA Integration

- [ ] **Task 3.1:** OpenFGA Authorization Model definieren und deployen
- [ ] **Task 3.2:** OpenFgaService (Check, Write, Delete, ListObjects, BatchCheck)
- [ ] **Task 3.3:** PermissionService (Business-Level Permission Checks)
- [ ] **Task 3.4:** PermissionCheckInterceptor (Pre-Request Checks)
- [ ] **Task 3.5:** Permission Caching Strategie

### Phase 4: User & Rollen Management

- [ ] **Task 4.1:** Domain Model + JPA Entities + MapStruct Mapper
- [ ] **Task 4.2:** UserService (CRUD, orchestriert Keycloak + OpenFGA + lokale DB)
- [ ] **Task 4.3:** RoleService (CRUD, orchestriert OpenFGA + lokale DB)
- [ ] **Task 4.4:** User Einladungs-Flow (Email + Keycloak Password Setup)
- [ ] **Task 4.5:** Controller (implementieren generierte Interfaces)

### Phase 5: Platform Admin

- [ ] **Task 5.1:** PlatformUserController + Service
- [ ] **Task 5.2:** Platform Realm Setup

### Phase 6: Testing

- [ ] **Task 6.1:** Unit Tests (Service Layer, Mocked Keycloak/OpenFGA)
- [ ] **Task 6.2:** Integration Tests (Testcontainers: Keycloak + OpenFGA + PostgreSQL)

### Spaeter

- [ ] MFA Konfiguration pro Tenant
- [ ] Social Login pro Tenant (Google, GitHub etc.)
- [ ] Audit Log (wer hat wann welche Permission geaendert)
- [ ] API Keys fuer Service-to-Service Kommunikation
