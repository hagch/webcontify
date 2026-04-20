package io.webcontify.shared.persistence;

import static io.webcontify.shared.persistence.RlsTenantIdentifierResolver.PLATFORM_TENANT_CONTEXT;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RlsConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Logger log = LoggerFactory.getLogger(RlsConnectionProvider.class);

    private final HikariDataSource dataSource;
    private final TenantSlugCache tenantSlugCache;

    public RlsConnectionProvider(HikariDataSource dataSource, TenantSlugCache tenantSlugCache) {
        this.dataSource = dataSource;
        this.tenantSlugCache = tenantSlugCache;
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
            setSearchPath(connection, tenantId);
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantId, Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.tenant_id', '', false)")) {
            ps.execute();
        }
        resetSearchPath(connection);
        connection.close();
    }

    private void setSearchPath(Connection connection, String tenantId) throws SQLException {
        try {
            UUID id = UUID.fromString(tenantId);
            // First check the Caffeine cache (no DB call).
            // On cache miss, query directly on the EXISTING connection to avoid
            // recursive connection allocation (TenantSlugResolver uses JPA which
            // would request another connection from this same provider → deadlock).
            String slug = tenantSlugCache.getIfPresent(id);
            if (slug == null) {
                slug = querySlugDirectly(connection, id);
                if (slug != null) {
                    tenantSlugCache.put(id, slug);
                }
            }
            if (slug != null) {
                String schemaName = "tenant_" + slug.replace("-", "_");
                if (schemaExists(connection, schemaName)) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("SET LOCAL search_path = " + schemaName + ", public");
                    }
                    log.trace("Set search_path to {}, public for tenant {}", schemaName, tenantId);
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant ID format for search_path resolution: {}", tenantId);
        }
    }

    private boolean schemaExists(Connection connection, String schemaName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, schemaName);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String querySlugDirectly(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT slug FROM tenant WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, tenantId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void resetSearchPath(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL search_path = public");
        }
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
