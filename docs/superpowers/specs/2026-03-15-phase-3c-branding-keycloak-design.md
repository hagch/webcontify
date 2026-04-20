# Phase 3c: Keycloak Branding + Branding Settings UI

## Ueberblick

Keycloak-Login und Emails (Invite, Password Reset) sollen das Tenant-Branding widerspiegeln. Dazu wird ein Custom Keycloak Theme erstellt, das Realm-Attributes dynamisch liest. Zusaetzlich wird die Branding Settings UI im Workspace implementiert, damit Tenant-Admins Logo und Farben verwalten koennen. Aenderungen werden per Spring Modulith Event an Keycloak synchronisiert.

## Entscheidungen

| Entscheidung | Begruendung |
|---|---|
| Keycloak Custom Theme statt eigener Email-Versand | Keycloak kann Login + Emails nativ rendern, kein doppelter Email-Stack |
| Realm-Attributes fuer dynamisches Branding | Ein Theme, alle Tenants — gleicher Ansatz wie in docs/05 geplant |
| Nur Invite + Password Reset Emails | Deckt 95% der User-Flows ab, restliche Emails spaeter nachruesten |
| Spring Modulith Event fuer Sync | tenant-Domain darf identity-Domain nicht direkt aufrufen (Bounded Context) |
| Kein dynamisches Platform-UI Theming | Whitelabel-Theming ist ein eigenes Feature, nicht in Phase 3c |
| shadcn File Upload Komponente | Konsistent mit dem restlichen UI-Stack |
| Invite-Flow bleibt bei Keycloak executeActionsEmail | Bereits in Phase 3b so implementiert. docs/05 beschreibt eigenen Token-Flow, dieser wird zugunsten des einfacheren Keycloak-Ansatzes verworfen. Branding ueber Theme statt eigene Emails |
| Kein SVG fuer Logo/Favicon | SVGs koennen JavaScript enthalten — Sicherheitsrisiko bei public S3 URLs. Nur PNG, JPG, ICO erlaubt |
| Favicon nicht in Keycloak Theme | Keycloak-Themes laden Favicons aus `resources/`, nicht dynamisch per Realm-Attribute. Favicon-Upload in UI, aber keine Keycloak-Integration |
| Platform Realm bekommt eigenes Base-Theme | Platform-Login (`platform.localhost`) soll Webcontify-Branding haben (Logo, Indigo/Emerald), nicht Keycloak-Default |

## 1. Keycloak Custom Theme

### Theme-Struktur

```
infra/keycloak/themes/webcontify/
├── login/
│   ├── theme.properties          ← parent=keycloak (Keycloak 26 Basis-Theme)
│   └── resources/css/styles.css  ← CSS mit realm-attribute-basierten Variablen
├── email/
│   ├── theme.properties          ← parent=keycloak
│   ├── html/
│   │   ├── executeActions.ftl    ← Invite Email (Set Password / Verify Email)
│   │   └── password-reset.ftl   ← Password Reset Email
│   └── text/
│       ├── executeActions.ftl    ← Plaintext-Fallback
│       └── password-reset.ftl   ← Plaintext-Fallback
```

`theme.properties` muss `parent=keycloak` setzen — das ist der Name des Basis-Themes in Keycloak 26. Fehlt der Parent, schlaegt das Theme-Loading lautlos fehl.

### Platform Realm: Statisches Base-Theme

Das `webcontify`-Theme dient fuer **beide** Anwendungsfaelle:
- **Platform Realm** (`platform.localhost`): Statisches Webcontify-Branding (Logo, Indigo Primary, "Webcontify" als Titel). Keine Realm-Attributes noetig — die Defaults im Theme sind das Webcontify-Branding.
- **Tenant Realms** (`acme.localhost` etc.): Dynamisches Branding via Realm-Attributes. Wenn Attributes gesetzt sind, ueberschreiben sie die Defaults.

Das heisst: Die FreeMarker-Templates und CSS verwenden Fallback-Werte die dem Webcontify-Branding entsprechen. Tenant-Realms ueberschreiben diese mit ihren eigenen Werten. Platform-Realm nutzt die Defaults.

`platform-realm.json` wird angepasst: `loginTheme` und `emailTheme` auf `"webcontify"` setzen.

### Realm-Attributes

| Attribut | Quelle | Beispiel | Fallback |
|---|---|---|---|
| `brandLogo` | S3 public URL aus `logoStorageKey` | `https://s3.localhost/tenant-acme/config/logo.png` | Kein Logo (nur Tenant-Name als Text) |
| `brandPrimaryColor` | `BrandingConfig.primaryColor` | `#6366F1` | `#6366F1` (Indigo) |
| `brandSecondaryColor` | `BrandingConfig.secondaryColor` | `#10B981` | `#10B981` (Emerald) |
| `brandTenantName` | `Tenant.name` | `Acme Corp` | Realm-Name |

### FreeMarker-Templates — Security

Alle Realm-Attributes muessen escaped werden:
- **HTML-Kontext:** `${(realm.attributes.brandTenantName!'Fallback')?html}`
- **CSS-Kontext:** Werte werden serverseitig auf `^#[0-9a-fA-F]{6}$` validiert bevor sie in Realm-Attributes geschrieben werden. Im Template nur innerhalb von CSS-Variablen verwenden.
- **URL-Kontext (Logo `src`):** Wert wird serverseitig auf `^https?://[^"'<>]+$` validiert. Im Template: `${(realm.attributes.brandLogo!'')?html}`

### FreeMarker-Templates — Inhalt

**Login-Seite:**
- Tenant-Logo oben (oder Tenant-Name als Text-Fallback)
- Primary Color fuer Login-Button und Links
- Tenant-Name als Titel

**Email-Templates (HTML):**
- Header: Tenant-Logo (oder Tenant-Name als Text wenn kein Logo)
- Body: Standard-Keycloak-Inhalt (Aktionslink)
- CTA-Button: Primary Color als Hintergrund
- Footer: Tenant-Name

**Email-Templates (Text):**
- Tenant-Name im Header
- Standard-Keycloak-Text mit Aktionslink

### Docker-Mount

```yaml
# infra/docker-compose.dev.yml (relativ zu infra/)
keycloak:
  volumes:
    - ./keycloak/platform-realm.json:/opt/keycloak/data/import/platform-realm.json:ro
    - ./keycloak/themes/webcontify:/opt/keycloak/themes/webcontify:ro
```

## 2. Backend: Branding Sync

### Event-Flow

```
Tenant-Admin speichert Branding (UI)
    |
    v
TenantConfigService.updateBranding()
    |-- Speichert BrandingConfig in DB (existiert bereits)
    |-- Konvertiert logoStorageKey → S3 public URL (innerhalb tenant-Modul via TenantFileService)
    |-- Publiziert TenantBrandingUpdatedEvent
    v
TenantBrandingSyncListener (identity-Modul, @ApplicationModuleListener)
    |-- Validiert Werte (Hex-Format, URL-Format) vor Keycloak-Write
    |-- Baut Realm-Attributes Map
    |-- Ruft keycloakAdminService.updateRealmBranding() auf (atomarer realm.update())
```

### TenantBrandingUpdatedEvent

Package: `io.webcontify.tenant.event` (im publizierenden Modul, analog zu `TenantCreatedEvent`)

```java
public record TenantBrandingUpdatedEvent(
    UUID tenantId,
    String tenantName,
    String keycloakRealmName,
    String primaryColor,        // nullable, bereits validiert (Hex)
    String secondaryColor,      // nullable, bereits validiert (Hex)
    String logoPublicUrl        // nullable, bereits aufgeloeste S3 URL
) {}
```

Kein `faviconPublicUrl` — Favicon ist in Keycloak-Themes nicht dynamisch per Realm-Attribute einbindbar.

### Backend-Validierung vor Keycloak-Write

Im `TenantBrandingSyncListener` vor dem Schreiben der Realm-Attributes:
- `primaryColor` / `secondaryColor`: Muss `^#[0-9a-fA-F]{6}$` matchen, sonst wird der Wert nicht geschrieben
- `logoPublicUrl`: Muss `^https?://[^"'<>]+$` matchen, sonst wird der Wert nicht geschrieben
- `brandTenantName`: Wird escaped geschrieben (Keycloak speichert Raw, FreeMarker escaped bei Ausgabe)

### Provisioning-Anpassung

`CreateRealmRequest` wird erweitert um `loginTheme`, `emailTheme` und `Map<String, String> initialAttributes`.

`TenantProvisioningListener` setzt beim Realm-Erstellen:
- `loginTheme` → `"webcontify"`
- `emailTheme` → `"webcontify"`
- Default Realm-Attributes: `brandTenantName` = Tenant-Name, `brandPrimaryColor` = `#6366F1`, `brandSecondaryColor` = `#10B981`

### Fehlerbehandlung

Wenn der Keycloak-Sync fehlschlaegt:
- DB-Update bleibt bestehen (kein Rollback)
- Error wird geloggt (WARN-Level mit tenantId + realmName)
- Frontend bekommt 200 (Branding gespeichert), aber Response-Body enthaelt `keycloakSyncSuccess: false`
- Naechstes Speichern synct erneut

Keycloak `realm.update()` ist atomar (vollstaendige Attribute-Map-Ersetzung) — kein partielles Update moeglich.

### File-Upload Backend-Validierung

`TenantFileController` (bzw. Spring Config) muss serverseitig validieren:
- **Groesse:** Logo max 2MB, Favicon max 500KB (via `spring.servlet.multipart.max-file-size` + explizite Pruefung im Controller)
- **Content-Type Allowlist:** `image/png`, `image/jpeg`, `image/x-icon` — kein `image/svg+xml`
- Dateien die die Validierung nicht bestehen → 400 Bad Request

## 3. Frontend: Branding Settings Seite

### Seite

`(workspace)/settings/branding/page.tsx` — ersetzt den aktuellen Placeholder.

### Upload-Flow

```
1. User waehlt/dropped Datei in File Upload Zone
2. Sofortige Vorschau (Client-seitig, via FileReader/Object URL)
3. User klickt "Speichern"
4. Frontend uploaded Datei an TenantFile-Endpunkt (POST /api/v1/tenant/files)
   → Erhaelt storageKey zurueck (z.B. "config/logo.png")
5. Frontend sendet Branding-Update (PUT /api/v1/tenant/config/branding)
   mit primaryColor, secondaryColor, logoStorageKey
6. Backend speichert in DB + publiziert Event → Keycloak Sync
```

Upload und Branding-Update sind zwei getrennte API-Calls. Fehlerbehandlung:
- Upload fehlgeschlagen → Toast, Branding-Update wird nicht gesendet
- Branding-Update fehlgeschlagen → Toast mit Fehlerdetails

### Formular-Bereiche

**Logo Upload:**
- shadcn File Upload Komponente (Drag-and-Drop + Vorschau)
- Akzeptiert: PNG, JPG (max 2MB)
- Upload an bestehenden TenantFileService (S3 `config/logo`)
- Loeschen-Button wenn Logo vorhanden
- Vorschau des aktuellen/neuen Logos in der Drop-Zone

**Favicon Upload:**
- Gleiche Mechanik wie Logo
- Akzeptiert: PNG, ICO (max 500KB)

**Primary Color:**
- shadcn Input mit Hex-Wert + nativer `<input type="color">` als Picker
- Validierung: `^#[0-9a-fA-F]{6}$`

**Secondary Color:**
- Gleiche Mechanik wie Primary Color

**Aktionen:**
- shadcn Button "Speichern"
- Loading State waehrend API-Call
- Sonner Toast fuer Erfolg/Fehler

### Komponenten

Ausschliesslich shadcn/ui:
- `Form`, `FormField`, `FormItem`, `FormLabel`, `FormMessage`
- `FileUpload` (neu zu installieren via `npx shadcn@latest add file-upload`)
- `Input`
- `Button`
- `Card`, `CardHeader`, `CardContent`
- `Separator`
- Sonner Toast

### Zod-Schema

Erweitert bestehendes `src/lib/schemas/tenant-config.ts` um `logoStorageKey` und `faviconStorageKey` (optional strings). Orval muss nach OpenAPI-Spec-Aenderungen neu generiert werden.

### API-Calls

Nutzt bestehende Orval-Hooks (nach Regenerierung):
- `useGetTenantBranding()` — Branding laden (inkl. aktuelle storageKeys)
- `useUpdateTenantBranding()` — Branding speichern (mit storageKeys + Farben)
- File Upload: direkter Fetch an TenantFile-Endpunkt (nicht via Orval)

## 4. Tests

### Backend

**Unit Tests (TDD):**
- `TenantBrandingSyncListenerTest` — Event empfangen, Realm-Attributes korrekt gemappt, Keycloak aufgerufen
- `TenantBrandingSyncListenerTest` — Fehlerfall: Keycloak nicht erreichbar → Logged, kein Crash
- `TenantBrandingSyncListenerTest` — Validierung: ungueltige Hex-Werte/URLs werden nicht an Keycloak weitergereicht
- Backend-Validierung: Content-Type + Groesse bei File-Upload

**Integration Test:**
- Branding-Update ueber API → Event publiziert → Keycloak Realm-Attributes verifizieren (Testcontainers)

**Provisioning:**
- Bestehenden `TenantProvisioningIntegrationTest` erweitern: Theme + Default-Attributes pruefen

### Frontend

**Vitest:**
- Branding-Formular: Rendern, Validierung, Submit, Error-Handling
- File Upload: Drop-Event, Vorschau, Loeschen
- Upload-Flow: File-Upload → storageKey → Branding-Update Sequenz

### Keycloak Theme

- Manueller Test: DevDataSeeder um Branding-Testdaten erweitern (Logo + Farben fuer Acme)
- Manueller Test: Tenant Branding setzen → `mail.localhost` (Mailpit) pruefen
- Manueller Test: Login-Seite auf `acme.localhost` pruefen

## Nicht in Scope

- Dynamisches Theming der Platform-UI zur Runtime (Whitelabel)
- Weitere Keycloak-Emails (Email Verification, Login Notification etc.)
- Custom Registrierungsseite (Keycloak Standard reicht)
- Advanced Branding Mode (Font Family, Border Radius, Sidebar Style, Custom CSS)
- Eigener Invite-Token-Flow (docs/05 beschreibt das, aber wir bleiben bei Keycloak executeActionsEmail)
- Favicon in Keycloak Theme (nicht dynamisch moeglich, nur im Frontend-UI verwaltbar)
