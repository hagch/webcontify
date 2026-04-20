# Webcontify - Platform UI

## Ueberblick

Die Platform UI ist die zentrale Verwaltungsoberflaeche fuer die gesamte Plattform. Sie deckt Tenant-Verwaltung, App-Management, Content Definition/Management, User/Rollen-Verwaltung und Tenant-Konfiguration ab.

Es gibt **eine einzige UI-Applikation** mit rollenbasierter Sichtbarkeit — kein separates Admin-Panel.

## Tech Stack

| Bereich | Technologie |
|---|---|
| Framework | Next.js (App Router) |
| Component Library | shadcn/ui (Radix-basiert, CSS Variables fuer Whitelabel) |
| Server State | TanStack React Query |
| Client State | Zustand |
| Forms | React Hook Form |
| Validierung | Zod (generiert aus OpenAPI Spec) |
| API Client | Generiert aus OpenAPI Spec (Orval) |
| Tables | TanStack Table + shadcn DataTable |
| Toasts | Sonner (shadcn Integration) |
| Icons | Lucide (shadcn-nativ) |
| i18n | next-intl |
| Monorepo | Nx Workspace (Frontend + Backend in einem Repo) |

## BFF-Pattern

**Thin BFF** als Ausgangspunkt: Next.js leitet API-Calls durch und fuegt Auth-Header hinzu. Frontend-Komponenten nutzen direkt den generierten API-Client. Fat BFF nur dort wo es echten Mehrwert bringt (z.B. Dashboard-Aggregation).

## Monorepo Struktur

```
root/
├── packages/
│   └── ui/                          ← @webcontify/ui (wiederverwendbare Component Library)
│       ├── src/
│       │   ├── components/
│       │   │   ├── button.tsx
│       │   │   ├── input.tsx
│       │   │   ├── dialog.tsx
│       │   │   ├── data-table.tsx   ← TanStack Table Wrapper
│       │   │   ├── form-field.tsx   ← RHF + shadcn Form Feld
│       │   │   ├── confirm-dialog.tsx
│       │   │   ├── search-input.tsx
│       │   │   ├── page-header.tsx
│       │   │   ├── empty-state.tsx
│       │   │   └── ...
│       │   ├── lib/
│       │   │   └── utils.ts
│       │   └── index.ts             ← Public API / Exports
│       └── tailwind.config.ts
│
├── apps/
│   ├── platform/                    ← Next.js Platform UI
│   │   └── src/ (siehe Dateistruktur unten)
│   └── backend/                     ← Spring Modulith (Gradle, io.webcontify)
│
├── nx.json
├── build.gradle.kts
└── pnpm-workspace.yaml
```

Nx verwaltet das gesamte Projekt — Java Backend (via Gradle Plugin) und Next.js Frontend. Caching, Task-Dependencies und Affected-Builds ueber Nx.

### Entscheidungsregel: Was kommt wohin?

| Frage | Ort |
|---|---|
| Wird es in mehreren Apps/Pages genutzt? | `packages/ui` |
| Ist es ein shadcn/ui Basis-Komponente? | `packages/ui` |
| Wird es NUR in einer Page genutzt? | `_components/` neben der Page |
| Ist es ein Layout-Teil (Sidebar, Header)? | `apps/platform/components/layout/` |

## Dateistruktur Platform App

```
apps/platform/src/
├── app/                              ← Next.js App Router (Pages)
│   ├── (auth)/
│   │   ├── login/page.tsx
│   │   └── callback/page.tsx
│   │
│   ├── (platform)/                   ← Platform-Admin Bereich
│   │   ├── layout.tsx                ← Guard: nur Platform-Admins
│   │   └── tenants/
│   │       ├── page.tsx              ← Tenant-Liste
│   │       └── [tenantId]/
│   │           ├── page.tsx          ← Tenant-Detail
│   │           └── settings/page.tsx ← Tenant konfigurieren
│   │
│   ├── (workspace)/                  ← Tenant-Kontext (aus JWT/Subdomain)
│   │   ├── layout.tsx                ← Guard: Tenant aus JWT resolved + Theme
│   │   ├── dashboard/page.tsx
│   │   ├── apps/
│   │   │   ├── page.tsx              ← App-Liste
│   │   │   └── [appSlug]/
│   │   │       ├── page.tsx          ← App-Dashboard
│   │   │       ├── content-definitions/
│   │   │       │   ├── page.tsx      ← Definitions-Liste dieser App
│   │   │       │   └── [definitionSlug]/
│   │   │       │       ├── page.tsx  ← Definition bearbeiten (Schema Builder)
│   │   │       │       └── entries/
│   │   │       │           ├── page.tsx       ← Entries-Liste
│   │   │       │           └── [entryId]/
│   │   │       │               └── page.tsx   ← Entry bearbeiten
│   │   │       └── settings/
│   │   │           └── page.tsx      ← App-Settings, Dependencies
│   │   ├── users/page.tsx            ← User-Verwaltung
│   │   ├── roles/page.tsx            ← Rollen & Berechtigungen
│   │   └── settings/
│   │       ├── branding/page.tsx     ← Tenant Branding
│   │       ├── smtp/page.tsx         ← SMTP Konfiguration
│   │       └── domain/page.tsx       ← Custom Domain
│   │
│   └── layout.tsx                    ← Root Layout (Auth Provider, QueryClient)
│
├── facades/                          ← Custom Hooks (Facade Pattern)
│   ├── use-apps-facade.ts
│   ├── use-content-definitions-facade.ts
│   ├── use-content-entries-facade.ts
│   ├── use-users-facade.ts
│   ├── use-roles-facade.ts
│   └── use-tenant-settings-facade.ts
│
├── stores/                           ← Zustand Stores
│   ├── ui.store.ts                   ← Sidebar, Theme (light/dark)
│   └── tenant-context.store.ts       ← Aktiver Tenant (fuer Platform-Admin Switcher)
│
├── generated/                        ← Orval Output (NICHT manuell editieren)
│   ├── api-client.ts
│   ├── model.ts
│   ├── hooks.ts
│   └── zod-schemas.ts
│
├── components/
│   └── layout/                       ← App-spezifische Layout-Komponenten
│       ├── sidebar.tsx
│       ├── header.tsx
│       ├── tenant-switcher.tsx
│       └── breadcrumbs.tsx
│
├── lib/
│   ├── auth.ts                       ← Keycloak Integration
│   ├── theme.ts                      ← Tenant-Theme Handling
│   └── utils.ts
│
└── types/                            ← Zusaetzliche Types (nicht generiert)
```

## Tenant-Erkennung (kein Slug im Pfad)

Kein `/t/[tenantSlug]/` in der URL. Tenant-Kontext wird implizit resolved:

| Nutzer | Tenant-Erkennung |
|---|---|
| Tenant-Admin | JWT Claim `tenant_id` — sieht nur seinen Tenant |
| Platform-Admin | Tenant-Switcher im Header — kann zwischen Tenants wechseln |
| Whitelabel | Custom Domain → Tenant-Resolution (z.B. `admin.kunde-domain.com`) |

URLs bleiben sauber: `app.platform.com/apps/...` statt `app.platform.com/t/acme/apps/...`

## Schichtenarchitektur Frontend

```
┌──────────────────────────────────────────────┐
│         Pages / Routes                        │  Duenn, nur Layout + Komposition
├──────────────────────────────────────────────┤
│         Facades (Custom Hooks)                │  Orchestrierung, Logik
│    useAppsFacade                              │
│    useContentDefinitionsFacade                │
│    useUserManagementFacade                    │
├──────────────────────────────────────────────┤
│     React Query Hooks (generiert via Orval)   │  Server State + API Calls
│     useGetApps, useCreateApp, ...             │
├──────────────────────────────────────────────┤
│     Zustand Stores                            │  Globaler Client State
│     uiStore, tenantContextStore               │
├──────────────────────────────────────────────┤
│     API Client (generiert via Orval)          │  HTTP Layer
├──────────────────────────────────────────────┤
│     @webcontify/ui                            │  Presentational Components
└──────────────────────────────────────────────┘
```

### State Management Regeln

| Frage | Antwort |
|---|---|
| Kommt es vom Server? | React Query |
| Ist es UI-State der ueber Komponenten geteilt wird? | Zustand |
| Ist es Form-State? | React Hook Form |
| Ist es State einer einzelnen Komponente? | useState |

## API Client Generation Pipeline

```
Backend OpenAPI Spec (YAML/JSON)
    ↓ (Build-Step / CI via Nx)
Orval Code Generator
    ↓
Generierte Artefakte:
├── api-client.ts        ← Typisierte Fetch-Funktionen
├── model.ts             ← TypeScript Interfaces/Types
├── zod-schemas.ts       ← Zod Schemas (synchron mit Backend-Validierung)
└── hooks.ts             ← React Query Hooks (useGetTenants, useCreateApp, etc.)
```

Frontend- und Backend-Validierung sind automatisch synchron, da beide aus derselben OpenAPI-Spec generiert werden.

## Form-Pattern

### Generierter Flow

```
OpenAPI Spec → Orval → Zod Schema (generiert)
                              ↓
                    React Hook Form + zodResolver
                              ↓
                    shadcn Form-Komponenten
                              ↓
                    Submit → generierter Mutation Hook → API Call
```

### Beispiel: App erstellen

```typescript
// Generiert aus OpenAPI Spec
const createAppSchema = z.object({
  name: z.string().min(1).max(100),
  slug: z.string().regex(/^[a-z0-9-]+$/),
  description: z.string().optional(),
})

// Manuell geschrieben
function CreateAppForm() {
  const form = useForm({
    resolver: zodResolver(createAppSchema),
  })
  const { mutate, isPending } = useCreateApp()

  return (
    <Form {...form}>
      <FormField name="name" label="Name" />
      <FormField name="slug" label="Slug" />
      <FormField name="description" label="Beschreibung" />
      <Button loading={isPending}>Erstellen</Button>
    </Form>
  )
}
```

## Error Handling

Zwei Fehlerarten, zwei Wege:

### Field-Fehler → unter dem Feld anzeigen

Backend liefert Fehler mit Field-Mapping:

```json
{
  "status": 422,
  "message": "Validation failed",
  "errors": [
    { "field": "slug", "message": "Slug already exists" }
  ]
}
```

Facade leitet an React Hook Form weiter via `form.setError(field, { message })`.

### Generelle Fehler → Toast Notification

Fehler ohne Field-Mapping (z.B. 500, Netzwerkfehler) werden als Toast angezeigt via Sonner.

### Backend Error Response Format

```json
{
  "status": number,
  "message": "Human-readable message",
  "errors": [
    { "field": "fieldName", "message": "Field-specific error" }
  ]
}
```

`errors` Array leer oder absent → Toast. `errors` mit Eintraegen → Field-Fehler setzen.

## Facade Pattern (Custom Hooks)

Angelehnt an Angular Facades — kapselt Orchestrierung von Server State, Client State und Aktionen:

```typescript
function useAppsFacade() {
  // Server State
  const { data: apps, isLoading } = useGetApps()
  const createMutation = useCreateApp()
  const deleteMutation = useDeleteApp()

  // Client State
  const [filter, setFilter] = useState('')

  // Derived State
  const filteredApps = useMemo(
    () => apps?.filter(a => a.name.includes(filter)),
    [apps, filter]
  )

  // Error Handling in Actions
  const createApp = (data: CreateAppRequest, form: UseFormReturn) => {
    createMutation.mutate(data, {
      onError: (error) => {
        if (error.errors?.length) {
          error.errors.forEach(e =>
            form.setError(e.field, { message: e.message })
          )
        } else {
          toast.error(error.message)
        }
      }
    })
  }

  return {
    apps: filteredApps,
    isLoading,
    filter,
    setFilter,
    createApp,
    isCreating: createMutation.isPending,
  }
}
```

## Webcontify Default Theme

### Farben

```
Light Mode:
  --primary:         #14B8A6 (Teal 500)
  --primary-hover:   #0D9488 (Teal 600)
  --secondary:       #64748B (Slate 500)
  --secondary-hover: #475569 (Slate 600)
  --background:      #FFFFFF
  --surface:         #F0FDFA (Teal 50)

Dark Mode:
  --primary:         #2DD4BF (Teal 400)
  --primary-hover:   #14B8A6 (Teal 500)
  --secondary:       #94A3B8 (Slate 400)
  --secondary-hover: #64748B (Slate 500)
  --background:      #042F2E (Teal 950)
  --surface:         #134E4A (Teal 900)

Signalfarben (fix, nicht per Tenant aenderbar):
  --success:         #10B981 (Emerald 500)
  --warning:         #F59E0B (Amber 500)
  --error:           #EF4444 (Red 500)
  --info:            #0EA5E9 (Sky 500)
```

### Fonts

| Verwendung | Font |
|---|---|
| UI / Body | Geist Sans (via `next/font`) |
| Mono / Code | Geist Mono |

### Defaults

```
Border Radius: 0.5rem (medium)
```

## Runtime Tenant-Theming

### Konzept

Tenant-Branding wird aus dem Backend geladen und per CSS Custom Properties auf den Container angewendet. shadcn-Komponenten reagieren automatisch.

### Theming-Umfang

**Simple Mode (Default):**

| Setting | Beschreibung |
|---|---|
| Primary Color | Hauptfarbe (Buttons, Links, Akzente) |
| Logo | Upload oder URL |
| Favicon | Upload |

**Advanced Mode (Toggle):**

| Setting | Beschreibung |
|---|---|
| Secondary Color | Zweite Akzentfarbe |
| Background Color | Hintergrund |
| Font Family | Google Fonts oder Custom |
| Border Radius | Rundung (sharp → rounded) |
| Sidebar Style | Hell / Dunkel / Accent |
| Custom CSS | Escape Hatch fuer Power User |

### Umsetzung

```
Tenant-Admin betritt Workspace
    ↓
Layout laedt GET /api/v1/tenants/current/branding
    ↓
Response: { primaryColor, logo, ... }
    ↓
CSS Custom Properties werden auf Container gesetzt
    ↓
shadcn-Komponenten nutzen die Variables automatisch
```

Theme-Farben existieren jeweils in einer Light- und Dark-Variante.

### Dark Mode

- Immer verfuegbar fuer alle Tenants (kein Tenant-Setting)
- User-Setting mit drei Optionen: System (Default), Light, Dark
- System-Default uebernimmt `prefers-color-scheme` vom OS
- Gespeichert in localStorage, optional Backend-Sync im User-Profil

## Keycloak Login Theming

Ein einziges Keycloak Base-Theme fuer alle Tenants, dynamisch ueber Realm-Attributes:

```
Keycloak Theme (einmalig erstellt)
├── login.ftl        ← Template mit ${realm.attributes.logo} etc.
├── styles.css       ← CSS Variables aus Realm-Attributes
└── ...

Realm "acme-corp" (Attributes)
├── brandLogo = "https://s3.../acme/logo.png"
├── brandPrimary = "220 90% 56%"
└── brandRadius = "0.5rem"
```

Bei Tenant-Provisioning werden die Realm-Attributes gesetzt. Bei Branding-Aenderungen werden sie aktualisiert.

## User Invitations

Eigener Invite-Flow (nicht Keycloak-Standard), da volle Kontrolle ueber Branding noetig.

### Flow

```
Tenant-Admin klickt "User einladen"
    ↓
Platform Backend erstellt Invite-Token (DB, TTL)
    ↓
Invite-Email ueber Tenant-SMTP mit Tenant-Branding
    ├── Logo aus Tenant Branding Config
    ├── Tenant-Name ("Du wurdest zu [Tenant] eingeladen")
    ├── Primary Color (Button-Farbe)
    ├── Einladender Name
    ├── Rolle (optional: "als Editor")
    ├── CTA Button → Link zur Registrierung
    └── Ablaufdatum ("Dieser Link ist 7 Tage gueltig")
    ↓
User klickt Link → Registrierungsseite
    ↓
User registriert sich → Keycloak User wird erstellt
    ↓
Invite-Token wird invalidiert
```

### Architektur

Lebt in der Identity & Access Domain:

```
Identity & Access Domain
├── User Management (CRUD → Keycloak)
├── Role Assignment (→ OpenFGA)
└── User Invitations
    ├── Invite erstellen
    ├── Invite-Email senden (Tenant SMTP + Branding)
    ├── Invite einloesen
    └── Invite-Status / Historie

Shared Kernel
└── Email Service
    ├── Template Rendering (mit Tenant-Branding)
    └── SMTP Sending (Tenant-Config)
```

### Password Reset

Bleibt vorerst Keycloak-Standard (eingebauter Flow). SMTP-Settings des Tenants werden im Keycloak Realm konfiguriert, sodass Emails vom richtigen Absender kommen.

## Navigation

```
Sidebar (Workspace):
├── Dashboard
├── Apps
│   ├── Blog App
│   │   ├── Content Definitions
│   │   │   ├── Article
│   │   │   ├── Category
│   │   │   └── Author
│   │   └── Settings
│   ├── Shop App
│   │   ├── Content Definitions
│   │   │   ├── Product
│   │   │   └── Order
│   │   └── Settings
│   └── + Neue App
├── Users & Roles
└── Tenant Settings
    ├── Branding
    ├── SMTP
    └── Domain

Sidebar (Platform-Admin, zusaetzlich):
├── Tenants
│   ├── Tenant-Liste
│   └── Tenant erstellen
└── System Settings
```

## Guards / Zugriffskontrolle

Guards laufen in den `layout.tsx` Dateien:

| Layout | Guard |
|---|---|
| Root Layout | Auth Check (Keycloak Session vorhanden?) → sonst Redirect /login |
| Platform Layout | Auth + Platform-Admin Rolle (OpenFGA) → sonst 403 |
| Workspace Layout | Auth + Tenant-Zugehoerigkeit + Theme laden → sonst 403 |

## i18n (Internationalisierung)

### Zwei Ebenen

| Ebene | Was | Tooling |
|---|---|---|
| Platform UI | Statische Texte (Labels, Buttons, Fehlermeldungen) | next-intl |
| Domain-Daten | App-Namen, Definition-Labels, Field-Beschreibungen | Eigene Translation-Tabellen (Backend) |

Content Entry i18n ist vorerst ausgeschlossen — kann spaeter nachgeruestet werden.

### Platform UI i18n (next-intl)

```
apps/platform/
└── messages/
    ├── en.json                 ← Default
    ├── de.json
    └── ...
```

- Sprache = User-Setting (wie Dark Mode)
- Default: Browser-Sprache
- next-intl integriert sich nativ mit App Router + Server Components

### Domain-Daten i18n (Translation-Tabellen)

Eigene Translation-Tabelle pro Entity (passt zu JPA, typsicher):

```sql
CREATE TABLE app_translations (
    app_id       UUID         NOT NULL REFERENCES apps(id),
    locale       VARCHAR(5)   NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    PRIMARY KEY (app_id, locale)
);

CREATE TABLE content_definition_translations (
    definition_id UUID        NOT NULL REFERENCES content_definitions(id),
    locale        VARCHAR(5)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    PRIMARY KEY (definition_id, locale)
);

CREATE TABLE content_field_translations (
    field_id     UUID         NOT NULL REFERENCES content_fields(id),
    locale       VARCHAR(5)   NOT NULL,
    label        VARCHAR(255) NOT NULL,
    placeholder  VARCHAR(255),
    help_text    TEXT,
    PRIMARY KEY (field_id, locale)
);
```

### Tenant Locale-Konfiguration

```
Tenant Config:
├── Available Locales: ["en", "de", "fr"]
├── Default Locale: "en"
└── Fallback: true/false (zeige Default wenn Uebersetzung fehlt)
```

### API

```
GET /api/v1/apps?locale=de
GET /api/v1/apps?locale=en&fallback=true
```

### UI: Locale-Switcher

Globaler Locale-Switcher im Header — gilt fuer Platform UI Texte UND Domain-Daten gleichzeitig.

### Was ist uebersetzbar?

| Entity | Felder | Tabelle |
|---|---|---|
| App | name, description | `app_translations` |
| Content Definition | name, description | `content_definition_translations` |
| Content Field | label, placeholder, help_text | `content_field_translations` |

## Testing

### Storybook (Component Tests) — nur `packages/ui`

Stories leben co-located neben der Komponente:

```
packages/ui/
├── .storybook/
│   ├── main.ts
│   └── preview.ts              ← Theme Provider, Decorators
└── src/components/
    ├── button.tsx
    ├── button.stories.tsx      ← Story neben Komponente
    ├── data-table.tsx
    ├── data-table.stories.tsx
    └── ...
```

Storybook bekommt einen Theme-Switcher Decorator:

```
Storybook Toolbar:
├── Theme: Default (Teal) | Beispiel-Tenant
├── Mode: Light | Dark
└── Viewport: Desktop | Tablet | Mobile
```

Page-spezifische Komponenten (`_components/`) werden NICHT in Storybook aufgenommen — diese werden durch E2E Tests abgedeckt.

### Playwright (E2E Tests)

```
apps/platform/
└── e2e/
    ├── playwright.config.ts
    ├── fixtures/
    │   ├── auth.fixture.ts         ← Login als Platform-Admin / Tenant-Admin
    │   └── tenant.fixture.ts       ← Tenant-Kontext Setup
    ├── tests/
    │   ├── auth/
    │   │   └── login.spec.ts
    │   ├── apps/
    │   │   ├── create-app.spec.ts
    │   │   └── app-dependencies.spec.ts
    │   ├── content/
    │   │   ├── create-definition.spec.ts
    │   │   └── manage-entries.spec.ts
    │   ├── users/
    │   │   └── invite-user.spec.ts
    │   └── settings/
    │       └── branding.spec.ts
    └── helpers/
```

Tests laufen gegen die laufende Dev-Umgebung und nutzen die DevDataSeeder-Daten (Test-Tenant "Acme", Platform-Admin).

### Nx Test Targets

| Befehl | Beschreibung |
|---|---|
| `nx storybook ui` | Storybook Dev Server fuer `@webcontify/ui` |
| `nx build-storybook ui` | Storybook Static Build |
| `nx e2e platform` | Playwright E2E Tests |
| `nx e2e platform --ui` | Playwright mit UI Mode (interaktiv) |

## Entscheidungslog

| # | Entscheidung | Begruendung |
|---|---|---|
| 1 | shadcn/ui | CSS Variables ideal fuer Whitelabel Runtime-Theming, Radix-basiert, Copy-Paste-Ownership |
| 2 | Zustand + React Query | Klare Trennung Server State (RQ) vs. Client State (Zustand) |
| 3 | Zod + React Hook Form | Zod-Schemas generiert aus OpenAPI = sync mit Backend, RHF performant |
| 4 | Orval fuer API Client | Generiert React Query Hooks + Zod Schemas + Types aus OpenAPI Spec |
| 5 | Thin BFF | Einfach starten, Fat BFF nur wo noetig (z.B. Dashboard-Aggregation) |
| 6 | Eine UI, rollenbasiert | Shared Code, weniger Deployment-Komplexitaet, konsistente UX |
| 7 | Facade Pattern (Custom Hooks) | Kapselt Orchestrierung wie Angular Facades, Pages bleiben duenn |
| 8 | Kein Tenant-Slug im Pfad | Tenant aus JWT/Subdomain/Switcher, saubere URLs |
| 9 | Page-spezifische Komponenten co-located | `_components/` neben der Page (Next.js Convention) |
| 10 | Wiederverwendbare Komponenten in Library | `packages/ui` fuer spaetere Wiederverwendung (Published Sites etc.) |
| 11 | Nx Workspace | Monorepo fuer Frontend + Backend, Gradle Plugin fuer Java |
| 12 | Theming: Simple + Advanced Mode | Simple als Default (Farbe, Logo), Advanced toggle fuer Power User |
| 13 | Dark Mode: User-Setting, System-Default | Immer verfuegbar, Tenant hat keinen Einfluss |
| 14 | Ein Keycloak Theme, dynamisch per Realm-Variablen | Wartbar, skaliert, Branding ueber Realm-Attributes |
| 15 | Eigener User Invite Flow | Volle Kontrolle ueber Branding, Rolle vorbelegen, Invite-Historie |
| 16 | Field-Fehler unter Feld, Rest als Toast | Konsistentes Error Handling Pattern, Sonner fuer Toasts |
| 17 | Content Definitions unter App | UI spiegelt Domain: Definitions gehoeren immer zu einer App |
| 18 | Teal + Slate als Default-Farben | Teal = frisch/leicht, Slate = neutral/zurueckhaltend, keine Signalfarben-Kollision |
| 19 | Geist Sans/Mono als Default-Font | Fuer Next.js optimiert, modern, Zero Layout Shift via next/font |
| 20 | Playwright fuer E2E Tests | Laeuft gegen Dev-Umgebung, nutzt DevDataSeeder-Daten |
| 21 | Storybook nur fuer packages/ui | Component Tests + Dokumentation, Theme-Switcher Decorator |
| 22 | Lucide Icons | shadcn-nativ, kein extra Dependency |
| 23 | next-intl fuer Platform UI i18n | Nativ mit App Router, User-Setting fuer Sprache |
| 24 | Eigene Translation-Tabellen pro Entity | Typsicher, passt zu JPA, kein generischer Ansatz |
| 25 | Content Entry i18n spaeter | Vorerst ausgeschlossen, nachruestbar |
