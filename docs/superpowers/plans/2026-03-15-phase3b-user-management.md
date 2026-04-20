# Phase 3b: User Management + Invite — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** User Management CRUD + Invite-Flow fuer Tenant Admins, aufbauend auf Phase 3a Infrastruktur.

**Architecture:** Lokale DB als Primary (tenant_user + tenant_role fuer Metadaten), Keycloak fuer Auth-Operationen (Create/Enable/Disable/Delete User), OpenFGA fuer Permissions + Rollenzuordnung (role#assignee). Keine tenant_user_role Join-Table — OpenFGA ist Single Source of Truth fuer User↔Rolle. UserActivationFilter setzt INVITED → ACTIVE beim ersten Login. Contract-First (OpenAPI → generierte Interfaces).

**Tech Stack:** Java 21, Spring Boot 4.0.3, JPA, MapStruct, Flyway, Keycloak Admin Client, OpenFGA, Next.js 16, React 19, React Hook Form, Zod, Orval, shadcn/ui, Vitest

**Spec:** `docs/superpowers/specs/2026-03-15-phase3b-user-management-design.md`

**Referenz-Dateien (lesen vor Implementierung):**
- `docs/08-db-schema-regeln.md` — DB Naming, RLS, Flyway
- `docs/09-openapi-spec-regeln.md` — OpenAPI Struktur, Response-Format
- `docs/03-identity-access-domain.md` — Identity Domain Ueberblick

---

## Chunk 1: DB Migration + OpenAPI Spec + Build Config

### Task 1.1: Flyway Migration

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V20260315_001__add_user_management_tables.sql`

- [ ] **Step 1: Migration-Datei erstellen**

```sql
-- V20260315_001__add_user_management_tables.sql

CREATE TABLE tenant_user (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    email           VARCHAR(255) NOT NULL,
    first_name      VARCHAR(255) NOT NULL,
    last_name       VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'INVITED',
    invited_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    CONSTRAINT uq_tenant_user_email UNIQUE (tenant_id, email)
);

CREATE TABLE tenant_role (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    is_system_role  BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    CONSTRAINT uq_tenant_role_name UNIQUE (tenant_id, name)
);

-- Keine tenant_user_role Tabelle — Zuordnung User↔Rolle laeuft ueber OpenFGA (role#assignee)

CREATE INDEX idx_tenant_user_tenant_id ON tenant_user(tenant_id);
CREATE INDEX idx_tenant_role_tenant_id ON tenant_role(tenant_id);

ALTER TABLE tenant_user ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenant_user ON tenant_user
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );

ALTER TABLE tenant_role ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenant_role ON tenant_role
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );

```

- [ ] **Step 2: Migration testen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew flywayMigrate` (oder App starten und Logs pruefen)
Expected: Migration V20260315_001 erfolgreich

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/resources/db/migration/V20260315_001__add_user_management_tables.sql
git commit -m "feat: add user management DB migration (tenant_user, tenant_role)"
```

---

### Task 1.2: OpenAPI Spec — identity-api.yaml

**Files:**
- Create: `openapi/identity-api.yaml`

- [ ] **Step 1: OpenAPI Spec erstellen**

```yaml
openapi: 3.1.0
info:
  title: Webcontify Identity API
  version: 1.0.0
  description: Tenant User Management endpoints

servers:
  - url: http://localhost:8080
    description: Local development server

tags:
  - name: TenantUsers
    description: Tenant User Management

paths:
  /api/v1/tenant/users:
    post:
      operationId: inviteUser
      summary: Invite a new user to the tenant
      tags:
        - TenantUsers
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/InviteUserRequest'
      responses:
        '201':
          description: User invited
          headers:
            Location:
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '400':
          description: Validation error
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ValidationProblemDetail'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '409':
          description: Email already exists
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
    get:
      operationId: listUsers
      summary: List all users in the tenant
      tags:
        - TenantUsers
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
      responses:
        '200':
          description: User list
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserListResponse'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden

  /api/v1/tenant/users/{userId}:
    get:
      operationId: getUser
      summary: Get user details
      tags:
        - TenantUsers
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: User details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '404':
          description: User not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
    delete:
      operationId: deleteUser
      summary: Delete a user
      tags:
        - TenantUsers
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: User deleted
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '404':
          description: User not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/tenant/users/{userId}/status:
    put:
      operationId: updateUserStatus
      summary: Update user status (activate/deactivate)
      tags:
        - TenantUsers
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateUserStatusRequest'
      responses:
        '200':
          description: Status updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '400':
          description: Invalid status transition
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '404':
          description: User not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/tenant/users/{userId}/resend-invite:
    post:
      operationId: resendInvite
      summary: Resend invite email
      tags:
        - TenantUsers
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Invite resent
        '400':
          description: User is not in INVITED status
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '404':
          description: User not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

components:
  schemas:
    InviteUserRequest:
      type: object
      required:
        - email
        - firstName
        - lastName
      properties:
        email:
          type: string
          format: email
          maxLength: 255
        firstName:
          type: string
          minLength: 1
          maxLength: 255
        lastName:
          type: string
          minLength: 1
          maxLength: 255

    UpdateUserStatusRequest:
      type: object
      required:
        - status
      properties:
        status:
          $ref: '#/components/schemas/UserStatusUpdate'

    UserStatusUpdate:
      type: string
      enum:
        - ACTIVE
        - DEACTIVATED

    UserStatus:
      type: string
      enum:
        - INVITED
        - ACTIVE
        - DEACTIVATED

    UserResponse:
      type: object
      required:
        - id
        - email
        - firstName
        - lastName
        - status
        - createdAt
        - updatedAt
      properties:
        id:
          type: string
          format: uuid
        email:
          type: string
        firstName:
          type: string
        lastName:
          type: string
        status:
          $ref: '#/components/schemas/UserStatus'
        invitedBy:
          type: string
          format: uuid
          nullable: true
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    UserListResponse:
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
            $ref: '#/components/schemas/UserResponse'
        page:
          type: integer
        size:
          type: integer
        totalElements:
          type: integer
          format: int64
        totalPages:
          type: integer
```

- [ ] **Step 2: Commit**

```bash
git add openapi/identity-api.yaml
git commit -m "feat: add identity-api.yaml OpenAPI spec for user management"
```

---

### Task 1.3: Build Config — Multi-Spec OpenAPI Generation

**Files:**
- Modify: `apps/backend/build.gradle.kts:121-141`

Das aktuelle `openApiGenerate` Task generiert nur `tenant-api.yaml`. Fuer `identity-api.yaml` brauchen wir ein zweites Task.

- [ ] **Step 1: Zweites OpenAPI Generate Task hinzufuegen**

Ersetze den bestehenden `openApiGenerate` Block und `compileJava` Dependency:

```kotlin
openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$openApiSpecDir/tenant-api.yaml")
    outputDir.set(openApiOutputDir)
    apiPackage.set("io.webcontify.generated.api")
    modelPackage.set("io.webcontify.generated.model")
    configOptions.set(openApiConfigOptions)
    typeMappings.set(openApiTypeMappings)
}

tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerateIdentity") {
    generatorName.set("spring")
    inputSpec.set("$openApiSpecDir/identity-api.yaml")
    outputDir.set(openApiOutputDir)
    apiPackage.set("io.webcontify.generated.api")
    modelPackage.set("io.webcontify.generated.model")
    configOptions.set(openApiConfigOptions)
    typeMappings.set(openApiTypeMappings)
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate", "openApiGenerateIdentity")
}
```

- [ ] **Step 2: Build testen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: Beide Specs generiert, `TenantUsersApi` Interface in `build/generated/openapi/`

- [ ] **Step 3: Commit**

```bash
git add apps/backend/build.gradle.kts
git commit -m "build: add identity-api.yaml to OpenAPI code generation"
```

---

### Task 1.4: Orval Config — Frontend Identity API

**Files:**
- Modify: `apps/platform/orval.config.ts`

- [ ] **Step 1: Identity-Config hinzufuegen**

```typescript
import { defineConfig } from "orval";

export default defineConfig({
  tenant: {
    input: {
      target: "../../openapi/tenant-api.yaml",
    },
    output: {
      target: "./src/generated/tenant.ts",
      client: "react-query",
      mode: "single",
      override: {
        mutator: {
          path: "./src/lib/orval-mutator.ts",
          name: "customInstance",
        },
      },
    },
  },
  identity: {
    input: {
      target: "../../openapi/identity-api.yaml",
    },
    output: {
      target: "./src/generated/identity.ts",
      client: "react-query",
      mode: "single",
      override: {
        mutator: {
          path: "./src/lib/orval-mutator.ts",
          name: "customInstance",
        },
      },
    },
  },
});
```

- [ ] **Step 2: Orval generieren**

Run: `cd apps/platform && pnpm orval`
Expected: `src/generated/identity.ts` wird erstellt mit Hooks: `useInviteUser`, `useListUsers`, `useGetUser`, `useUpdateUserStatus`, `useResendInvite`, `useDeleteUser`

- [ ] **Step 3: Commit**

```bash
git add apps/platform/orval.config.ts apps/platform/src/generated/identity.ts
git commit -m "build: add Orval config for identity API generation"
```

---

## Chunk 2: Backend Domain Model + JPA Entities

### Task 2.1: UserStatus Enum

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/UserStatus.java`

- [ ] **Step 1: Enum erstellen**

```java
package io.webcontify.identity.model;

public enum UserStatus {
    INVITED,
    ACTIVE,
    DEACTIVATED;

    public boolean canTransitionTo(UserStatus target) {
        return switch (this) {
            case INVITED -> target == ACTIVE || target == DEACTIVATED;
            case ACTIVE -> target == DEACTIVATED;
            case DEACTIVATED -> target == ACTIVE;
        };
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/UserStatus.java
git commit -m "feat: add UserStatus enum with state machine transitions"
```

---

### Task 2.2: TenantUser Domain Model

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/TenantUser.java`

- [ ] **Step 1: Domain Model erstellen**

```java
package io.webcontify.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class TenantUser {
    private UUID id;
    private UUID tenantId;
    private String email;
    private String firstName;
    private String lastName;
    private UserStatus status;
    private UUID invitedBy;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;

    // Getter + Setter (kein Lombok)
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public UUID getInvitedBy() { return invitedBy; }
    public void setInvitedBy(UUID invitedBy) { this.invitedBy = invitedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/TenantUser.java
git commit -m "feat: add TenantUser domain model"
```

---

### Task 2.3: JPA Entities (TenantUserEntity, TenantRoleEntity)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/repository/entity/TenantUserEntity.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/repository/entity/TenantRoleEntity.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/repository/entity/UserStatusConverter.java`

- [ ] **Step 1: UserStatusConverter erstellen**

```java
package io.webcontify.identity.repository.entity;

import io.webcontify.identity.model.UserStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {
    @Override
    public String convertToDatabaseColumn(UserStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public UserStatus convertToEntityAttribute(String value) {
        return value == null ? null : UserStatus.valueOf(value);
    }
}
```

- [ ] **Step 2: TenantUserEntity erstellen**

```java
package io.webcontify.identity.repository.entity;

import io.webcontify.identity.model.UserStatus;
import io.webcontify.shared.persistence.TenantAwareBaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tenant_user")
// Kein @EntityListeners noetig — AuditListener wird von AuditableBaseEntity geerbt
public class TenantUserEntity extends TenantAwareBaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    @Convert(converter = UserStatusConverter.class)
    private UserStatus status;

    @Column(name = "invited_by")
    private UUID invitedBy;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public UUID getInvitedBy() { return invitedBy; }
    public void setInvitedBy(UUID invitedBy) { this.invitedBy = invitedBy; }
}
```

- [ ] **Step 3: TenantRoleEntity erstellen**

```java
package io.webcontify.identity.repository.entity;

import io.webcontify.shared.persistence.TenantAwareBaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "tenant_role")
// Kein @EntityListeners noetig — AuditListener wird von AuditableBaseEntity geerbt
public class TenantRoleEntity extends TenantAwareBaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_system_role", nullable = false)
    private boolean systemRole;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystemRole() { return systemRole; }
    public void setSystemRole(boolean systemRole) { this.systemRole = systemRole; }
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/repository/entity/
git commit -m "feat: add JPA entities for tenant_user, tenant_role"
```

---

### Task 2.4: Spring Data Repositories

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/repository/TenantUserRepository.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/repository/TenantRoleRepository.java`
- [ ] **Step 1: TenantUserRepository erstellen**

```java
package io.webcontify.identity.repository;

import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface TenantUserRepository extends JpaRepository<TenantUserEntity, UUID> {

    @Query("SELECT u FROM TenantUserEntity u WHERE u.id = :id AND u.auditInfo.deletedAt IS NULL")
    Optional<TenantUserEntity> findActiveById(UUID id);

    @Query("SELECT u FROM TenantUserEntity u WHERE u.auditInfo.deletedAt IS NULL")
    Page<TenantUserEntity> findAllActive(Pageable pageable);

    @Query("SELECT u FROM TenantUserEntity u WHERE u.email = :email AND u.auditInfo.deletedAt IS NULL")
    Optional<TenantUserEntity> findActiveByEmail(String email);

    @Query("SELECT COUNT(u) FROM TenantUserEntity u WHERE u.status = :status AND u.auditInfo.deletedAt IS NULL")
    long countByStatus(UserStatus status);
}
```

- [ ] **Step 2: TenantRoleRepository erstellen**

```java
package io.webcontify.identity.repository;

import io.webcontify.identity.repository.entity.TenantRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRoleEntity, UUID> {
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/repository/TenantUserRepository.java
git add apps/backend/src/main/java/io/webcontify/identity/repository/TenantRoleRepository.java
git commit -m "feat: add Spring Data repositories for user management"
```

---

### Task 2.5: UserMapper (MapStruct)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/mapper/UserMapper.java`

Referenz: `apps/backend/src/main/java/io/webcontify/tenant/mapper/TenantMapper.java` fuer Pattern.

- [ ] **Step 1: UserMapper erstellen**

```java
package io.webcontify.identity.mapper;

import io.webcontify.identity.model.TenantUser;
import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    TenantUser toDomain(TenantUserEntity entity);

    @Mapping(target = "auditInfo", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    TenantUserEntity toEntity(TenantUser domain);

    @Named("domainStatusToApiStatus")
    default io.webcontify.generated.model.UserStatus domainStatusToApiStatus(UserStatus status) {
        return status == null ? null : io.webcontify.generated.model.UserStatus.fromValue(status.name());
    }

    @Mapping(source = "status", target = "status", qualifiedByName = "domainStatusToApiStatus")
    io.webcontify.generated.model.UserResponse toResponse(TenantUser domain);
}
```

- [ ] **Step 2: Build testen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: MapStruct generiert UserMapperImpl

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/mapper/UserMapper.java
git commit -m "feat: add UserMapper (MapStruct) for entity/domain/response mapping"
```

---

## Chunk 3: Backend Services

### Task 3.1: BadRequestException hinzufuegen

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/shared/exception/BadRequestException.java`

Existiert noch nicht. Wird fuer Status-Transitions und Last-Admin-Guard benoetigt.

- [ ] **Step 1: BadRequestException erstellen**

```java
package io.webcontify.shared.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends WebcontifyException {

    public BadRequestException(String code) {
        super(code, HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/shared/exception/BadRequestException.java
git commit -m "feat: add BadRequestException for 400 error responses"
```

---

### Task 3.2: KeycloakAdminService erweitern (Enable/Disable)

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/EnableKeycloakUserRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/DisableKeycloakUserRequest.java`

- [ ] **Step 1: Request Records erstellen**

```java
// EnableKeycloakUserRequest.java
package io.webcontify.identity.model;

import java.util.UUID;

public record EnableKeycloakUserRequest(String realmName, UUID userId) {}
```

```java
// DisableKeycloakUserRequest.java
package io.webcontify.identity.model;

import java.util.UUID;

public record DisableKeycloakUserRequest(String realmName, UUID userId) {}
```

- [ ] **Step 2: Methoden in KeycloakAdminService hinzufuegen**

In `KeycloakAdminService.java` nach `updateUser()` hinzufuegen:

```java
public void enableUser(EnableKeycloakUserRequest request) {
    var userResource = keycloak.realm(request.realmName())
            .users()
            .get(request.userId().toString());
    var user = userResource.toRepresentation();
    user.setEnabled(true);
    userResource.update(user);
}

public void disableUser(DisableKeycloakUserRequest request) {
    var userResource = keycloak.realm(request.realmName())
            .users()
            .get(request.userId().toString());
    var user = userResource.toRepresentation();
    user.setEnabled(false);
    userResource.update(user);
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/EnableKeycloakUserRequest.java
git add apps/backend/src/main/java/io/webcontify/identity/model/DisableKeycloakUserRequest.java
git add apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java
git commit -m "feat: add enable/disable user methods to KeycloakAdminService"
```

---

### Task 3.3: UserService — Invite + List + GetById

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/UserService.java`

Referenz fuer TenantContext/Authentication: `PermissionCheckAspect.java:40-42` zeigt wie userId aus SecurityContext extrahiert wird.

- [ ] **Step 1: UserService mit invite(), list(), getById() erstellen**

```java
package io.webcontify.identity.service;

import io.webcontify.identity.mapper.UserMapper;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final TenantUserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final OpenFgaService openFgaService;
    private final PermissionService permissionService;
    private final TenantInfrastructureLookupService lookupService;
    private final UserMapper userMapper;

    public UserService(
            TenantUserRepository userRepository,
            KeycloakAdminService keycloakAdminService,
            OpenFgaService openFgaService,
            PermissionService permissionService,
            TenantInfrastructureLookupService lookupService,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.openFgaService = openFgaService;
        this.permissionService = permissionService;
        this.lookupService = lookupService;
        this.userMapper = userMapper;
    }

    @Transactional
    public TenantUser invite(String email, String firstName, String lastName) {
        // Duplikat-Check
        userRepository.findActiveByEmail(email).ifPresent(existing -> {
            throw new ConflictException("user.email_already_exists");
        });

        var tenantInfo = TenantContext.getTenantInformation();
        var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

        // Keycloak User erstellen (gibt Keycloak-UUID zurueck, wird als PK verwendet)
        UUID keycloakUserId;
        try {
            var keycloakResponse = keycloakAdminService.createUser(
                    new CreateKeycloakUserRequest(infrastructure.keycloakRealmName(), email, firstName, lastName));
            keycloakUserId = keycloakResponse.keycloakUserId();
        } catch (Exception e) {
            log.error("Failed to create Keycloak user for email={}", email, e);
            throw e;
        }

        // DB-Insert (bei Fehler: Keycloak-User cleanup)
        TenantUserEntity entity;
        try {
            entity = new TenantUserEntity();
            entity.setId(keycloakUserId);
            entity.setTenantId(tenantInfo.tenantId());
            entity.setEmail(email);
            entity.setFirstName(firstName);
            entity.setLastName(lastName);
            entity.setStatus(UserStatus.INVITED);
            entity.setInvitedBy(resolveCurrentUserId());
            entity = userRepository.save(entity);
        } catch (Exception e) {
            log.error("DB insert failed for user={}, cleaning up Keycloak user", keycloakUserId, e);
            try {
                keycloakAdminService.deleteUser(
                        new DeleteKeycloakUserRequest(infrastructure.keycloakRealmName(), keycloakUserId));
            } catch (Exception cleanupEx) {
                log.error("Keycloak cleanup failed for user={}", keycloakUserId, cleanupEx);
            }
            throw e;
        }

        // OpenFGA: grant admin
        permissionService.grant(new GrantPermissionRequest(
                keycloakUserId, "admin", "tenant", tenantInfo.tenantId()));

        // Invite-Email senden
        keycloakAdminService.sendInviteEmail(
                new SendInviteEmailRequest(infrastructure.keycloakRealmName(), keycloakUserId));

        log.info("User invited: id={}, email={}, tenant={}", keycloakUserId, email, tenantInfo.tenantId());
        return userMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public Page<TenantUser> list(Pageable pageable) {
        return userRepository.findAllActive(pageable).map(userMapper::toDomain);
    }

    @Transactional(readOnly = true)
    public TenantUser getById(UUID userId) {
        return userRepository.findActiveById(userId)
                .map(userMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("user.not_found"));
    }

    private UUID resolveCurrentUserId() {
        var authentication = (TenantAwareAuthentication)
                SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) authentication.getPrincipal());
    }
}
```

- [ ] **Step 2: Build testen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/UserService.java
git commit -m "feat: add UserService with invite, list, getById"
```

---

### Task 3.4: UserService — updateStatus, resendInvite, delete

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/UserService.java`

- [ ] **Step 1: updateStatus() hinzufuegen**

In `UserService.java` nach `getById()`:

```java
@Transactional
public TenantUser updateStatus(UUID userId, UserStatus newStatus) {
    var entity = userRepository.findActiveById(userId)
            .orElseThrow(() -> new NotFoundException("user.not_found"));

    UserStatus currentStatus = entity.getStatus();

    if (!currentStatus.canTransitionTo(newStatus)) {
        throw new BadRequestException("user.invalid_status_transition");
    }

    UUID currentUserId = resolveCurrentUserId();
    if (newStatus == UserStatus.DEACTIVATED && userId.equals(currentUserId)) {
        throw new BadRequestException("user.cannot_deactivate_self");
    }

    // Last-Admin-Guard bei Deaktivierung
    if (newStatus == UserStatus.DEACTIVATED) {
        guardLastAdmin(userId);
    }

    var tenantInfo = TenantContext.getTenantInformation();
    var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

    // Keycloak enable/disable
    if (newStatus == UserStatus.DEACTIVATED) {
        keycloakAdminService.disableUser(
                new DisableKeycloakUserRequest(infrastructure.keycloakRealmName(), userId));
    } else if (newStatus == UserStatus.ACTIVE) {
        keycloakAdminService.enableUser(
                new EnableKeycloakUserRequest(infrastructure.keycloakRealmName(), userId));
    }

    entity.setStatus(newStatus);
    entity = userRepository.save(entity);

    log.info("User status updated: id={}, {} -> {}", userId, currentStatus, newStatus);
    return userMapper.toDomain(entity);
}
```

- [ ] **Step 2: resendInvite() hinzufuegen**

```java
@Transactional(readOnly = true)
public void resendInvite(UUID userId) {
    var entity = userRepository.findActiveById(userId)
            .orElseThrow(() -> new NotFoundException("user.not_found"));

    if (entity.getStatus() != UserStatus.INVITED) {
        throw new BadRequestException("user.not_invited");
    }

    var tenantInfo = TenantContext.getTenantInformation();
    var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

    keycloakAdminService.sendInviteEmail(
            new SendInviteEmailRequest(infrastructure.keycloakRealmName(), userId));

    log.info("Invite resent for user={}", userId);
}
```

- [ ] **Step 3: delete() hinzufuegen**

```java
@Transactional
public void delete(UUID userId) {
    UUID currentUserId = resolveCurrentUserId();
    if (userId.equals(currentUserId)) {
        throw new BadRequestException("user.cannot_delete_self");
    }

    var entity = userRepository.findActiveById(userId)
            .orElseThrow(() -> new NotFoundException("user.not_found"));

    guardLastAdmin(userId);

    var tenantInfo = TenantContext.getTenantInformation();
    var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());

    // Soft Delete in lokaler DB
    entity.getAuditInfo().setDeletedAt(java.time.OffsetDateTime.now());
    entity.getAuditInfo().setDeletedBy(currentUserId);
    userRepository.save(entity);

    // Hard Delete in Keycloak
    keycloakAdminService.deleteUser(
            new DeleteKeycloakUserRequest(infrastructure.keycloakRealmName(), userId));

    // OpenFGA Cleanup
    permissionService.revoke(new RevokePermissionRequest(
            userId, "admin", "tenant", tenantInfo.tenantId()));

    log.info("User deleted: id={}", userId);
}
```

- [ ] **Step 4: guardLastAdmin() private Hilfsmethode hinzufuegen**

```java
// Phase 3b: Alle User sind Admins (Entscheidung 3), daher reicht countByStatus.
// TODO Phase 3d: Auf OpenFGA Admin-Rolle filtern wenn Custom Roles eingefuehrt werden.
private void guardLastAdmin(UUID excludeUserId) {
    long activeAdminCount = userRepository.countByStatus(UserStatus.ACTIVE);
    var entity = userRepository.findActiveById(excludeUserId).orElse(null);
    if (entity != null && entity.getStatus() == UserStatus.ACTIVE && activeAdminCount <= 1) {
        throw new BadRequestException("user.last_admin_cannot_be_removed");
    }
}
```

- [ ] **Step 5: Build testen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/UserService.java
git commit -m "feat: add updateStatus, resendInvite, delete to UserService"
```

---

### Task 3.5: UserActivationService + UserActivationFilter

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/UserActivationService.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/security/UserActivationFilter.java`
- Modify: `apps/backend/src/main/java/io/webcontify/shared/security/SecurityConfiguration.java:52`

Die Aktivierungslogik wird in einen Spring-managed `UserActivationService` extrahiert (nicht direkt im Filter), damit `@Transactional` korrekt funktioniert und die RLS-Session-Variable innerhalb einer Transaktion gesetzt wird.

- [ ] **Step 1: UserActivationService erstellen**

```java
package io.webcontify.identity.service;

import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.TenantUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserActivationService {

    private static final Logger log = LoggerFactory.getLogger(UserActivationService.class);

    private final TenantUserRepository userRepository;

    public UserActivationService(TenantUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void activateOnFirstLogin(UUID userId) {
        userRepository.findById(userId).ifPresent(entity -> {
            if (entity.getStatus() == UserStatus.INVITED && entity.getAuditInfo().getDeletedAt() == null) {
                entity.setStatus(UserStatus.ACTIVE);
                entity.getAuditInfo().setUpdatedAt(OffsetDateTime.now());
                entity.getAuditInfo().setUpdatedBy(userId);
                userRepository.save(entity);
                log.info("User activated on first login: id={}", userId);
            }
        });
    }
}
```

- [ ] **Step 2: UserActivationFilter erstellen (delegiert an Service)**

```java
package io.webcontify.identity.security;

import io.webcontify.identity.service.UserActivationService;
import io.webcontify.shared.security.TenantAwareAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class UserActivationFilter extends OncePerRequestFilter {

    private final UserActivationService activationService;

    public UserActivationFilter(UserActivationService activationService) {
        this.activationService = activationService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof TenantAwareAuthentication tenantAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Platform Admin hat keinen tenant_user Eintrag
        if (tenantAuth.getTenantInformation().platformAdmin()) {
            filterChain.doFilter(request, response);
            return;
        }

        Object principal = tenantAuth.getPrincipal();
        UUID userId;
        try {
            userId = principal instanceof UUID uid ? uid : UUID.fromString(principal.toString());
        } catch (IllegalArgumentException e) {
            filterChain.doFilter(request, response);
            return;
        }

        activationService.activateOnFirstLogin(userId);

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: Filter in SecurityConfiguration registrieren**

In `SecurityConfiguration.java`, Constructor-Parameter hinzufuegen und Filter registrieren:

```java
// Neues Feld + Constructor-Parameter
private final UserActivationFilter userActivationFilter;

public SecurityConfiguration(
        JwtToTenantAuthenticationConverter jwtToTenantAuthenticationConverter,
        TenantSlugResolver tenantSlugResolver,
        UserActivationFilter userActivationFilter) {
    this.jwtToTenantAuthenticationConverter = jwtToTenantAuthenticationConverter;
    this.tenantSlugResolver = tenantSlugResolver;
    this.userActivationFilter = userActivationFilter;
}
```

In `securityFilterChain()`, nach der bestehenden `addFilterAfter` Zeile:

```java
.addFilterAfter(new TenantResolutionFilter(tenantSlugResolver), BearerTokenAuthenticationFilter.class)
.addFilterAfter(userActivationFilter, TenantResolutionFilter.class);
```

- [ ] **Step 4: Build testen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/UserActivationService.java
git add apps/backend/src/main/java/io/webcontify/identity/security/UserActivationFilter.java
git add apps/backend/src/main/java/io/webcontify/shared/security/SecurityConfiguration.java
git commit -m "feat: add UserActivationService + UserActivationFilter for INVITED -> ACTIVE on first login"
```

---

## Chunk 4: Controller + Provisioning-Erweiterung

### Task 4.1: UserController

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/api/UserController.java`

Implementiert das generierte `TenantUsersApi` Interface (aus `identity-api.yaml`). Referenz: generiertes Interface hat Methoden wie `inviteUser()`, `listUsers()`, etc.

- [ ] **Step 1: UserController erstellen**

```java
package io.webcontify.identity.api;

import io.webcontify.generated.api.TenantUsersApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.mapper.UserMapper;
import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
public class UserController implements TenantUsersApi {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @Override
    @RequiresPermission(action = "inviter", resource = "user_management")
    public ResponseEntity<UserResponse> inviteUser(InviteUserRequest request) {
        var user = userService.invite(request.getEmail(), request.getFirstName(), request.getLastName());
        var response = userMapper.toResponse(user);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "user_management")
    public ResponseEntity<UserListResponse> listUsers(Integer page, Integer size) {
        var pageResult = userService.list(PageRequest.of(page, size));

        var response = new UserListResponse();
        response.setItems(pageResult.getContent().stream()
                .map(userMapper::toResponse)
                .toList());
        response.setPage(pageResult.getNumber());
        response.setSize(pageResult.getSize());
        response.setTotalElements(pageResult.getTotalElements());
        response.setTotalPages(pageResult.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "user_management")
    public ResponseEntity<UserResponse> getUser(UUID userId) {
        var user = userService.getById(userId);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @Override
    @RequiresPermission(action = "editor", resource = "user_management")
    public ResponseEntity<UserResponse> updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        var newStatus = UserStatus.valueOf(request.getStatus().getValue());
        var user = userService.updateStatus(userId, newStatus);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @Override
    @RequiresPermission(action = "inviter", resource = "user_management")
    public ResponseEntity<Void> resendInvite(UUID userId) {
        userService.resendInvite(userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "deleter", resource = "user_management")
    public ResponseEntity<Void> deleteUser(UUID userId) {
        userService.delete(userId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Build testen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/api/UserController.java
git commit -m "feat: add UserController implementing TenantUsersApi"
```

---

### Task 4.2: TenantProvisioningListener erweitern (Step 4: tenant_user + tenant_role)

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java`

Step 3 (INITIAL_ADMIN, Zeile 85-101) muss erweitert werden: nach Keycloak-User-Erstellung auch tenant_role ("Admin"), tenant_user und tenant_user_role Eintraege anlegen.

**Wichtig:** Der Listener hat keinen HTTP-Request-Kontext. `TenantContext` nutzt SecurityContext, der hier nicht vorhanden ist. Die `TenantAwareDataSource` setzt `SET LOCAL app.tenant_id` basierend auf TenantContext. Fuer den Listener muessen wir die DB-Inserts via die Repositories machen — aber RLS erfordert den Tenant-Kontext. Loesung: Direkt den `TenantContext` ueber eine temporaere `TenantAwareAuthentication` im SecurityContext setzen.

- [ ] **Step 1: Neue Dependencies in Constructor**

Neue Felder + Constructor-Parameter:
```java
private final TenantUserRepository tenantUserRepository;
private final TenantRoleRepository tenantRoleRepository;
```

- [ ] **Step 2: Step 3 (INITIAL_ADMIN) Logik erweitern**

Ersetze den bestehenden Step 3 Block (Zeile 85-101):

```java
// Step 3: Initial Admin
if (!executeStep(steps, 3, ProvisioningStepType.INITIAL_ADMIN, () -> {
    var realmName = entity.getKeycloakRealmName();
    var storeId = entity.getOpenfgaStoreId();

    // Keycloak User erstellen
    var userResponse = keycloakAdminService.createUser(
            new CreateKeycloakUserRequest(realmName, event.adminEmail(), event.adminFirstName(), event.adminLastName()));
    UUID keycloakUserId = userResponse.keycloakUserId();

    // RLS-Kontext setzen fuer DB-Inserts
    setProvisioningSecurityContext(event.tenantId());
    try {
        // Admin-Rolle erstellen (is_system_role = true)
        var roleEntity = new TenantRoleEntity();
        roleEntity.setId(com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate());
        roleEntity.setTenantId(event.tenantId());
        roleEntity.setName("Admin");
        roleEntity.setSystemRole(true);
        roleEntity = tenantRoleRepository.save(roleEntity);

        // tenant_user Eintrag erstellen
        var userEntity = new TenantUserEntity();
        userEntity.setId(keycloakUserId);
        userEntity.setTenantId(event.tenantId());
        userEntity.setEmail(event.adminEmail());
        userEntity.setFirstName(event.adminFirstName());
        userEntity.setLastName(event.adminLastName());
        userEntity.setStatus(UserStatus.INVITED);
        // invitedBy = null (System-Erstellung)
        tenantUserRepository.save(userEntity);
        // User↔Rolle Zuordnung laeuft ueber OpenFGA (role#assignee), keine lokale Join-Table
    } finally {
        clearProvisioningSecurityContext();
    }

    // OpenFGA: grant admin
    openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
            new RelationshipTuple(
                    "user:" + keycloakUserId,
                    "admin",
                    "tenant:" + event.tenantId()))));

    // Invite-Email
    keycloakAdminService.sendInviteEmail(new SendInviteEmailRequest(realmName, keycloakUserId));
})) {
    persistStatus(entity, steps, initialStatus, false);
    return;
}
```

- [ ] **Step 3: Private Helper-Methoden fuer Security-Kontext hinzufuegen**

```java
private void setProvisioningSecurityContext(UUID tenantId) {
    var tenantInfo = new TenantInformation(tenantId, false);
    // Principal als UUID uebergeben (nicht String), damit AuditListener.resolveCurrentUser()
    // den User korrekt als UUID erkennt und nicht auf SystemUser.ID zurueckfaellt.
    var auth = new TenantAwareAuthentication(
            tenantInfo, SystemUser.ID, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
}

private void clearProvisioningSecurityContext() {
    SecurityContextHolder.clearContext();
}
```

Imports hinzufuegen:
```java
import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.TenantRoleRepository;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import org.springframework.security.core.context.SecurityContextHolder;
```

- [ ] **Step 4: Build testen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java
git commit -m "feat: extend TenantProvisioningListener to create tenant_user and tenant_role on provisioning"
```

---

## Chunk 5: Backend Tests

### Task 5.1: UserServiceTest (Unit Tests mit Mockito)

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/UserServiceTest.java`

**Hinweis:** TDD war fuer diesen Plan nicht moeglich, da die Services komplex und abhaengig von generierten Klassen sind. Die Tests werden nachgezogen. Pro Test: schreiben → laufen lassen → sicherstellen dass er gruen ist.

- [ ] **Step 1: Test-Klasse mit Setup erstellen**

```java
package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.identity.mapper.UserMapper;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import io.webcontify.shared.security.TenantAwareAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock TenantUserRepository userRepository;
    @Mock KeycloakAdminService keycloakAdminService;
    @Mock OpenFgaService openFgaService;
    @Mock PermissionService permissionService;
    @Mock TenantInfrastructureLookupService lookupService;
    @Mock UserMapper userMapper;

    @InjectMocks UserService userService;

    static final UUID TENANT_ID = UUID.randomUUID();
    static final UUID CURRENT_USER_ID = UUID.randomUUID();
    static final UUID TARGET_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var tenantInfo = new TenantInformation(TENANT_ID, false);
        var auth = new TenantAwareAuthentication(tenantInfo, CURRENT_USER_ID.toString(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var infra = new TenantInfrastructureInfo("store-123", "tenant-test");
        lenient().when(lookupService.getInfrastructure(TENANT_ID)).thenReturn(infra);
    }

    // Hilfsmethode
    private TenantUserEntity createUserEntity(UUID id, UserStatus status) {
        var entity = new TenantUserEntity();
        entity.setId(id);
        entity.setTenantId(TENANT_ID);
        entity.setEmail("user@test.com");
        entity.setFirstName("Max");
        entity.setLastName("Muster");
        entity.setStatus(status);
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(CURRENT_USER_ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(CURRENT_USER_ID);
        entity.setAuditInfo(auditInfo);
        return entity;
    }
}
```

**Hinweis:** `TenantInfrastructure` ist das Record das `lookupService.getInfrastructure()` zurueckgibt. Den genauen Typ aus dem bestehenden Code pruefen (`TenantInfrastructureLookupService`).

- [ ] **Step 2: Tests ausfuehren und Setup verifizieren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.UserServiceTest"`
Expected: 0 Tests, keine Fehler

- [ ] **Step 3: Invite Tests hinzufuegen**

```java
@Nested
class Invite {
    @Test
    void shouldInviteUserSuccessfully() {
        when(userRepository.findActiveByEmail("new@test.com")).thenReturn(Optional.empty());
        when(keycloakAdminService.createUser(any()))
                .thenReturn(new CreateKeycloakUserResponse(TARGET_USER_ID));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDomain(any(TenantUserEntity.class))).thenReturn(new TenantUser());

        userService.invite("new@test.com", "Max", "Muster");

        verify(keycloakAdminService).createUser(any());
        verify(userRepository).save(any());
        verify(permissionService).grant(any());
        verify(keycloakAdminService).sendInviteEmail(any());
    }

    @Test
    void shouldThrowConflictWhenEmailExists() {
        var existing = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
        when(userRepository.findActiveByEmail("existing@test.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.invite("existing@test.com", "Max", "Muster"))
                .isInstanceOf(ConflictException.class);

        verify(keycloakAdminService, never()).createUser(any());
    }

    @Test
    void shouldCleanupKeycloakUserOnDbError() {
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());
        when(keycloakAdminService.createUser(any()))
                .thenReturn(new CreateKeycloakUserResponse(TARGET_USER_ID));
        when(userRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> userService.invite("new@test.com", "Max", "Muster"))
                .isInstanceOf(RuntimeException.class);

        verify(keycloakAdminService).deleteUser(any());
    }
}
```

- [ ] **Step 4: Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.UserServiceTest"`
Expected: 3 Tests PASS

- [ ] **Step 5: UpdateStatus Tests hinzufuegen**

```java
@Nested
class UpdateStatus {
    @Test
    void shouldDeactivateActiveUser() {
        var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
        when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDomain(any(TenantUserEntity.class))).thenReturn(new TenantUser());

        userService.updateStatus(TARGET_USER_ID, UserStatus.DEACTIVATED);

        verify(keycloakAdminService).disableUser(any());
        verify(userRepository).save(any());
    }

    @Test
    void shouldRejectInvalidTransition() {
        var entity = createUserEntity(TARGET_USER_ID, UserStatus.DEACTIVATED);
        when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> userService.updateStatus(TARGET_USER_ID, UserStatus.DEACTIVATED))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRejectSelfDeactivation() {
        var entity = createUserEntity(CURRENT_USER_ID, UserStatus.ACTIVE);
        when(userRepository.findActiveById(CURRENT_USER_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> userService.updateStatus(CURRENT_USER_ID, UserStatus.DEACTIVATED))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRejectDeactivatingLastAdmin() {
        var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
        when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateStatus(TARGET_USER_ID, UserStatus.DEACTIVATED))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldActivateDeactivatedUser() {
        var entity = createUserEntity(TARGET_USER_ID, UserStatus.DEACTIVATED);
        when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDomain(any(TenantUserEntity.class))).thenReturn(new TenantUser());

        userService.updateStatus(TARGET_USER_ID, UserStatus.ACTIVE);

        verify(keycloakAdminService).enableUser(any());
    }
}
```

- [ ] **Step 6: Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.UserServiceTest"`
Expected: 8 Tests PASS

- [ ] **Step 7: Delete + ResendInvite Tests hinzufuegen**

```java
@Nested
class Delete {
    @Test
    void shouldDeleteUser() {
        var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
        when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(2L);

        userService.delete(TARGET_USER_ID);

        verify(userRepository).save(any());
        verify(keycloakAdminService).deleteUser(any());
        verify(permissionService).revoke(any());
    }

    @Test
    void shouldRejectSelfDeletion() {
        assertThatThrownBy(() -> userService.delete(CURRENT_USER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRejectDeletingLastAdmin() {
        var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
        when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> userService.delete(TARGET_USER_ID))
                .isInstanceOf(BadRequestException.class);
    }
}

@Nested
class ResendInvite {
    @Test
    void shouldResendInviteForInvitedUser() {
        var entity = createUserEntity(TARGET_USER_ID, UserStatus.INVITED);
        when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));

        userService.resendInvite(TARGET_USER_ID);

        verify(keycloakAdminService).sendInviteEmail(any());
    }

    @Test
    void shouldRejectResendForActiveUser() {
        var entity = createUserEntity(TARGET_USER_ID, UserStatus.ACTIVE);
        when(userRepository.findActiveById(TARGET_USER_ID)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> userService.resendInvite(TARGET_USER_ID))
                .isInstanceOf(BadRequestException.class);
    }
}
```

- [ ] **Step 8: Alle Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.UserServiceTest"`
Expected: 13 Tests PASS

- [ ] **Step 9: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/identity/service/UserServiceTest.java
git commit -m "test: add UserServiceTest (invite, updateStatus, delete, resendInvite)"
```

---

### Task 5.2: UserManagement Integration Test

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/identity/api/UserControllerIntegrationTest.java`

Referenz: `TenantProvisioningIntegrationTest.java` fuer Pattern (SpringBootTest + MockMvc + Testcontainers + @MockitoBean).

**Wichtig:** TestSecurityConfiguration setzt X-Test-Tenant-Id Header fuer Tenant-User Simulation. Der Principal ist "test-tenant-user" (String), nicht UUID. Fuer User-Endpoints brauchen wir eine UUID als Principal. Pruefen ob TestSecurityConfiguration angepasst werden muss (neuer Header `X-Test-User-Id`).

- [ ] **Step 1: TestSecurityConfiguration erweitern**

In `TestSecurityConfiguration.TestAuthenticationFilter`, den Principal als UUID setzen wenn `X-Test-User-Id` Header vorhanden:

```java
// In TestAuthenticationFilter.doFilterInternal(), im tenantIdHeader-Block:
String userIdHeader = request.getHeader("X-Test-User-Id");
Object principal = (userIdHeader != null && !userIdHeader.isBlank())
        ? userIdHeader
        : "test-tenant-user";
auth = new TenantAwareAuthentication(tenantUser, principal, List.of());
```

Neues Static Feld:
```java
public static final String TEST_USER_ID_HEADER = "X-Test-User-Id";
```

- [ ] **Step 2: Integration Test erstellen**

```java
package io.webcontify.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.identity.model.CreateKeycloakUserResponse;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.service.KeycloakAdminService;
import io.webcontify.identity.service.OpenFgaService;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.identity.service.TenantInfrastructureLookupService;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class UserControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean KeycloakAdminService keycloakAdminService;
    @MockitoBean OpenFgaService openFgaService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean TenantInfrastructureLookupService lookupService;

    @Autowired MockMvc mockMvc;
    @Autowired TenantUserRepository userRepository;
    @Autowired ObjectMapper objectMapper;

    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Hinweis: Tenant muss in DB existieren fuer FK.
        // Entweder Tenant vorher anlegen oder FK pruefen.
        // PermissionService Mock: check() immer true
        lenient().when(permissionService.check(any())).thenReturn(true);

        var keycloakUserId = UUID.randomUUID();
        when(keycloakAdminService.createUser(any()))
                .thenReturn(new CreateKeycloakUserResponse(keycloakUserId));
    }

    @Test
    void shouldInviteAndListUsers() throws Exception {
        // Hinweis: Dieser Test braucht einen existierenden Tenant in der DB.
        // Setup: Tenant via Repository anlegen oder via API.
        // Details haengen vom bestehenden Test-Setup ab — anpassen nach DB-State.

        String inviteBody = """
                {
                    "email": "invited@test.com",
                    "firstName": "Max",
                    "lastName": "Muster"
                }
                """;

        mockMvc.perform(post("/api/v1/tenant/users")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header("X-Test-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("invited@test.com"))
                .andExpect(jsonPath("$.status").value("INVITED"));

        mockMvc.perform(get("/api/v1/tenant/users")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header("X-Test-User-Id", USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
```

**Hinweis:** Dieser Test muss moeglicherweise angepasst werden je nach bestehendem Tenant-Setup in der Test-DB. Der Tenant mit TENANT_ID muss in der `tenant` Tabelle existieren (FK Constraint). Entweder im @BeforeEach einen Tenant anlegen oder TenantRepository nutzen.

- [ ] **Step 3: Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.api.UserControllerIntegrationTest"`
Expected: Tests PASS (ggf. Anpassungen noetig fuer Tenant-FK)

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/identity/api/UserControllerIntegrationTest.java
git add apps/backend/src/test/java/io/webcontify/shared/security/TestSecurityConfiguration.java
git commit -m "test: add UserController integration test + extend TestSecurityConfiguration"
```

---

### Task 5.3: TenantProvisioningIntegrationTest erweitern

**Files:**
- Modify: `apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningIntegrationTest.java`

- [ ] **Step 1: Verifikation fuer tenant_user in shouldCompleteFullProvisioningFlow erweitern**

Nach den bestehenden Verify-Zeilen hinzufuegen:

```java
// Step 5: Verify tenant_user entry was created
var tenantUsers = tenantUserRepository.findAll();
assertThat(tenantUsers).hasSize(1);
var tenantUser = tenantUsers.get(0);
assertThat(tenantUser.getEmail()).isEqualTo("admin@acme.com");
assertThat(tenantUser.getStatus()).isEqualTo(UserStatus.INVITED);
assertThat(tenantUser.getInvitedBy()).isNull(); // System-Erstellung
```

Neues `@Autowired` Feld:
```java
@Autowired TenantUserRepository tenantUserRepository;
```

- [ ] **Step 2: Test ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.TenantProvisioningIntegrationTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningIntegrationTest.java
git commit -m "test: verify tenant_user creation in provisioning integration test"
```

---

### Task 5.4: UserActivationServiceTest

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/UserActivationServiceTest.java`

- [ ] **Step 1: Test-Klasse erstellen**

```java
package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.model.AuditInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class UserActivationServiceTest {

    @Mock TenantUserRepository userRepository;
    @InjectMocks UserActivationService activationService;

    private TenantUserEntity createEntity(UUID id, UserStatus status) {
        var entity = new TenantUserEntity();
        entity.setId(id);
        entity.setStatus(status);
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(UUID.randomUUID());
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(UUID.randomUUID());
        entity.setAuditInfo(auditInfo);
        return entity;
    }

    @Test
    void shouldActivateInvitedUserOnFirstLogin() {
        UUID userId = UUID.randomUUID();
        var entity = createEntity(userId, UserStatus.INVITED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));

        activationService.activateOnFirstLogin(userId);

        assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(entity);
    }

    @Test
    void shouldNotChangeAlreadyActiveUser() {
        UUID userId = UUID.randomUUID();
        var entity = createEntity(userId, UserStatus.ACTIVE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));

        activationService.activateOnFirstLogin(userId);

        assertThat(entity.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotActivateDeactivatedUser() {
        UUID userId = UUID.randomUUID();
        var entity = createEntity(userId, UserStatus.DEACTIVATED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));

        activationService.activateOnFirstLogin(userId);

        assertThat(entity.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldHandleNonExistentUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        activationService.activateOnFirstLogin(userId);

        verify(userRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.UserActivationServiceTest"`
Expected: 4 Tests PASS

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/identity/service/UserActivationServiceTest.java
git commit -m "test: add UserActivationServiceTest (INVITED->ACTIVE, no-ops, missing user)"
```

---

### Task 5.5: UserMapperTest

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/identity/mapper/UserMapperTest.java`

- [ ] **Step 1: Test-Klasse erstellen**

```java
package io.webcontify.identity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.identity.model.TenantUser;
import io.webcontify.identity.model.UserStatus;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.model.AuditInfo;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.OffsetDateTime;
import java.util.UUID;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void shouldMapEntityToDomain() {
        var entity = new TenantUserEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setEmail("test@test.com");
        entity.setFirstName("Max");
        entity.setLastName("Muster");
        entity.setStatus(UserStatus.ACTIVE);
        entity.setInvitedBy(UUID.randomUUID());
        var auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(UUID.randomUUID());
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(UUID.randomUUID());
        entity.setAuditInfo(auditInfo);

        TenantUser domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(entity.getId());
        assertThat(domain.getEmail()).isEqualTo("test@test.com");
        assertThat(domain.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(domain.getCreatedAt()).isEqualTo(auditInfo.getCreatedAt());
    }

    @Test
    void shouldMapDomainToResponse() {
        var domain = new TenantUser();
        domain.setId(UUID.randomUUID());
        domain.setEmail("test@test.com");
        domain.setFirstName("Max");
        domain.setLastName("Muster");
        domain.setStatus(UserStatus.INVITED);
        domain.setCreatedAt(OffsetDateTime.now());
        domain.setUpdatedAt(OffsetDateTime.now());

        var response = mapper.toResponse(domain);

        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getStatus().getValue()).isEqualTo("INVITED");
    }
}
```

- [ ] **Step 2: Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.mapper.UserMapperTest"`
Expected: 2 Tests PASS

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/identity/mapper/UserMapperTest.java
git commit -m "test: add UserMapperTest (entity->domain, domain->response)"
```

---

## Chunk 6: Frontend — Components + Pages

### Task 6.1: Zod Schema + useUserActions Hook

**Files:**
- Create: `apps/platform/src/features/users/schemas/invite-user.ts`
- Create: `apps/platform/src/features/users/hooks/use-user-actions.ts`

- [ ] **Step 1: Zod Schema erstellen**

```typescript
// apps/platform/src/features/users/schemas/invite-user.ts
import { z } from "zod";

export const inviteUserSchema = z.object({
  email: z.string().email("Ungueltige E-Mail-Adresse").max(255),
  firstName: z.string().min(1, "Vorname ist erforderlich").max(255),
  lastName: z.string().min(1, "Nachname ist erforderlich").max(255),
});

export type InviteUserFormValues = z.infer<typeof inviteUserSchema>;
```

- [ ] **Step 2: useUserActions Hook erstellen**

```typescript
// apps/platform/src/features/users/hooks/use-user-actions.ts
"use client";

import { useQueryClient } from "@tanstack/react-query";
import {
  useResendInvite,
  useUpdateUserStatus,
  useDeleteUser,
  getListUsersQueryKey,
  getGetUserQueryKey,
} from "@/generated/identity";
import { toast } from "sonner";

export function useUserActions() {
  const queryClient = useQueryClient();

  const invalidateUsers = () => {
    queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
  };

  const resendInvite = useResendInvite({
    mutation: {
      onSuccess: () => {
        toast.success("Einladung erneut gesendet");
        invalidateUsers();
      },
      onError: () => {
        toast.error("Fehler beim Senden der Einladung");
      },
    },
  });

  const updateStatus = useUpdateUserStatus({
    mutation: {
      onSuccess: (_data, variables) => {
        const status = variables.data.status;
        const message = status === "ACTIVE" ? "User aktiviert" : "User deaktiviert";
        toast.success(message);
        invalidateUsers();
        queryClient.invalidateQueries({
          queryKey: getGetUserQueryKey(variables.userId),
        });
      },
      onError: (error: any) => {
        const code = error?.data?.code;
        if (code === "user.last_admin_cannot_be_removed") {
          toast.error("Der letzte Admin kann nicht deaktiviert werden");
        } else if (code === "user.cannot_deactivate_self") {
          toast.error("Du kannst dich nicht selbst deaktivieren");
        } else {
          toast.error("Fehler beim Aendern des Status");
        }
      },
    },
  });

  const deleteUser = useDeleteUser({
    mutation: {
      onSuccess: () => {
        toast.success("User geloescht");
        invalidateUsers();
      },
      onError: (error: any) => {
        const code = error?.data?.code;
        if (code === "user.last_admin_cannot_be_removed") {
          toast.error("Der letzte Admin kann nicht geloescht werden");
        } else if (code === "user.cannot_delete_self") {
          toast.error("Du kannst dich nicht selbst loeschen");
        } else {
          toast.error("Fehler beim Loeschen des Users");
        }
      },
    },
  });

  return { resendInvite, updateStatus, deleteUser };
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/features/users/
git commit -m "feat: add invite-user Zod schema and useUserActions hook"
```

---

### Task 6.2: UserStatusBadge + UserActionsMenu Komponenten

**Files:**
- Create: `apps/platform/src/features/users/components/user-status-badge.tsx`
- Create: `apps/platform/src/features/users/components/user-actions-menu.tsx`

- [ ] **Step 1: UserStatusBadge erstellen**

```tsx
// apps/platform/src/features/users/components/user-status-badge.tsx
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { UserStatus } from "@/generated/identity";

const statusConfig: Record<UserStatus, { label: string; className: string }> = {
  INVITED: {
    label: "Eingeladen",
    className: "bg-amber-100 text-amber-800 border-amber-200 hover:bg-amber-100",
  },
  ACTIVE: {
    label: "Aktiv",
    className: "bg-emerald-100 text-emerald-800 border-emerald-200 hover:bg-emerald-100",
  },
  DEACTIVATED: {
    label: "Deaktiviert",
    className: "bg-stone-100 text-stone-600 border-stone-200 hover:bg-stone-100",
  },
};

interface UserStatusBadgeProps {
  status: UserStatus;
}

export function UserStatusBadge({ status }: UserStatusBadgeProps) {
  const config = statusConfig[status];
  return (
    <Badge variant="outline" className={cn(config.className)}>
      {config.label}
    </Badge>
  );
}
```

- [ ] **Step 2: UserActionsMenu erstellen**

```tsx
// apps/platform/src/features/users/components/user-actions-menu.tsx
"use client";

import { MoreHorizontal, Mail, UserX, UserCheck, Trash2, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { UserResponse } from "@/generated/identity";
import { useUserActions } from "../hooks/use-user-actions";

interface UserActionsMenuProps {
  user: UserResponse;
  onDeleteConfirm: (user: UserResponse) => void;
}

export function UserActionsMenu({ user, onDeleteConfirm }: UserActionsMenuProps) {
  const { resendInvite, updateStatus } = useUserActions();

  const handleResendInvite = () => {
    resendInvite.mutate({ userId: user.id });
  };

  const handleDeactivate = () => {
    updateStatus.mutate({ userId: user.id, data: { status: "DEACTIVATED" } });
  };

  const handleActivate = () => {
    updateStatus.mutate({ userId: user.id, data: { status: "ACTIVE" } });
  };

  const isLoading = resendInvite.isPending || updateStatus.isPending;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            variant="ghost"
            size="icon"
            aria-label={`Aktionen fuer ${user.firstName} ${user.lastName}`}
            disabled={isLoading}
          />
        }
      >
        {isLoading ? (
          <Loader2 className="size-4 animate-spin" />
        ) : (
          <MoreHorizontal className="size-4" />
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {user.status === "INVITED" && (
          <DropdownMenuItem onClick={handleResendInvite}>
            <Mail className="mr-2 size-4" />
            Einladung erneut senden
          </DropdownMenuItem>
        )}
        {(user.status === "INVITED" || user.status === "ACTIVE") && (
          <DropdownMenuItem onClick={handleDeactivate}>
            <UserX className="mr-2 size-4" />
            Deaktivieren
          </DropdownMenuItem>
        )}
        {user.status === "DEACTIVATED" && (
          <DropdownMenuItem onClick={handleActivate}>
            <UserCheck className="mr-2 size-4" />
            Aktivieren
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          className="text-destructive focus:text-destructive"
          onClick={() => onDeleteConfirm(user)}
        >
          <Trash2 className="mr-2 size-4" />
          Loeschen
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/features/users/components/user-status-badge.tsx
git add apps/platform/src/features/users/components/user-actions-menu.tsx
git commit -m "feat: add UserStatusBadge and UserActionsMenu components"
```

---

### Task 6.3: InviteUserDialog

**Files:**
- Create: `apps/platform/src/features/users/components/invite-user-dialog.tsx`

Referenz-Pattern: `apps/platform/src/features/tenants/components/create-tenant-dialog.tsx`

- [ ] **Step 1: InviteUserDialog erstellen**

```tsx
// apps/platform/src/features/users/components/invite-user-dialog.tsx
"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import {
  useInviteUser,
  getListUsersQueryKey,
  type ProblemDetail,
  type ValidationProblemDetail,
} from "@/generated/identity";
import {
  inviteUserSchema,
  type InviteUserFormValues,
} from "../schemas/invite-user";

interface InviteUserDialogProps {
  children: React.ReactNode;
}

export function InviteUserDialog({ children }: InviteUserDialogProps) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const inviteUser = useInviteUser();

  const form = useForm<InviteUserFormValues>({
    resolver: zodResolver(inviteUserSchema),
    defaultValues: {
      email: "",
      firstName: "",
      lastName: "",
    },
  });

  async function onSubmit(values: InviteUserFormValues) {
    try {
      await inviteUser.mutateAsync({ data: values });
      toast.success("Einladung gesendet");
      setOpen(false);
      form.reset();
      queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
    } catch (error: unknown) {
      const err = error as ValidationProblemDetail | ProblemDetail;
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as keyof InviteUserFormValues, {
              message: fieldError.message,
            });
          }
          // Focus erstes fehlerhaftes Feld
          const firstField = validationErr.errors[0]?.field;
          if (firstField) {
            form.setFocus(firstField as keyof InviteUserFormValues);
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("email", {
          message: "Diese E-Mail-Adresse ist bereits vergeben.",
        });
        form.setFocus("email");
        return;
      }
      toast.error("Fehler beim Senden der Einladung. Bitte versuche es erneut.");
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>User einladen</DialogTitle>
          <DialogDescription>
            Sende eine Einladung an einen neuen User.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    E-Mail <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      autoComplete="email"
                      placeholder="user@example.com"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="firstName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Vorname <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      autoComplete="given-name"
                      placeholder="Max"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="lastName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Nachname <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      autoComplete="family-name"
                      placeholder="Mustermann"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
              >
                Abbrechen
              </Button>
              <Button type="submit" aria-busy={inviteUser.isPending}>
                {inviteUser.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                Einladen
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/users/components/invite-user-dialog.tsx
git commit -m "feat: add InviteUserDialog component"
```

---

### Task 6.4: UserTable + UserTableSkeleton

**Files:**
- Create: `apps/platform/src/features/users/components/user-table.tsx`
- Create: `apps/platform/src/features/users/components/user-table-skeleton.tsx`

Referenz: `apps/platform/src/features/tenants/components/tenant-table.tsx`

- [ ] **Step 1: UserTableSkeleton erstellen**

```tsx
// apps/platform/src/features/users/components/user-table-skeleton.tsx
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";

export function UserTableSkeleton() {
  return (
    <Table aria-label="User-Liste">
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead className="hidden md:table-cell">E-Mail</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="hidden lg:table-cell">Eingeladen am</TableHead>
          <TableHead className="text-right">Aktionen</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {Array.from({ length: 5 }).map((_, i) => (
          <TableRow key={i}>
            <TableCell><Skeleton className="h-4 w-32" /></TableCell>
            <TableCell className="hidden md:table-cell"><Skeleton className="h-4 w-40" /></TableCell>
            <TableCell><Skeleton className="h-5 w-20 rounded-full" /></TableCell>
            <TableCell className="hidden lg:table-cell"><Skeleton className="h-4 w-24" /></TableCell>
            <TableCell className="text-right"><Skeleton className="ml-auto h-8 w-8" /></TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

- [ ] **Step 2: UserTable erstellen**

```tsx
// apps/platform/src/features/users/components/user-table.tsx
"use client";

import { useState } from "react";
import Link from "next/link";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import type { UserResponse } from "@/generated/identity";
import { UserStatusBadge } from "./user-status-badge";
import { UserActionsMenu } from "./user-actions-menu";
import { UserTableSkeleton } from "./user-table-skeleton";
import { useUserActions } from "../hooks/use-user-actions";
import { Button } from "@/components/ui/button";
import { InviteUserDialog } from "./invite-user-dialog";
import { UserPlus } from "lucide-react";

interface UserTableProps {
  users: UserResponse[];
  isLoading: boolean;
  isRefetching: boolean;
}

export function UserTable({ users, isLoading, isRefetching }: UserTableProps) {
  const [deleteTarget, setDeleteTarget] = useState<UserResponse | null>(null);
  const { deleteUser } = useUserActions();

  if (isLoading) {
    return <UserTableSkeleton />;
  }

  if (users.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">Noch keine User</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Laden Sie den ersten User ein.
        </p>
        <InviteUserDialog>
          <Button className="mt-4">
            <UserPlus className="mr-2 size-4" />
            Ersten User einladen
          </Button>
        </InviteUserDialog>
      </div>
    );
  }

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString("de-DE", {
      day: "2-digit", month: "2-digit", year: "numeric",
    });
  };

  const handleDeleteConfirm = () => {
    if (deleteTarget) {
      deleteUser.mutate({ userId: deleteTarget.id });
      setDeleteTarget(null);
    }
  };

  return (
    <>
      <div className="relative">
        {isRefetching && (
          <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-background/60">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        )}
        <Table aria-label="User-Liste">
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead className="hidden md:table-cell">E-Mail</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="hidden lg:table-cell">Eingeladen am</TableHead>
              <TableHead className="text-right">Aktionen</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id}>
                <TableCell>
                  <Link href={`/users/${user.id}`} className="font-medium hover:underline">
                    {user.firstName} {user.lastName}
                  </Link>
                  <p className="text-sm text-muted-foreground md:hidden">{user.email}</p>
                </TableCell>
                <TableCell className="hidden md:table-cell text-muted-foreground">
                  {user.email}
                </TableCell>
                <TableCell>
                  <UserStatusBadge status={user.status} />
                </TableCell>
                <TableCell className="hidden lg:table-cell text-muted-foreground">
                  {formatDate(user.createdAt)}
                </TableCell>
                <TableCell className="text-right">
                  <UserActionsMenu user={user} onDeleteConfirm={setDeleteTarget} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <AlertDialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>User loeschen?</AlertDialogTitle>
            <AlertDialogDescription>
              {deleteTarget?.firstName} {deleteTarget?.lastName} wird unwiderruflich geloescht. Diese Aktion kann nicht rueckgaengig gemacht werden.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDeleteConfirm}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Loeschen
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/features/users/components/user-table.tsx
git add apps/platform/src/features/users/components/user-table-skeleton.tsx
git commit -m "feat: add UserTable and UserTableSkeleton components"
```

---

### Task 6.5: User List Page

**Files:**
- Modify: `apps/platform/src/app/(workspace)/users/page.tsx`

- [ ] **Step 1: User List Page implementieren**

```tsx
// apps/platform/src/app/(workspace)/users/page.tsx
"use client";

import { UserPlus } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { useListUsers } from "@/generated/identity";
import { UserTable } from "@/features/users/components/user-table";
import { InviteUserDialog } from "@/features/users/components/invite-user-dialog";

export default function UsersPage() {
  const { data, isLoading, isRefetching } = useListUsers();

  const users = data?.data?.items ?? [];

  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Users" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Users</h1>
            <p className="text-sm text-muted-foreground">
              User im Workspace verwalten.
            </p>
          </div>
          <InviteUserDialog>
            <Button>
              <UserPlus className="mr-2 size-4" />
              User einladen
            </Button>
          </InviteUserDialog>
        </div>
        <UserTable
          users={users}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
```

Layout-Pattern: Gleich wie `apps/platform/src/app/(platform)/tenants/page.tsx` — Button im Content-Bereich neben h1, nicht in AppHeader (AppHeader hat kein `actions` Prop).

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/app/(workspace)/users/page.tsx
git commit -m "feat: implement User List page with table, invite dialog, and loading states"
```

---

### Task 6.6: User Detail Page

**Files:**
- Create: `apps/platform/src/app/(workspace)/users/[userId]/page.tsx`

- [ ] **Step 1: User Detail Page erstellen**

```tsx
// apps/platform/src/app/(workspace)/users/[userId]/page.tsx
"use client";

import { use, useState } from "react";
import { useRouter } from "next/navigation";
import { Mail, UserX, UserCheck, Trash2, Loader2 } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { useGetUser } from "@/generated/identity";
import { UserStatusBadge } from "@/features/users/components/user-status-badge";
import { useUserActions } from "@/features/users/hooks/use-user-actions";

export default function UserDetailPage({
  params,
}: {
  params: Promise<{ userId: string }>;
}) {
  const { userId } = use(params);
  const router = useRouter();
  const { data, isLoading } = useGetUser(userId);
  const { resendInvite, updateStatus, deleteUser } = useUserActions();
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);

  const user = data?.data;

  const handleDelete = () => {
    deleteUser.mutate(
      { userId },
      { onSuccess: () => router.push("/users") }
    );
    setShowDeleteDialog(false);
  };

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return "-";
    return new Date(dateStr).toLocaleDateString("de-DE", {
      day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
    });
  };

  if (isLoading) {
    return (
      <>
        <AppHeader breadcrumbs={[{ label: "Users", href: "/users" }, { label: "..." }]} />
        <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
          <Skeleton className="h-8 w-48" />
          <div className="space-y-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-6 w-64" />
            ))}
          </div>
        </div>
      </>
    );
  }

  if (!user) return null;

  return (
    <>
      <AppHeader
        breadcrumbs={[
          { label: "Users", href: "/users" },
          { label: `${user.firstName} ${user.lastName}` },
        ]}
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          {user.firstName} {user.lastName}
        </h1>

        {/* Profil-Sektion */}
        <section>
          <h2 className="text-lg font-semibold mb-4">Profil</h2>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-sm text-muted-foreground">Name</dt>
              <dd className="font-medium">{user.firstName} {user.lastName}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">E-Mail</dt>
              <dd className="font-medium">{user.email}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">Status</dt>
              <dd><UserStatusBadge status={user.status} /></dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">Eingeladen am</dt>
              <dd className="font-medium">{formatDate(user.createdAt)}</dd>
            </div>
            <div>
              <dt className="text-sm text-muted-foreground">Eingeladen von</dt>
              <dd className="font-medium">{user.invitedBy ?? "System"}</dd>
            </div>
          </dl>
        </section>

        {/* Aktionen-Sektion */}
        <section>
          <h2 className="text-lg font-semibold mb-4">Aktionen</h2>
          <div className="flex flex-wrap items-center gap-3">
            {user.status === "INVITED" && (
              <Button
                variant="outline"
                onClick={() => resendInvite.mutate({ userId })}
                disabled={resendInvite.isPending}
              >
                {resendInvite.isPending ? (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                ) : (
                  <Mail className="mr-2 size-4" />
                )}
                Einladung erneut senden
              </Button>
            )}
            {(user.status === "INVITED" || user.status === "ACTIVE") && (
              <Button
                variant="outline"
                onClick={() => updateStatus.mutate({ userId, data: { status: "DEACTIVATED" } })}
                disabled={updateStatus.isPending}
              >
                {updateStatus.isPending ? (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                ) : (
                  <UserX className="mr-2 size-4" />
                )}
                Deaktivieren
              </Button>
            )}
            {user.status === "DEACTIVATED" && (
              <Button
                variant="outline"
                onClick={() => updateStatus.mutate({ userId, data: { status: "ACTIVE" } })}
                disabled={updateStatus.isPending}
              >
                {updateStatus.isPending ? (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                ) : (
                  <UserCheck className="mr-2 size-4" />
                )}
                Aktivieren
              </Button>
            )}
            <div className="ml-auto">
              <Button
                variant="destructive"
                onClick={() => setShowDeleteDialog(true)}
                disabled={deleteUser.isPending}
              >
                {deleteUser.isPending ? (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                ) : (
                  <Trash2 className="mr-2 size-4" />
                )}
                Loeschen
              </Button>
            </div>
          </div>
        </section>

        {/* Rollen-Sektion */}
        <section>
          <h2 className="text-lg font-semibold mb-4">Rollen</h2>
          <Badge variant="outline">Admin</Badge>
          <p className="mt-2 text-sm text-muted-foreground">
            Rollen-Verwaltung kommt in einer spaeteren Version.
          </p>
        </section>
      </div>

      <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>User loeschen?</AlertDialogTitle>
            <AlertDialogDescription>
              {user.firstName} {user.lastName} wird unwiderruflich geloescht. Diese Aktion kann nicht rueckgaengig gemacht werden.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Abbrechen</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Loeschen
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/app/(workspace)/users/\\[userId\\]/page.tsx
git commit -m "feat: implement User Detail page with actions and role placeholder"
```

---

## Chunk 7: Frontend Tests + Abschluss

### Task 7.1: Frontend Tests (Vitest)

**Files:**
- Create: `apps/platform/src/features/users/components/__tests__/invite-user-dialog.test.tsx`
- Create: `apps/platform/src/features/users/components/__tests__/user-table.test.tsx`

- [ ] **Step 1: invite-user-dialog.test.tsx erstellen**

```tsx
// apps/platform/src/features/users/components/__tests__/invite-user-dialog.test.tsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { InviteUserDialog } from "../invite-user-dialog";

// Mock Orval hooks
vi.mock("@/generated/identity", () => ({
  useInviteUser: () => ({
    mutateAsync: vi.fn().mockResolvedValue({ data: { id: "123" }, status: 201 }),
    isPending: false,
  }),
  getListUsersQueryKey: () => ["listUsers"],
}));

// Mock react-query
vi.mock("@tanstack/react-query", async () => {
  const actual = await vi.importActual("@tanstack/react-query");
  return {
    ...actual,
    useQueryClient: () => ({
      invalidateQueries: vi.fn(),
    }),
  };
});

// Mock sonner
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

describe("InviteUserDialog", () => {
  it("should render form fields when opened", async () => {
    const user = userEvent.setup();
    render(
      <InviteUserDialog>
        <button>Open</button>
      </InviteUserDialog>
    );

    await user.click(screen.getByText("Open"));
    expect(screen.getByLabelText(/e-mail/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/vorname/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/nachname/i)).toBeInTheDocument();
  });

  it("should show validation errors for empty fields", async () => {
    const user = userEvent.setup();
    render(
      <InviteUserDialog>
        <button>Open</button>
      </InviteUserDialog>
    );

    await user.click(screen.getByText("Open"));
    await user.click(screen.getByText("Einladen"));

    await waitFor(() => {
      expect(screen.getAllByText(/erforderlich|ungueltig/i).length).toBeGreaterThan(0);
    });
  });
});
```

- [ ] **Step 2: user-table.test.tsx erstellen**

```tsx
// apps/platform/src/features/users/components/__tests__/user-table.test.tsx
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { UserTable } from "../user-table";
import type { UserResponse } from "@/generated/identity";

// Mock hooks
vi.mock("../hooks/use-user-actions", () => ({
  useUserActions: () => ({
    resendInvite: { mutate: vi.fn(), isPending: false },
    updateStatus: { mutate: vi.fn(), isPending: false },
    deleteUser: { mutate: vi.fn(), isPending: false },
  }),
}));

vi.mock("@/generated/identity", () => ({
  useInviteUser: () => ({ mutateAsync: vi.fn(), isPending: false }),
  getListUsersQueryKey: () => ["listUsers"],
}));

vi.mock("@tanstack/react-query", async () => {
  const actual = await vi.importActual("@tanstack/react-query");
  return { ...actual, useQueryClient: () => ({ invalidateQueries: vi.fn() }) };
});

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const mockUser: UserResponse = {
  id: "user-1",
  email: "max@test.com",
  firstName: "Max",
  lastName: "Muster",
  status: "ACTIVE",
  createdAt: "2026-03-15T10:00:00Z",
  updatedAt: "2026-03-15T10:00:00Z",
};

describe("UserTable", () => {
  it("should show empty state when no users", () => {
    render(<UserTable users={[]} isLoading={false} isRefetching={false} />);
    expect(screen.getByText("Noch keine User")).toBeInTheDocument();
  });

  it("should render user data", () => {
    render(<UserTable users={[mockUser]} isLoading={false} isRefetching={false} />);
    expect(screen.getByText("Max Muster")).toBeInTheDocument();
    expect(screen.getByText("Aktiv")).toBeInTheDocument();
  });

  it("should show skeleton when loading", () => {
    render(<UserTable users={[]} isLoading={true} isRefetching={false} />);
    // Skeleton rendert — kein "Noch keine User"
    expect(screen.queryByText("Noch keine User")).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Tests ausfuehren**

Run: `cd apps/platform && pnpm vitest run src/features/users/`
Expected: Alle Tests PASS

**Hinweis:** Die Mocks muessen moeglicherweise an die tatsaechlich generierten Orval-Typen angepasst werden. Die Test-Dateien sind als Ausgangspunkt gedacht und koennen nach der Orval-Generierung verfeinert werden.

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/features/users/components/__tests__/
git commit -m "test: add frontend tests for InviteUserDialog and UserTable"
```

---

### Task 7.2: PROGRESS.md aktualisieren

**Files:**
- Modify: `docs/PROGRESS.md`

- [ ] **Step 1: Phase 3b Eintrag hinzufuegen**

Unter Phase 3a Eintrag:

```markdown
### Phase 3b — User Management + Invite (2026-03-15)

**Fertig:**
- Flyway Migration: tenant_user, tenant_role + RLS
- OpenAPI: identity-api.yaml (6 Endpoints)
- Backend: UserService, UserController, UserMapper, UserActivationFilter
- TenantProvisioningListener erweitert (tenant_user + tenant_role bei Provisioning)
- KeycloakAdminService: enableUser/disableUser
- Frontend: User List Page, Detail Page, Invite Dialog, Status Badge, Actions Menu
- Tests: UserServiceTest (Unit), UserControllerIntegrationTest, Frontend Vitest

**Naechster Schritt:** Phase 3d — Rollen-CRUD + Permission-Matrix
```

- [ ] **Step 2: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md — Phase 3b complete"
```

---

## Abhaengigkeitsgraph

```
Chunk 1 (DB + OpenAPI + Build Config)
    |
    v
Chunk 2 (Domain Model + JPA Entities + Repos + Mapper)
    |
    v
Chunk 3 (Services: UserService + UserActivationService + Filter)
    |
    v
Chunk 4 (Controller + Provisioning-Erweiterung)
    |
    v
Chunk 5 (Backend Tests)           Chunk 6 (Frontend)
    |                                  |
    v                                  v
                  Chunk 7 (Frontend Tests + PROGRESS.md)
```

Chunks 5 und 6 koennen **parallel** ausgefuehrt werden (unabhaengig). Chunk 7 haengt von beiden ab.

## Review-Notizen

Folgende Punkte beachten waehrend der Implementierung:

1. **UserMapper.toResponse()**: Nach Orval/OpenAPI-Generierung pruefen ob `UserStatus.fromValue()` existiert. Falls nicht, `valueOf()` verwenden.
2. **UserControllerIntegrationTest**: Braucht einen existierenden Tenant in der DB fuer FK. Im `@BeforeEach` via `TenantRepository` anlegen.
3. **Pagination-Struktur**: `UserListResponse` hat flache Felder (page, size, totalElements, totalPages) — weicht vom `TenantListResponse` Pattern ab (nested `pageInfo`). Das ist konsistent mit der Spec, aber ggf. in zukuenftigen Phasen vereinheitlichen.
4. **Rollenzuordnung**: User↔Rolle laeuft ausschliesslich ueber OpenFGA (`role#assignee`). Keine lokale `tenant_user_role` Tabelle. `tenant_role` speichert nur Metadaten (Name, Beschreibung).
