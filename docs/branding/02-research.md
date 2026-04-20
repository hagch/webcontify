# Branding Best-Practice Research

## How Established SaaS Products Handle Whitelabeling

### Stripe

**Branding Settings Page:**
- Simple, focused UI at Settings → Branding
- Fields: Icon (square, 128px+, < 512kb), Logo (non-square override), Brand Color, Accent Color
- Applied to: Emails, Checkout, Payment Links, Customer Portal, Invoices
- No "Advanced Mode" — keeps it minimal and opinionated
- Custom CSS is intentionally restricted to maintain platform consistency and accessibility

**Key Takeaway:** Two colors (brand + accent) is the sweet spot. Stripe deliberately limits customization to prevent bad UX.

### Auth0

**Per-Organization Branding:**
- Universal Login customization via no-code editor
- Fields: Page Background Color, Primary Color, Favicon URL, Font Configuration, Logo URL
- Full HTML override available for power users (Advanced Mode)
- Custom domains per tenant (one per tenant)
- Email template customization with branding variables
- Internationalization support for UI elements

**Key Takeaway:** Layered approach — simple color/logo settings for most users, full HTML override for power users. Separate "simple" and "advanced" modes.

### Clerk

**Pre-built Component Customization:**
- Colors, Typography, Button Styles via configuration settings
- Per-organization branding in multi-tenant scenarios
- SMS and Email template customization
- Authentication screen branding
- Minimal config surface — most customization is CSS-level

**Key Takeaway:** Focus on pre-built components that look good by default. Customization is additive, not replacement.

### Common Patterns Across Products

| Feature | Stripe | Auth0 | Clerk |
|---------|--------|-------|-------|
| Logo Upload | Yes | Yes | Yes |
| Favicon | No | Yes | No |
| Primary Color | Yes (brand) | Yes | Yes |
| Accent/Secondary Color | Yes | No | No |
| Font Selection | No | Yes | Yes |
| Custom CSS | No | Via HTML override | No |
| Email Branding | Yes | Yes | Yes |
| Live Preview | Yes (Checkout) | Yes (Universal Login) | Yes (Components) |
| Custom Domain | Yes | Yes (1 per tenant) | Yes |

---

## Technical Approaches for Dynamic Theming in Next.js

### Approach 1: CSS Variables on :root (Recommended)

Store tenant branding in backend, fetch on app load, inject as CSS variables on `document.documentElement.style`.

```
Tenant Config API → BrandingProvider → document.documentElement.style.setProperty()
```

**Pros:** Zero build-time dependency, works with Tailwind v4, SSR-compatible via inline `<style>` tag
**Cons:** Flash of default theme on first load (mitigated by SSR injection)

### Approach 2: Tenant Class on Body

Define CSS variable sets per tenant via class selectors (e.g., `body.tenant-acme`).

**Pros:** Simple, pure CSS
**Cons:** Requires predefined tenant themes at build time — not dynamic

### Approach 3: Server-Side Style Injection

Fetch tenant config in server component, inject `<style>` tag with CSS variables in layout.

**Pros:** No flash of default theme, SEO-friendly
**Cons:** Requires tenant identification in server component (already have via subdomain)

**Decision:** Use **Approach 3 (Server-Side) + Approach 1 (Client-Side for live preview)**. Server layout injects initial CSS variables from tenant config. Client-side BrandingProvider handles live updates during branding configuration.

---

## Keycloak Theme Approaches

### Current State (Our Implementation)

Already using realm attributes for dynamic branding:
- `brandPrimaryColor`, `brandSecondaryColor`, `brandLogo` stored as realm attributes
- FreeMarker templates read attributes and inject CSS variables
- Single theme (`webcontify`) serves all realms with different colors

This is already the recommended approach. The [keycloak-dynamic-branding](https://github.com/dteleguin/keycloak-dynamic-branding) project and [Phase Two keycloak-themes](https://github.com/p2-inc/keycloak-themes) use the same pattern.

### Extension Needed

- Add more CSS variables (accent color, border radius, font family)
- Support logo in login page header (already partially done)
- Extend email templates with more branding variables

---

## Design Decisions for Our Implementation

Based on research, here's the pragmatic approach:

### Simple Mode (Default)
Following Stripe's model — minimal, opinionated:
1. **Logo Upload** — Used in sidebar, login page, emails
2. **Favicon Upload** — Browser tab icon
3. **Primary Color** — Buttons, links, active states
4. **Secondary Color** — Secondary buttons, badges
5. **Accent Color** — Success states, highlights (new field)

### Advanced Mode (Toggle)
Following Auth0's layered approach:
6. **Font Family** — Dropdown with curated web-safe + Google Fonts options
7. **Border Radius** — Slider (0px sharp → 16px rounded)
8. **Additional Colors** — Background, Surface, Muted, Destructive overrides
9. **Custom CSS** — Textarea for power users (sandboxed, no `<script>`)

### Live Preview
Following Clerk/Auth0 — changes visible instantly:
- Split-pane layout: Config form on left, preview panel on right
- Preview shows: Button, Card, Table row, Login page mockup
- No save needed for preview — only "Save" persists to backend

### What We Skip (YAGNI)
- AI-powered theme suggestions — over-engineered for current phase
- Full HTML template override — too much maintenance burden
- Per-page branding — all pages share one theme
- Animation customization — not a branding concern
