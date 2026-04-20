# CLAUDE.md — Webcontify

Codebase instructions for Claude Code. These OVERRIDE default behavior.

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
- Next.js, shadcn, Orval, Facades
- **MUSS** shadcn/ui aus `@webcontify/ui` verwenden — kein raw `<button>`, `<input>`, `<table>`
- Kein `style={{}}` mit CSS Variables — Tailwind utility classes + `cn()`
- UX-Flows durchdenken bevor Code geschrieben wird (Loading/Error/Empty States, a11y, Responsive)

#### shadcn/ui Komponenten
- IMMER: `import { Button, Dialog, ... } from "@webcontify/ui"`
- Neue Basis-Komponenten: `packages/ui/src/components/` + Export in `index.ts`
- Storybook Story pflichtmäßig für jede neue Komponente in `packages/ui`

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
| Globaler Client State | Zustand Store |
| Form State | React Hook Form |
| Lokaler Component State | useState |

**Zustand:** Stores in `src/stores/`, `use[Name]Store`, kein Server State in Zustand.

#### Facade Pattern
- Facades in `src/facades/`, `use[Domain][List|Detail]Facade`
- Kapseln React Query + Mutations + Invalidierung
- Pages bleiben dünn — nur Layout + Facade + UI-Komposition

#### Dateistruktur Frontend
```
src/app/(platform)/[domain]/
├── page.tsx                    ← Dünn, nutzt Facade
├── [id]/page.tsx
└── _components/
    ├── create-[domain]-dialog.tsx
    ├── [domain]-table.tsx
    └── edit-[domain]-form.tsx
src/facades/                    ← Custom Hooks (Facade Pattern)
src/stores/                     ← Zustand Stores
src/generated/                  ← Orval Output (NICHT editieren)
src/lib/schemas/                ← Manuelle Zod Schemas
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
- Frontend Unit: Vitest (Facade Hooks)
- Frontend Component: Storybook + Testing Library
- E2E: Playwright für kritische Flows

---

## Architektur-Regeln (nicht verhandelbar)

- Contract-First: OpenAPI → generierte Controller Interfaces + DTOs
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
- Keine Emojis
- Kurze, präzise Antworten
- Datei-Referenzen mit Zeilennummer: `path/to/file.ts:42`
