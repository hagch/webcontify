# Branding Enhancements Design

**Datum:** 2026-03-24
**Status:** Approved
**Scope:** shadcn Color Picker, SVG Logo, Tenant-Name in Sidebar, Severity Colors

---

## Ueberblick

Vier Erweiterungen des Branding-Systems:

1. **shadcn Color Picker** — nativer `<input type="color">` wird durch Popover mit Farbfeld + Hue-Slider ersetzt
2. **SVG-only Logo Upload** — Logo akzeptiert nur SVG, Anzeige via `<img>` (XSS-sicher)
3. **Tenant-Name + Logo in Sidebar** — `TenantConfigResponse` liefert `name`/`slug`, Sidebar zeigt dynamisch Tenant-Logo und -Name
4. **Severity Colors** — `dangerColor`, `successColor`, `warningColor` als neue Branding-Felder

---

## 1. shadcn Color Picker

### Komponente: `components/ui/color-picker.tsx`

Popover-basierter Color Picker:
- **Trigger:** Kleiner Farbswatch-Button (zeigt aktuelle Farbe)
- **Popover-Inhalt:**
  - Saturation/Brightness-Farbfeld (CSS-Gradient-basiert, kein Canvas)
  - Hue-Slider darunter (0-360 Grad)
  - Hex-Input fuer exakte Werte
- **Props:** `value: string` (hex), `onChange: (hex: string) => void`

### Aenderungen an `ColorPickerField`

- Nativer `<input type="color">` wird durch `<ColorPicker>` ersetzt
- Hex-Input daneben bleibt bestehen
- Bidirektionale Sync: Hex-Input und Picker aktualisieren sich gegenseitig

### Farbkonvertierung

- Erweitere `color-utils.ts` um `hexToHsl()` und `hslToHex()` fuer interne Picker-Logik
- Bestehende `hexToOklch()` bleibt fuer CSS-Variable-Generierung

---

## 2. SVG-only Logo Upload

### Aenderungen an `LogoUpload`

- `accept` fuer Logo-Card: `image/svg+xml` (nur SVG)
- Favicon bleibt `image/png, image/x-icon` (unveraendert)
- Anzeige: `<img src="...">` statt Inline-SVG (XSS-Schutz, skaliert via `viewBox`)
- Max-Size: 2MB bleibt

### Branding-Form

- Logo-Card `accept` Prop auf `image/svg+xml` aendern
- i18n: Fehlermeldung/Hinweis anpassen ("Nur SVG-Dateien erlaubt")

---

## 3. Tenant-Name + Logo in Sidebar

### Backend: `TenantConfigResponse` erweitern

OpenAPI (`tenant-api.yaml`):
```yaml
TenantConfigResponse:
  type: object
  properties:
    name:
      type: string
    slug:
      type: string
    branding:
      $ref: '#/components/schemas/BrandingConfigResponse'
    email:
      $ref: '#/components/schemas/EmailConfigResponse'
    domain:
      $ref: '#/components/schemas/DomainConfigResponse'
```

Backend-Service fuellt `name` und `slug` aus dem Tenant-Kontext (via `TenantInformation`).

### Frontend: Sidebar

`AppSidebar` liest Tenant-Daten via `useGetTenantSelfConfig()`:

- **Logo vorhanden:** `<img>` mit Logo-URL, `size-8` im Square, `object-contain`
- **Kein Logo:** Erster Buchstabe des Tenant-Namens auf Primary-Hintergrund (wie bisher das "W")
- **Text:** Tenant-Name statt "Webcontify"
- **Collapsed-Mode:** Nur Logo/Buchstabe sichtbar (bestehendes Verhalten)

### BrandingProvider erweitern

`BrandingValues` bekommt `tenantName: string | null` und `tenantSlug: string | null`.
`useBranding()` liefert diese Werte mit.

---

## 4. Severity Colors in Advanced Settings

### OpenAPI

3 neue optionale Felder in `BrandingConfigResponse` und `UpdateBrandingConfigRequest`:

```yaml
dangerColor:
  type: string
  pattern: '^#[0-9a-fA-F]{6}$'
successColor:
  type: string
  pattern: '^#[0-9a-fA-F]{6}$'
warningColor:
  type: string
  pattern: '^#[0-9a-fA-F]{6}$'
```

### Backend

- Entity: 3 neue `VARCHAR` Spalten in Branding-Config-Tabelle
- Migration: `V20260324_001__add_severity_colors.sql`
- Mapper: Neue Felder mappen

### Frontend

**Schema (`tenant-config.ts`):**
- 3 neue `hexColorField`s: `dangerColor`, `successColor`, `warningColor`

**Form (`branding-form.tsx`):**
- 3 neue `ColorPickerField`s im Advanced-Bereich (nach Border Radius)

**BrandingProvider:**
- Defaults aus `globals.css`: `#EF4444` (Danger), `#059669` (Success), `#F59E0B` (Warning)
- Ueberschreibt `--destructive`, `--success`, `--warning` CSS Variables wenn gesetzt
- `removeBrandingFromDocument()` raesumt diese auch auf

**Preview:**
- 3 Severity-Badges im Preview ergaenzen (Danger, Success, Warning)

### Defaults

| Severity | Hex | OKLCH (Light) |
|----------|-----|--------------|
| Danger | `#EF4444` | `oklch(0.637 0.237 25)` |
| Success | `#059669` | `oklch(0.596 0.145 163)` |
| Warning | `#F59E0B` | `oklch(0.769 0.188 70)` |

---

## Nicht im Scope

- SVG-Sanitizing im Backend (nicht noetig bei `<img>`-Einbindung)
- Inline-SVG-Rendering (kein Bedarf aktuell)
- Dark-Mode-spezifische Severity-Farben (nutzen gleiche Logik wie Primary/Accent)
- Foreground-Farben fuer Severities (werden automatisch berechnet oder bleiben Default)

---

## Dateien die geaendert werden

### Backend
- `openapi/tenant-api.yaml` — Response + Request Schemas
- Tenant-Config Service — `name`/`slug` in Response fuellen
- Branding Entity — 3 neue Felder
- DB Migration — neue Spalten
- Mapper — neue Felder

### Frontend
- `components/ui/color-picker.tsx` — **NEU**
- `lib/color-utils.ts` — HSL-Konvertierung ergaenzen
- `lib/schemas/tenant-config.ts` — 3 neue Felder
- `features/settings/components/color-picker-field.tsx` — shadcn Picker nutzen
- `features/settings/components/logo-upload.tsx` — SVG accept
- `features/settings/components/branding-form.tsx` — Severity Fields + SVG
- `features/settings/components/branding-preview.tsx` — Severity Badges
- `providers/branding-provider.tsx` — Severity CSS Vars + tenantName/Slug
- `components/layout/app-sidebar.tsx` — Tenant Logo + Name
- `messages/en.json` + `messages/de.json` — neue i18n Keys
- `generated/tenant.ts` — regeneriert via Orval
