# Webcontify — Progress

## Phase 0: Projektgeruest

| Task | Status | Beschreibung |
|---|---|---|
| T0.1 | Done | Nx Workspace initialisiert (pnpm, nx.json, pnpm-workspace.yaml) |
| T0.2 | Done | Spring Boot 3.5.11 + Spring Modulith 1.4.8 Backend (Gradle, Java 21) |
| T0.3 | Done | Next.js 16 Platform App (App Router, Tailwind v4, Geist Fonts, next-intl) |
| T0.4 | Done | @webcontify/ui Shared Library (cn Helper, Storybook 8) |
| T0.5 | Done | Docker Compose Infrastruktur (PostgreSQL 17, Keycloak 26, OpenFGA, MinIO, Mailpit, Traefik v3) |
| T0.6 | Done | Flyway initiale Migration (Extensions, RLS App-Variable) |
| T0.7 | Done | OpenAPI Code Generation (Backend: openapi-generator, Frontend: Orval + React Query) |
| T0.8 | Done | PROGRESS.md + Dev-Workflow Verifizierung |

## Phase 1a: Tenant Domain — Vertikaler Slice

| Task | Status | Beschreibung |
|---|---|---|
| T1.1 | Done | Shared Kernel: TenantInformation, AuditInfo, SystemUser, UUIDv7 Generator |
| T1.2 | Done | Shared Kernel: Exception Hierarchy + RFC 7807 GlobalExceptionHandler |
| T1.3 | Done | Shared Kernel: AuditableBaseEntity, TenantAwareBaseEntity, AuditListener |
| T1.4 | Done | Shared Kernel: Structured Logging mit Correlation IDs |
| T1.5 | Done | Shared Kernel: RLS DataSource Proxy + TenantContext |
| T1.6 | Done | Flyway Migration: Tenant Table |
| T1.7 | Done | OpenAPI Spec: tenant-api.yaml + Multi-Spec Build Config |
| T1.8 | Done | Tenant Domain Model, JPA Entity, Repository (Testcontainers) |
| T1.9 | Done | Tenant MapStruct Mapper |
| T1.10 | Done | TenantService mit CRUD Operationen |
| T1.11 | Done | TenantAdminController (6 Platform Admin Endpoints) |
| T1.12 | Done | Spring Modulith Structure Verification Test |
| T1.13 | Done | Frontend: Orval Generation + Layout Components (Sidebar, Header, AppShell) |
| T1.14 | Done | Frontend: Tenant List Page mit Create Dialog |
| T1.15 | Done | Frontend: Tenant Detail Page mit Edit + Status Management |
| T1.16 | Done | PROGRESS.md Update |

## Phase 1b: Shared Services + Tenant Config — Vertikaler Slice

| Task | Status | Beschreibung |
|---|---|---|
| T1b.1 | Done | Shared Kernel: AES-256-GCM EncryptionService |
| T1b.2 | Done | Shared Kernel: S3 FileStorageService + S3Configuration (MinIO-kompatibel) |
| T1b.3 | Done | TenantConfig erweitert (BrandingConfig, EmailConfig, DomainConfig mit allen Feldern) |
| T1b.4 | Done | OpenAPI Spec: 5 TenantConfig Endpoints + 3 TenantFiles Endpoints |
| T1b.5 | Done | TenantConfigService + TenantConfigController + TenantConfigMapper |
| T1b.6 | Done | Shared Kernel: SMTP EmailService + Mailpit Dev-Support |
| T1b.7 | Done | TenantFileService + TenantFileController (Upload, Pre-signed URL, Delete) |
| T1b.8 | Done | Integration Tests: TenantConfigController (6 Tests) |
| T1b.9 | Done | Fix: TenantMapperTest (Status ignored by Design), Test application.yml |
| T1b.10 | Done | Frontend: Tenant Config UI (Branding, Email, Domain Tabs) |
| T1b.11 | Done | E2E Tests: Tenant Config Page (10 Playwright Tests) |

## Phase 1c: Config-Endpoints Admin-Scope Migration

| Task | Status | Beschreibung |
|---|---|---|
| T1c.1 | Done | OpenAPI Spec: Config-Endpoints unter /api/v1/admin/tenants/{tenantId}/config |
| T1c.2 | Done | Backend: TenantConfigController mit tenantId statt Placeholder |
| T1c.3 | Done | Backend: Integration Tests auf neue Admin-URLs angepasst |
| T1c.4 | Done | Frontend: Orval regenerieren + Facade/Page mit tenantId |
| T1c.5 | Done | E2E Tests: Tenant ueber UI erstellen, Config auf erstellten Tenant testen |

## Phase 2a: TenantProvisioningService

| Task | Status | Beschreibung |
|---|---|---|
| A.1 | Done | TenantProvisioningService: S3 Bucket bei Tenant-Erstellung + TDD |

## Phase 2b: Auth Integration (Keycloak JWT)

| Task | Status | Beschreibung |
|---|---|---|
| B.1 | Done | Keycloak Dev Realm Setup (platform-realm.json, Docker Mount) |
| B.2 | Done | Spring Security + JWT Resource Server + JwtToTenantAuthenticationConverter |
| B.3 | Done | TenantResolutionFilter (Subdomain Validation gegen JWT, TenantSlugResolver Interface) |
| B.4 | Done | AuditListener User-Aufloesung aus SecurityContext |
| B.5 | Done | Integration Tests anpassen (TestSecurityConfiguration mit X-Test-Tenant-Id Header) |
| B.6 | Done | Frontend: Keycloak Auth mit NextAuth.js v5 (PKCE, Token Refresh, Route Protection) |
| B.7 | Offen | Security Review + E2E Tests Auth Flow |

## Phase 2c: Tenant Self-Service Branding

| Task | Status | Beschreibung |
|---|---|---|
| C.1 | Done | OpenAPI Spec: Self-Service Config Endpoints (GET config, PUT branding) |
| C.2 | Done | TenantSelfServiceController + TDD (5 Integration Tests) |
| C.3 | Done | Frontend: Tenant Settings Branding Page |
| C.4 | Done | E2E Tests Self-Service Branding + Auth Flow (Playwright) |

## Phase 2d: UI/UX Redesign (alt — ersetzt durch 2e)

| Task | Status | Beschreibung |
|---|---|---|
| D.1–D.11 | Done | Alte UI/UX Redesign Tasks (alle abgeschlossen, Code wurde in Phase 2e ersetzt) |
| D.12 | Entfaellt | E2E Tests — wird in Phase 3 mit echtem Auth-Flow geschrieben |

## Phase 2e: Frontend Redesign (Clean Rebuild)

| Task | Status | Beschreibung |
|---|---|---|
| E.1 | Done | Clean Slate: packages/ui/ + alte Frontend-Dateien geloescht |
| E.2 | Done | shadcn/ui v4 Init (Indigo+Emerald+Stone Theme, globals.css) |
| E.3 | Done | Root Layout mit Providers (QueryClient, Session, AuthTokenSync, Theme) |
| E.4 | Done | Core shadcn Komponenten installiert (21 Komponenten) |
| E.5 | Done | Orval API Client regeneriert (standalone mutator) |
| E.6 | Done | useAuth Hook mit Platform Admin Detection (TDD, 5 Tests) |
| E.7 | Done | Sidebar Navigation Config mit rollenbasierter Sichtbarkeit (TDD, 6 Tests) |
| E.8 | Done | App Shell: AppSidebar, NavUser, AppHeader mit Breadcrumbs |
| E.9 | Done | (auth) Signin Page, (platform) + (workspace) Layouts |
| E.10 | Done | Tenant List Page (Table + Skeleton + Empty State) |
| E.11 | Done | Tenant Detail Page mit Skeleton Loading |
| E.12 | Done | Create Tenant Dialog (React Hook Form + Zod) |
| E.13 | Done | Workspace Placeholder Pages (Dashboard, Apps, Users, Roles, Settings) |
| E.14 | Done | Platform Admin Placeholder Pages (Admins, System Settings) |
| E.15 | Done | Route Protection erweitert fuer alle neuen Routes (TDD, 7 Tests) |
| E.16 | Done | asChild → render Prop Migration (React 19 / shadcn v4 / Base UI) |
| E.17 | Done | Build Verification (Build + Tests + Lint gruen) |

## Phase 3a: Identity Infrastructure (abgeschlossen 2026-03-14)

| Task | Status | Beschreibung |
|---|---|---|
| 3a.1 | Done | Gradle: OpenFGA SDK 0.7.2, Keycloak Admin Client 26, Caffeine Cache, AOP |
| 3a.2 | Done | Docker: OpenFGA v1.8.1 gepinnt, Traefik Wildcard fuer Tenant-Subdomains |
| 3a.3 | Done | Flyway: openfga_store_id, keycloak_realm_name, provisioning_status, PROVISIONING Status |
| 3a.4 | Done | OpenAPI Spec: CreateTenantRequest (adminEmail/First/LastName), ProvisioningStatus, retry endpoint |
| 3a.5 | Done | SlugGenerator mit Umlaut-Handling und Eindeutigkeitspruefung (TDD) |
| 3a.6 | Done | OpenFGA Authorization Model (model.json): user, tenant, user_management |
| 3a.7 | Done | OpenFGA Configuration (ClientConfiguration, api-url property) |
| 3a.8 | Done | OpenFGA Request/Response Records (CreateStore, Check, Write, Delete, ListObjects) |
| 3a.9 | Done | OpenFgaService: Store erstellen + Model deployen, Check, Write, Delete, ListObjects (TDD) |
| 3a.10 | Done | OpenFGA Integration Test mit Testcontainers (3 Tests: Store, Check, Deny) |
| 3a.11 | Done | Keycloak Admin Client Configuration (Service Account, client_credentials) |
| 3a.12 | Done | KeycloakAdminService Request/Response Records |
| 3a.13 | Done | KeycloakAdminService: Realm erstellen, User erstellen, SMTP, Branding, Invite Email (TDD) |
| 3a.14 | Done | Caffeine Cache fuer TenantInfrastructureLookupService (5min TTL) |
| 3a.15 | Done | TenantInfrastructureLookupService: openfga_store_id + keycloak_realm_name Lookup (TDD) |
| 3a.16 | Done | PermissionService: check, grant, revoke, listObjects via OpenFGA (TDD) |
| 3a.17 | Done | @RequiresPermission Annotation + PermissionCheckAspect (SpEL, Platform Admin Bypass, TDD) |
| 3a.18 | Done | ProvisioningStatus Model: ProvisioningStep, ProvisioningStepType, ProvisioningStepStatus |
| 3a.19 | Done | TenantService.create() migriert: Slug auto-generiert, async Event, isNameAvailable() |
| 3a.20 | Done | TenantProvisioningListener: @ApplicationModuleListener, S3→Keycloak→OpenFGA→Admin (TDD) |
| 3a.21 | Done | Retry Provisioning Endpoint: POST /tenants/{id}/retry-provisioning (TDD) |
| 3a.22 | Done | Provisioning Integration Test: full flow mit gemockten externen Services + Awaitility |
| 3a.23 | Done | Frontend: Dynamic NextAuth Realm-Aufloesung (platform vs. tenant subdomain) |
| 3a.24 | Done | Frontend: Create Tenant Dialog mit Admin-Feldern (kein Slug, Name-Check) |
| 3a.25 | Done | Frontend: ProvisioningStatus Komponente mit Polling, Retry, a11y (TDD) |

## Phase 3b — User Management + Invite (2026-03-15)

**Fertig:**
- Flyway Migration: tenant_user, tenant_role + RLS
- OpenAPI: identity-api.yaml (6 Endpoints)
- Backend: UserService, UserController, UserMapper, UserActivationFilter
- TenantProvisioningListener erweitert (tenant_user + tenant_role bei Provisioning)
- KeycloakAdminService: enableUser/disableUser
- Frontend: User List Page, Detail Page, Invite Dialog, Status Badge, Actions Menu
- Tests: UserServiceTest (Unit), UserControllerIntegrationTest, Frontend Vitest

## Phase 3c — Keycloak Branding + Settings UI (2026-03-16)

| Task | Status | Beschreibung |
|---|---|---|
| 3c.1 | Done | Keycloak Custom Theme (webcontify) — Login + Email FreeMarker Templates |
| 3c.2 | Done | Docker-Mount + Platform Realm Theme-Konfiguration |
| 3c.3 | Done | TenantBrandingUpdatedEvent + TenantConfigService Event-Publishing (TDD) |
| 3c.4 | Done | TenantBrandingSyncListener — Realm-Attributes Sync zu Keycloak (mit Merge, TDD) |
| 3c.5 | Done | CreateRealmRequest erweitert — Theme + Default-Attributes bei Provisioning (TDD) |
| 3c.6 | Done | File Upload Backend-Validierung (Content-Type, Size) + TenantContext Security Fix (TDD) |
| 3c.7 | Done | OpenAPI Spec: UpdateBrandingConfigRequest um Storage Keys erweitert + Mapper Fix |
| 3c.8 | Done | Frontend: Branding Settings Seite (Logo Upload, Color Picker, React Hook Form) |
| 3c.9 | Done | Frontend Tests (Vitest): ColorPickerField + BrandingForm (7 Tests) |
| 3c.10 | Done | Backend Integration Test: Branding Sync Flow (5 Tests, Testcontainers) |
| 3c.11 | Done | Modulith-Struktur Fix (shared -> identity::identity-security) |
| 3c.12 | Done | Full Test Suite gruen (214 Backend, 55 Frontend), Frontend Build OK |

## Phase 3d — Rollen-Verwaltung (2026-03-19)

| Task | Status | Beschreibung |
|---|---|---|
| 3d.1 | Done | OpenFGA Model: tenant_config Type hinzugefuegt |
| 3d.2 | Done | OpenAPI Spec: 8 Role Endpoints + 11 Schemas + InviteUserRequest erweitert |
| 3d.3 | Done | OpenFgaService: readTuples + checkForSubject (TDD) |
| 3d.4 | Done | Domain Model: TenantRole, RoleAssignee, RolePermissionPreview, RoleMapper, Repository Queries |
| 3d.5 | Done | RoleService: CRUD + OpenFGA Orchestrierung + Schutzlogik (TDD, 15 Unit Tests) |
| 3d.6 | Done | RoleController + UserRoleController + Integration Tests (5 Tests) |
| 3d.7 | Done | UserService: invite mit copyRolesFromUserId, guardLastAdmin OpenFGA-basiert, delete Cleanup (19 Tests) |
| 3d.8 | Done | TenantProvisioningListener: 4 System-Rollen + 20 OpenFGA-Tupel Batch |
| 3d.9 | Done | Full Backend Test Suite gruen (252 Tests) |
| 3d.10 | Done | Frontend: Orval Regeneration + Zod Schemas |
| 3d.11 | Done | Frontend: Rollen-Liste (Page, Table, Create Dialog, Actions Hook) |
| 3d.12 | Done | Frontend: Rollen-Detail (Tabs, Assignees, Permissions Preview, Edit Form) |
| 3d.13 | Done | Frontend: Invite mit Rollen-Klon + User-Tabelle "Mit gleichen Rollen einladen" Action |
| 3d.14 | Done | Frontend Tests: Role Table (5) + Create Dialog (4) = 78 Frontend Tests gesamt |
| 3d.15 | Done | Build Verification: 252 Backend + 78 Frontend Tests gruen, Frontend Build OK |

## Phase 3e — Platform Admin Management (2026-03-19)

| Task | Status | Beschreibung |
|---|---|---|
| 3e.1 | Done | OpenFGA Cleanup: type platform entfernt (ungenutzt) |
| 3e.2 | Done | OpenAPI Specs: platform-admin-api.yaml + auth-api.yaml + Code Generation |
| 3e.3 | Done | KeycloakAdminService: listUsersWithRole, assignRealmRole, getUserById, addRequiredAction |
| 3e.4 | Done | SecurityConfiguration: /api/v1/auth/** als authenticated |
| 3e.5 | Done | PlatformAdminService + AuthService (TDD, 13 Unit Tests) |
| 3e.6 | Done | PlatformAdminController + AuthController + Integration Tests (7 Tests) |
| 3e.7 | Done | Full Backend Test Suite gruen + TenantContextTest SecurityContext Fix |
| 3e.8 | Done | Frontend: Orval Config + Regeneration + Zod Schema |
| 3e.9 | Done | Frontend: Admin-Liste (Page, Table, Invite Dialog, Actions Menu) |
| 3e.10 | Done | Frontend: "Passwort aendern" Link im NavUser Dropdown (alle User) |
| 3e.11 | Done | Frontend Tests: Admin Table (5 Tests) = 83 Frontend Tests gesamt |
| 3e.12 | Done | Build Verification: Backend + 83 Frontend Tests gruen, Frontend Build OK |

## Phase 3f — E2E Tests + Security Review (2026-03-20)

| Task | Status | Beschreibung |
|---|---|---|
| 3f.1 | Done | E2E Projekt Scaffold (apps/e2e/, Playwright, Nx Config) |
| 3f.2 | Done | Helpers migriert + refactored (English, ApiClient mit Cleanup-Stack) |
| 3f.3 | Done | data-testid Attribute auf allen UI-Komponenten |
| 3f.4 | Done | Auth Flow Tests (Platform Login, Tenant Login, Token Expiry) |
| 3f.5 | Done | Tenant Tests (CRUD, Config) |
| 3f.6 | Done | User Tests (Invite, Invite mit Rollen-Klon) |
| 3f.7 | Done | Role Tests (CRUD, Assignment, Last-Admin-Guard) |
| 3f.8 | Done | Platform Admin Tests (Invite, Deactivate, Delete, Last-Admin) |
| 3f.9 | Done | Security Tests (Cross-Tenant Isolation, Permissions, Unauthorized, Input Validation) |
| 3f.10 | Done | Alte E2E Dateien migriert + geloescht, Frontend Build OK |

## Phase 3g — i18n + Invite Fix + Profile Self-Service (2026-03-20)

### Part 1: i18n Audit + Invite Flow Fix

| Task | Status | Beschreibung |
|---|---|---|
| 3g.1 | Done | i18n: Cookie-basiertes Locale-Switching, vollstaendige en.json + de.json |
| 3g.2 | Done | i18n: 18 Komponenten auf useTranslations() migriert |
| 3g.3 | Done | Invite: roleIds Pflichtfeld, tenant#admin entfernt, Privilege Escalation Check |
| 3g.4 | Done | Invite: Role Multi-Select (Checkbox-Gruppe) im Invite-Dialog |
| 3g.5 | Done | E2E Tests angepasst fuer roleIds, Backend + Frontend Tests gruen |

### Part 2: User Self-Service Profil

| Task | Status | Beschreibung |
|---|---|---|
| 3g.6 | Done | Backend: Avatar Upload (S3, Magic Bytes), Preferences (Keycloak Attributes), Profile Update |
| 3g.7 | Done | Frontend: Profil-Seite (Avatar, Name, Passwort, Praeferenzen) + UI/UX Review |
| 3g.8 | Done | Build Verification, PROGRESS.md |

## Phase 3h — Permission-Aware Navigation (2026-03-22)

| Task | Status | Beschreibung |
|---|---|---|
| 3h.1 | Done | OpenFGA Model: tenant_config Typ hinzugefuegt |
| 3h.2 | Done | OpenFgaService: batchCheck() implementiert |
| 3h.3 | Done | OpenAPI Spec: effective-permissions + set-role-permissions Endpoints |
| 3h.4 | Done | AuthService: getEffectivePermissions() mit BatchCheck |
| 3h.5 | Done | RoleService: setPermissions() + Delete Cleanup + BatchCheck Migration |
| 3h.6 | Done | Provisioning: Editor/Viewer Singleton-Tuples entfernt |
| 3h.7 | Done | Frontend: usePermissions Hook + Orval Regeneration |
| 3h.8 | Done | Frontend: Permission-aware Navigation |
| 3h.9 | Done | Frontend: Permission Guards auf Workspace-Seiten |
| 3h.10 | Done | Frontend: Editierbare Permission-Toggles auf Role-Detail |
| 3h.11 | Done | E2E Tests: Permission-Aware Navigation (6 Tests) + 3 Bugfixes |
| 3h.12 | Done | Build Verification: Backend + Frontend Tests gruen, Build OK |

## Phase 3i — Branding Enhancements (2026-03-24)

| Task | Status | Beschreibung |
|---|---|---|
| 3i.1 | Done | OpenAPI: TenantConfigResponse um name/slug, BrandingConfig um severity colors |
| 3i.2 | Done | Backend: BrandingConfig Record + Event + Listener um severity colors erweitert |
| 3i.3 | Done | Backend: TenantConfigWithInfo Wrapper mit name/slug, Controller + Service angepasst |
| 3i.4 | Done | Backend Tests: Alle 4 Testklassen auf neue Record-Signaturen aktualisiert |
| 3i.5 | Done | shadcn Color Picker: Popover mit HSV Farbfeld, Hue Slider, Hex Input |
| 3i.6 | Done | Logo Upload: Nur SVG akzeptiert (image/svg+xml), Hint-Text |
| 3i.7 | Done | Sidebar: Tenant-Logo (SVG via img) + Tenant-Name statt hardcoded "Webcontify" |
| 3i.8 | Done | Advanced Settings: Severity Colors (danger/success/warning) mit Color Picker |
| 3i.9 | Done | BrandingProvider: Severity CSS Variables + tenantName/tenantSlug |
| 3i.10 | Done | Preview: Severity Badges hinzugefuegt |
| 3i.11 | Done | i18n: EN + DE Keys fuer severity colors, SVG hint |

## Phase 3j — UI/UX Improvements (2026-03-25)

| Task | Status | Beschreibung |
|---|---|---|
| 3j.1 | Done | Sidebar Contrast Fix: Light Mode hover/active von Indigo 50 auf Indigo 100 |
| 3j.2 | Done | Keycloak Login CSS: Komplett-Rewrite mit Design-System Tokens (kein PatternFly) |
| 3j.3 | Done | Keycloak Template: Statische + dynamische Tokens, generisches Google Fonts Loading |
| 3j.4 | Done | Keycloak Seiten-Templates: login.ftl, login-reset-password.ftl, error.ftl, info.ftl |
| 3j.5 | Done | Keycloak Invite: Welcome-Password Template + Auto-Submit in info.ftl + i18n (EN+DE) |
| 3j.6 | Done | Backend Invite Redirect: redirectUri + clientId + lifespan in executeActionsEmail |
| 3j.7 | Done | Backend Font-Enum: OpenAPI + ALLOWED_FONTS + mapFontFamilyToCss (5→13 Fonts) |
| 3j.8 | Done | Frontend Font Setup: 12 Fonts via next/font/google, CSS-Variablen, Mapping-Tabellen |
| 3j.9 | Done | BrandingProvider: Font-Switching via --font-sans CSS Variable + Cleanup |
| 3j.10 | Done | FontSelect: Kategorisiertes Dropdown (Modern/Classic/Friendly/Serif) + i18n |
| 3j.11 | Done | Branding Form: Cards→Tabs (Logo, Farben, Typografie) + Font-Preview |
| 3j.12 | Done | AppHeader: Kontextabhaengige Top-Bar mit Union Type API (list/detail Mode) |
| 3j.13 | Done | Page Migration: 15 Seiten auf neue AppHeader API, Actions in Top-Bar |
| 3j.14 | Done | Zod Schema: fontFamily Enum auf 13 Werte erweitert |

## Phase 4a — App CRUD + Lifecycle

| Task | Status | Beschreibung |
|---|---|---|
| 4a.1 | Done | SlugGenerator nach shared.util verschoben (Cross-Module Reuse) |
| 4a.2 | Done | Flyway Migration: app Table mit RLS (NULLIF Pattern) |
| 4a.3 | Done | OpenAPI Spec: app-api.yaml (8 Endpoints), NameAvailabilityResponse nach common-schemas |
| 4a.4 | Done | Domain Model: App, AppStatus, AppEntity (extends TenantAwareBaseEntity) |
| 4a.5 | Done | AppRepository + AppMapper (MapStruct, 6 Query Methods) |
| 4a.6 | Done | AppService mit CRUD + Lifecycle, TenantContext Pattern (TDD, 9 Unit Tests) |
| 4a.7 | Done | AppController (implementiert generiertes AppApi Interface) |
| 4a.8 | Done | Integration Tests (7 Tests: CRUD, Status, Duplicate, Name-Check) |
| 4a.9 | Done | Frontend: Orval Config + Generation + i18n Keys (EN+DE) |
| 4a.10 | Done | Frontend: CreateAppDialog (Zod Schema, RHF, Name-Check) |
| 4a.11 | Done | Frontend: AppTable + AppTableSkeleton (Status Badges, Draft Indicator) |
| 4a.12 | Done | Frontend: App List Page + Sidebar Navigation |
| 4a.13 | Done | Frontend: App Detail Page (Status Management, Delete mit Confirmation) |
| 4a.14 | Done | Frontend: Vitest Tests (5 Tests) |
| 4a.15 | Done | Final Verification: Backend + Frontend Tests gruen, Build OK |

## Phase 4b — App Versioning + Snapshots

| Task | Status | Beschreibung |
|---|---|---|
| 4b.1 | Done | Flyway Migration: app_version + change_record Tabellen mit Indexes + RLS |
| 4b.2 | Done | OpenAPI Spec: 5 Version-Endpoints (publish, list, detail, rollback, suggest) |
| 4b.3 | Done | Shared ChangeRecord Infrastructure (Model, Entity, Repo, Service, TDD 3 Tests) |
| 4b.4 | Done | AppVersion Model, AppSnapshot Record, Entity, Repository, Mapper |
| 4b.5 | Done | AppSnapshotService (S3 Upload/Download), getObject auf FileStorageService (TDD 4 Tests) |
| 4b.6 | Done | AppVersionService (Publish, Rollback, Suggest), TenantSlugResolver.resolveSlugByTenantId, hasDraft Auto-Set (TDD 8 Tests) |
| 4b.7 | Done | AppVersionController (implementiert generiertes AppVersionApi Interface) |
| 4b.8 | Done | Integration Tests (7 Tests: Publish, List, Detail, Duplicate, Rollback, Suggest, hasDraft) |
| 4b.9 | Done | Frontend: Publish Dialog, Version List (Collapsible), Rollback Dialog, Tabs auf Detail Page |
| 4b.10 | Done | Final Verification: Backend + Frontend Tests gruen, Build OK |

## Phase 4c — Draft Revisions + Unified Diff

| Task | Status | Beschreibung |
|---|---|---|
| 4c.1 | Done | zjsonpatch Library (RFC 6902) hinzugefuegt |
| 4c.2 | Done | OpenAPI Spec: 3 Revision-Endpoints (list, detail, revert) |
| 4c.3 | Done | AppService: JSON Patch Revision-Tracking bei update() (zjsonpatch + DiffFlags) |
| 4c.4 | Done | AppRevisionService: list, get, revert, generateSummary (TDD, 5 Tests) |
| 4c.5 | Done | AppRevisionController + Integration Tests (5 Tests) |
| 4c.6 | Done | Frontend: RevisionDiffView, AppRevisionList, RevertDialog, Revisions Tab |
| 4c.7 | Done | Final Verification: 368 BE + 133 FE Tests gruen, Build OK |

## Phase 4d — App Dependencies + Problems View

| Task | Status | Beschreibung |
|---|---|---|
| 4d.1 | Done | Flyway Migration: app_dependency Tabelle mit Composite PK + RLS |
| 4d.2 | Done | OpenAPI Spec: 6 Dependency-Endpoints + Problems Endpoint + 9 Schemas |
| 4d.3 | Done | AppDependency Model, Entity (IdClass Composite Key), Repository |
| 4d.4 | Done | AppDependencyService: Cycle Detection (BFS), transitive Aufloesung, Graph (TDD, 8 Tests) |
| 4d.5 | Done | AppValidationService: Problems Detection fuer inaktive Dependencies (TDD, 3 Tests) |
| 4d.6 | Done | Publish-Validation + Delete-Guard (BadRequest/Conflict bei Problemen/Dependants) |
| 4d.7 | Done | AppDependencyController + Integration Tests (7 Tests) |
| 4d.8 | Done | Frontend: React Flow Graph, Add/Remove Dialogs, Problems Panel, Dependencies Tab |
| 4d.9 | Done | Final Verification: Backend + Frontend Tests gruen, Build OK |

## Phase 4e — App Permissions (OpenFGA) + Permission Matrix UI

| Task | Status | Beschreibung |
|---|---|---|
| 4e.1 | Done | OpenFGA Model: app_management + app Types mit Vererbung |
| 4e.2 | Done | @RequiresPermission auf allen 4 App Controllern, OpenFGA Tuples bei Create/Delete |
| 4e.3 | Done | RoleService: RESOURCE_ACTIONS um app_management (viewer/editor/admin) erweitert |
| 4e.4 | Done | AuthService: getEffectivePermissions um app_management erweitert |
| 4e.5 | Done | Frontend: Permission-aware Navigation (canAccessApps Gate) |
| 4e.6 | Done | Frontend: Permission Matrix UI (General + Apps & Content Tabs, AppPermissionsEditor) |
| 4e.7 | Done | Integration Test Fix: PermissionService Mock + X-Test-User-Id in allen App ITs |
| 4e.8 | Done | Final Verification: 392 BE + 134 FE Tests gruen, Build OK |

## Phase 4f — App Import/Export

| Task | Status | Beschreibung |
|---|---|---|
| 4f.1 | Done | OpenAPI Spec: 3 Import/Export-Endpoints + 6 Schemas |
| 4f.2 | Done | AppBlueprint Model, Snapshot Dependencies Population |
| 4f.3 | Done | AppExportService: Bundled Export mit transitiven Dependencies (TDD, 3 Tests) |
| 4f.4 | Done | AppImportService: Analyze/Preview + Execute mit topologischem Sort (TDD, 4 Tests) |
| 4f.5 | Done | AppImportExportController + Integration Tests (5 Tests) |
| 4f.6 | Done | Frontend: ImportBlueprintDialog (2-Step: Paste+Analyze → Preview+Execute), Export Download |
| 4f.7 | Done | Final Verification: Backend + Frontend Tests gruen, Build OK |

## Phase 4g — E2E Tests (Playwright)

| Task | Status | Beschreibung |
|---|---|---|
| 4g.1 | Done | App API Helpers fuer E2E (createApp, publishApp, updateApp, etc.) |
| 4g.2 | Done | BDU Test 1: App CRUD + Publish (Playwright) |
| 4g.3 | Done | BDU Test 2: Edit + Draft Badge + Revisions (Playwright) |
| 4g.4 | Done | BDU Test 3: Version History + Rollback (Playwright) |
| 4g.5 | Done | BDU Test 4: Dependencies + Problems (Playwright) |
| 4g.6 | Done | BDU Test 5: Import/Export Blueprint (Playwright) |
| 4g.7 | Done | BDU Test 6: Permission-Gated Navigation (Playwright) |
| 4g.8 | Offen | E2E Tests ausfuehren (erfordert pnpm dev / Docker Stack) |
| 4g.9 | Offen | Manual BDU Browser Verification (verify-e2e) |

## Naechste Schritte

- E2E Tests ausfuehren: `pnpm dev` starten, dann `cd apps/e2e && npx playwright test tests/apps/`
- Manual BDU Walkthrough im Browser
- Phase 4 abschliessen: alle Sub-Plaene 4a-4g fertig, Branch mergen
- Sub-Plan 4e: App Permissions (OpenFGA) + Permission Matrix UI
- Sub-Plan 4f: App Import/Export
- Sub-Plan 4g: E2E Tests (Playwright)
- Design Doc: `docs/plans/2026-04-04-app-domain-design.md`
- **Offen:** E2E Tests fuer Keycloak Theme + Invite Flow (erfordert Docker/Infrastruktur)

## Lokale Entwicklung

### Starten

```bash
# Gesamte Umgebung (Docker + Backend + Frontend)
pnpm dev

# Nur Infrastruktur
npx nx up infra

# Nur Backend
npx nx serve backend

# Nur Frontend
npx nx serve platform
```

### Lokale URLs

| URL | Service |
|---|---|
| `platform.localhost` | Next.js Platform UI |
| `api.localhost` | Spring Boot Backend (via Traefik, wenn konfiguriert) |
| `auth.localhost` | Keycloak Admin Console |
| `mail.localhost` | Mailpit |
| `s3.localhost` | MinIO Console |
| `fga.localhost` | OpenFGA |

### Code Generation

```bash
# Backend (Controller Interfaces + DTOs aus OpenAPI)
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate

# Frontend (React Query Hooks + Types aus OpenAPI)
npx nx generate platform
```

## Tech Stack Versionen

| Technologie | Version |
|---|---|
| Java | 21 (Amazon Corretto) |
| Spring Boot | 4.0.3 |
| Spring Modulith | 2.0.3 |
| Gradle | 8.12 |
| Node.js | 22.x |
| pnpm | 10.x |
| Nx | 20.8.x |
| Next.js | 16.x |
| React | 19.x |
| Tailwind CSS | 4.x |
| PostgreSQL | 17 |
| Keycloak | 26 |
| Storybook | 8.x |
