-- Fix RLS policies to safely handle empty string for app.tenant_id.
-- PostgreSQL does not guarantee short-circuit evaluation in RLS USING clauses,
-- so we must avoid casting empty string to UUID entirely.
-- Use a CASE expression or regexp check to only cast when the value is a valid UUID.

-- Fix tenant_user RLS policy
DROP POLICY IF EXISTS tenant_isolation_tenant_user ON tenant_user;
CREATE POLICY tenant_isolation_tenant_user ON tenant_user
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );

-- Fix tenant_role RLS policy
DROP POLICY IF EXISTS tenant_isolation_tenant_role ON tenant_role;
CREATE POLICY tenant_isolation_tenant_role ON tenant_role
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
