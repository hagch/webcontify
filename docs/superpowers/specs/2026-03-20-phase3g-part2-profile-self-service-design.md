# Phase 3g Part 2: User Self-Service Profil — Design Spec

## Ueberblick

Profil-Seite fuer alle User (Platform Admins + Tenant User): Profilbild-Upload, Anzeigename aendern, Passwort-Reset, Sprach- und Theme-Auswahl mit localStorage + Keycloak Sync.

---

## Profil-Seite

### Route + Navigation

- **Route:** `/profile` — eigenstaendige Top-Level Route, zugaenglich fuer alle authentifizierten User
- **Navigation:** Neuer Menuepunkt "Profil" im NavUser-Dropdown (zwischen "Passwort aendern" und "Sign out")
- **Layout:** Einfache Seite mit Sections: Profilbild, Name, Passwort, Praeferenzen

### Profil-Seite Sections

**1. Profilbild:**
- Aktuelles Bild anzeigen (oder Initialen-Avatar als Fallback)
- Upload-Button → Bild-Upload (max 2MB, JPEG/PNG)
- Bild wird in S3 gespeichert
- Loesch-Button um zum Initialen-Avatar zurueckzukehren

**2. Anzeigename:**
- Vorname + Nachname editierbar
- Wird in Keycloak aktualisiert (ueber AuthService → KeycloakAdminService.updateUser)

**3. Passwort:**
- "Passwort aendern" Button → oeffnet Keycloak Account Console (bestehender Mechanismus via `getAccountUrl`)

**4. Praeferenzen:**
- Sprache: Dropdown (Deutsch / English)
- Theme: Auswahl (Light / Dark / System)
- Aenderungen sofort in localStorage gespeichert + async an Backend gesynct

---

## Profilbild-Upload

### Storage

| User-Typ | Bucket | Pfad |
|---|---|---|
| Tenant User | `tenant-{slug}` | `profiles/{userId}.{ext}` |
| Platform Admin | `platform` | `profiles/{userId}.{ext}` |

Der `platform` Bucket wird beim **App-Start** in einem `ApplicationRunner` erstellt (nicht lazy beim ersten Upload — vermeidet Race Condition unter Last). Analog zum Tenant-Bucket Erstellung Pattern, aber einmalig beim Backend-Start.

### API Endpoints

| Method | Path | Beschreibung |
|---|---|---|
| POST | `/api/v1/auth/profile/avatar` | Profilbild hochladen (Multipart) |
| DELETE | `/api/v1/auth/profile/avatar` | Profilbild loeschen |
| GET | `/api/v1/auth/profile/avatar` | 302 Redirect zur Pre-signed URL (REST-konform) |

Alle unter `/api/v1/auth/` — funktioniert fuer Platform + Tenant User.

### Upload-Flow

1. Frontend sendet `multipart/form-data` an `POST /api/v1/auth/profile/avatar`
2. Backend validiert:
   - Max 2MB Dateigroe¨sse
   - Content-Type Header: `image/jpeg` oder `image/png`
   - **Magic Byte Validation:** Ersten Bytes gegen JPEG (`FF D8 FF`) und PNG (`89 50 4E 47`) Signaturen pruefen (verhindert manipulierte Content-Type Header, OWASP A03)
3. Backend speichert als **Private Asset** in S3: `files/profiles/{userId}.jpg`
   - NICHT `uploadPublicAsset` (wuerde unter `config/` mit oeffentlicher Bucket-Policy landen)
   - Stattdessen `uploadPrivateAsset` — Zugriff nur ueber Pre-signed URL
   - Avatar-Key wird als Keycloak User Attribute `avatar_key` gespeichert (fuer spaeteres Loeschen)
4. Backend gibt Pre-signed URL zurueck
5. Frontend zeigt neues Bild

### Avatar-Anzeige

Der NavUser-Dropdown und die User-Tabellen zeigen:
1. Profilbild aus S3 (wenn vorhanden)
2. Fallback: Initialen-Avatar (wie bisher)

Profilbild-Verfuegbarkeit wird ueber `hasAvatar: boolean` im `getMyProfile` Response signalisiert. Das Frontend ruft bei `hasAvatar=true` den `GET /profile/avatar` Endpoint auf (302 Redirect zur Pre-signed URL). Die URL wird clientseitig gecacht bis sie ablaeuft.

---

## Praeferenzen (Theme + Sprache)

### Speicher-Strategie

**localStorage als primaere Quelle** (sofort, kein Flicker):
- `theme` → via `next-themes` (existiert schon)
- `locale` → Cookie/localStorage fuer `next-intl`

**Backend Sync** (cross-device):
- Beim Aendern: `PUT /api/v1/auth/preferences` → Keycloak User Attributes (`theme`, `locale`)
- Beim Login: `GET /api/v1/auth/preferences` → localStorage ueberschreiben

### API Endpoints

| Method | Path | Beschreibung |
|---|---|---|
| GET | `/api/v1/auth/preferences` | User-Praeferenzen laden (theme, locale) |
| PUT | `/api/v1/auth/preferences` | User-Praeferenzen speichern |

### Request/Response

```yaml
UserPreferencesResponse:
  type: object
  properties:
    theme: { type: string, enum: [light, dark, system] }
    locale: { type: string, enum: [de, en] }

UpdateUserPreferencesRequest:
  type: object
  properties:
    theme: { type: string, enum: [light, dark, system] }
    locale: { type: string, enum: [de, en] }
```

### Keycloak Attributes

Gespeichert als User Attributes:
- `preferences_theme` → "light" | "dark" | "system"
- `preferences_locale` → "de" | "en"

---

## Backend Erweiterungen

### AuthService erweitern

```
uploadAvatar(file)
  → Bestimme Bucket (Tenant-Bucket oder "platform")
  → Validiere: max 2MB, Content-Type + Magic Bytes (JPEG/PNG)
  → Loesche altes Avatar falls vorhanden (avatar_key aus Keycloak Attribute)
  → Upload: fileStorageService.uploadPrivateAsset(bucket, "profiles/{userId}.{ext}", stream, contentType)
  → Speichere Storage-Key als Keycloak User Attribute "avatar_key"
  → Return Pre-signed URL

deleteAvatar()
  → Lese avatar_key aus Keycloak User Attribute
  → fileStorageService.deleteAsset(bucket, avatarKey)
  → Entferne avatar_key Attribut

getAvatarUrl()
  → Lese avatar_key aus Keycloak User Attribute
  → Wenn nicht gesetzt: 404
  → Return 302 Redirect zur Pre-signed URL (15 Min TTL)

getPreferences()
  → keycloakAdminService.getUserById(realm, userId)
  → Lese Attributes preferences_theme, preferences_locale
  → Return UserPreferencesResponse

updatePreferences(theme, locale)
  → keycloakAdminService.updateUserAttributes(realm, userId, attributes)
```

### AuthController erweitern

Neue Endpoints unter `/api/v1/auth/`:
- `POST /profile/avatar` — Multipart Upload
- `DELETE /profile/avatar`
- `GET /profile/avatar-url`
- `GET /preferences`
- `PUT /preferences`

### KeycloakAdminService erweitern

Neue Methode:
```
updateUserAttributes(realmName, userId, Map<String, List<String>> attributes)
  → GET User → merge Attributes → PUT User
  → Known Limitation: Read-Modify-Write ohne Optimistic Locking.
    Bei gleichzeitigen Requests koennen Attribute ueberschrieben werden.
    Akzeptabel fuer UI-Praeferenzen (nicht sicherheitskritisch).
```

### MyProfileResponse erweitern

```yaml
MyProfileResponse:
  properties:
    id: UUID
    email: string
    firstName: string
    lastName: string
    hasAvatar: boolean
    preferences:
      theme: string
      locale: string
```

---

## Frontend

### Neue Dateien

```
src/app/(workspace)/profile/page.tsx     — Profil-Seite (Workspace)
src/app/(platform)/profile/page.tsx      — Profil-Seite (Platform)
src/features/profile/
  ├── components/
  │   ├── avatar-upload.tsx
  │   ├── profile-form.tsx
  │   ├── preferences-form.tsx
  │   └── __tests__/
  ├── hooks/
  │   └── use-preferences-sync.ts
  └── schemas/
      └── profile.ts
```

### NavUser erweitern

- Profilbild statt Initialen-Avatar (wenn vorhanden)
- Neuer Menuepunkt "Profil" → navigiert zu `/profile`

### Preferences Sync Hook

```typescript
function usePreferencesSync() {
  // Beim Mount: GET /auth/preferences → localStorage ueberschreiben
  // Beim Aendern: localStorage sofort setzen + PUT /auth/preferences (fire-and-forget, stiller Retry bei Fehler)
}
```

---

## Testing

- Backend Unit Tests: AuthService (avatar upload, preferences CRUD)
- Frontend Vitest: Profile Form, Avatar Upload, Preferences Sync
- E2E: Profil-Seite oeffnen, Name aendern, Theme wechseln

---

## Abgrenzung

- Profilbild-Cropping (einfacher Upload reicht)
- Weitere Sprachen ausser DE/EN
- Email-Aenderung (komplex wegen Keycloak-Verifikation)
