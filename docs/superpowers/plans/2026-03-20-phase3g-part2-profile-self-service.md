# Phase 3g Part 2: User Self-Service Profil — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Profile page for all users with avatar upload, display name editing, password reset link, and preferences (theme + locale) with localStorage + Keycloak sync.

**Architecture:** Avatar stored as private S3 asset (pre-signed URL access). Preferences stored in Keycloak User Attributes with localStorage as primary cache. Platform bucket created at app start.

**Tech Stack:** Spring Boot, S3/MinIO, Keycloak Admin Client, Next.js, shadcn/ui, next-intl, next-themes

**Design Spec:** `docs/superpowers/specs/2026-03-20-phase3g-part2-profile-self-service-design.md`

**UI/UX:** Frontend components MUST use only shadcn/ui. UI/UX review via ui-ux-pro-max skill after implementation.

---

## File Structure

### Backend — New/Modified Files
- `openapi/auth-api.yaml` — new endpoints (avatar, preferences)
- `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java` — avatar + preferences
- `apps/backend/src/main/java/io/webcontify/identity/api/AuthController.java` — new endpoints
- `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java` — updateUserAttributes
- `apps/backend/src/main/java/io/webcontify/shared/filestorage/PlatformBucketInitializer.java` — ApplicationRunner
- `apps/backend/src/test/java/io/webcontify/identity/service/AuthServiceTest.java` — unit tests

### Frontend — New Files
- `apps/platform/src/app/(workspace)/profile/page.tsx`
- `apps/platform/src/app/(platform)/profile/page.tsx`
- `apps/platform/src/features/profile/components/avatar-upload.tsx`
- `apps/platform/src/features/profile/components/profile-form.tsx`
- `apps/platform/src/features/profile/components/preferences-form.tsx`
- `apps/platform/src/features/profile/hooks/use-preferences-sync.ts`
- `apps/platform/src/features/profile/schemas/profile.ts`

### Frontend — Modified Files
- `apps/platform/src/components/layout/nav-user.tsx` — profile link + avatar image

---

## Task 1: OpenAPI Spec + Backend Platform Bucket Initializer

**Files:**
- Modify: `openapi/auth-api.yaml`
- Create: `apps/backend/src/main/java/io/webcontify/shared/filestorage/PlatformBucketInitializer.java`

- [ ] **Step 1: Extend auth-api.yaml**

Add endpoints:
- `POST /api/v1/auth/profile/avatar` — multipart upload, 200 with avatarUrl
- `DELETE /api/v1/auth/profile/avatar` — 204
- `GET /api/v1/auth/profile/avatar` — 302 redirect to pre-signed URL (or 404 if no avatar)
- `GET /api/v1/auth/preferences` — 200 UserPreferencesResponse
- `PUT /api/v1/auth/preferences` — 200 UserPreferencesResponse
- `PUT /api/v1/auth/profile` — 200 MyProfileResponse (update firstName/lastName)

Add schemas: `UserPreferencesResponse`, `UpdateUserPreferencesRequest`, `UpdateProfileRequest`, `AvatarUploadResponse`

Update `MyProfileResponse` to include `hasAvatar: boolean` and nested `preferences`.

- [ ] **Step 2: Create PlatformBucketInitializer**

```java
@Component
public class PlatformBucketInitializer implements ApplicationRunner {
    private final FileStorageService fileStorageService;

    @Override
    public void run(ApplicationArguments args) {
        fileStorageService.createBucket("platform");
    }
}
```

- [ ] **Step 3: Regenerate + Commit**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
git add openapi/auth-api.yaml apps/backend/
git commit -m "feat: extend auth API spec with avatar and preferences endpoints"
```

---

## Task 2: Backend — KeycloakAdminService + AuthService (TDD)

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/AuthServiceTest.java`

- [ ] **Step 1: Add updateUserAttributes to KeycloakAdminService**

```java
public void updateUserAttributes(String realmName, UUID userId, Map<String, List<String>> attributes) {
    var userResource = keycloak.realm(realmName).users().get(userId.toString());
    var user = userResource.toRepresentation();
    var existing = user.getAttributes() != null ? new HashMap<>(user.getAttributes()) : new HashMap<String, List<String>>();
    existing.putAll(attributes);
    user.setAttributes(existing);
    userResource.update(user);
}
```

- [ ] **Step 2: Write AuthService tests first (TDD)**

Tests for:
- `uploadAvatar` — validates file size, content type, magic bytes
- `deleteAvatar` — reads avatar_key, deletes from S3, removes attribute
- `getAvatarUrl` — returns pre-signed URL or throws 404
- `getPreferences` — reads from Keycloak attributes
- `updatePreferences` — writes to Keycloak attributes
- `updateProfile` — updates firstName/lastName in Keycloak

- [ ] **Step 3: Implement AuthService methods**

Avatar: use `uploadPrivateAsset` (files/ prefix), store key as Keycloak `avatar_key` attribute.
Magic byte validation: check first 4 bytes of InputStream (JPEG: `FF D8 FF`, PNG: `89 50 4E 47`).
Bucket determination: Platform Admin → "platform", Tenant User → "tenant-{slug}" from TenantContext.

- [ ] **Step 4: Update AuthController**

Implement all new endpoints from the generated AuthApi interface.
Avatar GET: return `ResponseEntity.status(302).location(presignedUri).build()`

- [ ] **Step 5: Run tests**

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

- [ ] **Step 6: Commit**

```bash
git add apps/backend/
git commit -m "feat: add avatar upload, preferences, and profile update to AuthService"
```

---

## Task 3: Frontend — Orval + Profile Page + Components

**Files:**
- All new frontend files listed in File Structure

- [ ] **Step 1: Regenerate Orval**

```bash
npx nx generate platform
```

- [ ] **Step 2: Create Zod schema**

```typescript
// features/profile/schemas/profile.ts
export const updateProfileSchema = z.object({
  firstName: z.string().min(1).max(255),
  lastName: z.string().min(1).max(255),
});
```

- [ ] **Step 3: Create avatar-upload.tsx**

shadcn Card with:
- Current avatar image (from `GET /auth/profile/avatar` if hasAvatar) or Initialen-Fallback
- Upload button (hidden file input, accept="image/jpeg,image/png")
- Delete button (if avatar exists)
- Loading state during upload
- Max 2MB validation client-side

- [ ] **Step 4: Create profile-form.tsx**

shadcn Card with React Hook Form:
- First Name + Last Name inputs
- Save button
- Password section: "Change Password" button → opens Keycloak Account Console

- [ ] **Step 5: Create preferences-form.tsx**

shadcn Card:
- Language: Select (Deutsch / English) → sets NEXT_LOCALE cookie + syncs to backend
- Theme: RadioGroup or Select (Light / Dark / System) → uses next-themes setTheme + syncs

- [ ] **Step 6: Create use-preferences-sync.ts**

```typescript
export function usePreferencesSync() {
  // On mount: GET /auth/preferences → sync to localStorage/cookie
  // On change: update local immediately + PUT /auth/preferences (fire-and-forget with retry)
}
```

- [ ] **Step 7: Create profile pages**

Both `(workspace)/profile/page.tsx` and `(platform)/profile/page.tsx`:
```tsx
"use client";
import { AppHeader } from "@/components/layout/app-header";
import { AvatarUpload } from "@/features/profile/components/avatar-upload";
import { ProfileForm } from "@/features/profile/components/profile-form";
import { PreferencesForm } from "@/features/profile/components/preferences-form";

export default function ProfilePage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: t("nav.profile") }]} />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6 max-w-2xl">
        <AvatarUpload />
        <ProfileForm />
        <PreferencesForm />
      </div>
    </>
  );
}
```

- [ ] **Step 8: Update nav-user.tsx**

- Show avatar image instead of initials (if hasAvatar from getMyProfile)
- Add "Profile" menu item → navigates to `/profile`

- [ ] **Step 9: Add i18n keys for profile**

Add `profile.*` namespace to `messages/en.json` and `messages/de.json`.

- [ ] **Step 10: Verify**

```bash
npx nx build platform && npx nx test platform
```

- [ ] **Step 11: UI/UX Review**

Use ui-ux-pro-max skill to review the profile page components for design quality, accessibility, and UX patterns.

- [ ] **Step 12: Commit**

```bash
git add apps/platform/
git commit -m "feat: add profile page with avatar upload, name editing, and preferences"
```

---

## Task 4: E2E Tests + Build Verification + PROGRESS.md

- [ ] **Step 1: Add E2E profile test**

```
apps/e2e/tests/profile/profile-self-service.spec.ts
- should navigate to profile page from nav menu
- should update display name
- should change theme preference
```

- [ ] **Step 2: Run full test suite**

```bash
cd apps/e2e && npx playwright test --reporter=list
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
npx nx test platform && npx nx build platform
```

- [ ] **Step 3: Update PROGRESS.md**

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: update PROGRESS.md — Phase 3g complete"
```
