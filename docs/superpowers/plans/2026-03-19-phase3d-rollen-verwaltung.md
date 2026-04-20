# Phase 3d: Rollen-Verwaltung — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rollen-CRUD + bidirektionale User-zu-Rolle-Zuweisung fuer Tenant Self-Service, mit vier System-Rollen (Admin, Editor, Viewer, Inviter) und Invite-Klon-Feature.

**Architecture:** OpenFGA ist Single Source of Truth fuer alle Zuweisungen. Die lokale `tenant_role` Tabelle (existiert bereits) speichert nur Metadaten. Kein Join-Table. RoleService orchestriert DB + OpenFGA. Replace-Semantik fuer Zuweisungen (PUT mit kompletter Liste, Backend berechnet Delta).

**Tech Stack:** Java 21, Spring Boot 4.0.3, Spring Modulith, OpenFGA SDK 0.7.2, MapStruct, JUnit 5 + Testcontainers, Next.js 16, React 19, React Query (Orval), Zod, React Hook Form, shadcn/ui

**Design Spec:** `docs/superpowers/specs/2026-03-18-phase3d-rollen-verwaltung-design.md`

---

## File Structure

### Backend — New Files
- `apps/backend/src/main/java/io/webcontify/identity/model/TenantRole.java` — Domain Model
- `apps/backend/src/main/java/io/webcontify/identity/model/RoleAssignee.java` — Record fuer zugewiesene User
- `apps/backend/src/main/java/io/webcontify/identity/model/RolePermissionPreview.java` — Record fuer Permission-Vorschau
- `apps/backend/src/main/java/io/webcontify/identity/mapper/RoleMapper.java` — MapStruct Entity<->Domain<->Response
- `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java` — CRUD + OpenFGA Orchestrierung
- `apps/backend/src/main/java/io/webcontify/identity/api/RoleController.java` — REST Controller (implements generated interface)
- `apps/backend/src/test/java/io/webcontify/identity/service/RoleServiceTest.java` — Unit Tests
- `apps/backend/src/test/java/io/webcontify/identity/api/RoleControllerIntegrationTest.java` — Integration Tests

### Backend — Modified Files
- `apps/backend/src/main/resources/openfga/model.json` — `tenant_config` Type hinzufuegen
- `apps/backend/src/main/java/io/webcontify/identity/repository/TenantRoleRepository.java` — Query Methods
- `apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java` — System-Rollen + Singleton-Ressourcen
- `apps/backend/src/main/java/io/webcontify/identity/service/UserService.java` — setRoles, getRoles, invite erweitern, guardLastAdmin fix
- `apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java` — readTuples Methode
- `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaCheckRequest.java` — userSubject String-Feld hinzufuegen
- `openapi/identity-api.yaml` — Role Endpoints + Schemas

### Frontend — New Files
- `apps/platform/src/features/roles/components/role-table.tsx`
- `apps/platform/src/features/roles/components/create-role-dialog.tsx`
- `apps/platform/src/features/roles/components/edit-role-form.tsx`
- `apps/platform/src/features/roles/components/role-assignees-table.tsx`
- `apps/platform/src/features/roles/components/add-assignees-dialog.tsx`
- `apps/platform/src/features/roles/components/role-permissions-preview.tsx`
- `apps/platform/src/features/roles/hooks/use-role-actions.ts`
- `apps/platform/src/features/roles/schemas/role.ts`
- `apps/platform/src/app/(workspace)/roles/[roleId]/page.tsx`
- `apps/platform/src/features/roles/components/__tests__/role-table.test.tsx`
- `apps/platform/src/features/roles/components/__tests__/create-role-dialog.test.tsx`

### Frontend — Modified Files
- `apps/platform/src/app/(workspace)/roles/page.tsx` — Placeholder ersetzen
- `apps/platform/src/features/users/components/invite-user-dialog.tsx` — copyRolesFromUserId Feld
- `apps/platform/src/features/users/schemas/invite-user.ts` — copyRolesFromUserId
- `apps/platform/src/features/users/hooks/use-user-actions.ts` — setUserRoles, getUserRoles
- `apps/platform/src/features/users/components/user-table.tsx` — "Rollen klonen" Action

---

## Task 1: OpenFGA Model — tenant_config Type

**Files:**
- Modify: `apps/backend/src/main/resources/openfga/model.json:289` (vor der letzten `]`)

- [ ] **Step 1: tenant_config Type Definition hinzufuegen**

In `model.json`, nach dem `role_management` Type (Zeile 289), vor dem schliessenden `]` folgenden Type einfuegen:

```json
,
{
  "type": "tenant_config",
  "relations": {
    "tenant": {
      "this": {}
    },
    "editor": {
      "union": {
        "child": [
          { "this": {} },
          {
            "tupleToUserset": {
              "tupleset": { "object": "", "relation": "tenant" },
              "computedUserset": { "object": "", "relation": "admin" }
            }
          }
        ]
      }
    },
    "reader": {
      "union": {
        "child": [
          { "this": {} },
          { "computedUserset": { "object": "", "relation": "editor" } },
          {
            "tupleToUserset": {
              "tupleset": { "object": "", "relation": "tenant" },
              "computedUserset": { "object": "", "relation": "admin" }
            }
          }
        ]
      }
    }
  },
  "metadata": {
    "relations": {
      "tenant": {
        "directly_related_user_types": [{ "type": "tenant" }]
      },
      "editor": {
        "directly_related_user_types": [
          { "type": "user" },
          { "type": "role", "relation": "assignee" }
        ]
      },
      "reader": {
        "directly_related_user_types": [
          { "type": "user" },
          { "type": "role", "relation": "assignee" }
        ]
      }
    }
  }
}
```

- [ ] **Step 2: Bestehende Tests laufen lassen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.OpenFgaServiceIntegrationTest"`
Expected: PASS (Model wird bei Store-Erstellung deployed)

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/resources/openfga/model.json
git commit -m "feat: add tenant_config type to OpenFGA authorization model"
```

---

## Task 2: OpenAPI Spec — Role Endpoints + Schemas

**Files:**
- Modify: `openapi/identity-api.yaml`

- [ ] **Step 1: TenantRoles Tag + Role Schemas hinzufuegen**

In `identity-api.yaml`, neuen Tag `TenantRoles` und neuen Tag `TenantUserRoles` unter `tags:` hinzufuegen. Dann alle Role-Schemas unter `components/schemas:` ergaenzen:

```yaml
tags:
  - name: TenantUsers
    description: Tenant User Management
  - name: TenantRoles
    description: Tenant Role Management
  - name: TenantUserRoles
    description: User-Role Assignment
```

Schemas (unter `components/schemas:`):

```yaml
    CreateRoleRequest:
      type: object
      required:
        - name
      properties:
        name:
          type: string
          minLength: 1
          maxLength: 255
        description:
          type: string
          maxLength: 1000

    UpdateRoleRequest:
      type: object
      required:
        - name
      properties:
        name:
          type: string
          minLength: 1
          maxLength: 255
        description:
          type: string
          maxLength: 1000

    RoleResponse:
      type: object
      required:
        - id
        - name
        - systemRole
        - assigneeCount
        - createdAt
        - updatedAt
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        description:
          type: string
          nullable: true
        systemRole:
          type: boolean
        assigneeCount:
          type: integer
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    RoleDetailResponse:
      type: object
      required:
        - id
        - name
        - systemRole
        - assignees
        - permissions
        - createdAt
        - updatedAt
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        description:
          type: string
          nullable: true
        systemRole:
          type: boolean
        assignees:
          type: array
          items:
            $ref: '#/components/schemas/RoleAssigneeResponse'
        permissions:
          type: array
          items:
            $ref: '#/components/schemas/RolePermissionPreviewResponse'
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    RoleAssigneeResponse:
      type: object
      required:
        - userId
        - email
        - firstName
        - lastName
      properties:
        userId:
          type: string
          format: uuid
        email:
          type: string
        firstName:
          type: string
        lastName:
          type: string

    RolePermissionPreviewResponse:
      type: object
      required:
        - resourceType
        - actions
      properties:
        resourceType:
          type: string
        actions:
          type: array
          items:
            type: string

    RoleListResponse:
      type: object
      required:
        - roles
      properties:
        roles:
          type: array
          items:
            $ref: '#/components/schemas/RoleResponse'

    SetRoleAssigneesRequest:
      type: object
      required:
        - userIds
      properties:
        userIds:
          type: array
          items:
            type: string
            format: uuid
          maxItems: 100

    SetUserRolesRequest:
      type: object
      required:
        - roleIds
      properties:
        roleIds:
          type: array
          items:
            type: string
            format: uuid
          maxItems: 50

    UserRolesResponse:
      type: object
      required:
        - roles
      properties:
        roles:
          type: array
          items:
            $ref: '#/components/schemas/RoleResponse'
```

- [ ] **Step 2: Role CRUD Paths hinzufuegen**

Neue Paths in `identity-api.yaml`:

```yaml
  /api/v1/tenant/roles:
    post:
      operationId: createRole
      summary: Create a new custom role
      tags:
        - TenantRoles
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateRoleRequest'
      responses:
        '201':
          description: Role created
          headers:
            Location:
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RoleResponse'
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
          description: Role name already exists
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
    get:
      operationId: listRoles
      summary: List all roles in the tenant
      tags:
        - TenantRoles
      responses:
        '200':
          description: Role list
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RoleListResponse'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden

  /api/v1/tenant/roles/{roleId}:
    get:
      operationId: getRole
      summary: Get role details with assignees and permission preview
      tags:
        - TenantRoles
      parameters:
        - name: roleId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Role details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RoleDetailResponse'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '404':
          description: Role not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
    put:
      operationId: updateRole
      summary: Update role name and description (custom roles only)
      tags:
        - TenantRoles
      parameters:
        - name: roleId
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
              $ref: '#/components/schemas/UpdateRoleRequest'
      responses:
        '200':
          description: Role updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RoleResponse'
        '400':
          description: Cannot update system role
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '404':
          description: Role not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '409':
          description: Role name already exists
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
    delete:
      operationId: deleteRole
      summary: Delete a custom role (must have no assignees)
      tags:
        - TenantRoles
      parameters:
        - name: roleId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Role deleted
        '400':
          description: Cannot delete system role
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '404':
          description: Role not found
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '409':
          description: Role still has assignees
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/tenant/roles/{roleId}/assignees:
    put:
      operationId: setRoleAssignees
      summary: Set the complete list of users assigned to a role (replace)
      tags:
        - TenantRoles
      parameters:
        - name: roleId
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
              $ref: '#/components/schemas/SetRoleAssigneesRequest'
      responses:
        '200':
          description: Assignees updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RoleDetailResponse'
        '400':
          description: Invalid user IDs
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '409':
          description: Cannot remove last admin assignee
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'

  /api/v1/tenant/users/{userId}/roles:
    get:
      operationId: getUserRoles
      summary: Get all roles assigned to a user
      tags:
        - TenantUserRoles
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: User roles
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserRolesResponse'
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
    put:
      operationId: setUserRoles
      summary: Set the complete list of roles for a user (replace)
      tags:
        - TenantUserRoles
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
              $ref: '#/components/schemas/SetUserRolesRequest'
      responses:
        '200':
          description: User roles updated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserRolesResponse'
        '400':
          description: Invalid role IDs
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
        '401':
          description: Unauthorized
        '403':
          description: Forbidden
        '409':
          description: Cannot remove last admin assignee
          content:
            application/json:
              schema:
                $ref: './common-schemas.yaml#/components/schemas/ProblemDetail'
```

- [ ] **Step 3: InviteUserRequest um copyRolesFromUserId erweitern**

In `InviteUserRequest` Schema ein optionales Feld ergaenzen:

```yaml
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
        copyRolesFromUserId:
          type: string
          format: uuid
          description: Optional user ID to copy roles from
```

- [ ] **Step 4: Backend Code Generation ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate`
Expected: Neue Interfaces `TenantRolesApi`, `TenantUserRolesApi` generiert

- [ ] **Step 5: Commit**

```bash
git add openapi/identity-api.yaml apps/backend/src/main/java/io/webcontify/generated/
git commit -m "feat: add role management endpoints to OpenAPI spec"
```

---

## Task 3: OpenFgaService — readTuples + checkForSubject Methoden

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaReadTuplesRequest.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaCheckRequest.java` — optionales `userSubject` Feld
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/service/OpenFgaServiceIntegrationTest.java`

**Wichtig:** `getPermissionPreview` braucht Checks mit `role:{roleId}#assignee` als Subject statt `user:{userId}`. Die bestehende `check()` Methode baut nur `"user:" + userId`. Loesung: `OpenFgaCheckRequest` um ein optionales `userSubject` String-Feld erweitern. Wenn gesetzt, wird dieser Wert direkt als Subject verwendet statt `"user:" + userId`.

```java
// OpenFgaCheckRequest.java — erweitert um userSubject
public record OpenFgaCheckRequest(
    String storeId, UUID userId, String action,
    String resourceType, UUID resourceId,
    String userSubject  // optional, z.B. "role:abc#assignee"
) {
    // Bestehender Konstruktor bleibt kompatibel
    public OpenFgaCheckRequest(String storeId, UUID userId, String action, String resourceType, UUID resourceId) {
        this(storeId, userId, action, resourceType, resourceId, null);
    }
}
```

In `OpenFgaService.check()`: `userSubject` verwenden wenn gesetzt, sonst `"user:" + userId`:
```java
var subject = request.userSubject() != null ? request.userSubject() : "user:" + request.userId();
var sdkRequest = new ClientCheckRequest()
    .user(subject)
    .relation(request.action())
    ._object(object);
```

- [ ] **Step 1: Request Record erstellen**

```java
// OpenFgaReadTuplesRequest.java
package io.webcontify.identity.model;

public record OpenFgaReadTuplesRequest(String storeId, String objectType, String objectId, String relation) {}
```

- [ ] **Step 2: Failing Integration Test schreiben**

In `OpenFgaServiceIntegrationTest.java` neuen Test hinzufuegen:

```java
@Test
void shouldReadTuplesForObject() {
    var storeResponse = openFgaService.createStore(new CreateStoreRequest("test-read-tuples"));
    var storeId = storeResponse.storeId();

    // Write two assignee tuples
    var userId1 = UUID.randomUUID();
    var userId2 = UUID.randomUUID();
    var roleId = UUID.randomUUID();
    openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
        new RelationshipTuple("user:" + userId1, "assignee", "role:" + roleId),
        new RelationshipTuple("user:" + userId2, "assignee", "role:" + roleId)
    )));

    // Read tuples
    var tuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
        storeId, "role", roleId.toString(), "assignee"));

    assertThat(tuples).hasSize(2);
    assertThat(tuples).extracting(RelationshipTuple::user)
        .containsExactlyInAnyOrder("user:" + userId1, "user:" + userId2);
}
```

- [ ] **Step 3: Test ausfuehren — muss fehlschlagen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.OpenFgaServiceIntegrationTest.shouldReadTuplesForObject"`
Expected: FAIL — `readTuples` Methode existiert nicht

- [ ] **Step 4: readTuples Methode implementieren**

In `OpenFgaService.java` nach der `listObjects` Methode:

```java
// TODO: OpenFGA read paginiert (Standard 100 Tupel). Fuer Phase 3d ausreichend (maxItems: 100).
public List<RelationshipTuple> readTuples(OpenFgaReadTuplesRequest request) {
    try {
        var tupleKey = new ClientTupleKeyWithoutCondition()
                .relation(request.relation())
                ._object(request.objectType() + ":" + request.objectId());
        var sdkRequest = new ClientReadRequest().tupleKey(tupleKey);
        client.setStoreId(request.storeId());
        var response = client.read(sdkRequest).get();
        if (response.getTuples() == null) {
            return List.of();
        }
        return response.getTuples().stream()
                .map(t -> new RelationshipTuple(
                        t.getKey().getUser(),
                        t.getKey().getRelation(),
                        t.getKey().getObject()))
                .toList();
    } catch (Exception e) {
        throw new RuntimeException("OpenFGA readTuples failed", e);
    }
}
```

Imports hinzufuegen:

```java
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import java.util.List;
```

- [ ] **Step 5: Test ausfuehren — muss gruen sein**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.OpenFgaServiceIntegrationTest.shouldReadTuplesForObject"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaReadTuplesRequest.java \
       apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java \
       apps/backend/src/test/java/io/webcontify/identity/service/OpenFgaServiceIntegrationTest.java
git commit -m "feat: add readTuples method to OpenFgaService"
```

---

## Task 4: Domain Model + Mapper + Repository Queries

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/TenantRole.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/RoleAssignee.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/RolePermissionPreview.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/mapper/RoleMapper.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/repository/TenantRoleRepository.java`

- [ ] **Step 1: Domain Model Records erstellen**

```java
// TenantRole.java
package io.webcontify.identity.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class TenantRole {
    private UUID id;
    private UUID tenantId;
    private String name;
    private String description;
    private boolean systemRole;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private OffsetDateTime updatedAt;
    private UUID updatedBy;

    // Getter + Setter (gleiches Pattern wie TenantUser.java)
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystemRole() { return systemRole; }
    public void setSystemRole(boolean systemRole) { this.systemRole = systemRole; }
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

```java
// RoleAssignee.java
package io.webcontify.identity.model;

import java.util.UUID;

public record RoleAssignee(UUID userId, String email, String firstName, String lastName) {}
```

```java
// RolePermissionPreview.java
package io.webcontify.identity.model;

import java.util.List;

public record RolePermissionPreview(String resourceType, List<String> actions) {}
```

- [ ] **Step 2: RoleMapper erstellen**

```java
// RoleMapper.java
package io.webcontify.identity.mapper;

import io.webcontify.identity.model.TenantRole;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(source = "auditInfo.createdAt", target = "createdAt")
    @Mapping(source = "auditInfo.createdBy", target = "createdBy")
    @Mapping(source = "auditInfo.updatedAt", target = "updatedAt")
    @Mapping(source = "auditInfo.updatedBy", target = "updatedBy")
    TenantRole toDomain(TenantRoleEntity entity);

    @Mapping(target = "assigneeCount", ignore = true)
    io.webcontify.generated.model.RoleResponse toResponseInternal(TenantRole domain);

    default io.webcontify.generated.model.RoleResponse toResponse(TenantRole domain, int assigneeCount) {
        var response = toResponseInternal(domain);
        response.setAssigneeCount(assigneeCount);
        return response;
    }
}
```

Hinweis: `toResponse` als `default`-Methode, da MapStruct den `assigneeCount` nicht aus `TenantRole` mappen kann (kommt aus OpenFGA).

- [ ] **Step 3: Repository Query Methods hinzufuegen**

In `TenantRoleRepository.java`:

```java
package io.webcontify.identity.repository;

import io.webcontify.identity.repository.entity.TenantRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRoleEntity, UUID> {

    @Query("SELECT r FROM TenantRoleEntity r WHERE r.auditInfo.deletedAt IS NULL")
    List<TenantRoleEntity> findAllActive();

    @Query("SELECT r FROM TenantRoleEntity r WHERE r.id = :id AND r.auditInfo.deletedAt IS NULL")
    Optional<TenantRoleEntity> findActiveById(UUID id);

    @Query("SELECT r FROM TenantRoleEntity r WHERE r.name = :name AND r.auditInfo.deletedAt IS NULL")
    Optional<TenantRoleEntity> findActiveByName(String name);

    @Query("SELECT r FROM TenantRoleEntity r WHERE r.id IN :ids AND r.auditInfo.deletedAt IS NULL")
    List<TenantRoleEntity> findAllActiveByIds(List<UUID> ids);
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/TenantRole.java \
       apps/backend/src/main/java/io/webcontify/identity/model/RoleAssignee.java \
       apps/backend/src/main/java/io/webcontify/identity/model/RolePermissionPreview.java \
       apps/backend/src/main/java/io/webcontify/identity/mapper/RoleMapper.java \
       apps/backend/src/main/java/io/webcontify/identity/repository/TenantRoleRepository.java
git commit -m "feat: add role domain model, mapper, and repository queries"
```

---

## Task 5: RoleService — CRUD + OpenFGA Orchestrierung (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/RoleServiceTest.java`

- [ ] **Step 1: RoleServiceTest — Grundgeruest + createRole Test**

```java
package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.identity.mapper.RoleMapper;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantRoleRepository;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock TenantRoleRepository roleRepository;
    @Mock TenantUserRepository userRepository;
    @Mock OpenFgaService openFgaService;
    @Mock PermissionService permissionService;
    @Mock TenantInfrastructureLookupService lookupService;
    @Mock RoleMapper roleMapper;

    @InjectMocks RoleService roleService;

    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        var tenantInfo = new TenantInformation(TENANT_ID, false);
        var auth = new TenantAwareAuthentication(tenantInfo, USER_ID.toString(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        lenient().when(lookupService.getInfrastructure(TENANT_ID))
            .thenReturn(new TenantInfrastructureInfo("test-store", "test-realm"));
    }

    @Nested
    class Create {
        @Test
        void shouldCreateCustomRole() {
            when(roleRepository.findActiveByName("Editor")).thenReturn(Optional.empty());
            when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            var expectedDomain = new TenantRole();
            expectedDomain.setName("Editor");
            when(roleMapper.toDomain(any(TenantRoleEntity.class))).thenReturn(expectedDomain);

            var result = roleService.create("Editor", "Kann Inhalte bearbeiten");

            assertThat(result.getName()).isEqualTo("Editor");
            verify(roleRepository).save(any());
            verify(openFgaService).write(any()); // role#tenant tuple
        }

        @Test
        void shouldThrowConflictWhenNameExists() {
            when(roleRepository.findActiveByName("Admin")).thenReturn(Optional.of(new TenantRoleEntity()));

            assertThatThrownBy(() -> roleService.create("Admin", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("role.name_already_exists");
        }
    }

    @Nested
    class Update {
        @Test
        void shouldThrowWhenUpdatingSystemRole() {
            var entity = new TenantRoleEntity();
            entity.setSystemRole(true);
            when(roleRepository.findActiveById(any())).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> roleService.update(UUID.randomUUID(), "New Name", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("role.system_role_cannot_be_modified");
        }
    }

    @Nested
    class Delete {
        @Test
        void shouldThrowWhenDeletingSystemRole() {
            var entity = new TenantRoleEntity();
            entity.setSystemRole(true);
            when(roleRepository.findActiveById(any())).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> roleService.delete(UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("role.system_role_cannot_be_deleted");
        }

        @Test
        void shouldThrowWhenRoleHasAssignees() {
            var entity = new TenantRoleEntity();
            entity.setId(UUID.randomUUID());
            entity.setSystemRole(false);
            when(roleRepository.findActiveById(entity.getId())).thenReturn(Optional.of(entity));
            when(lookupService.getInfrastructure(TENANT_ID))
                .thenReturn(new TenantInfrastructureInfo("test-store", "test-realm"));
            when(openFgaService.readTuples(any()))
                .thenReturn(List.of(new RelationshipTuple("user:x", "assignee", "role:" + entity.getId())));

            assertThatThrownBy(() -> roleService.delete(entity.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("role.has_assignees");
        }
    }
}
```

- [ ] **Step 2: Test ausfuehren — muss fehlschlagen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.RoleServiceTest"`
Expected: FAIL — RoleService existiert nicht

- [ ] **Step 3: RoleService implementieren**

```java
package io.webcontify.identity.service;

import com.fasterxml.uuid.Generators;
import io.webcontify.identity.mapper.RoleMapper;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantRoleRepository;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private static final String ADMIN_ROLE_NAME = "Admin";
    private static final List<String> PERMISSION_RESOURCE_TYPES =
            List.of("user_management", "role_management", "tenant_config");
    private static final Map<String, List<String>> RESOURCE_ACTIONS = Map.of(
            "user_management", List.of("inviter", "editor", "deleter", "reader"),
            "role_management", List.of("creator", "editor", "deleter", "reader"),
            "tenant_config", List.of("editor", "reader")
    );

    private final TenantRoleRepository roleRepository;
    private final TenantUserRepository userRepository;
    private final OpenFgaService openFgaService;
    private final PermissionService permissionService;
    private final TenantInfrastructureLookupService lookupService;
    private final RoleMapper roleMapper;

    public RoleService(TenantRoleRepository roleRepository,
                       TenantUserRepository userRepository,
                       OpenFgaService openFgaService,
                       PermissionService permissionService,
                       TenantInfrastructureLookupService lookupService,
                       RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.openFgaService = openFgaService;
        this.permissionService = permissionService;
        this.lookupService = lookupService;
        this.roleMapper = roleMapper;
    }

    @Transactional
    public TenantRole create(String name, String description) {
        roleRepository.findActiveByName(name).ifPresent(existing -> {
            throw new ConflictException("role.name_already_exists");
        });

        var tenantInfo = TenantContext.getTenantInformation();
        var entity = new TenantRoleEntity();
        entity.setId(Generators.timeBasedEpochGenerator().generate());
        entity.setTenantId(tenantInfo.tenantId());
        entity.setName(name);
        entity.setDescription(description);
        entity.setSystemRole(false);
        entity = roleRepository.save(entity);

        // OpenFGA: role belongs to tenant
        var storeId = lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
            new RelationshipTuple("tenant:" + tenantInfo.tenantId(), "tenant", "role:" + entity.getId())
        )));

        log.info("Role created: id={}, name={}", entity.getId(), name);
        return roleMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public List<TenantRole> list() {
        return roleRepository.findAllActive().stream()
                .map(roleMapper::toDomain)
                .toList();
    }

    public int getAssigneeCount(UUID roleId) {
        var tenantInfo = TenantContext.getTenantInformation();
        var storeId = lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();
        var tuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                storeId, "role", roleId.toString(), "assignee"));
        return tuples.size();
    }

    @Transactional(readOnly = true)
    public TenantRole getById(UUID roleId) {
        return roleRepository.findActiveById(roleId)
                .map(roleMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("role.not_found"));
    }

    public List<RoleAssignee> getAssignees(UUID roleId) {
        var tenantInfo = TenantContext.getTenantInformation();
        var storeId = lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();
        var tuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                storeId, "role", roleId.toString(), "assignee"));

        var userIds = tuples.stream()
                .map(t -> UUID.fromString(t.user().replace("user:", "")))
                .toList();

        if (userIds.isEmpty()) return List.of();

        return userRepository.findAllById(userIds).stream()
                .map(u -> new RoleAssignee(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName()))
                .toList();
    }

    public List<RolePermissionPreview> getPermissionPreview(UUID roleId) {
        var tenantInfo = TenantContext.getTenantInformation();
        var storeId = lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();
        var roleSubject = "role:" + roleId + "#assignee";
        var result = new ArrayList<RolePermissionPreview>();

        for (var resourceType : PERMISSION_RESOURCE_TYPES) {
            var actions = RESOURCE_ACTIONS.get(resourceType);
            var grantedActions = new ArrayList<String>();
            for (var action : actions) {
                var allowed = openFgaService.check(new OpenFgaCheckRequest(
                    storeId, null, action, resourceType, tenantInfo.tenantId(), roleSubject));
                if (allowed) {
                    grantedActions.add(action);
                }
            }
            if (!grantedActions.isEmpty()) {
                result.add(new RolePermissionPreview(resourceType, grantedActions));
            }
        }
        return result;
    }

    @Transactional
    public TenantRole update(UUID roleId, String name, String description) {
        var entity = roleRepository.findActiveById(roleId)
                .orElseThrow(() -> new NotFoundException("role.not_found"));

        if (entity.isSystemRole()) {
            throw new BadRequestException("role.system_role_cannot_be_modified");
        }

        // Name uniqueness check (if name changed)
        if (!entity.getName().equals(name)) {
            roleRepository.findActiveByName(name).ifPresent(existing -> {
                throw new ConflictException("role.name_already_exists");
            });
        }

        entity.setName(name);
        entity.setDescription(description);
        entity = roleRepository.save(entity);

        log.info("Role updated: id={}, name={}", roleId, name);
        return roleMapper.toDomain(entity);
    }

    @Transactional
    public void delete(UUID roleId) {
        var entity = roleRepository.findActiveById(roleId)
                .orElseThrow(() -> new NotFoundException("role.not_found"));

        if (entity.isSystemRole()) {
            throw new BadRequestException("role.system_role_cannot_be_deleted");
        }

        var tenantInfo = TenantContext.getTenantInformation();
        var storeId = lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();

        // Check no assignees
        var assigneeTuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                storeId, "role", roleId.toString(), "assignee"));
        if (!assigneeTuples.isEmpty()) {
            throw new ConflictException("role.has_assignees");
        }

        // Soft delete
        entity.getAuditInfo().setDeletedAt(java.time.OffsetDateTime.now());
        entity.getAuditInfo().setDeletedBy(resolveCurrentUserId());
        roleRepository.save(entity);

        // Cleanup OpenFGA: tenant relation + any permission tuples
        var tuplesToDelete = new ArrayList<RelationshipTuple>();
        tuplesToDelete.add(new RelationshipTuple(
                "tenant:" + tenantInfo.tenantId(), "tenant", "role:" + roleId));

        // Delete permission tuples for this role across all resource types
        for (var resourceType : PERMISSION_RESOURCE_TYPES) {
            for (var action : RESOURCE_ACTIONS.get(resourceType)) {
                tuplesToDelete.add(new RelationshipTuple(
                        "role:" + roleId + "#assignee", action,
                        resourceType + ":" + tenantInfo.tenantId()));
            }
        }

        try {
            openFgaService.delete(new OpenFgaDeleteRequest(storeId, tuplesToDelete));
        } catch (Exception e) {
            log.warn("Some OpenFGA tuples may not have existed during role cleanup roleId={}", roleId, e);
        }

        log.info("Role deleted: id={}", roleId);
    }

    public void setAssignees(UUID roleId, List<UUID> userIds) {
        var entity = roleRepository.findActiveById(roleId)
                .orElseThrow(() -> new NotFoundException("role.not_found"));

        // Validate all userIds exist in tenant
        var existingUsers = userRepository.findAllById(userIds);
        if (existingUsers.size() != userIds.size()) {
            throw new BadRequestException("role.assignee_not_found");
        }

        var tenantInfo = TenantContext.getTenantInformation();
        var storeId = lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();

        // Read current assignees
        var currentTuples = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                storeId, "role", roleId.toString(), "assignee"));
        var currentUserIds = currentTuples.stream()
                .map(t -> UUID.fromString(t.user().replace("user:", "")))
                .collect(Collectors.toSet());
        var targetUserIds = new HashSet<>(userIds);

        // Delta
        var toAdd = targetUserIds.stream().filter(id -> !currentUserIds.contains(id)).toList();
        var toRemove = currentUserIds.stream().filter(id -> !targetUserIds.contains(id)).toList();

        // Last admin guard
        if (entity.getName().equals(ADMIN_ROLE_NAME) && entity.isSystemRole()) {
            guardLastAdminAssignment(roleId, toRemove, currentUserIds, storeId);
        }

        // Write new tuples
        if (!toAdd.isEmpty()) {
            var addTuples = toAdd.stream()
                    .map(uid -> new RelationshipTuple("user:" + uid, "assignee", "role:" + roleId))
                    .toList();
            openFgaService.write(new OpenFgaWriteRequest(storeId, addTuples));
        }

        // Delete old tuples
        if (!toRemove.isEmpty()) {
            var removeTuples = toRemove.stream()
                    .map(uid -> new RelationshipTuple("user:" + uid, "assignee", "role:" + roleId))
                    .toList();
            openFgaService.delete(new OpenFgaDeleteRequest(storeId, removeTuples));

            // Compensating transaction: verify admin still has assignees
            if (entity.getName().equals(ADMIN_ROLE_NAME) && entity.isSystemRole()) {
                var remaining = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                        storeId, "role", roleId.toString(), "assignee"));
                if (remaining.isEmpty()) {
                    // Rollback
                    var rollbackTuples = toRemove.stream()
                            .map(uid -> new RelationshipTuple("user:" + uid, "assignee", "role:" + roleId))
                            .toList();
                    openFgaService.write(new OpenFgaWriteRequest(storeId, rollbackTuples));
                    throw new ConflictException("role.last_admin_cannot_be_removed");
                }
            }
        }

        log.info("Role assignees updated: roleId={}, added={}, removed={}", roleId, toAdd.size(), toRemove.size());
    }

    private void guardLastAdminAssignment(UUID roleId, List<UUID> toRemove, Set<UUID> currentUserIds, String storeId) {
        int remainingCount = currentUserIds.size() - toRemove.size();
        if (remainingCount < 1) {
            throw new ConflictException("role.last_admin_cannot_be_removed");
        }
    }

    private UUID resolveCurrentUserId() {
        var authentication = (io.webcontify.shared.security.TenantAwareAuthentication)
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) authentication.getPrincipal());
    }
}
```

- [ ] **Step 4: Tests ausfuehren — muessen gruen sein**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.RoleServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java \
       apps/backend/src/test/java/io/webcontify/identity/service/RoleServiceTest.java
git commit -m "feat: add RoleService with CRUD and OpenFGA orchestration (TDD)"
```

---

## Task 6: RoleController + Integration Tests

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/api/RoleController.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/api/RoleControllerIntegrationTest.java`

- [ ] **Step 1: Integration Test Grundgeruest schreiben**

```java
package io.webcontify.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantRoleRepository;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import io.webcontify.identity.service.*;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class RoleControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean KeycloakAdminService keycloakAdminService;
    @MockitoBean OpenFgaService openFgaService;
    @MockitoBean PermissionService permissionService;
    @MockitoBean FileStorageService fileStorageService;
    @MockitoBean TenantInfrastructureLookupService lookupService;

    @Autowired MockMvc mockMvc;
    @Autowired TenantRoleRepository roleRepository;
    @Autowired TenantUserRepository userRepository;
    @Autowired TenantRepository tenantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @BeforeEach
    void setUp() {
        roleRepository.deleteAll();
        userRepository.deleteAll();

        if (tenantRepository.findById(TENANT_ID).isEmpty()) {
            var tenant = new TenantEntity();
            tenant.setId(TENANT_ID);
            tenant.setName("Test Tenant");
            tenant.setSlug("test-tenant");
            tenant.setStatus("ACTIVE");
            tenant.setKeycloakRealmName("test-realm");
            tenant.setOpenfgaStoreId("test-store");
            var provStatus = ProvisioningStatus.initial("admin@test.com", "Admin", "User");
            tenant.setProvisioningStatus(provStatus.toJson());
            var auditInfo = new AuditInfo();
            auditInfo.setCreatedAt(OffsetDateTime.now());
            auditInfo.setCreatedBy(SYSTEM_USER_ID);
            auditInfo.setUpdatedAt(OffsetDateTime.now());
            auditInfo.setUpdatedBy(SYSTEM_USER_ID);
            tenant.setAuditInfo(auditInfo);
            tenantRepository.save(tenant);
        }

        lenient().when(permissionService.check(any())).thenReturn(true);
        var infra = new TenantInfrastructureInfo("test-store", "test-realm");
        lenient().when(lookupService.getInfrastructure(TENANT_ID)).thenReturn(infra);
        lenient().when(openFgaService.readTuples(any())).thenReturn(List.of());
    }

    @Test
    void shouldCreateAndListRoles() throws Exception {
        String body = """
                {"name": "Redakteur", "description": "Kann Inhalte bearbeiten"}
                """;

        mockMvc.perform(post("/api/v1/tenant/roles")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Redakteur"))
                .andExpect(jsonPath("$.systemRole").value(false));

        mockMvc.perform(get("/api/v1/tenant/roles")
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[0].name").value("Redakteur"));
    }

    private TenantRoleEntity createRoleEntity(String name, boolean systemRole) {
        var role = new TenantRoleEntity();
        role.setId(UUID.randomUUID());
        role.setTenantId(TENANT_ID);
        role.setName(name);
        role.setSystemRole(systemRole);
        // AuditInfo wird vom AuditListener gesetzt, aber im Test muss der
        // SecurityContext korrekt stehen (setUp setzt X-Test-Tenant-Id Header)
        return roleRepository.save(role);
    }

    @Test
    void shouldRejectSystemRoleUpdate() throws Exception {
        var role = createRoleEntity("Admin", true);

        String body = """
                {"name": "Super Admin"}
                """;

        mockMvc.perform(put("/api/v1/tenant/roles/" + role.getId())
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectSystemRoleDelete() throws Exception {
        var role = createRoleEntity("Admin", true);

        mockMvc.perform(delete("/api/v1/tenant/roles/" + role.getId())
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectDeleteWhenRoleHasAssignees() throws Exception {
        var role = createRoleEntity("Custom Role", false);

        when(openFgaService.readTuples(any()))
            .thenReturn(List.of(new RelationshipTuple("user:x", "assignee", "role:" + role.getId())));

        mockMvc.perform(delete("/api/v1/tenant/roles/" + role.getId())
                        .header("X-Test-Tenant-Id", TENANT_ID.toString())
                        .header(TestSecurityConfiguration.TEST_USER_ID_HEADER, USER_ID.toString()))
                .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: Test ausfuehren — muss fehlschlagen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.api.RoleControllerIntegrationTest"`
Expected: FAIL — RoleController existiert nicht

- [ ] **Step 3: RoleController implementieren**

```java
package io.webcontify.identity.api;

import io.webcontify.generated.api.TenantRolesApi;
import io.webcontify.generated.model.*;
import io.webcontify.identity.mapper.RoleMapper;
import io.webcontify.identity.security.RequiresPermission;
import io.webcontify.identity.service.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
public class RoleController implements TenantRolesApi {

    private final RoleService roleService;
    private final RoleMapper roleMapper;

    public RoleController(RoleService roleService, RoleMapper roleMapper) {
        this.roleService = roleService;
        this.roleMapper = roleMapper;
    }

    @Override
    @RequiresPermission(action = "creator", resource = "role_management")
    public ResponseEntity<RoleResponse> createRole(CreateRoleRequest request) {
        var role = roleService.create(request.getName(), request.getDescription());
        var response = roleMapper.toResponse(role, 0);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(role.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "role_management")
    public ResponseEntity<RoleListResponse> listRoles() {
        var roles = roleService.list();
        var response = new RoleListResponse();
        response.setRoles(roles.stream()
                .map(r -> roleMapper.toResponse(r, roleService.getAssigneeCount(r.getId())))
                .toList());
        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "reader", resource = "role_management")
    public ResponseEntity<RoleDetailResponse> getRole(UUID roleId) {
        var role = roleService.getById(roleId);
        var assignees = roleService.getAssignees(roleId);
        var permissions = roleService.getPermissionPreview(roleId);

        var response = new RoleDetailResponse();
        response.setId(role.getId());
        response.setName(role.getName());
        response.setDescription(role.getDescription());
        response.setSystemRole(role.isSystemRole());
        response.setAssignees(assignees.stream()
                .map(a -> {
                    var ar = new RoleAssigneeResponse();
                    ar.setUserId(a.userId());
                    ar.setEmail(a.email());
                    ar.setFirstName(a.firstName());
                    ar.setLastName(a.lastName());
                    return ar;
                }).toList());
        response.setPermissions(permissions.stream()
                .map(p -> {
                    var pr = new RolePermissionPreviewResponse();
                    pr.setResourceType(p.resourceType());
                    pr.setActions(p.actions());
                    return pr;
                }).toList());
        response.setCreatedAt(role.getCreatedAt());
        response.setUpdatedAt(role.getUpdatedAt());

        return ResponseEntity.ok(response);
    }

    @Override
    @RequiresPermission(action = "editor", resource = "role_management")
    public ResponseEntity<RoleResponse> updateRole(UUID roleId, UpdateRoleRequest request) {
        var role = roleService.update(roleId, request.getName(), request.getDescription());
        return ResponseEntity.ok(roleMapper.toResponse(role, roleService.getAssigneeCount(roleId)));
    }

    @Override
    @RequiresPermission(action = "deleter", resource = "role_management")
    public ResponseEntity<Void> deleteRole(UUID roleId) {
        roleService.delete(roleId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequiresPermission(action = "editor", resource = "role_management")
    public ResponseEntity<RoleDetailResponse> setRoleAssignees(UUID roleId, SetRoleAssigneesRequest request) {
        roleService.setAssignees(roleId, request.getUserIds());
        return getRole(roleId); // Re-fetch for full detail response
    }
}
```

- [ ] **Step 4: Tests ausfuehren — muessen gruen sein**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.api.RoleControllerIntegrationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/api/RoleController.java \
       apps/backend/src/test/java/io/webcontify/identity/api/RoleControllerIntegrationTest.java
git commit -m "feat: add RoleController with integration tests"
```

---

## Task 7: UserService Erweiterungen — setRoles, getRoles, Invite-Klon

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/UserService.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/service/UserServiceTest.java`

- [ ] **Step 1: Tests fuer setRoles + getRoles + invite mit copyRolesFromUserId schreiben**

Neue Nested Classes in `UserServiceTest.java`:

```java
@Nested
class SetRoles {
    @Test
    void shouldSetUserRoles() {
        var userId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var roleEntity = new TenantRoleEntity();
        roleEntity.setId(roleId);
        when(roleRepository.findAllActiveByIds(List.of(roleId))).thenReturn(List.of(roleEntity));
        when(openFgaService.listObjects(any())).thenReturn(List.of());

        roleService.setUserRoles(userId, List.of(roleId));

        verify(openFgaService).write(any());
    }
}

@Nested
class InviteWithRoleClone {
    @Test
    void shouldCopyRolesFromSourceUser() {
        // Setup source user with roles
        var sourceUserId = UUID.randomUUID();
        when(userRepository.findActiveById(sourceUserId)).thenReturn(Optional.of(createUserEntity()));
        when(openFgaService.listObjects(any())).thenReturn(List.of("role:" + UUID.randomUUID()));

        // ... test invite with copyRolesFromUserId
    }

    @Test
    void shouldThrowWhenSourceUserNotInTenant() {
        var sourceUserId = UUID.randomUUID();
        when(userRepository.findActiveById(sourceUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.invite("test@test.com", "Test", "User", sourceUserId))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("invite.source_user_not_found");
    }
}
```

- [ ] **Step 2: UserService erweitern**

Neue Methoden in `UserService.java`:

```java
public List<TenantRole> getUserRoles(UUID userId) {
    userRepository.findActiveById(userId)
        .orElseThrow(() -> new NotFoundException("user.not_found"));

    var tenantInfo = TenantContext.getTenantInformation();
    var storeId = lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();
    var roleObjects = openFgaService.listObjects(new OpenFgaListObjectsRequest(
        storeId, userId, "assignee", "role"));

    if (roleObjects.isEmpty()) return List.of();

    var roleIds = roleObjects.stream()
        .map(obj -> UUID.fromString(obj.replace("role:", "")))
        .toList();

    return roleRepository.findAllActiveByIds(roleIds).stream()
        .map(roleMapper::toDomain)
        .toList();
}

public void setUserRoles(UUID userId, List<UUID> roleIds) {
    userRepository.findActiveById(userId)
        .orElseThrow(() -> new NotFoundException("user.not_found"));

    // Validate roleIds
    var existingRoles = roleRepository.findAllActiveByIds(roleIds);
    if (existingRoles.size() != roleIds.size()) {
        throw new BadRequestException("role.not_found");
    }

    var tenantInfo = TenantContext.getTenantInformation();
    var storeId = lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();

    // Current roles
    var currentObjects = openFgaService.listObjects(new OpenFgaListObjectsRequest(
        storeId, userId, "assignee", "role"));
    var currentRoleIds = currentObjects.stream()
        .map(obj -> UUID.fromString(obj.replace("role:", "")))
        .collect(java.util.stream.Collectors.toSet());
    var targetRoleIds = new HashSet<>(roleIds);

    var toAdd = targetRoleIds.stream().filter(id -> !currentRoleIds.contains(id)).toList();
    var toRemove = currentRoleIds.stream().filter(id -> !targetRoleIds.contains(id)).toList();

    // Last admin guard: check if removing Admin role from this user
    guardLastAdminRoleRemoval(userId, toRemove, storeId);

    if (!toAdd.isEmpty()) {
        var addTuples = toAdd.stream()
            .map(rid -> new RelationshipTuple("user:" + userId, "assignee", "role:" + rid))
            .toList();
        openFgaService.write(new OpenFgaWriteRequest(storeId, addTuples));
    }
    if (!toRemove.isEmpty()) {
        var removeTuples = toRemove.stream()
            .map(rid -> new RelationshipTuple("user:" + userId, "assignee", "role:" + rid))
            .toList();
        openFgaService.delete(new OpenFgaDeleteRequest(storeId, removeTuples));
    }
}
```

`invite()` Methode Signatur erweitern: `invite(String email, String firstName, String lastName, UUID copyRolesFromUserId)`. **WICHTIG:** `copyRolesFromUserId` Validierung MUSS am Anfang von `invite()` passieren, VOR Keycloak-User-Create:

```java
// Am ANFANG von invite(), vor Keycloak-Aufruf:
if (copyRolesFromUserId != null) {
    userRepository.findActiveById(copyRolesFromUserId)
        .orElseThrow(() -> new BadRequestException("invite.source_user_not_found"));
}
```

Am Ende, nach dem bestehenden Ablauf (Rollen-Klon als best-effort):

```java
// Rollen klonen (best-effort)
if (copyRolesFromUserId != null) {
    try {
        var sourceRoles = getUserRoles(copyRolesFromUserId);
        var roleIds = sourceRoles.stream().map(TenantRole::getId).toList();
        if (!roleIds.isEmpty()) {
            setUserRoles(keycloakUserId, roleIds);
        }
    } catch (Exception e) {
        log.warn("Failed to copy roles from userId={} to new user={}", copyRolesFromUserId, keycloakUserId, e);
    }
}
```

**Bestehende Tests anpassen:** Alle Aufrufe von `userService.invite(email, firstName, lastName)` in `UserServiceTest` muessen auf die 4-Parameter-Signatur mit `null` als letztem Argument angepasst werden: `userService.invite(email, firstName, lastName, null)`.

**UserService Dependencies:** `TenantRoleRepository` und `RoleMapper` als neue Dependencies im Konstruktor von `UserService` hinzufuegen:

```java
private final TenantRoleRepository roleRepository;
private final RoleMapper roleMapper;

public UserService(... , TenantRoleRepository roleRepository, RoleMapper roleMapper) {
    // ...
    this.roleRepository = roleRepository;
    this.roleMapper = roleMapper;
}
```

- [ ] **Step 3: guardLastAdmin durch OpenFGA-basierte Variante ersetzen**

Die bestehende `guardLastAdmin(UUID excludeUserId)` Methode ersetzen:

```java
private void guardLastAdminRoleRemoval(UUID userId, List<UUID> rolesToRemove, String storeId) {
    // Find Admin system role
    var adminRole = roleRepository.findActiveByName("Admin");
    if (adminRole.isEmpty()) return;

    var adminRoleId = adminRole.get().getId();
    if (!rolesToRemove.contains(adminRoleId)) return;

    // Check how many users have Admin role
    var adminAssignees = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
        storeId, "role", adminRoleId.toString(), "assignee"));
    if (adminAssignees.size() <= 1) {
        throw new ConflictException("role.last_admin_cannot_be_removed");
    }
}
```

- [ ] **Step 4: Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.UserServiceTest"`
Expected: PASS

- [ ] **Step 5: UserController anpassen fuer neue Endpoints**

In `UserController.java` die neuen `TenantUserRolesApi` Methoden implementieren:

```java
@Override
@RequiresPermission(action = "reader", resource = "user_management")
public ResponseEntity<UserRolesResponse> getUserRoles(UUID userId) {
    var roles = userService.getUserRoles(userId);
    var response = new UserRolesResponse();
    response.setRoles(roles.stream().map(r -> roleMapper.toResponse(r, 0)).toList());
    return ResponseEntity.ok(response);
}

@Override
@RequiresPermission(action = "editor", resource = "user_management")
public ResponseEntity<UserRolesResponse> setUserRoles(UUID userId, SetUserRolesRequest request) {
    userService.setUserRoles(userId, request.getRoleIds());
    return getUserRoles(userId);
}
```

Auch `inviteUser()` anpassen:

```java
@Override
@RequiresPermission(action = "inviter", resource = "user_management")
public ResponseEntity<UserResponse> inviteUser(InviteUserRequest request) {
    var user = userService.invite(
        request.getEmail(), request.getFirstName(), request.getLastName(),
        request.getCopyRolesFromUserId());
    // ... rest unchanged
}
```

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/UserService.java \
       apps/backend/src/main/java/io/webcontify/identity/api/UserController.java \
       apps/backend/src/test/java/io/webcontify/identity/service/UserServiceTest.java
git commit -m "feat: add setUserRoles, getUserRoles, and invite role cloning"
```

---

## Task 8: Provisioning erweitern — System-Rollen + Singleton-Ressourcen + Migration

**WICHTIG — Bestehende Tenants:** Der bestehende Code schreibt `tenant#admin` Tupel fuer den Admin-User. Das neue Format nutzt `role#assignee`. Fuer existierende Dev-Tenants: ein Datenmigrations-Script oder DevDataSeeder-Update ist noetig. In Produktion gibt es noch keine Tenants, daher reicht ein Clean-Provisioning. Im DevDataSeeder: bestehende Tenants loeschen und neu provisionieren lassen, oder manuell die alten `tenant#admin` Tupel durch `role#assignee` ersetzen.

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningListenerTest.java`

- [ ] **Step 1: Test fuer System-Rollen-Erstellung schreiben**

Bestehenden Provisioning Test erweitern um zu verifizieren, dass 4 System-Rollen und Singleton-Ressourcen erstellt werden.

- [ ] **Step 2: Step 3 (INITIAL_ADMIN) in der Provisioning-Logik erweitern**

Im bestehenden Step 3 Block (`INITIAL_ADMIN`, Zeile 123-161):

1. Vier System-Rollen erstellen (Admin, Editor, Viewer, Inviter) statt nur "Admin"
2. Singleton-Ressourcen in OpenFGA schreiben:
   - `user_management:{tenantId}#tenant -> tenant:{tenantId}`
   - `role_management:{tenantId}#tenant -> tenant:{tenantId}`
   - `tenant_config:{tenantId}#tenant -> tenant:{tenantId}`
3. Admin-Rolle Permissions in OpenFGA:
   - `user_management:{tenantId}#inviter -> role:admin-role#assignee`
   - `user_management:{tenantId}#editor -> role:admin-role#assignee`
   - `user_management:{tenantId}#deleter -> role:admin-role#assignee`
   - `role_management:{tenantId}#creator -> role:admin-role#assignee`
   - `role_management:{tenantId}#editor -> role:admin-role#assignee`
   - `role_management:{tenantId}#deleter -> role:admin-role#assignee`
   - `tenant_config:{tenantId}#editor -> role:admin-role#assignee`
4. Inviter-Rolle Permissions:
   - `user_management:{tenantId}#inviter -> role:inviter-role#assignee`
5. Editor/Viewer bekommen `tenant_config:{tenantId}#reader`
6. Admin-User bekommt `role:admin-role#assignee` statt `tenant#admin`

- [ ] **Step 3: Tests ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.TenantProvisioningListenerTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java \
       apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningListenerTest.java
git commit -m "feat: extend provisioning with 4 system roles and singleton resources"
```

---

## Task 9: Full Backend Test Suite

- [ ] **Step 1: Gesamte Backend Test Suite ausfuehren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: ALL PASS

- [ ] **Step 2: Commit (falls Fixes noetig)**

```bash
git commit -m "fix: resolve test issues from role management integration"
```

---

## Task 10: Frontend — Orval Regeneration + Zod Schemas

**Files:**
- Modify: `apps/platform/src/features/users/schemas/invite-user.ts`
- Create: `apps/platform/src/features/roles/schemas/role.ts`

- [ ] **Step 1: Orval regenerieren**

Run: `cd /Users/christophhagelkruys/Documents/private/projects/claudetest && npx nx generate platform`
Expected: Neue Hooks in `apps/platform/src/generated/identity.ts`: `useListRoles`, `useCreateRole`, `useGetRole`, `useUpdateRole`, `useDeleteRole`, `useSetRoleAssignees`, `useGetUserRoles`, `useSetUserRoles`

- [ ] **Step 2: Role Zod Schema erstellen**

```typescript
// apps/platform/src/features/roles/schemas/role.ts
import { z } from "zod";

export const createRoleSchema = z.object({
  name: z.string().min(1, "Name ist erforderlich").max(255),
  description: z.string().max(1000).optional(),
});

export type CreateRoleFormValues = z.infer<typeof createRoleSchema>;

export const updateRoleSchema = createRoleSchema;
export type UpdateRoleFormValues = z.infer<typeof updateRoleSchema>;
```

- [ ] **Step 3: Invite Schema erweitern**

```typescript
// apps/platform/src/features/users/schemas/invite-user.ts
import { z } from "zod";

export const inviteUserSchema = z.object({
  email: z.string().email("Ungueltige E-Mail-Adresse").max(255),
  firstName: z.string().min(1, "Vorname ist erforderlich").max(255),
  lastName: z.string().min(1, "Nachname ist erforderlich").max(255),
  copyRolesFromUserId: z.string().uuid().optional(),
});

export type InviteUserFormValues = z.infer<typeof inviteUserSchema>;
```

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/generated/ \
       apps/platform/src/features/roles/schemas/role.ts \
       apps/platform/src/features/users/schemas/invite-user.ts
git commit -m "feat: regenerate Orval hooks and add role schemas"
```

---

## Task 11: Frontend — Rollen-Liste

**Files:**
- Modify: `apps/platform/src/app/(workspace)/roles/page.tsx`
- Create: `apps/platform/src/features/roles/components/role-table.tsx`
- Create: `apps/platform/src/features/roles/components/create-role-dialog.tsx`
- Create: `apps/platform/src/features/roles/hooks/use-role-actions.ts`

- [ ] **Step 1: use-role-actions Hook erstellen**

```typescript
// apps/platform/src/features/roles/hooks/use-role-actions.ts
"use client";

import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  useCreateRole,
  useUpdateRole,
  useDeleteRole,
  useSetRoleAssignees,
  getListRolesQueryKey,
  getGetRoleQueryKey,
} from "@/generated/identity";

export function useRoleActions() {
  const queryClient = useQueryClient();
  const createMutation = useCreateRole();
  const updateMutation = useUpdateRole();
  const deleteMutation = useDeleteRole();
  const setAssigneesMutation = useSetRoleAssignees();

  const invalidateRoles = () => {
    queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
  };

  return {
    createRole: createMutation,
    updateRole: updateMutation,
    deleteRole: deleteMutation,
    setRoleAssignees: setAssigneesMutation,
    invalidateRoles,
    isCreating: createMutation.isPending,
    isUpdating: updateMutation.isPending,
    isDeleting: deleteMutation.isPending,
  };
}
```

- [ ] **Step 2: Create-Role-Dialog erstellen**

Pattern wie `invite-user-dialog.tsx`: React Hook Form + Zod + shadcn Dialog. Felder: Name (required), Description (optional).

- [ ] **Step 3: Role Table erstellen**

Pattern wie `user-table.tsx`: Spalten Name, Beschreibung, System Badge, Assignees Count, Actions (Edit, Delete — nur fuer Custom Roles). Klick auf Zeile → `/roles/[roleId]`.

- [ ] **Step 4: Roles Page ersetzen**

```typescript
// apps/platform/src/app/(workspace)/roles/page.tsx
"use client";

import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";
import { useListRoles } from "@/generated/identity";
import { RoleTable } from "@/features/roles/components/role-table";
import { CreateRoleDialog } from "@/features/roles/components/create-role-dialog";

export default function RolesPage() {
  const { data, isLoading, isRefetching } = useListRoles();

  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Rollen" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Rollen</h1>
          <CreateRoleDialog>
            <Button>
              <Plus className="mr-2 size-4" />
              Rolle erstellen
            </Button>
          </CreateRoleDialog>
        </div>
        <RoleTable
          roles={data?.data.roles ?? []}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
```

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/app/\(workspace\)/roles/page.tsx \
       apps/platform/src/features/roles/
git commit -m "feat: add role list page with create dialog"
```

---

## Task 12: Frontend — Rollen-Detail-Seite

**Files:**
- Create: `apps/platform/src/app/(workspace)/roles/[roleId]/page.tsx`
- Create: `apps/platform/src/features/roles/components/edit-role-form.tsx`
- Create: `apps/platform/src/features/roles/components/role-assignees-table.tsx`
- Create: `apps/platform/src/features/roles/components/add-assignees-dialog.tsx`
- Create: `apps/platform/src/features/roles/components/role-permissions-preview.tsx`

- [ ] **Step 1: Rollen-Detail Page erstellen**

`/roles/[roleId]/page.tsx` mit:
- Header (Name, Beschreibung — editierbar bei Custom Roles)
- Tabs (shadcn Tabs): "Zugewiesene User" + "Permissions"
- Tab 1: `RoleAssigneesTable` + "User hinzufuegen" Button → `AddAssigneesDialog`
- Tab 2: `RolePermissionsPreview` (read-only)

- [ ] **Step 2: Edit Role Form**

Inline-Edit fuer Name + Beschreibung. Nur bei Custom Roles sichtbar. React Hook Form + Zod.

- [ ] **Step 3: Assignees Table + Add Dialog**

`RoleAssigneesTable`: Tabelle mit User-Liste, Entfernen-Action pro Zeile.
`AddAssigneesDialog`: Multi-Select Combobox mit Suche (bestehende Tenant-User die noch nicht zugewiesen sind).

- [ ] **Step 4: Permissions Preview**

Gruppiert nach Resource-Typ. Zeigt Actions als Badges. Hinweis-Text "Permissions koennen aktuell nicht bearbeitet werden".

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/app/\(workspace\)/roles/\[roleId\]/ \
       apps/platform/src/features/roles/components/
git commit -m "feat: add role detail page with assignees and permissions preview"
```

---

## Task 13: Frontend — User-Seite Erweiterungen (Rollen-Zuweisung + Invite-Klon)

**Files:**
- Modify: `apps/platform/src/features/users/components/invite-user-dialog.tsx`
- Modify: `apps/platform/src/features/users/components/user-table.tsx`
- Modify: `apps/platform/src/features/users/hooks/use-user-actions.ts`

- [ ] **Step 1: Invite Dialog um "Berechtigungen uebernehmen von" erweitern**

In `invite-user-dialog.tsx`:
- Neues optionales Feld `copyRolesFromUserId` als Combobox mit User-Suche
- Props erweitern: `initialCopyFromUserId?: string` fuer den "klonen"-Flow
- Bei Auswahl: Rollen des Quell-Users als Vorschau-Badges anzeigen (via `useGetUserRoles`)

- [ ] **Step 2: User Table — "Rollen klonen" Action**

In `user-table.tsx`: neue Action "User mit gleichen Rollen einladen" im Actions-Menu. Oeffnet den Invite-Dialog mit vorausgefuelltem `copyRolesFromUserId`.

- [ ] **Step 3: User Actions Hook erweitern**

In `use-user-actions.ts`: `useGetUserRoles`, `useSetUserRoles` Hooks exportieren.

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/features/users/
git commit -m "feat: extend invite dialog with role cloning and user-to-role assignment"
```

---

## Task 14: Frontend Tests

**Files:**
- Create: `apps/platform/src/features/roles/components/__tests__/role-table.test.tsx`
- Create: `apps/platform/src/features/roles/components/__tests__/create-role-dialog.test.tsx`

- [ ] **Step 1: Role Table Tests**

```typescript
// role-table.test.tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { RoleTable } from "../role-table";

describe("RoleTable", () => {
  it("should render role names", () => { /* ... */ });
  it("should show system role badge", () => { /* ... */ });
  it("should show empty state", () => { /* ... */ });
  it("should show skeleton when loading", () => { /* ... */ });
  it("should not show delete action for system roles", () => { /* ... */ });
});
```

- [ ] **Step 2: Create Role Dialog Tests**

```typescript
// create-role-dialog.test.tsx
describe("CreateRoleDialog", () => {
  it("should render form fields", () => { /* ... */ });
  it("should validate required name", () => { /* ... */ });
  it("should submit form successfully", () => { /* ... */ });
  it("should show error on duplicate name", () => { /* ... */ });
});
```

- [ ] **Step 3: Tests ausfuehren**

Run: `cd /Users/christophhagelkruys/Documents/private/projects/claudetest && npx nx test platform`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/features/roles/components/__tests__/
git commit -m "test: add frontend tests for role table and create dialog"
```

---

## Task 15: Build Verification + PROGRESS.md

- [ ] **Step 1: Full Backend Test Suite**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: ALL PASS

- [ ] **Step 2: Frontend Build + Tests**

Run: `cd /Users/christophhagelkruys/Documents/private/projects/claudetest && npx nx test platform && npx nx build platform`
Expected: ALL PASS, Build OK

- [ ] **Step 3: PROGRESS.md aktualisieren**

Phase 3d Tasks eintragen mit Status Done.

- [ ] **Step 4: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md — Phase 3d complete"
```
