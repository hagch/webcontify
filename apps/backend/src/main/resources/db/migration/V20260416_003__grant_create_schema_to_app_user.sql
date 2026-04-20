-- Allow the application user to create per-tenant schemas at runtime.
-- Required by TenantSchemaService for tenant provisioning.
-- Uses dynamic SQL because GRANT does not support current_database().
-- Skips gracefully if the webcontify_app role does not exist (e.g. in Testcontainers).
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'webcontify_app') THEN
        EXECUTE format('GRANT CREATE ON DATABASE %I TO webcontify_app', current_database());
    END IF;
END
$$;
