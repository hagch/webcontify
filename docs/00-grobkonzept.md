# Grobkonzept - Webcontify

## Vision

Eine Plattform mit der andere Leute Applikationen bauen koennen (Low-Code).
Kunden koennen eigene Content-Strukturen definieren, Webseiten visuell bauen und Workflows erstellen.

## Architektur-Prinzipien

- **Domain Driven Design** - Klare Bounded Contexts
- **KISS** - So einfach wie moeglich, so komplex wie noetig
- **DRY** - Keine Duplikation, aber keine voreiligen Abstraktionen
- **Clean Code** - Lesbar, wartbar, testbar

## Tech Stack

| Komponente | Technologie |
|---|---|
| Frontend / BFF | Next.js (Backend for Frontend) |
| Backend | Java, Spring Modulith |
| Datenbank | PostgreSQL (RLS fuer Multi-Tenancy) |
| Query Layer | jOOQ |
| Auth (Authentifizierung) | Keycloak (Realm pro Tenant) |
| Auth (Autorisierung) | OpenFGA (ReBAC, Zanzibar-basiert) |
| File Storage | S3-kompatibel (Garage), ein Bucket pro Tenant |
| Encryption | Application-Level AES (kein externer Vault) |
| Page Editor | Puck.js |
| Workflow Editor | React Flow |
| API Stil | REST + OpenAPI (versioniert, `/api/v1/...`) |

## Bounded Contexts / Domains

### 1. Tenant Management

**Verantwortung:** Multi-Tenancy, Sub-Tenants, Tenant-Konfiguration

- Tenant CRUD
- Sub-Tenant Hierarchie
- Tenant-spezifische Konfiguration
- Row-Level Security Setup

**Status:** Im Detail besprochen, siehe `02-tenant-domain.md`

### 2. Identity & Access

**Verantwortung:** Authentifizierung, Rollen, Permissions (tenant-scoped)

- Keycloak-Integration
- Rollen-Mapping pro Tenant
- Permission-System

**Status:** Im Detail besprochen, siehe `03-identity-access-domain.md`

### 3. App

**Verantwortung:** Gruppierung von Content Definitions, Pages und Workflows

- App CRUD (Name, Slug, Version, Status)
- App Dependencies (App A haengt von App B ab)
- Alle Ressourcen (Content, Pages, Workflows) gehoeren zu einer App
- Spaeter: Marketplace (App-Templates teilen/verkaufen)

**Status:** Im Detail besprochen, siehe `04-app-domain.md`

### 4. Content (Definition + Management)

**Verantwortung:** Dynamische Content-Typen und deren Daten

Ein Bounded Context mit zwei Subdomains:
- **Content Definition** - Schema-Builder: Custom Content Types, Fields, Validierung
- **Content Management** - CRUD auf Basis der Definitionen

**Status:** Im Detail besprochen, siehe `01-content-domain.md`

### 5. Platform UI

**Verantwortung:** Verwaltungsoberflaeche fuer die gesamte Plattform

- Next.js App Router, shadcn/ui, Thin BFF
- Nx Monorepo (Frontend + Backend)
- Orval generiert API Client, React Query Hooks, Zod Schemas
- Facade Pattern (Custom Hooks) fuer Orchestrierung
- Tenant-Theming via CSS Variables (Simple + Advanced Mode)
- Keycloak Login Theming via Realm-Attributes
- User Invitations mit Whitelabel-Branding

**Status:** Im Detail besprochen, siehe `05-ui-domain.md`

### 6. Page Builder

**Verantwortung:** Visuelle Seiten-Komposition

- Puck.js Integration (Frontend)
- Page-Daten Persistierung (Backend)
- Verknuepfung mit Content-Eintraegen
- Komponenten-Registry

**Status:** Noch nicht im Detail besprochen

### 7. Workflow Engine

**Verantwortung:** Prozess-Definition und Ausfuehrung

- Flow-Definition via React Flow (Frontend)
- Simple State Machine (Backend, Day 1)
- Spaeter: Temporal.io Integration fuer Timer/Events

**Status:** Noch nicht im Detail besprochen

## Architektur-Uebersicht

```
+-----------------------------------------+
|            Next.js (BFF)                |
|  - SSR/RSC fuer Page Rendering          |
|  - Puck.js Editor                       |
|  - React Flow Editor                    |
|  - API-Calls an Java Backend            |
+-------------------+---------------------+
                    | REST (OpenAPI)
+-------------------v---------------------+
|          Java Backend                    |
|          Spring Modulith                 |
|                                          |
|  +----------+  +-----------+             |
|  | tenant   |  | identity  |             |
|  +----------+  +-----------+             |
|  +----------+  +-----------+             |
|  | app      |  | content   |             |
|  +----------+  +-----------+             |
|  +----------+  +-----------+             |
|  | page     |  | workflow  |             |
|  +----------+  +-----------+             |
|  shared-kernel                           |
+-------------------+---------------------+
                    |
+-------------------v---------------------+
|          PostgreSQL                       |
|  Row-Level Security pro Tenant           |
+------------------------------------------+
```

## Spring Modulith Modul-Struktur

```
backend/
  tenant/              -- Tenant Verwaltung, Provisioning
  identity/            -- Keycloak-Integration, OpenFGA, User/Rollen Management
  app/                 -- App Verwaltung, Dependencies
  content/             -- Definition + Management (Subdomains)
    definition/
    management/
    shared/
  page/                -- Page Builder Backend
  workflow/            -- Flow Definition + Execution
  shared-kernel/       -- TenantInformation, AuditInfo, Base Entities, RLS Proxy, FileStorage, Email, Encryption, Error Handling
```

## Entscheidungslog

| # | Entscheidung | Begruendung |
|---|---|---|
| 1 | Shared DB + Row-Level Security | Einfachster Ansatz fuer Multi-Tenancy, skaliert spaeter |
| 2 | Typisierte EAV Value-Tables | Native Indexierung, FKs, Type Safety ohne DDL zur Runtime |
| 3 | Spring Modulith | Monolith mit klaren Grenzen, spaeter trennbar |
| 4 | jOOQ als Query Layer | Dynamische type-safe Queries fuer EAV Modell |
| 5 | Keycloak | Multi-Tenancy Support, bewaeehrte Loesung |
| 6 | Simple Workflow zuerst | KISS, Temporal spaeter bei Bedarf |
| 7 | Storage Strategy Pattern | EAV Day 1, Migration zu Dedicated Tables spaeter moeglich |
| 8 | JPA fuer statisch, jOOQ fuer dynamisch | JPA fuer CRUD auf statischen Tabellen, jOOQ fuer dynamische EAV Queries |
| 9 | Contract-First (OpenAPI) | openapi-generator generiert Controller Interfaces + DTOs aus YAML Spec |
| 10 | Kein Lombok | Java Records fuer DTOs (generiert), manuelle Konstruktoren fuer Domain/Entity |
| 11 | Getrennte Domain- und JPA-Klassen | Sauberes Domain Model ohne JPA-Annotationen, MapStruct fuer Mapping |
| 12 | RHS Operators fuer Filter | `?field=operator:value` Syntax, Spring Pageable/Sort Konventionen |
| 13 | S3/Garage File Storage | Ein Bucket pro Tenant, Bucket wird bei Tenant-Provisioning erstellt |
| 14 | Application-Level Encryption | AES fuer sensitive Config-Daten (z.B. SMTP Passwort), kein Vault |
| 15 | Keine Sub-Tenants in Phase 1 | Spaeter nachruestbar via parent_id, KISS |
| 16 | Whitelabel | Eigene Domain, Branding, E-Mail Config pro Tenant |
| 17 | DataSource Proxy fuer RLS | SET LOCAL auf jeder Connection, funktioniert fuer JPA + jOOQ |
| 18 | Tenant Resolution Chain | Custom Domain -> JWT Claim -> Header (erste Quelle gewinnt) |
| 19 | File Access: Public vs Pre-signed | Config-Files (Logo etc.) public, Rest via kurzlebige Pre-signed URLs |
| 20 | Platform UI als Single Point of Management | Keycloak + OpenFGA sind interne Services, Verwaltung nur ueber Platform API |
| 21 | Keycloak nur fuer Auth | Authentifizierung + User Lifecycle, keine Rollen/Permissions |
| 22 | OpenFGA fuer alle Permissions | Single Source of Truth: Rollen, Permissions, ReBAC |
| 23 | Realm pro Tenant | Eigener Keycloak Realm pro Tenant fuer Whitelabel Login |
| 24 | Explizite Permission Propagation | Keine Defaults bei ReBAC Reference-Berechtigungen, Pflichtfeld |
| 25 | App als Gruppierung | Content, Pages, Workflows gehoeren immer zu einer App |
| 26 | App Dependencies statt Shared Content | Apps referenzieren andere Apps explizit, kein Shared-Scope auf Tenant-Ebene |
| 27 | Version auf App (ohne Constraints) | Semver-Feld jetzt, Version-Constraints kommen mit Marketplace |
| 28 | shadcn/ui fuer Component Library | CSS Variables ideal fuer Whitelabel Runtime-Theming |
| 29 | Nx Workspace | Monorepo fuer Frontend + Backend, Gradle Plugin fuer Java |
| 30 | Orval fuer API Client Generation | React Query Hooks + Zod Schemas + Types aus OpenAPI Spec |
| 31 | Facade Pattern (Custom Hooks) | Orchestrierung wie Angular Facades, Pages bleiben duenn |
| 32 | Kein Tenant-Slug im URL-Pfad | Tenant aus JWT/Subdomain/Switcher, saubere URLs |
| 33 | Ein Keycloak Theme, dynamisch | Realm-Attributes fuer Tenant-Branding auf Login-Seite |
| 34 | Eigener User Invite Flow | Whitelabel-Branding in Emails, Rolle vorbelegen, Invite-Historie |
| 35 | Nx dev als Single Command | Ein Befehl startet gesamte Entwicklungsumgebung |
| 36 | Traefik fuer lokale Subdomains | *.localhost, automatische Container-Erkennung |
| 37 | Mailpit fuer Email Testing | SMTP-Empfaenger lokal, UI zum Inspizieren |
| 38 | DevDataSeeder statt Flyway fuer Test-Daten | Befuellt DB + Keycloak + OpenFGA + S3 im dev Profil |
| 39 | Teal + Slate als Default-Farben | Frisch, leicht, keine Signalfarben-Kollision |
| 40 | Geist Sans/Mono | Fuer Next.js optimiert, modern |
| 41 | Playwright fuer E2E Tests | Gegen Dev-Umgebung, nutzt DevDataSeeder-Daten |
| 42 | Storybook fuer Component Tests | Nur packages/ui, co-located Stories |
| 43 | Lucide Icons | shadcn-nativ |
| 44 | next-intl fuer UI i18n | App Router Integration, User-Setting |
| 45 | Eigene Translation-Tabellen pro Entity | App, Content Definition, Content Field |
| 46 | Content Entry i18n spaeter | Vorerst ausgeschlossen, nachruestbar |
| 47 | TenantInformation statt TenantId | Record mit tenantId + platformAdmin, erweiterbar fuer Feature Flags etc. |
| 48 | UUIDv7 fuer alle Entity IDs | Zeitbasiert, sortierbar, bessere Index-Performance als UUIDv4 |
| 49 | Zweistufige Base Entity Hierarchie | AuditableBaseEntity (Tenant) -> TenantAwareBaseEntity (alles andere) |
| 50 | AuditListener statt MappedSuperclass-Logik | Zentral, erweiterbar fuer History/Changelog |
| 51 | Kein FK fuer createdBy/updatedBy | User kann geloescht werden ohne Audit-Eintraege zu beeinflussen |
| 52 | PLATFORM_ADMIN nur fuer RLS Bypass | Einzige Keycloak-Rolle, alles andere ueber OpenFGA |
| 53 | Subdomain ist Wahrheit fuer Tenant | JWT-Tenant wird gegen Subdomain validiert, Mismatch -> Redirect |
| 54 | i18n Keys in Validation Errors | Backend sprachunabhaengig, Frontend uebersetzt |
| 55 | RFC 7807 + code Feld | Problem Detail Standard, code fuer Dokumentationsverknuepfung |
| 56 | Zentraler SMTP Phase 1, Tenant-SMTP Phase 2 | Erstmal nur From-Adresse pro Tenant, eigener SMTP spaeter |
| 57 | Tabellennamen Singular snake_case | `app`, `content_definition`, `tenant_user` |
| 58 | Soft Delete auf allen Tabellen | `deleted_at` / `deleted_by` in AuditInfo, Filterung im Application Layer |
| 59 | Flyway mit Datum-Nummerierung | `V20260302_001__create_tenant_table.sql` |
| 60 | Enums als VARCHAR | Kein PostgreSQL Enum, einfacher zu migrieren |
| 61 | TIMESTAMPTZ fuer alle Timestamps | Immer mit Zeitzone, immer UTC |
| 62 | RLS nur fuer Tenant-Isolation | Kein Soft Delete Filter in RLS Policy |
| 63 | Plural in API URLs, Singular in DB | REST-Standard `/apps` vs DB `app` |
| 64 | Immer Wrapper-Objekt fuer Listen | Konsistent, Pagination nachruestbar ohne Breaking Change |
| 65 | 201 + Location Header + Body | Create-Endpoints geben erstellte Ressource zurueck |
| 66 | Eine OpenAPI Spec pro Domain-Modul | `tenant-api.yaml`, `content-api.yaml` etc. |

## Naechste Schritte

- [x] Tenant Domain im Detail besprochen
- [x] Identity & Access Domain im Detail besprochen
- [x] Platform UI Domain im Detail besprochen
- [x] Local Development Setup besprochen
- [x] Shared Kernel definieren, siehe `07-shared-kernel.md`
- [x] OpenFGA Authorization Model definieren, siehe `03-identity-access-domain.md`
- [x] DB-Schema grobe Regeln festlegen, siehe `08-db-schema-regeln.md`
- [x] OpenAPI Spec grobe Regeln festlegen, siehe `09-openapi-spec-regeln.md`
- [ ] Implementierung starten
- [ ] Page Builder Domain im Detail besprechen
- [ ] Workflow Domain im Detail besprechen
- [x] Content Domain im Detail besprochen
