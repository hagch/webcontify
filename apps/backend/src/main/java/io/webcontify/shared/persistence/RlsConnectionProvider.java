package io.webcontify.shared.persistence;

import static io.webcontify.shared.persistence.RlsTenantIdentifierResolver.PLATFORM_TENANT_CONTEXT;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RlsConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final HikariDataSource dataSource;

    public RlsConnectionProvider(HikariDataSource dataSource) {
        this.dataSource = dataSource;
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
                    "SELECT set_config('app.tenant_id', ?, true)")) {
                ps.setString(1, tenantId);
                ps.execute();
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
