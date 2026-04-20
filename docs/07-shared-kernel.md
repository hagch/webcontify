# Shared Kernel - Detailplanung

## Ueberblick

Der Shared Kernel enthaelt alle domainuebergreifenden Bausteine: Value Objects, Base Entities,
Infrastruktur-Services und Cross-Cutting Concerns. Er wird von allen Modulen referenziert,
enthaelt aber keine eigene Geschaeftslogik.

**Modul:** `shared-kernel`
**Package:** `io.webcontify.shared`

## Value Objects

### TenantInformation

Traegt den Tenant-Kontext durch die gesamte Applikation. Wird im SecurityContext gehalten
und ist ueber `TenantContext.current()` zugreifbar.

```
TenantInformation (Record)
  tenantId: UUID (nullable - null bei Platform Admin)
  platformAdmin: boolean

Validierung:
  - tenantId ist Pflicht wenn platformAdmin = false
  - tenantId ist null wenn platformAdmin = true
```

**Aufloesung (Filter, vor Controller):**
1. JWT vorhanden mit `PLATFORM_ADMIN` Rolle -> `TenantInformation(null, true)`
2. JWT vorhanden mit `tenant_id` Claim -> Subdomain pruefen:
   - Tenant aus Subdomain existiert nicht -> 404 Not Found
   - JWT-Tenant matcht Subdomain -> `TenantInformation(tenantId, false)`
   - JWT-Tenant matcht nicht -> Redirect auf korrekte Subdomain
3. Kein JWT (unauthentifiziert) -> Tenant aus Subdomain aufloesen:
   - Subdomain existiert in DB -> `TenantInformation(tenantId, false)`
   - Subdomain existiert nicht -> 404 Not Found

**Zugriff im Service Layer:**

```
TenantContext (Utility)
  static current(): TenantInformation
    -> liest aus SecurityContextHolder
    -> wirft IllegalStateException wenn kein Tenant im Context

TenantAwareAuthentication (Custom Authentication Token)
  getTenantInformation(): TenantInformation
```

### AuditInfo

Automatisch befuellte Audit-Felder fuer alle Entities.

```
AuditInfo (Record, @Embedded)
  createdAt: Instant
  createdBy: UUID
  updatedAt: Instant
  updatedBy: UUID
  deletedAt: Instant (nullable)
  deletedBy: UUID (nullable)
```

- `createdBy` / `updatedBy` / `deletedBy` sind UUIDs (Keycloak User-ID), **kein Foreign Key** auf User-Tabelle
- User kann geloescht werden ohne Audit-Eintraege zu beeinflussen
- Anzeige: best-effort Username-Aufloesung (existiert User noch -> Name, sonst Fallback-Text)
- System-Aktionen verwenden `SystemUser.ID`
- `deletedAt` / `deletedBy` werden **nicht** vom AuditListener gesetzt, sondern explizit im Service
- Soft Delete Filterung passiert im Application Layer (nicht in RLS Policy)

**SystemUser:**

```
SystemUser
  ID: UUID = "00000000-0000-0000-0000-000000000000"
```

### Auditable (Interface)

```
Auditable
  getAuditInfo(): AuditInfo
  setAuditInfo(AuditInfo): void
```

## Base Entities

Zweistufige Vererbungshierarchie mit UUIDv7 (zeitbasiert, sortierbar, bessere Index-Performance).
ID-Generierung: `com.fasterxml.uuid.Generators.timeBasedEpochGenerator()`.

### AuditableBaseEntity

Basis fuer Entities **ohne** Tenant-Zuordnung (z.B. Tenant-Entity selbst).

```
@MappedSuperclass
@EntityListeners(AuditListener.class)
AuditableBaseEntity implements Auditable
  id: UUID (UUIDv7, applikationsseitig generiert)
  auditInfo: AuditInfo (@Embedded)
```

### TenantAwareBaseEntity

Basis fuer alle Entities **mit** Tenant-Zuordnung (Standard fuer fast alle Entities).

```
@MappedSuperclass
TenantAwareBaseEntity extends AuditableBaseEntity
  tenantId: UUID (nicht nullable, wird durch RLS zusaetzlich abgesichert)
```

**Verwendung:**
- Tenant-Entity -> erbt von `AuditableBaseEntity`
- Alle anderen Entities -> erben von `TenantAwareBaseEntity`

## AuditListener

Zentraler JPA EntityListener fuer automatische Audit-Befuellung.

```
AuditListener
  @PrePersist onPrePersist(Auditable entity)
    -> setzt createdAt, createdBy, updatedAt, updatedBy
    -> User-ID aus TenantContext oder SystemUser.ID

  @PreUpdate onPreUpdate(Auditable entity)
    -> aktualisiert updatedAt, updatedBy
    -> createdAt und createdBy bleiben unveraendert
```

- Fuer **dynamische Tabellen (jOOQ/EAV):** Gleiche Logik als Utility-Methode im Service,
  da EAV-Tabellen kein JPA nutzen
- Spaeter erweiterbar fuer **History/Changelog** (z.B. `@PostUpdate`, `@PostPersist`)

## RLS DataSource Decorator

Proxy um die DataSource der `SET LOCAL app.tenant_id` auf jeder Connection setzt,
damit PostgreSQL Row-Level Security Policies greifen. Funktioniert fuer JPA und jOOQ.

**Ablauf:**
1. Request kommt rein -> Filter extrahiert `TenantInformation` in SecurityContext
2. Connection wird aus dem Pool geholt
3. Decorator prueft `TenantInformation`:
   - `platformAdmin = true` -> kein `SET LOCAL`, RLS wird umgangen
   - `platformAdmin = false` -> `SET LOCAL app.tenant_id = '<uuid>'`
4. Query laeuft mit (oder ohne) RLS
5. Connection geht zurueck in den Pool -> `SET LOCAL` gilt nur innerhalb der Transaktion

**RLS Policy Pattern:**

```sql
CREATE POLICY tenant_isolation ON <table>
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );
```

- `current_setting(..., true)` gibt NULL zurueck statt Error wenn Variable nicht gesetzt
- `IS NULL` -> Platform Admin (kein Tenant gesetzt, sieht alles)

## Email Service

Zentraler E-Mail-Versand ueber Spring Mail mit Thymeleaf-Templates und Tenant-Branding.

```
EmailService (Interface)
  send(EmailMessage): void

EmailMessage (Record)
  to: String
  subject: String
  templateName: String
  variables: Map<String, Object>
```

**Ablauf:**
1. Template laden (z.B. `invite.html`)
2. Tenant-Branding aus DB/Cache laden (Logo-URL, Farben, Absender-Mail)
3. Variablen + Branding-Variablen in Template rendern (Thymeleaf)
4. Versenden mit Tenant-spezifischem Absender (`From`-Adresse)

**Tenant-Konfiguration:**
- Jeder Tenant hat eine konfigurierte Absender-Adresse (z.B. `noreply@acme.com`)
- Branding (Logo, Primary Color) kommt aus der Tenant-Config (fuer UI-Theming sowieso vorhanden)
- **Phase 1:** Zentraler SMTP-Server, unterschiedliche `From`-Adressen pro Tenant
- **Phase 2:** Optionaler eigener SMTP-Server pro Tenant (Credentials verschluesselt gespeichert)

**Lokal:** Mailpit (im Docker Compose)

## File Storage Service

S3-kompatibler File Storage mit Tenant-Isolation (ein Bucket pro Tenant).

```
FileStorageService (Interface)
  uploadPublicAsset(filename, content, contentType): String  -> gibt direkte URL zurueck
  uploadPrivateAsset(filename, content, contentType): String -> gibt internen Pfad zurueck
  getPresignedUrl(path, expiry): String                      -> Pre-signed URL fuer private Assets
  deleteAsset(path): void
```

**Bucket-Struktur:**

```
tenant-<tenantId>/
  config/       <- Public Assets (Logo, Favicon, Theming)
  files/        <- Private Assets (nur via Pre-signed URL)
```

- `uploadPublicAsset` legt unter `config/` ab
- `uploadPrivateAsset` legt unter `files/` ab
- Bucket-Name wird intern aus `TenantContext.current()` aufgeloest
- Bucket wird automatisch beim Tenant-Provisioning erstellt

**Lokal:** MinIO (im Docker Compose)
**Prod:** S3-kompatibler Service (Garage oder AWS S3)

## Encryption Service

Application-Level Encryption fuer sensitive statische Entity-Felder (z.B. SMTP-Credentials).

```
EncryptionService (Interface)
  encrypt(value: String): String
  decrypt(encryptedValue: String): String
```

**Status:** Interface definiert, Implementierung kommt spaeter (TODO).
Geplant: AES-256-GCM, Key Management noch offen.

## Error Format

Basiert auf **RFC 7807 Problem Detail** (Spring Boot 3 `ProblemDetail`), erweitert um
maschinenlesbaren `code` fuer Dokumentationsverknuepfung.

### Standard-Response

```json
{
    "type": "https://webcontify.io/errors/tenant-not-found",
    "title": "Tenant Not Found",
    "status": 404,
    "code": "TENANT_NOT_FOUND",
    "detail": "Tenant with subdomain 'acme' does not exist.",
    "instance": "/api/v1/tenants/acme"
}
```

### Validation-Response

```json
{
    "type": "https://webcontify.io/errors/validation-error",
    "title": "Validation Error",
    "status": 400,
    "code": "VALIDATION_ERROR",
    "errors": [
        { "field": "name", "message": "validation.not_blank" },
        { "field": "email", "message": "validation.invalid_email" }
    ]
}
```

- `errors[].message` ist ein **i18n Key** - Frontend uebersetzt
- Backend bleibt sprachunabhaengig
- Whitelabel-Tenants koennen eigene Texte/Sprachen im Frontend pflegen

### Exception Hierarchy

```
WebcontifyException (abstract, extends RuntimeException)
  code: String
  status: int
  |
  +-- NotFoundException (404)
  +-- ValidationException (400)
  |     errors: List<FieldError>
  +-- ConflictException (409)
  +-- ForbiddenException (403)

FieldError (Record)
  field: String
  message: String (i18n Key)
```

### Globaler ExceptionHandler

`@RestControllerAdvice` der alle Exceptions faengt:
- `WebcontifyException` -> mappt auf ProblemDetail + code + ggf. errors
- `Exception` (Catch-All) -> loggt vollen Stacktrace, gibt generische Response zurueck:

```json
{
    "type": "https://webcontify.io/errors/internal-error",
    "title": "Internal Server Error",
    "status": 500,
    "code": "INTERNAL_ERROR"
}
```

Keine internen Details werden an den Client geleakt.
