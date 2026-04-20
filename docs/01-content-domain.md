# Content Domain - Detailplanung

## Ueberblick

Die Content Domain ist das Herzstueck der Plattform. Sie ermoeglicht Nutzern eigene Content-Typen
zu definieren (Schema) und darauf basierend Eintraege zu erstellen, bearbeiten und abzufragen.

**Bounded Context:** Content
**Subdomains:** Definition, Management
**Storage:** Typisierte EAV Value-Tables (mit Migrationspfad zu Dedicated Tables)

## Subdomain: Content Definition

### Entitaeten

#### ContentDefinition (Aggregate Root)

```
ContentDefinition
  id: UUID
  tenantId: UUID
  name: String (unique pro Tenant)
  description: String
  storageStrategy: StorageStrategy (EAV | DEDICATED_TABLE)
  fields: List<FieldDefinition>
  createdAt: Timestamp
  updatedAt: Timestamp
  createdBy: UUID
```

#### FieldDefinition (Entity, gehoert zu ContentDefinition)

```
FieldDefinition
  id: UUID
  definitionId: UUID (FK)
  tenantId: UUID
  fieldKey: String (unique pro Definition)
  fieldType: FieldType
  required: Boolean
  sortable: Boolean
  filterable: Boolean
  config: FieldConfig (typ-spezifisch)
  position: Integer (Reihenfolge)
```

#### FieldType (Enum / Value Object)

```
TEXT        -> content_values_text
INTEGER     -> content_values_integer
DECIMAL     -> content_values_decimal
BOOLEAN     -> content_values_boolean
DATE        -> content_values_date
REFERENCE   -> content_values_reference (FK auf content_entries)
```

#### FieldConfig (Value Object, typ-spezifisch)

```
TextFieldConfig:
  minLength: Integer
  maxLength: Integer
  pattern: String (Regex)
  defaultValue: String

IntegerFieldConfig:
  min: Long
  max: Long
  defaultValue: Long

DecimalFieldConfig:
  min: BigDecimal
  max: BigDecimal
  precision: Integer
  scale: Integer

BooleanFieldConfig:
  defaultValue: Boolean

DateFieldConfig:
  minDate: LocalDate
  maxDate: LocalDate

ReferenceFieldConfig:
  targetDefinitionId: UUID
  onDelete: CASCADE | SET_NULL | RESTRICT
```

### API Endpunkte

```
POST   /api/v1/content-definitions                   Erstellen
GET    /api/v1/content-definitions                   Liste (tenant-scoped)
GET    /api/v1/content-definitions/{id}              Detail
PUT    /api/v1/content-definitions/{id}              Aktualisieren
DELETE /api/v1/content-definitions/{id}              Loeschen (nur wenn keine Entries)
POST   /api/v1/content-definitions/{id}/fields       Field hinzufuegen
PUT    /api/v1/content-definitions/{id}/fields/{fid} Field aendern
DELETE /api/v1/content-definitions/{id}/fields/{fid} Field entfernen
```

## Subdomain: Content Management

### Entitaeten

#### ContentEntry (Aggregate Root)

```
ContentEntry
  id: UUID
  tenantId: UUID
  definitionId: UUID (FK)
  values: Map<String, FieldValue>  -- fieldKey -> typisierter Wert
  createdAt: Timestamp
  updatedAt: Timestamp
  createdBy: UUID
```

#### FieldValue (Value Object)

```
FieldValue
  fieldId: UUID
  fieldKey: String
  fieldType: FieldType
  value: Object (typisiert je nach FieldType)
```

### API Endpunkte (dynamisch pro Content Definition)

```
POST   /api/v1/content/{definitionName}/entries              Erstellen
GET    /api/v1/content/{definitionName}/entries              Liste (filter, sort, paginate)
GET    /api/v1/content/{definitionName}/entries/{id}         Detail
PUT    /api/v1/content/{definitionName}/entries/{id}         Aktualisieren
DELETE /api/v1/content/{definitionName}/entries/{id}         Loeschen
```

### Query-Syntax

**Filter:** RHS Operators - Feldname als Query-Param, Operator und Wert rechts vom `=`

```
?{fieldKey}={operator}:{value}
?{fieldKey}={value}              (ohne Operator = eq)
```

**Operatoren:**

| Operator | Bedeutung | Beispiel |
|---|---|---|
| `eq` | Gleich (default) | `?status=published` oder `?status=eq:published` |
| `neq` | Ungleich | `?status=neq:draft` |
| `gt` | Groesser als | `?views=gt:100` |
| `gte` | Groesser gleich | `?views=gte:100` |
| `lt` | Kleiner als | `?views=lt:50` |
| `lte` | Kleiner gleich | `?views=lte:50` |
| `like` | Enthält (Text) | `?title=like:Spring` |
| `in` | In Liste | `?status=in:published,archived` |
| `isnull` | Ist null | `?subtitle=isnull:true` |

**Sortierung und Pagination:** Spring-Konventionen

```
?sort=fieldKey,asc|desc          (default: asc)
?sort=createdAt,desc&sort=title,asc   (mehrfach moeglich)
?page=0&size=20                  (0-basiert, default size=20)
```

**Vollstaendiges Beispiel:**

```
GET /api/v1/content/blog-post/entries?published=true&views=gt:100&sort=createdAt,desc&page=0&size=20
```

### OpenAPI Strategie

**Phase 1 (Day 1):** Generische OpenAPI Spec mit `additionalProperties` fuer dynamische Filter-Parameter.
Der Endpunkt dokumentiert die Struktur (Operatoren, Pagination, Sort), aber nicht die konkreten Felder pro Content Type.

**Phase 2 (Spaeter):** Generierte OpenAPI Specs pro Content Definition - typsichere Dokumentation
basierend auf den definierten FieldDefinitions.

## Datenbank-Schema

### Statische Tabellen

```sql
-- Content Definitions
CREATE TABLE content_definitions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    storage_strategy VARCHAR(50) NOT NULL DEFAULT 'EAV',
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),
    created_by       UUID,
    UNIQUE(tenant_id, name)
);

-- Field Definitions
CREATE TABLE content_field_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    definition_id   UUID NOT NULL REFERENCES content_definitions(id),
    field_key       VARCHAR(255) NOT NULL,
    field_type      VARCHAR(50) NOT NULL,
    required        BOOLEAN NOT NULL DEFAULT false,
    sortable        BOOLEAN NOT NULL DEFAULT false,
    filterable      BOOLEAN NOT NULL DEFAULT false,
    config          JSONB,
    position        INTEGER NOT NULL DEFAULT 0,
    UNIQUE(definition_id, field_key)
);

-- Content Entries
CREATE TABLE content_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    definition_id   UUID NOT NULL REFERENCES content_definitions(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID
);

-- Typisierte Value-Tables
CREATE TABLE content_values_text (
    entry_id  UUID NOT NULL REFERENCES content_entries(id) ON DELETE CASCADE,
    field_id  UUID NOT NULL REFERENCES content_field_definitions(id),
    tenant_id UUID NOT NULL,
    value     TEXT,
    PRIMARY KEY (entry_id, field_id)
);
CREATE INDEX idx_cvt_value ON content_values_text(field_id, value);

CREATE TABLE content_values_integer (
    entry_id  UUID NOT NULL REFERENCES content_entries(id) ON DELETE CASCADE,
    field_id  UUID NOT NULL REFERENCES content_field_definitions(id),
    tenant_id UUID NOT NULL,
    value     BIGINT,
    PRIMARY KEY (entry_id, field_id)
);
CREATE INDEX idx_cvi_value ON content_values_integer(field_id, value);

CREATE TABLE content_values_decimal (
    entry_id  UUID NOT NULL REFERENCES content_entries(id) ON DELETE CASCADE,
    field_id  UUID NOT NULL REFERENCES content_field_definitions(id),
    tenant_id UUID NOT NULL,
    value     NUMERIC,
    PRIMARY KEY (entry_id, field_id)
);
CREATE INDEX idx_cvd_value ON content_values_decimal(field_id, value);

CREATE TABLE content_values_boolean (
    entry_id  UUID NOT NULL REFERENCES content_entries(id) ON DELETE CASCADE,
    field_id  UUID NOT NULL REFERENCES content_field_definitions(id),
    tenant_id UUID NOT NULL,
    value     BOOLEAN,
    PRIMARY KEY (entry_id, field_id)
);
CREATE INDEX idx_cvb_value ON content_values_boolean(field_id, value);

CREATE TABLE content_values_date (
    entry_id  UUID NOT NULL REFERENCES content_entries(id) ON DELETE CASCADE,
    field_id  UUID NOT NULL REFERENCES content_field_definitions(id),
    tenant_id UUID NOT NULL,
    value     TIMESTAMP,
    PRIMARY KEY (entry_id, field_id)
);
CREATE INDEX idx_cvdt_value ON content_values_date(field_id, value);

CREATE TABLE content_values_reference (
    entry_id  UUID NOT NULL REFERENCES content_entries(id) ON DELETE CASCADE,
    field_id  UUID NOT NULL REFERENCES content_field_definitions(id),
    tenant_id UUID NOT NULL,
    value     UUID NOT NULL REFERENCES content_entries(id),
    PRIMARY KEY (entry_id, field_id)
);
CREATE INDEX idx_cvr_value ON content_values_reference(field_id, value);
```

### Row-Level Security

```sql
-- Auf jeder Tabelle:
ALTER TABLE content_definitions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON content_definitions
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

-- Analog fuer alle anderen Tabellen
```

## Storage Strategy Pattern

### Interface

```java
public interface ContentStorage {
    ContentEntry save(ContentDefinition definition, ContentEntry entry);
    Optional<ContentEntry> findById(ContentDefinition definition, UUID entryId);
    Page<ContentEntry> query(ContentDefinition definition, QueryCriteria criteria);
    void delete(ContentDefinition definition, UUID entryId);
}
```

### Router

```java
@Component
public class ContentStorageRouter implements ContentStorage {
    private final EavContentStorage eavStorage;
    private final DedicatedTableContentStorage dedicatedStorage; // spaeter

    @Override
    public ContentEntry save(ContentDefinition def, ContentEntry entry) {
        return resolve(def).save(def, entry);
    }

    private ContentStorage resolve(ContentDefinition def) {
        return switch (def.getStorageStrategy()) {
            case EAV -> eavStorage;
            case DEDICATED_TABLE -> dedicatedStorage;
        };
    }
}
```

### Migrationspfad EAV -> Dedicated Table

1. Admin triggert Migration fuer einen Content Type
2. System generiert CREATE TABLE via jOOQ basierend auf FieldDefinitions
3. RLS Policy wird auf neue Tabelle angewendet
4. Daten werden im Background von EAV-Tables in Dedicated Table kopiert
5. `storage_strategy` wird auf `DEDICATED_TABLE` umgestellt
6. EAV-Daten werden async aufgeraeumt
7. Migration ist reversibel (Strategy zurueck auf EAV)

## Libraries & Technologie-Entscheidungen

### Content Definition (statisch)

| Bereich | Technologie | Begruendung |
|---|---|---|
| DB Layer | Spring Data JPA | Statisches Schema, wenig Boilerplate |
| Validation | Jakarta Validation | Standard-Annotationen auf Domain Model |
| Mapping | MapStruct | DTO <-> Domain <-> JPA Entity |
| JSON | Jackson | Polymorphe FieldConfig Serialisierung (JSONB) |
| OpenAPI | openapi-generator-maven-plugin | Contract-First: Spec generiert Controller Interfaces + DTOs |
| Migration | Flyway | Schon gesetzt |
| Testing | JUnit 5 + Testcontainers + AssertJ | Testcontainers fuer PostgreSQL Integration Tests |

### Content Management (dynamisch)

| Bereich | Technologie | Begruendung |
|---|---|---|
| DB Layer | jOOQ | Dynamische type-safe Queries fuer EAV Modell |
| Validation | Custom (ContentValidationService) | Validierung zur Laufzeit gegen FieldConfig |
| Mapping | MapStruct | DTO <-> Domain Model (kein JPA Entity hier) |
| JSON | Jackson | Request/Response Serialisierung |
| OpenAPI | openapi-generator-maven-plugin | Contract-First: generischer Spec mit additionalProperties |
| Migration | Flyway | Value-Tables Schema |
| Testing | JUnit 5 + Testcontainers + AssertJ | Testcontainers fuer PostgreSQL Integration Tests |

### Kein Lombok

Statt Lombok: Java Records fuer DTOs (werden hier generiert), manuelle Builder/Konstruktoren fuer Domain Model und JPA Entities.

### Contract-First Ansatz (OpenAPI)

1. OpenAPI Spec als YAML schreiben (Single Source of Truth)
2. `openapi-generator-maven-plugin` generiert:
   - Controller Interfaces
   - Request/Response DTOs
3. Unsere Controller implementieren die generierten Interfaces
4. Phase 1: Generischer Spec fuer Content Management
5. Phase 2 (spaeter): Generierte Specs pro Content Definition

## Package-Struktur (Spring Modulith)

```
content/
  ContentModuleConfiguration.java

  definition/
    api/
      ContentDefinitionController.java    (implementiert generiertes Interface)
    service/
      ContentDefinitionService.java       (Business Logic, arbeitet mit Domain Model)
    mapper/
      ContentDefinitionMapper.java        (MapStruct: gen. DTO <-> Domain <-> JPA Entity)
    model/
      ContentDefinition.java              (Domain Model, clean)
      FieldDefinition.java                (Domain Model, clean)
      FieldType.java                      (Enum)
      FieldConfig.java                    (Value Object, sealed interface)
      StorageStrategy.java                (Enum)
    repository/
      ContentDefinitionRepository.java    (Spring Data JPA Interface)
      entity/
        ContentDefinitionEntity.java      (JPA Entity)
        FieldDefinitionEntity.java        (JPA Entity)

  management/
    api/
      ContentEntryController.java         (implementiert generiertes Interface)
    service/
      ContentEntryService.java            (Business Logic)
      ContentValidationService.java       (Validierung gegen FieldConfig)
    mapper/
      ContentEntryMapper.java             (MapStruct: gen. DTO <-> Domain Model)
    model/
      ContentEntry.java                   (Domain Model, clean)
      FieldValue.java                     (Value Object)
    storage/
      ContentStorage.java                 (Interface)
      ContentStorageRouter.java
      EavContentStorage.java              (jOOQ)
      DedicatedTableContentStorage.java   (spaeter)

  shared/
    ContentId.java                        (Value Object)
    TenantScoped.java                     (Marker Interface)
```

## Implementierungs-Tasks

### Phase 1: Grundlagen

- [ ] **Task 1.1:** Projekt-Setup Spring Modulith + jOOQ + PostgreSQL
- [ ] **Task 1.2:** Shared Kernel erstellen (BaseEntity, TenantContext, AuditInfo)
- [ ] **Task 1.3:** RLS-Infrastruktur (TenantId im Connection-Context setzen)

### Phase 2: Content Definition

- [ ] **Task 2.1:** Datenbank-Schema erstellen (Flyway Migrations)
- [ ] **Task 2.2:** OpenAPI Spec schreiben (Content Definition Endpunkte)
- [ ] **Task 2.3:** openapi-generator-maven-plugin konfigurieren (Controller Interfaces + DTOs)
- [ ] **Task 2.4:** Domain Model implementieren (ContentDefinition, FieldDefinition, FieldType, FieldConfig)
- [ ] **Task 2.5:** JPA Entities erstellen (ContentDefinitionEntity, FieldDefinitionEntity)
- [ ] **Task 2.6:** MapStruct Mapper (gen. DTO <-> Domain <-> JPA Entity)
- [ ] **Task 2.7:** ContentDefinitionRepository (Spring Data JPA)
- [ ] **Task 2.8:** ContentDefinitionService (CRUD + Validierung)
- [ ] **Task 2.9:** ContentDefinitionController (implementiert generiertes Interface)
- [ ] **Task 2.10:** Tests (Unit + Integration mit Testcontainers)

### Phase 3: Content Management

- [ ] **Task 3.1:** Datenbank-Schema fuer Value-Tables (Flyway Migrations)
- [ ] **Task 3.2:** OpenAPI Spec schreiben (Content Management Endpunkte, generisch)
- [ ] **Task 3.3:** Domain Model implementieren (ContentEntry, FieldValue)
- [ ] **Task 3.4:** MapStruct Mapper (gen. DTO <-> Domain Model)
- [ ] **Task 3.5:** ContentStorage Interface + EavContentStorage (jOOQ)
- [ ] **Task 3.6:** ContentStorageRouter
- [ ] **Task 3.7:** ContentValidationService (Validierung gegen FieldConfig)
- [ ] **Task 3.8:** ContentEntryService (CRUD)
- [ ] **Task 3.9:** Dynamische Query-Logik (RHS Operators, Sort, Pagination via jOOQ)
- [ ] **Task 3.10:** ContentEntryController (implementiert generiertes Interface)
- [ ] **Task 3.11:** Tests (Unit + Integration mit Testcontainers)

### Phase 4: Spaeter

- [ ] **Task 4.1:** DedicatedTableContentStorage implementieren
- [ ] **Task 4.2:** Migration-Service (EAV -> Dedicated Table)
- [ ] **Task 4.3:** Content Versionierung
- [ ] **Task 4.4:** Bulk-Operationen (Import/Export)
