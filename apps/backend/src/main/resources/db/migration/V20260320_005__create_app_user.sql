-- Create a dedicated application user that is NOT a superuser.
-- RLS policies are enforced on non-superusers, so the app must use this user.
-- The superuser 'webcontify' is used only for Flyway migrations.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'webcontify_app') THEN
        CREATE ROLE webcontify_app WITH LOGIN PASSWORD 'webcontify_app';
    END IF;
END
$$;

-- Grant necessary privileges on all tables
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO webcontify_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO webcontify_app;

-- Ensure future tables are also accessible
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO webcontify_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO webcontify_app;
