# Branding Codebase Analysis

## Executive Summary

The codebase already has a **functional but basic** branding system. Tenant admins can configure primary/secondary colors, upload logos and favicons. These settings sync to Keycloak login/email themes via Spring Modulith events. However, the **Platform UI (Next.js workspace) does not apply tenant branding** — it uses hardcoded CSS variables.

---

## What Already Exists

### Backend

| Component | Location | Status |
|-----------|----------|--------|
| BrandingConfig model | `tenant/model/TenantConfig.java` | 2 colors + logo + favicon |
| TenantConfigService | `tenant/service/TenantConfigService.java` | CRUD for branding config |
| TenantBrandingUpdatedEvent | `tenant/event/TenantBrandingUpdatedEvent.java` | Event-driven sync |
| TenantBrandingSyncListener | `identity/service/TenantBrandingSyncListener.java` | Syncs to Keycloak realm attributes |
| KeycloakAdminService | `identity/service/KeycloakAdminService.java` | Updates realm branding + localization texts |
| TenantFileService | `tenant/service/TenantFileService.java` | S3 upload (public config/ + private files/) |
| Input validation | Sync listener | Hex color regex, safe URL pattern |

### Database

- Branding stored as **JSONB** in `tenant.config` column — no separate table
- Schema evolution is app-level (add fields to Java record, old rows get null)
- No migration needed for adding new branding fields

### Keycloak Theme

| File | Purpose |
|------|---------|
| `infra/keycloak/themes/webcontify/login/template.ftl` | Login page with CSS variable injection |
| `infra/keycloak/themes/webcontify/login/resources/css/styles.css` | Button/link/background styling |
| `infra/keycloak/themes/webcontify/email/html/executeActions.ftl` | Invite email with branding |
| `infra/keycloak/themes/webcontify/email/html/password-reset.ftl` | Password reset with branding |
| `infra/keycloak/themes/webcontify/email/messages/messages_en.properties` | Default branding values |

**Keycloak CSS Variables (template.ftl):**
```css
:root {
  --wc-primary: ${(realm.attributes['brandPrimaryColor']!'#6366F1')};
  --wc-primary-hover: color-mix(in srgb, var(--wc-primary) 85%, black);
  --wc-secondary: ${(realm.attributes['brandSecondaryColor']!'#10B981')};
}
```

### Frontend (Next.js Platform UI)

| Component | Location | Status |
|-----------|----------|--------|
| globals.css | `app/globals.css` | OKLCH-based CSS variables, hardcoded Indigo/Emerald/Stone |
| ThemeProvider | `providers/theme-provider.tsx` | next-themes for dark/light mode only |
| BrandingForm | `features/settings/components/branding-form.tsx` | Platform admin config page (per tenant) |
| ColorPickerField | `features/settings/components/color-picker-field.tsx` | Hex color input + native picker |
| color-utils.ts | `lib/color-utils.ts` | hexToHsl() conversion utility |
| tenant-config.ts | `lib/schemas/tenant-config.ts` | Zod validation for branding fields |
| Preferences sync | `hooks/use-preferences-sync.ts` | User dark/light preference from Keycloak |

### OpenAPI

**Platform Admin endpoints (already exist):**
- `PUT /api/v1/admin/tenants/{tenantId}/config/branding` — Admin updates tenant branding
- `GET /api/v1/admin/tenants/{tenantId}/config` — Get full tenant config

**Self-service endpoints (already exist):**
- `PUT /api/v1/tenant/config/branding` — Tenant admin updates own branding
- `GET /api/v1/tenant/config` — Get own config

**File endpoints (already exist):**
- `POST /api/v1/tenant/files` — Upload file (multipart, public flag)
- `GET /api/v1/tenant/files/{storageKey}` — Get file URL
- `DELETE /api/v1/tenant/files/{storageKey}` — Delete file

---

## What Is Missing

### Critical Gaps

1. **No dynamic CSS injection in Platform UI** — Tenant colors are saved but the workspace UI ignores them. `globals.css` has hardcoded values.
2. **No accent color** — Only primary + secondary, but the design uses Emerald as accent separately from secondary.
3. **No font customization** — Hardcoded to system font stack.
4. **No button radius customization** — Hardcoded `--radius: 0.5rem`.
5. **No live preview** — Branding form submits and reloads, no instant visual feedback.
6. **No workspace branding page** — Only platform admin can configure branding (per tenant detail page). Tenant admins need their own settings page.

### Nice-to-Have Gaps

7. **No custom CSS** — Power users cannot inject arbitrary styles.
8. **No email branding preview** — Can't see how emails will look.
9. **No branding defaults per platform** — Every new tenant starts with hardcoded Indigo/Emerald.

---

## Architecture Observations

### Strengths
- **JSONB config** — Adding new branding fields requires zero DB migrations. Just extend the Java record.
- **Event-driven Keycloak sync** — Clean separation. TenantConfigService publishes, Identity module subscribes.
- **S3 file storage** — Logo/favicon already work with public URLs.
- **Hex validation** — Both backend (regex in sync listener) and frontend (Zod schema) validate colors.

### Risks
- **OKLCH in globals.css** — Current CSS uses OKLCH color space. Dynamic injection must convert hex → OKLCH or switch to HSL. Decision needed.
- **Keycloak theme uses simple CSS vars** — Only 3 variables (primary, primary-hover, secondary). Extending needs FTL template changes.
- **No caching strategy** — Branding config is fetched per request. Needs consideration for performance.

### Data Flow (Current)

```
Frontend Form → PUT /api/v1/tenant/config/branding
                       ↓
              TenantConfigService.updateBranding()
                       ↓
              Save to DB (JSONB) + Resolve logo URL
                       ↓
              Publish TenantBrandingUpdatedEvent
                       ↓
              TenantBrandingSyncListener
                       ↓
              KeycloakAdminService.updateRealmBranding()
                       ↓
              Keycloak Realm Attributes updated
                       ↓
              Next login page renders with new colors
```

**Missing link:** Platform UI never reads branding config to apply CSS variables dynamically.

### Proposed Data Flow (New)

```
Same backend flow as above
                +
Frontend loads tenant branding via GET /api/v1/tenant/config
                       ↓
              BrandingProvider injects CSS variables into :root
                       ↓
              Platform UI renders with tenant colors
                       ↓
              Live preview in branding settings page
```
