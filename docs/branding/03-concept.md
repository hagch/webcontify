# Branding Concept & Implementation Plan

## UI Concept: Branding Configuration Page

### Simple Mode (Default)

The branding settings page (`/settings/branding`) presents five core fields:

1. **Logo Upload** — PNG/JPEG, max 2MB, shown in sidebar + Keycloak login + emails
2. **Favicon Upload** — PNG/ICO, max 0.5MB, shown in browser tab
3. **Primary Color** — Hex picker, used for buttons, links, active states, sidebar highlights
4. **Secondary Color** — Hex picker, used for secondary buttons, badges
5. **Accent Color** — Hex picker, used for success/highlight states (NEW field)

Layout: Two-column on desktop — form on left (60%), live preview panel on right (40%). Single column on mobile with preview collapsed into a toggle.

### Advanced Mode (Toggle)

A collapsible "Advanced" section below the simple fields:

6. **Font Family** — Dropdown with options: System Default, Inter, Geist (current), Plus Jakarta Sans, DM Sans. These are all Google Fonts already commonly used in SaaS products.
7. **Border Radius** — Slider from 0px (sharp) to 1rem (rounded), default 0.5rem. Controls `--radius` CSS variable.

**Decision: No custom CSS field.** The risk of XSS/broken layouts outweighs the benefit for non-technical users. If needed later, can be added behind a platform admin permission.

### Live Preview Panel

The preview panel shows a mini-mockup with:
- A button (primary), a secondary button, a badge (accent)
- A card with a table row
- A mini login page mockup (shows how Keycloak will look)
- Updates instantly as user changes colors (no save needed)
- "Save" button persists to backend + triggers Keycloak sync

### Self-Service vs Platform Admin

- **Workspace branding page** (`/settings/branding`): Already exists, uses `useGetTenantSelfConfig` + `useUpdateTenantSelfBranding`
- **Platform admin page** (`/tenants/[id]/config`): Already exists, uses admin endpoints
- Both share the same `BrandingForm` component — only the API hooks differ

---

## Technical Plan

### 1. Data Model Extension

**BrandingConfig record** (add 3 new fields):

```java
public record BrandingConfig(
    String logoStorageKey,
    String faviconStorageKey,
    String primaryColor,       // existing
    String secondaryColor,     // existing
    String accentColor,        // NEW — hex
    String fontFamily,         // NEW — enum string: "system", "inter", "geist", "plus-jakarta-sans", "dm-sans"
    String borderRadius        // NEW — CSS value: "0", "0.25rem", "0.5rem", "0.75rem", "1rem"
) {}
```

**No DB migration needed** — BrandingConfig is stored as JSONB. New fields are nullable; old rows get `null` (defaults apply in frontend).

### 2. OpenAPI Spec Changes

Extend `UpdateBrandingConfigRequest` and `BrandingConfigResponse` with:

```yaml
accentColor:
  type: string
  pattern: '^#[0-9a-fA-F]{6}$'
fontFamily:
  type: string
  enum: [system, inter, geist, plus-jakarta-sans, dm-sans]
borderRadius:
  type: string
  enum: ['0', '0.25rem', '0.5rem', '0.75rem', '1rem']
```

### 3. CSS Variable Injection Strategy

**Problem:** `globals.css` uses OKLCH color space. Converting hex → OKLCH at runtime is complex and fragile.

**Decision:** Override only the semantic CSS variables that tenant branding should affect. Use `color-mix()` and hex values directly — modern browsers support hex in CSS variables, and Tailwind v4's `@theme inline` maps CSS variables to color utilities.

**Implementation:** A `BrandingProvider` component wraps the workspace layout. It:
1. Reads tenant branding config from React Query cache (already fetched by workspace layout)
2. Converts hex colors to CSS variable overrides on `document.documentElement`
3. For the preview panel: applies overrides to a scoped `<div>` instead of `:root`

**CSS Variables affected by tenant branding:**

| Tenant Field | CSS Variables Overridden |
|---|---|
| primaryColor | `--primary`, `--ring`, `--sidebar-primary`, `--sidebar-accent-foreground`, `--sidebar-ring`, `--chart-1` |
| secondaryColor | (no override — "secondary" in shadcn means neutral background, not brand secondary) |
| accentColor | `--accent`, `--chart-2` |
| borderRadius | `--radius` |
| fontFamily | `--font-sans` (via next/font dynamic import) |

**Important design decision:** `secondaryColor` from branding does NOT map to shadcn's `--secondary` CSS variable. In shadcn/ui, `--secondary` is a neutral background color (Stone 100). The tenant's "secondary color" is a brand color used for specific UI elements (badges, secondary buttons). We'll handle it via a custom `--brand-secondary` CSS variable and apply it to specific components.

**Hex → OKLCH conversion:** We need a `hexToOklch()` utility since the existing CSS uses OKLCH. This is a straightforward mathematical conversion (hex → sRGB → linear RGB → OKLCH).

### 4. Keycloak Theme Extension

Extend the FTL template and CSS to support the new fields:

```css
/* template.ftl additions */
--wc-accent: ${(realm.attributes['brandAccentColor']!'#10B981')};
--wc-radius: ${(realm.attributes['brandBorderRadius']!'0.5rem')};
--wc-font: ${(realm.attributes['brandFontFamily']!'system-ui, sans-serif')};
```

Extend `TenantBrandingSyncListener` to sync new attributes to Keycloak realm.

### 5. Event Extension

Add new fields to `TenantBrandingUpdatedEvent`:

```java
public record TenantBrandingUpdatedEvent(
    UUID tenantId,
    String tenantName,
    String keycloakRealmName,
    String primaryColor,
    String secondaryColor,
    String accentColor,        // NEW
    String fontFamily,         // NEW
    String borderRadius,       // NEW
    String logoPublicUrl
) {}
```

### 6. Frontend Architecture

```
BrandingProvider (new)
├── Reads useGetTenantSelfConfig()
├── Injects CSS variables on :root via useEffect
├── Provides BrandingContext for preview override
└── Handles defaults when no branding configured

BrandingForm (enhanced)
├── Simple fields: logo, favicon, primary, secondary, accent
├── Advanced toggle: font, radius
├── Live preview panel (reads local form state, not saved config)
└── Reset to defaults button

BrandingPreview (new)
├── Scoped container with CSS variable overrides from form state
├── Shows: buttons, card, table row, login mockup
└── Updates on every form change (no debounce needed — CSS vars are instant)
```

---

## TDD Implementation Steps

### Step 1: OpenAPI Spec Extension
- Add `accentColor`, `fontFamily`, `borderRadius` to `UpdateBrandingConfigRequest` and `BrandingConfigResponse`
- Regenerate backend + frontend code
- Commit: `feat: extend branding OpenAPI spec with accent, font, radius`

### Step 2: Backend — BrandingConfig Model Extension
- **Test:** Unit test that BrandingConfig record accepts new fields and serializes to/from JSON correctly
- **Implement:** Add fields to `BrandingConfig` record
- **Test:** Integration test that PUT `/api/v1/tenant/config/branding` accepts and persists new fields
- Commit: `feat: extend BrandingConfig with accent color, font family, border radius`

### Step 3: Backend — Event & Keycloak Sync Extension
- **Test:** Unit test that `TenantBrandingSyncListener` maps new fields to Keycloak attributes
- **Implement:** Extend `TenantBrandingUpdatedEvent`, update sync listener, update `KeycloakAdminService`
- Commit: `feat: sync accent, font, radius to Keycloak realm attributes`

### Step 4: Keycloak Theme Extension
- **Implement:** Add new CSS variables to `template.ftl`, extend `styles.css` to use them
- **Implement:** Update email templates for accent color
- Commit: `feat: extend Keycloak theme with accent, font, radius CSS variables`

### Step 5: Frontend — hexToOklch Utility
- **Test:** Unit test for `hexToOklch()` with known color conversions
- **Implement:** `hexToOklch()` function in `lib/color-utils.ts`
- Commit: `feat: add hexToOklch color conversion utility`

### Step 6: Frontend — BrandingProvider
- **Test:** Unit test that BrandingProvider sets CSS variables on document element
- **Implement:** `BrandingProvider` component + context
- **Integrate:** Add to workspace layout
- Commit: `feat: add BrandingProvider for dynamic tenant theming`

### Step 7: Frontend — BrandingPreview Component
- **Test:** Unit test that preview renders with overridden colors
- **Implement:** `BrandingPreview` component with scoped CSS variables
- Commit: `feat: add live branding preview component`

### Step 8: Frontend — Enhanced BrandingForm
- **Implement:** Add accent color picker, font dropdown, radius slider
- **Implement:** Add advanced mode toggle
- **Implement:** Wire preview panel to form state
- **Implement:** i18n keys for all new labels
- Commit: `feat: enhance branding form with accent, font, radius and live preview`

### Step 9: Frontend — Orval Regeneration & Integration
- Regenerate Orval hooks with new schema
- Update Zod validation schema
- Wire new fields to form
- Commit: `feat: integrate new branding fields end-to-end`

### Step 10: Branding Defaults & Reset
- **Test:** BrandingProvider applies defaults when no branding configured
- **Implement:** "Reset to defaults" button in form
- **Implement:** Default values constant shared between provider and form
- Commit: `feat: add branding defaults and reset functionality`

### Step 11: Platform Admin Branding Config
- Verify platform admin config page works with new fields (should work via shared BrandingForm component)
- Add preview panel to admin config page
- Commit: `feat: add branding preview to platform admin config page`

---

## Defaults

| Field | Default Value | Fallback Source |
|-------|---------------|-----------------|
| primaryColor | `#6366F1` (Indigo 500) | globals.css |
| secondaryColor | `#10B981` (Emerald 500) | globals.css |
| accentColor | `#10B981` (Emerald 500) | globals.css |
| fontFamily | `system` | Geist via next/font |
| borderRadius | `0.5rem` | globals.css |
| logo | none | — |
| favicon | none | — |

---

## Out of Scope (YAGNI)

- Dark mode branding overrides (dark mode adjusts brightness automatically)
- Per-page branding
- Custom CSS injection
- Email branding preview
- AI-powered theme suggestions
- Background image/pattern customization
