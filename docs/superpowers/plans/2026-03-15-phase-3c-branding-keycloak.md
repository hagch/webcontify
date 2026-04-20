# Phase 3c: Keycloak Branding + Branding Settings UI — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keycloak Login-Seiten und Emails (Invite, Password Reset) zeigen Tenant-Branding. Tenant-Admins koennen Logo und Farben ueber die Branding Settings UI verwalten.

**Architecture:** Custom Keycloak FreeMarker-Theme (`webcontify`) liest Realm-Attributes dynamisch. Backend synct Branding-Aenderungen per Spring Modulith Event (`TenantBrandingUpdatedEvent`) von der tenant-Domain zur identity-Domain, die Keycloak Realm-Attributes aktualisiert. Frontend-UI nutzt shadcn File Upload + Color Picker.

**Tech Stack:** Keycloak 26 FreeMarker Themes, Spring Modulith Events, Spring Boot 4, Next.js 16, shadcn/ui (File Upload), React Hook Form + Zod, Orval

**Spec:** `docs/superpowers/specs/2026-03-15-phase-3c-branding-keycloak-design.md`

---

## Chunk 1: Keycloak Custom Theme + Docker Setup

### Task 1: Keycloak Theme — Login

**Files:**
- Create: `infra/keycloak/themes/webcontify/login/theme.properties`
- Create: `infra/keycloak/themes/webcontify/login/resources/css/styles.css`
- Create: `infra/keycloak/themes/webcontify/login/template.ftl`

- [ ] **Step 1: Create login theme.properties**

```properties
parent=keycloak
```

File: `infra/keycloak/themes/webcontify/login/theme.properties`

Note: Kein `import=common/keycloak` — in Keycloak 26 ist das veraltet. `parent=keycloak` erbt automatisch alle Common-Ressourcen.

- [ ] **Step 2: Create login template.ftl to inject CSS variables and logo**

File: `infra/keycloak/themes/webcontify/login/template.ftl`

This overrides the parent `template.ftl` to inject realm-attribute-based CSS variables and the logo.

**WICHTIG:** Vor dem Schreiben muss die Keycloak 26 base `template.ftl` gelesen werden. Keycloak 26 nutzt **PatternFly 4** (nicht PF3). Lade das Base-Theme aus dem laufenden Keycloak-Container:

```bash
docker cp $(docker ps -q -f name=keycloak):/opt/keycloak/lib/lib/main/org.keycloak.keycloak-themes-26.0.0.jar /tmp/kc-themes.jar
cd /tmp && unzip -o kc-themes.jar "theme/keycloak/login/*" -d kc-themes
```

Dann die `template.ftl` lesen und minimal ueberschreiben. Key additions:
- `<style>` block in `<head>` that sets CSS variables from `realm.attributes`
- Logo `<img>` tag reading `realm.attributes.brandLogo`
- Tenant name from `realm.attributes.brandTenantName`

Security: All attribute values must use `?html` escaping in HTML context. Color values are pre-validated server-side to `^#[0-9a-fA-F]{6}$`.

Fallback-Werte = Webcontify-Branding (Indigo #6366F1, "Webcontify"). Platform Realm nutzt diese Defaults, Tenant Realms ueberschreiben via Realm-Attributes.

- [ ] **Step 3: Create login styles.css**

File: `infra/keycloak/themes/webcontify/login/resources/css/styles.css`

**WICHTIG:** Die CSS-Selektoren muessen zu Keycloak 26 PatternFly 4 Klassen passen. Pruefe die tatsaechlichen Klassen aus der extrahierten Base-Theme `template.ftl` (Step 2). Die CSS-Variablen `--wc-primary`, `--wc-primary-hover` werden per FreeMarker im `template.ftl` gesetzt und hier referenziert.

Grundstruktur (Selektoren anpassen nach Base-Theme-Analyse):

```css
/* Webcontify Custom Login Theme */
/* CSS Variables werden im template.ftl per FreeMarker gesetzt */

/* Primary button */
.pf-c-button.pf-m-primary {
    background-color: var(--wc-primary);
    border-color: var(--wc-primary);
}

.pf-c-button.pf-m-primary:hover {
    background-color: var(--wc-primary-hover);
    border-color: var(--wc-primary-hover);
}

/* Links */
a {
    color: var(--wc-primary);
}

/* Logo */
.kc-logo-text {
    display: none;
}
```

- [ ] **Step 4: Commit**

```bash
git add infra/keycloak/themes/webcontify/login/
git commit -m "feat: add Keycloak custom login theme (webcontify)"
```

---

### Task 2: Keycloak Theme — Email Templates

**Files:**
- Create: `infra/keycloak/themes/webcontify/email/theme.properties`
- Create: `infra/keycloak/themes/webcontify/email/html/executeActions.ftl`
- Create: `infra/keycloak/themes/webcontify/email/html/password-reset.ftl`
- Create: `infra/keycloak/themes/webcontify/email/text/executeActions.ftl`
- Create: `infra/keycloak/themes/webcontify/email/text/password-reset.ftl`

- [ ] **Step 1: Create email theme.properties**

```properties
parent=keycloak
```

File: `infra/keycloak/themes/webcontify/email/theme.properties`

- [ ] **Step 2: Read Keycloak 26 base email templates**

Extrahiere die Base-Templates:

```bash
cd /tmp && unzip -o kc-themes.jar "theme/keycloak/email/*" -d kc-themes
```

Lies `theme/keycloak/email/html/executeActions.ftl` und `password-reset.ftl` um die erforderlichen FreeMarker-Variablen zu verstehen (`${link}`, `${requiredActions}`, `${realmName}`, `${expiration}`, etc.).

- [ ] **Step 3: Create HTML executeActions.ftl (Invite Email)**

Template structure:
- HTML email mit **Inline-Styles** (Email-Clients ignorieren externes CSS)
- Header: Logo `<img>` from `${(realm.attributes.brandLogo!'')?html}` mit `<#if>`-Fallback zu Tenant-Name als Text
- Tenant name: `${(realm.attributes.brandTenantName!realm.displayName)?html}`
- CTA button background: `style="background-color: ${(realm.attributes.brandPrimaryColor!'#6366F1')}"` (pre-validated server-side)
- Body: Standard Keycloak action links (aus Base-Template uebernehmen)
- Footer: Tenant name

Security: `?html` auf alle Text-Werte. Color-Werte nur in `style=""`-Attributen — serverseitig auf Hex-Pattern validiert.

- [ ] **Step 4: Create HTML password-reset.ftl**

Gleiche HTML-Struktur wie executeActions.ftl, aber mit Password-Reset-Text und `${link}` fuer die Reset-URL. FreeMarker-Variablen aus dem Base-Template uebernehmen.

- [ ] **Step 5: Create text/executeActions.ftl (Plaintext)**

Plaintext-Variante. **Wichtig:** Message-Key ist `executeActionsBody` (NICHT `executeActionsBodyHtml`).

```
${(realm.attributes.brandTenantName!realm.displayName)}
---

[Aktions-Text aus Base-Template uebernehmen — msg() Calls beibehalten]

---
${(realm.attributes.brandTenantName!realm.displayName)}
```

- [ ] **Step 6: Create text/password-reset.ftl (Plaintext)**

Gleiches Pattern wie text/executeActions.ftl mit Password-Reset Message-Key.

- [ ] **Step 7: Commit**

```bash
git add infra/keycloak/themes/webcontify/email/
git commit -m "feat: add Keycloak branded email templates (invite + password reset)"
```

---

### Task 3: Docker + Platform Realm Setup

**Files:**
- Modify: `infra/docker-compose.dev.yml:30-53` (keycloak volumes)
- Modify: `infra/keycloak/platform-realm.json:6-9` (theme fields)

- [ ] **Step 1: Add theme volume mount to docker-compose.dev.yml**

In `infra/docker-compose.dev.yml`, add to keycloak volumes (after line ~39):

```yaml
      - ./keycloak/themes/webcontify:/opt/keycloak/themes/webcontify:ro
```

- [ ] **Step 2: Update platform-realm.json theme fields**

In `infra/keycloak/platform-realm.json`, change lines 6-9:

```json
  "loginTheme": "webcontify",
  "accountTheme": "keycloak.v3",
  "adminTheme": "keycloak.v2",
  "emailTheme": "webcontify",
```

- [ ] **Step 3: Manual test — restart Keycloak, verify theme loads**

```bash
cd infra && docker compose -f docker-compose.dev.yml up -d keycloak --force-recreate
```

Open `auth.localhost` → Platform realm → Login tab → verify "webcontify" theme is listed.
Open `platform.localhost` in incognito → verify login page has Webcontify styling (Indigo buttons).

- [ ] **Step 4: Commit**

```bash
git add infra/docker-compose.dev.yml infra/keycloak/platform-realm.json
git commit -m "feat: mount webcontify theme in Docker + set as platform default"
```

---

## Chunk 2: Backend — Event + Branding Sync

### Task 4: TenantBrandingUpdatedEvent

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/tenant/event/TenantBrandingUpdatedEvent.java`

- [ ] **Step 1: Create the event record**

File: `apps/backend/src/main/java/io/webcontify/tenant/event/TenantBrandingUpdatedEvent.java`

```java
package io.webcontify.tenant.event;

import java.util.UUID;

public record TenantBrandingUpdatedEvent(
        UUID tenantId,
        String tenantName,
        String keycloakRealmName,
        String primaryColor,
        String secondaryColor,
        String logoPublicUrl
) {}
```

Package: `io.webcontify.tenant.event` (gleich wie `TenantCreatedEvent`). Dieses Package ist als `@NamedInterface("tenant-events")` exponiert und wird vom `identity`-Modul via `allowedDependencies` konsumiert. Alle Felder nullable ausser tenantId.

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/tenant/event/TenantBrandingUpdatedEvent.java
git commit -m "feat: add TenantBrandingUpdatedEvent record"
```

---

### Task 5: TenantConfigService — Publish Event on Branding Update

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/tenant/service/TenantConfigService.java:37-46`
- Test: `apps/backend/src/test/java/io/webcontify/tenant/service/TenantConfigServiceBrandingEventTest.java` (create)

- [ ] **Step 1: Write failing test — event is published on branding update**

Create test file: `apps/backend/src/test/java/io/webcontify/tenant/service/TenantConfigServiceBrandingEventTest.java`

Test that when `updateBranding()` is called:
1. BrandingConfig is saved in DB (existing behavior)
2. `TenantBrandingUpdatedEvent` is published via `ApplicationEventPublisher`
3. Event contains correct tenantName, keycloakRealmName, primaryColor, secondaryColor, logoPublicUrl

Mock: `TenantRepository`, `ApplicationEventPublisher`, `TenantFileService` (fuer URL-Aufloesung)
Verify: `eventPublisher.publishEvent(argThat(event -> ...))` prueft alle Felder.

Note: `TenantFileService` liegt im selben `tenant`-Modul — Intra-Modul-Zugriff, kein Modulith-Problem.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantConfigServiceBrandingEventTest*" -i
```

Expected: FAIL — TenantConfigService doesn't publish events yet.

- [ ] **Step 3: Implement — inject dependencies and publish event**

Modify `apps/backend/src/main/java/io/webcontify/tenant/service/TenantConfigService.java`:

1. Add fields: `ApplicationEventPublisher eventPublisher`, `TenantFileService tenantFileService`
2. In `updateBranding()` method (lines 37-46), after saving to DB:
   - Die `TenantEntity` ist bereits via `findTenantOrThrow()` geladen — daraus `name` und `keycloakRealmName` lesen
   - `logoStorageKey` → public URL: Bucket-Name aus Entity-Slug berechnen (vermeidet doppelten DB-Lookup in TenantFileService). Alternativ `tenantFileService.getFileUrl()` nutzen wenn der N+1-Overhead akzeptabel ist
   - Publish `TenantBrandingUpdatedEvent` mit allen Feldern

- [ ] **Step 4: Run test to verify it passes**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantConfigServiceBrandingEventTest*" -i
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/tenant/service/TenantConfigService.java apps/backend/src/test/java/io/webcontify/tenant/service/TenantConfigServiceBrandingEventTest.java
git commit -m "feat: publish TenantBrandingUpdatedEvent on branding update"
```

---

### Task 6: TenantBrandingSyncListener

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/TenantBrandingSyncListener.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java:89-94` (fix merge)
- Test: `apps/backend/src/test/java/io/webcontify/identity/service/TenantBrandingSyncListenerTest.java`

- [ ] **Step 1: Write failing tests**

Create test: `apps/backend/src/test/java/io/webcontify/identity/service/TenantBrandingSyncListenerTest.java`

Tests:
1. `syncsBrandingToKeycloak()` — Event with all fields → `updateRealmBranding()` called with correct attribute map (`brandLogo`, `brandPrimaryColor`, `brandSecondaryColor`, `brandTenantName`)
2. `skipsInvalidHexColor()` — Event with `primaryColor = "not-a-hex"` → attribute map does NOT contain `brandPrimaryColor`
3. `skipsInvalidLogoUrl()` — Event with `logoPublicUrl = "javascript:alert(1)"` → attribute map does NOT contain `brandLogo`
4. `handlesKeycloakFailureGracefully()` — `updateRealmBranding()` throws → logged, no exception propagated

Mock: `KeycloakAdminService`

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantBrandingSyncListenerTest*" -i
```

Expected: FAIL — class doesn't exist yet.

- [ ] **Step 3: Fix KeycloakAdminService.updateRealmBranding() — MERGE statt REPLACE**

**WICHTIG:** Die aktuelle Implementierung (`KeycloakAdminService.java:92`) macht `realm.setAttributes(request.brandingAttributes())` — das **ersetzt** alle Realm-Attributes komplett. Keycloak-interne Attributes gehen verloren.

Fix in `KeycloakAdminService.java:89-94`:

```java
public void updateRealmBranding(UpdateRealmBrandingRequest request) {
    var realmResource = keycloak.realm(request.realmName());
    var realm = realmResource.toRepresentation();
    var existing = realm.getAttributes() != null
            ? new HashMap<>(realm.getAttributes())
            : new HashMap<String, String>();
    existing.putAll(request.brandingAttributes());
    realm.setAttributes(existing);
    realmResource.update(realm);
}
```

- [ ] **Step 4: Implement TenantBrandingSyncListener**

File: `apps/backend/src/main/java/io/webcontify/identity/service/TenantBrandingSyncListener.java`

```java
package io.webcontify.identity.service;

import io.webcontify.identity.model.UpdateRealmBrandingRequest;
import io.webcontify.tenant.event.TenantBrandingUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TenantBrandingSyncListener {

    private static final Logger log = LoggerFactory.getLogger(TenantBrandingSyncListener.class);
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern SAFE_URL = Pattern.compile("^https?://[^\"'<>]+$");

    private final KeycloakAdminService keycloakAdminService;

    public TenantBrandingSyncListener(KeycloakAdminService keycloakAdminService) {
        this.keycloakAdminService = keycloakAdminService;
    }

    @ApplicationModuleListener
    public void onBrandingUpdated(TenantBrandingUpdatedEvent event) {
        var attributes = buildAttributes(event);
        try {
            keycloakAdminService.updateRealmBranding(
                    new UpdateRealmBrandingRequest(event.keycloakRealmName(), attributes));
            log.info("Synced branding to Keycloak realm '{}'", event.keycloakRealmName());
        } catch (Exception e) {
            log.warn("Failed to sync branding to Keycloak realm '{}': {}",
                    event.keycloakRealmName(), e.getMessage(), e);
        }
    }

    Map<String, String> buildAttributes(TenantBrandingUpdatedEvent event) {
        var attrs = new HashMap<String, String>();
        if (event.tenantName() != null) {
            attrs.put("brandTenantName", event.tenantName());
        }
        if (event.primaryColor() != null && HEX_COLOR.matcher(event.primaryColor()).matches()) {
            attrs.put("brandPrimaryColor", event.primaryColor());
        }
        if (event.secondaryColor() != null && HEX_COLOR.matcher(event.secondaryColor()).matches()) {
            attrs.put("brandSecondaryColor", event.secondaryColor());
        }
        if (event.logoPublicUrl() != null && SAFE_URL.matcher(event.logoPublicUrl()).matches()) {
            attrs.put("brandLogo", event.logoPublicUrl());
        }
        return attrs;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantBrandingSyncListenerTest*" -i
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/TenantBrandingSyncListener.java apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java apps/backend/src/test/java/io/webcontify/identity/service/TenantBrandingSyncListenerTest.java
git commit -m "feat: add TenantBrandingSyncListener + fix realm attributes merge"
```

---

### Task 7: CreateRealmRequest Extension + Provisioning Theme Setup

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/model/CreateRealmRequest.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java:23-51` (createRealm)
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java:73-86` (Step 1)
- Test: Extend existing `apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningListenerTest.java`

- [ ] **Step 1: Write failing test — provisioning sets theme and default attributes on realm**

Extend `TenantProvisioningListenerTest`: Add test that verifies `createRealm()` is called with:
- `loginTheme = "webcontify"`
- `emailTheme = "webcontify"`
- `initialAttributes` containing `brandTenantName`, `brandPrimaryColor` (#6366F1), `brandSecondaryColor` (#10B981)

Use `ArgumentCaptor<CreateRealmRequest>` to capture and verify all fields.

- [ ] **Step 2: Run test to verify it fails**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantProvisioningListenerTest*" -i
```

Expected: FAIL — CreateRealmRequest doesn't have theme fields yet (compile error).

- [ ] **Step 3: Extend CreateRealmRequest**

File: `apps/backend/src/main/java/io/webcontify/identity/model/CreateRealmRequest.java`

```java
package io.webcontify.identity.model;

import java.util.Map;

public record CreateRealmRequest(
        String tenantSlug,
        SmtpConfig platformSmtpConfig,
        String loginTheme,
        String emailTheme,
        Map<String, String> initialAttributes
) {}
```

- [ ] **Step 4: Update KeycloakAdminService.createRealm() to use new fields**

In `KeycloakAdminService.java`, update `createRealm()` method (around line 25-50). After `realm.setSmtpServer(...)`, add:

```java
if (request.loginTheme() != null) {
    realm.setLoginTheme(request.loginTheme());
}
if (request.emailTheme() != null) {
    realm.setEmailTheme(request.emailTheme());
}
if (request.initialAttributes() != null) {
    realm.setAttributes(request.initialAttributes());
}
```

- [ ] **Step 5: Update TenantProvisioningListener to pass theme + attributes**

In `TenantProvisioningListener.java`, update Step 1 (Keycloak Realm, around line 74-85). Change `new CreateRealmRequest(event.slug(), smtpConfig)` to:

```java
new CreateRealmRequest(
    event.slug(),
    smtpConfig,
    "webcontify",
    "webcontify",
    Map.of(
        "brandTenantName", tenantEntity.getName(),
        "brandPrimaryColor", "#6366F1",
        "brandSecondaryColor", "#10B981"
    )
)
```

- [ ] **Step 6: Fix all other call sites of CreateRealmRequest**

Betroffene Dateien (vollstaendige Liste):
- `apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningListenerTest.java` — bestehende Tests nutzen `any(CreateRealmRequest.class)` Matcher, sollten weiterhin kompilieren
- `apps/backend/src/test/java/io/webcontify/identity/service/KeycloakAdminServiceTest.java` — falls `new CreateRealmRequest(...)` direkt aufgerufen wird, Felder ergaenzen
- `apps/backend/src/test/java/io/webcontify/identity/model/KeycloakAdminRecordTest.java` — Record-Test updaten

Suche: `grep -r "new CreateRealmRequest" apps/backend/src/`

- [ ] **Step 7: Run all provisioning + Keycloak tests**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantProvisioning*" --tests "*KeycloakAdmin*" -i
```

Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/ apps/backend/src/test/java/io/webcontify/identity/
git commit -m "feat: set webcontify theme + default branding on tenant realm creation"
```

---

### Task 8: File Upload Backend-Validierung + Tenant-Context Fix

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/tenant/api/TenantFileController.java`
- Test: `apps/backend/src/test/java/io/webcontify/tenant/api/TenantFileControllerValidationTest.java` (create)

- [ ] **Step 1: Write failing tests — content-type, file size, and tenant context**

Create test: `TenantFileControllerValidationTest.java`

Tests:
1. `rejectsSvgUpload()` — Upload with `contentType=image/svg+xml` → 400 Bad Request
2. `rejectsOversizedFile()` — Upload with file > 2MB → 400 Bad Request
3. `acceptsPngUpload()` — Upload with `contentType=image/png`, <2MB → 201 Created
4. `acceptsJpegUpload()` — Upload with `contentType=image/jpeg` → 201 Created
5. `acceptsIcoUpload()` — Upload with `contentType=image/x-icon` → 201 Created

Use Spring Boot Integration Test with Testcontainers (wie bestehende Controller-Tests).

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantFileControllerValidationTest*" -i
```

Expected: FAIL — no validation exists.

- [ ] **Step 3: Fix PLACEHOLDER_TENANT_ID Security-Luecke**

**KRITISCH:** `TenantFileController.java` nutzt aktuell eine hardcoded `PLACEHOLDER_TENANT_ID`. Das ist eine Auth-Bypass-Luecke — ein User koennte Files in fremde Tenant-Buckets hochladen.

Ersetze in `TenantFileController.java`:
- `PLACEHOLDER_TENANT_ID` → `TenantContext.current().tenantId()` (oder die bestehende Tenant-Resolution aus dem SecurityContext)
- Pruefe wie andere Self-Service Controller (z.B. `TenantSelfServiceController`) die Tenant-ID aufloesen und folge dem gleichen Pattern

- [ ] **Step 4: Add content-type and size validation**

In `TenantFileController.java`, update `uploadFile()` method:

```java
private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/png", "image/jpeg", "image/x-icon");
private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

// In uploadFile(), vor dem Upload:
if (file.getSize() > MAX_FILE_SIZE) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "File size exceeds maximum of 2MB");
}
if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "File type not allowed. Accepted: PNG, JPEG, ICO");
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantFileControllerValidationTest*" -i
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/tenant/api/TenantFileController.java apps/backend/src/test/java/io/webcontify/tenant/api/TenantFileControllerValidationTest.java
git commit -m "fix: replace placeholder tenant ID + add file upload validation"
```

---

## Chunk 3: Frontend — OpenAPI + Branding Settings UI

### Task 9: OpenAPI Spec Update + Orval Regeneration

**Files:**
- Modify: `openapi/tenant-api.yaml` (UpdateBrandingConfigRequest schema)
- Modify: `apps/platform/src/generated/` (regenerated)
- Modify: `apps/platform/src/components/ui/` (new file-upload component, falls verfuegbar)

- [ ] **Step 1: Extend UpdateBrandingConfigRequest in OpenAPI Spec**

In `openapi/tenant-api.yaml`, finde das `UpdateBrandingConfigRequest` Schema und ergaenze `logoStorageKey` und `faviconStorageKey`:

```yaml
UpdateBrandingConfigRequest:
  type: object
  properties:
    primaryColor:
      type: string
      pattern: '^#[0-9a-fA-F]{6}$'
    secondaryColor:
      type: string
      pattern: '^#[0-9a-fA-F]{6}$'
    logoStorageKey:
      type: string
      description: S3 storage key for the logo file
    faviconStorageKey:
      type: string
      description: S3 storage key for the favicon file
```

Pruefe auch `BrandingConfigResponse` — dort sollten `logoStorageKey` und `faviconStorageKey` bereits existieren (laut generiertem TypeScript Interface). Falls nicht, auch dort ergaenzen.

- [ ] **Step 2: Regenerate Backend OpenAPI**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
```

Verify: `UpdateBrandingConfigRequest` DTO hat jetzt `logoStorageKey` + `faviconStorageKey`.

- [ ] **Step 3: Update Backend TenantConfigService.updateBranding()**

Der existierende `updateBranding()` muss die neuen Felder aus dem Request in die BrandingConfig uebernehmen. Pruefe ob `BrandingConfig` bereits `logoStorageKey`/`faviconStorageKey` hat (ja, laut TenantConfig.java). Stelle sicher, dass der generierte Request die Werte korrekt durchreicht.

- [ ] **Step 4: Install shadcn file-upload component**

```bash
cd apps/platform && npx shadcn@latest add file-upload
```

Falls `file-upload` nicht im offiziellen shadcn Registry ist: Eigene Komponente bauen mit shadcn `Card` + `Button` + nativer HTML5 Drag-and-Drop API (`onDragOver`, `onDrop`, `<input type="file">`). Styling konsistent mit shadcn.

- [ ] **Step 5: Regenerate Orval API client**

```bash
npx nx generate platform
```

Verify: `UpdateBrandingConfigRequest` in `src/generated/tenant.ts` hat jetzt `logoStorageKey` + `faviconStorageKey`.

- [ ] **Step 6: Commit**

```bash
git add openapi/tenant-api.yaml apps/backend/src/main/java/io/webcontify/ apps/platform/src/components/ui/ apps/platform/src/generated/
git commit -m "feat: extend branding API with logo/favicon storage keys + regenerate clients"
```

---

### Task 10: Branding Settings Page — UI Implementation

**Files:**
- Modify: `apps/platform/src/app/(workspace)/settings/branding/page.tsx` (replace placeholder)
- Create: `apps/platform/src/features/settings/components/branding-form.tsx`
- Create: `apps/platform/src/features/settings/components/logo-upload.tsx`
- Create: `apps/platform/src/features/settings/components/color-picker-field.tsx`
- Modify: `apps/platform/src/lib/schemas/tenant-config.ts` (extend branding schema)

Note: Vor der Implementierung den `ui-ux-pro-max:ui-ux-pro-max` Skill aufrufen um das UI-Design der Branding Settings Seite zu reviewen und zu challengen. Feedback anwenden bevor Code geschrieben wird.

- [ ] **Step 1: Extend Zod schema for branding**

Modify `apps/platform/src/lib/schemas/tenant-config.ts`:

```typescript
export const updateBrandingSchema = z.object({
  primaryColor: z
    .string()
    .regex(/^#[0-9a-fA-F]{6}$/, "Ungueltiges Hex-Format")
    .optional()
    .or(z.literal("")),
  secondaryColor: z
    .string()
    .regex(/^#[0-9a-fA-F]{6}$/, "Ungueltiges Hex-Format")
    .optional()
    .or(z.literal("")),
  logoStorageKey: z.string().optional().or(z.literal("")),
  faviconStorageKey: z.string().optional().or(z.literal("")),
});
```

- [ ] **Step 2: Create ColorPickerField component**

File: `apps/platform/src/features/settings/components/color-picker-field.tsx`

Combines:
- shadcn `Input` fuer Hex-Wert Text-Input
- Nativer `<input type="color">` als visueller Picker (gestylt als kleines Quadrat neben dem Input)
- Verbunden via React Hook Form `Controller`

Props: `name`, `label`, `control` (from useForm)
Uses: shadcn `FormField`, `FormItem`, `FormLabel`, `FormControl`, `FormMessage`, `Input`

- [ ] **Step 3: Create LogoUpload component**

File: `apps/platform/src/features/settings/components/logo-upload.tsx`

Nutzt shadcn File Upload (oder Custom-Komponente falls nicht verfuegbar):
- Drag-and-Drop Zone
- Accept: `image/png, image/jpeg` (max 2MB)
- Vorschau des aktuellen Logos wenn `currentLogoUrl` Prop gesetzt
- Vorschau der neuen Datei (via `URL.createObjectURL`) nach Auswahl
- Loeschen-Button (cleared file + ruft `onDelete` callback)

Props: `currentLogoUrl?: string`, `onFileSelect: (file: File | null) => void`, `onDelete: () => void`, `accept?: string`, `maxSize?: number`, `label: string`
Uses: shadcn `Button`, `Card`

- [ ] **Step 4: Create BrandingForm component**

File: `apps/platform/src/features/settings/components/branding-form.tsx`

Orchestriert:
1. Laedt aktuelles Branding via `useGetTenantSelfConfig()` — **ACHTUNG:** Hook heisst `useGetTenantSelfConfig()`, NICHT `useGetTenantSelfBranding()`. Branding ist unter `data?.branding` (nested)
2. React Hook Form mit `zodResolver(updateBrandingSchema)`, `defaultValues` aus API
3. Logo Upload (LogoUpload Komponente)
4. Favicon Upload (LogoUpload Komponente mit ICO accept + 500KB limit)
5. Color Picker (ColorPickerField x2)
6. Submit Handler:
   a. Wenn neue Logo-Datei: Upload via `POST /api/v1/tenant/files` (public=true), storageKey zurueck
   b. Wenn neue Favicon-Datei: gleicher Upload-Flow
   c. `useUpdateTenantSelfBranding()` mit Form-Daten + Storage-Keys aufrufen
   d. Toast bei Erfolg/Fehler (Sonner)

Uses: shadcn `Form`, `Card`, `CardHeader`, `CardContent`, `Button`, `Separator`, Sonner toast

- [ ] **Step 5: Update branding page to use BrandingForm**

Replace placeholder in `apps/platform/src/app/(workspace)/settings/branding/page.tsx`:

```tsx
import { AppHeader } from "@/components/layout/app-header";
import { BrandingForm } from "@/features/settings/components/branding-form";

export default function BrandingSettingsPage() {
  return (
    <>
      <AppHeader
        breadcrumbs={[{ label: "Settings" }, { label: "Branding" }]}
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Branding</h1>
          <p className="text-sm text-muted-foreground">
            Passe das Erscheinungsbild deines Workspaces an.
          </p>
        </div>
        <BrandingForm />
      </div>
    </>
  );
}
```

- [ ] **Step 6: Commit**

```bash
git add apps/platform/src/features/settings/ apps/platform/src/app/\(workspace\)/settings/branding/page.tsx apps/platform/src/lib/schemas/tenant-config.ts
git commit -m "feat: implement branding settings page with logo upload + color picker"
```

---

### Task 11: Frontend Tests — Branding Form

**Files:**
- Create: `apps/platform/src/features/settings/components/__tests__/branding-form.test.tsx`
- Create: `apps/platform/src/features/settings/components/__tests__/color-picker-field.test.tsx`

- [ ] **Step 1: Write tests for ColorPickerField**

Tests:
1. Rendert Label, Hex-Input und Color-Picker
2. Updated Hex-Input wenn Color-Picker aendert
3. Zeigt Validierungsfehler bei ungueltigem Hex

- [ ] **Step 2: Write tests for BrandingForm**

Tests:
1. Rendert alle Sektionen (Logo, Favicon, Farben, Speichern-Button)
2. Laedt und zeigt aktuelles Branding aus API (Mock: `useGetTenantSelfConfig`)
3. Zeigt Validierungsfehler bei ungueltigem Farbformat
4. Submitted Formular mit korrekten Daten (verify API Calls)
5. Zeigt Loading State waehrend Submit
6. Zeigt Toast bei Erfolg
7. Zeigt Toast bei Fehler

Mock: Orval Hooks (`useGetTenantSelfConfig`, `useUpdateTenantSelfBranding`), fetch fuer File-Upload

- [ ] **Step 3: Run tests**

```bash
cd apps/platform && npx vitest run --reporter=verbose src/features/settings/
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/features/settings/components/__tests__/
git commit -m "test: add frontend tests for branding form + color picker"
```

---

## Chunk 4: Integration + Verification

### Task 12: Backend Integration Test — Branding Sync Flow

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/tenant/service/TenantConfigBrandingSyncIntegrationTest.java`

- [ ] **Step 1: Write integration test**

Spring Boot Integration Test with Testcontainers (PostgreSQL):
1. Create a tenant with realm (via existing test setup)
2. Update branding via `TenantConfigService.updateBranding()`
3. Verify `TenantBrandingUpdatedEvent` was published (use `@RecordApplicationEvents`)
4. Verify event contains correct attributes

Fuer den Keycloak-Teil: Mock `KeycloakAdminService` und verifiziere dass `updateRealmBranding()` mit der korrekten Attributes-Map aufgerufen wird. Voller Keycloak-Testcontainer-Test ist optional.

- [ ] **Step 2: Run integration test**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*TenantConfigBrandingSyncIntegrationTest*" -i
```

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/tenant/service/TenantConfigBrandingSyncIntegrationTest.java
git commit -m "test: add integration test for branding sync to Keycloak"
```

---

### Task 13: DevDataSeeder — Branding Testdaten

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/shared/DevDataSeeder.java` (oder wo der Seeder liegt)

- [ ] **Step 1: Erweitere DevDataSeeder um Branding-Testdaten**

Fuer den Acme-Test-Tenant: Setze BrandingConfig mit:
- `primaryColor: "#EF4444"` (Red — bewusst anders als Default um den Unterschied zu sehen)
- `secondaryColor: "#10B981"` (Emerald)
- Logo: Optional ein kleines Test-Logo in `infra/keycloak/` oder S3 (kann auch leer bleiben)

Setze auch die Keycloak Realm-Attributes fuer den Acme-Realm (via `keycloakAdminService.updateRealmBranding()`).

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/shared/
git commit -m "chore: add branding test data to DevDataSeeder"
```

---

### Task 14: Run Full Test Suite + Build Verification

- [ ] **Step 1: Run all backend tests**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -i
```

Expected: All tests PASS. Fix any regressions.

- [ ] **Step 2: Run all frontend tests**

```bash
cd apps/platform && npx vitest run
```

Expected: All tests PASS.

- [ ] **Step 3: Run frontend build**

```bash
npx nx build platform
```

Expected: Build succeeds without errors.

- [ ] **Step 4: Run Spring Modulith structure verification**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*ModulithStructureTest*" -i
```

Expected: PASS — verifies that `tenant.event` is correctly accessible by `identity` module.

- [ ] **Step 5: Fix any issues found, commit fixes**

---

### Task 15: Manual Verification + PROGRESS.md

**Files:**
- Modify: `docs/PROGRESS.md`

- [ ] **Step 1: Manual test — full branding flow**

1. Start system: `pnpm dev`
2. Login als Platform Admin auf `platform.localhost` → verify Webcontify-branded Login-Seite (Indigo Buttons, kein Keycloak-Default)
3. Tenant "Acme" oeffnen oder neuen erstellen
4. Login als Tenant Admin auf `acme.localhost` → verify Login-Seite mit Acme-Branding (Red, falls DevDataSeeder konfiguriert)
5. Settings → Branding oeffnen
6. Logo hochladen, Farben aendern
7. Speichern → Toast pruefen
8. Abmelden → Login-Seite auf `acme.localhost` pruefen → neue Farben?
9. User einladen (Users Seite)
10. `mail.localhost` (Mailpit) → Invite-Email mit Tenant-Logo + Farben pruefen
11. Password Reset auf Tenant Login-Seite ausloesen → Mailpit → gebrandete Email pruefen

- [ ] **Step 2: Update PROGRESS.md**

```markdown
## Phase 3c — Keycloak Branding + Settings UI (2026-03-15)

| Task | Status | Beschreibung |
|---|---|---|
| 3c.1 | Done | Keycloak Custom Theme (webcontify) — Login + Email FreeMarker Templates |
| 3c.2 | Done | Docker-Mount + Platform Realm Theme-Konfiguration |
| 3c.3 | Done | TenantBrandingUpdatedEvent + TenantConfigService Event-Publishing |
| 3c.4 | Done | TenantBrandingSyncListener — Realm-Attributes Sync zu Keycloak (mit Merge) |
| 3c.5 | Done | CreateRealmRequest erweitert — Theme + Default-Attributes bei Provisioning |
| 3c.6 | Done | File Upload Backend-Validierung + TenantContext Security Fix |
| 3c.7 | Done | OpenAPI Spec: UpdateBrandingConfigRequest um Storage Keys erweitert |
| 3c.8 | Done | Frontend: Branding Settings Seite (Logo Upload, Color Picker) |
| 3c.9 | Done | Frontend + Backend Tests |
| 3c.10 | Done | Integration Test: Branding Sync Flow |
| 3c.11 | Done | DevDataSeeder: Branding Testdaten fuer Acme |
```

- [ ] **Step 3: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md — Phase 3c complete"
```
