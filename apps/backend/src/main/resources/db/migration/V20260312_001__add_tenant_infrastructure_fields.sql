-- Add infrastructure fields to tenant table
ALTER TABLE tenant ADD COLUMN openfga_store_id VARCHAR(255);
ALTER TABLE tenant ADD COLUMN keycloak_realm_name VARCHAR(255);
ALTER TABLE tenant ADD COLUMN provisioning_status JSONB;

-- Unique constraint on tenant name
ALTER TABLE tenant ADD CONSTRAINT uq_tenant_name UNIQUE (name);
