# Tenant Schema Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Create PostgreSQL schema-per-tenant isolation (`tenant_{slug}`) for dynamic content tables. Add `published`/`pending_action` columns on `content_field_definition` for deferred DDL. Extend RLS connection setup with search_path.

**Architecture:** Each tenant gets a dedicated PostgreSQL schema created during provisioning. The `RlsConnectionProvider` sets both `app.tenant_id` (for RLS on public tables) and `search_path = 'tenant_{slug}', public` (for dynamic content tables). Content tables live in the tenant schema, platform tables in public. Schema isolation replaces RLS on dynamic tables.

**Tech Stack:** Java 21, Spring Boot 4, Hibernate, HikariCP, PostgreSQL 17, Flyway, Testcontainers

---

## Task 1: Flyway Migration — Add published + pending_action columns

**Goal:** Extend `content_field_definition` with two columns that track publication state and pending DDL actions for deferred schema management.

### Steps

- [ ] Create file `apps/backend/src/main/resources/db/migration/V20260415_001__add_field_published_columns.sql` with the following content:

```sql
ALTER TABLE content_field_definition
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE content_field_definition
    ADD COLUMN pending_action VARCHAR(20);

COMMENT ON COLUMN content_field_definition.published IS 'True when this field has been published (column exists in dedicated table)';
COMMENT ON COLUMN content_field_definition.pending_action IS 'Pending DDL action on next publish: ADDED, REMOVED, or NULL';
```

- [ ] Open `apps/backend/src/main/java/io/webcontify/content/definition/repository/entity/ContentFieldEntity.java` and add two new fields after the existing `position` field and their getters/setters:

```java
@Column(nullable = false)
private boolean published = false;

@Column(name = "pending_action")
private String pendingAction;
```

Add getters and setters:

```java
public boolean isPublished() {
    return published;
}

public void setPublished(boolean published) {
    this.published = published;
}

public String getPendingAction() {
    return pendingAction;
}

public void setPendingAction(String pendingAction) {
    this.pendingAction = pendingAction;
}
```

- [ ] Run compilation check:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

- [ ] Commit:

```
feat(content): add published and pending_action columns for deferred DDL
```

---

## Task 2: Extend TenantInformation with slug field

**Goal:** Add `slug` to the `TenantInformation` record so downstream components (e.g. `RlsConnectionProvider`) can resolve the schema name without an extra DB lookup at connection time.

### Steps

- [ ] Open `apps/backend/src/test/java/io/webcontify/shared/model/TenantInformationTest.java` — if the file does not exist, create it. Add these test cases:

```java
package io.webcontify.shared.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantInformationTest {

    @Test
    void twoArgConstructorSetsTenantIdAndPlatformAdminWithNullsForRest() {
        var id = UUID.randomUUID();
        var info = new TenantInformation(id, false);
        assertThat(info.tenantId()).isEqualTo(id);
        assertThat(info.platformAdmin()).isFalse();
        assertThat(info.realmName()).isNull();
        assertThat(info.slug()).isNull();
    }

    @Test
    void threeArgConstructorBackwardCompatSetsNullSlug() {
        var id = UUID.randomUUID();
        var info = new TenantInformation(id, false, "tenant-acme");
        assertThat(info.realmName()).isEqualTo("tenant-acme");
        assertThat(info.slug()).isNull();
    }

    @Test
    void fourArgConstructorSetsAllFields() {
        var id = UUID.randomUUID();
        var info = new TenantInformation(id, false, "tenant-acme", "acme");
        assertThat(info.tenantId()).isEqualTo(id);
        assertThat(info.platformAdmin()).isFalse();
        assertThat(info.realmName()).isEqualTo("tenant-acme");
        assertThat(info.slug()).isEqualTo("acme");
    }

    @Test
    void nonPlatformAdminWithNullTenantIdThrows() {
        assertThatThrownBy(() -> new TenantInformation(null, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void platformAdminWithNullTenantIdIsValid() {
        var info = new TenantInformation(null, true, null, null);
        assertThat(info.platformAdmin()).isTrue();
        assertThat(info.tenantId()).isNull();
    }
}
```

- [ ] Run tests — expect compile failure (slug field does not exist yet):

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.shared.model.TenantInformationTest
```

- [ ] Update `apps/backend/src/main/java/io/webcontify/shared/model/TenantInformation.java`:

```java
package io.webcontify.shared.model;

import java.util.UUID;

public record TenantInformation(UUID tenantId, boolean platformAdmin, String realmName, String slug) {

    public TenantInformation(UUID tenantId, boolean platformAdmin) {
        this(tenantId, platformAdmin, null, null);
    }

    public TenantInformation(UUID tenantId, boolean platformAdmin, String realmName) {
        this(tenantId, platformAdmin, realmName, null);
    }

    public TenantInformation {
        // Platform admins CAN have a tenantId when operating on a tenant subdomain
        if (!platformAdmin && tenantId == null) {
            throw new IllegalArgumentException("Tenant ID is required for non-platform-admin");
        }
    }
}
```

- [ ] Run compilation check to verify existing callers (2-arg and 3-arg constructors) still compile:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

- [ ] Run tests — expect green:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.shared.model.TenantInformationTest
```

- [ ] Commit:

```
refactor(shared): add slug to TenantInformation for schema resolution
```

---

## Task 3: JwtToTenantAuthenticationConverter populates slug

**Goal:** The JWT converter already resolves `tenantId` from the token. Extend it to also resolve the tenant `slug` via `TenantSlugResolver` and store it in `TenantInformation`. This means downstream code (including `RlsConnectionProvider`) can read the slug from the security context without an extra DB call per request.

### Context

Current file: `apps/backend/src/main/java/io/webcontify/shared/security/JwtToTenantAuthenticationConverter.java`

- Already injects `TenantSlugResolver`
- For non-admin users, creates `new TenantInformation(tenantId, false, realmName)` — needs to also resolve and pass slug
- Platform admin path: `new TenantInformation(null, true, realmName)` — slug stays null (admins have no single tenant schema)

### Steps

- [ ] Open `apps/backend/src/test/java/io/webcontify/shared/security/JwtToTenantAuthenticationConverterTest.java`. If the file exists, add the following test case. If it does not exist, create the file with these tests:

```java
package io.webcontify.shared.security;

import io.webcontify.shared.model.TenantInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtToTenantAuthenticationConverterTest {

    @Mock
    TenantSlugResolver tenantSlugResolver;

    @InjectMocks
    JwtToTenantAuthenticationConverter converter;

    @Test
    void shouldPopulateSlugOnTenantInformationForNonAdminUser() {
        var tenantId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var slug = "acme";

        when(tenantSlugResolver.resolveSlugByTenantId(tenantId))
                .thenReturn(Optional.of(slug));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .issuer("http://auth.localhost/realms/tenant-acme")
                .build();

        var auth = converter.convert(jwt);

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(TenantInformation.class);
        var info = (TenantInformation) auth.getPrincipal();
        assertThat(info.tenantId()).isEqualTo(tenantId);
        assertThat(info.slug()).isEqualTo(slug);
        assertThat(info.realmName()).isEqualTo("tenant-acme");
    }

    @Test
    void shouldLeaveSlugNullForPlatformAdmin() {
        var userId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("realm_access", Map.of("roles", List.of("PLATFORM_ADMIN")))
                .issuer("http://auth.localhost/realms/master")
                .build();

        var auth = converter.convert(jwt);

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(TenantInformation.class);
        var info = (TenantInformation) auth.getPrincipal();
        assertThat(info.platformAdmin()).isTrue();
        assertThat(info.slug()).isNull();
    }
}
```

- [ ] Run tests — expect red (slug not yet populated):

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.shared.security.JwtToTenantAuthenticationConverterTest
```

- [ ] Update `apps/backend/src/main/java/io/webcontify/shared/security/JwtToTenantAuthenticationConverter.java`. In the `convert` method, replace the non-admin branch:

```java
// Before (line ~51):
tenantInformation = new TenantInformation(tenantId, false, realmName);

// After:
String slug = tenantSlugResolver.resolveSlugByTenantId(tenantId).orElse(null);
tenantInformation = new TenantInformation(tenantId, false, realmName, slug);
```

- [ ] Run tests — expect green:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.shared.security.JwtToTenantAuthenticationConverterTest
```

- [ ] Commit:

```
feat(shared): populate slug in TenantInformation via JWT converter
```

---

## Task 4: Extend RlsConnectionProvider with search_path

**Goal:** When a connection is handed to Hibernate for a tenant context, set `search_path = 'tenant_{slug}', public` in addition to `app.tenant_id`. This routes jOOQ/JDBC queries to the tenant-specific schema for dynamic content tables. Platform tables in `public` remain accessible via the fallback in the search_path. Reset both settings on connection release.

The slug must be obtained without a DB round-trip per connection. Since `RlsConnectionProvider` only receives `tenantId` (UUID string), inject `TenantSlugResolver` and add a slug cache backed by Caffeine (same pattern as `TenantInfrastructureLookupService`). The cache name `tenantSlug` must be registered in `CaffeineCacheConfiguration`.

### Context

- Current file: `apps/backend/src/main/java/io/webcontify/shared/persistence/RlsConnectionProvider.java`
- Cache config: `apps/backend/src/main/java/io/webcontify/identity/config/CaffeineCacheConfiguration.java` — currently registers only `tenantInfrastructure`
- `releaseConnection` currently resets `app.tenant_id` to `''` unconditionally — needs `RESET search_path` added too

### Steps

- [ ] Update `apps/backend/src/main/java/io/webcontify/identity/config/CaffeineCacheConfiguration.java` to also register the `tenantSlug` cache:

```java
@Bean
public CacheManager cacheManager() {
    var cacheManager = new CaffeineCacheManager("tenantInfrastructure", "tenantSlug");
    cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES));
    return cacheManager;
}
```

- [ ] Create `apps/backend/src/main/java/io/webcontify/shared/persistence/TenantSlugCache.java`:

```java
package io.webcontify.shared.persistence;

import io.webcontify.shared.security.TenantSlugResolver;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantSlugCache {

    private final TenantSlugResolver tenantSlugResolver;

    public TenantSlugCache(TenantSlugResolver tenantSlugResolver) {
        this.tenantSlugResolver = tenantSlugResolver;
    }

    @Cacheable(value = "tenantSlug", key = "#tenantId")
    public String resolveSlug(UUID tenantId) {
        return tenantSlugResolver.resolveSlugByTenantId(tenantId).orElse(null);
    }

    @CacheEvict(value = "tenantSlug", key = "#tenantId")
    public void evict(UUID tenantId) {
        // Cache eviction only
    }
}
```

- [ ] Write unit test `apps/backend/src/test/java/io/webcontify/shared/persistence/RlsConnectionProviderTest.java`:

```java
package io.webcontify.shared.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RlsConnectionProviderTest {

    @Mock HikariDataSource dataSource;
    @Mock TenantSlugCache slugCache;
    @Mock Connection connection;
    @Mock PreparedStatement preparedStatement;
    @Mock Statement statement;

    RlsConnectionProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        provider = new RlsConnectionProvider(dataSource, slugCache);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(connection.createStatement()).thenReturn(statement);
    }

    @Test
    void shouldSetTenantIdAndSearchPathForTenantConnection() throws Exception {
        var tenantId = UUID.randomUUID();
        when(slugCache.resolveSlug(tenantId)).thenReturn("acme");

        provider.getConnection(tenantId.toString());

        verify(preparedStatement).setString(1, tenantId.toString());
        verify(preparedStatement).execute();
        verify(statement).execute("SET search_path = 'tenant_acme', public");
    }

    @Test
    void shouldUsePlatformConnectionWithoutSettingAnything() throws Exception {
        provider.getConnection(RlsTenantIdentifierResolver.PLATFORM_TENANT_CONTEXT);

        verifyNoInteractions(slugCache);
        verify(connection, never()).prepareStatement(anyString());
        verify(connection, never()).createStatement();
    }

    @Test
    void shouldResetTenantIdAndSearchPathOnRelease() throws Exception {
        var tenantId = UUID.randomUUID();
        provider.releaseConnection(tenantId.toString(), connection);

        verify(preparedStatement).execute();
        verify(statement).execute("RESET search_path");
        verify(connection).close();
    }

    @Test
    void shouldReplaceHyphensWithUnderscoresInSchemaName() throws Exception {
        var tenantId = UUID.randomUUID();
        when(slugCache.resolveSlug(tenantId)).thenReturn("my-tenant-slug");

        provider.getConnection(tenantId.toString());

        verify(statement).execute("SET search_path = 'tenant_my_tenant_slug', public");
    }
}
```

- [ ] Run tests — expect red (RlsConnectionProvider constructor does not accept TenantSlugCache yet):

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.shared.persistence.RlsConnectionProviderTest
```

- [ ] Update `apps/backend/src/main/java/io/webcontify/shared/persistence/RlsConnectionProvider.java`:

```java
package io.webcontify.shared.persistence;

import static io.webcontify.shared.persistence.RlsTenantIdentifierResolver.PLATFORM_TENANT_CONTEXT;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

@Component
public class RlsConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final HikariDataSource dataSource;
    private final TenantSlugCache slugCache;

    public RlsConnectionProvider(HikariDataSource dataSource, TenantSlugCache slugCache) {
        this.dataSource = dataSource;
        this.slugCache = slugCache;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantId) throws SQLException {
        Connection connection = dataSource.getConnection();
        if (!PLATFORM_TENANT_CONTEXT.equals(tenantId)) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.tenant_id', ?, false)")) {
                ps.setString(1, tenantId);
                ps.execute();
            }

            UUID tenantUuid = UUID.fromString(tenantId);
            String slug = slugCache.resolveSlug(tenantUuid);
            if (slug != null) {
                String schemaName = "tenant_" + slug.replace("-", "_");
                try (Statement st = connection.createStatement()) {
                    st.execute("SET search_path = '" + schemaName + "', public");
                }
            }
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantId, Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
        try (Statement st = connection.createStatement()) {
            st.execute("RESET search_path");
        }
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Unwrapping not supported");
    }
}
```

- [ ] Run tests — expect green:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.shared.persistence.RlsConnectionProviderTest
```

- [ ] Run full backend tests to verify no regressions:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

- [ ] Commit:

```
feat(shared): extend RLS connection setup with tenant schema search_path
```

---

## Task 5: Add TENANT_SCHEMA to ProvisioningStepType enum

**Goal:** Register the new provisioning step type so the listener can track schema creation like other infrastructure steps.

### Context

- `apps/backend/src/main/java/io/webcontify/shared/model/ProvisioningStepType.java` — currently has 4 values: `S3_BUCKET`, `KEYCLOAK_REALM`, `OPENFGA_STORE`, `INITIAL_ADMIN`
- `ProvisioningStatus.initial()` in `apps/backend/src/main/java/io/webcontify/shared/model/ProvisioningStatus.java` builds the initial list — needs `TENANT_SCHEMA` step inserted between `OPENFGA_STORE` and `INITIAL_ADMIN`
- The provisioning status is serialized to JSONB in the DB column — adding a new enum value is backward compatible (existing tenants already have their schema, the field would just be missing from old snapshots, but the step list is rebuilt from `initial()` only for NEW tenants)

### Steps

- [ ] Update `apps/backend/src/main/java/io/webcontify/shared/model/ProvisioningStepType.java`:

```java
package io.webcontify.shared.model;

public enum ProvisioningStepType {
    S3_BUCKET,
    KEYCLOAK_REALM,
    OPENFGA_STORE,
    TENANT_SCHEMA,
    INITIAL_ADMIN
}
```

- [ ] Update `apps/backend/src/main/java/io/webcontify/shared/model/ProvisioningStatus.java` — add `TENANT_SCHEMA` step in `initial()` between `OPENFGA_STORE` and `INITIAL_ADMIN`:

```java
public static ProvisioningStatus initial(String adminEmail, String adminFirstName, String adminLastName) {
    return new ProvisioningStatus(
            List.of(
                    ProvisioningStep.pending(ProvisioningStepType.S3_BUCKET),
                    ProvisioningStep.pending(ProvisioningStepType.KEYCLOAK_REALM),
                    ProvisioningStep.pending(ProvisioningStepType.OPENFGA_STORE),
                    ProvisioningStep.pending(ProvisioningStepType.TENANT_SCHEMA),
                    ProvisioningStep.pending(ProvisioningStepType.INITIAL_ADMIN)),
            adminEmail,
            adminFirstName,
            adminLastName);
}
```

- [ ] Run compilation check:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileJava
```

- [ ] Commit:

```
feat(identity): add TENANT_SCHEMA provisioning step type
```

---

## Task 6: Create TenantSchemaService

**Goal:** Encapsulate all PostgreSQL schema lifecycle DDL (create, drop) in a dedicated service. Schema names follow the pattern `tenant_{slug}` with hyphens replaced by underscores.

### Steps

- [ ] Write unit test `apps/backend/src/test/java/io/webcontify/identity/service/TenantSchemaServiceTest.java`:

```java
package io.webcontify.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TenantSchemaServiceTest {

    @Mock JdbcTemplate jdbcTemplate;

    @InjectMocks TenantSchemaService tenantSchemaService;

    @Test
    void shouldCreateSchemaWithCorrectName() {
        tenantSchemaService.createSchema("acme");

        var captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());

        var ddls = captor.getAllValues();
        assertThat(ddls.get(0)).isEqualTo("CREATE SCHEMA IF NOT EXISTS \"tenant_acme\"");
        assertThat(ddls.get(1)).isEqualTo("REVOKE ALL ON SCHEMA \"tenant_acme\" FROM PUBLIC");
    }

    @Test
    void shouldReplaceHyphensWithUnderscoresInSchemaName() {
        tenantSchemaService.createSchema("my-tenant");

        var captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());

        assertThat(captor.getAllValues().get(0))
                .isEqualTo("CREATE SCHEMA IF NOT EXISTS \"tenant_my_tenant\"");
    }

    @Test
    void shouldDropSchemaWithCascade() {
        tenantSchemaService.dropSchema("acme");

        verify(jdbcTemplate).execute("DROP SCHEMA IF EXISTS \"tenant_acme\" CASCADE");
    }

    @Test
    void shouldDropSchemaWithHyphensReplaced() {
        tenantSchemaService.dropSchema("my-tenant");

        verify(jdbcTemplate).execute("DROP SCHEMA IF EXISTS \"tenant_my_tenant\" CASCADE");
    }
}
```

- [ ] Run tests — expect red (class does not exist yet):

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.identity.service.TenantSchemaServiceTest
```

- [ ] Create `apps/backend/src/main/java/io/webcontify/identity/service/TenantSchemaService.java`:

```java
package io.webcontify.identity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantSchemaService {

    private final JdbcTemplate jdbcTemplate;

    public TenantSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createSchema(String tenantSlug) {
        String schemaName = schemaName(tenantSlug);
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
        jdbcTemplate.execute("REVOKE ALL ON SCHEMA \"" + schemaName + "\" FROM PUBLIC");
        jdbcTemplate.execute("GRANT USAGE ON SCHEMA \"" + schemaName + "\" TO CURRENT_USER");
    }

    public void dropSchema(String tenantSlug) {
        String schemaName = schemaName(tenantSlug);
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
    }

    private String schemaName(String tenantSlug) {
        return "tenant_" + tenantSlug.replace("-", "_");
    }
}
```

- [ ] Run tests — expect green:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.identity.service.TenantSchemaServiceTest
```

- [ ] Commit:

```
feat(identity): add TenantSchemaService for schema lifecycle
```

---

## Task 7: TenantProvisioningListener executes TENANT_SCHEMA step

**Goal:** Wire `TenantSchemaService` into the provisioning flow. The new step runs after `OPENFGA_STORE` (index 2) and before `INITIAL_ADMIN` (now at index 4). All step indices shift by one.

### Context

Current listener step indices:
- 0: `S3_BUCKET`
- 1: `KEYCLOAK_REALM`
- 2: `OPENFGA_STORE`
- 3: `INITIAL_ADMIN`

After this task:
- 0: `S3_BUCKET`
- 1: `KEYCLOAK_REALM`
- 2: `OPENFGA_STORE`
- 3: `TENANT_SCHEMA` ← new
- 4: `INITIAL_ADMIN`

The listener test `shouldExecuteAllStepsAndPersistAfterEachStep` currently verifies 4 `persistStatus` calls — this must increase to 5. The test `shouldSkipCompletedSteps` uses a hardcoded 4-element step list — it must be updated to include `TENANT_SCHEMA`.

### Steps

- [ ] Update `apps/backend/src/test/java/io/webcontify/identity/service/TenantProvisioningListenerTest.java`:

  1. Add `@Mock TenantSchemaService tenantSchemaService;` field
  2. Update `setUp()` to pass `tenantSchemaService` to the `TenantProvisioningListener` constructor (add as last parameter before `tenantUserRepository`)
  3. In `shouldExecuteAllStepsAndPersistAfterEachStep`: change `times(4)` to `times(5)` for `persistStatus`, change `get(3)` to `get(4)` for last-status/last-completed assertions
  4. In `shouldSkipCompletedSteps`: add `ProvisioningStep.pending(ProvisioningStepType.TENANT_SCHEMA)` between `OPENFGA_STORE` and `INITIAL_ADMIN` in the steps list, making it 5 elements
  5. Add a new test that verifies `tenantSchemaService.createSchema("acme")` is called:

```java
@Test
void shouldCreateTenantSchemaAfterOpenFgaStore() {
    var tenantId = UUID.randomUUID();
    var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");
    var initialStatus = ProvisioningStatus.initial("admin@acme.com", "Max", "Muster");

    when(statusPersister.loadSnapshot(tenantId))
            .thenReturn(new ProvisioningStatusPersister.ProvisioningSnapshot("Acme Corp", initialStatus));
    when(keycloakAdminService.createRealm(any())).thenReturn(new CreateRealmResponse("tenant-acme"));
    when(keycloakAdminService.createUser(any())).thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
    when(openFgaService.createStore(any())).thenReturn(new CreateStoreResponse("store-123"));
    when(tenantRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    listener.onTenantCreated(event);

    verify(tenantSchemaService).createSchema("acme");
}
```

- [ ] Run tests — expect red (listener constructor does not accept TenantSchemaService yet):

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.identity.service.TenantProvisioningListenerTest
```

- [ ] Update `apps/backend/src/main/java/io/webcontify/identity/service/TenantProvisioningListener.java`:

  1. Add `private final TenantSchemaService tenantSchemaService;` field
  2. Add `TenantSchemaService tenantSchemaService` parameter to constructor and assign it
  3. Add the TENANT_SCHEMA step between `OPENFGA_STORE` and `INITIAL_ADMIN`:

```java
// Step 3: Tenant Schema
if (!executeStep(steps, 3, ProvisioningStepType.TENANT_SCHEMA, () ->
        tenantSchemaService.createSchema(event.slug()))) {
    persistAndReturn(event, steps, initialStatus, false, null);
    return;
}
persistAndReturn(event, steps, initialStatus, false, null);

// Step 4: Initial Admin  (was step 3)
final String adminRealmName = realmName;
final String adminStoreId = storeId;
if (!executeStep(steps, 4, ProvisioningStepType.INITIAL_ADMIN, () -> {
    // ... existing INITIAL_ADMIN body unchanged ...
```

  4. Update all references to `executeStep(steps, 3, ProvisioningStepType.INITIAL_ADMIN, ...)` → `executeStep(steps, 4, ...)`

- [ ] Run tests — expect green:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.identity.service.TenantProvisioningListenerTest
```

- [ ] Commit:

```
feat(identity): provision tenant schema during tenant creation
```

---

## Task 8: TenantService.delete triggers schema drop

**Goal:** When a tenant is soft-deleted, also drop the tenant schema (irreversible data removal by design — the admin explicitly deletes a tenant). If the entity has no slug (edge case: tenant creation never completed), skip the drop.

### Context

Current `delete` method in `apps/backend/src/main/java/io/webcontify/tenant/service/TenantService.java` only sets `deletedAt`/`deletedBy` on `AuditInfo`. The `TenantService` currently has no `TenantSchemaService` dependency.

The `TenantService` lives in the `tenant` module. `TenantSchemaService` lives in the `identity` module. To avoid a circular dependency, keep `TenantSchemaService` in `identity` but inject it into `TenantService` via the existing module boundary (Spring dependency injection across modules is fine here — the modules communicate via events for async flows but direct injection for sync operations is acceptable per the existing architecture).

Alternatively: publish a `TenantDeletedEvent` from `TenantService` and handle schema drop in a listener in the `identity` module. This keeps module boundaries cleaner. **Use this approach.**

### Steps

- [ ] Create event class `apps/backend/src/main/java/io/webcontify/tenant/event/TenantDeletedEvent.java`:

```java
package io.webcontify.tenant.event;

import java.util.UUID;

public record TenantDeletedEvent(UUID tenantId, String slug) {}
```

- [ ] Update `apps/backend/src/main/java/io/webcontify/tenant/service/TenantService.java` — in the `delete` method, publish the event before the save:

```java
public void delete(UUID id) {
    TenantEntity entity =
            tenantRepository
                    .findByIdAndAuditInfoDeletedAtIsNull(id)
                    .orElseThrow(() -> new NotFoundException("tenant.not_found"));

    entity.getAuditInfo().setDeletedAt(OffsetDateTime.now());
    entity.getAuditInfo().setDeletedBy(SystemUser.ID);
    tenantRepository.save(entity);

    eventPublisher.publishEvent(new TenantDeletedEvent(id, entity.getSlug()));
}
```

- [ ] Write unit test for the updated `delete` method — add to `apps/backend/src/test/java/io/webcontify/tenant/service/TenantServiceTest.java`:

```java
@Test
void shouldPublishTenantDeletedEventOnDelete() {
    UUID id = UUID.randomUUID();
    TenantEntity entity = createEntity();
    entity.setId(id);
    entity.setSlug("acme");
    when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(id))
            .thenReturn(Optional.of(entity));

    tenantService.delete(id);

    ArgumentCaptor<TenantDeletedEvent> eventCaptor =
            ArgumentCaptor.forClass(TenantDeletedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    TenantDeletedEvent event = eventCaptor.getValue();
    assertThat(event.tenantId()).isEqualTo(id);
    assertThat(event.slug()).isEqualTo("acme");
}
```

- [ ] Run tests — expect red until implementation is done:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.tenant.service.TenantServiceTest
```

- [ ] Create listener `apps/backend/src/main/java/io/webcontify/identity/service/TenantSchemaCleanupListener.java`:

```java
package io.webcontify.identity.service;

import io.webcontify.tenant.event.TenantDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class TenantSchemaCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaCleanupListener.class);

    private final TenantSchemaService tenantSchemaService;

    public TenantSchemaCleanupListener(TenantSchemaService tenantSchemaService) {
        this.tenantSchemaService = tenantSchemaService;
    }

    @ApplicationModuleListener
    public void onTenantDeleted(TenantDeletedEvent event) {
        if (event.slug() == null) {
            log.warn("TenantDeletedEvent received with null slug for tenantId={}, skipping schema drop",
                    event.tenantId());
            return;
        }
        log.info("Dropping schema for tenant slug={}", event.slug());
        tenantSchemaService.dropSchema(event.slug());
        log.info("Schema dropped for tenant slug={}", event.slug());
    }
}
```

- [ ] Write unit test `apps/backend/src/test/java/io/webcontify/identity/service/TenantSchemaCleanupListenerTest.java`:

```java
package io.webcontify.identity.service;

import io.webcontify.tenant.event.TenantDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantSchemaCleanupListenerTest {

    @Mock TenantSchemaService tenantSchemaService;

    @InjectMocks TenantSchemaCleanupListener listener;

    @Test
    void shouldDropSchemaOnTenantDeleted() {
        var event = new TenantDeletedEvent(UUID.randomUUID(), "acme");

        listener.onTenantDeleted(event);

        verify(tenantSchemaService).dropSchema("acme");
    }

    @Test
    void shouldSkipDropWhenSlugIsNull() {
        var event = new TenantDeletedEvent(UUID.randomUUID(), null);

        listener.onTenantDeleted(event);

        verifyNoInteractions(tenantSchemaService);
    }
}
```

- [ ] Run tests — expect green:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.tenant.service.TenantServiceTest
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.identity.service.TenantSchemaCleanupListenerTest
```

- [ ] Commit:

```
feat(tenant): publish TenantDeletedEvent and drop schema on tenant delete
```

---

## Task 9: Integration test — full schema lifecycle

**Goal:** Verify end-to-end that schema creation during provisioning and schema removal on delete work against a real PostgreSQL instance.

### Context

- Use existing `TenantProvisioningIntegrationTest` test class as a pattern (Testcontainers + `@SpringBootTest` + `MockMvc`)
- Add a new test method in the existing `TenantProvisioningIntegrationTest` OR create a dedicated `TenantSchemaLifecycleIntegrationTest.java` — prefer a separate file for clarity

### Steps

- [ ] Create `apps/backend/src/test/java/io/webcontify/identity/service/TenantSchemaLifecycleIntegrationTest.java`:

```java
package io.webcontify.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.webcontify.identity.model.CreateKeycloakUserResponse;
import io.webcontify.identity.model.CreateRealmResponse;
import io.webcontify.identity.model.CreateStoreResponse;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfiguration.class)
class TenantSchemaLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean KeycloakAdminService keycloakAdminService;
    @MockitoBean OpenFgaService openFgaService;
    @MockitoBean FileStorageService fileStorageService;

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpMocks() {
        when(keycloakAdminService.createRealm(any()))
                .thenReturn(new CreateRealmResponse("tenant-schematest"));
        when(keycloakAdminService.createUser(any()))
                .thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
        when(openFgaService.createStore(any()))
                .thenReturn(new CreateStoreResponse("store-schema-test"));
    }

    @Test
    void shouldCreateTenantSchemaOnProvisioning() throws Exception {
        String requestBody = """
                {
                    "name": "Schema Test Corp",
                    "adminEmail": "admin@schematest.com",
                    "adminFirstName": "Max",
                    "adminLastName": "Muster"
                }
                """;

        var result = mockMvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String tenantIdStr = objectMapper.readTree(responseBody).get("id").asText();
        UUID tenantId = UUID.fromString(tenantIdStr);

        // Wait for async provisioning to complete
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> tenantRepository.findById(tenantId)
                        .map(e -> "ACTIVE".equals(e.getStatus()))
                        .orElse(false));

        // Verify that the tenant schema was created in PostgreSQL
        var entity = tenantRepository.findById(tenantId).orElseThrow();
        String expectedSchema = "tenant_" + entity.getSlug().replace("-", "_");

        Boolean schemaExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                Boolean.class, expectedSchema);

        assertThat(schemaExists).isTrue();
    }

    @Test
    void shouldDropTenantSchemaOnDelete() throws Exception {
        // Create and provision the tenant first
        String requestBody = """
                {
                    "name": "Delete Schema Corp",
                    "adminEmail": "admin@deleteschema.com",
                    "adminFirstName": "Max",
                    "adminLastName": "Muster"
                }
                """;

        var createResult = mockMvc.perform(post("/api/v1/admin/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        String tenantIdStr = objectMapper.readTree(responseBody).get("id").asText();
        UUID tenantId = UUID.fromString(tenantIdStr);

        // Wait for provisioning
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> tenantRepository.findById(tenantId)
                        .map(e -> "ACTIVE".equals(e.getStatus()))
                        .orElse(false));

        var entity = tenantRepository.findById(tenantId).orElseThrow();
        String expectedSchema = "tenant_" + entity.getSlug().replace("-", "_");

        // Confirm schema exists before delete
        Boolean existsBefore = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                Boolean.class, expectedSchema);
        assertThat(existsBefore).isTrue();

        // Delete the tenant
        mockMvc.perform(delete("/api/v1/admin/tenants/" + tenantId))
                .andExpect(status().isNoContent());

        // Wait for async schema drop
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    Boolean exists = jdbcTemplate.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)",
                            Boolean.class, expectedSchema);
                    return Boolean.FALSE.equals(exists);
                });
    }
}
```

- [ ] Run integration tests:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.identity.service.TenantSchemaLifecycleIntegrationTest
```

- [ ] Commit:

```
test(identity): integration test for tenant schema lifecycle
```

---

## Task 10: Verify full test suite

**Goal:** Confirm that all unit tests pass and no regressions were introduced by the changes across Tasks 1–9.

### Steps

- [ ] Run all backend unit and integration tests:

```bash
cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

- [ ] Verify the output — all tests must be green. Pay particular attention to:
  - `TenantProvisioningListenerTest` — step count assertions updated for 5 steps
  - `TenantServiceTest` — `shouldSoftDeleteTenant` still passes, new event test passes
  - `TenantProvisioningIntegrationTest` — existing flow still completes (now has 5 steps)
  - `RlsConnectionProviderTest` — search_path is set and reset
  - `TenantSchemaServiceTest` — schema name derivation correct
  - `TenantSchemaCleanupListenerTest` — null slug handled safely
  - `TenantSchemaLifecycleIntegrationTest` — schema created on provision, dropped on delete

- [ ] If any test fails, investigate and fix before proceeding. Common issues:
  - `TenantProvisioningIntegrationTest.shouldCompleteFullProvisioningFlow` — it calls `verify(statusPersister, times(4))` — this now needs to be `times(5)` since there are 5 steps with intermediate persists. Check and update accordingly.
  - `shouldSkipCompletedSteps` in listener test — step list must have 5 entries now.

- [ ] Final commit (only if there were fixup changes):

```
fix(identity): align test assertions to 5-step provisioning flow
```

---

## Summary of file changes

| File | Action |
|---|---|
| `db/migration/V20260415_001__add_field_published_columns.sql` | Create |
| `content/definition/repository/entity/ContentFieldEntity.java` | Extend (2 fields + getters/setters) |
| `shared/model/TenantInformation.java` | Extend (add `slug` field + backward-compat constructors) |
| `shared/model/ProvisioningStepType.java` | Extend (add `TENANT_SCHEMA`) |
| `shared/model/ProvisioningStatus.java` | Extend (`initial()` adds TENANT_SCHEMA step) |
| `shared/security/JwtToTenantAuthenticationConverter.java` | Extend (resolve + populate slug) |
| `shared/persistence/RlsConnectionProvider.java` | Extend (search_path set/reset, inject TenantSlugCache) |
| `shared/persistence/TenantSlugCache.java` | Create |
| `identity/config/CaffeineCacheConfiguration.java` | Extend (register `tenantSlug` cache) |
| `identity/service/TenantSchemaService.java` | Create |
| `identity/service/TenantSchemaCleanupListener.java` | Create |
| `identity/service/TenantProvisioningListener.java` | Extend (TENANT_SCHEMA step at index 3) |
| `tenant/event/TenantDeletedEvent.java` | Create |
| `tenant/service/TenantService.java` | Extend (publish TenantDeletedEvent in `delete`) |
| `shared/model/TenantInformationTest.java` | Create |
| `shared/security/JwtToTenantAuthenticationConverterTest.java` | Create or extend |
| `shared/persistence/RlsConnectionProviderTest.java` | Create |
| `identity/service/TenantSchemaServiceTest.java` | Create |
| `identity/service/TenantSchemaCleanupListenerTest.java` | Create |
| `identity/service/TenantSchemaLifecycleIntegrationTest.java` | Create |
| `identity/service/TenantProvisioningListenerTest.java` | Extend (5 steps, new verify) |
| `tenant/service/TenantServiceTest.java` | Extend (event assert on delete) |
