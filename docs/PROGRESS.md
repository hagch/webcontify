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

## Naechste Schritte

- Phase 1c abgeschlossen
- Naechste Phase: TenantProvisioningService (Bucket bei Tenant-Erstellung), Auth Integration (TenantContext)

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
