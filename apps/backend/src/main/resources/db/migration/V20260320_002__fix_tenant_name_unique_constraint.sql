-- Replace the global unique constraint on name with a partial index
-- that only enforces uniqueness among non-deleted tenants.
-- This allows soft-deleted tenant names to be reused.

ALTER TABLE tenant DROP CONSTRAINT IF EXISTS uq_tenant_name;

CREATE UNIQUE INDEX uq_tenant_name_active ON tenant(name) WHERE deleted_at IS NULL;
