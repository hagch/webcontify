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

## Phase 3e–3g: Identity & Access — noch offen

| Task | Status | Beschreibung |
|---|---|---|
| 3e | Offen | Platform Admin User Management |
| 3f | Offen | E2E Tests Auth Flow + Security Review |
| 3g | Offen | Tenant Self-Service User Management |

## Naechste Schritte

- Phase 3e: Platform Admin User Management
- Voraussetzung: Phase 3d abgeschlossen

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
