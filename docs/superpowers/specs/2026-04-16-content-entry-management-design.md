# Content Entry Management — Design

**Datum:** 2026-04-16
**Phase:** 5
**Branch:** `feat/content-entries-v2`
**Scope:** Vollständige CRUD-Funktionalität für Content-Entries auf Basis der bestehenden Content-Definitionen — inkl. tenant-isolierter Schemas, dynamischer Tabellen pro Definition, Cursor-Pagination, Reference/Mirror-Auflösung, History und Frontend.

---

## 1. Problem

Die Content-Definitions-Domain ist fertig (Phase 4: Definitionen + Felder + Validierung + OpenFGA-Permissions auf Definition-Ebene). Was fehlt:

- Keine Möglichkeit, **Content-Entries** anzulegen, zu lesen, zu editieren oder zu löschen
- Keine **Storage-Strategie** für Entries (per-Definition Tabelle? EAV? JSONB?)
- Keine **Tenant-Schema-Isolation** für dynamische Tabellen
- Kein **Lifecycle** für DDL-Änderungen an Feldern (sofort vs. deferred)
- Keine **Pagination/Filter/Sort** über variable Spalten
- Kein **History/Audit** für Entries
- Kein **Frontend** für dynamische Entry-Forms und -Tables

Im Repo existiert bereits ein paralleler Branch `feat/content-entry-management` mit ~50 Commits, der einen ersten Wurf gemacht hat — wird aber nicht weiterverwendet (Permissions zu eng integriert, Modulith-Boundary nicht sauber, Mirror-N+1, Soft-Delete-Lücken). Dieser Spec ist der saubere Neuanfang.

---

## 2. Architektur-Übersicht

### 2.1 Modulith-Boundaries

```
content (allowedDependencies: shared, app)
├── definition/                bestehend (CRUD für Definitionen + Felder)
├── fieldtype/                 NEU — Registry + Handler-Strategy
│   ├── FieldTypeRegistry
│   ├── FieldTypeHandler       Interface (cast, validateValue, columnType, buildCondition, formatForDisplay, isVirtual)
│   └── handler/               TextHandler, IntegerHandler, DecimalHandler, BooleanHandler, DateHandler, EnumHandler, ReferenceHandler, MirrorHandler
├── management/                NEU — Entry-Layer
│   ├── model                  ContentEntry, EntryReference, EntryHistoryRecord, FieldValue
│   ├── storage                ContentStorage Interface + DedicatedTableStorage
│   ├── service                ContentEntryService, ContentValidationService, ReferenceResolver, DisplayFormatResolver
│   ├── event                  ContentEntryCreatedEvent, ContentEntryUpdatedEvent, ContentEntryDeletedEvent
│   ├── api                    ContentEntryController, ContentEntryMapper
│   └── ddl                    DynamicTableManager, DdlDiffEngine, ContentDdlListener
└── package-info.java          ApplicationModule + NamedInterfaces für api/event/storage
```

### 2.2 Wer ruft wen?

```
HTTP Request
  └─> ContentEntryController              (api)
       └─> ContentEntryService            (service, orchestriert)
            ├─> ContentValidationService  (cast + required + unique + reference targets)
            ├─> DedicatedTableStorage     (storage, jOOQ-Queries auf cd_*-Tabellen)
            │    └─> FieldTypeRegistry    (cast + columnType + buildCondition)
            ├─> ReferenceResolver         (REFERENCE/MIRROR-Display + Inverse-Lookup)
            └─> ApplicationEventPublisher (Created/Updated/Deleted Events)

App Publish-Flow
  AppService.publish() → AppPublishedEvent (existiert bereits in app/event)
       └─> ContentDdlListener             (subscribes via @ApplicationModuleListener)
            └─> DdlDiffEngine             (berechnet ADD/DROP per pending_action)
                 └─> DynamicTableManager  (führt DDL via jOOQ aus)
```

### 2.3 Dependencies / Voraussetzungen auf `main`

| Vorhanden | Wird verwendet |
|---|---|
| `app.event.AppPublishedEvent` | Trigger für DDL-Apply |
| `content.definition.*` (Entities, Service, Repos) | Definitionen + Felder lesen |
| `content.definition.model.*` (FieldType, FieldConfig, OnDeleteStrategy, RelationType) | Domain-Modelle wiederverwenden |
| `shared.persistence.RlsConnectionProvider` | Erweitert um `search_path` |
| `shared.security.TenantContext` | `getCurrentUserId()` für audit cols |
| `shared.changelog.ChangeRecordService` | Bleibt für Definition-DDL-Audit; Entries bekommen eigene Companion-History-Tabellen |
| `shared.util.UuidGenerator` | UUIDv7 für Entry-IDs |
| Identity Phase 3a–3h | Kein neuer OpenFGA-Type |

---

## 3. Storage Layer

### 3.1 Tabellenstruktur

Pro Tenant ein eigenes Postgres-Schema: `tenant_{slug}` (Slug aus `tenant.slug`, hyphens → underscores).

Pro Definition eine dedizierte Tabelle `tenant_{slug}.cd_{def_slug}`:

| Spalte | Typ | Zweck |
|---|---|---|
| `id` | UUID PK (UUIDv7) | Entry-ID |
| `version` | BIGINT NOT NULL DEFAULT 0 | Optimistic Locking |
| `created_at` | TIMESTAMPTZ NOT NULL | Audit |
| `updated_at` | TIMESTAMPTZ NOT NULL | Audit |
| `created_by` | UUID NOT NULL | Audit (kein FK auf User) |
| `updated_by` | UUID NOT NULL | Audit |
| `deleted_at` | TIMESTAMPTZ NULL | Soft Delete |
| `deleted_by` | UUID NULL | Soft Delete |
| `cf_{field_key}` | per Handler.columnType() | User-Felder |

Indexe:
- PK auf `id`
- Composite `(cf_{field_key}, id)` für jedes sortable Feld → Keyset-Pagination
- Partial UNIQUE `(cf_{field_key}) WHERE deleted_at IS NULL` für jedes unique Feld

Companion History-Tabelle `tenant_{slug}.cd_{def_slug}_history`:

| Spalte | Typ |
|---|---|
| `id` | UUID PK |
| `entry_id` | UUID NOT NULL (Index) |
| `action` | VARCHAR(20) — `CREATED`/`UPDATED`/`DELETED` |
| `changed_fields` | JSONB — `{cf_title: {old: "A", new: "B"}}` |
| `changed_at` | TIMESTAMPTZ |
| `changed_by` | UUID |

### 3.2 ContentStorage Interface

```java
public interface ContentStorage {
    ContentEntry save(String schemaName, ContentDefinition def, Map<String, Object> values, UUID createdBy);
    ContentEntry update(String schemaName, ContentDefinition def, UUID entryId, long expectedVersion, Map<String, Object> values, UUID updatedBy);
    void softDelete(String schemaName, ContentDefinition def, UUID entryId, UUID deletedBy);
    Optional<ContentEntry> findById(String schemaName, ContentDefinition def, UUID entryId);
    CursorPage<ContentEntry> query(String schemaName, ContentDefinition def, ContentQuery query);
    CursorPage<ContentEntry> search(String schemaName, ContentDefinition def, String searchTerm, CursorPageRequest page);
    List<ContentEntry> findByReferenceField(String schemaName, ContentDefinition def, String fieldKey, UUID targetEntryId);
    boolean existsByFieldValue(String schemaName, ContentDefinition def, String fieldKey, Object value, UUID excludeEntryId);
    void nullifyField(String schemaName, ContentDefinition def, String fieldKey, UUID targetEntryId, UUID updatedBy);
    CursorPage<EntryHistoryRecord> getHistory(String schemaName, ContentDefinition def, UUID entryId, CursorPageRequest page);
}
```

### 3.3 DedicatedTableStorage Implementierung

- jOOQ `DSLContext` mit `DSL.name(schema, table)` und `DSL.field(name, dataType)` — keine String-Interpolation
- Jede Schreib-Operation in einer `@Transactional`-Methode: Insert/Update + History-Insert atomar
- **Soft-Delete-Filter zentral**: ein `appendNotDeleted(SelectConditionStep<?>)` Helper, den ALLE Read-Methoden (inkl. `query()`, `search()`, `findByReferenceField()`) zwingend nutzen
- **Optimistic Locking**: `UPDATE ... WHERE id=? AND version=? RETURNING version` — bei 0 affected rows → `OptimisticLockException` (RFC-7807, HTTP 409)
- **Mirror Fields** werden via `handler.isVirtual()` aus DDL- und Read/Write-Operationen ausgeschlossen — werden nur im `ReferenceResolver` zur Laufzeit aufgelöst

---

## 4. DDL Lifecycle

### 4.1 Pending Action statt sofortiges DDL

Auf `content_field_definition` werden zwei Spalten ergänzt:

- `published BOOLEAN NOT NULL DEFAULT false` — wurde dieses Feld bereits in eine reale Tabellen-Spalte materialisiert?
- `pending_action VARCHAR(20) NULL` — was muss bei nächstem Publish passieren? `ADDED` / `REMOVED` / `MODIFIED` / NULL

Field-CRUD im `ContentFieldService` setzt nur diese Flags, **kein** sofortiges DDL.

### 4.2 ContentDdlListener

```java
@ApplicationModuleListener
public void on(AppPublishedEvent event) {
    // 1. Alle ContentDefinitions des Apps laden
    // 2. Pro Definition: pending_action-Felder via DdlDiffEngine zu DdlChange-Liste mappen
    // 3. DynamicTableManager.applyChanges(schema, definition, changes)
    // 4. Felder-Flags clearen: published=true, pending_action=null
    // 5. ContentFieldChangeLog-Eintrag (action, oldType, newType, dataLossFlag)
}
```

### 4.3 DynamicTableManager

- `createTable(schema, def)` — neue Definition: cd_-Table + History-Table + Indexe
- `applyChanges(schema, def, List<DdlChange>)` — ADD_COLUMN / DROP_COLUMN / ADD_INDEX / ADD_UNIQUE
- `dropTable(schema, def)` — Definition gelöscht
- Alle DDL-Statements mit `SET LOCAL lock_timeout = '5s'` davor
- `@Transactional` pro Operation
- Indexe und UNIQUE-Constraints werden **mit** der Spalte erzeugt (im selben DDL-Block)

### 4.4 ContentFieldChangeLog

Bestehende Idee aus altem Plan 2 übernehmen — JPA-Entity `content_field_change_log` mit RLS, sammelt: `field_id`, `action`, `old_type`, `new_type`, `data_loss_flag`, `executed_at`, `executed_by`. Nur für Definition-DDL-Audit. Entry-Daten-Audit läuft über die per-Definition History-Tables.

---

## 5. Cursor-Pagination + Filter + Sort

### 5.1 Domain

```java
record CursorPageRequest(String cursor, int size) {  // size: default 20, max 100
    public int effectiveSize() { ... }
}

record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {}

record ContentQuery(List<Filter> filters, List<SortCriterion> sorts, CursorPageRequest page) {}

record Filter(String column, Operator operator, Object rawValue) {}

enum Operator { EQ, NEQ, GT, GTE, LT, LTE, LIKE, IN, ISNULL }

record SortCriterion(String column, Direction direction) {}  // max 3 enforced
```

### 5.2 Cursor-Format

`Base64(JSON({sorts: [{col, dir}], lastValues: [Object], lastId: UUID}))`

- Cursor enthält die Sort-Spec → bei abweichender Sort-Spec im Request: `IllegalArgumentException` (stale cursor)
- Tiebreaker auf `id` immer dabei
- NULLs LAST bei ASC, NULLs FIRST bei DESC

### 5.3 RHS-Operator-Syntax

Query-Param-Format: `?cf_title=eq:Hello&cf_count=gte:5`

Operator-Whitelist pro Field-Type via `FieldTypeHandler.supportedOperators()`:

| Type | Erlaubte Operatoren |
|---|---|
| TEXT | EQ, NEQ, LIKE, IN, ISNULL |
| INTEGER | EQ, NEQ, GT, GTE, LT, LTE, IN, ISNULL |
| DECIMAL | EQ, NEQ, GT, GTE, LT, LTE, ISNULL |
| BOOLEAN | EQ, NEQ, ISNULL |
| DATE | EQ, NEQ, GT, GTE, LT, LTE, ISNULL |
| ENUM | EQ, NEQ, IN, ISNULL |
| REFERENCE | EQ, NEQ, IN, ISNULL |
| MIRROR | — (nicht filterbar, virtuelles Feld) |

### 5.4 Search-Endpoint

Separater Endpoint für Reference-Combobox:

```
GET /api/v1/apps/{appSlug}/content/{defSlug}/entries/search?q=John&size=20&cursor=...
```

Liefert `CursorPage<EntryReference>` (id + display) — Display via `DisplayFormatResolver` aus `definition.displayFormat`-Template (`{cf_first_name} {cf_last_name}`).

`q` matcht auf alle Felder, die im `displayFormat` referenziert sind, via `LIKE %q%`.

---

## 6. Service Layer + Domain Events

### 6.1 ContentEntryService

Public API (4 Methoden):

```java
ContentEntry create(UUID appId, String defSlug, Map<String, Object> values);
ContentEntry update(UUID appId, String defSlug, UUID entryId, long version, Map<String, Object> values);
void delete(UUID appId, String defSlug, UUID entryId);
ContentEntry findById(UUID appId, String defSlug, UUID entryId);  // wirft NotFoundException wenn nicht vorhanden (Storage liefert Optional, Service unwrappt)
CursorPage<ContentEntry> query(UUID appId, String defSlug, ContentQuery query);
CursorPage<EntryReference> search(UUID appId, String defSlug, String q, CursorPageRequest page);
CursorPage<EntryHistoryRecord> getHistory(UUID appId, String defSlug, UUID entryId, CursorPageRequest page);
```

Reihenfolge in `create`:

1. Definition laden (mit `@RequiresPermission("reader", "content_definition", appId)` — **bestehend, bleibt**)
2. `ContentValidationService.validateAndCast(values, definition)` → cast + required + unique + reference targets exist
3. `Schema = "tenant_" + TenantContext.getTenantInformation().slug()`
4. `DedicatedTableStorage.save(schema, def, castedValues, currentUserId)`
5. `eventPublisher.publishEvent(new ContentEntryCreatedEvent(tenantId, defId, entry.id()))`
6. Return Entry

### 6.2 ContentValidationService

```java
Map<String, Object> validateAndCast(Map<String, Object> raw, ContentDefinition def, UUID excludeEntryIdForUnique);
```

Pipeline pro Field:
1. `handler.cast(raw, fieldConfig)` — wirft `ValidationException` bei Cast-Fehler
2. Required-Check wenn `field.isRequired()` und value null → `ValidationException` mit field-level error
3. `handler.validateValue(cast, fieldConfig)` — Min/Max/Pattern/AllowedValues
4. Unique-Check wenn `field.isUnique()` → `storage.existsByFieldValue(...)` → `ConflictException` (HTTP 409)
5. Reference-Target-Check für REFERENCE: `storage.findById(targetSchema, targetDef, value)` → 404 wenn nicht vorhanden

Mirror-Felder werden komplett übersprungen (write-protected, virtual).

### 6.3 ReferenceResolver

Wandelt UUIDs in REFERENCE-Spalten und MIRROR-Felder in `EntryReference{id, display}` um, beim Read.

**Mirror-Resolution-Performance-Fix:** Statt `findAll` + Java-Filter:

```java
List<ContentEntry> referencing = storage.findByReferenceField(
    sourceSchema, sourceDef, sourceFieldKey, targetEntryId);
```

`findByReferenceField` baut: `WHERE cf_{sourceFieldKey} = ? AND deleted_at IS NULL`.

### 6.4 OnDelete-Enforcement

Vor `softDelete`: alle inversen Referenzen finden (alle Definitionen, alle REFERENCE-Felder, die auf diese Definition zeigen):

| OnDelete | Verhalten |
|---|---|
| `RESTRICT` | Wenn referenziert: `ConflictException` (HTTP 409) mit Liste der blockenden Entries |
| `SET_NULL` | `storage.nullifyField(...)` für jede Referenz |
| `CASCADE` | Recursive `softDelete()` aller referenzierenden Entries (mit Cycle-Detection via Set<UUID>) |

### 6.5 Domain Events

```java
public record ContentEntryCreatedEvent(UUID tenantId, UUID definitionId, UUID entryId) {}
public record ContentEntryUpdatedEvent(UUID tenantId, UUID definitionId, UUID entryId, Set<String> changedFieldKeys) {}
public record ContentEntryDeletedEvent(UUID tenantId, UUID definitionId, UUID entryId) {}
```

- Spring `ApplicationEvent`, Modulith-konform (in `content.management.event` Package mit `NamedInterface`)
- **Keine Listener jetzt** — nur publishen, damit Search-Indexierung / Webhooks / externer Audit-Sink später andocken können
- `@TransactionalEventListener(phase = AFTER_COMMIT)` für zukünftige Listener empfohlen

### 6.6 Permissions — bewusst weggelassen

- Bestehend: `@RequiresPermission("reader", "content_definition", appId)` auf Definition-Read im `ContentDefinitionService` — **bleibt unverändert**
- **Neu**: keine OpenFGA-Checks für Entry-CRUD oder Field-CRUD
- Tenant-Isolation via JWT/Subdomain + RLS bleibt selbstverständlich (sonst sähe jeder alles)
- `createdBy/updatedBy/deletedBy` weiterhin aus `TenantContext.getCurrentUserId()`

---

## 7. Frontend

### 7.1 Routes

```
src/app/(workspace)/apps/[appSlug]/content/[defSlug]/entries/page.tsx          List
src/app/(workspace)/apps/[appSlug]/content/[defSlug]/entries/[entryId]/page.tsx Detail
```

### 7.2 Components

```
src/features/content-entries/
├── components/
│   ├── entry-table.tsx                    Dynamic columns + Filter-Inputs + Sort-Toggles + Cursor-Pagination
│   ├── entry-table-columns.tsx            FieldCellRenderer pro FieldType
│   ├── entry-form-dialog.tsx              Create/Edit Dialog
│   ├── dynamic-field-renderer.tsx         Registry Dispatcher
│   ├── inputs/
│   │   ├── text-field-input.tsx           shadcn Input
│   │   ├── integer-field-input.tsx        shadcn Input type=number
│   │   ├── decimal-field-input.tsx        shadcn Input type=number step=0.01
│   │   ├── boolean-field-input.tsx        shadcn Checkbox
│   │   ├── date-field-input.tsx           shadcn Popover + Calendar
│   │   ├── enum-field-input.tsx           shadcn Select
│   │   └── reference-field-input.tsx      Combobox + useEntrySearch + Debounce 300ms + Cursor
│   ├── entry-detail-page.tsx              Tabs: Details + History
│   ├── entry-detail-values.tsx            Key-Value Layout
│   ├── entry-history-list.tsx             Paginated diffs
│   └── entry-delete-dialog.tsx            Mit 409 RESTRICT-Handling
├── hooks/
│   └── use-entry-search.ts
├── schemas/
│   └── build-entry-schema.ts              Zod-Schema-Builder aus FieldDefinition[]
└── ...
```

### 7.3 Dynamische Form-Validierung

`buildEntrySchema(fields: FieldDefinition[]): ZodSchema` baut zur Laufzeit ein Zod-Schema:

- TEXT → `z.string().min(minLength).max(maxLength).regex(pattern)`
- INTEGER → `z.coerce.number().int().min(min).max(max)`
- DECIMAL → `z.coerce.number().min(min).max(max)`
- BOOLEAN → `z.boolean()`
- DATE → `z.string().datetime()` (ISO 8601)
- ENUM → `z.enum([...allowedValues])`
- REFERENCE → `z.object({ id: z.string().uuid(), display: z.string() }).nullable()`
- MIRROR → übersprungen (read-only)

`required` → kein `.optional()`. Optional → `.nullable().optional()`.

### 7.4 Frontend Permissions

Keine Permission-Gates für Entry-Buttons. Alle Tenant-User mit JWT sehen alle Entry-Actions. (Definition-List/Detail-Permissions bleiben bestehen.)

### 7.5 i18n

Neue Namespace: `entries.*` in `en.json` + `de.json` — Form, Table, Detail, History, Delete, Reference (~50 Keys).

---

## 8. Cross-Cutting Concerns

### 8.1 Tenant-Schema + RLS-search_path-Erweiterung

`RlsConnectionProvider.getConnection(tenantId)` wird erweitert:

```java
if (!PLATFORM_TENANT_CONTEXT.equals(tenantId)) {
    SET app.tenant_id = '<tenantId>'
    String slug = tenantSlugResolver.resolveSlug(tenantId);  // Caffeine-cached
    SET search_path = 'tenant_' + slug + ', public'
}
```

**Risiko:** Diese Änderung trifft ALLE bestehenden Repos (Tenant, Identity, App). Eigener Sub-Plan **5a** mit:
- Separatem Integrationstest, der bestehende JPA-Repos gegen den neuen `search_path` validiert
- Caffeine-Cache für Slug-Lookup (5min TTL, wie `TenantInfrastructureLookupService`)
- `search_path = public` als Fallback wenn Slug nicht resolvbar

### 8.2 Tenant-Schema-Lifecycle

- `TenantSchemaService.createSchema(slug)` — Flyway-extern, JdbcTemplate-DDL
- `TenantSchemaService.dropSchema(slug)` — `DROP SCHEMA tenant_{slug} CASCADE`
- Provisioning: neuer `TENANT_SCHEMA`-Step zwischen `OPENFGA_STORE` und `INITIAL_ADMIN`
- Cleanup: `TenantDeletedEvent`-Listener → `dropSchema` (wenn Hard-Delete; bei Soft-Delete: nichts)

### 8.3 Soft-Delete in dynamischen Queries

Alle SELECTs auf `cd_*`-Tabellen MÜSSEN `deleted_at IS NULL` filtern. Zentrale Durchsetzung über Storage-internen Helper, der von ALLEN Read-Methoden zwingend genutzt wird (`findById`, `query`, `search`, `findByReferenceField`).

### 8.4 Optimistic Locking

- `version` BIGINT auf jeder cd_-Tabelle, Default 0
- Update: `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`
- 0 affected rows → `OptimisticLockException` (HTTP 409, code `entry.optimistic_lock_failed`)
- Frontend: bei 409 Re-Fetch + Diff-Anzeige + manuelle Konfliktauflösung (Out-of-Scope für Phase 5; einfacher Toast + Reload reicht)

### 8.5 i18n Error Codes

Bestehendes RFC-7807-Pattern mit `code`-Feld nutzen. Neue Codes:

```
entry.not_found
entry.field.required
entry.field.invalid_value
entry.field.below_min
entry.field.exceeds_max
entry.field.pattern_mismatch
entry.field.duplicate_value
entry.field.invalid_reference_target
entry.optimistic_lock_failed
entry.delete_restricted
```

Field-level errors via `RfcProblem.errors[]` Array (Pfad + Code).

### 8.6 Modulith-Verifier

`content/package-info.java` auf `NamedInterface` umstellen für die Packages, die extern adressierbar sein müssen (`api`, `event`, `model` falls von App-Modul referenziert). `ApplicationModules.verify()` Test im `WebcontifyApplicationTests`.

---

## 9. Sub-Plan-Struktur

Sequentielle Implementierung. Jeder Sub-Plan = ein Subagent-Run mit Review-Checkpoint.

| # | Sub-Plan | Inhalt | Geschätzt |
|---|---|---|---|
| **5a** | Tenant-Schema + RLS search_path | `TenantInformation.slug`, `RlsConnectionProvider`-Erweiterung, `TenantSchemaService`, `TenantProvisioningListener`-Step, `TenantDeletedEvent`-Listener, Regression-Tests aller bestehenden Repos | klein |
| **5b** | FieldTypeRegistry + 8 Handler | `FieldTypeHandler`-Interface mit allen 6 Methoden, Registry, 8 Handler-Klassen mit Tests | mittel |
| **5c** | Dynamic Table Manager | `pending_action`/`published` columns auf content_field, `DdlDiffEngine`, `DynamicTableManager`, `ContentDdlListener`, `content_field_change_log` | mittel |
| **5d** | Storage Foundation | `ContentEntry`/`EntryReference`/`EntryHistoryRecord`-Models, `ContentStorage`-Interface, `DedicatedTableStorage` (alle Methoden in einem Rutsch, kein Stub-Pattern), `DisplayFormatResolver`, optimistic locking, soft-delete-helper | groß |
| **5e** | Cursor-Pagination + Filter + Sort | `CursorPageRequest`/`CursorPage`/`ContentQuery`/`Filter`/`Operator`-Records, `CursorPaginationBuilder`, `RhsOperatorParser`, `buildCondition` in jedem Handler, `query()`+`search()`+`findByReferenceField()` Storage-Methoden | mittel |
| **5f** | Service + API + Events | `ContentValidationService`, `ReferenceResolver`, `ContentEntryService`, OnDelete-Enforcement, Domain Events, OpenAPI Spec, Controller, Mapper | groß |
| **5g** | Frontend | Routes, Form, Table, Detail, History, Delete, Reference-Combobox, i18n, Vitest-Tests | groß |

**Reihenfolge ist hart sequentiell** — jeder Plan baut auf dem vorigen. Pro Plan: writing-plans → execution → review-checkpoint → commit.

---

## 10. Out of Scope (bewusst)

| Topic | Begründung |
|---|---|
| **OpenFGA-Permissions für Entry/Field-CRUD** | User-Entscheidung. Bestehende Definition-Read-Permissions bleiben |
| **FILE-Field-Type** | S3 ist da, aber separater Slice. Einfacher nachzuziehen wenn Entry-Foundation steht |
| **Full-Text-Search (tsvector/trigram)** | Nur LIKE-basierte Search jetzt; FTS später bei Bedarf |
| **Concurrent-Edit-Konflikt-Resolution-UX** | 409 + Reload reicht; keine 3-Way-Merge-UI |
| **Bulk-Operations (Bulk-Delete, Bulk-Edit)** | Nicht Teil dieser Phase |
| **Entry-Versioning (gerollte Versionen)** | History-Diff reicht für Audit; Versionen-Rollback nicht jetzt |
| **Webhooks/External Audit-Sink** | Domain Events sind die Hook-Stelle, Listener kommen später |

---

## 11. Decisions Log

| Entscheidung | Begründung |
|---|---|
| **Per-Definition Tabelle statt EAV** | Performance, Type-Safety auf DB-Ebene, native Indexierung. Konsistent mit `docs/01-content-domain.md` |
| **Schema-per-Tenant statt tenant_id-Column** | Echte Isolation (DROP SCHEMA), keine RLS-Komplexität für dynamische Tabellen, einfacheres Backup/Restore pro Tenant |
| **Companion History-Table pro Definition** | Performance bei großen Datasets, JSONB-Diff statt Full-Row-Snapshot, parallel zu `shared.changelog` (das bleibt für Definition-DDL-Audit) |
| **Cursor-Pagination statt Offset** | Keine Drift bei Live-Daten, performant bei großen Tabellen, Standard für moderne APIs |
| **Optimistic Locking statt Pessimistic** | Schreibarme Workload (Content-Editor), kein Lock-Hold-Risiko |
| **DDL deferred bis App-Publish** | Definition-Editor kann ohne Konsequenzen experimentieren, DDL-Risiko nur bei expliziter Publish-Aktion |
| **Domain Events publishen ohne Listener** | Future-Proofing für Search/Webhooks/Audit ohne Vorab-Investition |
| **Permissions weglassen** | User-Entscheidung; Tenant-Isolation reicht für jetzt |

---

## 12. Risiken

| Risiko | Mitigation |
|---|---|
| **`search_path`-Erweiterung bricht bestehende Repos** | Eigener Sub-Plan 5a mit Regression-Tests aller bestehenden JPA-Repos |
| **Slug-Lookup pro Connection ist teuer** | Caffeine-Cache, 5min TTL, Slug auch direkt in `TenantInformation` |
| **DDL bei großen Tabellen blockt** | `lock_timeout = 5s` + Ausführung im App-Publish-Window |
| **Cursor-Stale bei geändertem Sort** | Cursor enthält Sort-Spec, Mismatch → `IllegalArgumentException` |
| **Mirror-Resolution-Performance** | Dedicated Storage-Methode `findByReferenceField` mit WHERE-Filter, kein Java-Filter |
| **JSONB-Diff-Größe bei großen Text-Feldern** | Alte Werte truncaten auf 4KB im History-Eintrag (Soft-Limit) |
