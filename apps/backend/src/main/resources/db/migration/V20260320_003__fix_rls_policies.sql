-- Fix RLS policies to correctly handle session-level tenant isolation.
-- The previous policies used transaction-local set_config (is_local=true) which
-- was reset after each autocommit statement. We now use session-level settings.
-- The policy must also handle the empty-string case used by PLATFORM_TENANT_CONTEXT.

-- Fix tenant_user RLS policy
DROP POLICY IF EXISTS tenant_isolation_tenant_user ON tenant_user;
CREATE POLICY tenant_isolation_tenant_user ON tenant_user
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );

-- Fix tenant_role RLS policy
DROP POLICY IF EXISTS tenant_isolation_tenant_role ON tenant_role;
CREATE POLICY tenant_isolation_tenant_role ON tenant_role
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );
