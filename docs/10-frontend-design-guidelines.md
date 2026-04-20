# Frontend Design Guidelines

Zentrales Referenz-Dokument fuer Design-Entscheidungen, Styling-Patterns, Komponenten-Nutzung und Entwicklungsrichtlinien im Frontend. Dieses Dokument ist die Single Source of Truth fuer alle visuellen und strukturellen Entscheidungen.

**Vorrang:** Dieses Dokument hat Vorrang vor `docs/05-ui-domain.md`, das teilweise veraltete Informationen enthaelt (z.B. Teal/Slate Farben statt Indigo/Stone, Facade-Pattern statt direkte React Query Hooks, packages/ui statt components/ui).

---

## 1. Design System

### 1.1 Farbpalette

Das Design-System nutzt OKLCh-Farbraum fuer alle CSS-Variablen (bessere Farbinterpolation als HSL/Hex).

**Primaer: Indigo**
```
Light:  Indigo 500  oklch(0.585 0.233 277)  #6366F1
Dark:   Indigo 400  oklch(0.715 0.199 277)  #818CF8
```

**Akzent: Emerald**
```
Light:  Emerald 500  oklch(0.765 0.177 163)  #10B981
Dark:   Emerald 400  oklch(0.765 0.177 163)  #34D399
```
Hinweis: In `globals.css` nutzen Light und Dark Mode aktuell denselben OKLCh-Wert fuer `--accent`. Der Hex-Wert #34D399 ist konzeptuell korrekt fuer Emerald 400, aber der OKLCh-Wert muesste fuer eine echte Unterscheidung angepasst werden.

**Neutrals: Stone (warme Grautoene)**
```
Stone 50   oklch(0.985 0.002 75)   #FAFAF9  — Background (Light)
Stone 100  oklch(0.97 0.001 75)    #F5F5F4  — Secondary/Muted
Stone 200  oklch(0.923 0.003 75)   #E7E5E4  — Borders
Stone 300  oklch(0.869 0.005 56)   #D6D3D1  — Input Borders
Stone 400  oklch(0.709 0.01 56)    #A8A29E  — Muted Foreground (Dark)
Stone 500  oklch(0.553 0.013 58)   #78716C  — Muted Foreground (Light)
Stone 600  oklch(0.444 0.011 73)   #57534E  — Sidebar Foreground
Stone 700  oklch(0.321 0.009 17)   #44403C  — Borders (Dark)
Stone 800  oklch(0.268 0.007 34)   #292524  — Cards (Dark)
Stone 900  oklch(0.216 0.006 56)   #1C1917  — Background (Dark), Foreground (Light)
```

**Signalfarben (fix, nicht per Tenant aenderbar):**
```
Success:  Emerald 600  oklch(0.596 0.145 163)  #059669
Warning:  Amber 500    oklch(0.769 0.188 70)   #F59E0B
Error:    Red 500      oklch(0.637 0.237 25)    #EF4444
Info:     Sky 500      oklch(0.685 0.169 237)   #0EA5E9
```

### 1.2 Typografie

**Default Fonts:**
- UI/Body: Geist Sans (geladen via `next/font/google`, Variable: `--font-sans`)
- Code/Mono: Geist Mono (Variable: `--font-mono`)

**Font Sizes (Tailwind):**
```
text-xs     0.75rem / 1rem      — Badges, Hints, Timestamps
text-sm     0.875rem / 1.25rem  — Tabellen, Form Labels, Secondary Text
text-base   1rem / 1.5rem       — Body Text, Buttons (selten)
text-lg     1.125rem / 1.75rem  — Page Descriptions
text-xl     1.25rem / 1.75rem   — Card Titles
text-2xl    1.5rem / 2rem       — Page Titles
text-3xl    1.875rem / 2.25rem  — Hero (selten)
```

**Font Weights:**
```
font-normal   400  — Body Text
font-medium   500  — Labels, Table Headers, Buttons
font-semibold 600  — Card Titles, Badge Text
font-bold     700  — Page Titles, Headings
```

**Konventionen:**
- `tabular-nums` global aktiviert (`body { font-variant-numeric: tabular-nums; }`)
- Keine `font-light` (300) verwenden — zu duenn fuer Screen-Rendering
- Headings: `font-bold` fuer h1/h2, `font-semibold` fuer h3+

### 1.3 Spacing

Tailwind Standard-Scale. Konsistent verwenden:

```
gap-1    0.25rem  — Innerhalb von Inline-Elementen
gap-2    0.5rem   — Zwischen eng verwandten Elementen
gap-3    0.75rem  — Form Fields (Label → Input)
gap-4    1rem     — Zwischen Sektionen innerhalb einer Card
gap-6    1.5rem   — Zwischen Cards/Sektionen
gap-8    2rem     — Zwischen grossen Sektionen
```

**Page Padding:**
```
p-4 md:p-6  — Standard Page Content Padding
px-4        — Header/Footer Padding
```

### 1.4 Border Radius

CSS-Variable basiert, skaliert vom Basis-Wert `--radius: 0.5rem`:

```
radius-sm   calc(var(--radius) * 0.6)  — kleine Elemente (Badges, Tags)
radius-md   calc(var(--radius) * 0.8)  — Inputs, Buttons
radius-lg   var(--radius)              — Cards, Dialoge
radius-xl   calc(var(--radius) * 1.4)  — grosse Container
radius-2xl  calc(var(--radius) * 1.8)  — selten, spezielle Container
radius-3xl  calc(var(--radius) * 2.2)  — selten
radius-4xl  calc(var(--radius) * 2.6)  — selten
```

Tenant-Branding kann `--radius` ueberschreiben (0 bis 1rem).

### 1.5 Schatten

Tailwind Defaults, sparsam einsetzen:

```
shadow-sm    — Dropdowns, Popovers
shadow       — Cards (nur wenn floating, nicht in Grid)
shadow-md    — Dialoge, Modals
shadow-lg    — Nicht verwenden (zu schwer)
```

Grundregel: Cards in Content-Bereichen nutzen `border` statt `shadow`. Shadows nur fuer Overlays (Dialoge, Dropdowns, Popovers).

---

## 2. Komponenten-Richtlinien

### 2.1 shadcn/ui Pflicht

**Alle UI-Elemente MUESSEN shadcn/ui Komponenten nutzen.** Kein rohes HTML:

```tsx
// RICHTIG
import { Button } from "@/components/ui/button";
<Button variant="outline" size="sm">Erstellen</Button>

// FALSCH
<button className="bg-primary text-white px-4 py-2 rounded">Erstellen</button>
```

**Installierte Basis-Komponenten:**
```
badge, breadcrumb, button, card, checkbox, color-picker,
dialog, dropdown-menu, form, input, label, popover,
scroll-area, select, separator, sheet, sidebar, skeleton,
sonner, switch, table, tabs, textarea, tooltip
```

Neue Basis-Komponenten via `npx shadcn@latest add <name>` installieren.

### 2.2 Button Variants

```
default    — Primary Action (nur 1x pro View)
secondary  — Secondary Actions
outline    — Tertiary Actions, Cancel
ghost      — Subtle Actions (in Toolbars, Menus)
destructive — Loeschen, Deaktivieren
link       — Inline-Links die wie Buttons agieren
```

**Loading Pattern (Button bleibt aktiv, Spinner zeigt Status):**
```tsx
<Button>
  {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
  Save
</Button>
```
Buttons werden bei Loading **nicht** disabled — nur der Spinner wird angezeigt. Ausnahme: Destructive Actions (Delete) werden disabled um Doppelklick zu verhindern.

### 2.3 Forms

**Immer:** React Hook Form + `zodResolver` + shadcn `<Form>` / `<FormField>`

```tsx
const form = useForm<CreateTenantRequest>({
  resolver: zodResolver(createTenantRequestSchema),
  defaultValues: { name: "", slug: "" },  // IMMER setzen
});
```

**Validation:**
- Zod Schemas aus Orval-Generation (`src/generated/`) oder manuell (`src/lib/schemas/`)
- `defaultValues` IMMER setzen (verhindert uncontrolled→controlled Warnungen)
- Field-Fehler vom Backend via `form.setError(field, { message })`
- Generelle Fehler (500, Netzwerk) via Sonner Toast
- Async-Validierung (Slug/Domain Eindeutigkeit) nur beim Submit, nicht inline

### 2.4 Tables

shadcn `<Table>` mit manueller Komposition (kein TanStack Table noetig bei einfachen Listen):

```tsx
<Table>
  <TableHeader>
    <TableRow>
      <TableHead>{t("name")}</TableHead>
      <TableHead>{t("status")}</TableHead>
      <TableHead className="text-right">{t("actions")}</TableHead>
    </TableRow>
  </TableHeader>
  <TableBody>
    {items.map(item => (
      <TableRow key={item.id}>
        <TableCell>{item.name}</TableCell>
        <TableCell><StatusBadge status={item.status} /></TableCell>
        <TableCell className="text-right"><ActionsMenu item={item} /></TableCell>
      </TableRow>
    ))}
  </TableBody>
</Table>
```

### 2.5 Dialoge

shadcn `<Dialog>` fuer Create/Edit Flows:

```tsx
<Dialog open={open} onOpenChange={setOpen}>
  <DialogTrigger render={<Button>Create</Button>} />
  <DialogContent>
    <DialogHeader>
      <DialogTitle>{t("createTitle")}</DialogTitle>
      <DialogDescription>{t("createDescription")}</DialogDescription>
    </DialogHeader>
    <CreateForm onSuccess={() => setOpen(false)} />
  </DialogContent>
</Dialog>
```

Hinweis: Dieses Projekt nutzt `render` Prop statt `asChild` (Migration auf React 19 / shadcn v4 / Base UI, siehe PROGRESS.md Phase 2e, Task E.16).

### 2.6 Loading States

| Zustand | Pattern |
|---------|---------|
| Initial Load | `<Skeleton>` Komponenten im Layout der Zielseite |
| Table Refetch | Opacity-Overlay auf TableBody (`opacity-50 pointer-events-none`) |
| Button Submit | Spinner im Button (Button bleibt aktiv, Destructive Actions disabled) |
| Page Navigation | Sofortige Navigation, Content laedt nach (Skeleton) |

```tsx
// Skeleton Beispiel
{isLoading ? (
  <div className="space-y-3">
    <Skeleton className="h-8 w-48" />
    <Skeleton className="h-64 w-full" />
  </div>
) : (
  <ActualContent />
)}
```

### 2.7 Empty States

Zentrierte Message mit optionalem CTA:

```tsx
<div className="flex flex-col items-center justify-center py-12 text-center">
  <Icon className="h-12 w-12 text-muted-foreground mb-4" />
  <h3 className="text-lg font-semibold">{t("emptyTitle")}</h3>
  <p className="text-sm text-muted-foreground mt-1 mb-4">{t("emptyDescription")}</p>
  <Button>{t("createFirst")}</Button>
</div>
```

### 2.8 Error States

- **Field-Fehler:** Automatisch via React Hook Form + shadcn `<FormMessage>`
- **Page-Level Fehler:** Toast via Sonner (`toast.error(message)`)
- **Full-Page Error:** Next.js `error.tsx` Boundary pro Route-Segment mit Retry-Button (selten, nur bei kritischen Fehlern). Zeigt generische Fehlermeldung + "Erneut versuchen" Button der `reset()` aufruft.

### 2.9 Animations & Transitions

- `tw-animate-css` ist global importiert (siehe `globals.css` Zeile 2)
- **Erlaubt:** `transition-colors`, `transition-opacity`, `animate-spin` (Loading), `animate-pulse` (Skeleton)
- **Sparsam:** `transition-all` nur wenn mehrere Properties gleichzeitig animiert werden
- **Verboten:** Aufwaendige Layout-Animationen (`animate-bounce`, `animate-ping` etc.) ausser fuer Notifications
- **`prefers-reduced-motion`:** shadcn/tw-animate-css respektiert das automatisch. Eigene Animationen muessen `motion-reduce:` Prefix nutzen.
- Duration: `duration-150` (schnelle Hover), `duration-200` (Standard), `duration-300` (Overlays/Modals)

### 2.10 data-testid Konventionen

Alle interaktiven und semantisch wichtigen Elemente bekommen `data-testid` Attribute fuer E2E Tests:

```
Pattern: [context]-[element-type]
Beispiele:
  data-testid="create-tenant-button"
  data-testid="tenant-name-input"
  data-testid="tenant-row"
  data-testid="submit-button"
  data-testid="delete-confirm-dialog"
```

- **Buttons:** `[action]-button` oder `[action]-[context]-button`
- **Inputs:** `[field]-input`
- **Rows:** `[entity]-row`
- **Dialoge:** `[action]-dialog` oder `[action]-[entity]-dialog`
- **Tables:** `[entity]-table`
- Kebab-case, englische Bezeichnungen

---

## 3. Layout-System

### 3.1 App Shell

```
┌──────────────────────────────────────────────────┐
│ Sidebar │ Top-Bar (Header)                        │
│         │─────────────────────────────────────────│
│ Nav     │ Content Area                            │
│ Items   │                                         │
│         │                                         │
│─────────│                                         │
│ NavUser │                                         │
└──────────────────────────────────────────────────┘
```

- **Sidebar:** Collapsible (Icon-Mode), Breite: `13.75rem` (normal), `3.5rem` (collapsed)
- **Top-Bar:** Kontextabhaengig (siehe Paket 4 in UI/UX Spec), `h-14`, sticky
- **Content Area:** Padding `p-4 md:p-6`, scrollable

### 3.2 Sidebar

- Tenant-Logo + Name im Header (Workspace) oder "Webcontify" (Platform)
- Sections mit Separatoren
- Icons: Lucide, `h-4 w-4`
- Active State: `bg-sidebar-accent text-sidebar-accent-foreground`
- Hover State: Gleiche Farben wie Active (subtiler)
- Collapsed Mode: Nur Icons, Tooltip mit Label
- NavUser im Footer: Avatar + Name + Settings/Logout Dropdown

### 3.3 Responsive Verhalten

- **Desktop (>= 1024px):** Sidebar sichtbar, Content neben Sidebar
- **Tablet (768-1023px):** Sidebar collapsed (Icon-Mode)
- **Mobile (< 768px):** Sidebar als Sheet (Overlay), Hamburger-Menu in Top-Bar

### 3.4 Page Layouts

**List Page:**
```tsx
<div className="flex flex-col gap-6 p-4 md:p-6">
  {/* Top-Bar liefert Titel + Actions */}
  <TableWithData />
</div>
```

**Detail Page:**
```tsx
<div className="flex flex-col gap-6 p-4 md:p-6">
  {/* Top-Bar liefert Breadcrumbs + Actions */}
  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
    <div className="lg:col-span-2">{/* Main Content */}</div>
    <div>{/* Sidebar Info */}</div>
  </div>
</div>
```

**Settings Page (Tabs):**
```tsx
<div className="flex flex-col gap-6 p-4 md:p-6">
  <Tabs defaultValue="general">
    <TabsList>
      <TabsTrigger value="general">{t("general")}</TabsTrigger>
      <TabsTrigger value="advanced">{t("advanced")}</TabsTrigger>
    </TabsList>
    <TabsContent value="general">{/* ... */}</TabsContent>
    <TabsContent value="advanced">{/* ... */}</TabsContent>
  </Tabs>
</div>
```

---

## 4. Tenant-Branding (Whitelabel)

### 4.1 Ueberschreibbare Properties

| Property | CSS Variable | Beschreibung |
|----------|-------------|--------------|
| Primary Color | `--primary`, `--ring`, `--sidebar-primary`, `--chart-1` | Buttons, Links, Focus Ring |
| Secondary Color | `--brand-secondary` | Sekundaere Akzentfarbe (Hex, nicht OKLCh; nicht in Tailwind Theme registriert — nur via `style` oder `var()` nutzbar) |
| Accent Color | `--accent`, `--chart-2` | Badges, Highlights |
| Danger Color | `--destructive` | Error States |
| Success Color | `--success` | Success States |
| Warning Color | `--warning` | Warning States |
| Border Radius | `--radius` | Globaler Radius (0 bis 1rem) |
| Font Family | `--font-sans` | Gesamte UI Font |

### 4.2 Branding-Anwendung

```
Workspace Layout
  → BrandingProvider
    → GET /api/v1/tenant/config
    → Hex → OKLCh Konvertierung
    → CSS Variables auf document.documentElement setzen
    → Alle shadcn Komponenten reagieren automatisch
```

Platform-Admin-Bereich: **Kein Tenant-Branding** — nutzt Default-Theme.

### 4.3 Keycloak-Theme Sync

```
Tenant speichert Branding
  → TenantBrandingUpdatedEvent
  → TenantBrandingSyncListener
  → Keycloak Admin API: Realm Attributes aktualisieren
  → Login-Seite nutzt aktualisierte Werte
```

Keycloak-Theme nutzt eigene CSS-Variablen (`--wc-primary`, `--wc-radius`, `--wc-font`), die aus Realm-Attributes gelesen werden.

---

## 5. Internationalisierung (i18n)

### 5.1 Grundregeln

- **ALLE UI-Texte** ueber `useTranslations()` (next-intl) — keine hardcoded Strings
- Unterstuetzte Sprachen: Englisch (`en.json`), Deutsch (`de.json`)
- Default: Browser-Sprache, umschaltbar via Cookie
- Keys in dot-notation: `tenants.createDialog.title`

### 5.2 Translation-Struktur

```json
{
  "tenants": {
    "title": "Tenants",
    "createDialog": {
      "title": "Create Tenant",
      "description": "Enter details for the new tenant.",
      "nameLabel": "Name",
      "namePlaceholder": "e.g. Acme Corp"
    },
    "table": {
      "name": "Name",
      "status": "Status",
      "actions": "Actions"
    }
  }
}
```

### 5.3 Konventionen

- Keys nach Feature gruppieren (gleiche Struktur wie Dateistruktur)
- Suffix-Pattern: `...Label`, `...Placeholder`, `...Description`, `...Title`
- Plurals: `{count, plural, one {# item} other {# items}}`
- Parameter: `"greeting": "Welcome, {name}!"`
- **EN und DE immer gleichzeitig pflegen** — keine fehlenden Keys

### 5.4 Nutzung in Komponenten

```tsx
import { useTranslations } from "next-intl";

function TenantList() {
  const t = useTranslations("tenants");
  return <h1>{t("title")}</h1>;
}
```

---

## 6. State Management

### 6.1 Hierarchie

| Was | Womit |
|-----|-------|
| Server State (API-Daten) | React Query (via Orval Hooks) |
| Form State | React Hook Form |
| Lokaler Component State | `useState` |
| Globaler Client State (selten) | Zustand Store |

### 6.2 React Query Patterns

```tsx
// Direkt verwenden, kein Wrapper
const { data, isLoading, error } = useGetTenants();
const { mutate, isPending } = useCreateTenant();

// Mutation mit Invalidierung
mutate(data, {
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: getGetTenantsQueryKey() });
    toast.success(t("createSuccess"));
  },
  onError: (error) => {
    // Field errors → form.setError()
    // General errors → toast.error()
  },
});
```

### 6.3 Kein Facade-Layer

React Query Hooks direkt in Komponenten verwenden. Custom Hooks nur bei echtem Bedarf (z.B. `useAuth`, `usePermissions`, `useBranding`).

---

## 7. Dateistruktur

```
src/
├── app/
│   ├── (platform)/[domain]/         ← Duenne Pages (Layout + UI-Komposition)
│   ├── (workspace)/[domain]/        ← Tenant-scoped Pages
│   └── layout.tsx                   ← Root Layout (Providers)
├── features/[domain]/
│   ├── components/
│   │   ├── __tests__/               ← Vitest Tests
│   │   ├── create-[domain]-dialog.tsx
│   │   ├── [domain]-table.tsx
│   │   └── edit-[domain]-form.tsx
│   ├── hooks/                       ← Domain-spezifische Custom Hooks
│   └── schemas/                     ← Zod Schemas (manuell)
├── components/
│   ├── layout/                      ← App Shell (Sidebar, Header)
│   └── ui/                          ← shadcn/ui Basis-Komponenten
├── generated/                       ← Orval Output (NICHT editieren)
├── hooks/                           ← Globale Hooks (useAuth, usePermissions)
├── lib/                             ← Utilities (color-utils, cn)
├── messages/                        ← i18n (en.json, de.json)
└── providers/                       ← Context Provider (Branding, Theme)
```

### 7.1 Namenskonventionen

- **Komponenten:** `kebab-case.tsx` (z.B. `create-tenant-dialog.tsx`)
- **Hooks:** `use-[name].ts` (z.B. `use-auth.ts`)
- **Schemas:** `[name]-schema.ts`
- **Tests:** `__tests__/[component].test.tsx`
- **i18n Keys:** `camelCase` in dot-notation

---

## 8. Styling-Richtlinien

### 8.1 Tailwind-Only

- **Immer** Tailwind Utility Classes + `cn()` fuer bedingte Klassen
- **Nie** `style={{}}` mit CSS Variables (Ausnahme: dynamisches Branding in Preview)
- **Nie** globales CSS ausser in `globals.css`

```tsx
// RICHTIG
<div className={cn("flex items-center gap-2", isActive && "bg-accent")}>

// FALSCH
<div style={{ display: "flex", gap: "8px", backgroundColor: isActive ? "var(--accent)" : undefined }}>
```

### 8.2 Dark Mode

- Via `class` Strategy (nicht `media`)
- Tailwind `dark:` Prefix fuer Dark-Mode-spezifische Styles
- shadcn Komponenten handlen Dark Mode automatisch via CSS Variables
- Eigene Komponenten: `bg-background text-foreground` als Default, `dark:` nur wenn noetig

### 8.3 CSS Variables

Definiert in `globals.css`, gemapped via `@theme inline`. Neue Variables:
1. In `globals.css` unter `:root` und `.dark` definieren
2. In `@theme inline` Block mappen (`--color-[name]: var(--[name])`)
3. In Tailwind via `bg-[name]`, `text-[name]` etc. verwenden

---

## 9. Testing

### 9.1 Frontend Unit Tests (Vitest)

- Co-located in `__tests__/` neben der Komponente
- Testing Library fuer Component Tests
- Mindestens testen: Render, User Interactions, Error States, Loading States

```tsx
import { render, screen } from "@testing-library/react";
import { TenantTable } from "../tenant-table";

describe("TenantTable", () => {
  it("renders tenant rows", () => {
    render(<TenantTable tenants={mockTenants} />);
    expect(screen.getByText("Acme Corp")).toBeInTheDocument();
  });
});
```

### 9.2 E2E Tests (Playwright)

- Verzeichnis: `apps/e2e/`
- **Sprache: Englisch** (alle Selektoren, Assertions, Beschreibungen)
- **Selektoren: `data-testid`** (keine Text-Selektoren, keine CSS-Klassen)
- Gegen laufende Dev-Umgebung
- API-Cleanup nach jedem Test (ApiClient mit Cleanup-Stack)

```tsx
test("should create a tenant", async ({ page }) => {
  await page.getByTestId("create-tenant-button").click();
  await page.getByTestId("tenant-name-input").fill("Test Tenant");
  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("tenant-row")).toHaveCount(1);
});
```

---

## 10. Accessibility (a11y)

### 10.1 Grundregeln

- shadcn/ui (Radix-basiert) liefert a11y out-of-the-box — nicht ueberschreiben
- Alle interaktiven Elemente: fokussierbar via Tastatur
- `aria-label` fuer Icon-Only Buttons
- Skip-to-content Link im Root Layout (bereits implementiert)
- Farbkontrast: Mindestens WCAG AA (4.5:1 fuer Text, 3:1 fuer grosse Text)
- Focus Ring: `--ring` Variable, automatisch via shadcn

### 10.2 Formulare

- `<FormLabel>` immer mit `htmlFor` (shadcn macht das automatisch)
- Error Messages mit `aria-describedby` (shadcn `<FormMessage>` macht das automatisch)
- Required Fields: visuelles `*` + `aria-required`

---

## 11. Performance

### 11.1 Font Loading

- Default Font (Geist): via `next/font/google` (Zero Layout Shift)
- Tenant Fonts: Dynamisch via Google Fonts Stylesheet-Link
- Font-Auswahl-Dropdown: Lazy Loading via Intersection Observer
- Nur die aktive Tenant-Font wird global geladen

### 11.2 Images

- Logos: SVG bevorzugt (skaliert, klein)
- Favicon: PNG/ICO
- File Uploads: Backend-Validierung (Content-Type, Magic Bytes, Size Limit)
- Pre-signed URLs fuer S3-Dateien

### 11.3 Bundle

- Orval-generierte Hooks: Tree-Shakeable
- Lucide Icons: Einzeln importieren (`import { Settings } from "lucide-react"`)
- Keine barrel exports in Feature-Modulen

---

## 12. Entscheidungslog (Frontend-spezifisch)

| # | Entscheidung | Begruendung |
|---|---|---|
| 1 | Indigo + Emerald + Stone | Professionell, warm (Stone statt Slate/Zinc), keine Signalfarben-Kollision |
| 2 | OKLCh Farbraum | Perceptually uniform, bessere Interpolation als HSL bei Branding-Overrides |
| 3 | Geist als Default Font | Fuer Next.js optimiert, Zero Layout Shift via next/font |
| 4 | Kein Facade-Layer | React Query Hooks direkt, Custom Hooks nur bei echtem Bedarf |
| 5 | Kein Zustand Store vorerst | Auth via useAuth(), Tenant via Subdomain, kein globaler State noetig |
| 6 | shadcn/ui direkt in App | packages/ui geloescht, Komponenten in components/ui/ |
| 7 | Tabs statt Cards (Branding) | Spart vertikalen Platz, bessere Organisation |
| 8 | Kontextabhaengige Top-Bar | Ersetzt redundante Breadcrumbs + Page-Header, skaliert fuer App Domain |
| 9 | Font Lazy Loading | Intersection Observer im Dropdown, nur sichtbare Fonts laden |
| 10 | 12 kuratierte Fonts | Qualitaet > Quantitaet, alle Google Fonts kompatibel |
