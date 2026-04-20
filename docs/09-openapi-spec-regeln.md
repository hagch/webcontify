# OpenAPI Spec Regeln

## Dateistruktur

Eine Spec-Datei pro Domain-Modul, alle unter `openapi/` Verzeichnis:

```
openapi/
  common-schemas.yaml       -- Gemeinsame Schemas (Error Responses, Pagination, AuditInfo)
  tenant-api.yaml            -- Tenant Management
  identity-api.yaml          -- User, Rollen, Permissions
  app-api.yaml               -- App Management + Dependencies
  content-api.yaml           -- Content Definitions + Entries
```

Gemeinsame Schemas werden via `$ref` referenziert.

## URL-Konventionen

### Base Path

`/api/v1`

### Scopes

| Scope | Prefix | Tenant-Quelle |
|---|---|---|
| Tenant-scoped | `/api/v1/tenant/...` | JWT / Subdomain |
| Platform Admin | `/api/v1/admin/...` | Kein Tenant |
| Content (App-Kontext) | `/api/v1/content/{appSlug}/...` | JWT / Subdomain |

### Ressourcen-Pfade

Ressourcen im **Plural** in URLs (auch wenn DB-Tabellen Singular sind):

```
/api/v1/tenant/apps
/api/v1/tenant/apps/{id}
/api/v1/tenant/users
/api/v1/tenant/users/{id}
/api/v1/tenant/roles
/api/v1/tenant/roles/{id}
/api/v1/content/{appSlug}/definitions
/api/v1/content/{appSlug}/definitions/{id}
/api/v1/content/{appSlug}/{definitionName}/entries
/api/v1/content/{appSlug}/{definitionName}/entries/{id}
```

### Versionierung

- URL-Versionierung (`/api/v1/...`)
- Neue Major-Version = neue Spec-Datei + neuer Pfad (`/api/v2/...`)

## Naming Conventions

### OperationIds

`camelCase`, Verb + Ressource:

| Aktion | Pattern | Beispiel |
|---|---|---|
| Erstellen | `createXxx` | `createApp`, `createRole` |
| Auflisten | `listXxx` | `listApps`, `listUsers` |
| Detail | `getXxx` | `getApp`, `getContentDefinition` |
| Aktualisieren | `updateXxx` | `updateApp`, `updateUser` |
| Loeschen | `deleteXxx` | `deleteApp`, `deleteRole` |

### Schema-Namen

`PascalCase` mit Suffixen:

| Typ | Pattern | Beispiel |
|---|---|---|
| Create Request | `CreateXxxRequest` | `CreateAppRequest` |
| Update Request | `UpdateXxxRequest` | `UpdateAppRequest` |
| Single Response | `XxxResponse` | `AppResponse` |
| List Response | `XxxListResponse` | `AppListResponse` |

## Response-Format

### Einzelne Ressource

```yaml
AppResponse:
  type: object
  properties:
    id:
      type: string
      format: uuid
    name:
      type: string
    slug:
      type: string
    # ...
```

### Listen (immer Wrapper-Objekt)

Alle Listen-Endpoints geben ein Wrapper-Objekt zurueck, nie ein nacktes Array.
So ist das Format konsistent und Pagination kann ohne Breaking Change nachgeruestet werden.

**Ohne Pagination:**

```yaml
AppListResponse:
  type: object
  required:
    - items
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/AppResponse'
```

**Mit Pagination:**

```yaml
ContentEntryListResponse:
  type: object
  required:
    - items
    - page
    - size
    - totalElements
    - totalPages
  properties:
    items:
      type: array
      items:
        $ref: '#/components/schemas/ContentEntryResponse'
    page:
      type: integer
    size:
      type: integer
    totalElements:
      type: integer
    totalPages:
      type: integer
```

### Create-Responses

`201 Created` + `Location` Header + Body mit erstellter Ressource:

```yaml
responses:
  '201':
    description: Created
    headers:
      Location:
        schema:
          type: string
        description: URL der erstellten Ressource
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/AppResponse'
```

## Query-Parameter

### Pagination

```yaml
parameters:
  - name: page
    in: query
    schema:
      type: integer
      default: 0
  - name: size
    in: query
    schema:
      type: integer
      default: 20
  - name: sort
    in: query
    schema:
      type: string
    description: "Feld + Richtung, z.B. name,asc"
```

### Filter

RHS Operators: `?field=operator:value`

```
?name=eq:Blog
?status=in:ACTIVE,DRAFT
?createdAt=gte:2026-01-01T00:00:00Z
```

Operatoren: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `in`, `like`

## Error Responses

Basiert auf RFC 7807 Problem Detail (siehe `07-shared-kernel.md`).
Error Schemas werden in jeder Spec-Datei definiert.

### Standard Error

```yaml
ProblemDetail:
  type: object
  required:
    - type
    - title
    - status
    - code
  properties:
    type:
      type: string
      format: uri
    title:
      type: string
    status:
      type: integer
    code:
      type: string
    detail:
      type: string
    instance:
      type: string
```

### Validation Error

```yaml
ValidationProblemDetail:
  allOf:
    - $ref: '#/components/schemas/ProblemDetail'
    - type: object
      properties:
        errors:
          type: array
          items:
            $ref: '#/components/schemas/FieldError'

FieldError:
  type: object
  required:
    - field
    - message
  properties:
    field:
      type: string
    message:
      type: string
      description: i18n Key, Frontend uebersetzt
```

### Standard Error Responses pro Endpoint

```yaml
responses:
  '400':
    description: Validation Error
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/ValidationProblemDetail'
  '401':
    description: Unauthorized
  '403':
    description: Forbidden
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/ProblemDetail'
  '404':
    description: Not Found
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/ProblemDetail'
  '500':
    description: Internal Server Error
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/ProblemDetail'
```

## Tags

Ein Tag pro Domain:

```yaml
tags:
  - name: Tenant
    description: Tenant Management
  - name: User
    description: User Management
  - name: Role
    description: Role Management
  - name: Permission
    description: Permission Management
  - name: App
    description: App Management
  - name: ContentDefinition
    description: Content Definition Management
  - name: ContentEntry
    description: Content Entry Management
  - name: PlatformAdmin
    description: Platform Administration
```
