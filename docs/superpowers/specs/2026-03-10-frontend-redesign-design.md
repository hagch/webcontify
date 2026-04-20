# Frontend Redesign — Design Spec

## Kontext

Das bestehende Frontend (Phase 0-2d) wird komplett neu aufgebaut. Das Backend bleibt unveraendert. Grund: visuelle Qualitaet, Code-Architektur, UX und Gesamtstruktur sollen von Grund auf sauber sein.

## Scope

- Frontend komplett neu (Clean Delete von `apps/platform/src/` und `packages/ui/`)
- Backend, OpenAPI Specs, Migrations bleiben unberuehrt
- Alle Domains werden durchdesignt: Tenants, Apps, Content, Users/Roles, Settings, Dashboard
- Hybrid-Ansatz: Gesamtstruktur grob, dann iterativ verfeinern und implementieren

---

## 1. Visuelles Design

### Farbpalette: Indigo + Emerald + Stone

**Indigo als Primary** (Navigation, Buttons, Links, primaere Aktionen):
```
Light:  #6366F1 (Indigo 500), Hover: #4F46E5 (Indigo 600)
Dark:   #818CF8 (Indigo 400), Hover: #6366F1 (Indigo 500)
Tint:   #EEF2FF (Indigo 50) — fuer Active States, Badges
```

**Emerald als Akzent** (positive Indikatoren, Trends, Growth):
```
Light:  #10B981 (Emerald 500)
Dark:   #34D399 (Emerald 400)
```

**Stone fuer warme Neutraltoene:**
```
Background:  #FAFAF9 (Stone 50)
Surface:     #F5F5F4 (Stone 100)
Border:      #E7E5E4 (Stone 200)
Muted Text:  #A8A29E (Stone 400)
Body Text:   #57534E (Stone 500)
Heading:     #1C1917 (Stone 950)
```

**Dark Mode Neutrals:**
```
Background:  #1C1917 (Stone 900)
Surface:     #292524 (Stone 800)
Border:      #3D3835 (Stone 700)
```

**Signalfarben (fix, nicht per Tenant aenderbar):**
```
Success:   #059669 (Emerald 600) — bewusst dunkler als Emerald Akzent (#10B981) um Verwechslung zu vermeiden
Warning:   #F59E0B (Amber 500)
Error:     #EF4444 (Red 500)
Info:      #0EA5E9 (Sky 500)
```

**Dark Mode Text-Farben:**
```
Primary Text:    #FAFAF9 (Stone 50) — Kontrast >= 4.5:1 auf Stone 900
Secondary Text:  #D6D3D1 (Stone 300) — Kontrast >= 4.5:1 auf Stone 900
Muted Text:      #A8A29E (Stone 400) — Kontrast >= 3:1 auf Stone 900
Primary (Indigo): #818CF8 (Indigo 400) — Kontrast >= 3:1 auf Stone 900
Accent (Emerald): #34D399 (Emerald 400) — Kontrast >= 3:1 auf Stone 900
```

### Design-Stil

- Supabase-inspiriert: strukturiert, informationsdicht, freundlich, selbsterklaerend
- Warme Toene (Stone statt Slate) fuer einladenden Eindruck
- Eigene Identitaet durch Indigo+Emerald Kombi — hebt sich von Standard-SaaS ab

### Fonts

- Body: Geist Sans (via `next/font`, Zero Layout Shift)
- Code: Geist Mono
- Border Radius: 0.5rem (Medium)
- `font-variant-numeric: tabular-nums` fuer alle Zahlen in Tables und Stat-Cards

### Kontrast-Anforderungen

- Normaler Text: >= 4.5:1 (WCAG AA)
- Grosser Text (>= 18px bold oder >= 24px): >= 3:1
- UI-Elemente (Icons, Borders): >= 3:1
- Jedes Farbpaar muss in Light UND Dark Mode separat geprueft werden

---

## 2. App Shell Layout

### Sidebar (Klassisch + Collapsible)

- **Erweitert:** 220px, heller Hintergrund, klare Sections mit Trennlinien und Labels
- **Collapsed:** 56px Icon-Mode, Tooltips bei Hover
- **Toggle:** Button in Sidebar + Keyboard Shortcut
- shadcn Sidebar Komponente (built-in Collapsible + Mobile Sheet Support)

### Sidebar-Aufbau (von oben nach unten)

1. **Logo + App-Name** (Webcontify)
2. **Tenant-Switcher** (nur fuer Platform Admins sichtbar) — Dropdown mit Tenant-Liste
3. **Navigation** mit Section Labels und Trennlinien
4. **User Footer** — Avatar, Name, Rolle, Drei-Punkt-Menu (Theme Toggle, Logout)

### Header

- SidebarTrigger (Hamburger)
- Breadcrumbs (Workspace / Tenants / Acme Corp)
- Command Palette Trigger (Cmd+K) — Suche rechts

### Mobile

- Sheet-basierte Sidebar (shadcn built-in)
- Responsive Tables/Cards

---

## 3. Navigation & Routing

### Routing-Gruppen

**`(auth)/`** — Nicht eingeloggt
- `/signin` — Login Page (Keycloak Redirect)

**`(platform)/`** — Platform Admin Bereich
- `/tenants` — Tenant-Liste + Suche + Filter
- `/tenants/new` — Tenant erstellen
- `/tenants/[id]` — Tenant-Detail
- `/tenants/[id]/settings` — Branding, Email, Domain
- `/tenants/[id]/users` — Users dieses Tenants
- `/admins` — Platform Admins verwalten (einladen, entfernen)
- `/settings` — System Settings (spaeter)

**`(workspace)/`** — Tenant-Kontext
- `/dashboard` — Tenant-Dashboard
- `/apps` — App-Uebersicht
- `/apps/[slug]` — App-Dashboard
- `/apps/[slug]/content` — Content Definitions
- `/apps/[slug]/content/[def]` — Schema Builder
- `/apps/[slug]/content/[def]/entries` — Entry-Liste + CRUD
- `/apps/[slug]/settings` — App-Settings
- `/users` — Tenant-User-Liste + Invite
- `/users/[id]` — User-Detail + Rollen
- `/roles` — Rollen + Permission-Matrix
- `/settings/branding` — Tenant Branding
- `/settings/email` — SMTP
- `/settings/domain` — Custom Domain

### Rollenbasierte Sidebar-Sichtbarkeit

| Element | Platform Admin | Tenant Admin | Tenant User |
|---|---|---|---|
| Tenants | Ja | — | — |
| Platform Admins | Ja | — | — |
| System Settings | Ja | — | — |
| Tenant-Switcher | Ja | — | — |
| Dashboard | (via Switcher) | Ja | Ja |
| Apps | (via Switcher) | Ja | Nur mit Permission |
| Users & Roles | (via Switcher) | Ja | — |
| Settings | (via Switcher) | Ja | — |

### Platform Admin "God Mode"

- Platform Admin waehlt Tenant im Switcher → Redirect zur Tenant-Subdomain
- Backend erkennt Platform Admin via JWT (`PLATFORM_ADMIN` Rolle) → voller Zugriff
- Sidebar wechselt zu Workspace-Navigation + "Zurueck zur Platform" Link
- Konsistent mit bestehender Backend-Architektur (TenantResolutionFilter + Role Bypass)
- Kein Cross-Tenant User Support — Platform Admin und Tenant User sind getrennte Accounts

---

## 4. Frontend-Architektur

### Component Library

- shadcn/ui direkt in der Next.js App (`src/components/ui/`)
- Kein separates `packages/ui` Package (YAGNI — spaeter extrahierbar wenn noetig)
- shadcn CLI fuer Komponenten-Installation

### Ordnerstruktur (Feature-basiert)

```
apps/platform/src/
├── app/                          ← Next.js Routes (duenn)
│   ├── (auth)/signin/
│   ├── (platform)/
│   │   ├── layout.tsx            ← Platform Admin Guard
│   │   ├── tenants/
│   │   ├── admins/
│   │   └── settings/
│   ├── (workspace)/
│   │   ├── layout.tsx            ← Tenant Guard + Branding
│   │   ├── dashboard/
│   │   ├── apps/
│   │   ├── users/
│   │   ├── roles/
│   │   └── settings/
│   ├── layout.tsx                ← Root (Providers)
│   └── page.tsx                  ← Redirect
│
├── features/                     ← Domain-Logik
│   ├── tenants/
│   │   ├── components/           ← Wiederverwendbar in Domain
│   │   ├── hooks/
│   │   └── schemas/
│   ├── users/
│   ├── apps/
│   ├── content/
│   ├── roles/
│   └── settings/
│
├── components/
│   ├── ui/                       ← shadcn Basis-Komponenten
│   └── layout/                   ← AppShell, Sidebar, Header
│
├── hooks/                        ← App-weite Hooks (useAuth, useMobile)
├── lib/                          ← Auth Config, API Client, Utils
├── generated/                    ← Orval Output (nicht editieren)
└── types/                        ← Globale Types
```

### Komponentenregeln

| Frage | Ort |
|---|---|
| shadcn Basis-Komponente? | `components/ui/` |
| Layout-Teil (Shell, Sidebar, Header)? | `components/layout/` |
| Wiederverwendbar in einer Domain? | `features/[domain]/components/` |
| Nur fuer eine Page, keine Wiederverwendung? | `app/.../[route]/_components/` |

### State Management

| Was? | Womit? |
|---|---|
| Server State (API-Daten) | React Query (via Orval Hooks) — direkt, kein Facade-Layer |
| Auth State | `useAuth()` Hook (duenner NextAuth-Wrapper) |
| Tenant-Kontext | Subdomain (keine State-Verwaltung noetig) |
| Form State | React Hook Form + Zod |
| Lokaler Component State | useState |
| Globaler Client State | Zustand — nur wenn wirklich noetig (vorerst nicht) |

### Data Fetching Pattern

- Orval-generierte React Query Hooks direkt in Komponenten
- Custom Hooks nur bei komplexer Orchestrierung (mehrere abhaengige Queries)
- Kein Facade-Layer als Standard
- Entscheidung je nach Screen-Komplexitaet

### Auth

- `useAuth()` Hook als duenner NextAuth-Wrapper
- Liefert: `user`, `isPlatformAdmin`, `currentTenantId`
- Erweiterbar fuer OpenFGA-Permissions in Phase 3
- Kein eigener Auth Store

---

## 5. UX Patterns

### Loading States

| Kontext | Verhalten |
|---|---|
| Initial Page Load | Skeleton-Komponenten (shadcn), animate-pulse |
| Table Refetch (Mutation, manuell, Filter) | Semi-transparentes Overlay + Spinner ueber Table-Body |
| Button-Aktion (Save, Create, Delete) | Loading-State mit Spinner + `aria-busy="true"`, ignoriert Klicks waehrend in-flight (nicht `disabled`) |
| Spinner/Skeleton Schwelle | Erst nach >300ms anzeigen um Flackern zu vermeiden |

Kein "Loading..." Text. Niemals.

### Error Handling

| Fehlerart | Darstellung |
|---|---|
| Field-Fehler (422) | Unter dem jeweiligen Feld via `form.setError()` + `role="alert"` / `aria-live="polite"` |
| Generelle Fehler (500, Netzwerk) | Sonner Toast (auto-dismiss 5s, `aria-live="polite"`) |
| Fehlermeldungen | Muessen Ursache + Loesung enthalten ("Slug existiert bereits. Waehle einen anderen.") |
| Bei mehreren Fehlern | Auto-Focus auf erstes invalides Feld |

### Empty States

- Pro Screen eine klare "Noch keine X vorhanden" Ansicht mit CTA-Button
- Hilft neuen Usern den ersten Schritt zu machen

### Destructive Actions

- Confirmation Dialog vor Delete, Status-Aenderung, Entfernen von Usern
- Destructive Buttons: semantische Danger-Farbe (Red), visuell getrennt von primaeren Aktionen
- Undo-Option per Toast wo moeglich ("Tenant geloescht. Rueckgaengig machen?")

### Async Validierung

- **Standard:** Nur beim Submit validieren (Backend 422 → Field-Fehler)
- **Ausnahme:** Inline-Validierung fuer UX-kritische Felder (Slug, Domain) — Debounce 300-500ms → Spinner im Input → Haken oder Fehler
- Inline-Validierung auf Blur (nicht auf Keystroke)
- `aria-live="polite"` fuer Inline-Validierungs-Feedback
- Braucht dedizierte Check-Endpoints im Backend

### Forms

- Sichtbare Labels fuer jedes Input-Feld (kein Placeholder-only)
- Required-Felder mit Asterisk markieren
- Semantische Input Types: `type="email"`, `type="url"`, `type="tel"` etc.
- `autocomplete` Attribute fuer Browser-Autofill
- Input-Hoehe >= 44px fuer Touch Targets
- Submit-Feedback: Loading → Success/Error State

### URL State

- Filter, Tabs, Pagination, Sortierung in URL Query Params abbilden
- Bookmarkable, teilbar, Browser-Back funktioniert
- `nuqs` oder `useSearchParams` fuer URL State Management

### Accessibility

- **Focus States:** Sichtbare Focus-Ringe (`focus-visible:ring-*`, 2-4px) auf allen interaktiven Elementen. Niemals `outline: none` ohne Ersatz.
- **Keyboard Navigation:** Tab-Reihenfolge = visuelle Reihenfolge. Volle Keyboard-Unterstuetzung fuer alle Flows.
- **Skip-to-Content:** Link am Seitenanfang fuer Keyboard-User
- **Heading Hierarchy:** Sequentiell h1→h6, kein Level ueberspringen
- **ARIA Labels:** `aria-label` fuer Icon-only Buttons, `aria-live` fuer dynamische Updates
- **Color-not-only:** Information nie nur durch Farbe vermitteln (immer Icon/Text dazu)
- **`prefers-reduced-motion`:** Animationen reduzieren/deaktivieren wenn gesetzt
- **Semantisches HTML:** `<button>` statt `<div onClick>`, `<nav>`, `<main>`, `<aside>` etc.
- **Bilder:** `alt`-Text fuer bedeutungstragende Bilder, `alt=""` fuer dekorative

### Animation & Transitions

- Micro-Interactions: 150-300ms, nie >500ms
- Easing: `ease-out` fuer Einblenden, `ease-in` fuer Ausblenden. Kein `linear` fuer UI.
- Nur `transform` und `opacity` animieren (GPU-beschleunigt)
- Kein `transition: all` — explizite Properties angeben
- Animationen muessen unterbrechbar sein (User-Interaktion bricht laufende Animation ab)
- `prefers-reduced-motion`: Animationen auf Minimum reduzieren (nur essentielle State-Changes)

### Touch & Responsive

- Touch Targets: min 44x44px, ggf. `hitSlop` wenn visuell kleiner
- Touch Spacing: min 8px zwischen interaktiven Elementen
- `touch-action: manipulation` auf interaktiven Elementen (entfernt 300ms Delay)
- `overscroll-behavior: contain` in Modals und Sheets
- Breakpoints: 375px (Mobile) / 768px (Tablet) / 1024px (Desktop) / 1440px (Wide)
- `min-h-dvh` statt `100vh` auf Mobile
- Kein horizontaler Scroll auf Mobile

### Content Handling

- Text-Container muessen mit langen Inhalten umgehen: Truncation (`text-ellipsis`) oder Line-Clamp
- Flex Children brauchen `min-w-0` um Overflow zu verhindern
- Lange Listen (>50 Items): Virtualisierung (Content Entries, User-Listen)

### Dark Mode Implementation

- `color-scheme: dark` auf `<html>` setzen
- `theme-color` Meta-Tag passend zum aktiven Theme
- Light und Dark Mode unabhaengig testen (nicht inferieren)

---

## 6. Screens Uebersicht (Wireframe-Level)

### Platform Admin

| Screen | Hauptelemente |
|---|---|
| Tenant-Liste | Table mit Search/Filter, Status-Badges, Create-Button |
| Tenant-Detail | Overview Card, Tabs (Settings, Users), Status Management |
| Tenant Settings | Tabs: Branding (Logo, Farben), Email (SMTP), Domain |
| Platform Admins | Einfache Liste, Invite-Button |

### Workspace

| Screen | Hauptelemente |
|---|---|
| Dashboard | Stat-Cards (Apps, Users, Entries), Recent Activity |
| App-Liste | Card Grid oder Table, Create-Button |
| App-Detail | Dashboard mit Content Definitions, Settings |
| Content Definitions | Schema Builder (Felder hinzufuegen/sortieren) |
| Content Entries | Table mit Inline-Edit oder Detail-Dialog |
| Users | Table, Invite-Button, Rollen-Badges |
| User-Detail | Profil, Rollen-Zuweisung |
| Roles | Rollen-Liste, Permission-Matrix (Checkbox-Grid) |
| Settings/Branding | Color Picker, Logo Upload, Preview |
| Settings/Email | SMTP Form, Test-Email Button |
| Settings/Domain | Domain Input, DNS-Anleitung, Verifikation-Status |

---

## 7. Whitelabel / Theming

- Tenant-Branding wird via CSS Custom Properties zur Runtime angewendet
- shadcn-Komponenten reagieren automatisch auf Variable-Aenderungen
- TenantBrandingProvider laedt Branding aus Backend und setzt CSS Variables
- Dark Mode: User-Setting (System/Light/Dark), nicht pro Tenant steuerbar
- Indigo+Emerald+Stone ist der Default — Tenants ueberschreiben mit eigenen Farben

---

## 8. Tech Stack (unveraendert)

| Bereich | Technologie |
|---|---|
| Framework | Next.js 16 (App Router) |
| Components | shadcn/ui (Radix, CSS Variables) |
| Styling | Tailwind v4 |
| Server State | React Query (via Orval) |
| Forms | React Hook Form + Zod |
| Auth | NextAuth.js v5 + Keycloak |
| Icons | Lucide |
| Toasts | Sonner |
| i18n | next-intl |
| API Client | Orval (generiert) |
| Tests | Vitest (Unit), Playwright (E2E) |

---

## 9. Was wird portiert (nicht neu geschrieben)

- Auth-Konfiguration (NextAuth + Keycloak Provider)
- API Client Setup (Orval Config + Custom Instance mit Auth Headers)
- Middleware (Tenant Resolution + i18n)
- Orval-generierte Hooks (werden nur neu generiert)
- Zod Schemas (aus `lib/schemas/`)

## 10. Was wird geloescht

- `packages/ui/` — komplett (shadcn wird direkt in App installiert)
- `apps/platform/src/components/` — komplett (neu aufgebaut)
- `apps/platform/src/facades/` — komplett (kein Facade-Layer)
- `apps/platform/src/stores/` — komplett (kein Zustand Store vorerst)
- `apps/platform/src/app/(platform)/` — komplett (neu aufgebaut)
- `apps/platform/src/app/(workspace)/` — komplett (neu aufgebaut)
- Alte Providers werden durch neue ersetzt (gleiche Funktionalitaet, sauberere Implementierung)
