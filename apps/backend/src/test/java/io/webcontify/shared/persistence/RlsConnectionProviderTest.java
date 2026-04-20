package io.webcontify.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RlsConnectionProviderTest {

    @Mock private HikariDataSource dataSource;
    @Mock private TenantSlugCache tenantSlugCache;
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private PreparedStatement schemaCheckStatement;
    @Mock private PreparedStatement slugQueryStatement;
    @Mock private Statement statement;
    @Mock private ResultSet resultSet;
    @Mock private ResultSet schemaResultSet;
    @Mock private ResultSet slugResultSet;

    private RlsConnectionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RlsConnectionProvider(dataSource, tenantSlugCache);
    }

    @Test
    void shouldSetTenantIdAndSearchPathOnGetConnection() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(dataSource.getConnection()).thenReturn(connection);
        // First prepareStatement call: SET tenant_id
        // Second prepareStatement call: slug query (cache miss)
        // Third prepareStatement call: schema exists check
        when(connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)"))
                .thenReturn(preparedStatement);
        when(tenantSlugCache.getIfPresent(tenantId)).thenReturn(null);
        when(connection.prepareStatement("SELECT slug FROM tenant WHERE id = ? AND deleted_at IS NULL"))
                .thenReturn(slugQueryStatement);
        when(slugQueryStatement.executeQuery()).thenReturn(slugResultSet);
        when(slugResultSet.next()).thenReturn(true);
        when(slugResultSet.getString(1)).thenReturn("acme-corp");
        when(connection.prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?"))
                .thenReturn(schemaCheckStatement);
        when(schemaCheckStatement.executeQuery()).thenReturn(schemaResultSet);
        when(schemaResultSet.next()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);

        Connection result = provider.getConnection(tenantId.toString());

        assertThat(result).isSameAs(connection);
        verify(preparedStatement).setString(1, tenantId.toString());
        verify(preparedStatement).execute();
        verify(tenantSlugCache).put(tenantId, "acme-corp");
        verify(statement).execute("SET LOCAL search_path = tenant_acme_corp, public");
    }

    @Test
    void shouldSkipSearchPathWhenSlugNotFound() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)"))
                .thenReturn(preparedStatement);
        when(tenantSlugCache.getIfPresent(tenantId)).thenReturn(null);
        when(connection.prepareStatement("SELECT slug FROM tenant WHERE id = ? AND deleted_at IS NULL"))
                .thenReturn(slugQueryStatement);
        when(slugQueryStatement.executeQuery()).thenReturn(slugResultSet);
        when(slugResultSet.next()).thenReturn(false);

        provider.getConnection(tenantId.toString());

        verify(connection, never()).createStatement();
    }

    @Test
    void shouldNotSetTenantIdOrSearchPathForPlatformContext() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);

        Connection result = provider.getConnection("");

        assertThat(result).isSameAs(connection);
        verify(connection, never()).prepareStatement(any());
        verify(connection, never()).createStatement();
    }

    @Test
    void shouldResetTenantIdAndSearchPathOnRelease() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(connection.prepareStatement(any())).thenReturn(preparedStatement);
        when(connection.createStatement()).thenReturn(statement);

        provider.releaseConnection(tenantId.toString(), connection);

        verify(preparedStatement).execute();
        verify(statement).execute("SET LOCAL search_path = public");
        verify(connection).close();
    }

    @Test
    void shouldReplaceHyphensWithUnderscoresInSchemaName() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)"))
                .thenReturn(preparedStatement);
        when(tenantSlugCache.getIfPresent(tenantId)).thenReturn("my-test-tenant");
        when(connection.prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?"))
                .thenReturn(schemaCheckStatement);
        when(schemaCheckStatement.executeQuery()).thenReturn(schemaResultSet);
        when(schemaResultSet.next()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);

        provider.getConnection(tenantId.toString());

        verify(statement).execute("SET LOCAL search_path = tenant_my_test_tenant, public");
    }

    @Test
    void shouldUseCachedSlugWithoutDbQuery() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)"))
                .thenReturn(preparedStatement);
        when(tenantSlugCache.getIfPresent(tenantId)).thenReturn("cached-tenant");
        when(connection.prepareStatement("SELECT 1 FROM information_schema.schemata WHERE schema_name = ?"))
                .thenReturn(schemaCheckStatement);
        when(schemaCheckStatement.executeQuery()).thenReturn(schemaResultSet);
        when(schemaResultSet.next()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);

        provider.getConnection(tenantId.toString());

        // Should NOT query slug from DB since it was cached
        verify(connection, never()).prepareStatement("SELECT slug FROM tenant WHERE id = ? AND deleted_at IS NULL");
        verify(statement).execute("SET LOCAL search_path = tenant_cached_tenant, public");
    }
}
