# App Domain - Detailplanung

## Ueberblick

Die App Domain ist die uebergeordnete Gruppierung fuer Content Definitions, Pages und Workflows.
Eine App ist ein eigenstaendiges, in sich geschlossenes Paket das innerhalb eines Tenants
deployed wird. Apps koennen voneinander abhaengen.

**Bounded Context:** App
**Storage:** Spring Data JPA (statisches Schema)

## Konzept

```
Tenant
  -> App "Core" (v1.0.0)
      -> Content Definitions: User, Media, Tag
  -> App "Blog" (v1.0.0) -- depends on "Core"
      -> Content Definitions: blog-post (Reference -> Core.User), category
      -> Pages: Homepage, Blog-Uebersicht, Blog-Detail
      -> Workflows: Publish-Flow, Review-Flow
  -> App "Shop" (v1.0.0) -- depends on "Core"
      -> Content Definitions: product, order (Reference -> Core.User)
      -> Pages: Shop, Produktseite, Checkout
      -> Workflows: Order-Flow, Payment-Flow
```

### Regeln

- Content Definitions, Pages, Workflows gehoeren **immer** zu einer App
- Keine "losen" Ressourcen auf Tenant-Ebene
- Apps koennen von anderen Apps abhaengen (explizite Dependencies)
- Eine abhaengige App kann Content Definitions der Dependency **referenzieren**
- Ohne Dependency keine Sichtbarkeit auf Ressourcen einer anderen App
- Dependencies sind nicht zirkulaer (A -> B -> A ist nicht erlaubt)

## Entitaeten

### App (Aggregate Root)

```
App
  id: UUID
  tenantId: UUID
  name: String (unique pro Tenant)
  slug: String (unique pro Tenant, URL-safe)
  description: String
  version: String (Semver, z.B. "1.0.0")
  status: AppStatus
  dependencies: List<AppDependency>
  createdAt: Timestamp
  updatedAt: Timestamp
  createdBy: UUID
```

### AppStatus (Enum)

```
DRAFT       -- App wird gerade aufgebaut
ACTIVE      -- App ist aktiv und nutzbar
INACTIVE    -- App ist deaktiviert
```

### AppDependency (Value Object)

```
AppDependency
  appId: UUID (FK)
  dependsOnAppId: UUID (FK)
  -- Kein versionConstraint in Phase 1 (kommt mit Marketplace)
```

## Auswirkungen auf andere Domains

### Content Domain

ContentDefinition bekommt ein `appId` Feld:

```
ContentDefinition
  id: UUID
  tenantId: UUID
  appId: UUID (FK -> apps)       // NEU
  name: String (unique pro App)  // unique Scope aendert sich: pro App statt pro Tenant
  ...
```

**Reference-Felder Validierung:** Wenn ein Reference-Feld auf eine Content Definition
einer anderen App zeigt, muss eine AppDependency existieren. Sonst wird die
Definition abgelehnt.

### Page Domain (spaeter)

```
Page
  id: UUID
  tenantId: UUID
  appId: UUID (FK -> apps)
  ...
```

### Workflow Domain (spaeter)

```
Workflow
  id: UUID
  tenantId: UUID
  appId: UUID (FK -> apps)
  ...
```

## API Endpunkte

### App Management (Tenant Admin)

```
POST   /api/v1/tenant/apps                          App erstellen
GET    /api/v1/tenant/apps                          Alle Apps im Tenant
GET    /api/v1/tenant/apps/{id}                     App Detail (inkl. Dependencies + Ressourcen)
PUT    /api/v1/tenant/apps/{id}                     App aktualisieren
DELETE /api/v1/tenant/apps/{id}                     App loeschen (nur wenn DRAFT oder keine Entries)
PUT    /api/v1/tenant/apps/{id}/status               App Status aendern
```

### App Dependencies (Tenant Admin)

```
POST   /api/v1/tenant/apps/{id}/dependencies         Dependency hinzufuegen
DELETE /api/v1/tenant/apps/{id}/dependencies/{depId}  Dependency entfernen
         (nur wenn keine References auf die Dependency-App existieren)
GET    /api/v1/tenant/apps/{id}/dependencies          Dependencies auflisten
```

### Ressourcen einer App

Content, Pages und Workflows werden ueber ihre bestehenden Endpunkte verwaltet,
aber immer im Kontext einer App:

```
POST   /api/v1/content/{appSlug}/definitions         Content Definition erstellen (in App)
GET    /api/v1/content/{appSlug}/definitions          Content Definitions der App
...
```

**Anpassung der Content API:** Der bisherige Endpunkt `/api/v1/content-definitions`
wird zu `/api/v1/content/{appSlug}/definitions` um den App-Kontext herzustellen.

**Content Entries** bleiben ueber den Definition-Namen erreichbar, aber ebenfalls
im App-Kontext:

```
GET    /api/v1/content/{appSlug}/{definitionName}/entries
POST   /api/v1/content/{appSlug}/{definitionName}/entries
...
```

## Datenbank-Schema

```sql
CREATE TABLE apps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    description     TEXT,
    version         VARCHAR(50) NOT NULL DEFAULT '0.1.0',
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID,
    UNIQUE(tenant_id, name),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE app_dependencies (
    app_id          UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    depends_on_id   UUID NOT NULL REFERENCES apps(id),
    tenant_id       UUID NOT NULL,
    PRIMARY KEY (app_id, depends_on_id),
    CHECK (app_id != depends_on_id)
);

-- RLS
ALTER TABLE apps ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON apps
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

ALTER TABLE app_dependencies ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON app_dependencies
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

-- Content Definitions: appId hinzufuegen
ALTER TABLE content_definitions ADD COLUMN app_id UUID NOT NULL REFERENCES apps(id);
-- Unique Constraint aendern: pro App statt pro Tenant
ALTER TABLE content_definitions DROP CONSTRAINT content_definitions_tenant_id_name_key;
ALTER TABLE content_definitions ADD CONSTRAINT content_definitions_app_id_name_key UNIQUE(app_id, name);
```

## Validierungen

### Dependency-Validierung

- Keine zirkulaeren Dependencies (A -> B -> A)
- Dependency muss im selben Tenant sein
- Dependency-App muss Status ACTIVE haben

### Reference-Feld Validierung (Content Domain)

Beim Erstellen eines Reference-Feldes:

```
1. Target Content Definition ermitteln
2. Gehoert Target zur selben App? -> OK
3. Gehoert Target zu einer anderen App?
   -> Existiert AppDependency von meiner App auf die Target-App? -> OK
   -> Keine Dependency? -> Fehler: "App 'Blog' hat keine Dependency auf App 'Core'"
```

### Dependency-Entfernung Validierung

Beim Entfernen einer Dependency pruefen ob noch References existieren:

```
1. Alle Content Definitions meiner App durchgehen
2. Haben Reference-Felder die auf Content Definitions der Dependency-App zeigen?
   -> Ja? -> Fehler: "Dependency kann nicht entfernt werden, References existieren"
   -> Nein? -> OK, Dependency entfernen
```

## OpenFGA Integration

Apps sind als Ressource im konsolidierten Authorization Model abgebildet.
Siehe `03-identity-access-domain.md` fuer das vollstaendige Modell.

**Vererbung:**
- `tenant admin` -> `app admin` -> `app editor` -> `app viewer`
- `app admin` und `tenant admin` -> `app creator`
- `app editor` -> `content_definition editor/deleter`
- `app viewer` -> `content_definition viewer`
- `app creator` -> `content_definition creator`

Berechtigungen koennen auf App-Ebene vergeben werden und fliessen
auf die Ressourcen der App herunter.

## Libraries & Technologie-Entscheidungen

| Bereich | Technologie | Begruendung |
|---|---|---|
| DB Layer | Spring Data JPA | Statisches Schema |
| OpenAPI | openapi-generator-maven-plugin | Contract-First |
| Mapping | MapStruct | DTO <-> Domain <-> JPA Entity |
| Testing | JUnit 5 + Testcontainers + AssertJ | Standard |

## Package-Struktur (Spring Modulith)

```
app/
  api/
    AppController.java                    (App CRUD + Dependencies)
  service/
    AppService.java                       (CRUD + Validierung)
    AppDependencyService.java             (Dependency Management + Zyklen-Pruefung)
  mapper/
    AppMapper.java                        (MapStruct)
  model/
    App.java                              (Domain Model)
    AppStatus.java                        (Enum)
    AppDependency.java                    (Value Object)
  repository/
    AppRepository.java                    (Spring Data JPA)
    AppDependencyRepository.java          (Spring Data JPA)
    entity/
      AppEntity.java                      (JPA Entity)
      AppDependencyEntity.java            (JPA Entity)
```

## Implementierungs-Tasks

### Phase 1: Grundlagen

- [ ] **Task 1.1:** Datenbank-Schema erstellen (Flyway Migration)
- [ ] **Task 1.2:** OpenAPI Spec schreiben (App + Dependencies Endpunkte)
- [ ] **Task 1.3:** Domain Model + JPA Entities + MapStruct Mapper

### Phase 2: Core

- [ ] **Task 2.1:** AppService (CRUD)
- [ ] **Task 2.2:** AppDependencyService (Dependency Management + Zyklen-Pruefung)
- [ ] **Task 2.3:** Controller (implementiert generierte Interfaces)

### Phase 3: Integration

- [ ] **Task 3.1:** Content Domain anpassen (appId auf ContentDefinition)
- [ ] **Task 3.2:** Content API Pfade anpassen ({appSlug} Prefix)
- [ ] **Task 3.3:** Reference-Feld Validierung (Dependency-Check)
- [ ] **Task 3.4:** OpenFGA: App als Ressource-Typ + Permission Vererbung

### Phase 4: Testing

- [ ] **Task 4.1:** Unit Tests (Zyklen-Pruefung, Dependency-Validierung)
- [ ] **Task 4.2:** Integration Tests

### Spaeter (Marketplace)

- [ ] App-Versioning mit Semver-Constraints auf Dependencies
- [ ] App-Templates (Export als Blueprint)
- [ ] Marketplace: App-Templates teilen/verkaufen
- [ ] App-Installation (Template in Tenant instantiieren)
- [ ] App-Update (Migration zwischen Versionen)
