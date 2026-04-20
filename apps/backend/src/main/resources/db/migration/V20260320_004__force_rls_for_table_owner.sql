-- Force RLS on tenant-scoped tables so that the table owner (application DB user)
-- is also subject to the tenant isolation policy.
-- Without FORCE ROW LEVEL SECURITY, PostgreSQL bypasses RLS for table owners,
-- which means the application user would see rows from ALL tenants.

ALTER TABLE tenant_user FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_role FORCE ROW LEVEL SECURITY;
