# Phase 3a: Identity & Access Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate OpenFGA authorization and Keycloak admin into the backend, migrate tenant provisioning to async, and add permission check infrastructure.

**Architecture:** Identity module (Spring Modulith) with OpenFgaService, KeycloakAdminService, PermissionService. Async provisioning via Modulith Event Publication Registry. Annotation-based permission checks with AOP. Frontend gets dynamic auth and provisioning status UI.

**Tech Stack:** Spring Boot 4.0.3, OpenFGA Java SDK 0.7.x, Keycloak Admin Client 26.0.0, Caffeine Cache, Next.js 16, NextAuth v5

**Spec:** `docs/superpowers/specs/2026-03-12-phase3a-identity-infrastructure-design.md`

---

## Chunk 1: Infrastructure & Dependencies

### Task 1: Gradle Dependencies

**Files:**
- Modify: `apps/backend/build.gradle.kts`

- [ ] **Step 1: Add new dependencies**

Add to `dependencies` block in `apps/backend/build.gradle.kts`:

```kotlin
// OpenFGA Java SDK
implementation("dev.openfga:openfga-sdk:0.7.2")

// Keycloak Admin Client
implementation("org.keycloak:keycloak-admin-client:26.0.0")

// Caffeine Cache
implementation("com.github.ben-manes.caffeine:caffeine")
implementation("org.springframework.boot:spring-boot-starter-cache")

// AOP for @RequiresPermission
implementation("org.springframework.boot:spring-boot-starter-aop")
```

Note: `spring-modulith-starter-jpa` is already present and includes JDBC support for the Event Publication Registry. The event_publication table already exists (`V20260304_001`). No additional modulith dependency needed.

- [ ] **Step 2: Verify build compiles**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add apps/backend/build.gradle.kts
git commit -m "build: add OpenFGA SDK, Keycloak Admin Client, Caffeine, AOP dependencies"
```

---

### Task 2: Pin OpenFGA Docker Version & Traefik Wildcard

**Files:**
- Modify: `infra/docker-compose.dev.yml`

- [ ] **Step 1: Pin OpenFGA to v1.8.1 and add Traefik wildcard**

In `infra/docker-compose.dev.yml`:

1. Change `openfga/openfga:latest` → `openfga/openfga:v1.8.1` (both `openfga` and `openfga-migrate` services)
2. Add wildcard Traefik rule to the `platform` service (Next.js) with lower priority than specific rules:

```yaml
# On platform service labels, add:
- "traefik.http.routers.platform-wildcard.rule=HostRegexp(`{subdomain:[a-z0-9-]+}.localhost`)"
- "traefik.http.routers.platform-wildcard.priority=1"
- "traefik.http.routers.platform-wildcard.entrypoints=web"
```

Also add explicit `priority=10` to all existing specific rules (`api.localhost`, `auth.localhost`, `mail.localhost`, `s3.localhost`, `fga.localhost`) to ensure they always take precedence over the wildcard (priority=1). HostRegexp rules can have different default priority behavior than Host() rules — explicit priority is safer.

3. Update CORS in `SecurityConfiguration.java`: Add pattern matching for `*.localhost` subdomains. Change the CORS allowed origins from hardcoded `platform.localhost` to a pattern:

```java
.allowedOriginPatterns("http://*.localhost")
```

- [ ] **Step 2: Verify docker compose config is valid**

Run: `cd infra && docker compose -f docker-compose.dev.yml config --quiet`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add infra/docker-compose.dev.yml apps/backend/src/main/java/io/webcontify/shared/security/SecurityConfiguration.java
git commit -m "chore: pin OpenFGA v1.8.1, add Traefik wildcard for tenant subdomains, CORS pattern"
```

---

### Task 3: Flyway Migration — New Tenant Fields

**Files:**
- Create: `apps/backend/src/main/resources/db/migration/V20260312_001__add_tenant_infrastructure_fields.sql`

- [ ] **Step 1: Write migration SQL**

```sql
-- Add infrastructure fields to tenant table
ALTER TABLE tenant ADD COLUMN openfga_store_id VARCHAR(255);
ALTER TABLE tenant ADD COLUMN keycloak_realm_name VARCHAR(255);
ALTER TABLE tenant ADD COLUMN provisioning_status JSONB;

-- Unique constraint on tenant name
ALTER TABLE tenant ADD CONSTRAINT uq_tenant_name UNIQUE (name);
```

- [ ] **Step 2: Update TenantEntity JPA class**

In `apps/backend/src/main/java/io/webcontify/tenant/repository/entity/TenantEntity.java`, add fields:

```java
@Column(name = "openfga_store_id")
private String openfgaStoreId;

@Column(name = "keycloak_realm_name")
private String keycloakRealmName;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "provisioning_status", columnDefinition = "jsonb")
private String provisioningStatus;
```

Add getters and setters.

- [ ] **Step 3: Add PROVISIONING to TenantStatus enum**

In `apps/backend/src/main/java/io/webcontify/tenant/model/TenantStatus.java`:

```java
public enum TenantStatus {
    PROVISIONING,
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
```

- [ ] **Step 4: Update Tenant domain model**

In `apps/backend/src/main/java/io/webcontify/tenant/model/Tenant.java`, add fields:

```java
private String openfgaStoreId;
private String keycloakRealmName;
private String provisioningStatus; // JSONB as String
```

- [ ] **Step 5: Update TenantMapper**

In `apps/backend/src/main/java/io/webcontify/tenant/mapper/TenantMapper.java`, add mappings for the new fields between Entity ↔ Domain model. The `provisioningStatus` field is stored as String (JSONB). Serialization/deserialization uses `ProvisioningStatus.toJson()` and `ProvisioningStatus.fromJson()` (defined in Task 18). In `TenantService.create()` (Task 19), the initial JSON is set via `ProvisioningStatus.initial().toJson()`. The TenantMapper maps provisioningStatus as a plain String pass-through — the JSON parsing happens only when the provisioning logic needs to interpret the steps. For the API response, the TenantMapper must also map the String to the generated `ProvisioningStatus` DTO — use Jackson ObjectMapper to parse the JSON String into the generated schema type.

- [ ] **Step 6: Add PROVISIONING to OpenAPI TenantStatus enum**

In `openapi/tenant-api.yaml`, find the `TenantStatus` schema and add `PROVISIONING`:

```yaml
TenantStatus:
  type: string
  enum:
    - PROVISIONING
    - ACTIVE
    - INACTIVE
    - SUSPENDED
```

- [ ] **Step 7: Regenerate OpenAPI code**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate`
Expected: BUILD SUCCESSFUL, generated code includes PROVISIONING status

- [ ] **Step 8: Verify build + existing tests pass**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build`
Expected: BUILD SUCCESSFUL, all tests green

- [ ] **Step 9: Commit**

```bash
git add apps/backend/src/main/resources/db/migration/ apps/backend/src/main/java/io/webcontify/tenant/ openapi/tenant-api.yaml
git commit -m "feat: add tenant infrastructure fields, PROVISIONING status, Flyway migration"
```

---

### Task 4: OpenAPI Spec — Update CreateTenantRequest & Add Retry Endpoint

**Files:**
- Modify: `openapi/tenant-api.yaml`

- [ ] **Step 1: Update CreateTenantRequest schema**

Replace the existing `CreateTenantRequest` schema:

```yaml
CreateTenantRequest:
  type: object
  required:
    - name
    - adminEmail
    - adminFirstName
    - adminLastName
  properties:
    name:
      type: string
      minLength: 1
      maxLength: 255
    adminEmail:
      type: string
      format: email
      maxLength: 255
    adminFirstName:
      type: string
      minLength: 1
      maxLength: 255
    adminLastName:
      type: string
      minLength: 1
      maxLength: 255
```

- [ ] **Step 2: Add provisioningStatus to TenantResponse**

Add to `TenantResponse` properties:

```yaml
openfgaStoreId:
  type: string
  nullable: true
keycloakRealmName:
  type: string
  nullable: true
provisioningStatus:
  $ref: '#/components/schemas/ProvisioningStatus'
  nullable: true
```

Add new schemas:

```yaml
ProvisioningStatus:
  type: object
  properties:
    steps:
      type: array
      items:
        $ref: '#/components/schemas/ProvisioningStepInfo'

ProvisioningStepInfo:
  type: object
  required:
    - step
    - status
  properties:
    step:
      type: string
      enum:
        - S3_BUCKET
        - KEYCLOAK_REALM
        - OPENFGA_STORE
        - INITIAL_ADMIN
    status:
      type: string
      enum:
        - PENDING
        - IN_PROGRESS
        - COMPLETED
        - FAILED
    completedAt:
      type: string
      format: date-time
      nullable: true
    error:
      type: string
      nullable: true
```

- [ ] **Step 3: Add retry-provisioning endpoint**

Add under `/api/v1/admin/tenants/{tenantId}`:

```yaml
/api/v1/admin/tenants/{tenantId}/retry-provisioning:
  post:
    operationId: retryTenantProvisioning
    summary: Retry failed provisioning steps
    tags:
      - Tenant Admin
    parameters:
      - $ref: '#/components/parameters/TenantId'
    responses:
      '202':
        description: Provisioning retry initiated
      '404':
        $ref: '#/components/responses/NotFound'
      '409':
        description: Tenant is not in PROVISIONING status
```

- [ ] **Step 4: Add name uniqueness check endpoint**

Add endpoint for frontend validation:

```yaml
/api/v1/admin/tenants/check-name:
  get:
    operationId: checkTenantNameAvailability
    summary: Check if tenant name is available
    tags:
      - Tenant Admin
    parameters:
      - name: name
        in: query
        required: true
        schema:
          type: string
    responses:
      '200':
        description: Name availability result
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/NameAvailabilityResponse'

# Add schema:
NameAvailabilityResponse:
  type: object
  required:
    - available
  properties:
    available:
      type: boolean
    suggestedSlug:
      type: string
```

- [ ] **Step 5: Regenerate OpenAPI code (backend + frontend)**

Run:
```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew openApiGenerate
npx nx generate platform
```

- [ ] **Step 6: Fix compilation errors from changed CreateTenantRequest**

The generated `CreateTenantRequest` no longer has `slug`. Update:
- `TenantMapper.java` — update mapping from new request fields, ignore slug (generated in service)
- `TenantAdminController` (or generated interface) — verify method signature

- [ ] **Step 7: Verify build**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add openapi/tenant-api.yaml apps/backend/ apps/platform/src/generated/
git commit -m "feat: update OpenAPI spec with new CreateTenantRequest, provisioning status, retry endpoint"
```

---

### Task 5: Slug Generator (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/tenant/service/SlugGenerator.java`
- Create: `apps/backend/src/test/java/io/webcontify/tenant/service/SlugGeneratorTest.java`

- [ ] **Step 1: Write failing tests**

```java
package io.webcontify.tenant.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    private final SlugGenerator slugGenerator = new SlugGenerator();

    @ParameterizedTest
    @CsvSource({
        "Acme Corporation, acme-corporation",
        "My Company, my-company",
        "UPPER CASE, upper-case",
        "already-slug, already-slug",
        "  spaces  around  , spaces-around",
        "Über Grüße, ueber-gruesse",
        "Café & Bar, cafe-bar",
        "test--double, test-double",
        "123 Numbers, 123-numbers"
    })
    void shouldGenerateSlugFromName(String name, String expectedSlug) {
        assertThat(slugGenerator.generate(name)).isEqualTo(expectedSlug);
    }

    @Test
    void shouldGenerateSlugWithSuffixWhenDuplicate() {
        assertThat(slugGenerator.generateUnique("Acme", slug -> slug.equals("acme")))
            .isEqualTo("acme-2");
    }

    @Test
    void shouldIncrementSuffixUntilUnique() {
        assertThat(slugGenerator.generateUnique("Acme", slug -> slug.equals("acme") || slug.equals("acme-2")))
            .isEqualTo("acme-3");
    }

    @Test
    void shouldReturnBaseSlugWhenNoConflict() {
        assertThat(slugGenerator.generateUnique("Acme", slug -> false))
            .isEqualTo("acme");
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.tenant.service.SlugGeneratorTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement SlugGenerator**

```java
package io.webcontify.tenant.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.function.Predicate;

@Component
public class SlugGenerator {

    public String generate(String name) {
        String slug = name.trim().toLowerCase();
        // Normalize unicode and replace German umlauts
        slug = slug.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                   .replace("ß", "ss");
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD)
                         .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        // Keep only alphanumeric and spaces/hyphens
        slug = slug.replaceAll("[^a-z0-9\\s-]", "");
        // Replace whitespace with hyphens
        slug = slug.replaceAll("\\s+", "-");
        // Collapse multiple hyphens
        slug = slug.replaceAll("-{2,}", "-");
        // Trim leading/trailing hyphens
        slug = slug.replaceAll("^-|-$", "");
        return slug;
    }

    public String generateUnique(String name, Predicate<String> existsCheck) {
        String base = generate(name);
        if (!existsCheck.test(base)) {
            return base;
        }
        int suffix = 2;
        while (existsCheck.test(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.tenant.service.SlugGeneratorTest"`
Expected: PASS — all tests green

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/tenant/service/SlugGenerator.java apps/backend/src/test/java/io/webcontify/tenant/service/SlugGeneratorTest.java
git commit -m "feat: add SlugGenerator with TDD (name-to-slug, umlaut handling, uniqueness)"
```

---

### Task 6: OpenFGA Authorization Model File

**Files:**
- Create: `infra/openfga/model.fga`

- [ ] **Step 1: Create the authorization model file**

Create `infra/openfga/model.fga`:

```
model
  schema 1.1

type user

type platform
  relations
    define admin: [user]

type tenant
  relations
    define admin: [user]
    define member: [user] or admin

type role
  relations
    define tenant: [tenant]
    define assignee: [user]

type user_management
  relations
    define tenant: [tenant]
    define inviter: [user, role#assignee] or admin from tenant
    define editor: [user, role#assignee] or admin from tenant
    define deleter: [user, role#assignee] or admin from tenant
    define reader: [user, role#assignee] or editor or inviter or deleter or admin from tenant

type role_management
  relations
    define tenant: [tenant]
    define creator: [user, role#assignee] or admin from tenant
    define editor: [user, role#assignee] or admin from tenant
    define deleter: [user, role#assignee] or admin from tenant
    define reader: [user, role#assignee] or editor or creator or deleter or admin from tenant
```

- [ ] **Step 2: Commit**

```bash
git add infra/openfga/model.fga
git commit -m "feat: add OpenFGA authorization model (minimal Phase 3a)"
```

---

## Chunk 2: OpenFGA Service (Identity Module, TDD)

### Task 7: OpenFGA Configuration

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/config/OpenFgaConfiguration.java`
- Modify: `apps/backend/src/main/resources/application.yml`

- [ ] **Step 1: Add OpenFGA config properties to application.yml**

```yaml
openfga:
  api-url: http://localhost:8080
  authorization-model-path: classpath:openfga/model.fga
```

- [ ] **Step 2: Copy model.fga to backend resources**

Create `apps/backend/src/main/resources/openfga/model.fga` (copy from `infra/openfga/model.fga`). This is loaded at runtime by `OpenFgaService.createStore()` to deploy the model into new stores.

**Important:** The model file now exists in two places (`infra/openfga/model.fga` and `apps/backend/src/main/resources/openfga/model.fga`). Add a Gradle copy task to `build.gradle.kts` to sync from infra to resources at build time:

```kotlin
tasks.register<Copy>("syncOpenFgaModel") {
    from("${rootProject.projectDir}/infra/openfga/model.fga")
    into("src/main/resources/openfga/")
}
tasks.named("processResources") { dependsOn("syncOpenFgaModel") }
```

This ensures model changes in `infra/openfga/` are automatically picked up by the backend build.

- [ ] **Step 3: Create OpenFgaConfiguration**

```java
package io.webcontify.identity.config;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFgaConfiguration {

    @Value("${openfga.api-url}")
    private String apiUrl;

    @Bean
    public OpenFgaClient openFgaClient() throws Exception {
        var config = new ClientConfiguration().apiUrl(apiUrl);
        return new OpenFgaClient(config);
    }
}
```

- [ ] **Step 4: Verify build**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/ apps/backend/src/main/resources/
git commit -m "feat: add OpenFGA configuration and model resource"
```

---

### Task 8: OpenFgaService — Request/Response Records

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/CreateStoreRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/CreateStoreResponse.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaCheckRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaWriteRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaDeleteRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaListObjectsRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/OpenFgaBatchCheckRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/RelationshipTuple.java`

- [ ] **Step 1: Create all record classes**

Each record in its own file under `io.webcontify.identity.model`:

```java
// CreateStoreRequest.java
package io.webcontify.identity.model;
public record CreateStoreRequest(String tenantSlug) {}

// CreateStoreResponse.java
package io.webcontify.identity.model;
public record CreateStoreResponse(String storeId) {}

// RelationshipTuple.java
package io.webcontify.identity.model;
public record RelationshipTuple(String user, String relation, String object) {}

// OpenFgaCheckRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record OpenFgaCheckRequest(String storeId, UUID userId, String action, String resourceType, UUID resourceId) {}

// OpenFgaWriteRequest.java
package io.webcontify.identity.model;
import java.util.List;
public record OpenFgaWriteRequest(String storeId, List<RelationshipTuple> tuples) {}

// OpenFgaDeleteRequest.java
package io.webcontify.identity.model;
import java.util.List;
public record OpenFgaDeleteRequest(String storeId, List<RelationshipTuple> tuples) {}

// OpenFgaListObjectsRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record OpenFgaListObjectsRequest(String storeId, UUID userId, String action, String resourceType) {}

// OpenFgaBatchCheckRequest.java
package io.webcontify.identity.model;
import java.util.List;
public record OpenFgaBatchCheckRequest(String storeId, List<OpenFgaCheckRequest> checks) {}
```

- [ ] **Step 2: Verify build**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/
git commit -m "feat: add OpenFGA request/response record types"
```

---

### Task 9: OpenFgaService — Implementation (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/OpenFgaServiceTest.java`

- [ ] **Step 1: Write failing unit tests**

**Important:** The OpenFGA Java SDK `OpenFgaClient` uses `CompletableFuture`-based async methods. The key SDK classes for mocking:
- `OpenFgaClient.createStore(ClientCreateStoreRequest)` → `CompletableFuture<CreateStoreResponse>`
- `OpenFgaClient.check(ClientCheckRequest)` → `CompletableFuture<CheckResponse>` (has `.getAllowed()`)
- `OpenFgaClient.write(ClientWriteRequest)` → `CompletableFuture<Void>`
- `OpenFgaClient.deleteTuples(ClientDeleteTuplesRequest)` → `CompletableFuture<Void>`
- `OpenFgaClient.listObjects(ClientListObjectsRequest)` → `CompletableFuture<ListObjectsResponse>`
- `OpenFgaClient.writeAuthorizationModel(WriteAuthorizationModelRequest)` → `CompletableFuture<WriteAuthorizationModelResponse>`
- Store ID is set via `ClientWriteRequest.storeId()` / options

```java
package io.webcontify.identity.service;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.*;
import dev.openfga.sdk.api.model.*;
import io.webcontify.identity.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenFgaServiceTest {

    @Mock
    private OpenFgaClient openFgaClient;

    @Mock
    private ResourceLoader resourceLoader;

    private OpenFgaService openFgaService;

    @BeforeEach
    void setUp() {
        openFgaService = new OpenFgaService(openFgaClient, "classpath:openfga/model.fga", resourceLoader);
    }

    @Test
    void createStore_shouldCreateStoreAndDeployModel() throws Exception {
        // Given
        var sdkCreateResponse = new CreateStoreResponse();
        sdkCreateResponse.setId("store-new-123");
        when(openFgaClient.createStore(any(ClientCreateStoreRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(sdkCreateResponse));

        var modelResponse = new WriteAuthorizationModelResponse();
        modelResponse.setAuthorizationModelId("model-1");
        when(openFgaClient.writeAuthorizationModel(any()))
            .thenReturn(CompletableFuture.completedFuture(modelResponse));

        when(resourceLoader.getResource("classpath:openfga/model.fga"))
            .thenReturn(new ClassPathResource("openfga/model.fga"));

        // When
        var result = openFgaService.createStore(new CreateStoreRequest("test-tenant"));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.storeId()).isEqualTo("store-new-123");
        verify(openFgaClient).createStore(any());
        verify(openFgaClient).writeAuthorizationModel(any());
    }

    @Test
    void check_shouldReturnTrueWhenAllowed() throws Exception {
        // Given
        var checkResponse = new CheckResponse();
        checkResponse.setAllowed(true);
        when(openFgaClient.check(any(ClientCheckRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(checkResponse));

        var request = new OpenFgaCheckRequest("store-123", UUID.randomUUID(), "reader", "user_management", null);

        // When
        var result = openFgaService.check(request);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void check_shouldReturnFalseWhenDenied() throws Exception {
        // Given
        var checkResponse = new CheckResponse();
        checkResponse.setAllowed(false);
        when(openFgaClient.check(any(ClientCheckRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(checkResponse));

        var request = new OpenFgaCheckRequest("store-123", UUID.randomUUID(), "editor", "user_management", null);

        // When
        var result = openFgaService.check(request);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void write_shouldWriteTuples() throws Exception {
        // Given
        when(openFgaClient.write(any(ClientWriteRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        var tuples = List.of(new RelationshipTuple("user:123", "admin", "tenant:456"));
        var request = new OpenFgaWriteRequest("store-123", tuples);

        // When
        openFgaService.write(request);

        // Then
        verify(openFgaClient).write(any(ClientWriteRequest.class));
    }

    @Test
    void delete_shouldDeleteTuples() throws Exception {
        // Given
        when(openFgaClient.deleteTuples(any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        var tuples = List.of(new RelationshipTuple("user:123", "admin", "tenant:456"));
        var request = new OpenFgaDeleteRequest("store-123", tuples);

        // When
        openFgaService.delete(request);

        // Then
        verify(openFgaClient).deleteTuples(any());
    }

    @Test
    void listObjects_shouldReturnObjectIds() throws Exception {
        // Given
        var listResponse = new ListObjectsResponse();
        listResponse.setObjects(List.of("user_management:tenant-1", "user_management:tenant-2"));
        when(openFgaClient.listObjects(any(ClientListObjectsRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(listResponse));

        var request = new OpenFgaListObjectsRequest("store-123", UUID.randomUUID(), "reader", "user_management");

        // When
        var result = openFgaService.listObjects(request);

        // Then
        assertThat(result).containsExactly("user_management:tenant-1", "user_management:tenant-2");
    }
}
```

**Note:** The exact SDK class names and method signatures may vary slightly between SDK versions. The implementing engineer should verify against `dev.openfga:openfga-sdk:0.7.2` javadocs and adjust mock types if needed. The key pattern is: mock returns `CompletableFuture.completedFuture(response)`.

- [ ] **Step 2: Run tests — verify they fail**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.OpenFgaServiceTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement OpenFgaService**

```java
package io.webcontify.identity.service;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.*;
import dev.openfga.sdk.api.model.*;
import io.webcontify.identity.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OpenFgaService {

    private final OpenFgaClient openFgaClient;
    private final String modelPath;
    private final ResourceLoader resourceLoader;

    public OpenFgaService(
            OpenFgaClient openFgaClient,
            @Value("${openfga.authorization-model-path}") String modelPath,
            ResourceLoader resourceLoader) {
        this.openFgaClient = openFgaClient;
        this.modelPath = modelPath;
        this.resourceLoader = resourceLoader;
    }

    public CreateStoreResponse createStore(CreateStoreRequest request) {
        try {
            // 1. Create store
            var createReq = new ClientCreateStoreRequest().name("tenant-" + request.tenantSlug());
            var storeResponse = openFgaClient.createStore(createReq).get();
            var storeId = storeResponse.getId();

            // 2. Read model from classpath resource
            var resource = resourceLoader.getResource(modelPath);
            var modelContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // 3. Deploy authorization model to new store
            // Parse the .fga DSL and write the model
            // Note: The SDK expects a WriteAuthorizationModelRequest with TypeDefinitions.
            // The .fga file needs to be parsed — use the OpenFGA SDK's DSL parser if available,
            // or convert to JSON format. Check SDK for `ClientWriteAuthorizationModelRequest`.
            // For implementation, the engineer should use the OpenFGA CLI (`fga model transform`)
            // to convert .fga to JSON, or use the SDK's built-in parser.
            var modelReq = new WriteAuthorizationModelRequest();
            // Set schema version and type definitions from parsed model
            openFgaClient.writeAuthorizationModel(modelReq).get();

            return new CreateStoreResponse(storeId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpenFGA store for tenant: " + request.tenantSlug(), e);
        }
    }

    public boolean check(OpenFgaCheckRequest request) {
        try {
            var checkReq = new ClientCheckRequest()
                .user("user:" + request.userId())
                .relation(request.action())
                ._object(request.resourceType() + ":" +
                    (request.resourceId() != null ? request.resourceId() : request.resourceType()));

            // Set store ID via client options
            var options = new ClientCheckOptions().storeId(request.storeId());
            var response = openFgaClient.check(checkReq, options).get();
            return Boolean.TRUE.equals(response.getAllowed());
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA check failed", e);
        }
    }

    public void write(OpenFgaWriteRequest request) {
        try {
            var tupleKeys = request.tuples().stream()
                .map(t -> new ClientTupleKey()
                    .user(t.user()).relation(t.relation())._object(t.object()))
                .toList();

            var writeReq = new ClientWriteRequest().writes(tupleKeys);
            var options = new ClientWriteOptions().storeId(request.storeId());
            openFgaClient.write(writeReq, options).get();
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA write failed", e);
        }
    }

    public void delete(OpenFgaDeleteRequest request) {
        try {
            var tupleKeys = request.tuples().stream()
                .map(t -> new ClientTupleKeyWithoutCondition()
                    .user(t.user()).relation(t.relation())._object(t.object()))
                .toList();

            var deleteReq = new ClientWriteRequest().deletes(tupleKeys);
            var options = new ClientWriteOptions().storeId(request.storeId());
            openFgaClient.write(deleteReq, options).get();
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA delete failed", e);
        }
    }

    public List<String> listObjects(OpenFgaListObjectsRequest request) {
        try {
            var listReq = new ClientListObjectsRequest()
                .user("user:" + request.userId())
                .relation(request.action())
                .type(request.resourceType());

            var options = new ClientListObjectsOptions().storeId(request.storeId());
            var response = openFgaClient.listObjects(listReq, options).get();
            return response.getObjects();
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA listObjects failed", e);
        }
    }

    public Map<OpenFgaCheckRequest, Boolean> batchCheck(OpenFgaBatchCheckRequest request) {
        try {
            // Execute individual checks and collect results
            // OpenFGA SDK v0.7+ may have native BatchCheck — check SDK docs
            return request.checks().stream()
                .collect(Collectors.toMap(
                    check -> check,
                    this::check
                ));
        } catch (Exception e) {
            throw new RuntimeException("OpenFGA batchCheck failed", e);
        }
    }
}
```

**Note for implementing engineer:** The exact SDK class names (`ClientCheckOptions`, `ClientWriteOptions`, etc.) may differ in v0.7.2. The pattern above follows the SDK's builder style. Key points:
- Store ID is passed per-request via options objects, not set globally on the client
- The `.fga` model file needs to be parsed into `TypeDefinition` objects — check if the SDK provides a DSL parser, otherwise pre-convert to JSON using `fga model transform --file=model.fga` and store as `model.json`
- All SDK methods return `CompletableFuture` — use `.get()` for synchronous calls

- [ ] **Step 4: Run tests — verify they pass**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.OpenFgaServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/OpenFgaService.java apps/backend/src/test/java/io/webcontify/identity/service/OpenFgaServiceTest.java
git commit -m "feat: implement OpenFgaService with TDD (store, check, write, delete, list)"
```

---

### Task 10: OpenFGA Integration Test (Testcontainers)

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/OpenFgaServiceIntegrationTest.java`

- [ ] **Step 1: Write integration test with OpenFGA testcontainer**

```java
package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OpenFgaServiceIntegrationTest {

    @Container
    static GenericContainer<?> openfgaContainer = new GenericContainer<>("openfga/openfga:v1.8.1")
            .withCommand("run")
            .withExposedPorts(8080);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("openfga.api-url", () ->
            "http://" + openfgaContainer.getHost() + ":" + openfgaContainer.getMappedPort(8080));
    }

    @Autowired
    private OpenFgaService openFgaService;

    @Test
    void shouldCreateStoreAndDeployModel() {
        var response = openFgaService.createStore(new CreateStoreRequest("integration-test"));
        assertThat(response.storeId()).isNotBlank();
    }

    @Test
    void shouldWriteAndCheckPermission() {
        // Create store
        var store = openFgaService.createStore(new CreateStoreRequest("check-test"));
        var userId = UUID.randomUUID();

        // Write tenant admin relationship
        openFgaService.write(new OpenFgaWriteRequest(store.storeId(), List.of(
            new RelationshipTuple("user:" + userId, "admin", "tenant:test-tenant")
        )));

        // Check — tenant admin should have user_management reader via inheritance
        // First need to write the tenant relationship on user_management
        openFgaService.write(new OpenFgaWriteRequest(store.storeId(), List.of(
            new RelationshipTuple("tenant:test-tenant", "tenant", "user_management:test-tenant")
        )));

        var allowed = openFgaService.check(new OpenFgaCheckRequest(
            store.storeId(), userId, "reader", "user_management", null
        ));
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldDenyUnauthorizedAccess() {
        var store = openFgaService.createStore(new CreateStoreRequest("deny-test"));
        var userId = UUID.randomUUID();

        var allowed = openFgaService.check(new OpenFgaCheckRequest(
            store.storeId(), userId, "editor", "user_management", null
        ));
        assertThat(allowed).isFalse();
    }
}
```

**Context loading:** Since `@SpringBootTest` loads the full application context, this test also needs the existing PostgreSQL testcontainer (for JPA/Flyway) and potentially Keycloak. Use the existing test infrastructure from the project:
1. Check if a shared `AbstractIntegrationTest` base class or `@TestConfiguration` exists for Testcontainers setup
2. If not, create a limited context: `@SpringBootTest(classes = {OpenFgaConfiguration.class, OpenFgaService.class})` to avoid loading JPA/Keycloak beans
3. The OpenFGA container is self-contained (in-memory datastore) — no PostgreSQL dependency needed for this specific test if using limited context

Recommended: Use limited context (`@SpringBootTest(classes = ...)`) for this test since it only needs the OpenFGA client and service.

- [ ] **Step 2: Run integration test**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.OpenFgaServiceIntegrationTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/identity/service/OpenFgaServiceIntegrationTest.java
git commit -m "test: add OpenFGA integration test with Testcontainers"
```

---

## Chunk 3: Keycloak Admin Service (Identity Module, TDD)

### Task 11: Keycloak Admin Configuration

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/config/KeycloakAdminConfiguration.java`
- Modify: `apps/backend/src/main/resources/application.yml`
- Modify: `infra/keycloak/platform-realm.json`

- [ ] **Step 1: Add Service Account client to platform-realm.json**

Add a new confidential client `platform-backend` to `infra/keycloak/platform-realm.json` in the `clients` array:

```json
{
  "clientId": "platform-backend",
  "name": "Platform Backend Service Account",
  "enabled": true,
  "clientAuthenticatorType": "client-secret",
  "secret": "platform-backend-secret",
  "serviceAccountsEnabled": true,
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "publicClient": false,
  "protocol": "openid-connect"
}
```

Also configure the service account with the required master realm roles. In Keycloak 26, to create and manage other realms from a service account in a non-master realm, the service account needs:

1. Add `master-realm` client roles to the service account: `create-realm`
2. After realm creation, the service account automatically gets `realm-admin` of the new realm

In `platform-realm.json`, add to the service account user's `clientRoles`:

```json
"serviceAccountClientRoles": {
  "master-realm": ["create-realm"]
}
```

**Alternative (simpler for dev):** Use the master realm admin credentials for the service account instead of a platform-realm service account. The `KeycloakBuilder` supports this via `realm("master")`. For production, a scoped service account in the platform realm is preferred.

The implementing engineer should verify this works by:
1. Starting Keycloak with the updated realm JSON
2. Obtaining a token for the service account
3. Calling `POST /admin/realms` to create a test realm

- [ ] **Step 2: Add Keycloak admin properties to application.yml**

```yaml
keycloak:
  admin:
    server-url: http://auth.localhost
    client-id: platform-backend
    client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET:platform-backend-secret}
    realm: webcontify-platform
```

- [ ] **Step 3: Create KeycloakAdminConfiguration**

```java
package io.webcontify.identity.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfiguration {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType("client_credentials")
                .build();
    }
}
```

- [ ] **Step 4: Verify build**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/config/KeycloakAdminConfiguration.java apps/backend/src/main/resources/application.yml infra/keycloak/platform-realm.json
git commit -m "feat: add Keycloak Admin Client configuration with Service Account"
```

---

### Task 12: KeycloakAdminService — Request/Response Records

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/SmtpConfig.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/CreateRealmRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/CreateRealmResponse.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/CreateKeycloakUserRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/CreateKeycloakUserResponse.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/SendInviteEmailRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/UpdateRealmSmtpRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/UpdateRealmBrandingRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/DeleteKeycloakUserRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/UpdateKeycloakUserRequest.java`

- [ ] **Step 1: Create all record classes**

```java
// SmtpConfig.java
package io.webcontify.identity.model;
public record SmtpConfig(String host, int port, String username, String password, boolean starttls) {}

// CreateRealmRequest.java
package io.webcontify.identity.model;
public record CreateRealmRequest(String tenantSlug, SmtpConfig platformSmtpConfig) {}

// CreateRealmResponse.java
package io.webcontify.identity.model;
public record CreateRealmResponse(String realmName) {}

// CreateKeycloakUserRequest.java
package io.webcontify.identity.model;
public record CreateKeycloakUserRequest(String realmName, String email, String firstName, String lastName) {}

// CreateKeycloakUserResponse.java
package io.webcontify.identity.model;
import java.util.UUID;
public record CreateKeycloakUserResponse(UUID keycloakUserId) {}

// SendInviteEmailRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record SendInviteEmailRequest(String realmName, UUID userId) {}

// UpdateRealmSmtpRequest.java
package io.webcontify.identity.model;
public record UpdateRealmSmtpRequest(String realmName, SmtpConfig smtpConfig) {}

// UpdateRealmBrandingRequest.java
package io.webcontify.identity.model;
import java.util.Map;
public record UpdateRealmBrandingRequest(String realmName, Map<String, String> brandingAttributes) {}

// DeleteKeycloakUserRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record DeleteKeycloakUserRequest(String realmName, UUID userId) {}

// UpdateKeycloakUserRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record UpdateKeycloakUserRequest(String realmName, UUID userId, String email, String firstName, String lastName) {}
```

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/
git commit -m "feat: add Keycloak Admin request/response record types"
```

---

### Task 13: KeycloakAdminService — Implementation (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/KeycloakAdminServiceTest.java`

- [ ] **Step 1: Write failing unit tests**

```java
package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceTest {

    @Mock
    private Keycloak keycloak;
    @Mock
    private RealmsResource realmsResource;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private UserResource userResource;

    @InjectMocks
    private KeycloakAdminService keycloakAdminService;

    @Test
    void createRealm_shouldCreateRealmWithCorrectSettings() {
        when(keycloak.realms()).thenReturn(realmsResource);

        var smtp = new SmtpConfig("localhost", 1025, "", "", false);
        var request = new CreateRealmRequest("acme", smtp);

        var result = keycloakAdminService.createRealm(request);

        assertThat(result.realmName()).isEqualTo("tenant-acme");

        var realmCaptor = ArgumentCaptor.forClass(RealmRepresentation.class);
        verify(realmsResource).create(realmCaptor.capture());

        var realm = realmCaptor.getValue();
        assertThat(realm.getRealm()).isEqualTo("tenant-acme");
        assertThat(realm.isEnabled()).isTrue();
        assertThat(realm.isRegistrationAllowed()).isFalse();
        assertThat(realm.isResetPasswordAllowed()).isTrue();
        assertThat(realm.getSmtpServer()).containsEntry("host", "localhost");
        assertThat(realm.getClients()).hasSize(1);
        assertThat(realm.getClients().get(0).getClientId()).isEqualTo("tenant-ui");
        assertThat(realm.getClients().get(0).isPublicClient()).isTrue();
    }

    @Test
    void createUser_shouldCreateUserWithUpdatePasswordAction() {
        when(keycloak.realm("tenant-acme")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        var userId = UUID.randomUUID();
        var response = mock(Response.class);
        when(response.getLocation()).thenReturn(URI.create("/users/" + userId));
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);

        var request = new CreateKeycloakUserRequest("tenant-acme", "alice@example.com", "Alice", "Smith");
        var result = keycloakAdminService.createUser(request);

        assertThat(result.keycloakUserId()).isEqualTo(userId);

        var userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(userCaptor.capture());

        var user = userCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getFirstName()).isEqualTo("Alice");
        assertThat(user.getLastName()).isEqualTo("Smith");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getRequiredActions()).contains("UPDATE_PASSWORD");
    }

    @Test
    void sendInviteEmail_shouldTriggerExecuteActionsEmail() {
        var userId = UUID.randomUUID();
        when(keycloak.realm("tenant-acme")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(userId.toString())).thenReturn(userResource);

        keycloakAdminService.sendInviteEmail(new SendInviteEmailRequest("tenant-acme", userId));

        verify(userResource).executeActionsEmail(List.of("UPDATE_PASSWORD"));
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.KeycloakAdminServiceTest"`
Expected: FAIL

- [ ] **Step 3: Implement KeycloakAdminService**

```java
package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KeycloakAdminService {

    private final Keycloak keycloak;

    public KeycloakAdminService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    public CreateRealmResponse createRealm(CreateRealmRequest request) {
        String realmName = "tenant-" + request.tenantSlug();

        var realm = new RealmRepresentation();
        realm.setRealm(realmName);
        realm.setEnabled(true);
        realm.setRegistrationAllowed(false);
        realm.setResetPasswordAllowed(true);
        realm.setLoginWithEmailAllowed(true);

        // SMTP from platform default
        var smtp = request.platformSmtpConfig();
        realm.setSmtpServer(Map.of(
            "host", smtp.host(),
            "port", String.valueOf(smtp.port()),
            "user", smtp.username(),
            "password", smtp.password(),
            "starttls", String.valueOf(smtp.starttls()),
            "auth", "true"
        ));

        // Create PKCE public client
        var client = new ClientRepresentation();
        client.setClientId("tenant-ui");
        client.setPublicClient(true);
        client.setStandardFlowEnabled(true);
        client.setDirectAccessGrantsEnabled(false);
        // Set PKCE attributes, redirect URIs for {slug}.localhost
        client.setRedirectUris(List.of("http://" + request.tenantSlug() + ".localhost/*"));
        client.setWebOrigins(List.of("http://" + request.tenantSlug() + ".localhost"));
        client.setAttributes(Map.of(
            "pkce.code.challenge.method", "S256"
        ));
        realm.setClients(List.of(client));

        keycloak.realms().create(realm);
        return new CreateRealmResponse(realmName);
    }

    public CreateKeycloakUserResponse createUser(CreateKeycloakUserRequest request) {
        var user = new UserRepresentation();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        user.setEmailVerified(true); // Invited users don't need to verify
        user.setRequiredActions(List.of("UPDATE_PASSWORD"));

        var response = keycloak.realm(request.realmName()).users().create(user);
        // Extract user ID from response Location header
        String userId = response.getLocation().getPath()
            .replaceAll(".*/([^/]+)$", "$1");

        return new CreateKeycloakUserResponse(java.util.UUID.fromString(userId));
    }

    public void sendInviteEmail(SendInviteEmailRequest request) {
        keycloak.realm(request.realmName())
            .users()
            .get(request.userId().toString())
            .executeActionsEmail(List.of("UPDATE_PASSWORD"));
    }

    public void updateRealmSmtp(UpdateRealmSmtpRequest request) {
        var realm = keycloak.realm(request.realmName()).toRepresentation();
        var smtp = request.smtpConfig();
        realm.setSmtpServer(Map.of(
            "host", smtp.host(),
            "port", String.valueOf(smtp.port()),
            "user", smtp.username(),
            "password", smtp.password(),
            "starttls", String.valueOf(smtp.starttls()),
            "auth", "true"
        ));
        keycloak.realm(request.realmName()).update(realm);
    }

    public void updateRealmBranding(UpdateRealmBrandingRequest request) {
        var realm = keycloak.realm(request.realmName()).toRepresentation();
        realm.setAttributes(request.brandingAttributes());
        keycloak.realm(request.realmName()).update(realm);
    }

    public void deleteUser(DeleteKeycloakUserRequest request) {
        keycloak.realm(request.realmName())
            .users()
            .get(request.userId().toString())
            .remove();
    }

    public void updateUser(UpdateKeycloakUserRequest request) {
        var userResource = keycloak.realm(request.realmName())
            .users()
            .get(request.userId().toString());
        var user = userResource.toRepresentation();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        userResource.update(user);
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.KeycloakAdminServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/KeycloakAdminService.java apps/backend/src/test/java/io/webcontify/identity/service/KeycloakAdminServiceTest.java
git commit -m "feat: implement KeycloakAdminService with TDD (realm, user, SMTP, branding)"
```

---

## Chunk 4: Permission Check Infrastructure (TDD)

### Task 14: Caffeine Cache Configuration

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/config/CaffeineCacheConfiguration.java`

- [ ] **Step 1: Create cache configuration**

```java
package io.webcontify.identity.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CaffeineCacheConfiguration {

    @Bean
    public CacheManager cacheManager() {
        var cacheManager = new CaffeineCacheManager("tenantInfrastructure");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES));
        return cacheManager;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/config/CaffeineCacheConfiguration.java
git commit -m "feat: add Caffeine cache configuration for tenant infrastructure lookup"
```

---

### Task 15: TenantInfrastructureLookupService

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/TenantInfrastructureLookupService.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/TenantInfrastructureLookupServiceTest.java`

- [ ] **Step 1: Write failing test**

Test that the service looks up openfgaStoreId and keycloakRealmName for a tenant, and that results are cached.

```java
package io.webcontify.identity.service;

import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantInfrastructureLookupServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantInfrastructureLookupService lookupService;

    @Test
    void shouldReturnStoreIdForTenant() {
        var tenantId = UUID.randomUUID();
        var entity = new TenantEntity();
        entity.setOpenfgaStoreId("store-123");
        entity.setKeycloakRealmName("tenant-acme");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(entity));

        var result = lookupService.getInfrastructure(tenantId);

        assertThat(result.openfgaStoreId()).isEqualTo("store-123");
        assertThat(result.keycloakRealmName()).isEqualTo("tenant-acme");
    }

    @Test
    void shouldThrowWhenTenantNotFound() {
        var tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lookupService.getInfrastructure(tenantId))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

- [ ] **Step 3: Implement**

```java
package io.webcontify.identity.service;

import io.webcontify.tenant.repository.TenantRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantInfrastructureLookupService {

    private final TenantRepository tenantRepository;

    public record TenantInfrastructure(String openfgaStoreId, String keycloakRealmName) {}

    public TenantInfrastructureLookupService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Cacheable(value = "tenantInfrastructure", key = "#tenantId")
    public TenantInfrastructure getInfrastructure(UUID tenantId) {
        var entity = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        return new TenantInfrastructure(entity.getOpenfgaStoreId(), entity.getKeycloakRealmName());
    }

    @CacheEvict(value = "tenantInfrastructure", key = "#tenantId")
    public void evict(UUID tenantId) {
        // Cache eviction only
    }
}
```

Note: This service accesses `TenantRepository` which is in the `tenant` module. This is an allowed cross-module dependency for read access — the identity module depends on the tenant module's published interface (repository). If Spring Modulith structure verification flags this, expose a dedicated query interface from the tenant module.

- [ ] **Step 4: Run test — verify it passes**

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/TenantInfrastructureLookupService.java apps/backend/src/test/java/io/webcontify/identity/service/TenantInfrastructureLookupServiceTest.java
git commit -m "feat: add TenantInfrastructureLookupService with Caffeine cache (TDD)"
```

---

### Task 16: PermissionService (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/CheckPermissionRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/GrantPermissionRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/RevokePermissionRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/ListPermittedObjectsRequest.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/PermissionService.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/PermissionServiceTest.java`

- [ ] **Step 1: Create request records**

```java
// CheckPermissionRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record CheckPermissionRequest(UUID userId, String action, String resourceType, UUID resourceId) {}

// GrantPermissionRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record GrantPermissionRequest(UUID userId, String relation, String resourceType, UUID resourceId) {}

// RevokePermissionRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record RevokePermissionRequest(UUID userId, String relation, String resourceType, UUID resourceId) {}

// ListPermittedObjectsRequest.java
package io.webcontify.identity.model;
import java.util.UUID;
public record ListPermittedObjectsRequest(UUID userId, String action, String resourceType) {}
```

- [ ] **Step 2: Write failing tests**

Test PermissionService delegates to OpenFgaService with correct store ID resolved from TenantContext:

```java
package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private OpenFgaService openFgaService;
    @Mock
    private TenantInfrastructureLookupService lookupService;

    @InjectMocks
    private PermissionService permissionService;

    // TenantContext.current() is a static method that reads from SecurityContext.
    // For unit tests, use MockedStatic<TenantContext> to control it.

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String STORE_ID = "store-123";

    private void setupTenantContext(org.mockito.MockedStatic<TenantContext> mockedContext) {
        mockedContext.when(TenantContext::current)
            .thenReturn(new TenantInformation(TENANT_ID, false));
        when(lookupService.getInfrastructure(TENANT_ID))
            .thenReturn(new TenantInfrastructureLookupService.TenantInfrastructure(STORE_ID, "tenant-acme"));
    }

    @Test
    void check_shouldDelegateToOpenFgaWithResolvedStoreId() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(TenantContext.class)) {
            setupTenantContext(mockedContext);
            var userId = UUID.randomUUID();
            when(openFgaService.check(any(OpenFgaCheckRequest.class))).thenReturn(true);

            var result = permissionService.check(
                new CheckPermissionRequest(userId, "reader", "user_management", null));

            assertThat(result).isTrue();
            verify(openFgaService).check(argThat(req ->
                req.storeId().equals(STORE_ID) && req.userId().equals(userId)));
        }
    }

    @Test
    void grant_shouldWriteTupleViaOpenFga() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(TenantContext.class)) {
            setupTenantContext(mockedContext);
            var userId = UUID.randomUUID();
            var resourceId = UUID.randomUUID();

            permissionService.grant(
                new GrantPermissionRequest(userId, "admin", "tenant", resourceId));

            verify(openFgaService).write(argThat(req ->
                req.storeId().equals(STORE_ID) &&
                req.tuples().get(0).user().equals("user:" + userId)));
        }
    }

    @Test
    void revoke_shouldDeleteTupleViaOpenFga() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(TenantContext.class)) {
            setupTenantContext(mockedContext);
            var userId = UUID.randomUUID();
            var resourceId = UUID.randomUUID();

            permissionService.revoke(
                new RevokePermissionRequest(userId, "admin", "tenant", resourceId));

            verify(openFgaService).delete(argThat(req ->
                req.storeId().equals(STORE_ID)));
        }
    }

    @Test
    void listObjects_shouldDelegateAndMapToUUIDs() {
        try (var mockedContext = org.mockito.Mockito.mockStatic(TenantContext.class)) {
            setupTenantContext(mockedContext);
            var userId = UUID.randomUUID();
            var objectId = UUID.randomUUID();
            when(openFgaService.listObjects(any(OpenFgaListObjectsRequest.class)))
                .thenReturn(List.of("user_management:" + objectId));

            var result = permissionService.listObjects(
                new ListPermittedObjectsRequest(userId, "reader", "user_management"));

            assertThat(result).containsExactly(objectId);
        }
    }
}
```

- [ ] **Step 3: Run tests — verify they fail**

- [ ] **Step 4: Implement PermissionService**

```java
package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import io.webcontify.shared.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PermissionService {

    private final OpenFgaService openFgaService;
    private final TenantInfrastructureLookupService lookupService;

    public PermissionService(OpenFgaService openFgaService,
                             TenantInfrastructureLookupService lookupService) {
        this.openFgaService = openFgaService;
        this.lookupService = lookupService;
    }

    public boolean check(CheckPermissionRequest request) {
        var storeId = resolveStoreId();
        return openFgaService.check(new OpenFgaCheckRequest(
            storeId, request.userId(), request.action(),
            request.resourceType(), request.resourceId()
        ));
    }

    public void grant(GrantPermissionRequest request) {
        var storeId = resolveStoreId();
        var tuple = new RelationshipTuple(
            "user:" + request.userId(),
            request.relation(),
            request.resourceType() + ":" + request.resourceId()
        );
        openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(tuple)));
    }

    public void revoke(RevokePermissionRequest request) {
        var storeId = resolveStoreId();
        var tuple = new RelationshipTuple(
            "user:" + request.userId(),
            request.relation(),
            request.resourceType() + ":" + request.resourceId()
        );
        openFgaService.delete(new OpenFgaDeleteRequest(storeId, List.of(tuple)));
    }

    public List<UUID> listObjects(ListPermittedObjectsRequest request) {
        var storeId = resolveStoreId();
        var objects = openFgaService.listObjects(new OpenFgaListObjectsRequest(
            storeId, request.userId(), request.action(), request.resourceType()
        ));
        return objects.stream()
            .map(obj -> UUID.fromString(obj.split(":")[1]))
            .toList();
    }

    private String resolveStoreId() {
        var tenantInfo = TenantContext.current();
        return lookupService.getInfrastructure(tenantInfo.tenantId()).openfgaStoreId();
    }
}
```

- [ ] **Step 5: Run tests — verify they pass**

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/ apps/backend/src/test/java/io/webcontify/identity/
git commit -m "feat: implement PermissionService with TDD (check, grant, revoke, listObjects)"
```

---

### Task 17: @RequiresPermission Annotation + Aspect (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/security/RequiresPermission.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/security/PermissionCheckAspect.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/security/PermissionCheckAspectTest.java`

- [ ] **Step 1: Create annotation**

```java
package io.webcontify.identity.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String action();
    String resource();
    String resourceId() default "";
}
```

- [ ] **Step 2: Write failing tests for aspect**

Test cases:
- Method without annotation → no check
- Method with annotation, user has permission → proceeds
- Method with annotation, user denied → throws AccessDeniedException
- Platform admin → bypasses check
- SpEL resourceId evaluation → resolves method parameter

- [ ] **Step 3: Run tests — verify they fail**

- [ ] **Step 4: Implement PermissionCheckAspect**

```java
package io.webcontify.identity.security;

import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class PermissionCheckAspect {

    private final PermissionService permissionService;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public PermissionCheckAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        var tenantInfo = TenantContext.current();

        // Platform admin bypasses all checks
        if (tenantInfo.platformAdmin()) {
            return joinPoint.proceed();
        }

        // Extract user ID from TenantAwareAuthentication in SecurityContext
        var authentication = (io.webcontify.shared.security.TenantAwareAuthentication)
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(authentication.getToken().getSubject());
        UUID resourceId = null;

        // Evaluate SpEL for resourceId if present
        if (!requiresPermission.resourceId().isEmpty()) {
            resourceId = evaluateSpel(joinPoint, requiresPermission.resourceId());
        }

        var request = new CheckPermissionRequest(
            userId, requiresPermission.action(),
            requiresPermission.resource(), resourceId
        );

        if (!permissionService.check(request)) {
            throw new AccessDeniedException("Insufficient permissions");
        }

        return joinPoint.proceed();
    }

    private UUID evaluateSpel(ProceedingJoinPoint joinPoint, String expression) {
        var signature = (MethodSignature) joinPoint.getSignature();
        var paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        var context = new StandardEvaluationContext();

        if (paramNames != null) {
            var args = joinPoint.getArgs();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return spelParser.parseExpression(expression).getValue(context, UUID.class);
    }
}
```

The user ID is extracted from the JWT `sub` claim via `TenantAwareAuthentication.getToken().getSubject()`. The `TenantAwareAuthentication` class extends `JwtAuthenticationToken` and stores the JWT. The `sub` claim contains the Keycloak user UUID.

- [ ] **Step 5: Run tests — verify they pass**

- [ ] **Step 6: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/security/ apps/backend/src/test/java/io/webcontify/identity/security/
git commit -m "feat: implement @RequiresPermission annotation + AOP aspect with TDD"
```

---

## Chunk 5: Async Provisioning (Migration + Event-Driven)

### Task 18: Provisioning Model Classes

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/ProvisioningStepType.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/ProvisioningStepStatus.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/ProvisioningStep.java`
- Create: `apps/backend/src/main/java/io/webcontify/identity/model/ProvisioningStatus.java`

- [ ] **Step 1: Create provisioning enums and records**

```java
// ProvisioningStepType.java
package io.webcontify.identity.model;
public enum ProvisioningStepType { S3_BUCKET, KEYCLOAK_REALM, OPENFGA_STORE, INITIAL_ADMIN }

// ProvisioningStepStatus.java
package io.webcontify.identity.model;
public enum ProvisioningStepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

// ProvisioningStep.java
package io.webcontify.identity.model;
import java.time.Instant;
public record ProvisioningStep(ProvisioningStepType step, ProvisioningStepStatus status, Instant completedAt, String error) {
    public static ProvisioningStep pending(ProvisioningStepType step) {
        return new ProvisioningStep(step, ProvisioningStepStatus.PENDING, null, null);
    }
    public ProvisioningStep inProgress() {
        return new ProvisioningStep(step, ProvisioningStepStatus.IN_PROGRESS, null, null);
    }
    public ProvisioningStep completed() {
        return new ProvisioningStep(step, ProvisioningStepStatus.COMPLETED, Instant.now(), null);
    }
    public ProvisioningStep failed(String error) {
        return new ProvisioningStep(step, ProvisioningStepStatus.FAILED, null, error);
    }
}

// ProvisioningStatus.java
package io.webcontify.identity.model;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

// Includes admin details so they are available for retry without external lookups
public record ProvisioningStatus(
    List<ProvisioningStep> steps,
    String adminEmail,
    String adminFirstName,
    String adminLastName
) {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    public static ProvisioningStatus initial(String adminEmail, String adminFirstName, String adminLastName) {
        return new ProvisioningStatus(List.of(
            ProvisioningStep.pending(ProvisioningStepType.S3_BUCKET),
            ProvisioningStep.pending(ProvisioningStepType.KEYCLOAK_REALM),
            ProvisioningStep.pending(ProvisioningStepType.OPENFGA_STORE),
            ProvisioningStep.pending(ProvisioningStepType.INITIAL_ADMIN)
        ), adminEmail, adminFirstName, adminLastName);
    }

    public String toJson() {
        try { return MAPPER.writeValueAsString(this); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public static ProvisioningStatus fromJson(String json) {
        try { return MAPPER.readValue(json, ProvisioningStatus.class); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public boolean allCompleted() {
        return steps.stream().allMatch(s -> s.status() == ProvisioningStepStatus.COMPLETED);
    }

    public boolean hasFailed() {
        return steps.stream().anyMatch(s -> s.status() == ProvisioningStepStatus.FAILED);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/model/
git commit -m "feat: add provisioning status model classes"
```

---

### Task 19: TenantCreatedEvent + Migrate TenantService.create (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/tenant/event/TenantCreatedEvent.java`
- Modify: `apps/backend/src/main/java/io/webcontify/tenant/service/TenantService.java`
- Modify: `apps/backend/src/main/java/io/webcontify/tenant/service/TenantProvisioningService.java`
- Modify existing tests for TenantService

- [ ] **Step 1: Create TenantCreatedEvent**

```java
package io.webcontify.tenant.event;

import java.util.UUID;

public record TenantCreatedEvent(
    UUID tenantId,
    String slug,
    String adminEmail,
    String adminFirstName,
    String adminLastName
) {}
```

- [ ] **Step 2: Update TenantService.create()**

Modify `TenantService.create()`:
1. Accept new fields (adminEmail, adminFirstName, adminLastName) — either add params or pass through from the generated CreateTenantRequest
2. Generate slug from name using `SlugGenerator`
3. Set initial status to `PROVISIONING`
4. Set `provisioningStatus` to `ProvisioningStatus.initial().toJson()`
5. Save tenant
6. Publish `TenantCreatedEvent` via `ApplicationEventPublisher`
7. Return saved tenant

Remove: direct call to `TenantProvisioningService.provision()` from the controller/service.

- [ ] **Step 3: Update TenantProvisioningService**

Remove or deprecate the old synchronous provisioning logic. The S3 bucket creation code will be reused in the `TenantProvisioningListener` (Task 20). Keep the S3 bucket creation method but remove the orchestration.

- [ ] **Step 4: Update/fix existing tests**

Existing `TenantServiceTest` and `TenantProvisioningServiceTest` need updates:
- TenantService tests: verify event is published, status is PROVISIONING
- TenantProvisioningService tests: update for new shape

- [ ] **Step 5: Add name uniqueness check method to TenantService**

```java
public boolean isNameAvailable(String name) {
    return !tenantRepository.existsByName(name);
}
```

Add `existsByName(String name)` to `TenantRepository`.

- [ ] **Step 6: Verify all tests pass**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: All tests green

- [ ] **Step 7: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/tenant/ apps/backend/src/test/java/io/webcontify/tenant/
git commit -m "feat: migrate TenantService to async provisioning with TenantCreatedEvent"
```

---

### Task 20: TenantProvisioningListener (TDD)

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningListenerTest.java`

- [ ] **Step 1: Write failing tests**

Test that the listener:
1. Handles `TenantCreatedEvent`
2. Executes steps in order (S3 → Keycloak → OpenFGA → Initial Admin)
3. Updates provisioning status after each step
4. Sets tenant status to ACTIVE when all complete
5. Sets step to FAILED on error, leaves tenant in PROVISIONING

- [ ] **Step 2: Run tests — verify they fail**

- [ ] **Step 3: Implement TenantProvisioningListener**

```java
package io.webcontify.identity.service;

import io.webcontify.identity.model.*;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TenantProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningListener.class);

    private final TenantRepository tenantRepository;
    private final FileStorageService fileStorageService;
    private final KeycloakAdminService keycloakAdminService;
    private final OpenFgaService openFgaService;
    private final PlatformSmtpProperties platformSmtpProperties;

    public TenantProvisioningListener(
            TenantRepository tenantRepository,
            FileStorageService fileStorageService,
            KeycloakAdminService keycloakAdminService,
            OpenFgaService openFgaService,
            PlatformSmtpProperties platformSmtpProperties) {
        this.tenantRepository = tenantRepository;
        this.fileStorageService = fileStorageService;
        this.keycloakAdminService = keycloakAdminService;
        this.openFgaService = openFgaService;
        this.platformSmtpProperties = platformSmtpProperties;
    }

    @ApplicationModuleListener
    public void onTenantCreated(TenantCreatedEvent event) {
        // NOTE: No @Transactional on the whole method — external service calls (S3, Keycloak, OpenFGA)
        // would hold the transaction open too long. Instead, each status update is saved individually
        // via tenantRepository.save() which opens its own transaction.
        log.info("Starting provisioning for tenant: {}", event.slug());

        var entity = tenantRepository.findById(event.tenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + event.tenantId()));

        var status = ProvisioningStatus.fromJson(entity.getProvisioningStatus());
        var steps = new ArrayList<>(status.steps());

        // Step 1: S3 Bucket
        executeStep(steps, 0, ProvisioningStepType.S3_BUCKET, () -> {
            fileStorageService.createBucket("tenant-" + event.slug());
        }, entity);

        // Step 2: Keycloak Realm
        executeStep(steps, 1, ProvisioningStepType.KEYCLOAK_REALM, () -> {
            var smtpConfig = new SmtpConfig(
                platformSmtpProperties.host(), platformSmtpProperties.port(),
                platformSmtpProperties.username(), platformSmtpProperties.password(),
                platformSmtpProperties.starttls());
            var response = keycloakAdminService.createRealm(
                new CreateRealmRequest(event.slug(), smtpConfig));
            entity.setKeycloakRealmName(response.realmName());
        }, entity);

        // Step 3: OpenFGA Store
        executeStep(steps, 2, ProvisioningStepType.OPENFGA_STORE, () -> {
            var response = openFgaService.createStore(
                new io.webcontify.identity.model.CreateStoreRequest(event.slug()));
            entity.setOpenfgaStoreId(response.storeId());
        }, entity);

        // Step 4: Initial Admin
        executeStep(steps, 3, ProvisioningStepType.INITIAL_ADMIN, () -> {
            var realmName = entity.getKeycloakRealmName();
            var userResponse = keycloakAdminService.createUser(
                new CreateKeycloakUserRequest(realmName, event.adminEmail(),
                    event.adminFirstName(), event.adminLastName()));

            // Write OpenFGA relationship: user is tenant admin
            openFgaService.write(new OpenFgaWriteRequest(entity.getOpenfgaStoreId(), List.of(
                new RelationshipTuple(
                    "user:" + userResponse.keycloakUserId(),
                    "admin",
                    "tenant:" + event.tenantId())
            )));

            // Send invite email
            keycloakAdminService.sendInviteEmail(
                new SendInviteEmailRequest(realmName, userResponse.keycloakUserId()));
        }, entity);

        // Check if all completed
        var finalStatus = new ProvisioningStatus(steps);
        entity.setProvisioningStatus(finalStatus.toJson());

        if (finalStatus.allCompleted()) {
            entity.setStatus(TenantStatus.ACTIVE.name());
            log.info("Provisioning completed for tenant: {}", event.slug());
        } else {
            log.warn("Provisioning incomplete for tenant: {}", event.slug());
        }

        tenantRepository.save(entity);
    }

    private void executeStep(List<ProvisioningStep> steps, int index,
                             ProvisioningStepType expectedType, Runnable action,
                             Object entity) {
        var step = steps.get(index);
        if (step.status() == ProvisioningStepStatus.COMPLETED) return; // Skip completed
        if (step.step() != expectedType) throw new IllegalStateException("Step mismatch");

        steps.set(index, step.inProgress());
        // Persist intermediate status here if needed

        try {
            action.run();
            steps.set(index, step.completed());
        } catch (Exception e) {
            log.error("Provisioning step {} failed for tenant", expectedType, e);
            steps.set(index, step.failed(e.getMessage()));
            // Steps are sequential and dependent — stop on first failure.
            // The Event Publication Registry will retry the entire event,
            // and completed steps are skipped (checked at start of executeStep).
            throw new RuntimeException("Provisioning failed at step: " + expectedType, e);
        }
    }
}
```

**Step 3a: Create PlatformSmtpProperties before implementing the listener.**

Create `apps/backend/src/main/java/io/webcontify/identity/config/PlatformSmtpProperties.java`:

```java
package io.webcontify.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.smtp")
public record PlatformSmtpProperties(
    String host,
    int port,
    String username,
    String password,
    boolean starttls
) {}
```

Enable config properties scanning — add `@EnableConfigurationProperties(PlatformSmtpProperties.class)` to `OpenFgaConfiguration.java` or a dedicated config class.

Add to `apps/backend/src/main/resources/application.yml`:

```yaml
platform:
  smtp:
    host: ${PLATFORM_SMTP_HOST:localhost}
    port: ${PLATFORM_SMTP_PORT:1025}
    username: ${PLATFORM_SMTP_USER:}
    password: ${PLATFORM_SMTP_PASSWORD:}
    starttls: ${PLATFORM_SMTP_STARTTLS:false}
```

- [ ] **Step 4: Run tests — verify they pass**

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningListenerTest.java apps/backend/src/main/resources/application.yml
git commit -m "feat: implement async TenantProvisioningListener with event-driven steps (TDD)"
```

---

### Task 21: Retry Provisioning Endpoint

**Files:**
- Create: `apps/backend/src/main/java/io/webcontify/identity/api/TenantProvisioningController.java`
- Create: `apps/backend/src/test/java/io/webcontify/identity/api/TenantProvisioningControllerTest.java`

- [ ] **Step 1: Write failing test**

Test: POST to retry-provisioning re-publishes TenantCreatedEvent for tenant in PROVISIONING status. Returns 409 if tenant is not PROVISIONING.

- [ ] **Step 2: Run test — verify it fails**

- [ ] **Step 3: Implement controller**

```java
package io.webcontify.identity.api;

import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tenants")
public class TenantProvisioningController {

    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TenantProvisioningController(TenantRepository tenantRepository,
                                         ApplicationEventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/{tenantId}/retry-provisioning")
    public ResponseEntity<Void> retryProvisioning(@PathVariable UUID tenantId) {
        var entity = tenantRepository.findById(tenantId)
                .orElseThrow(); // Returns 404 via GlobalExceptionHandler

        if (!TenantStatus.PROVISIONING.name().equals(entity.getStatus())) {
            return ResponseEntity.status(409).build();
        }

        // Admin details are stored in provisioning_status JSONB (see ProvisioningStatus record)
        var status = ProvisioningStatus.fromJson(entity.getProvisioningStatus());

        eventPublisher.publishEvent(new TenantCreatedEvent(
            entity.getId(), entity.getSlug(),
            status.adminEmail(), status.adminFirstName(), status.adminLastName()
        ));

        return ResponseEntity.accepted().build();
    }
}
```

**Important:** This requires updating the `ProvisioningStatus` record (Task 18) to include admin details:

```java
public record ProvisioningStatus(
    List<ProvisioningStep> steps,
    String adminEmail,
    String adminFirstName,
    String adminLastName
) {
    public static ProvisioningStatus initial(String adminEmail, String adminFirstName, String adminLastName) {
        return new ProvisioningStatus(List.of(
            ProvisioningStep.pending(ProvisioningStepType.S3_BUCKET),
            ProvisioningStep.pending(ProvisioningStepType.KEYCLOAK_REALM),
            ProvisioningStep.pending(ProvisioningStepType.OPENFGA_STORE),
            ProvisioningStep.pending(ProvisioningStepType.INITIAL_ADMIN)
        ), adminEmail, adminFirstName, adminLastName);
    }
    // ... rest of methods
}
```

This way admin details are persisted in the JSONB and available for retry without external lookups.

- [ ] **Step 4: Run test — verify it passes**

- [ ] **Step 5: Commit**

```bash
git add apps/backend/src/main/java/io/webcontify/identity/api/ apps/backend/src/test/java/io/webcontify/identity/api/
git commit -m "feat: add retry-provisioning endpoint for failed tenant provisioning"
```

---

### Task 22: Provisioning Integration Test

**Files:**
- Create: `apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningIntegrationTest.java`

- [ ] **Step 1: Write integration test**

Full provisioning flow with Testcontainers (PostgreSQL + Keycloak + OpenFGA):
1. POST create tenant with admin details
2. Verify tenant is in PROVISIONING status
3. Wait for async provisioning to complete (poll tenant status)
4. Verify: S3 bucket exists, Keycloak realm exists with correct client, OpenFGA store exists with model, user exists in Keycloak, relationship exists in OpenFGA, tenant status is ACTIVE

- [ ] **Step 2: Run integration test**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "io.webcontify.identity.service.TenantProvisioningIntegrationTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningIntegrationTest.java
git commit -m "test: add full provisioning integration test (Keycloak + OpenFGA + PostgreSQL)"
```

---

## Chunk 6: Frontend Changes

### Task 23: Dynamic NextAuth Configuration

**Files:**
- Modify: `apps/platform/src/auth.ts`
- Modify: `apps/platform/src/middleware.ts`
- Create: `apps/platform/src/lib/auth-utils.ts`
- Create: `apps/platform/src/lib/__tests__/auth-utils.test.ts`

- [ ] **Step 1: Write failing tests for auth utils**

```typescript
// apps/platform/src/lib/__tests__/auth-utils.test.ts
import { describe, it, expect } from 'vitest'
import { getKeycloakIssuer, getRealmFromHostname, isPlatformHost } from '../auth-utils'

describe('auth-utils', () => {
  it('should return platform realm for platform.localhost', () => {
    expect(getRealmFromHostname('platform.localhost')).toBe('webcontify-platform')
  })

  it('should return tenant realm for tenant subdomain', () => {
    expect(getRealmFromHostname('acme.localhost')).toBe('tenant-acme')
  })

  it('should build correct issuer URL for platform', () => {
    expect(getKeycloakIssuer('platform.localhost'))
      .toBe('http://auth.localhost/realms/webcontify-platform')
  })

  it('should build correct issuer URL for tenant', () => {
    expect(getKeycloakIssuer('acme.localhost'))
      .toBe('http://auth.localhost/realms/tenant-acme')
  })

  it('should identify platform host', () => {
    expect(isPlatformHost('platform.localhost')).toBe(true)
    expect(isPlatformHost('acme.localhost')).toBe(false)
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `npx nx test platform --testPathPattern=auth-utils`
Expected: FAIL

- [ ] **Step 3: Implement auth-utils**

```typescript
// apps/platform/src/lib/auth-utils.ts
const AUTH_BASE_URL = process.env.KEYCLOAK_BASE_URL || 'http://auth.localhost'
const PLATFORM_HOSTNAME = 'platform.localhost'
const PLATFORM_REALM = 'webcontify-platform'

export function isPlatformHost(hostname: string): boolean {
  return hostname === PLATFORM_HOSTNAME
}

export function getRealmFromHostname(hostname: string): string {
  if (isPlatformHost(hostname)) return PLATFORM_REALM
  const slug = hostname.split('.')[0]
  return `tenant-${slug}`
}

export function getKeycloakIssuer(hostname: string): string {
  const realm = getRealmFromHostname(hostname)
  return `${AUTH_BASE_URL}/realms/${realm}`
}
```

- [ ] **Step 4: Run tests — verify they pass**

- [ ] **Step 5: Update auth.ts to use dynamic issuer**

Modify `apps/platform/src/auth.ts`:
- Import `getKeycloakIssuer` from `@/lib/auth-utils`
- Make the Keycloak provider configuration use dynamic issuer based on request hostname
- Note: NextAuth v5 with App Router may require using `headers()` to get hostname in server context. Check NextAuth docs for dynamic provider configuration.

- [ ] **Step 6: Update middleware.ts**

Modify `apps/platform/src/middleware.ts`:
- Import `isPlatformHost` from `@/lib/auth-utils`
- Parse hostname to determine platform vs tenant context
- Ensure route protection works for both contexts

- [ ] **Step 7: Verify build**

Run: `npx nx build platform`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add apps/platform/src/lib/auth-utils.ts apps/platform/src/lib/__tests__/auth-utils.test.ts apps/platform/src/auth.ts apps/platform/src/middleware.ts
git commit -m "feat: dynamic NextAuth realm resolution based on hostname (TDD)"
```

---

### Task 24: Update Create Tenant Dialog

**Files:**
- Modify: `apps/platform/src/features/tenants/schemas/create-tenant.ts` (or similar Zod schema)
- Modify: `apps/platform/src/features/tenants/components/create-tenant-dialog.tsx`

- [ ] **Step 1: Update Zod schema**

Replace slug field with admin fields:

```typescript
import { z } from 'zod'

export const createTenantSchema = z.object({
  name: z.string().min(1).max(255),
  adminEmail: z.string().email().max(255),
  adminFirstName: z.string().min(1).max(255),
  adminLastName: z.string().min(1).max(255),
})

export type CreateTenantFormValues = z.infer<typeof createTenantSchema>
```

- [ ] **Step 2: Update dialog component**

In `create-tenant-dialog.tsx`:
1. Remove slug field
2. Add fields: Admin Email, Admin Vorname, Admin Nachname
3. Add name uniqueness check on submit (call `checkTenantNameAvailability` before create)
4. After successful creation: redirect to tenant detail page
5. Update `defaultValues` to match new schema

- [ ] **Step 3: Regenerate Orval hooks**

Run: `npx nx generate platform`

Verify that the generated `useCreateTenant` hook accepts the new request shape.

- [ ] **Step 4: Verify build + tests**

Run: `npx nx build platform && npx nx test platform`
Expected: BUILD SUCCESSFUL, tests green

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/features/tenants/
git commit -m "feat: update Create Tenant dialog with admin fields, remove slug input"
```

---

### Task 25: Provisioning Status Component

**Files:**
- Create: `apps/platform/src/features/tenants/components/provisioning-status.tsx`
- Create: `apps/platform/src/features/tenants/components/__tests__/provisioning-status.test.tsx`
- Modify: `apps/platform/src/features/tenants/` (tenant detail page)

- [ ] **Step 1: Write failing tests**

```typescript
// provisioning-status.test.tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ProvisioningStatus } from '../provisioning-status'

describe('ProvisioningStatus', () => {
  it('should show all steps with correct labels', () => {
    const steps = [
      { step: 'S3_BUCKET', status: 'COMPLETED' },
      { step: 'KEYCLOAK_REALM', status: 'IN_PROGRESS' },
      { step: 'OPENFGA_STORE', status: 'PENDING' },
      { step: 'INITIAL_ADMIN', status: 'PENDING' },
    ]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} />)

    expect(screen.getByText('Speicher')).toBeInTheDocument()
    expect(screen.getByText('Authentifizierung')).toBeInTheDocument()
    expect(screen.getByText('Berechtigungssystem')).toBeInTheDocument()
    expect(screen.getByText('Initialer Admin')).toBeInTheDocument()
  })

  it('should show spinner for IN_PROGRESS step', () => {
    const steps = [{ step: 'S3_BUCKET', status: 'IN_PROGRESS' }]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} />)
    // Verify spinner/loading indicator is present
  })

  it('should show error and retry button for FAILED step', () => {
    const steps = [{ step: 'S3_BUCKET', status: 'FAILED', error: 'Connection refused' }]
    render(<ProvisioningStatus steps={steps} onRetry={() => {}} />)
    expect(screen.getByText('Connection refused')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /wiederholen/i })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

- [ ] **Step 3: Implement ProvisioningStatus component**

Create a checklist-style component using shadcn/ui components (Card, Badge, etc.):
- Each step shows: label, status icon (check/spinner/error), optional error message
- FAILED steps show a Retry button
- Step labels: S3_BUCKET → "Speicher", KEYCLOAK_REALM → "Authentifizierung", OPENFGA_STORE → "Berechtigungssystem", INITIAL_ADMIN → "Initialer Admin"

- [ ] **Step 4: Integrate into Tenant Detail page**

Modify the tenant detail page:
- If tenant status is `PROVISIONING`: show `<ProvisioningStatus>` component
- Poll tenant detail endpoint every 2 seconds while status is PROVISIONING (use React Query `refetchInterval`)
- When status changes to ACTIVE: stop polling, show normal detail view
- Retry button calls `retryTenantProvisioning` mutation

- [ ] **Step 5: Run tests — verify they pass**

- [ ] **Step 6: Commit**

```bash
git add apps/platform/src/features/tenants/
git commit -m "feat: add ProvisioningStatus component with polling and retry (TDD)"
```

---

### Task 26: Final Verification

- [ ] **Step 1: Run all backend tests**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
Expected: All green

- [ ] **Step 2: Run all frontend tests**

Run: `npx nx test platform`
Expected: All green

- [ ] **Step 3: Run full build**

Run: `npx nx run-many -t build`
Expected: All green

- [ ] **Step 4: Verify Spring Modulith structure**

Check if Spring Modulith structure verification test passes with the new `identity` module. If it fails due to cross-module dependencies, expose required interfaces or add `@ApplicationModule(allowedDependencies = ...)` annotations.

- [ ] **Step 5: Update PROGRESS.md**

Update `docs/PROGRESS.md`:
- Mark Task 3a as Done
- Update Tasks 3b-3g descriptions if needed based on what was built
- Add notes about what's ready for Phase 3b

- [ ] **Step 6: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md — Phase 3a complete"
```
