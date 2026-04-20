# Tenant Domain - Detailplanung

## Ueberblick

Die Tenant Domain verwaltet die Multi-Tenancy der Plattform. Jeder Tenant ist ein isolierter
Mandant mit eigener Konfiguration (Branding, Domain, E-Mail), eigenem File Storage Bucket
und eigenen Nutzern. Die Plattform ist eine Whitelabel-Loesung.

**Bounded Context:** Tenant
**Storage:** Spring Data JPA (statisches Schema)
**Keine Sub-Tenants in Phase 1** (spaeter nachruestbar via `parent_id`)

## Entitaeten

### Tenant (Aggregate Root)

```
Tenant
  id: UUID
  name: String
  slug: String (unique, URL-safe, wird fuer Bucket-Name etc. verwendet)
  status: TenantStatus (ACTIVE | INACTIVE | SUSPENDED)
  config: TenantConfig (JSONB)
  createdAt: Timestamp
  updatedAt: Timestamp
  createdBy: UUID
```

### TenantStatus (Enum)

```
ACTIVE      -- Tenant ist aktiv und nutzbar
INACTIVE    -- Tenant ist deaktiviert (z.B. noch nicht freigeschaltet)
SUSPENDED   -- Tenant ist gesperrt (z.B. Zahlungsverzug)
```

### TenantConfig (Value Object, JSONB)

```
TenantConfig
  branding: BrandingConfig
  email: EmailConfig
  domain: DomainConfig

BrandingConfig:
  logoStorageKey: String          (Referenz auf File in S3/Garage)
  faviconStorageKey: String       (Referenz auf File in S3/Garage)
  primaryColor: String            (#hex)
  secondaryColor: String          (#hex)

EmailConfig:
  smtpHost: String
  smtpPort: Integer
  smtpUser: String
  smtpPassword: String            (Application-Level verschluesselt)
  fromAddress: String
  fromName: String

DomainConfig:
  customDomain: String            (z.B. "app.kunde.com")
  sslStatus: SslStatus (PENDING | ACTIVE | FAILED)
```

## Tenant Handling (Spring Boot)

### Tenant Resolution

Tenant wird per Request aufgeloest ueber eine Chain (erste Quelle die matcht gewinnt):

1. **Custom Domain** - Host Header gegen `tenants.config->domain->customDomain` matchen
2. **JWT Claim** - `tenant_id` Claim aus Keycloak Token
3. **Header** - `X-Tenant-Id` Header (Fallback, z.B. Platform Admin wechselt Tenant)

```java
@Component
public class TenantResolutionFilter extends OncePerRequestFilter {
    private final TenantResolver tenantResolver;
    private final TenantContext tenantContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        UUID tenantId = tenantResolver.resolve(request);
        tenantContext.setTenantId(tenantId);
        filterChain.doFilter(request, response);
    }
}
```

### TenantContext (RequestScope)

```java
@Component
@RequestScope
public class TenantContext {
    private UUID tenantId;
    private boolean platformAdmin;
    // getters/setters
}
```

### RLS via DataSource Proxy

Ein DataSource-Wrapper setzt bei jeder Connection `SET LOCAL` fuer PostgreSQL RLS.
Funktioniert fuer JPA und jOOQ gleichermassen - eine Stelle, kein Auseinanderlaufen.

```java
public class TenantAwareDataSource implements DataSource {
    private final DataSource delegate;
    private final TenantContext tenantContext;

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = delegate.getConnection();
        try (var stmt = conn.createStatement()) {
            stmt.execute("SET LOCAL app.current_tenant_id = '"
                + tenantContext.getTenantId() + "'");
            stmt.execute("SET LOCAL app.is_platform_admin = '"
                + tenantContext.isPlatformAdmin() + "'");
        }
        return conn;
    }
}
```

**Wichtig:** `SET LOCAL` gilt nur fuer die aktuelle Transaktion und wird automatisch
zurueckgesetzt - kein Risiko von Tenant-Leaks zwischen Requests.

### Request Flow

```
HTTP Request
  -> TenantResolutionFilter (Domain -> JWT -> Header)
    -> TenantContext (RequestScope Bean)
      -> TenantAwareDataSource (SET LOCAL auf Connection)
        -> RLS greift automatisch auf allen Tabellen
          -> JPA / jOOQ Queries sind tenant-isoliert
```

## Tenant Provisioning

Beim Erstellen eines neuen Tenants passiert automatisch:

1. Tenant-Datensatz in DB erstellen
2. S3/Garage Bucket erstellen (`tenant-{slug}`)
3. RLS-Kontext fuer neuen Tenant verfuegbar machen
4. Default-Konfiguration setzen

Dies wird ueber einen `TenantProvisioningService` orchestriert. Wenn ein Schritt fehlschlaegt,
wird der Tenant auf `INACTIVE` gesetzt und der Fehler geloggt.

## API Endpunkte

### Platform Admin (tenant-uebergreifend)

```
POST   /api/v1/admin/tenants                     Tenant erstellen (inkl. Provisioning)
GET    /api/v1/admin/tenants                      Alle Tenants auflisten
GET    /api/v1/admin/tenants/{id}                 Tenant Detail
PUT    /api/v1/admin/tenants/{id}                 Tenant aktualisieren
PUT    /api/v1/admin/tenants/{id}/status           Tenant Status aendern (activate/suspend)
DELETE /api/v1/admin/tenants/{id}                 Tenant loeschen (Soft Delete)
```

### Tenant Admin (eigener Tenant)

```
GET    /api/v1/tenant/config                      Eigene Tenant-Konfiguration lesen
PUT    /api/v1/tenant/config/branding              Branding aktualisieren
PUT    /api/v1/tenant/config/email                 E-Mail Konfiguration aktualisieren
PUT    /api/v1/tenant/config/domain                Domain Konfiguration aktualisieren
POST   /api/v1/tenant/config/email/test            Test-E-Mail versenden
```

### File Upload (Tenant-scoped)

```
POST   /api/v1/tenant/files                       File hochladen (-> S3/Garage Bucket)
GET    /api/v1/tenant/files/{storageKey}           File abrufen (Pre-signed URL oder Proxy)
DELETE /api/v1/tenant/files/{storageKey}           File loeschen
```

## Datenbank-Schema

```sql
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    config          JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID
);

CREATE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_status ON tenants(status);
```

### Row-Level Security

```sql
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

-- Platform Admin: sieht alle Tenants
CREATE POLICY platform_admin_all ON tenants
    USING (current_setting('app.is_platform_admin', true)::BOOLEAN = true);

-- Tenant Admin/User: sieht nur eigenen Tenant
CREATE POLICY tenant_isolation ON tenants
    USING (id = current_setting('app.current_tenant_id')::UUID);
```

## Shared Services

### File Storage (shared-kernel)

**Zwei Zugriffslevel:**

| Typ | Prefix | Zugriff | Beispiel |
|---|---|---|---|
| Tenant Config | `config/` | Public (Bucket Policy: public-read) | Logo, Favicon |
| Geschuetzte Files | `files/` | Pre-signed URL (kurzlebig, z.B. 15 Min) | Workflow Attachments |

**Bucket-Struktur pro Tenant:**

```
tenant-{slug}/
  config/                    <- public-read (Bucket Policy)
    logo.png
    favicon.ico
  files/                     <- private, nur via Pre-signed URL
    {uuid}/filename.pdf
```

```java
public interface FileStorageService {
    // Public (config/)
    String uploadPublic(String bucketName, String key, InputStream data, String contentType);
    String getPublicUrl(String bucketName, String key);

    // Protected (files/)
    String uploadProtected(String bucketName, String key, InputStream data, String contentType);
    String generatePresignedUrl(String bucketName, String key, Duration expiration);

    void delete(String bucketName, String key);
    void createBucket(String bucketName);
}
```

Implementierung: `S3FileStorageService` (kompatibel mit Garage und AWS S3)

**Bucket-Namenskonvention:** `tenant-{slug}`

### E-Mail Service (shared-kernel)

```java
public interface EmailService {
    void send(UUID tenantId, EmailMessage message);
}
```

Implementierung: `TenantEmailService` liest SMTP-Config vom Tenant und versendet.
Wird spaeter von der Workflow Engine genutzt.

### Encryption Service (shared-kernel)

```java
public interface EncryptionService {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
```

Implementierung: `AesEncryptionService` - Application-Level AES Verschluesselung.
Encryption Key wird ueber Application Properties konfiguriert.

## Libraries & Technologie-Entscheidungen

| Bereich | Technologie | Begruendung |
|---|---|---|
| DB Layer | Spring Data JPA | Statisches Schema |
| File Storage | S3-kompatibles SDK (AWS SDK v2) | Kompatibel mit Garage und AWS S3 |
| Encryption | AES (javax.crypto) | Application-Level, kein externer Vault noetig |
| E-Mail | Jakarta Mail (spring-boot-starter-mail) | SMTP Versand |
| OpenAPI | openapi-generator-maven-plugin | Contract-First |
| Mapping | MapStruct | DTO <-> Domain <-> JPA Entity |
| Testing | JUnit 5 + Testcontainers + AssertJ | Testcontainers fuer PostgreSQL + LocalStack/MinIO |

## Package-Struktur (Spring Modulith)

```
tenant/
  api/
    TenantAdminController.java            (Platform Admin Endpunkte)
    TenantConfigController.java           (Tenant Admin Endpunkte)
    TenantFileController.java             (File Upload Endpunkte)
  service/
    TenantService.java                    (CRUD)
    TenantProvisioningService.java        (Tenant erstellen + Bucket + Defaults)
    TenantConfigService.java              (Config lesen/aktualisieren)
  mapper/
    TenantMapper.java                     (MapStruct)
  model/
    Tenant.java                           (Domain Model)
    TenantStatus.java                     (Enum)
    TenantConfig.java                     (Value Object)
    BrandingConfig.java                   (Value Object)
    EmailConfig.java                      (Value Object)
    DomainConfig.java                     (Value Object)
  repository/
    TenantRepository.java                 (Spring Data JPA)
    entity/
      TenantEntity.java                   (JPA Entity)

shared-kernel/
  filestorage/
    FileStorageService.java               (Interface)
    S3FileStorageService.java             (Garage/S3 Implementierung)
  email/
    EmailService.java                     (Interface)
    EmailMessage.java                     (Value Object)
    TenantEmailService.java              (liest SMTP Config, versendet)
  encryption/
    EncryptionService.java                (Interface)
    AesEncryptionService.java             (AES Implementierung)
  tenant/
    TenantContext.java                    (ThreadLocal/RequestScope: current Tenant)
    TenantId.java                         (Value Object)
```

## Implementierungs-Tasks

### Phase 1: Grundlagen

- [ ] **Task 1.1:** Datenbank-Schema erstellen (Flyway Migration)
- [ ] **Task 1.2:** OpenAPI Spec schreiben (Platform Admin + Tenant Admin Endpunkte)
- [ ] **Task 1.3:** openapi-generator konfigurieren

### Phase 2: Core

- [ ] **Task 2.1:** Domain Model implementieren (Tenant, TenantConfig, Status)
- [ ] **Task 2.2:** JPA Entity + Repository
- [ ] **Task 2.3:** MapStruct Mapper
- [ ] **Task 2.4:** TenantService (CRUD)
- [ ] **Task 2.5:** TenantConfigService (Config lesen/aktualisieren)
- [ ] **Task 2.6:** Controller (implementieren generierte Interfaces)

### Phase 3: Shared Services

- [ ] **Task 3.1:** EncryptionService (AES, fuer SMTP Passwort)
- [ ] **Task 3.2:** FileStorageService + S3FileStorageService (Garage/S3)
- [ ] **Task 3.3:** TenantProvisioningService (Tenant erstellen + Bucket anlegen)
- [ ] **Task 3.4:** EmailService + TenantEmailService
- [ ] **Task 3.5:** TenantContext (ThreadLocal/RequestScope fuer current Tenant)

### Phase 4: File Upload

- [ ] **Task 4.1:** OpenAPI Spec fuer File Endpunkte
- [ ] **Task 4.2:** TenantFileController + Service
- [ ] **Task 4.3:** Pre-signed URL Generierung

### Phase 5: Testing

- [ ] **Task 5.1:** Unit Tests (Service Layer)
- [ ] **Task 5.2:** Integration Tests (Testcontainers: PostgreSQL + MinIO/LocalStack)

### Spaeter

- [ ] Sub-Tenant Hierarchie (parent_id, vererbte Config)
- [ ] Tenant Usage Limits (max Users, max Content Types, Storage Quota)
- [ ] Tenant Billing Integration
