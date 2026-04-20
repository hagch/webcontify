# Branding Implementation — Summary

## What Was Implemented

### Backend
- **Extended `BrandingConfig`** with `accentColor` (hex), `fontFamily` (enum), `borderRadius` (enum)
- **Extended `TenantBrandingUpdatedEvent`** to carry new fields
- **Updated `TenantBrandingSyncListener`** with validation for fonts (allow-list) and radii (allow-list), maps font family to CSS font-stack string
- **Updated `TenantConfigMapper`** for enum ↔ string conversion
- **No DB migration needed** — JSONB schema evolution

### OpenAPI
- Added `accentColor`, `fontFamily`, `borderRadius` to `UpdateBrandingConfigRequest` and `BrandingConfigResponse`
- All fields optional, backward compatible

### Keycloak Theme
- **Login template** (`template.ftl`): Added `--wc-accent`, `--wc-radius`, `--wc-font` CSS variables with realm attribute fallbacks
- **Dynamic Google Fonts**: Loads font stylesheet when non-system font is configured
- **Login CSS** (`styles.css`): Applied `--wc-radius` to buttons, inputs, login card; applied `--wc-font` to body
- **Email defaults** (`messages_en.properties`): Added default values for new attributes

### Frontend
- **`hexToOklch()` utility** — Converts hex colors to OKLCH format matching `globals.css` color space
- **`BrandingProvider`** — Injects tenant CSS variables on `:root` for workspace pages; cleanup on unmount
- **`BrandingPreview`** — Scoped live preview with buttons, card, progress bar, login mockup
- **Enhanced `BrandingForm`** — Three color pickers (primary, secondary, accent), collapsible advanced section (font dropdown, radius selector), reset button, two-column layout with sticky preview
- **Orval regeneration** — Updated generated React Query hooks and Zod types
- **i18n** — All new labels in `en.json` + `de.json`

### Tests
- `hexToOklch` unit tests (6 cases: black, white, indigo, emerald, invalid, case-insensitive)
- Updated `BrandingForm` tests for new structure and i18n
- `TenantBrandingSyncListener` tests for new attribute mapping + validation (invalid font, invalid radius)
- `TenantConfigServiceBrandingEventTest` updated for new event fields
- `TenantConfigControllerIntegrationTest` — new test for accent/font/radius API roundtrip

---

## What Is Still Open

### Not Implemented (By Design)
- **Dark mode branding overrides** — Dark mode automatically adjusts brightness; separate overrides would be over-engineering
- **Custom CSS injection** — XSS risk outweighs benefit (see decision log)
- **Email branding preview** — Nice-to-have, not critical for MVP
- **Per-page branding** — All pages share one theme

### Future Enhancements
1. **Server-side CSS injection** — Eliminate flash of default theme by injecting `<style>` in server layout. Requires refactoring workspace layout to server component.
2. **Font loading in BrandingProvider** — Currently, only Keycloak dynamically loads Google Fonts. The workspace uses Geist via `next/font`. To fully support custom fonts in the workspace, need dynamic `next/font` loading or `<link>` injection.
3. **Favicon injection** — The `BrandingProvider` doesn't inject the tenant's favicon into `<head>`. Would need a `useEffect` with `document.querySelector('link[rel="icon"]')`.
4. **Logo in sidebar** — The `AppSidebar` doesn't read the branding logo. Would need to use `useBranding()` hook to show tenant logo instead of "Webcontify" text.
5. **Platform admin branding preview** — The admin config page at `/tenants/[id]/config` uses different API hooks. The `BrandingPreview` component works, but isn't wired up to the admin form yet (they use different generated hooks).

---

## Known Limitations

1. **OKLCH conversion accuracy** — The `hexToOklch()` conversion is mathematically correct but may produce slightly different values than hand-tuned OKLCH values in `globals.css`. The visual difference is negligible.
2. **Font loading delay** — Custom fonts in Keycloak login page load via Google Fonts CDN. First visit may show a brief flash of system font.
3. **No font preview in dropdown** — The font family dropdown shows font names in the default font, not in the actual font face. Would need dynamic font loading in the settings page.
4. **borderRadius on Keycloak** — The Keycloak login page applies radius to buttons, inputs, and the main card. Other Keycloak elements (alerts, tooltips) still use default PatternFly radii.

---

## Files Changed

### New Files
| File | Purpose |
|------|---------|
| `apps/platform/src/providers/branding-provider.tsx` | CSS variable injection + context |
| `apps/platform/src/features/settings/components/branding-preview.tsx` | Live preview component |
| `docs/branding/01-analysis.md` | Codebase analysis |
| `docs/branding/02-research.md` | Best practice research |
| `docs/branding/03-concept.md` | Concept & implementation plan |
| `docs/branding/04-decisions.md` | Decision log |
| `docs/branding/05-summary.md` | This file |

### Modified Files
| File | Change |
|------|--------|
| `openapi/tenant-api.yaml` | Added accentColor, fontFamily, borderRadius |
| `TenantConfig.java` | Extended BrandingConfig record |
| `TenantBrandingUpdatedEvent.java` | Added 3 new fields |
| `TenantConfigService.java` | Pass new fields in event |
| `TenantBrandingSyncListener.java` | Validate + sync new attributes |
| `TenantConfigMapper.java` | Enum conversion for new fields |
| `template.ftl` | New CSS variables + Google Fonts loading |
| `styles.css` | Apply radius + font to Keycloak elements |
| `messages_en.properties` | Default branding values |
| `color-utils.ts` | Added hexToOklch() |
| `tenant-config.ts` (schema) | Added accentColor, fontFamily, borderRadius |
| `branding-form.tsx` | Full rewrite with preview + advanced section |
| `(workspace)/layout.tsx` | BrandingProvider integration |
| `en.json` / `de.json` | i18n keys for new fields |
| `color-utils.test.ts` | hexToOklch tests |
| `branding-form.test.tsx` | Updated for new structure |
| `TenantBrandingSyncListenerTest.java` | New validation tests |
| `TenantConfigServiceBrandingEventTest.java` | Updated event fields |
| `TenantConfigControllerIntegrationTest.java` | New fields API test |

### No New Dependencies Added
All changes use existing libraries (React Hook Form, Zod, shadcn/ui, next-intl, MapStruct, Spring Modulith).
