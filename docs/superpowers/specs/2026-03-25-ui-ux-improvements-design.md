# UI/UX Improvements — Design Spec

**Datum:** 2026-03-25
**Branch:** `feature/ui-ux-improvements`
**Abhaengigkeiten:** Phase 3i (Branding Enhancements) abgeschlossen

---

## Uebersicht

5 Arbeitspakete fuer UI/UX-Verbesserungen, sequentiell abzuarbeiten:

1. **Keycloak Login Page Redesign** — Visuell nahtlose Integration ins Design-System
2. **Keycloak Invite-Flow Fix** — Redirect nach Passwort-Setzen + Zwischenseite eliminieren
3. **Font-Auswahl & Preview** — Bug Fix + erweiterte Auswahl + Lazy-Loading Preview
4. **Breadcrumbs & Main-Layout Redesign** — Kontextabhaengige Top-Bar statt redundanter Header
5. **Sidebar Contrast Fix** — Hover/Active-State im Light Mode sichtbar machen

---

## Paket 1: Keycloak Login Page Redesign

### Ist-Zustand

- Keycloak-Theme basiert auf PatternFly v5 mit CSS-Overrides (`styles.css`)
- Branding-Variables (`--wc-primary`, `--wc-secondary`, etc.) werden aus Realm-Attributes gelesen
- Gradient-Background mit zentrierter Login-Card
- Logo/Tenant-Name im Header der Card
- PatternFly-Klassen werden ueberschrieben, aber das Layout bleibt PatternFly-nativ

### Soll-Zustand

**Ansatz: Eigenes CSS mit Design-System-Tokens, PatternFly als Basis**

Das Keycloak-Theme behaelt `parent=keycloak.v2` (PatternFly-Basis fuer Formular-Macros, Error-Handling etc.), aber ueberschreibt das gesamte visuelle Erscheinungsbild mit eigenen CSS-Tokens. Dadurch bleiben Keycloak-Funktionalitaeten (CSRF, Error-Messages, Required-Actions) intakt, waehrend das Design komplett unserem System entspricht.

Die Design-Tokens werden als CSS Custom Properties direkt eingebunden — kein Tailwind-Build noetig.

**Layout: Card mit Branding-Header**

- Zentrierte Card auf neutral-grauem Hintergrund (kein Gradient)
- Oben in der Card: Tenant-Logo (oder Tenant-Name als Fallback)
- Darunter: Seitentitel (z.B. "Sign in to your account")
- Formular mit Input-Feldern, Primary-Button
- Footer-Links (Forgot Password, Language Switcher)

**Design-Tokens im Keycloak-Theme:**

```css
:root {
  /* Aus Realm-Attributes (dynamisch) */
  --wc-primary: #6366F1;
  --wc-primary-hover: color-mix(in srgb, var(--wc-primary) 85%, black);
  --wc-radius: 0.5rem;
  --wc-font: system-ui, sans-serif;

  /* Statische Tokens (identisch mit globals.css) */
  --wc-bg: #FAFAF9;           /* Stone 50 */
  --wc-card: #FFFFFF;
  --wc-card-border: #E7E5E4;  /* Stone 200 */
  --wc-text: #1C1917;         /* Stone 950 */
  --wc-text-muted: #78716C;   /* Stone 500 */
  --wc-input-border: #D6D3D1; /* Stone 300 */
  --wc-input-bg: #FFFFFF;
  --wc-focus-ring: var(--wc-primary);
  --wc-link: var(--wc-primary);
  --wc-error: #EF4444;        /* Red 500 */
}
```

**Abzudeckende Seiten:**

| Seite | Template | Beschreibung |
|-------|----------|--------------|
| Login | `login.ftl` | E-Mail/Passwort Formular, "Forgot Password" Link |
| Update Password | `login-update-password.ftl` | "Willkommen, setze dein Passwort" (Invite-Flow) |
| Reset Password | `login-reset-password.ftl` | E-Mail-Eingabe fuer Passwort-Reset |
| Update Password (Reset) | `login-update-password.ftl` | Neues Passwort nach Reset-Link |
| Error | `error.ftl` | Abgelaufene Links, ungueltige Sessions |
| Info | `info.ftl` | "E-Mail gesendet", "Aktion abgeschlossen" |

**Wichtige Details:**

- Dark Mode Support: `prefers-color-scheme: dark` Media Query mit angepassten Token-Werten
- Google Fonts Loading: Generisch — der Font-Name wird aus Realm-Attributes gelesen und dynamisch als Google Fonts URL geladen. Keine Hardcodierung der Font-Namen im Template. Google Fonts Weights: `wght@400;500;600;700` fuer alle Fonts. `font-display: swap` fuer FOUT-Vermeidung.
- Focus-Ring: `:focus-visible { outline: 2px solid var(--wc-focus-ring); outline-offset: 2px; }` auf Inputs, Buttons, Links. PatternFly-eigene Focus-Styles werden explizit ueberschrieben.
- Language Switcher: Schlichtes Select-Element, gestylt mit Design-Tokens
- Error Messages: Rote Alert-Box unterhalb des Formulars mit `--wc-error`
- Logo: `max-height: 48px`, zentriert, Fallback auf Tenant-Name in Bold
- Responsive: Card nimmt volle Breite auf Mobile (< 640px), max-width 420px auf Desktop
- `color-mix()` Browser-Support: Wird in allen modernen Browsern unterstuetzt (Chrome 111+, Firefox 113+, Safari 16.4+). Fuer den Enterprise-Kontext ist das ausreichend — Keycloak 26 selbst erfordert moderne Browser.

### Nicht im Scope

- Registration-Seite (deaktiviert, Invite-only)
- OTP/2FA-Seiten (nicht konfiguriert)
- Account Management Console (nutzt Keycloak v3 Theme)

---

## Paket 2: Keycloak Invite-Flow Fix

### Ist-Zustand

1. Platform Admin oder Tenant Admin ladet User per E-Mail ein
2. Backend ruft Keycloak Execute-Actions-Email API auf
3. User erhaelt E-Mail mit Branded Template (`executeActions.ftl`)
4. Klick auf Link fuehrt zu Keycloak Action-Token-Seite
5. **Problem 1:** Zwischenseite "You need to update your password" — erfordert extra Klick
6. **Problem 2:** Nach Passwort-Setzen kein Redirect zur Anwendung

### Soll-Zustand

**Ziel:** E-Mail → Klick → Direkt Passwort-Formular → Direkt in der App (wie Clerk/Auth0)

**Loesung Teil 1: Zwischenseite eliminieren**

Template ueberschreiben mit Auto-Submit. Der exakte Template-Name muss gegen Keycloak 26's `base`-Theme verifiziert werden (moeglicherweise `login-action-token.ftl` statt `login-actions-action-token.ftl`):

```html
<!-- Auto-Submit: User sieht diese Seite nicht -->
<form id="kc-action-token-form" action="${url.loginAction}" method="post">
  <noscript>
    <!-- Fallback fuer deaktiviertes JavaScript — gestylt mit Tenant-Branding -->
    <div class="wc-noscript-card">
      <p>Click below to continue setting up your account.</p>
      <button type="submit" class="wc-btn-primary">Continue</button>
    </div>
  </noscript>
</form>
<script>document.getElementById('kc-action-token-form').submit();</script>
```

Der User landet direkt beim Passwort-Formular ohne Zwischenschritt. Der `noscript`-Fallback wird mit dem Keycloak-Theme Branding gestylt (Card + Primary-Button).

**Loesung Teil 2: Redirect nach Passwort-Setzen**

Backend: Beim `executeActionsEmail`-API-Call `clientId` und `redirectUri` Parameter mitgeben:

```
PUT /admin/realms/{realm}/users/{id}/execute-actions-email
  ?client_id=tenant-ui
  &redirect_uri=https://{tenant-slug}.{domain}
  &lifespan=86400
```

- `client_id`: `tenant-ui` (der in Keycloak konfigurierte Client — siehe `KeycloakAdminService.java:72`)
- `redirect_uri`: Tenant-Subdomain URL (fuer Tenant-User) oder Platform-URL (fuer Platform-Admins)
- `lifespan`: Als benannte Konstante (`INVITE_LINK_EXPIRATION_SECONDS = 86400`) im Backend, nicht als Magic Number
- Keycloak leitet nach abgeschlossener Required Action automatisch ueber den OAuth-Flow zur redirect_uri

**Sicherheit: Redirect-URI Whitelist**

Keycloak validiert `redirect_uri` gegen die "Valid Redirect URIs" des Clients. Da Tenant-Subdomains dynamisch sind, muss der `tenant-ui` Client in jedem Tenant-Realm eine Wildcard-Redirect-URI konfiguriert haben. Das wird bereits beim Realm-Setup (`CreateRealmRequest`) gesetzt — dort muss `*.{base-domain}` als Valid Redirect URI konfiguriert sein. Verifizieren, dass das aktuell der Fall ist.

**Loesung Teil 3: Update-Password-Seite als Willkommensseite stylen**

Das `login-update-password.ftl` Template bekommt ein angepasstes Design:
- Titel: "Welcome! Set your password" (statt "Update password")
- Gleiche Card-Struktur wie Login-Seite (Paket 1)
- Tenant-Logo und Branding

### Aenderungen Backend

**`KeycloakAdminService`:** `sendExecuteActionsEmail()` um `clientId` + `redirectUri` Parameter erweitern.

```java
// Aktuell:
usersResource.get(userId).executeActionsEmail(actions);

// Neu:
usersResource.get(userId).executeActionsEmail(
    "tenant-ui",                      // clientId
    redirectUri,                      // tenant subdomain URL
    INVITE_LINK_EXPIRATION_SECONDS,   // 86400 (24h)
    actions
);
```

Die `redirectUri` wird kontextabhaengig berechnet:
- Tenant-User Invite: `https://{tenant-slug}.webcontify.io`
- Platform-Admin Invite: `https://platform.webcontify.io`

### Aenderungen Keycloak-Theme

| Datei | Aenderung |
|-------|-----------|
| `login-actions-action-token.ftl` | Neues Template: Auto-Submit (kein Zwischenklick) |
| `login-update-password.ftl` | Neues Template: "Welcome" Branding + gleiches Design wie Login |

---

## Paket 3: Font-Auswahl & Preview

### Bug: Font wird im Frontend nicht angewendet

**Ursache:** Die `BrandingProvider` (`branding-provider.tsx:124-180`) setzt in `buildCssOverrides()` keine CSS-Variable fuer die Font-Family. Es gibt keinen Code-Pfad der `--font-sans` oder eine aehnliche Variable basierend auf `values.fontFamily` setzt. Die Font wird nur in Keycloak angewendet (ueber Google Fonts Link im `template.ftl`).

**Fix:** Alle 12 Fonts werden per `next/font/google` im Root Layout statisch registriert. Jede Font bekommt eine eigene CSS-Variable (z.B. `--font-inter`, `--font-roboto`). Der BrandingProvider setzt `--font-sans` auf die Variable der aktiven Font.

```typescript
// In buildCssOverrides():
if (values.fontFamily && values.fontFamily !== "system" && values.fontFamily !== "geist") {
  overrides["--font-sans"] = FONT_CSS_VAR_MAP[values.fontFamily]; // z.B. "var(--font-inter)"
}

// In removeBrandingFromDocument():
// --font-sans wird einfach entfernt — faellt automatisch auf den next/font Default (Geist) zurueck.
```

**Vorteile gegenueber dynamischem Google Fonts Loading:**
- Self-hosted: Keine externen Requests zur Laufzeit
- Zero Layout Shift: next/font optimiert Font-Loading automatisch
- Kein CSP-Problem: Keine externen Domains noetig
- Einfacheres Cleanup: Property entfernen statt Originalwert wiederherstellen

**Hinweis:** Keycloak-Theme kann kein `next/font` nutzen — dort wird weiterhin Google Fonts per `<link>` im FreeMarker-Template geladen (generisches Loading aus Realm-Attributes).

### Kuratierte Font-Liste (12 Fonts)

Basierend auf Verbreitung in SaaS-Produkten, Diversitaet und UI-Tauglichkeit:

| Font | Kategorie | Bekannte Nutzer | Google Fonts |
|------|-----------|-----------------|--------------|
| **System Default** | System | — | — |
| **Inter** | Modern Geometric | GitHub, Figma, Linear, Vercel | Ja |
| **Geist** | Modern Geometric | Vercel, Next.js | Ja (next/font) |
| **DM Sans** | Modern Geometric | Stripe Docs, Fintech | Ja |
| **Plus Jakarta Sans** | Modern Geometric | Moderne Startups, Fintech | Ja |
| **Roboto** | Neo-Grotesque | Google (alle Produkte), Android | Ja |
| **Source Sans 3** | Neo-Grotesque | Adobe, Developer Tools, Palantir | Ja |
| **IBM Plex Sans** | Neo-Grotesque | IBM, Carbon Design System | Ja |
| **Open Sans** | Humanist | WordPress, Trello, Enterprise | Ja |
| **Lato** | Humanist | Asana, Enterprise SaaS | Ja |
| **Nunito Sans** | Rounded/Friendly | Laravel, B2C SaaS | Ja |
| **Poppins** | Geometric Rounded | Canva, B2C SaaS | Ja |
| **Merriweather** | Serif | Medium, Content-Plattformen | Ja |

**Kategorisierung im UI:**

```
Modern         Inter, Geist, DM Sans, Plus Jakarta Sans
Classic        Roboto, Source Sans 3, IBM Plex Sans
Friendly       Open Sans, Lato, Nunito Sans, Poppins
Serif          Merriweather
```

### Font-Auswahl UI: Dropdown mit eigenem Font-Rendering

Jeder Eintrag im Select-Dropdown wird in seiner eigenen Font gerendert. Technische Umsetzung:

**Lazy Loading via Intersection Observer:**

```
Select oeffnet → Dropdown-Items werden gerendert
  → Intersection Observer beobachtet jedes Item
  → Item wird sichtbar → Google Font Stylesheet fuer diese Font wird geladen
  → Font rendert im Dropdown-Item
```

- Nur sichtbare Fonts werden geladen (Performance)
- Bereits geladene Fonts werden nicht erneut geladen (Set/Cache)
- Die aktuell ausgewaehlte/aktive Font wird immer geladen (fuer den Trigger-Text)

**Komponente:** Neues `FontSelect` in `features/settings/components/font-select.tsx`

- Nutzt shadcn `<Select>` als Basis
- Gruppiert nach Kategorie (Modern, Classic, Friendly, Serif)
- Font-Rendering pro Item: Via Intersection Observer Callback wird `element.style.fontFamily` gesetzt wenn das Item sichtbar wird (nicht als JSX `style={{}}` Prop, um die Architektur-Regel "kein style={{}}" zu respektieren — dynamische Fonts sind eine berechtigte Ausnahme die per Ref geloest wird)
- Intersection Observer auf dem `<SelectContent>` ScrollArea
- Hinweis: shadcn `<Select>` nutzt Radix Portal — der Intersection Observer muss auf dem Portal-Container operieren, nicht auf dem Trigger-Element

### Branding-Form Tabs

Die aktuelle vertikale Card-Anordnung wird durch horizontale Tabs ersetzt:

**Tab 1: Logo & Identity**
- Logo Upload (SVG, 2MB max)
- Favicon Upload (PNG/ICO, 0.5MB max)

**Tab 2: Colors**
- Primary Color
- Secondary Color
- Accent Color
- Danger Color (bisher unter "Advanced")
- Success Color (bisher unter "Advanced")
- Warning Color (bisher unter "Advanced")

**Tab 3: Typography**
- Font-Auswahl (neues FontSelect mit Preview)
- Border Radius

**Preview:** Bleibt rechts als sticky Column. Reagiert live auf Aenderungen in allen Tabs. Die Preview zeigt auch die ausgewaehlte Font an (Beispieltext wird in der Font gerendert).

### Aenderungen Backend

**`BrandingConfig` Record:** `fontFamily` bleibt `String` (kein Java Enum), aber die erlaubten Werte werden in der OpenAPI Spec als `enum` definiert (fliesst ueber Orval in die Frontend-Typen):

```java
// Bisherige erlaubte Werte: system, inter, geist, plus-jakarta-sans, dm-sans
// Neue erlaubte Werte: + roboto, source-sans-3, ibm-plex-sans, open-sans, lato, nunito-sans, poppins, merriweather
```

**`TenantBrandingSyncListener`:** `ALLOWED_FONTS` Set (Zeile 20-21) + `mapFontFamilyToCss()` Switch erweitern:

```java
// ALLOWED_FONTS erweitern (sonst werden neue Fonts beim Keycloak-Sync gedroppt!):
private static final Set<String> ALLOWED_FONTS = Set.of(
    "system", "inter", "geist", "plus-jakarta-sans", "dm-sans",
    "roboto", "source-sans-3", "ibm-plex-sans", "open-sans",
    "lato", "nunito-sans", "poppins", "merriweather"
);
```

**`TenantBrandingSyncListener`:** Font-CSS-Mapping erweitern:

```java
case "roboto" -> "'Roboto', sans-serif";
case "source-sans-3" -> "'Source Sans 3', sans-serif";
case "ibm-plex-sans" -> "'IBM Plex Sans', sans-serif";
case "open-sans" -> "'Open Sans', sans-serif";
case "lato" -> "'Lato', sans-serif";
case "nunito-sans" -> "'Nunito Sans', sans-serif";
case "poppins" -> "'Poppins', sans-serif";
case "merriweather" -> "'Merriweather', serif";
```

### Aenderungen Frontend

| Datei | Aenderung |
|-------|-----------|
| `branding-provider.tsx` | Font-CSS-Variable auf next/font Variable switchen + removeBrandingFromDocument() |
| `lib/fonts.ts` | Neue Datei: Alle 12 Fonts per next/font registriert + Mapping-Tabellen |
| `app/layout.tsx` | Alle Font-Variablen als CSS-Klassen auf `<html>` setzen |
| `branding-form.tsx` | Cards → Tabs Komponente (3 Tabs) |
| `font-select.tsx` | Neue Komponente: Font-Dropdown mit Lazy Loading |
| `branding-preview.tsx` | Font-Preview im Beispieltext |
| `en.json` / `de.json` | Neue i18n Keys fuer Tab-Labels, Font-Kategorien, neue Font-Namen |

---

## Paket 4: Breadcrumbs & Main-Layout Redesign

### Ist-Zustand

- `AppHeader` (h-14): Breadcrumb-Navigation in eigener Zeile
- Jede Page rendert zusaetzlich einen eigenen Header (Titel + Beschreibung + Actions)
- Redundanz: Breadcrumb zeigt z.B. "Tenants" → Page-Header zeigt "Tenants" nochmal
- Vertikaler Platz wird verschwendet

### Soll-Zustand: Kontextabhaengige Top-Bar

**Konzept (inspiriert von Notion, Stripe Dashboard):**

Die `AppHeader`-Komponente wird zur kontextabhaengigen Top-Bar, die den separaten Page-Header ersetzt. Es gibt zwei Modi:

**Modus 1: List View (flache Navigation)**
```
┌─────────────────────────────────────────────────────────────┐
│  Tenants                                    [+ Create]      │
│─────────────────────────────────────────────────────────────│
│  Search / Filter / Tabs                                     │
│─────────────────────────────────────────────────────────────│
│  Table Content...                                           │
└─────────────────────────────────────────────────────────────┘
```
- Seitentitel links (als h1)
- Action-Buttons rechts (Create, Export etc.)
- Keine Breadcrumbs (Sidebar zeigt aktive Seite)

**Modus 2: Detail View (tiefe Navigation)**
```
┌─────────────────────────────────────────────────────────────┐
│  ← Tenants / Acme Corp                    [Edit] [Delete]   │
│─────────────────────────────────────────────────────────────│
│  Detail Content...                                          │
└─────────────────────────────────────────────────────────────┘
```
- Breadcrumb-Pfad links (Eltern-Seite als Link + aktuelles Element)
- Action-Buttons rechts
- Breadcrumbs kollabieren bei tiefer Navigation (> 3 Ebenen):
  `← Apps / ... / Products / Sneaker XL`

**Modus 3: Deep Nested View (App Domain, zukuenftig)**
```
┌─────────────────────────────────────────────────────────────┐
│  ← ... / Products / Sneaker XL             [Save] [Delete]  │
│─────────────────────────────────────────────────────────────│
│  Content...                                                  │
└─────────────────────────────────────────────────────────────┘
```
- Mittlere Breadcrumb-Segmente werden als `...` mit Dropdown kollabiert
- Erstes und letztes Segment immer sichtbar

### API der neuen AppHeader-Komponente

```tsx
// Union Type: List-Modus ODER Detail-Modus, nie beides gleichzeitig
type PageHeaderProps = {
  actions?: React.ReactNode;
} & (
  | { mode: "list"; title: string; description?: string; breadcrumbs?: never }
  | { mode: "detail"; breadcrumbs: BreadcrumbSegment[]; title?: never; description?: never }
);
```

- `mode: "list"`: Titel links (als h1), optionale Description als muted Text darunter, Actions rechts
- `mode: "detail"`: Breadcrumbs links (letztes Segment als aktueller Seitentitel), Actions rechts. Keine Description in Detail-Modus.
- `actions`: Slot fuer Buttons, wird rechts ausgerichtet
- Breadcrumb-Kollaps (> 3 Segmente): Mittlere Segmente als `...` mit shadcn `<DropdownMenu>` (keyboard-navigierbar via Tab/Enter/Space)

### Aenderungen

**Betroffene Seiten (alle Pages die aktuell eigenen Header rendern):**

| Bereich | Seiten |
|---------|--------|
| Platform | Tenant List, Tenant Detail, Admin List, System Settings |
| Workspace | Dashboard, Users List, User Detail, Roles List, Role Detail, Settings/Branding |
| Zukuenftig | Apps List, App Detail, Content Types, Content Entries |

**Jede Seite wird migriert:**
- Eigener Page-Header entfernt
- `title`/`breadcrumbs` + `actions` an die neue `AppHeader`-Komponente uebergeben
- Layout-Wrapper im Content-Bereich angepasst (kein redundanter Titel mehr)

### Sticky Verhalten

- Die Top-Bar ist sticky (`sticky top-0 z-10`)
- Beim Scrollen bleibt sie sichtbar
- Hoehe: `h-14` (gleich wie aktuell)
- Border-bottom als visueller Separator

---

## Paket 5: Sidebar Contrast Fix (Light Mode)

### Ist-Zustand

```css
/* Light Mode */
--sidebar-accent: oklch(0.98 0.003 277);    /* Indigo 50 #EEF2FF — kaum sichtbar */
--sidebar-accent-foreground: oklch(0.585 0.233 277); /* Indigo 500 — Textfarbe OK */

/* Dark Mode (zum Vergleich — funktioniert gut) */
--sidebar-accent: oklch(0.268 0.007 34);    /* Stone 800 — deutlich sichtbar */
--sidebar-accent-foreground: oklch(0.869 0.005 56); /* Stone 300 */
```

Hover/Active-Background im Light Mode ist `Indigo 50` auf `Stone 50` Background — praktisch unsichtbar (Lightness 0.98 vs 0.985).

### Soll-Zustand

```css
/* Light Mode — korrigiert */
--sidebar-accent: oklch(0.94 0.028 277);    /* Indigo 100 #E0E7FF — sichtbar */
--sidebar-accent-foreground: oklch(0.457 0.24 277); /* Indigo 600 — etwas kraeftiger */
```

**Warum Indigo 100 statt 200:**
- Indigo 200 (`oklch(0.87 0.065 277)`) waere zu stark — der Hover-Effekt soll subtil bleiben, nur eben sichtbar
- Indigo 100 bietet genug Kontrast (Delta-Lightness ~0.045) ohne aufdringlich zu wirken
- Im Dark Mode ist das Verhaeltnis aehnlich: Stone 800 auf Stone 900 (Delta ~0.052)

### Aenderung

Nur `globals.css`, Zeile 51-52:

```css
/* Vorher */
--sidebar-accent: oklch(0.98 0.003 277);    /* Indigo 50 #EEF2FF */
--sidebar-accent-foreground: oklch(0.585 0.233 277); /* Indigo 500 */

/* Nachher */
--sidebar-accent: oklch(0.94 0.028 277);    /* Indigo 100 #E0E7FF */
--sidebar-accent-foreground: oklch(0.457 0.24 277);  /* Indigo 600 */
```

---

## Reihenfolge & Abhaengigkeiten

```
Paket 5 (Sidebar Contrast)     — Standalone, schnell, keine Abhaengigkeiten
    ↓
Paket 1 (Keycloak Login)       — Neues Theme-Grundgeruest
    ↓
Paket 2 (Invite Flow)          — Nutzt neues Theme aus Paket 1
    ↓
Paket 3 (Font-Auswahl)         — Aendert Branding-Form + Provider
    ↓
Paket 4 (Layout Redesign)      — Aendert alle Pages, kommt zuletzt
```

Paket 5 wird vorgezogen weil es ein Einzeiler ist und sofort Wirkung zeigt.

---

## Testplan

### Paket 1: Keycloak Login
- [ ] Login-Seite rendert mit Tenant-Branding (Logo, Farben, Font, Border-Radius)
- [ ] Dark Mode funktioniert (prefers-color-scheme)
- [ ] Focus-Ring sichtbar auf Inputs und Buttons (Keyboard-Navigation)
- [ ] Fehlerhafte Credentials zeigen Error-Message
- [ ] "Forgot Password" Link fuehrt zur Reset-Seite
- [ ] Password-Reset-Seite ist konsistent gestylt
- [ ] Error-Seite (abgelaufener Link) ist konsistent gestylt
- [ ] Info-Seite ("E-Mail gesendet") ist konsistent gestylt
- [ ] Responsive: Mobile-Layout (< 640px) funktioniert
- [ ] Google Fonts Loading funktioniert fuer alle 12 Fonts (Weights 400-700)

### Paket 2: Invite Flow
- [ ] Invite-E-Mail → Klick → direkt Passwort-Formular (kein Zwischenklick)
- [ ] Passwort-Formular zeigt "Welcome" Branding
- [ ] Nach Passwort-Setzen: Redirect zur Tenant-Subdomain
- [ ] Platform-Admin Invite: Redirect zur Platform-URL
- [ ] Abgelaufener Invite-Link: Error-Seite mit Branding
- [ ] Redirect-URI Whitelist: Keycloak Client hat passende Valid Redirect URIs konfiguriert

### Paket 3: Font-Auswahl
- [ ] Font-Bug gefixt: Ausgewaehlte Font wird im gesamten Workspace angewendet
- [ ] Font-Cleanup: Beim Verlassen des Workspace wird --font-sans auf Geist zurueckgesetzt
- [ ] Vitest: buildCssOverrides() + removeBrandingFromDocument() mit Font-Handling (TDD)
- [ ] 12 Fonts in Dropdown verfuegbar, gruppiert nach Kategorie
- [ ] Jeder Dropdown-Eintrag rendert in seiner eigenen Font
- [ ] Fonts werden lazy geladen (Intersection Observer)
- [ ] Preview zeigt Beispieltext in ausgewaehlter Font
- [ ] Branding-Form nutzt Tabs statt Cards
- [ ] Tab-Wechsel behaelt Form-State bei (lokaler Tab-State, kein URL-Hash noetig)
- [ ] Backend akzeptiert alle neuen Font-Werte
- [ ] ALLOWED_FONTS Whitelist im TenantBrandingSyncListener erweitert
- [ ] Keycloak-Theme laedt korrekte Google Font

### Paket 4: Layout Redesign
- [ ] Alle Listenseiten: Titel + Actions in Top-Bar, kein separater Page-Header
- [ ] Alle Detailseiten: Breadcrumbs + Actions in Top-Bar
- [ ] Top-Bar ist sticky beim Scrollen
- [ ] Breadcrumbs kollabieren bei > 3 Segmenten
- [ ] Sidebar Active-State stimmt mit aktueller Seite ueberein
- [ ] Kein visueller Bruch zwischen Seiten

### Paket 5: Sidebar Contrast
- [ ] Light Mode: Hover auf Sidebar-Item zeigt sichtbaren Background-Wechsel
- [ ] Light Mode: Active Sidebar-Item hat deutlichen Background
- [ ] Light Mode: Keyboard-Focus sichtbar auf Sidebar-Items
- [ ] Dark Mode: Unveraendert, funktioniert weiterhin
- [ ] Tenant-Branding ueberschreibt korrekt (--sidebar-accent-foreground)
- [ ] Hinweis: Bei Tenants mit sehr heller Primary-Color (z.B. Gelb) kann --sidebar-accent-foreground wenig Kontrast gegen --sidebar-accent haben — das ist ein bestehendes Problem des Branding-Overrides und wird hier nicht geloest
