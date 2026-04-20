package io.webcontify.shared.persistence;

import static org.mockito.Mockito.*;

import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantAwareDataSourceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSetLocalTenantIdForRegularTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        setAuthentication(new TenantInformation(tenantId, false));

        DataSource delegate = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(delegate.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        TenantAwareDataSource dataSource = new TenantAwareDataSource(delegate);
        dataSource.getConnection();

        verify(statement).execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
        verify(statement).close();
    }

    @Test
    void shouldNotSetLocalTenantIdForPlatformAdmin() throws Exception {
        setAuthentication(new TenantInformation(null, true));

        DataSource delegate = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(delegate.getConnection()).thenReturn(connection);

        TenantAwareDataSource dataSource = new TenantAwareDataSource(delegate);
        dataSource.getConnection();

        verify(connection, never()).createStatement();
    }

    @Test
    void shouldNotSetTenantIdWhenNoAuthenticationPresent() throws Exception {
        DataSource delegate = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(delegate.getConnection()).thenReturn(connection);

        TenantAwareDataSource dataSource = new TenantAwareDataSource(delegate);
        dataSource.getConnection();

        verify(connection, never()).createStatement();
    }

    private void setAuthentication(TenantInformation info) {
        var auth = new TenantAwareAuthentication(info, "user", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
