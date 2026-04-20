-- Replace the global unique constraint on slug with a partial index
-- that only enforces uniqueness among non-deleted tenants.
-- This allows soft-deleted tenant slugs to be reused.

ALTER TABLE tenant DROP CONSTRAINT IF EXISTS uq_tenant_slug;

CREATE UNIQUE INDEX uq_tenant_slug_active ON tenant(slug) WHERE deleted_at IS NULL;
