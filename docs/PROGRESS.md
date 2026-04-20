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

## Naechste Phase

Phase 1: Tenant Domain implementieren (vertikaler Slice).

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
| Spring Boot | 3.5.11 |
| Spring Modulith | 1.4.8 |
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
