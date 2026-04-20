# Phase 3a: Identity & Access Infrastruktur — Design Spec

## Scope

OpenFGA + Keycloak Admin Integration als Basis fuer User Management (Phase 3b).
Kein User/Role CRUD, kein Invite-Flow UI — nur die Infrastruktur-Services, Async Provisioning und Permission-Check-Mechanismus.

---

## Entscheidungen

| # | Entscheidung | Begruendung |
|---|---|---|
| 1 | Minimales OpenFGA Model (user, platform, tenant, role, user_management, role_management) | Modell waechst mit dem Code, weniger Komplexitaet zum Testen |
| 2 | Ein OpenFGA Store pro Tenant | Tenant-Isolation auf OpenFGA-Ebene, kein Cross-Tenant Risiko |
| 3 | Store-ID + Realm-Name als Felder auf `tenants` Tabelle | Pragmatisch, direkter Zugriff ohne Join |
| 4 | Keycloak Service Account (Client Credentials) | Granular berechtigt, Secret aus Environment |
| 5 | Ein Client pro Tenant-Realm (`tenant-ui`, PKCE, public) | Frontend NextAuth muss sich gegen jeweiligen Realm authentifizieren |
| 6 | Platform SMTP als Default fuer neue Tenants | Invite-Flow funktioniert sofort, Tenant kann spaeter eigenen SMTP konfigurieren |
| 7 | Keycloak Forgot Password (Built-in) mit Tenant-SMTP | Robust, sicher, kein Custom Code fuer Reset-Flow |
| 8 | Keine Self-Registration, nur Invite | User werden ueber Platform API angelegt, Keycloak Required Action fuer Passwort |
| 9 | Branding auf Keycloak Login Page via Realm-Attributes | Custom Theme liest dynamisch aus Realm-Attributes, kommt zusammen mit Frontend |
| 10 | Annotation-basierte Permission Checks (`@RequiresPermission`) | Deklarativ fuer Endpoint-Level, programmatisch im Service fuer List-Filtering |
| 11 | Request-scoped Cache fuer OpenFGA Checks | Kein Stale-Risiko, keine redundanten Calls innerhalb eines Requests |
| 12 | Caffeine Cache fuer Store-ID/Realm-Name Lookup | Lokaler In-Memory Cache, TTL 5 Min, Spring Cache Abstraction |
| 13 | Async Provisioning mit Spring Modulith Event Publication Registry | Persistierte Events, automatischer Retry mit Backoff, crash-safe |
| 14 | Kein automatischer Rollback bei Provisioning-Fehler | Retry nur fehlgeschlagenen Step, einfacher als Compensation Logic |
| 15 | Slug automatisch aus Tenant-Name generiert | User kann Slug nicht aendern, Uniqueness ueber Tenant-Name |
| 16 | Unique Constraint auf Tenant-Name | Verhindert Duplikate, Async-Validierung im Frontend beim Submit |
| 17 | Subdomain bestimmt Keycloak Realm | `{slug}.localhost` → Realm `tenant-{slug}`, `platform.localhost` → Platform Realm |
| 18 | Polling fuer Provisioning Status (3s Intervall) | Einfach, Provisioning ist seltener Admin-Vorgang, 3s reicht aus, Notification Service spaeter |
| 19 | Identity Modul von Anfang an | Services gehoeren fachlich zusammen, kein spaeteres Verschieben |
| 20 | Events fuer async Kommunikation, Published Interfaces fuer sync | Spring Modulith konform, spaeter zu Microservices extrahierbar |
| 21 | Typisierte Request/Response Objekte fuer alle Service-Interfaces | Records/POJOs statt primitive Parameter, erweiterbar ohne Breaking Changes |
| 22 | Profilbild in S3 (nicht Keycloak) | Feature der Platform, nicht von Keycloak, URL in lokaler DB |
| 23 | Initialer Tenant Admin bei Erstellung (Name + Email + Vorname + Nachname) | Ein-Schritt-Onboarding fuer Platform Admin |
| 24 | Platform Admin Cross-Subdomain Session (kein Re-Login) | Platform Admin behält Platform-Session auf Tenant-Subdomains, Backend akzeptiert PLATFORM_ADMIN ohnehin |
| 25 | Progress-Bar + Statustext im Provisioning UI | A11y: nicht nur Farbe/Icons, Screen-Reader-tauglich, schnelles Gesamtbild |
| 26 | Formular-Sektionen im Create Tenant Dialog | UX: Trennung von Tenant-Info und Admin-Info reduziert kognitive Last |
| 27 | Workspace-Not-Found Fehlerseite | UX: Dedizierte Seite statt kryptischem Keycloak-Fehler bei nicht-existenten Subdomains |

---

## OpenFGA Integration

### Authorization Model (minimal, Phase 3a)

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

Wird bei Tenant-Provisioning in den neuen OpenFGA Store deployed.

### OpenFgaService (identity Modul, published Interface)

```java
// Request/Response Records
record CreateStoreRequest(String tenantSlug) {}
record CreateStoreResponse(String storeId) {}

// OpenFga-prefixed Records um Verwechslung mit PermissionService Records zu vermeiden
record OpenFgaCheckRequest(String storeId, UUID userId, String action, String resourceType, UUID resourceId) {}
record OpenFgaWriteRequest(String storeId, List<RelationshipTuple> tuples) {}
record OpenFgaDeleteRequest(String storeId, List<RelationshipTuple> tuples) {}
record OpenFgaListObjectsRequest(String storeId, UUID userId, String action, String resourceType) {}
record OpenFgaBatchCheckRequest(String storeId, List<OpenFgaCheckRequest> checks) {}

record RelationshipTuple(String user, String relation, String object) {}
```

Methoden:
- `createStore(CreateStoreRequest)` → `CreateStoreResponse`
- `check(OpenFgaCheckRequest)` → `boolean`
- `write(OpenFgaWriteRequest)` → `void`
- `delete(OpenFgaDeleteRequest)` → `void`
- `listObjects(OpenFgaListObjectsRequest)` → `List<String>`
- `batchCheck(OpenFgaBatchCheckRequest)` → `Map<OpenFgaCheckRequest, Boolean>`

Request-scoped Cache auf `check()` Ergebnissen.

### Store-ID Lookup

Aus `tenant.openfga_store_id` Feld. Caffeine Cache mit `@Cacheable("tenantInfrastructure")`, TTL 5 Minuten, `@CacheEvict` bei Tenant-Update. Max Size 1000. Bei Cache-Miss (z.B. frisch erstellter Tenant) wird ein normaler DB-Lookup ausgefuehrt.

---

## Keycloak Admin Integration

### KeycloakAdminService (identity Modul, published Interface)

```java
// Request/Response Records
// SmtpConfig: Neuer Record im identity Modul (nicht der bestehende EmailConfig aus Tenant-Config).
// EmailConfig enthaelt Tenant-facing Felder (fromAddress, fromName), SmtpConfig enthaelt nur
// die technischen SMTP-Verbindungsdaten die Keycloak braucht (host, port, user, password, starttls).
record SmtpConfig(String host, int port, String username, String password, boolean starttls) {}

record CreateRealmRequest(String tenantSlug, SmtpConfig platformSmtpConfig) {}
record CreateRealmResponse(String realmName) {}

record CreateKeycloakUserRequest(String realmName, String email, String firstName, String lastName) {}
record CreateKeycloakUserResponse(UUID keycloakUserId) {}

record SendInviteEmailRequest(String realmName, UUID userId) {}

record UpdateRealmSmtpRequest(String realmName, SmtpConfig smtpConfig) {}
record UpdateRealmBrandingRequest(String realmName, Map<String, String> brandingAttributes) {}

record DeleteKeycloakUserRequest(String realmName, UUID userId) {}
record UpdateKeycloakUserRequest(String realmName, UUID userId, String email, String firstName, String lastName) {}
```

Methoden:
- `createRealm(CreateRealmRequest)` → `CreateRealmResponse`
  - Erstellt Realm mit Client `tenant-ui` (PKCE, public)
  - SMTP aus Platform-Default
  - Login Flow aktiv, Registration deaktiviert
  - Forgot Password aktiviert
- `createUser(CreateKeycloakUserRequest)` → `CreateKeycloakUserResponse`
  - User mit Required Action `UPDATE_PASSWORD`
- `sendInviteEmail(SendInviteEmailRequest)` → `void`
  - Triggert Keycloak "Execute Actions" Email
- `updateRealmSmtp(UpdateRealmSmtpRequest)` → `void`
- `updateRealmBranding(UpdateRealmBrandingRequest)` → `void`
- `deleteUser(DeleteKeycloakUserRequest)` → `void`
- `updateUser(UpdateKeycloakUserRequest)` → `void`

### Authentifizierung

Service Account im Platform Realm mit Client Credentials Grant. Konfiguration:
- `keycloak.admin.client-id` (Environment Variable)
- `keycloak.admin.client-secret` (Environment Variable)
- `keycloak.admin.server-url` (aus bestehender Config)

### Dependencies (Gradle)

```kotlin
// Keycloak Admin Client
implementation("org.keycloak:keycloak-admin-client:26.0.0")

// OpenFGA Java SDK (BatchCheck ab v0.7.0)
implementation("dev.openfga:openfga-sdk:0.7.+")

// Spring Modulith Event Publication Registry (persistierte Events, Retry)
implementation("org.springframework.modulith:spring-modulith-starter-jdbc")

// Caffeine Cache
implementation("com.github.ben-manes.caffeine:caffeine")
implementation("org.springframework.boot:spring-boot-starter-cache")
```

OpenFGA Server Version in Docker Compose muss >= 1.8.0 sein (BatchCheck Support).
Aktuelle Version im `docker-compose.dev.yml` pruefen und ggf. aktualisieren.

---

## DB-Schema Aenderungen

### Flyway Migration

```sql
-- Neue Felder auf tenant Tabelle (Singular, siehe docs/08-db-schema-regeln.md)
ALTER TABLE tenant ADD COLUMN openfga_store_id VARCHAR(255);
ALTER TABLE tenant ADD COLUMN keycloak_realm_name VARCHAR(255);
ALTER TABLE tenant ADD COLUMN provisioning_status JSONB;

-- Unique Constraint auf Tenant-Name
ALTER TABLE tenant ADD CONSTRAINT uq_tenant_name UNIQUE (name);
```

Bestehender Unique-Index auf `slug` bleibt erhalten. Slug wird automatisch aus Name generiert —
bei einzigartigem Namen ist auch der Slug einzigartig. Der Suffix-Fallback greift als Safety Net
bei Namens-Kollisionen nach Normalisierung (z.B. `"Acme"` und `"ACME!"` → beide `acme`).

### Neuer TenantStatus

`PROVISIONING` wird zu den bestehenden Werten hinzugefuegt (ACTIVE, INACTIVE, SUSPENDED).

### Provisioning Status Struktur (JSONB)

```json
{
  "steps": [
    {"step": "S3_BUCKET", "status": "COMPLETED", "completedAt": "2026-03-12T10:00:00Z"},
    {"step": "KEYCLOAK_REALM", "status": "IN_PROGRESS", "error": null},
    {"step": "OPENFGA_STORE", "status": "PENDING"},
    {"step": "INITIAL_ADMIN", "status": "PENDING"}
  ]
}
```

Step-Status-Werte: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`
Bei `FAILED`: `error` Feld mit Fehlermeldung.

### Slug-Generierung

- Automatisch aus Tenant-Name: `"Acme Corporation"` → `acme-corporation`
- Lowercase, Sonderzeichen entfernen, Leerzeichen zu Bindestrichen, Umlaute normalisieren
- Duplikat-Suffix als Safety Net (`acme-corporation-2`)
- Uniqueness primaer ueber Tenant-Name Unique Constraint

### Tenant Create Request (geaendert)

Bisherig: `name`, `slug`
Neu: `name`, `adminEmail`, `adminFirstName`, `adminLastName`

---

## Async Provisioning

### Migration bestehender TenantProvisioningService

Der bestehende `TenantProvisioningService` (synchron, erstellt nur S3-Bucket) wird umgebaut:
- `TenantService.create()` erstellt den Tenant mit Status `PROVISIONING` (statt direkt `TenantProvisioningService.provision()` aufzurufen)
- `TenantService.create()` published ein `TenantCreatedEvent`
- Der bisherige S3-Bucket-Code wandert als Step 1 in den neuen `TenantProvisioningListener` im identity Modul
- `TenantAdminController.createTenant()` gibt sofort den Tenant mit Status `PROVISIONING` zurueck (201 + Location)

### Event Flow

1. `TenantService.create()` → Tenant in DB mit Status `PROVISIONING` + initiale `provisioning_status` JSON
2. Published `TenantCreatedEvent(tenantId, slug, adminEmail, adminFirstName, adminLastName)`
3. `TenantProvisioningListener` im identity Modul reagiert (`@ApplicationModuleListener`)
4. Listener fuehrt Steps sequenziell aus, updated `provisioning_status` JSON nach jedem Step:
   - Step 1: S3 Bucket erstellen
   - Step 2: Keycloak Realm + Client + SMTP
   - Step 3: OpenFGA Store + Authorization Model
   - Step 4: Initialer Admin User (Keycloak User + OpenFGA `tenant#admin` Relationship + Invite Email)
5. Alle Steps erfolgreich → Status `ACTIVE`
6. Ein Step failed → Status bleibt `PROVISIONING`, Step bekommt `FAILED` + Error Message

### Retry-Logik

- Spring Modulith Event Publication Registry persistiert das Event
- Automatischer Retry mit Exponential Backoff (3 Versuche, 5s/15s/45s)
- Nach Max-Retries: Step bleibt `FAILED`
- Manueller Retry: `POST /api/v1/admin/tenants/{id}/retry-provisioning` — dieser Endpoint wird in Phase 3a als Teil der Provisioning-Infrastruktur implementiert (Ausnahme von "keine Controller in Phase 3a", da er zum Provisioning gehoert, nicht zu User Management)

### Compensation

Kein automatischer Rollback. Retry versucht nur den fehlgeschlagenen Step erneut.

---

## Permission Check Infrastruktur

### @RequiresPermission Annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String action();
    String resource();
    String resourceId() default "";  // SpEL: "#id", leer = Type-Level Check
}
```

### PermissionCheckAspect (AOP)

- Greift vor Controller-Methoden mit `@RequiresPermission`
- Holt User-ID + Tenant-ID aus SecurityContext/TenantContext
- Holt OpenFGA Store-ID ueber Tenant-ID (Caffeine cached)
- Evaluiert SpEL fuer `resourceId` gegen Methoden-Parameter
- Ruft `OpenFgaService.check()` auf
- Wirft `AccessDeniedException` (403) bei fehlender Berechtigung
- Platform Admin (`PLATFORM_ADMIN` Rolle) bypassed alle Checks

### PermissionService (published Interface)

```java
record CheckPermissionRequest(UUID userId, String action, String resourceType, UUID resourceId) {}
record GrantPermissionRequest(UUID userId, String relation, String resourceType, UUID resourceId) {}
record RevokePermissionRequest(UUID userId, String relation, String resourceType, UUID resourceId) {}
record ListPermittedObjectsRequest(UUID userId, String action, String resourceType) {}

boolean check(CheckPermissionRequest request);
void grant(GrantPermissionRequest request);
void revoke(RevokePermissionRequest request);
List<UUID> listObjects(ListPermittedObjectsRequest request);
```

Store-ID Aufloesung intern ueber aktuellen Tenant aus TenantContext.

---

## Package-Struktur

```
identity/
  api/
    TenantProvisioningController.java  (nur Retry-Endpoint)
  service/
    OpenFgaService.java
    KeycloakAdminService.java
    PermissionService.java
    TenantProvisioningListener.java
  model/
    RelationshipTuple.java
    ProvisioningStep.java
    ProvisioningStepStatus.java
  security/
    RequiresPermission.java          (Annotation)
    PermissionCheckAspect.java       (AOP Aspect)
  config/
    OpenFgaConfiguration.java
    KeycloakAdminConfiguration.java
    CaffeineCacheConfiguration.java
```

---

## Frontend-Aenderungen

### Dynamische NextAuth Konfiguration + Platform Admin Cross-Subdomain

Die bestehende NextAuth-Konfiguration (`apps/platform/src/auth.ts`) ist aktuell auf den Platform Realm hartcodiert.
Aenderungen:

- **Keycloak Provider wird dynamisch konfiguriert:** Issuer-URL basierend auf Hostname ableiten
  - `platform.localhost` → `http://auth.localhost/realms/webcontify-platform`
  - `{slug}.localhost` → `http://auth.localhost/realms/tenant-{slug}`
- **Platform Admin Cross-Subdomain Session:** Platform Admins behalten ihre Platform-Session auf allen Tenant-Subdomains. Kein erneutes Anmelden erforderlich — der Platform-JWT wird durchgereicht, das Backend akzeptiert PLATFORM_ADMIN auf allen Tenants. Nur regulaere Tenant-User authentifizieren sich gegen den jeweiligen Tenant-Realm.
- **Breaking Change fuer bestehende Auth:** Die statische `KEYCLOAK_ISSUER` Environment Variable wird durch dynamische Hostname-basierte Aufloesung ersetzt
- **Callback URLs in Keycloak:** Jeder Tenant-Realm Client braucht die korrekte Redirect URI (`https://{slug}.localhost/*`)
- **Token Refresh:** Bestehende Refresh-Logik bleibt, Issuer ist aber dynamisch
- **Middleware:** Muss Hostname parsen um Platform vs Tenant Context zu unterscheiden. Platform Admin mit gueltiger Session wird auf Tenant-Subdomains nicht zum Tenant-Realm Login weitergeleitet.
- **Workspace-Not-Found Page:** Dedizierte Fehlerseite fuer nicht-existente Tenant-Subdomains ("Dieser Workspace existiert nicht." + Link zur Platform)

### Tenant Create Dialog (angepasst)

- Slug-Feld entfernt
- **Zwei Formular-Sektionen:** "Tenant" (Name) getrennt durch Separator von "Initialer Administrator" (Email, Vorname, Nachname)
- Email-Feld mit `type="email"` fuer korrekte Mobile-Keyboard
- Uniqueness-Check auf Tenant-Name beim Submit, Inline-Fehler unter Name-Feld bei Duplikat
- Nach Erstellung: Toast "Tenant wird erstellt..." + Redirect zur Tenant-Detail-Seite

### Tenant Detail — Provisioning Status

Wenn Tenant Status `PROVISIONING`:
- **Progress-Bar** (shadcn Progress) + Text "X von 4 Schritten abgeschlossen" ueber der Checklist
- Checklist mit Steps (jeweils Icon + Label + **Statustext**):
  - Speicher (S3) → CheckCircle2/Loader2/Circle/XCircle + "Abgeschlossen"/"Wird eingerichtet..."/"Ausstehend"/"Fehlgeschlagen"
  - Authentifizierung (Keycloak) → gleich
  - Berechtigungssystem (OpenFGA) → gleich
  - Initialer Admin → gleich
- **Accessibility:** `aria-live="polite"` auf Step-Liste, Statustext zusaetzlich zu Icons (nicht nur Farbe)
- Polling alle **3 Sekunden** (ausreichend fuer seltenen Admin-Vorgang)
- Bei `FAILED` Step: Fehlermeldung + Retry-Button (Primary/default variant, NICHT destructive-rot). Button mit Loading-State, Fehlermeldung bleibt waehrend Retry sichtbar.
- **Fallback:** Wenn `provisioningStatus` null → alle Steps als PENDING anzeigen
- Container mit `min-h-[300px]` um Layout-Sprung beim Wechsel zu Detail-Ansicht zu vermeiden
- Bei `ACTIVE`: normale Detail-Ansicht

### Traefik Wildcard (Infrastruktur-Aenderung)

Eigener Task — nicht Teil der Frontend-Entwicklung:
- Wildcard-Rule `*.localhost` → Next.js Frontend mit niedrigerer Prioritaet
- Spezifische Rules (`api.localhost`, `auth.localhost`, `mail.localhost`, `s3.localhost`, `fga.localhost`) bleiben mit hoeherer Prioritaet
- Aenderung in `infra/docker-compose.dev.yml` (Traefik Labels)

---

## Testing-Strategie

### Backend Unit Tests (Mockito)

- `OpenFgaServiceTest` — Store erstellen, Check, Write, Delete, ListObjects
- `KeycloakAdminServiceTest` — Realm erstellen, User anlegen, SMTP Config
- `PermissionCheckAspectTest` — Annotation-Auswertung, SpEL Evaluation, Platform Admin Bypass
- `PermissionServiceTest` — Check/Grant/Revoke Delegation
- `SlugGeneratorTest` — Name-zu-Slug Konvertierung, Umlaute, Sonderzeichen, Duplikat-Suffix

### Backend Integration Tests (Testcontainers)

- `TenantProvisioningIntegrationTest` — Kompletter Flow mit echtem Keycloak + OpenFGA + PostgreSQL
- `PermissionCheckIntegrationTest` — Echter OpenFGA Container, Model deployed, Check/Write/ListObjects
- `TenantCreationIntegrationTest` — Neues Request-Format, Slug-Generierung, Uniqueness-Check

### Frontend Tests (Vitest)

- `useAuth` Hook — dynamische Realm-Aufloesung basierend auf Hostname
- Provisioning Status Komponente — Step-Anzeige, Polling, Retry-Button

### Kein E2E in Phase 3a

Kommt in Brainstorm B mit vollstaendigem User Management Flow.

---

## Abgrenzung: Was kommt in Brainstorm B

- User Management Endpoints (CRUD, Invite)
- Role Management Endpoints (CRUD, Permission-Matrix)
- Frontend: User-Liste, User-Detail, Rollen-Verwaltung
- Keycloak Custom Theme mit Tenant-Branding
- Profilbild Upload
- E2E Tests fuer Auth + User Management Flow
