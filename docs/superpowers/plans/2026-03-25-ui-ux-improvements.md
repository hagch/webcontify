# UI/UX Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 5 UI/UX-Verbesserungen implementieren: Sidebar Contrast Fix, Keycloak Login Redesign, Invite-Flow Fix, Font-Auswahl mit Preview, und Layout Redesign.

**Architecture:** Keycloak-Theme wird mit eigenem CSS auf Design-System-Tokens umgestellt (parent=keycloak.v2 bleibt). BrandingProvider bekommt Font-Loading. AppHeader wird zur kontextabhaengigen Top-Bar mit Union-Type API.

**Tech Stack:** FreeMarker (Keycloak Templates), CSS Custom Properties, Next.js 16 + React 19, shadcn/ui v4, Tailwind v4, Spring Boot 4 + Keycloak Admin Client 26

**Spec:** `docs/superpowers/specs/2026-03-25-ui-ux-improvements-design.md`
**Design Guidelines:** `docs/10-frontend-design-guidelines.md`

---

## File Structure

### Keycloak Theme (Paket 1 + 2)
```
infra/keycloak/themes/webcontify/login/
├── theme.properties                          — Unchanged (parent=keycloak.v2)
├── template.ftl                              — MODIFY: Neues Layout, Design-Tokens, generisches Font-Loading
├── login.ftl                                 — CREATE: Custom Login-Formular Template
├── login-update-password.ftl                 — CREATE: "Welcome, set your password" Template
├── login-reset-password.ftl                  — CREATE: Password-Reset E-Mail-Eingabe
├── login-action-token.ftl                    — CREATE: Auto-Submit (kein Zwischenklick)
├── error.ftl                                 — CREATE: Error-Page Template
├── info.ftl                                  — CREATE: Info-Page Template
└── resources/css/
    └── styles.css                            — REWRITE: Eigenes CSS mit Design-Tokens
```

### Backend (Paket 2 + 3)
```
apps/backend/src/main/java/io/webcontify/identity/
├── service/
│   ├── KeycloakAdminService.java             — MODIFY: sendInviteEmail() mit redirectUri
│   └── TenantBrandingSyncListener.java       — MODIFY: ALLOWED_FONTS + mapFontFamilyToCss()
└── model/
    └── SendInviteEmailRequest.java           — MODIFY: redirectUri Feld hinzufuegen
openapi/tenant-api.yaml                       — MODIFY: fontFamily Enum erweitern
```

### Frontend (Paket 3 + 4 + 5)
```
apps/platform/src/
├── app/globals.css                           — MODIFY: Sidebar-Accent-Werte (Paket 5)
├── providers/branding-provider.tsx            — MODIFY: Font-Variable Switching (Paket 3)
├── lib/fonts.ts                              — CREATE: Alle 12 Fonts per next/font + Mappings
├── features/settings/components/
│   ├── branding-form.tsx                     — REWRITE: Tabs statt Cards (Paket 3)
│   ├── font-select.tsx                       — CREATE: Font-Dropdown mit Lazy Loading
│   ├── branding-preview.tsx                  — MODIFY: Font-Preview
│   └── __tests__/
│       ├── branding-provider.test.ts         — CREATE: Font-Switching Tests
│       └── font-select.test.tsx              — CREATE: FontSelect Tests
├── components/layout/
│   └── app-header.tsx                        — REWRITE: Kontextabhaengige Top-Bar (Paket 4)
└── app/
    ├── (platform)/**/*.tsx                   — MODIFY: Alle Pages migrieren (Paket 4)
    └── (workspace)/**/*.tsx                  — MODIFY: Alle Pages migrieren (Paket 4)
```

---

## Task 1: Sidebar Contrast Fix (Paket 5)

**Files:**
- Modify: `apps/platform/src/app/globals.css:51-52`

- [ ] **Step 1: Aendere Light-Mode Sidebar-Accent Werte**

In `apps/platform/src/app/globals.css`, Zeile 51-52 aendern:

```css
/* Vorher */
--sidebar-accent: oklch(0.98 0.003 277);    /* Indigo 50 #EEF2FF */
--sidebar-accent-foreground: oklch(0.585 0.233 277); /* Indigo 500 */

/* Nachher */
--sidebar-accent: oklch(0.94 0.028 277);    /* Indigo 100 #E0E7FF */
--sidebar-accent-foreground: oklch(0.457 0.24 277);  /* Indigo 600 */
```

- [ ] **Step 2: Visuell verifizieren**

Run: `nx serve platform`
Pruefe: Sidebar-Items im Light Mode hovern — Hintergrund-Wechsel muss sichtbar sein. Dark Mode darf sich nicht aendern.

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/app/globals.css
git commit -m "fix: improve sidebar hover/active contrast in light mode"
```

---

## Task 2: Keycloak Login Theme — CSS Rewrite (Paket 1)

**Files:**
- Rewrite: `infra/keycloak/themes/webcontify/login/resources/css/styles.css`

- [ ] **Step 1: Schreibe das komplette neue CSS**

Ersetze den gesamten Inhalt von `styles.css` mit eigenem Design-System CSS:

```css
/* Webcontify Design System — Keycloak Login Theme
 * Ueberschreibt PatternFly v5 komplett mit eigenen Design-Tokens.
 * Dynamische Tokens (--wc-*) werden in template.ftl aus Realm-Attributes gesetzt.
 */

/* ------------------------------------------------------------------ */
/* Reset & Base                                                        */
/* ------------------------------------------------------------------ */

body {
  font-family: var(--wc-font);
  background-color: var(--wc-bg);
  color: var(--wc-text);
  margin: 0;
  padding: 0;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ------------------------------------------------------------------ */
/* Card Container                                                      */
/* ------------------------------------------------------------------ */

.pf-v5-c-login {
  width: 100%;
  max-width: 420px;
  margin: 2rem auto;
  padding: 0 1rem;
}

.pf-v5-c-login__container {
  background-color: var(--wc-card);
  border: 1px solid var(--wc-card-border);
  border-radius: calc(var(--wc-radius) * 2);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

/* ------------------------------------------------------------------ */
/* Branding Header                                                     */
/* ------------------------------------------------------------------ */

#kc-header-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 2rem 0.5rem;
}

.wc-brand-logo {
  max-height: 48px;
  max-width: 240px;
  object-fit: contain;
}

.wc-brand-name {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--wc-text);
}

/* ------------------------------------------------------------------ */
/* Main Content                                                        */
/* ------------------------------------------------------------------ */

.pf-v5-c-login__main {
  border-radius: 0;
  box-shadow: none;
  background: none;
}

.pf-v5-c-login__main-header {
  padding: 1rem 2rem 0;
}

.pf-v5-c-login__main-header-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--wc-text);
  text-align: center;
}

.pf-v5-c-login__main-body {
  padding: 1.5rem 2rem 2rem;
}

/* ------------------------------------------------------------------ */
/* Form Controls                                                       */
/* ------------------------------------------------------------------ */

.pf-v5-c-form-control {
  border: 1px solid var(--wc-input-border);
  border-radius: var(--wc-radius);
  background-color: var(--wc-input-bg);
  color: var(--wc-text);
  padding: 0.5rem 0.75rem;
  font-size: 0.875rem;
  line-height: 1.25rem;
  width: 100%;
  transition: border-color 0.15s;
}

.pf-v5-c-form-control:focus-within,
.pf-v5-c-form-control:focus {
  border-color: var(--wc-focus-ring);
  outline: 2px solid var(--wc-focus-ring);
  outline-offset: 1px;
}

.pf-v5-c-form__label-text {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--wc-text);
}

/* ------------------------------------------------------------------ */
/* Buttons                                                             */
/* ------------------------------------------------------------------ */

.pf-v5-c-button {
  border-radius: var(--wc-radius);
  font-size: 0.875rem;
  font-weight: 500;
  padding: 0.5rem 1rem;
  cursor: pointer;
  transition: background-color 0.15s, border-color 0.15s;
}

.pf-v5-c-button.pf-m-primary {
  background-color: var(--wc-primary);
  border-color: var(--wc-primary);
  color: #ffffff;
  width: 100%;
}

.pf-v5-c-button.pf-m-primary:hover,
.pf-v5-c-button.pf-m-primary:focus-visible {
  background-color: var(--wc-primary-hover);
  border-color: var(--wc-primary-hover);
}

.pf-v5-c-button:focus-visible {
  outline: 2px solid var(--wc-focus-ring);
  outline-offset: 2px;
}

/* ------------------------------------------------------------------ */
/* Links                                                               */
/* ------------------------------------------------------------------ */

a, a:visited {
  color: var(--wc-link);
  text-decoration: none;
}

a:hover, a:focus {
  color: var(--wc-primary-hover);
  text-decoration: underline;
}

a:focus-visible {
  outline: 2px solid var(--wc-focus-ring);
  outline-offset: 2px;
}

/* ------------------------------------------------------------------ */
/* Alert / Error Messages                                              */
/* ------------------------------------------------------------------ */

.pf-v5-c-alert {
  border-radius: var(--wc-radius);
  padding: 0.75rem 1rem;
  font-size: 0.875rem;
  margin-bottom: 1rem;
}

.pf-v5-c-alert.pf-m-danger {
  background-color: #FEF2F2;
  border: 1px solid #FECACA;
  color: #991B1B;
}

.pf-v5-c-alert.pf-m-success {
  background-color: #F0FDF4;
  border: 1px solid #BBF7D0;
  color: #166534;
}

.pf-v5-c-alert.pf-m-info {
  background-color: #EFF6FF;
  border: 1px solid #BFDBFE;
  color: #1E40AF;
}

/* ------------------------------------------------------------------ */
/* Footer                                                              */
/* ------------------------------------------------------------------ */

.pf-v5-c-login__main-footer {
  padding: 0 2rem 2rem;
  text-align: center;
}

.pf-v5-c-login__main-footer a {
  font-size: 0.875rem;
}

/* ------------------------------------------------------------------ */
/* Language Switcher                                                    */
/* ------------------------------------------------------------------ */

.pf-v5-c-login__main-header-utilities select {
  border: 1px solid var(--wc-input-border);
  border-radius: var(--wc-radius);
  background-color: var(--wc-input-bg);
  color: var(--wc-text-muted);
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
}

/* ------------------------------------------------------------------ */
/* noscript Fallback (Paket 2)                                         */
/* ------------------------------------------------------------------ */

.wc-noscript-card {
  text-align: center;
  padding: 2rem;
  font-size: 0.875rem;
  color: var(--wc-text-muted);
}

.wc-btn-primary {
  display: inline-block;
  padding: 0.5rem 1.5rem;
  background-color: var(--wc-primary);
  color: #ffffff;
  border: none;
  border-radius: var(--wc-radius);
  font-weight: 500;
  cursor: pointer;
  margin-top: 1rem;
}

/* ------------------------------------------------------------------ */
/* Dark Mode                                                           */
/* ------------------------------------------------------------------ */

@media (prefers-color-scheme: dark) {
  :root {
    --wc-bg: #1C1917;
    --wc-card: #292524;
    --wc-card-border: #44403C;
    --wc-text: #FAFAF9;
    --wc-text-muted: #A8A29E;
    --wc-input-border: #57534E;
    --wc-input-bg: #292524;
    --wc-error: #F87171;
  }

  .pf-v5-c-alert.pf-m-danger {
    background-color: #450A0A;
    border-color: #7F1D1D;
    color: #FCA5A5;
  }
}

/* ------------------------------------------------------------------ */
/* Responsive                                                          */
/* ------------------------------------------------------------------ */

@media (max-width: 640px) {
  .pf-v5-c-login {
    max-width: 100%;
    margin: 1rem auto;
  }
}

/* ------------------------------------------------------------------ */
/* PatternFly Overrides (verstecke ungewollte Elemente)                */
/* ------------------------------------------------------------------ */

div.kc-logo-text { display: none; }
.pf-v5-c-login__header { background: none; }
.pf-v5-c-brand { background: none; padding: 0; }
```

- [ ] **Step 2: Visuell verifizieren**

Run: Keycloak Docker Container neu starten (`nx infra:down && nx infra:up`).
Gehe zu `auth.localhost` → Tenant-Realm → Login-Seite. Pruefe Card-Layout, Farben, Dark Mode.

- [ ] **Step 3: Commit**

```bash
git add infra/keycloak/themes/webcontify/login/resources/css/styles.css
git commit -m "feat: rewrite Keycloak login theme CSS with design system tokens"
```

---

## Task 3: Keycloak Login Theme — Template Update (Paket 1)

**Files:**
- Modify: `infra/keycloak/themes/webcontify/login/template.ftl:91-110`

- [ ] **Step 1: Aktualisiere template.ftl**

Aendere den CSS-Variables Block (Zeile 91-110) und das Google Fonts Loading:

1. Ersetze die statischen Token-Definitionen mit den neuen `--wc-bg`, `--wc-card`, etc. Tokens
2. Mache das Google Fonts Loading generisch: Font-Name aus Realm-Attribut lesen, URL dynamisch bauen. Konkret: Die `<link>` URL in Zeile 108 aendern von `family=${fontName?url('UTF-8')}&display=swap` zu `family=${fontName?url('UTF-8')}:wght@400;500;600;700&display=swap` — vier Weights fuer UI-Nutzung.
3. Entferne die PatternFly Dark Mode Klasse Logik (unser CSS nutzt `prefers-color-scheme`)

Relevante Zeilen in `template.ftl:91-110` — die `<style>` und Google Fonts `<link>` Bloecke.

- [ ] **Step 2: Verifizieren**

Infrastruktur neu starten, Login-Seite mit verschiedenen Tenant-Brandings testen.

- [ ] **Step 3: Commit**

```bash
git add infra/keycloak/themes/webcontify/login/template.ftl
git commit -m "feat: update Keycloak template with design system tokens and generic font loading"
```

---

## Task 4: Keycloak Seiten-Templates (Paket 1)

**Files:**
- Create: `infra/keycloak/themes/webcontify/login/login.ftl`
- Create: `infra/keycloak/themes/webcontify/login/login-reset-password.ftl`
- Create: `infra/keycloak/themes/webcontify/login/error.ftl`
- Create: `infra/keycloak/themes/webcontify/login/info.ftl`

- [ ] **Step 1: Keycloak base-Theme Referenz lesen**

Lies die originalen Templates aus dem Keycloak v26 `base` Theme (im Keycloak Docker Container unter `/opt/keycloak/lib/lib/main/org.keycloak.keycloak-themes-*.jar`). Extrahiere die exakten Form-Actions, CSRF-Tokens, und Macro-Aufrufe. Die FreeMarker-Logik muss identisch bleiben — nur das HTML/CSS aendert sich.

Run: `docker exec keycloak find /opt/keycloak/lib/lib/main -name "keycloak-themes*" | head -1` und dann die Templates extrahieren.

- [ ] **Step 2: Erstelle login.ftl**

Template fuer die Login-Seite. Wichtig: `<#import "template.ftl" as layout>` + `<@layout.registrationLayout>` Macro beibehalten. Formular-Felder (username, password), CSRF-Token, Remember-Me, "Forgot Password"-Link.

- [ ] **Step 3: Erstelle login-reset-password.ftl**

Password-Reset-Seite (E-Mail-Eingabe). Gleiches Card-Layout wie Login.

- [ ] **Step 4: Erstelle error.ftl**

Error-Seite (abgelaufene Links, ungueltige Sessions). Card mit Error-Icon und Fehlermeldung.

- [ ] **Step 5: Erstelle info.ftl**

Info-Seite ("E-Mail gesendet", "Aktion abgeschlossen"). Card mit Info-Meldung.

- [ ] **Step 6: Verifizieren**

Alle Seiten manuell testen: Login, falsches Passwort (Error), Forgot Password (Reset), abgelaufener Link (Error-Seite).

- [ ] **Step 7: Commit**

```bash
git add infra/keycloak/themes/webcontify/login/login.ftl infra/keycloak/themes/webcontify/login/login-reset-password.ftl infra/keycloak/themes/webcontify/login/error.ftl infra/keycloak/themes/webcontify/login/info.ftl
git commit -m "feat: add custom Keycloak templates for login, reset, error, and info pages"
```

---

## Task 5: Keycloak Invite-Flow — Auto-Submit Template (Paket 2)

**Files:**
- Create: `infra/keycloak/themes/webcontify/login/login-action-token.ftl` (Name gegen KC26 base verifizieren!)
- Create: `infra/keycloak/themes/webcontify/login/login-update-password.ftl`

- [ ] **Step 1: Verifiziere den Template-Namen**

Run: Im Keycloak Docker Container pruefen welcher Template-Name fuer Action-Token-Seiten verwendet wird:
```bash
docker exec keycloak find /opt/keycloak -name "*action-token*" -o -name "*action_token*" 2>/dev/null
```

- [ ] **Step 2: Erstelle Auto-Submit Template**

Das Template (z.B. `login-action-token.ftl`) rendert ein verstecktes Formular mit Auto-Submit per JavaScript. `noscript`-Fallback mit Branding. Siehe Spec Paket 2.

- [ ] **Step 3: Erstelle login-update-password.ftl**

"Welcome! Set your password" Template mit Tenant-Branding. Gleiches Card-Layout wie Login. Titel angepasst. Zwei Passwort-Felder (new + confirm).

- [ ] **Step 4: Verifizieren**

Einen Test-User per Keycloak Admin Console mit Required Action "UPDATE_PASSWORD" versehen. Link aufrufen → sollte direkt beim Passwort-Formular landen.

- [ ] **Step 5: Commit**

```bash
git add infra/keycloak/themes/webcontify/login/login-action-token.ftl infra/keycloak/themes/webcontify/login/login-update-password.ftl
git commit -m "feat: add auto-submit action token and welcome password templates"
```

---

## Task 6: Backend — Invite Redirect (Paket 2)

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/model/SendInviteEmailRequest.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java:108-113`
- Modify: Alle Aufrufer von `sendInviteEmail()` (UserService, PlatformAdminService)
- Test: Bestehende Tests anpassen

- [ ] **Step 1: Schreibe den Test fuer den neuen Parameter**

In der bestehenden Test-Klasse fuer KeycloakAdminService (oder ein neuer Unit-Test): Verifiziere, dass `executeActionsEmail` mit clientId, redirectUri und lifespan aufgerufen wird.

- [ ] **Step 2: Run Test — muss fehlschlagen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests *KeycloakAdminService*`

- [ ] **Step 3: Erweitere SendInviteEmailRequest**

```java
public record SendInviteEmailRequest(
    String realmName,
    UUID userId,
    String redirectUri  // NEU: Tenant-Subdomain URL
) {}
```

- [ ] **Step 4: Aendere KeycloakAdminService.sendInviteEmail()**

```java
private static final int INVITE_LINK_EXPIRATION_SECONDS = 86400; // 24h

public void sendInviteEmail(SendInviteEmailRequest request) {
    keycloak.realm(request.realmName())
            .users()
            .get(request.userId().toString())
            .executeActionsEmail(
                "tenant-ui",
                request.redirectUri(),
                INVITE_LINK_EXPIRATION_SECONDS,
                List.of("UPDATE_PASSWORD")
            );
}
```

- [ ] **Step 5: Passe alle Aufrufer an**

Suche nach `sendInviteEmail(` in der Codebase. **Alle 3 Aufrufer** muessen die `redirectUri` mitgeben:
- `UserService.java:116,209`: `"http://" + tenantSlug + ".localhost"` (lokal), konfigurierbar fuer Prod
- `PlatformAdminService.java:52`: `"http://platform.localhost"`
- `TenantProvisioningListener.java:184`: `"http://" + tenantSlug + ".localhost"` (initialer Admin-Invite)

**Betroffene Test-Dateien (alle muessen den neuen `redirectUri` Parameter beruecksichtigen):**
- `KeycloakAdminServiceTest.java`
- `UserServiceTest.java`
- `PlatformAdminServiceTest.java`
- `TenantProvisioningListenerTest.java`
- `TenantProvisioningIntegrationTest.java`
- `PlatformAdminControllerIntegrationTest.java`

- [ ] **Step 6: Run Tests — muessen gruen sein**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

- [ ] **Step 7: Verifiziere Redirect-URI Whitelist**

Pruefe `KeycloakAdminService.java:76`: `client.setRedirectUris(List.of("http://" + request.tenantSlug() + ".localhost/*"))` — das Pattern muss zum berechneten `redirectUri` passen. Fuer lokale Entwicklung: OK. Fuer Produktion: muss `https://{slug}.{domain}/*` sein.

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/
git commit -m "feat: add redirect URI and lifespan to invite email flow"
```

---

## Task 7: Backend — Font-Enum erweitern (Paket 3)

**Files:**
- Modify: `openapi/tenant-api.yaml:749` (fontFamily Enum)
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/TenantBrandingSyncListener.java:20-21,90-98`
- Test: `TenantBrandingSyncListenerTest`

- [ ] **Step 1: Erweitere OpenAPI Spec (Contract-First!)**

In `openapi/tenant-api.yaml`, die `fontFamily` Enums an **beiden** Stellen erweitern:
- `BrandingConfigResponse.fontFamily` (ca. Zeile 749)
- `UpdateBrandingConfigRequest.fontFamily` (ca. Zeile 801)

Neue Werte hinzufuegen: `roboto, source-sans-3, ibm-plex-sans, open-sans, lato, nunito-sans, poppins, merriweather`

- [ ] **Step 2: Regeneriere Backend + Frontend Code**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
cd apps/platform && pnpm generate
```

- [ ] **Step 3: Schreibe Test fuer neue Fonts**

Erweitere den bestehenden `TenantBrandingSyncListenerTest` mit Testcases fuer die neuen Fonts (z.B. `roboto` → `'Roboto', sans-serif`, `merriweather` → `'Merriweather', serif`).

- [ ] **Step 4: Run Test — muss fehlschlagen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests *TenantBrandingSyncListener*`

- [ ] **Step 5: Erweitere ALLOWED_FONTS (Zeile 20-21)**

```java
private static final java.util.Set<String> ALLOWED_FONTS = java.util.Set.of(
        "system", "inter", "geist", "plus-jakarta-sans", "dm-sans",
        "roboto", "source-sans-3", "ibm-plex-sans", "open-sans",
        "lato", "nunito-sans", "poppins", "merriweather");
```

- [ ] **Step 4: Erweitere mapFontFamilyToCss() (Zeile 90-98)**

```java
private String mapFontFamilyToCss(String fontFamily) {
    return switch (fontFamily) {
        case "inter" -> "'Inter', sans-serif";
        case "geist" -> "'Geist', sans-serif";
        case "plus-jakarta-sans" -> "'Plus Jakarta Sans', sans-serif";
        case "dm-sans" -> "'DM Sans', sans-serif";
        case "roboto" -> "'Roboto', sans-serif";
        case "source-sans-3" -> "'Source Sans 3', sans-serif";
        case "ibm-plex-sans" -> "'IBM Plex Sans', sans-serif";
        case "open-sans" -> "'Open Sans', sans-serif";
        case "lato" -> "'Lato', sans-serif";
        case "nunito-sans" -> "'Nunito Sans', sans-serif";
        case "poppins" -> "'Poppins', sans-serif";
        case "merriweather" -> "'Merriweather', serif";
        default -> "system-ui, sans-serif";
    };
}
```

- [ ] **Step 7: Run Tests — muessen gruen sein**

- [ ] **Step 8: Run Full Backend Test Suite**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

- [ ] **Step 9: Commit**

```bash
git add openapi/tenant-api.yaml apps/backend/src/main/java/io/webcontify/identity/service/TenantBrandingSyncListener.java
git commit -m "feat: expand font selection to 12 curated fonts"
```

---

## Task 8: Frontend — Font Setup via next/font (Paket 3)

**Files:**
- Create: `apps/platform/src/lib/fonts.ts`
- Modify: `apps/platform/src/app/layout.tsx`
- Test: `apps/platform/src/lib/__tests__/fonts.test.ts`

Alle 12 Fonts werden per `next/font/google` statisch importiert (self-hosted, Zero Layout Shift, kein externer Request zur Laufzeit). Jede Font bekommt eine eigene CSS-Variable. Der BrandingProvider setzt dann `--font-sans` auf die Variable der aktiven Font.

- [ ] **Step 1: Schreibe Tests fuer Font-Mapping**

```typescript
// apps/platform/src/lib/__tests__/fonts.test.ts
import { describe, it, expect } from "vitest";
import { FONT_CSS_VAR_MAP, FONT_CSS_FALLBACK_MAP } from "../fonts";

describe("fonts", () => {
  it("should map all font keys to CSS variable references", () => {
    expect(FONT_CSS_VAR_MAP["inter"]).toBe("var(--font-inter)");
    expect(FONT_CSS_VAR_MAP["roboto"]).toBe("var(--font-roboto)");
    expect(FONT_CSS_VAR_MAP["merriweather"]).toBe("var(--font-merriweather)");
  });

  it("should map system to system-ui", () => {
    expect(FONT_CSS_VAR_MAP["system"]).toBe("system-ui, sans-serif");
  });

  it("should map geist to the default --font-sans variable", () => {
    expect(FONT_CSS_VAR_MAP["geist"]).toBe("var(--font-geist)");
  });

  it("should have a fallback CSS map for Keycloak (non-next/font context)", () => {
    expect(FONT_CSS_FALLBACK_MAP["inter"]).toBe("'Inter', sans-serif");
    expect(FONT_CSS_FALLBACK_MAP["merriweather"]).toBe("'Merriweather', serif");
    expect(FONT_CSS_FALLBACK_MAP["system"]).toBe("system-ui, sans-serif");
  });

  it("should have entries for all 12 fonts + system", () => {
    expect(Object.keys(FONT_CSS_VAR_MAP)).toHaveLength(13);
    expect(Object.keys(FONT_CSS_FALLBACK_MAP)).toHaveLength(13);
  });
});
```

- [ ] **Step 2: Run Tests — muessen fehlschlagen**

Run: `cd apps/platform && pnpm vitest src/lib/__tests__/fonts.test.ts`

- [ ] **Step 3: Implementiere Font-Definitionen**

```typescript
// apps/platform/src/lib/fonts.ts
import {
  Inter, Roboto, Lato, Poppins, Merriweather,
  DM_Sans, Plus_Jakarta_Sans, Source_Sans_3,
  IBM_Plex_Sans, Open_Sans, Nunito_Sans,
} from "next/font/google";
import { Geist } from "next/font/google";

// Alle Fonts mit eigener CSS-Variable laden
export const fontInter = Inter({ variable: "--font-inter", subsets: ["latin"] });
export const fontGeist = Geist({ variable: "--font-geist", subsets: ["latin"] });
export const fontDmSans = DM_Sans({ variable: "--font-dm-sans", subsets: ["latin"] });
export const fontPlusJakarta = Plus_Jakarta_Sans({ variable: "--font-plus-jakarta-sans", subsets: ["latin"] });
export const fontRoboto = Roboto({ variable: "--font-roboto", subsets: ["latin"], weight: ["400", "500", "600", "700"] });
export const fontSourceSans = Source_Sans_3({ variable: "--font-source-sans-3", subsets: ["latin"] });
export const fontIbmPlex = IBM_Plex_Sans({ variable: "--font-ibm-plex-sans", subsets: ["latin"], weight: ["400", "500", "600", "700"] });
export const fontOpenSans = Open_Sans({ variable: "--font-open-sans", subsets: ["latin"] });
export const fontLato = Lato({ variable: "--font-lato", subsets: ["latin"], weight: ["400", "700"] });
export const fontNunitoSans = Nunito_Sans({ variable: "--font-nunito-sans", subsets: ["latin"] });
export const fontPoppins = Poppins({ variable: "--font-poppins", subsets: ["latin"], weight: ["400", "500", "600", "700"] });
export const fontMerriweather = Merriweather({ variable: "--font-merriweather", subsets: ["latin"], weight: ["400", "700"] });

// Alle Font-Instanzen fuer className-Injection im Layout
export const allFontVariables = [
  fontGeist, fontInter, fontDmSans, fontPlusJakarta, fontRoboto,
  fontSourceSans, fontIbmPlex, fontOpenSans, fontLato,
  fontNunitoSans, fontPoppins, fontMerriweather,
];

// Mapping: Font-Key → CSS var() Referenz (fuer BrandingProvider)
export const FONT_CSS_VAR_MAP: Record<string, string> = {
  system: "system-ui, sans-serif",
  geist: "var(--font-geist)",
  inter: "var(--font-inter)",
  "dm-sans": "var(--font-dm-sans)",
  "plus-jakarta-sans": "var(--font-plus-jakarta-sans)",
  roboto: "var(--font-roboto)",
  "source-sans-3": "var(--font-source-sans-3)",
  "ibm-plex-sans": "var(--font-ibm-plex-sans)",
  "open-sans": "var(--font-open-sans)",
  lato: "var(--font-lato)",
  "nunito-sans": "var(--font-nunito-sans)",
  poppins: "var(--font-poppins)",
  merriweather: "var(--font-merriweather)",
};

// Fallback-Map fuer Keycloak-Theme (kein next/font dort)
export const FONT_CSS_FALLBACK_MAP: Record<string, string> = {
  system: "system-ui, sans-serif",
  geist: "'Geist', sans-serif",
  inter: "'Inter', sans-serif",
  "dm-sans": "'DM Sans', sans-serif",
  "plus-jakarta-sans": "'Plus Jakarta Sans', sans-serif",
  roboto: "'Roboto', sans-serif",
  "source-sans-3": "'Source Sans 3', sans-serif",
  "ibm-plex-sans": "'IBM Plex Sans', sans-serif",
  "open-sans": "'Open Sans', sans-serif",
  lato: "'Lato', sans-serif",
  "nunito-sans": "'Nunito Sans', sans-serif",
  poppins: "'Poppins', sans-serif",
  merriweather: "'Merriweather', serif",
};
```

- [ ] **Step 4: Aktualisiere layout.tsx**

Ersetze die bisherige Geist-Only-Deklaration in `apps/platform/src/app/layout.tsx`:

```typescript
import { Geist_Mono } from "next/font/google";
import { allFontVariables } from "@/lib/fonts";

const geistMono = Geist_Mono({ variable: "--font-mono", subsets: ["latin"] });

// In der className des <html> Tags:
// Alle Font-Variablen + Mono Font
className={`${allFontVariables.map(f => f.variable).join(" ")} ${geistMono.variable}`}
```

Geist wird jetzt aus `fonts.ts` statt direkt importiert. `--font-sans` wird weiterhin in `globals.css` als Default auf Geist gesetzt — der BrandingProvider ueberschreibt dann zur Laufzeit.

- [ ] **Step 5: Run Tests — muessen gruen sein**

Run: `cd apps/platform && pnpm vitest run`

- [ ] **Step 6: Run Build — muss erfolgreich sein**

Run: `nx build platform`

- [ ] **Step 7: Commit**

```bash
git add apps/platform/src/lib/fonts.ts apps/platform/src/lib/__tests__/fonts.test.ts apps/platform/src/app/layout.tsx
git commit -m "feat: register all 12 tenant fonts via next/font for self-hosted loading"
```

---

## Task 9: Frontend — BrandingProvider Font-Handling (Paket 3)

**Files:**
- Modify: `apps/platform/src/providers/branding-provider.tsx`
- Test: `apps/platform/src/providers/__tests__/branding-provider.test.ts`

Da alle Fonts bereits per `next/font` geladen sind (Task 8), muss der BrandingProvider nur noch `--font-sans` auf die richtige CSS-Variable umschalten. Kein dynamisches Font-Loading, kein Cleanup von Link-Elementen.

- [ ] **Step 1: Schreibe Tests fuer Font-Handling im BrandingProvider**

Spezifische Testcases fuer `buildCssOverrides()`:
- `buildCssOverrides({ fontFamily: "inter" })` → `{ "--font-sans": "var(--font-inter)" }` enthalten
- `buildCssOverrides({ fontFamily: "system" })` → `--font-sans` NICHT enthalten (nutzt Default)
- `buildCssOverrides({ fontFamily: "geist" })` → `--font-sans` NICHT enthalten (Geist ist der Default von next/font)
- `buildCssOverrides({ fontFamily: "merriweather" })` → `{ "--font-sans": "var(--font-merriweather)" }`
- `removeBrandingFromDocument()` → `--font-sans` wird entfernt (faellt auf den next/font Default zurueck)

- [ ] **Step 2: Run Tests — muessen fehlschlagen**

Run: `cd apps/platform && pnpm vitest src/providers/__tests__/branding-provider.test.ts`

- [ ] **Step 3: Erweitere buildCssOverrides()**

In `branding-provider.tsx`:

```typescript
import { FONT_CSS_VAR_MAP } from "@/lib/fonts";

// In buildCssOverrides(), nach dem borderRadius Block:
if (values.fontFamily && values.fontFamily !== "system" && values.fontFamily !== "geist") {
  const cssVar = FONT_CSS_VAR_MAP[values.fontFamily];
  if (cssVar) {
    overrides["--font-sans"] = cssVar;
  }
}
```

- [ ] **Step 4: Erweitere removeBrandingFromDocument()**

`--font-sans` zur Liste der zu entfernenden Properties hinzufuegen (Zeile 104-118):

```typescript
const props = [
  "--primary", "--ring", "--sidebar-primary", "--sidebar-accent-foreground",
  "--sidebar-ring", "--chart-1", "--accent", "--chart-2", "--radius",
  "--brand-secondary", "--destructive", "--success", "--warning",
  "--font-sans",  // NEU: Font auf Default (Geist) zuruecksetzen
];
```

Da `--font-sans` per `next/font` als Klasse auf `<html>` gesetzt ist, faellt der Wert automatisch auf Geist zurueck wenn die inline-Style Property entfernt wird.

- [ ] **Step 5: Run Tests — muessen gruen sein**

Run: `cd apps/platform && pnpm vitest run`

- [ ] **Step 6: Commit**

```bash
git add apps/platform/src/providers/branding-provider.tsx apps/platform/src/providers/__tests__/branding-provider.test.ts
git commit -m "feat: add font switching to BrandingProvider via next/font CSS variables"
```

---

## Task 10: Frontend — FontSelect Komponente (Paket 3)

**Files:**
- Create: `apps/platform/src/features/settings/components/font-select.tsx`
- Test: `apps/platform/src/features/settings/components/__tests__/font-select.test.tsx`

- [ ] **Step 1: Schreibe Tests fuer FontSelect**

Tests: rendert Font-Optionen gruppiert, zeigt aktuelle Font im Trigger, ruft onChange auf.

- [ ] **Step 2: Run Tests — fehlschlagen**

- [ ] **Step 3: Implementiere FontSelect**

Komponente mit shadcn `<Select>`, `<SelectGroup>` fuer Kategorien (Modern, Classic, Friendly, Serif), Intersection Observer fuer Lazy Font Loading. Jedes Item nutzt Ref + Intersection Observer Callback um `element.style.fontFamily` zu setzen.

- [ ] **Step 4: Run Tests — gruen**

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/features/settings/components/font-select.tsx apps/platform/src/features/settings/components/__tests__/font-select.test.tsx
git commit -m "feat: add FontSelect component with lazy font loading"
```

---

## Task 11: Frontend — Branding Form Tabs + Preview (Paket 3)

**Files:**
- Rewrite: `apps/platform/src/features/settings/components/branding-form.tsx`
- Modify: `apps/platform/src/features/settings/components/branding-preview.tsx`
- Modify: `apps/platform/messages/en.json` + `de.json`

- [ ] **Step 1: Ergaenze i18n Keys**

In `en.json` und `de.json` unter `config.branding`:
- Tab-Labels: `tabLogoIdentity` (EN: "Logo & Identity" / DE: "Logo & Identitaet"), `tabColors` (EN: "Colors" / DE: "Farben"), `tabTypography` (EN: "Typography" / DE: "Typografie")
- Font-Kategorien: `fontCategoryModern` (EN: "Modern" / DE: "Modern"), `fontCategoryClassic` (EN: "Classic" / DE: "Klassisch"), `fontCategoryFriendly` (EN: "Friendly" / DE: "Freundlich"), `fontCategorySerif` (EN: "Serif" / DE: "Serif")
- Neue Font-Namen: `fontFamilyOptions.roboto` ("Roboto"), `fontFamilyOptions.sourceSans3` ("Source Sans 3"), `fontFamilyOptions.ibmPlexSans` ("IBM Plex Sans"), `fontFamilyOptions.openSans` ("Open Sans"), `fontFamilyOptions.lato` ("Lato"), `fontFamilyOptions.nunitoSans` ("Nunito Sans"), `fontFamilyOptions.poppins` ("Poppins"), `fontFamilyOptions.merriweather` ("Merriweather")

**Wichtig:** EN und DE immer gleichzeitig pflegen — keine fehlenden Keys!

- [ ] **Step 2: Refactore Branding Form zu Tabs**

Ersetze die Card-basierte Struktur durch shadcn `<Tabs>`:
- Tab 1 (Logo & Identity): Logo + Favicon Upload
- Tab 2 (Colors): Alle 6 Color Picker (Primary, Secondary, Accent, Danger, Success, Warning)
- Tab 3 (Typography): FontSelect + Border Radius

Grid-Layout beibehalten: Links Tabs (lg:col-span-3), rechts Preview (lg:col-span-2, sticky).

- [ ] **Step 3: Erweitere BrandingPreview um Font-Preview**

In `branding-preview.tsx`: Beispieltext ("The quick brown fox...") der in der aktuell ausgewaehlten Font gerendert wird.

- [ ] **Step 4: Run Tests**

Run: `cd apps/platform && pnpm vitest run`

- [ ] **Step 5: Run Build**

Run: `nx build platform`

- [ ] **Step 6: Commit**

```bash
git add apps/platform/src/features/settings/components/ apps/platform/messages/
git commit -m "feat: refactor branding form to tabs with font preview"
```

---

## Task 12: Frontend — AppHeader Redesign (Paket 4)

**Files:**
- Rewrite: `apps/platform/src/components/layout/app-header.tsx`
- Test: `apps/platform/src/components/layout/__tests__/app-header.test.tsx`

- [ ] **Step 1: Schreibe Tests fuer die neue AppHeader API**

```typescript
// Tests fuer:
// - List-Modus: title + description + actions
// - Detail-Modus: breadcrumbs + actions
// - Breadcrumb-Kollaps bei > 3 Segmenten
// - Sticky behavior (className check)
```

- [ ] **Step 2: Run Tests — fehlschlagen**

- [ ] **Step 3: Implementiere neue AppHeader-Komponente**

Union Type API:
```tsx
type PageHeaderProps = {
  actions?: React.ReactNode;
} & (
  | { mode: "list"; title: string; description?: string; breadcrumbs?: never }
  | { mode: "detail"; breadcrumbs: BreadcrumbSegment[]; title?: never; description?: never }
);
```

- List-Modus: h1 Titel links, Actions rechts, optionale Description als muted Text
- Detail-Modus: Breadcrumbs links (shadcn `<Breadcrumb>`), Actions rechts
- Breadcrumb-Kollaps: > 3 Segmente → mittlere als `...` mit shadcn `<DropdownMenu>`
- `sticky top-0 z-10 bg-background` + `border-b`

- [ ] **Step 4: Run Tests — gruen**

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/components/layout/app-header.tsx apps/platform/src/components/layout/__tests__/app-header.test.tsx
git commit -m "feat: redesign AppHeader as contextual top-bar with union type API"
```

---

## Task 13: Frontend — Page Migration (Paket 4)

**Files:**
- Modify: Alle 14+ Pages die AppHeader verwenden (siehe File Structure oben)

Jede Page wird migriert: eigener Page-Header entfernt, `mode` + `title`/`breadcrumbs` + `actions` an AppHeader.

- [ ] **Step 1: Migriere Platform-Seiten**

- `(platform)/tenants/page.tsx` → `mode="list"` + `title={t("tenants.title")}` + Create-Button als `actions`
- `(platform)/tenants/[id]/page.tsx` → `mode="detail"` + Breadcrumbs `[Tenants, TenantName]` + Actions
- `(platform)/tenants/[id]/config/page.tsx` → `mode="detail"` + Breadcrumbs
- `(platform)/admins/page.tsx` → `mode="list"`
- `(platform)/settings/page.tsx` → `mode="list"`

- [ ] **Step 2: Migriere Workspace-Seiten**

- `(workspace)/dashboard/page.tsx` → `mode="list"` + `title={t("dashboard.title")}`
- `(workspace)/users/page.tsx` → `mode="list"` + Invite-Button
- `(workspace)/users/[userId]/page.tsx` → `mode="detail"` + Breadcrumbs
- `(workspace)/roles/page.tsx` → `mode="list"` + Create-Button
- `(workspace)/roles/[roleId]/page.tsx` → `mode="detail"` + Breadcrumbs
- `(workspace)/apps/page.tsx` → `mode="list"`
- `(workspace)/settings/branding/page.tsx` → `mode="list"` + `title={t("config.branding.title")}`
- `(workspace)/settings/domain/page.tsx` → `mode="list"`
- `(workspace)/settings/email/page.tsx` → `mode="list"`

- [ ] **Step 3: Migriere Profile-Seite**

- `(profile)/profile/page.tsx` → `mode="list"` + `title={t("profile.title")}`

- [ ] **Step 4: Entferne redundante Page-Header aus jeder Seite**

Jede Seite hat aktuell einen eigenen Titel/Header im Content-Bereich. Diesen entfernen — der Titel kommt jetzt aus der Top-Bar.

- [ ] **Step 5: Run Tests + Build**

```bash
cd apps/platform && pnpm vitest run && nx build platform
```

- [ ] **Step 6: Commit**

```bash
git add apps/platform/src/app/
git commit -m "feat: migrate all pages to contextual top-bar layout"
```

---

## Task 14: PROGRESS.md + Abschluss

**Files:**
- Modify: `docs/PROGRESS.md`

- [ ] **Step 1: Aktualisiere PROGRESS.md**

Neue Sektion "Phase 3j — UI/UX Improvements" mit allen abgeschlossenen Tasks.

- [ ] **Step 2: Full Test Suite + Lint + Build**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
cd apps/platform && pnpm vitest run
nx lint platform
nx build platform
```

- [ ] **Step 3: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md with Phase 3j UI/UX improvements"
```
