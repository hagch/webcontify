-- Replace table-level unique constraints with partial unique indexes
-- that exclude soft-deleted rows (deleted_at IS NULL).
-- This allows re-creating an app with the same name/slug after soft delete.

ALTER TABLE app DROP CONSTRAINT app_tenant_id_name_key;
ALTER TABLE app DROP CONSTRAINT app_tenant_id_slug_key;

CREATE UNIQUE INDEX uq_app_tenant_name_active ON app(tenant_id, name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_app_tenant_slug_active ON app(tenant_id, slug) WHERE deleted_at IS NULL;
