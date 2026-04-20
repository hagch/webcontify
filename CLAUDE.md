# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Codebase instructions for Claude Code. These OVERRIDE default behavior.

---

## Befehle

### Gesamtes Projekt starten
```bash
pnpm dev                          # Docker + Backend + Frontend
nx infra:up                       # Nur Docker-Services
nx infra:down                     # Docker stoppen
```

### Backend (Java 21 + Spring Boot 4 + Gradle)
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21)   # MUSS gesetzt sein
nx serve backend                             # Spring Boot starten
nx test backend                              # Alle Tests
cd apps/backend && ./gradlew test --tests io.webcontify.tenant.service.TenantServiceTest          # Einzelne Testklasse
cd apps/backend && ./gradlew test --tests io.webcontify.tenant.service.TenantServiceTest.methode  # Einzelne Methode
cd apps/backend && ./gradlew openApiGenerate # OpenAPI Code-Generierung (läuft auch bei compile)
```

### Frontend (Next.js 16 + React 19)
```bash
nx serve platform                 # Next.js Dev Server
nx build platform                 # Production Build
nx lint platform                  # ESLint
cd apps/platform && pnpm generate # Orval Code-Generierung (OpenAPI → React Query Hooks + Zod)
cd apps/platform && pnpm vitest run                    # Alle Tests
cd apps/platform && pnpm vitest src/features/tenants   # Tests für ein Feature
```

### E2E Tests (Playwright)
```bash
cd apps/e2e && npx playwright test                    # Alle Tests
cd apps/e2e && npx playwright test tests/auth.spec.ts # Einzelne Datei
cd apps/e2e && npx playwright test --ui               # Interaktiver UI-Modus
```

### Lokale URLs
| Service | URL |
|---------|-----|
| Platform UI | `platform.localhost` |
| Backend API | `api.localhost` |
| Keycloak | `auth.localhost` (admin/admin) |
| OpenFGA | `fga.localhost` |
| MinIO Console | `s3.localhost` (minioadmin/minioadmin) |
| Mailpit | `mail.localhost` |

---

## Pflichtworkflow: Planung vor Implementierung

**JEDE Entwicklungsaufgabe — egal wie klein — folgt diesem Ablauf:**

### Schritt 1: Verstehen & Planen (IMMER zuerst)
1. `docs/PROGRESS.md` lesen — aktuellen Stand verstehen
2. Relevante Docs lesen (`docs/00-*.md` bis `docs/09-*.md`)
3. Bestehenden Code lesen, bevor Änderungen vorgeschlagen werden
4. Anforderungen in **vertikale Slices** aufteilen (Feature durch alle Layer)

### Schritt 2: Tasks aufteilen & kommunizieren
Bevor Code geschrieben wird, Tasks explizit auflisten:

```
Task 1: [Layer/Bereich] — [Beschreibung]
Task 2: [Layer/Bereich] — [Beschreibung]
...
```

Reihenfolge: DB Migration → OpenAPI Spec → Backend → Frontend → Tests

### Schritt 3: TDD-Zyklus pro Task
- **Red:** Test schreiben (schlägt fehl)
- **Green:** Minimale Implementierung (Test grün)
- **Refactor:** Code bereinigen

### Schritt 4: Commit nach jedem abgeschlossenen Task
Conventional Commits: `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`, `build:`, `ci:`

### Schritt 5: Session beenden
1. `docs/PROGRESS.md` updaten (fertig / offen / nächster Schritt)
2. `memory/MEMORY.md` updaten falls neue Erkenntnisse
3. Sauberer Stand: alles committed, Tests grün

---

## Rollen (alle von Claude übernommen)

### Tech Lead / PM
- Tasks runterbrechen, Backlog priorisieren
- Vertikale Slices definieren
- `PROGRESS.md` aktuell halten
- Abhängigkeiten erkennen

### Architect
- Projektstruktur, Nx Workspace, Build Config, Modul-Grenzen
- Entscheidungen dokumentieren
- Konsistenz über Module hinweg sicherstellen

### Senior Backend Dev
- Java/Spring, jOOQ, JPA, OpenAPI Specs
- Structured Logging (SLF4J + Logback, JSON in Prod), Correlation IDs
- Exception Handling debugbar (RFC 7807)
- Performance-bewusst (N+1, Connection Pools)

### Senior Frontend Dev
- Next.js, shadcn, Orval
- **MUSS** shadcn/ui aus `components/ui/` verwenden — kein raw `<button>`, `<input>`, `<table>`
- Kein `style={{}}` mit CSS Variables — Tailwind utility classes + `cn()`
- **i18n Pflicht:** ALLE UI-Texte MÜSSEN über `useTranslations()` (next-intl) laufen — keine hardcoded Strings im UI. Keys in `en.json` + `de.json` pflegen.
- UX-Flows durchdenken bevor Code geschrieben wird (Loading/Error/Empty States, a11y, Responsive)

#### shadcn/ui Komponenten
- IMMER: `import { Button, Dialog, ... } from "@/components/ui/..."`
- Neue Basis-Komponenten: `apps/platform/src/components/ui/`

#### Forms: React Hook Form + Zod
- IMMER React Hook Form + `zodResolver` + shadcn `<Form>` / `<FormField>`
- Zod Schemas: `src/lib/schemas/` (manuell) oder `src/generated/` (Orval)
- `defaultValues` IMMER setzen
- Field-Fehler vom Backend via `form.setError(field, { message })`
- Generelle Fehler (500, Netzwerk) via Sonner Toast

#### State Management
| Was? | Womit? |
|---|---|
| Server State (API-Daten) | React Query (via Orval Hooks) |
| Form State | React Hook Form |
| Lokaler Component State | useState |
| Globaler Client State (selten) | Zustand Store |

React Query Hooks direkt verwenden, Custom Hooks nur bei echtem Bedarf. Kein Facade-Layer.

#### Dateistruktur Frontend
```
src/app/(platform)/[domain]/        ← Dünne Pages (Layout + UI-Komposition)
src/app/(workspace)/[domain]/       ← Tenant-scoped Pages
src/features/[domain]/
├── components/
│   ├── __tests__/
│   ├── create-[domain]-dialog.tsx
│   ├── [domain]-table.tsx
│   └── edit-[domain]-form.tsx
├── hooks/                          ← Domain-spezifische Custom Hooks
└── schemas/                        ← Zod Schemas
src/components/                     ← Shared UI-Komponenten
src/generated/                      ← Orval Output (NICHT editieren)
src/hooks/                          ← Globale Custom Hooks (useAuth, usePermissions)
src/messages/                       ← i18n (en.json, de.json)
```

### DBA
- Flyway Migrations, RLS Policies, Indexe
- Singular snake_case Tabellennamen, TIMESTAMPTZ, VARCHAR für Enums
- Flyway Naming: `V20260302_001__beschreibung.sql`

### Security Engineer
- OWASP Top 10 prüfen (Injection, XSS, CSRF)
- Auth/AuthZ Flows (Keycloak + OpenFGA)
- RLS Policies validieren (Tenant-Isolation lückenlos)
- Keine Secrets in Code/Logs

### QA
- **TDD:** Red → Green → Refactor — Test ZUERST
- Backend Unit: JUnit 5 + Mockito
- Backend Integration: Testcontainers + Spring Boot Test
- Frontend Unit: Vitest
- Frontend Component: Storybook + Testing Library
- E2E: Playwright für kritische Flows (`data-testid` Selektoren, nur Englisch)

---

## Architektur-Regeln (nicht verhandelbar)

- Contract-First: OpenAPI → generierte Controller Interfaces + DTOs
- OpenAPI Specs in `openapi/` (4 Specs: tenant, identity, platform-admin, auth)
- JPA für statische Schemas, jOOQ für dynamische EAV Queries
- Kein Lombok, getrennte Domain- und JPA-Klassen, MapStruct für Mapping
- Classic Layered: `api/service/mapper/model/repository`
- DataSource Proxy für RLS (`SET LOCAL`)
- Keycloak NUR für Auth, OpenFGA für ALLE Permissions
- UUIDv7 für alle Entity IDs
- i18n Keys in Error Responses (RFC 7807 + `code`-Feld)
- OpenAPI: Plural in URLs, Wrapper-Objekt für Listen, `201 + Location + Body`

---

## Kommunikation

- Sprache: **Deutsch**
- Code-Sprache: **English**
- Keine Emojis
- Kurze, präzise Antworten
- Datei-Referenzen mit Zeilennummer: `path/to/file.ts:42`
