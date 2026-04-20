# Branding Implementation — Decision Log

## Decision 1: OKLCH Color Space for CSS Variable Injection

**Chosen:** Convert hex colors to OKLCH before injecting as CSS variables.

**Why:** The existing `globals.css` uses OKLCH throughout. Injecting hex values would create inconsistency and break `color-mix()` operations that rely on OKLCH color space.

**Alternatives considered:**
- **Inject hex directly** — simpler, but breaks the OKLCH-based theme system and `color-mix()` operations
- **Switch globals.css to HSL** — too disruptive, would require touching every color definition
- **Use `hexToHsl()` (existing utility)** — Tailwind v4 doesn't use HSL natively; OKLCH is the native format

**Trade-off:** Added ~50 lines of color math (`hexToOklch`), but ensures consistency with the existing theme system.

---

## Decision 2: secondaryColor ≠ shadcn --secondary

**Chosen:** Tenant's `secondaryColor` maps to a custom `--brand-secondary` CSS variable, NOT to shadcn's `--secondary`.

**Why:** In shadcn/ui, `--secondary` is a neutral background color (Stone 100). The tenant's "secondary color" is a brand color for specific UI elements (badges, secondary buttons). Overriding `--secondary` would break the neutral background system.

**Alternatives considered:**
- **Override --secondary** — would make all secondary backgrounds (dropdowns, tags) use the brand color, breaking the design
- **Remove secondaryColor entirely** — too restrictive; tenants need a second brand color

---

## Decision 3: No Custom CSS Field

**Chosen:** No textarea for arbitrary custom CSS.

**Why:** XSS risk and broken layout risk outweigh the benefit for non-technical tenant admins. The structured fields (colors, font, radius) cover 95% of branding needs.

**Alternatives considered:**
- **Sandboxed CSS textarea** — still allows `position: fixed`, `z-index: 99999`, and other disruptive properties
- **CSS field with allow-list** — complex to maintain, still not safe against creative abuse

**Future option:** If needed, add behind a platform admin permission (not tenant admin).

---

## Decision 4: Server-Side + Client-Side CSS Injection

**Chosen:** `BrandingProvider` runs client-side in workspace layout, applying CSS variables via `useEffect`.

**Why:** The workspace layout is already a client component (`"use client"`). Server-side injection would require restructuring to a server component, which conflicts with the existing permission/navigation hooks.

**Alternatives considered:**
- **Server-side `<style>` tag injection** — would prevent flash of default theme, but requires server component which conflicts with existing client-side hooks (usePermissions, useTranslations)
- **Middleware injection** — could inject CSS vars via response headers, but too hacky and doesn't work with Next.js streaming

**Trade-off:** Brief flash of default theme on first load. Acceptable because the workspace already shows a loading skeleton while fetching initial data.

---

## Decision 5: Font Options as Curated Enum

**Chosen:** Fixed list of 5 fonts: System Default, Inter, Geist, Plus Jakarta Sans, DM Sans.

**Why:** Arbitrary Google Fonts selection creates performance risk (each font is a network request) and design risk (bad fonts break layouts). A curated list ensures quality.

**Alternatives considered:**
- **Free-text Google Fonts URL** — too much risk of broken fonts, slow loads
- **Full Google Fonts dropdown (1000+ fonts)** — overwhelming UX, most fonts look bad in UI context
- **No font option** — too restrictive for whitelabel use case

---

## Decision 6: Border Radius as Enum (Not Slider)

**Chosen:** 5 predefined radius values: 0, 0.25rem, 0.5rem, 0.75rem, 1rem.

**Why:** A continuous slider creates infinite possibilities, most of which look bad. Predefined values are easier to test and ensure consistent design.

**Alternatives considered:**
- **Continuous slider (0-2rem)** — too many bad outcomes, harder to match Keycloak theme
- **Only 3 options (sharp/default/rounded)** — too few; 5 hits the sweet spot

---

## Decision 7: No DB Migration for New Fields

**Chosen:** No Flyway migration. New fields are added to the Java record and stored in the existing JSONB `config` column.

**Why:** JSONB columns support schema evolution at the application level. Old rows return `null` for new fields, and the frontend applies defaults. This is a deliberate architectural choice (see `docs/01-content-domain.md`).

**Alternatives considered:**
- **New table `tenant_branding`** — unnecessary normalization for a simple config object
- **ALTER TABLE ADD COLUMN** — not possible, the fields are inside JSONB

---

## Decision 8: Live Preview via Scoped CSS Variables

**Chosen:** Preview panel applies CSS variable overrides to a scoped `<div>` using `applyBrandingToElement()`, not to `:root`.

**Why:** Preview must show changes without affecting the rest of the page. Only the save action persists to `:root` (via `BrandingProvider`).

**Alternatives considered:**
- **iframe preview** — too complex, would need a separate endpoint
- **Override :root directly** — would change the entire page while editing, confusing UX
