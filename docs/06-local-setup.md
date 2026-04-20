# Webcontify - Local Development Setup

## Ueberblick

Ein Befehl (`nx dev`) startet die gesamte Entwicklungsumgebung mit Hot Reloading. Infrastruktur laeuft in Docker, Applikationen nativ mit Nx.

## Architektur

```
nx dev
  ↓
  1. docker compose up -d          ← Infrastruktur
  │   ├── PostgreSQL
  │   ├── Keycloak (+ dev-realm.json Import)
  │   ├── OpenFGA
  │   ├── Garage (S3)
  │   ├── Mailpit
  │   └── Traefik (Reverse Proxy)
  │
  2. nx serve backend              ← Spring Boot (dev Profil)
  │   ├── spring-boot-devtools     ← Auto-Restart bei Java-Aenderungen
  │   └── DevDataSeeder            ← Test-Daten beim Start
  │
  3. nx serve platform             ← Next.js Dev Server
  │   └── Hot Module Replacement
```

## Lokale URLs (via Traefik)

`*.localhost` resolved automatisch auf `127.0.0.1` — kein `/etc/hosts` noetig.

| URL | Service | Beschreibung |
|---|---|---|
| `platform.localhost` | Next.js | Platform UI |
| `acme.localhost` | Next.js | Test-Tenant Subdomain |
| `api.localhost` | Spring Boot | Backend API |
| `auth.localhost` | Keycloak | Auth Admin + Login |
| `mail.localhost` | Mailpit | Email Testing UI |
| `s3.localhost` | Garage | S3-kompatibler File Storage |
| `fga.localhost` | OpenFGA | Playground / API |

## Dateistruktur

```
infra/
├── docker-compose.dev.yml
├── keycloak/
│   └── dev-realm.json              ← Vorkonfigurierte Realms + User
└── traefik/
    └── traefik.dev.yml             ← Routing-Config
```

## Docker Compose (Infrastruktur)

### docker-compose.dev.yml

```yaml
services:
  traefik:
    image: traefik:v3
    ports:
      - "80:80"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - ./traefik/traefik.dev.yml:/etc/traefik/traefik.yml

  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: webcontify
      POSTGRES_USER: webcontify
      POSTGRES_PASSWORD: webcontify
    ports:
      - "5432:5432"

  keycloak:
    image: quay.io/keycloak/keycloak:26
    command: start-dev --import-realm
    volumes:
      - ./keycloak/dev-realm.json:/opt/keycloak/data/import/dev-realm.json
    labels:
      - "traefik.http.routers.keycloak.rule=Host(`auth.localhost`)"

  openfga:
    image: openfga/openfga:latest
    ports:
      - "8081:8080"
    labels:
      - "traefik.http.routers.openfga.rule=Host(`fga.localhost`)"

  garage:
    image: dxflrs/garage:v1
    labels:
      - "traefik.http.routers.garage.rule=Host(`s3.localhost`)"

  mailpit:
    image: axllent/mailpit:latest
    labels:
      - "traefik.http.routers.mailpit.rule=Host(`mail.localhost`)"
```

## Traefik (Reverse Proxy)

Traefik erkennt Container automatisch ueber Docker Labels. Vorteile gegenueber Nginx:

- Keine manuelle Config noetig
- Container werden automatisch registriert
- `*.localhost` Routing ohne `/etc/hosts`

## Nx Targets

```json
{
  "targets": {
    "dev": {
      "executor": "nx:run-commands",
      "options": {
        "commands": [
          "docker compose -f infra/docker-compose.dev.yml up -d",
          "nx serve backend",
          "nx serve platform"
        ],
        "parallel": true
      }
    },
    "infra:up": {
      "executor": "nx:run-commands",
      "options": {
        "command": "docker compose -f infra/docker-compose.dev.yml up -d"
      }
    },
    "infra:down": {
      "executor": "nx:run-commands",
      "options": {
        "command": "docker compose -f infra/docker-compose.dev.yml down"
      }
    }
  }
}
```

### Befehle

| Befehl | Beschreibung |
|---|---|
| `nx dev` | Startet alles (Infra + Backend + Frontend) |
| `nx infra:up` | Nur Docker Infrastruktur starten |
| `nx infra:down` | Docker Infrastruktur stoppen |
| `nx serve backend` | Nur Spring Boot starten |
| `nx serve platform` | Nur Next.js starten |

## Hot Reloading

| Komponente | Mechanismus |
|---|---|
| Next.js (Frontend) | HMR eingebaut, sofort |
| Spring Boot (Backend) | `spring-boot-devtools` — Auto-Restart bei Class-Changes |
| OpenAPI → Client | Orval Watch Mode oder Nx Task bei Spec-Aenderung |
| packages/ui | Next.js erkennt Aenderungen in Workspace-Dependencies |
| Infra (Docker) | Kein Hot Reload noetig, laeuft persistent |

## Test-Daten Strategie

### Schema: Flyway

Flyway verwaltet ausschliesslich Schema-Migrationen. Keine Test-Daten in Flyway.

```
backend/src/main/resources/db/migration/
├── V1__create_tenant_table.sql
├── V2__create_app_table.sql
├── V3__create_content_tables.sql
└── ...
```

### Auth: Keycloak Realm Import

Vorkonfigurierter `dev-realm.json` wird beim Keycloak-Start importiert:

```
Platform Realm:
├── User: admin@platform.local
│   ├── Password: admin
│   └── Rolle: platform-admin

Tenant Realm "acme":
├── User: user@acme.local
│   ├── Password: user
│   └── Rolle: tenant-admin
├── SMTP: mailpit (localhost:1025)
└── Theme: Default
```

### Daten: DevDataSeeder (Spring)

`@Profile("dev")` — laeuft nur im Dev-Profil beim Application Startup. Idempotent (prueft ob Daten bereits existieren).

```
DevDataSeeder erstellt:
│
├── Tenant "Acme Corp"
│   ├── Slug: acme
│   ├── Branding: Default
│   └── S3 Bucket: acme
│
├── App "Blog"
│   ├── Content Definition: Article
│   │   └── Fields: title (TEXT), body (TEXT), published (BOOLEAN)
│   ├── Content Definition: Category
│   │   └── Fields: name (TEXT)
│   └── 3 Beispiel-Entries
│
└── OpenFGA
    ├── Store: acme
    ├── admin@platform.local → platform:admin
    └── user@acme.local → tenant:acme#admin
```

### Warum kein Flyway fuer Test-Daten?

| Kriterium | Flyway | DevDataSeeder |
|---|---|---|
| Kann DB befuellen | Ja | Ja |
| Kann Keycloak konfigurieren | Nein | Ja |
| Kann OpenFGA Tuples setzen | Nein | Ja |
| Kann S3 Bucket erstellen | Nein | Ja |
| Idempotent | Ja (einmalig) | Ja (selbst implementiert) |
| Nur in Dev | Umstaendlich | `@Profile("dev")` |

Test-Daten muessen ueber mehrere Systeme verteilt werden (DB, Keycloak, OpenFGA, S3) — das kann nur der DevDataSeeder.

## Entscheidungslog

| # | Entscheidung | Begruendung |
|---|---|---|
| 1 | `nx dev` als Single Command | Ein Befehl startet alles, Developer Experience |
| 2 | Traefik statt Nginx | Automatische Container-Erkennung via Docker Labels |
| 3 | *.localhost Subdomains | Kein /etc/hosts noetig, Whitelabel lokal testbar |
| 4 | Mailpit fuer Email Testing | UI zum Inspizieren von Emails, SMTP auf Port 1025 |
| 5 | Keycloak Realm Import | Schneller Start, vorkonfigurierte User + SMTP |
| 6 | DevDataSeeder statt Flyway fuer Daten | Kann alle Systeme befuellen (DB + Keycloak + OpenFGA + S3) |
| 7 | Flyway nur fuer Schema | Klare Trennung Schema-Migration vs. Test-Daten |
| 8 | spring-boot-devtools | Hot Reload fuer Java ohne manuellen Restart |
