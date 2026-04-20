# Phase 3h: Permission-Aware Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Navigation dynamisch basierend auf OpenFGA-Permissions filtern + Rollen-Permissions konfigurierbar machen.

**Architecture:** Neuer `GET /api/v1/auth/effective-permissions` Endpoint mit BatchCheck, neuer `PUT /api/v1/tenant/roles/{roleId}/permissions` Endpoint mit Delta-basierter Tuple-Verwaltung. Frontend: `usePermissions()` Hook (React Query), permission-aware nav-config, editable Permission-Toggles auf Role-Detail.

**Tech Stack:** Spring Boot 4, OpenFGA SDK 0.7.2 (BatchCheck), Next.js 16, React Query (Orval), Vitest, JUnit 5 + Testcontainers + Mockito

**Spec:** `docs/superpowers/specs/2026-03-22-permission-aware-navigation-design.md`

---

## File Structure

### Backend (neu)
| File | Verantwortung |
|------|--------------|
| `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaBatchCheckResponse.java` | Response Record fuer BatchCheck |
| `apps/backend/src/main/java/io/webcontify/identity/model/EffectivePermissions.java` | Domain Model fuer aggregierte Permissions |
| `apps/backend/src/main/java/io/webcontify/identity/model/SetRolePermissionsRequest.java` | Domain Model fuer Permission-Zuweisung |

### Backend (aendern)
| File | Aenderung |
|------|-----------|
| `apps/backend/src/main/resources/openfga/model.fga` | `tenant_config` Typ ergaenzen |
| `apps/backend/src/main/resources/openfga/model.json` | `tenant_config` Typ ergaenzen (compiled) |
| `openapi/auth-api.yaml` | EffectivePermissions Endpoint + Schemas |
| `openapi/identity-api.yaml` | SetRolePermissions Endpoint + Schemas |
| `apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java` | `batchCheck()` + Thread-Safety |
| `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java` | `getEffectivePermissions()` |
| `apps/backend/src/main/java/io/webcontify/identity/api/AuthController.java` | `getEffectivePermissions()` |
| `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java` | `setPermissions()`, `delete()` Cleanup, BatchCheck-Migration |
| `apps/backend/src/main/java/io/webcontify/identity/api/RoleController.java` | `setPermissions()` |
| `apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java` | Editor/Viewer Tuples entfernen |

### Frontend (neu)
| File | Verantwortung |
|------|--------------|
| `apps/platform/src/hooks/use-permissions.ts` | `usePermissions()` Hook |
| `apps/platform/src/hooks/__tests__/use-permissions.test.ts` | Tests |
| `apps/platform/src/features/roles/components/role-permissions-editor.tsx` | Editierbare Permission-Toggles |

### Frontend (aendern)
| File | Aenderung |
|------|-----------|
| `apps/platform/src/components/layout/nav-config.ts` | Granulare Permission-Flags |
| `apps/platform/src/components/layout/__tests__/nav-config.test.ts` | Tests aktualisieren |
| `apps/platform/src/app/(workspace)/layout.tsx` | `usePermissions()` statt hardcoded |
| `apps/platform/src/app/(workspace)/users/page.tsx` | Permission Guard |
| `apps/platform/src/app/(workspace)/roles/page.tsx` | Permission Guard |
| `apps/platform/src/app/(workspace)/roles/[roleId]/page.tsx` | Permission Editor statt Preview |
| `apps/platform/src/app/(workspace)/settings/*/page.tsx` | Permission Guard |

---

## Task 1: OpenFGA Model Fix — tenant_config

**Files:**
- Modify: `apps/backend/src/main/resources/openfga/model.fga`
- Modify: `apps/backend/src/main/resources/openfga/model.json`

- [ ] **Step 1: model.fga erweitern**

Am Ende von `model.fga` (nach `role_management`) den `tenant_config` Typ hinzufuegen:

```
type tenant_config
  relations
    define tenant: [tenant]
    define editor: [user, role#assignee] or admin from tenant
    define reader: [user, role#assignee] or editor or admin from tenant
```

- [ ] **Step 2: model.json aktualisieren**

Die JSON-Version muss manuell synchronisiert werden. Den neuen `tenant_config` Typ als Type Definition im `type_definitions` Array hinzufuegen — gleiches Pattern wie `user_management` und `role_management`, nur mit `editor` und `reader` statt 4 Relations.

- [ ] **Step 3: Backend Tests laufen lassen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: Alle bestehenden Tests gruen (OpenFGA Model wird bei Store-Erstellung deployed, aendert bestehende Tests nicht)

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/resources/openfga/model.fga apps/backend/src/main/resources/openfga/model.json
git commit -m "fix: add tenant_config type to OpenFGA authorization model"
```

---

## Task 2: OpenFgaService — batchCheck()

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaBatchCheckResponse.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/service/OpenFgaServiceTest.java`

**SDK-Referenz (OpenFGA SDK 0.7.2):**
- `client.batchCheck(List<ClientCheckRequest>)` → `CompletableFuture<List<ClientBatchCheckResponse>>`
- `client.batchCheck(List<ClientCheckRequest>, ClientBatchCheckOptions)` → dito
- `ClientBatchCheckResponse.getRequest()` → originaler `ClientCheckRequest`
- `ClientBatchCheckResponse.getAllowed()` → `Boolean`
- `ClientBatchCheckOptions` hat KEIN `storeId()` — Store muss via `client.setStoreId()` gesetzt werden
- **Thread-Safety Limit:** `client.setStoreId()` ist globaler State. SDK 0.7.2 bietet keinen per-Request Override. Akzeptabel fuer jetzt (Single-Thread Request-Processing in Spring MVC), als Tech-Debt dokumentieren.

- [ ] **Step 1: OpenFgaBatchCheckResponse Record erstellen**

```java
package io.webcontify.identity.model;

import java.util.Map;

public record OpenFgaBatchCheckResponse(Map<String, Boolean> results) {}
```

Key = zusammengesetzt aus `action:resourceType` (z.B. `"reader:user_management"`), Value = allowed.

- [ ] **Step 2: Test fuer batchCheck schreiben**

In `OpenFgaServiceTest.java` einen neuen `@Nested` Block `BatchCheck` mit Test:

```java
@Nested
class BatchCheck {
    @Test
    void shouldReturnBatchResults() throws Exception {
        // Arrange: 2 checks, einer allowed, einer denied
        var check1 = new OpenFgaCheckRequest(STORE_ID, USER_ID, "reader", "user_management", TENANT_ID);
        var check2 = new OpenFgaCheckRequest(STORE_ID, USER_ID, "editor", "user_management", TENANT_ID);
        var batchRequest = new OpenFgaBatchCheckRequest(STORE_ID, List.of(check1, check2));

        // Mock: SDK batchCheck returns List<ClientBatchCheckResponse>
        // Erstelle ClientCheckRequest fuer jeden Check um getRequest() zu matchen
        var sdkCheck1 = new ClientCheckRequest()
                .user("user:" + USER_ID).relation("reader")._object("user_management:" + TENANT_ID);
        var sdkCheck2 = new ClientCheckRequest()
                .user("user:" + USER_ID).relation("editor")._object("user_management:" + TENANT_ID);

        var resp1 = mock(ClientBatchCheckResponse.class);
        when(resp1.getRequest()).thenReturn(sdkCheck1);
        when(resp1.getAllowed()).thenReturn(true);

        var resp2 = mock(ClientBatchCheckResponse.class);
        when(resp2.getRequest()).thenReturn(sdkCheck2);
        when(resp2.getAllowed()).thenReturn(false);

        when(openFgaClient.batchCheck(anyList(), any(ClientBatchCheckOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(List.of(resp1, resp2)));

        // Act
        var result = openFgaService.batchCheck(batchRequest);

        // Assert
        assertThat(result.results()).hasSize(2);
        assertThat(result.results().get("reader:user_management")).isTrue();
        assertThat(result.results().get("editor:user_management")).isFalse();
    }
}
```

- [ ] **Step 3: Test ausfuehren — muss fehlschlagen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*.OpenFgaServiceTest.BatchCheck.*"`
Expected: FAIL — Methode `batchCheck` existiert nicht.

- [ ] **Step 4: batchCheck() implementieren**

In `OpenFgaService.java`:

```java
public OpenFgaBatchCheckResponse batchCheck(OpenFgaBatchCheckRequest request) {
    try {
        var checks = request.checks().stream()
                .map(check -> {
                    var subject = check.userSubject() != null ? check.userSubject() : "user:" + check.userId();
                    var object = buildObject(check.resourceType(), check.resourceId());
                    return new ClientCheckRequest()
                            .user(subject)
                            .relation(check.action())
                            ._object(object);
                })
                .toList();

        client.setStoreId(request.storeId());
        var responses = client.batchCheck(checks, new ClientBatchCheckOptions()).get();

        // Korrelation via getRequest().getRelation() + getRequest().getObject()
        var results = new java.util.HashMap<String, Boolean>();
        for (var response : responses) {
            var req = response.getRequest();
            // Key: "action:resourceType" (z.B. "reader:user_management")
            var objectParts = req.getObject().split(":");
            var key = req.getRelation() + ":" + objectParts[0];
            results.put(key, Boolean.TRUE.equals(response.getAllowed()));
        }

        log.debug("OpenFGA batchCheck storeId={} checks={} results={}", request.storeId(), checks.size(), results.size());
        return new OpenFgaBatchCheckResponse(results);
    } catch (Exception e) {
        throw new RuntimeException("OpenFGA batchCheck failed", e);
    }
}
```

**Hinweis:** `client.setStoreId()` ist nicht thread-safe — akzeptabel fuer Spring MVC (ein Request pro Thread), wird in Phase 4 mit WebFlux/Virtual Threads ggf. angepasst.

- [ ] **Step 5: Test ausfuehren — muss gruen sein**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*.OpenFgaServiceTest.BatchCheck.*"`
Expected: PASS

- [ ] **Step 6: Alle Tests laufen lassen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: Alle Tests gruen.

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaBatchCheckResponse.java \
       apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java \
       apps/backend/src/test/java/io/webcontify/identity/service/OpenFgaServiceTest.java
git commit -m "feat: implement OpenFGA batchCheck"
```

---

## Task 3: OpenAPI Spec — Effective Permissions Endpoint

**Files:**
- Modify: `openapi/auth-api.yaml`

- [ ] **Step 1: EffectivePermissions Schemas hinzufuegen**

In `auth-api.yaml` unter `components/schemas`:

```yaml
EffectivePermissionsResponse:
  type: object
  required:
    - permissions
    - isTenantAdmin
  properties:
    permissions:
      type: object
      additionalProperties:
        type: array
        items:
          type: string
      description: "Map von resourceType zu Liste von gewährten Actions"
    isTenantAdmin:
      type: boolean
```

- [ ] **Step 2: Endpoint definieren**

In `auth-api.yaml` unter `paths`:

```yaml
/api/v1/auth/effective-permissions:
  get:
    operationId: getEffectivePermissions
    tags:
      - auth
    summary: Effective permissions of current user
    parameters:
      - name: scope
        in: query
        required: false
        schema:
          type: string
          default: navigation
    responses:
      '200':
        description: Effective permissions
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/EffectivePermissionsResponse'
      '400':
        description: Invalid scope
        content:
          application/json:
            schema:
              $ref: 'common-schemas.yaml#/components/schemas/ProblemDetail'
      '401':
        description: Unauthorized
```

- [ ] **Step 3: Backend Code generieren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerateAuth`
Expected: Generierte `AuthApi` Interface hat `getEffectivePermissions` Methode.

- [ ] **Step 4: Commit**

```bash
git add openapi/auth-api.yaml
git commit -m "feat: add effective-permissions endpoint to auth OpenAPI spec"
```

---

## Task 4: OpenAPI Spec — SetRolePermissions Endpoint

**Files:**
- Modify: `openapi/identity-api.yaml`

- [ ] **Step 1: Request Schema hinzufuegen**

In `identity-api.yaml` unter `components/schemas`:

```yaml
SetRolePermissionsRequest:
  type: object
  required:
    - permissions
  properties:
    permissions:
      type: array
      items:
        $ref: '#/components/schemas/RolePermissionEntry'

RolePermissionEntry:
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
```

- [ ] **Step 2: Endpoint definieren**

In `identity-api.yaml` unter `/api/v1/tenant/roles/{roleId}/permissions`:

```yaml
put:
  operationId: setRolePermissions
  tags:
    - roles
  summary: Set permissions for a role
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
          $ref: '#/components/schemas/SetRolePermissionsRequest'
  responses:
    '200':
      description: Permissions updated
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/RoleDetailResponse'
    '400':
      description: Invalid request or system role
      content:
        application/json:
          schema:
            $ref: 'common-schemas.yaml#/components/schemas/ProblemDetail'
    '403':
      description: Forbidden - privilege escalation or missing permission
    '404':
      description: Role not found
      content:
        application/json:
          schema:
            $ref: 'common-schemas.yaml#/components/schemas/ProblemDetail'
```

- [ ] **Step 3: Backend Code generieren**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerateIdentity`
Expected: Generiertes `RolesApi` Interface hat `setRolePermissions` Methode.

- [ ] **Step 4: Commit**

```bash
git add openapi/identity-api.yaml
git commit -m "feat: add set-role-permissions endpoint to identity OpenAPI spec"
```

---

## Task 5: Backend — Effective Permissions (AuthService + Controller)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/EffectivePermissions.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/api/AuthController.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/AuthServiceEffectivePermissionsTest.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/api/AuthControllerIntegrationTest.java` (falls vorhanden, sonst neuer Test)

- [ ] **Step 1: EffectivePermissions Domain Record**

```java
package io.webcontify.identity.model;

import java.util.List;
import java.util.Map;

public record EffectivePermissions(
    Map<String, List<String>> permissions,
    boolean isTenantAdmin
) {}
```

- [ ] **Step 1b: OpenFgaService als neue Dependency in AuthService hinzufuegen**

`AuthService` hat aktuell: `KeycloakAdminService`, `FileStorageService`, `TenantInfrastructureLookupService`, `keycloakBaseUrl`. Fuer `getEffectivePermissions()` wird `OpenFgaService` benoetigt.

Im Konstruktor hinzufuegen:
```java
private final OpenFgaService openFgaService;
// ... bestehende Felder ...

public AuthService(KeycloakAdminService keycloakAdminService,
                   FileStorageService fileStorageService,
                   TenantInfrastructureLookupService lookupService,
                   OpenFgaService openFgaService,  // NEU
                   @Value("${keycloak.base-url}") String keycloakBaseUrl) {
    // ...
    this.openFgaService = openFgaService;
}
```

- [ ] **Step 2: Unit Test fuer AuthService.getEffectivePermissions schreiben**

Neuer Test `AuthServiceEffectivePermissionsTest.java`:

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceEffectivePermissionsTest {

    @Mock OpenFgaService openFgaService;
    @Mock KeycloakAdminService keycloakAdminService;
    @Mock FileStorageService fileStorageService;
    @Mock TenantInfrastructureLookupService lookupService;

    @InjectMocks AuthService authService;

    // Test 1: Navigation scope — User hat reader auf user_management
    // Test 2: Platform Admin bekommt alle Permissions
    // Test 3: Unbekannter scope -> BadRequestException
    // Test 4: User ohne Permissions bekommt leere Map
}
```

- [ ] **Step 3: Test ausfuehren — muss fehlschlagen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*.AuthServiceEffectivePermissionsTest.*"`
Expected: FAIL

- [ ] **Step 4: AuthService.getEffectivePermissions() implementieren**

```java
private static final Map<String, List<String>> NAVIGATION_RESOURCE_ACTIONS = Map.of(
    "user_management", List.of("inviter", "editor", "deleter", "reader"),
    "role_management", List.of("creator", "editor", "deleter", "reader"),
    "tenant_config", List.of("editor", "reader")
);

public EffectivePermissions getEffectivePermissions(String scope) {
    if (scope != null && !scope.equals("navigation")) {
        throw new BadRequestException("auth.invalid_scope");
    }

    var tenantInfo = TenantContext.getTenantInformation();

    if (tenantInfo.platformAdmin()) {
        // Platform Admin: alle Permissions
        var allPermissions = NAVIGATION_RESOURCE_ACTIONS.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new EffectivePermissions(allPermissions, true);
    }

    var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());
    UUID userId = resolveCurrentUserId();

    // BatchCheck aufbauen: alle Actions + Admin-Check
    var checks = new ArrayList<OpenFgaCheckRequest>();
    NAVIGATION_RESOURCE_ACTIONS.forEach((resourceType, actions) ->
        actions.forEach(action ->
            checks.add(new OpenFgaCheckRequest(infrastructure.openfgaStoreId(),
                userId, action, resourceType, tenantInfo.tenantId()))
        )
    );
    // Admin-Check
    checks.add(new OpenFgaCheckRequest(infrastructure.openfgaStoreId(),
        userId, "admin", "tenant", tenantInfo.tenantId()));

    var batchResponse = openFgaService.batchCheck(
        new OpenFgaBatchCheckRequest(infrastructure.openfgaStoreId(), checks));

    // Ergebnisse aggregieren
    var permissions = new HashMap<String, List<String>>();
    NAVIGATION_RESOURCE_ACTIONS.forEach((resourceType, actions) -> {
        var grantedActions = actions.stream()
            .filter(action -> Boolean.TRUE.equals(
                batchResponse.results().get(action + ":" + resourceType)))
            .toList();
        if (!grantedActions.isEmpty()) {
            permissions.put(resourceType, grantedActions);
        }
    });

    boolean isTenantAdmin = Boolean.TRUE.equals(
        batchResponse.results().get("admin:tenant"));

    return new EffectivePermissions(permissions, isTenantAdmin);
}
```

- [ ] **Step 5: AuthController.getEffectivePermissions() implementieren**

```java
@Override
public ResponseEntity<EffectivePermissionsResponse> getEffectivePermissions(String scope) {
    var effective = authService.getEffectivePermissions(scope);
    var response = new EffectivePermissionsResponse();
    response.setPermissions(effective.permissions());
    response.setIsTenantAdmin(effective.isTenantAdmin());
    return ResponseEntity.ok(response);
}
```

- [ ] **Step 6: Tests ausfuehren — muessen gruen sein**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*.AuthServiceEffectivePermissionsTest.*"`
Expected: PASS

- [ ] **Step 7: Integration Test fuer den Endpoint**

Test in `AuthControllerIntegrationTest` (oder neu erstellen):
- GET `/api/v1/auth/effective-permissions` mit gemocktem `openFgaService.batchCheck()`
- Prueft Response-Format (permissions Map, isTenantAdmin)

- [ ] **Step 8: Alle Tests gruen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: Alle Tests gruen.

- [ ] **Step 9: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/EffectivePermissions.java \
       apps/backend/src/main/java/io/webcontify/identity/service/AuthService.java \
       apps/backend/src/main/java/io/webcontify/identity/api/AuthController.java \
       apps/backend/src/test/
git commit -m "feat: implement effective-permissions endpoint with BatchCheck"
```

---

## Task 6: Backend — RoleService.setPermissions() + Delete Cleanup

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/identity/api/RoleController.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/service/RoleServiceTest.java`

- [ ] **Step 1: Unit Tests fuer setPermissions schreiben**

In `RoleServiceTest.java` neuen `@Nested` Block `SetPermissions`:

```java
@Nested
class SetPermissions {
    @Test
    void shouldWritePermissionTuples() { /* Custom Role bekommt inviter auf user_management */ }

    @Test
    void shouldRejectSystemRole() { /* Admin-Rolle -> BadRequestException */ }

    @Test
    void shouldRejectUnknownResourceType() { /* "unknown" -> BadRequestException */ }

    @Test
    void shouldRejectPrivilegeEscalation() {
        /* User hat nur reader, versucht editor zu vergeben -> AccessDeniedException */
    }

    @Test
    void shouldDeltaUpdatePermissions() {
        /* Rolle hat inviter, Request setzt editor -> inviter wird entfernt, editor geschrieben */
    }
}
```

- [ ] **Step 2: Tests ausfuehren — muessen fehlschlagen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*.RoleServiceTest.SetPermissions.*"`
Expected: FAIL

- [ ] **Step 3: RoleService.setPermissions() implementieren**

```java
public void setPermissions(UUID roleId, List<RolePermissionEntry> permissions) {
    var entity = roleRepository.findActiveById(roleId)
            .orElseThrow(() -> new NotFoundException("role.not_found"));

    if (entity.isSystemRole()) {
        throw new BadRequestException("role.system_role_permissions_cannot_be_modified");
    }

    var tenantInfo = TenantContext.getTenantInformation();
    var infrastructure = lookupService.getInfrastructure(tenantInfo.tenantId());
    UUID currentUserId = resolveCurrentUserId();

    // Validierung + Privilege Escalation Guard
    for (var perm : permissions) {
        var allowedActions = RESOURCE_ACTIONS.get(perm.resourceType());
        if (allowedActions == null) {
            throw new BadRequestException("role.invalid_resource_type");
        }
        for (var action : perm.actions()) {
            if (!allowedActions.contains(action)) {
                throw new BadRequestException("role.invalid_action");
            }
            // Privilege Escalation Check: pruefe ob aktueller User die Permission selbst hat
            if (!tenantInfo.platformAdmin()) {
                var checkRequest = new CheckPermissionRequest(currentUserId, action, perm.resourceType(), tenantInfo.tenantId());
                if (!permissionService.check(checkRequest)) {
                    throw new AccessDeniedException("role.privilege_escalation");
                }
            }
        }
    }

    // Aktuelle Permission-Tuples lesen
    String roleSubject = "role:" + roleId + "#assignee";
    var currentPermissions = readCurrentPermissionTuples(infrastructure.openfgaStoreId(), roleSubject, tenantInfo.tenantId());

    // Target Tuples berechnen
    var targetTuples = new HashSet<RelationshipTuple>();
    for (var perm : permissions) {
        for (var action : perm.actions()) {
            targetTuples.add(new RelationshipTuple(roleSubject, action, perm.resourceType() + ":" + tenantInfo.tenantId()));
        }
    }

    // Delta
    var toAdd = new HashSet<>(targetTuples);
    toAdd.removeAll(currentPermissions);

    var toRemove = new HashSet<>(currentPermissions);
    toRemove.removeAll(targetTuples);

    // Writes zuerst, dann Deletes (sicherere Richtung)
    if (!toAdd.isEmpty()) {
        openFgaService.write(new OpenFgaWriteRequest(infrastructure.openfgaStoreId(), List.copyOf(toAdd)));
    }
    if (!toRemove.isEmpty()) {
        openFgaService.delete(new OpenFgaDeleteRequest(infrastructure.openfgaStoreId(), List.copyOf(toRemove)));
    }

    log.info("Role permissions updated: roleId={}, added={}, removed={}", roleId, toAdd.size(), toRemove.size());
}

private Set<RelationshipTuple> readCurrentPermissionTuples(String storeId, String roleSubject, UUID tenantId) {
    var tuples = new HashSet<RelationshipTuple>();
    for (var resourceType : RESOURCE_ACTIONS.keySet()) {
        // Alle Actions fuer diese Resource in einem Call lesen (Relation = null liest alle)
        // Dann lokal auf roleSubject filtern
        var allowedActions = RESOURCE_ACTIONS.get(resourceType);
        for (var action : allowedActions) {
            var readResult = openFgaService.readTuples(new OpenFgaReadTuplesRequest(
                    storeId, resourceType, tenantId.toString(), action));
            readResult.stream()
                    .filter(t -> t.user().equals(roleSubject))
                    .forEach(tuples::add);
        }
    }
    return tuples;
}
// Hinweis: Aktuell 10 readTuples-Calls (3 Resources x 3-4 Actions). Optimierung
// (ein Read pro Resource ohne Action-Filter) haengt davon ab ob OpenFGA Read ohne
// Relation-Filter alle Tuples liefert — das muss zur Implementierungszeit geprueft werden.
```

**Wichtig:** `PermissionService` muss als neue Dependency im `RoleService`-Konstruktor hinzugefuegt werden (aktuell nicht vorhanden). In `RoleServiceTest.java` muss `@Mock PermissionService permissionService` ergaenzt werden — `@InjectMocks` injiziert den Mock automatisch.

- [ ] **Step 4: RoleController.setRolePermissions() implementieren**

```java
@Override
@RequiresPermission(action = "editor", resource = "role_management")
public ResponseEntity<RoleDetailResponse> setRolePermissions(UUID roleId, SetRolePermissionsRequest request) {
    var entries = request.getPermissions().stream()
            .map(p -> new RolePermissionEntry(p.getResourceType(), p.getActions()))
            .toList();
    roleService.setPermissions(roleId, entries);
    // Reload role detail
    return getRole(roleId);
}
```

- [ ] **Step 5: Tests ausfuehren — muessen gruen sein**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*.RoleServiceTest.SetPermissions.*"`
Expected: PASS

- [ ] **Step 6: Delete Cleanup — Test schreiben**

In `RoleServiceTest.java` den bestehenden `@Nested class Delete` Block erweitern:

```java
@Test
void shouldCleanupPermissionTuplesOnDelete() {
    // Setup: Rolle hat Permission-Tuples
    // Act: delete()
    // Verify: openFgaService.delete() wird mit Permission-Tuples aufgerufen
}
```

- [ ] **Step 7: RoleService.delete() erweitern**

Vor dem bestehenden Delete-Code die Permission-Cleanup-Logik einfuegen:

```java
// Permission-Tuples aufraumen
String roleSubject = "role:" + roleId + "#assignee";
var permissionTuples = readCurrentPermissionTuples(infrastructure.openfgaStoreId(), roleSubject, tenantInfo.tenantId());
if (!permissionTuples.isEmpty()) {
    try {
        openFgaService.delete(new OpenFgaDeleteRequest(infrastructure.openfgaStoreId(), List.copyOf(permissionTuples)));
    } catch (Exception e) {
        log.warn("OpenFGA permission cleanup failed for role={}, continuing", roleId, e);
    }
}
```

- [ ] **Step 8: getPermissionPreview auf BatchCheck migrieren**

Die bestehende `getPermissionPreview()` Methode umschreiben: statt N einzelne `check()`-Calls einen `batchCheck()` verwenden.

- [ ] **Step 9: Alle Tests gruen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: Alle Tests gruen.

- [ ] **Step 10: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/RoleService.java \
       apps/backend/src/main/java/io/webcontify/identity/api/RoleController.java \
       apps/backend/src/test/
git commit -m "feat: implement role permission management with privilege escalation guard"
```

---

## Task 7: Provisioning — Editor/Viewer Tuples anpassen

**Files:**
- Modify: `apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java`
- Modify: `apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningListenerTest.java`

- [ ] **Step 1: Test anpassen**

Im bestehenden Provisioning-Test die erwartete Tuple-Anzahl und -Inhalte anpassen: Editor/Viewer `tenant_config` reader-Tuples entfernen.

- [ ] **Step 2: TenantProvisioningListener anpassen**

Die zwei Zeilen entfernen:

```java
// ENTFERNEN:
// tuples.add(new RelationshipTuple("role:" + editorRoleId  + "#assignee", "reader", "tenant_config:" + tid));
// tuples.add(new RelationshipTuple("role:" + viewerRoleId  + "#assignee", "reader", "tenant_config:" + tid));
```

- [ ] **Step 3: Tests gruen**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: Alle Tests gruen.

- [ ] **Step 4: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java \
       apps/backend/src/test/
git commit -m "fix: remove editor/viewer singleton permission tuples from provisioning"
```

---

## Task 8: Frontend — Code Generation + usePermissions Hook

**Files:**
- Create: `apps/platform/src/hooks/use-permissions.ts`
- Create: `apps/platform/src/hooks/__tests__/use-permissions.test.ts`

- [ ] **Step 1: Orval regenerieren**

Run: `npx nx generate platform`
Expected: `src/generated/auth.ts` hat jetzt `useGetEffectivePermissions` Hook.

- [ ] **Step 2: Test fuer usePermissions schreiben**

```typescript
import { renderHook } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

// Mock der generierten Query
vi.mock("@/generated/auth", () => ({
  useGetEffectivePermissions: vi.fn(),
}));

describe("usePermissions", () => {
  it("returns can() helper that checks specific permission", () => { /* ... */ });
  it("returns canAccess() helper that checks any permission on resource", () => { /* ... */ });
  it("returns isLoading true while query is loading", () => { /* ... */ });
  it("returns all permissions for tenant admin", () => { /* ... */ });
  it("returns empty permissions when no data", () => { /* ... */ });
});
```

- [ ] **Step 3: Test ausfuehren — muss fehlschlagen**

Run: `cd apps/platform && npx vitest run src/hooks/__tests__/use-permissions.test.ts`
Expected: FAIL

- [ ] **Step 4: usePermissions Hook implementieren**

```typescript
"use client";

import { useGetEffectivePermissions } from "@/generated/auth";

export function usePermissions() {
  const query = useGetEffectivePermissions({ scope: "navigation" }, {
    query: { staleTime: 60 * 1000 },
  });

  const permissions = query.data?.permissions ?? {};
  const isTenantAdmin = query.data?.isTenantAdmin ?? false;

  function can(resource: string, action: string): boolean {
    return permissions[resource]?.includes(action) ?? false;
  }

  function canAccess(resource: string): boolean {
    return (permissions[resource]?.length ?? 0) > 0;
  }

  return {
    permissions,
    isTenantAdmin,
    isLoading: query.isLoading,
    can,
    canAccess,
  };
}
```

- [ ] **Step 5: Tests gruen**

Run: `cd apps/platform && npx vitest run src/hooks/__tests__/use-permissions.test.ts`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add apps/platform/src/hooks/use-permissions.ts \
       apps/platform/src/hooks/__tests__/use-permissions.test.ts \
       apps/platform/src/generated/
git commit -m "feat: add usePermissions hook with effective permissions query"
```

---

## Task 9: Frontend — Permission-Aware Navigation

**Files:**
- Modify: `apps/platform/src/components/layout/nav-config.ts`
- Modify: `apps/platform/src/components/layout/__tests__/nav-config.test.ts`
- Modify: `apps/platform/src/app/(workspace)/layout.tsx`

- [ ] **Step 1: nav-config.test.ts anpassen**

Tests aktualisieren: `isAdmin: boolean` durch granulare Flags ersetzen:

```typescript
describe("getWorkspaceNavItems", () => {
  it("shows only overview when no permissions", () => {
    const sections = getWorkspaceNavItems({
      canAccessUserManagement: false,
      canAccessRoleManagement: false,
      canAccessTenantConfig: false,
    }, mockT);
    expect(sections).toHaveLength(1);
    expect(sections[0].label).toBe("nav.sidebar.overview");
  });

  it("shows management section when user has user_management access", () => {
    const sections = getWorkspaceNavItems({
      canAccessUserManagement: true,
      canAccessRoleManagement: false,
      canAccessTenantConfig: false,
    }, mockT);
    const mgmt = sections.find(s => s.label === "nav.sidebar.management");
    expect(mgmt).toBeDefined();
    expect(mgmt!.items).toHaveLength(1); // nur Users, nicht Roles
  });

  it("shows settings section when user has tenant_config access", () => { /* ... */ });
  it("shows all sections for full admin", () => { /* ... */ });
});
```

- [ ] **Step 2: Tests ausfuehren — muessen fehlschlagen**

Run: `cd apps/platform && npx vitest run src/components/layout/__tests__/nav-config.test.ts`
Expected: FAIL (Signatur hat sich geaendert)

- [ ] **Step 3: nav-config.ts anpassen**

```typescript
export interface WorkspaceNavOptions {
  canAccessUserManagement: boolean;
  canAccessRoleManagement: boolean;
  canAccessTenantConfig: boolean;
}

export function getWorkspaceNavItems(options: WorkspaceNavOptions, t: TranslationFn): NavSection[] {
  const sections: NavSection[] = [
    {
      label: t("sidebar.overview"),
      items: [
        { label: t("sidebar.dashboard"), href: "/dashboard", icon: LayoutDashboard },
        { label: t("sidebar.apps"), href: "/apps", icon: AppWindow },
      ],
    },
  ];

  const managementItems: NavItem[] = [];
  if (options.canAccessUserManagement) {
    managementItems.push({ label: t("sidebar.users"), href: "/users", icon: Users });
  }
  if (options.canAccessRoleManagement) {
    managementItems.push({ label: t("sidebar.roles"), href: "/roles", icon: Shield });
  }
  if (managementItems.length > 0) {
    sections.push({ label: t("sidebar.management"), items: managementItems });
  }

  if (options.canAccessTenantConfig) {
    sections.push({
      label: t("sidebar.settings"),
      items: [
        { label: t("sidebar.branding"), href: "/settings/branding", icon: Palette },
        { label: t("sidebar.email"), href: "/settings/email", icon: Mail },
        { label: t("sidebar.domain"), href: "/settings/domain", icon: Globe },
      ],
    });
  }

  return sections;
}
```

- [ ] **Step 4: layout.tsx anpassen**

```typescript
"use client";

import { useTranslations } from "next-intl";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getWorkspaceNavItems } from "@/components/layout/nav-config";
import { usePermissions } from "@/hooks/use-permissions";

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  const t = useTranslations("nav");
  const { canAccess, isLoading } = usePermissions();

  const sections = getWorkspaceNavItems({
    canAccessUserManagement: canAccess("user_management"),
    canAccessRoleManagement: canAccess("role_management"),
    canAccessTenantConfig: canAccess("tenant_config"),
  }, t);

  return (
    <SidebarProvider>
      <AppSidebar sections={sections} isLoading={isLoading} />
      <SidebarInset>
        <main id="main-content">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
```

Hinweis: `AppSidebar` braucht ggf. ein `isLoading` Prop fuer Skeleton-State in den dynamischen Sections.

- [ ] **Step 5: Tests gruen**

Run: `cd apps/platform && npx vitest run src/components/layout/__tests__/nav-config.test.ts`
Expected: PASS

- [ ] **Step 6: Frontend Build pruefen**

Run: `cd apps/platform && npx next build`
Expected: Build erfolgreich.

- [ ] **Step 7: Commit**

```bash
git add apps/platform/src/components/layout/nav-config.ts \
       apps/platform/src/components/layout/__tests__/nav-config.test.ts \
       apps/platform/src/app/\(workspace\)/layout.tsx
git commit -m "feat: permission-aware workspace navigation"
```

---

## Task 10: Frontend — Permission Guards auf Workspace-Seiten

**Files:**
- Modify: `apps/platform/src/app/(workspace)/users/page.tsx`
- Modify: `apps/platform/src/app/(workspace)/roles/page.tsx`
- Modify: `apps/platform/src/app/(workspace)/settings/branding/page.tsx`
- Modify: `apps/platform/src/app/(workspace)/settings/email/page.tsx`
- Modify: `apps/platform/src/app/(workspace)/settings/domain/page.tsx`

- [ ] **Step 1: i18n Keys hinzufuegen**

In `en.json` und `de.json`:

```json
"errors": {
  "forbidden": "You do not have permission for this action",
  "noAccess": "You do not have access to this area"
}
```

(DE: `"forbidden": "Keine Berechtigung fuer diese Aktion"`, `"noAccess": "Kein Zugriff auf diesen Bereich"`)

- [ ] **Step 2: Permission Guard Pattern auf jede Seite anwenden**

Pattern fuer jede geschuetzte Seite (Beispiel `users/page.tsx`):

```typescript
const { canAccess, isLoading } = usePermissions();
const router = useRouter();

useEffect(() => {
  if (!isLoading && !canAccess("user_management")) {
    toast.error(t("errors.noAccess"));
    router.replace("/dashboard");
  }
}, [isLoading, canAccess]);

if (isLoading || !canAccess("user_management")) {
  return <PageSkeleton />;
}
```

Anwenden auf:
- `users/page.tsx` → `canAccess("user_management")`
- `roles/page.tsx` → `canAccess("role_management")`
- `settings/branding/page.tsx` → `canAccess("tenant_config")`
- `settings/email/page.tsx` → `canAccess("tenant_config")`
- `settings/domain/page.tsx` → `canAccess("tenant_config")`

- [ ] **Step 3: Frontend Build pruefen**

Run: `cd apps/platform && npx next build`
Expected: Build erfolgreich.

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/app/\(workspace\)/users/page.tsx \
       apps/platform/src/app/\(workspace\)/roles/page.tsx \
       apps/platform/src/app/\(workspace\)/settings/ \
       apps/platform/src/messages/
git commit -m "feat: add permission guards to workspace pages"
```

---

## Task 11: Frontend — Editable Permission Toggles

**Files:**
- Create: `apps/platform/src/features/roles/components/role-permissions-editor.tsx`
- Modify: `apps/platform/src/app/(workspace)/roles/[roleId]/page.tsx`
- Delete (oder ersetzen): `apps/platform/src/features/roles/components/role-permissions-preview.tsx`

- [ ] **Step 1: role-permissions-editor.tsx implementieren**

Checkbox-basiertes UI pro Resource-Gruppe:
- System-Rollen: Alle Checkboxen disabled + Info-Banner
- Custom Roles: Checkboxen editierbar
- `reader` automatisch als checked anzeigen wenn andere Action aktiv (disabled, visuell)
- Privilege Escalation: Checkboxen fuer Permissions die der aktuelle User nicht hat disabled + Tooltip
- Dirty State: Speichern-Button nur wenn Aenderungen, Abbrechen-Button zum Reset
- Submit: `useSetRolePermissions()` Mutation aufrufen, bei Erfolg `getGetRoleQueryKey()` invalidieren

React Hook Form + Zod Schema fuer das Formular:

```typescript
const permissionSchema = z.object({
  permissions: z.array(z.object({
    resourceType: z.string(),
    actions: z.array(z.string()),
  })),
});
```

- [ ] **Step 2: Role Detail Page anpassen**

In `roles/[roleId]/page.tsx` den Permissions-Tab aendern:
- `<RolePermissionsPreview>` ersetzen durch `<RolePermissionsEditor>`
- Props: `roleId`, `permissions` (aktuelle), `isSystemRole`, `onSaved`

- [ ] **Step 3: i18n Keys fuer Permission-Editor**

In `en.json` und `de.json` unter `roles.permissions`:
- `systemRoleNotice`: "Permissions for system roles cannot be changed"
- `escalationTooltip`: "You do not have this permission yourself"
- `save`: "Save"
- `cancel`: "Cancel"
- `saveSuccess`: "Permissions updated"
- `saveError`: "Failed to update permissions"

- [ ] **Step 4: Frontend Build pruefen**

Run: `cd apps/platform && npx next build`
Expected: Build erfolgreich.

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/features/roles/components/role-permissions-editor.tsx \
       apps/platform/src/app/\(workspace\)/roles/\[roleId\]/page.tsx \
       apps/platform/src/messages/
git commit -m "feat: editable permission toggles on role detail page"
```

---

## Task 12: E2E Tests — Permission-Aware Navigation

**Files:**
- Create: `apps/e2e/tests/permissions/navigation-permissions.spec.ts`

E2E Tests pruefen den vollen Flow: UI → API → OpenFGA → Navigation-Sichtbarkeit.

**Konventionen (aus `memory/feedback_e2e_conventions.md`):**
- English only, `data-testid` Selectors
- `test.describe.serial()` fuer sequentielle Tests
- `apiClient.registerForCleanup()` fuer LIFO-Cleanup
- Keycloak User direkt via Admin API erstellen

- [ ] **Step 1: Test-File erstellen**

```typescript
import { test, expect } from "@playwright/test";
import { ApiClient } from "../../helpers/api";
import { loginAsTenantUser, loginAsAdmin } from "../../helpers/auth";
import {
  createKeycloakUser,
  setUserPassword,
  clearRequiredActions,
  enableDirectAccessGrants,
} from "../../helpers/keycloak-admin";

test.describe.serial("Permission-Aware Navigation", () => {
  let apiClient: ApiClient;
  let tenantSlug: string;
  let tenantId: string;

  // Rollen-IDs
  let adminRoleId: string;
  let viewerRoleId: string;
  let customRoleId: string;

  // Test-User Credentials
  const viewerEmail = `viewer-${Date.now()}@test.com`;
  const viewerPassword = "Test1234!";
  const customEmail = `custom-${Date.now()}@test.com`;
  const customPassword = "Test1234!";

  test.beforeAll(async () => {
    apiClient = new ApiClient();

    // 1. Tenant erstellen + warten auf Provisioning
    const tenant = await apiClient.createTenant(`perm-test-${Date.now()}`, {
      adminEmail: `admin-${Date.now()}@test.com`,
      adminFirstName: "Admin",
      adminLastName: "User",
    });
    tenantId = tenant.id;
    tenantSlug = tenant.slug;
    await apiClient.waitForTenantProvisioning(tenantId);

    // 2. Direct Access Grants aktivieren fuer Token-Abruf
    const keycloakToken = await apiClient.getKeycloakAdminToken();
    const realmName = `tenant-${tenantSlug}`;
    await enableDirectAccessGrants(keycloakToken, realmName);

    // 3. Rollen-IDs holen (Admin, Viewer)
    const tenantToken = await apiClient.getTenantAdminToken(realmName);
    const roles = await apiClient.listRoles(tenantToken);
    adminRoleId = roles.items.find((r: any) => r.name === "Admin")!.id;
    viewerRoleId = roles.items.find((r: any) => r.name === "Viewer")!.id;

    // 4. Custom Role mit user_management:reader erstellen
    const customRole = await apiClient.createRole(tenantToken, {
      name: "Nav Tester",
      description: "E2E test role",
    });
    customRoleId = customRole.id;

    // 5. Permissions auf Custom Role setzen: nur user_management reader
    await apiClient.setRolePermissions(tenantToken, customRoleId, {
      permissions: [
        { resourceType: "user_management", actions: ["reader"] },
      ],
    });

    // 6. Viewer User erstellen (hat Viewer-Rolle = keine Singleton-Perms)
    const viewerKeycloakId = await createKeycloakUser(keycloakToken, realmName, {
      email: viewerEmail,
      firstName: "Viewer",
      lastName: "User",
    });
    await setUserPassword(keycloakToken, realmName, viewerKeycloakId, viewerPassword);
    await clearRequiredActions(keycloakToken, realmName, viewerKeycloakId);
    // Invite user via API so they exist in DB + get Viewer role
    await apiClient.inviteUser(tenantToken, {
      email: viewerEmail,
      firstName: "Viewer",
      lastName: "User",
      roleIds: [viewerRoleId],
    });

    // 7. Custom User erstellen (hat Custom Role = user_management:reader)
    const customKeycloakId = await createKeycloakUser(keycloakToken, realmName, {
      email: customEmail,
      firstName: "Custom",
      lastName: "User",
    });
    await setUserPassword(keycloakToken, realmName, customKeycloakId, customPassword);
    await clearRequiredActions(keycloakToken, realmName, customKeycloakId);
    await apiClient.inviteUser(tenantToken, {
      email: customEmail,
      firstName: "Custom",
      lastName: "User",
      roleIds: [customRoleId],
    });
  });

  test.afterAll(async () => {
    await apiClient.cleanup();
  });

  test("viewer sees only Dashboard and Apps in sidebar", async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, viewerEmail, viewerPassword);
    await page.waitForLoadState("networkidle");

    // Dashboard + Apps sichtbar
    await expect(page.getByTestId("nav-item-dashboard")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-apps")).toBeVisible();

    // Management + Settings NICHT sichtbar
    await expect(page.getByTestId("nav-item-users")).not.toBeVisible();
    await expect(page.getByTestId("nav-item-roles")).not.toBeVisible();
    await expect(page.getByTestId("nav-item-branding")).not.toBeVisible();
  });

  test("viewer gets redirected when navigating to /users directly", async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, viewerEmail, viewerPassword);
    await page.goto(`http://${tenantSlug}.localhost/users`);
    await page.waitForLoadState("networkidle");

    // Redirect auf Dashboard
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });
  });

  test("custom role user sees Users menu but not Roles or Settings", async ({ page }) => {
    await loginAsTenantUser(page, tenantSlug, customEmail, customPassword);
    await page.waitForLoadState("networkidle");

    // Dashboard + Apps + Users sichtbar (hat user_management:reader)
    await expect(page.getByTestId("nav-item-dashboard")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-users")).toBeVisible();

    // Roles + Settings NICHT sichtbar
    await expect(page.getByTestId("nav-item-roles")).not.toBeVisible();
    await expect(page.getByTestId("nav-item-branding")).not.toBeVisible();
  });

  test("admin sees all menu items", async ({ page }) => {
    // Login als der beim Provisioning erstellte Admin
    // (der Admin-User wird beim Provisioning mit Admin-Rolle erstellt)
    await loginAsAdmin(page);
    // Navigiere zum Tenant
    await page.goto(`http://${tenantSlug}.localhost/dashboard`);
    await page.waitForLoadState("networkidle");

    // Alle Items sichtbar
    await expect(page.getByTestId("nav-item-dashboard")).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId("nav-item-users")).toBeVisible();
    await expect(page.getByTestId("nav-item-roles")).toBeVisible();
    await expect(page.getByTestId("nav-item-branding")).toBeVisible();
  });

  test("admin can edit permissions on custom role", async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(`http://${tenantSlug}.localhost/roles`);
    await page.waitForLoadState("networkidle");

    // Custom Role finden und oeffnen
    await page.getByText("Nav Tester").click();
    await page.waitForLoadState("networkidle");

    // Permissions Tab oeffnen
    await page.getByTestId("role-permissions-tab").click();

    // Checkbox fuer user_management:reader sollte aktiv sein
    const readerCheckbox = page.getByTestId("permission-user_management-reader");
    await expect(readerCheckbox).toBeChecked({ timeout: 5000 });

    // Inviter hinzufuegen
    const inviterCheckbox = page.getByTestId("permission-user_management-inviter");
    await inviterCheckbox.check();

    // Speichern
    await page.getByTestId("save-permissions-button").click();

    // Erfolgs-Toast
    await expect(page.getByText(/permissions updated|berechtigungen aktualisiert/i))
      .toBeVisible({ timeout: 5000 });
  });

  test("system role permissions are read-only", async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(`http://${tenantSlug}.localhost/roles`);
    await page.waitForLoadState("networkidle");

    // Admin-Rolle oeffnen
    await page.getByText("Admin").first().click();
    await page.waitForLoadState("networkidle");

    // Permissions Tab
    await page.getByTestId("role-permissions-tab").click();

    // Info-Banner sichtbar
    await expect(page.getByTestId("system-role-permissions-notice")).toBeVisible({ timeout: 5000 });

    // Save-Button nicht sichtbar
    await expect(page.getByTestId("save-permissions-button")).not.toBeVisible();
  });
});
```

Hinweis: Die `data-testid` Werte wie `nav-item-dashboard`, `permission-user_management-reader`, `save-permissions-button`, `system-role-permissions-notice`, `role-permissions-tab` muessen in den entsprechenden Frontend-Komponenten (Tasks 9-11) gesetzt werden. Die E2E-Tests definieren die erwarteten Test-IDs — die Implementierung muss diese liefern.

- [ ] **Step 2: data-testid Attribute sicherstellen**

Folgende `data-testid` muessen in den Frontend-Komponenten vorhanden sein (ggf. in Tasks 9-11 ergaenzen):

Navigation (`nav-config.ts` / `app-sidebar.tsx`):
- `nav-item-dashboard`, `nav-item-apps`, `nav-item-users`, `nav-item-roles`
- `nav-item-branding`, `nav-item-email`, `nav-item-domain`

Permission Editor (`role-permissions-editor.tsx`):
- `permission-{resourceType}-{action}` (z.B. `permission-user_management-reader`)
- `save-permissions-button`, `cancel-permissions-button`
- `system-role-permissions-notice`

Role Detail (`roles/[roleId]/page.tsx`):
- `role-permissions-tab`

- [ ] **Step 3: API Helper-Methoden ergaenzen (falls noetig)**

In `apps/e2e/helpers/api.ts` ggf. neue Methoden:
- `setRolePermissions(token, roleId, body)` — `PUT /api/v1/tenant/roles/{roleId}/permissions`
- `listRoles(token)` — `GET /api/v1/tenant/roles` (falls nicht vorhanden)

- [ ] **Step 4: E2E Tests ausfuehren**

Run: `npx nx e2e e2e --grep "Permission-Aware Navigation"`
Expected: Alle 6 Tests gruen.

Hinweis: Erfordert laufende Infrastruktur (`npx nx up infra`, Backend, Frontend).

- [ ] **Step 5: Commit**

```bash
git add apps/e2e/tests/permissions/navigation-permissions.spec.ts \
       apps/e2e/helpers/
git commit -m "test: add E2E tests for permission-aware navigation"
```

---

## Task 13: Alle Tests + Build Verification

**Files:** keine neuen

- [ ] **Step 1: Backend Tests**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: Alle Tests gruen.

- [ ] **Step 2: Frontend Tests**

Run: `cd apps/platform && npx vitest run`
Expected: Alle Tests gruen.

- [ ] **Step 3: Frontend Build**

Run: `cd apps/platform && npx next build`
Expected: Build erfolgreich.

- [ ] **Step 4: PROGRESS.md updaten**

Phase 3h Tabelle in `docs/PROGRESS.md` hinzufuegen:

```markdown
## Phase 3h — Permission-Aware Navigation (2026-03-22)

| Task | Status | Beschreibung |
|---|---|---|
| 3h.1 | Done | OpenFGA Model: tenant_config Typ hinzugefuegt |
| 3h.2 | Done | OpenFgaService: batchCheck() + Thread-Safety Fix |
| 3h.3 | Done | OpenAPI Spec: effective-permissions + set-role-permissions Endpoints |
| 3h.4 | Done | AuthService: getEffectivePermissions() mit BatchCheck |
| 3h.5 | Done | RoleService: setPermissions() + Delete Cleanup + BatchCheck Migration |
| 3h.6 | Done | Provisioning: Editor/Viewer Singleton-Tuples entfernt |
| 3h.7 | Done | Frontend: usePermissions Hook + Orval Regeneration |
| 3h.8 | Done | Frontend: Permission-aware Navigation |
| 3h.9 | Done | Frontend: Permission Guards auf Workspace-Seiten |
| 3h.10 | Done | Frontend: Editierbare Permission-Toggles auf Role-Detail |
| 3h.11 | Done | E2E Tests: Permission-Aware Navigation (6 Tests) |
| 3h.12 | Done | Build Verification: Backend + Frontend Tests gruen, Build OK |
```

- [ ] **Step 5: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: add Phase 3h progress"
```
