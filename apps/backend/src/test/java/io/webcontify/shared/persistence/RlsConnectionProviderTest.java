package io.webcontify.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    @Mock private Statement statement;

    private RlsConnectionProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RlsConnectionProvider(dataSource, tenantSlugCache);
    }

    @Test
    void shouldSetTenantIdAndSearchPathOnGetConnection() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(preparedStatement);
        when(connection.createStatement()).thenReturn(statement);
        when(tenantSlugCache.resolveSlug(tenantId)).thenReturn(Optional.of("acme-corp"));

        Connection result = provider.getConnection(tenantId.toString());

        assertThat(result).isSameAs(connection);
        verify(preparedStatement).setString(1, tenantId.toString());
        verify(preparedStatement).execute();
        verify(statement).execute("SET LOCAL search_path = tenant_acme_corp, public");
    }

    @Test
    void shouldSkipSearchPathWhenSlugNotFound() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(preparedStatement);
        when(tenantSlugCache.resolveSlug(tenantId)).thenReturn(Optional.empty());

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
        when(connection.prepareStatement(any())).thenReturn(preparedStatement);
        when(connection.createStatement()).thenReturn(statement);
        when(tenantSlugCache.resolveSlug(tenantId)).thenReturn(Optional.of("my-test-tenant"));

        provider.getConnection(tenantId.toString());

        verify(statement).execute("SET LOCAL search_path = tenant_my_test_tenant, public");
    }
}
