package io.webcontify.tenant.service;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantSchemaService {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaService.class);
    private static final Pattern VALID_SCHEMA_SLUG = Pattern.compile("^[a-z0-9_]+$");

    private final JdbcTemplate jdbcTemplate;

    public TenantSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createSchema(String slug) {
        String schemaName = toSchemaName(slug);
        String sql = "CREATE SCHEMA IF NOT EXISTS " + schemaName;
        jdbcTemplate.execute(sql);
        log.info("Created tenant schema: {}", schemaName);
    }

    public void dropSchema(String slug) {
        String schemaName = toSchemaName(slug);
        String sql = "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE";
        jdbcTemplate.execute(sql);
        log.info("Dropped tenant schema: {}", schemaName);
    }

    private String toSchemaName(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Invalid slug: must not be null or blank");
        }
        String normalized = slug.replace("-", "_");
        if (!VALID_SCHEMA_SLUG.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Invalid slug for schema name: '" + slug + "' (after normalization: '" + normalized + "')");
        }
        return "tenant_" + normalized;
    }
}
