# DB-Schema Regeln

## Naming Conventions

### Tabellen und Spalten

| Element | Konvention | Beispiel |
|---|---|---|
| Tabellennamen | `snake_case`, **Singular** | `app`, `content_definition`, `tenant_user` |
| Spaltennamen | `snake_case` | `tenant_id`, `created_at` |
| Primary Key | immer `id` | `id UUID PRIMARY KEY` |
| Foreign Keys | `<entity>_id` | `app_id`, `tenant_id` |

### Constraints und Indizes

| Element | Konvention | Beispiel |
|---|---|---|
| Index | `idx_<table>_<columns>` | `idx_app_tenant_id` |
| Unique Constraint | `uq_<table>_<columns>` | `uq_app_tenant_id_name` |
| Check Constraint | `chk_<table>_<description>` | `chk_app_dependency_no_self_ref` |
| RLS Policy | `tenant_isolation_<table>` | `tenant_isolation_app` |

## Datentypen

| Zweck | Typ | Anmerkung |
|---|---|---|
| IDs | `UUID` | UUIDv7, applikationsseitig generiert, kein `DEFAULT gen_random_uuid()` |
| Timestamps | `TIMESTAMPTZ` | Mit Zeitzone, immer UTC |
| Enums | `VARCHAR` | Kein PostgreSQL Enum (schwer zu migrieren) |
| JSON-Daten | `JSONB` | |
| Kurze Strings | `VARCHAR(n)` | Sinnvolles Limit (z.B. 255 fuer Name, 100 fuer Slug) |
| Lange Texte | `TEXT` | Nur fuer unbegrenzte Felder (z.B. Description) |

## Standard-Spalten

Jede Tabelle hat folgende Spalten (ueber `AuditableBaseEntity` / `TenantAwareBaseEntity`):

```sql
-- Basis (alle Tabellen)
id              UUID PRIMARY KEY,
created_at      TIMESTAMPTZ NOT NULL,
created_by      UUID NOT NULL,
updated_at      TIMESTAMPTZ NOT NULL,
updated_by      UUID NOT NULL,
deleted_at      TIMESTAMPTZ,              -- Soft Delete (NULL = nicht geloescht)
deleted_by      UUID,                     -- Wer hat geloescht

-- Zusaetzlich bei Tenant-bezogenen Tabellen
tenant_id       UUID NOT NULL
```

- `created_by` / `updated_by` / `deleted_by` sind UUIDs (Keycloak User-ID), **kein Foreign Key**
- User kann geloescht werden ohne Audit-Eintraege zu beeinflussen

## Soft Delete

Alle Tabellen nutzen Soft Delete ueber `deleted_at` / `deleted_by` in `AuditInfo`.

**Filterung im Application Layer** (nicht in RLS Policy):
- Default: Repositories filtern `deleted_at IS NULL` standardmaessig
- Admin-Zugriff: Explizite Methoden wie `findAllIncludingDeleted()` fuer Tenant Admin / Platform Admin
- `AuditListener` setzt `deleted_at`/`deleted_by` **nicht** automatisch — passiert explizit im Service

**Warum nicht in RLS?**
- RLS soll nur eine Verantwortung haben: Tenant-Isolation
- Soft Delete ist Business-Logik
- Tenant Admin und Platform Admin muessen geloesche Eintraege sehen koennen

## Row-Level Security (RLS)

Jede Tabelle mit `tenant_id` bekommt RLS aktiviert. Die Policy kuemmert sich **ausschliesslich**
um Tenant-Isolation.

```sql
ALTER TABLE <table> ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_<table> ON <table>
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );
```

- `current_setting(..., true)` gibt NULL zurueck statt Error wenn Variable nicht gesetzt
- `IS NULL` -> Platform Admin (kein Tenant gesetzt, sieht alles)
- Kein Soft Delete Filter in der Policy

## Flyway Migrations

### Versionierung

Format: `V<datum>_<nummer>__<beschreibung>.sql`

```
V20260302_001__create_tenant_table.sql
V20260302_002__create_app_table.sql
V20260303_001__add_slug_to_app.sql
```

### Regeln

- Eine Migration pro logische Aenderung
- Keine `IF EXISTS` in Migrations (soll fehlschlagen wenn Zustand nicht stimmt)
- RLS Policy wird in der gleichen Migration wie die Tabelle erstellt
- Migrations sind idempotent-sicher durch Flyway-Versionierung (nicht durch IF EXISTS)

### Beispiel-Migration

```sql
-- V20260302_001__create_tenant_table.sql

CREATE TABLE tenant (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    config          JSONB,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID
);

-- Keine RLS auf tenant (hat kein tenant_id)
```

```sql
-- V20260302_002__create_app_table.sql

CREATE TABLE app (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL,
    description     TEXT,
    version         VARCHAR(50) NOT NULL DEFAULT '0.1.0',
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    CONSTRAINT uq_app_tenant_id_slug UNIQUE(tenant_id, slug),
    CONSTRAINT uq_app_tenant_id_name UNIQUE(tenant_id, name)
);

CREATE INDEX idx_app_tenant_id ON app(tenant_id);

ALTER TABLE app ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_app ON app
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );
```
