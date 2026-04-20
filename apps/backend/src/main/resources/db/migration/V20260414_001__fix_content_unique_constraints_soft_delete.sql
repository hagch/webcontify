-- Replace table-level unique constraints with partial unique indexes that
-- exclude soft-deleted rows (deleted_at IS NULL). Same pattern as the apps
-- fix in V20260406_001. Without this, re-creating a content definition
-- (or a field on a definition) with the same name/slug/field_key after a
-- soft delete fails with a duplicate-key violation even though the record
-- is semantically gone.

-- content_definition: (app_id, name) and (app_id, slug)
ALTER TABLE content_definition DROP CONSTRAINT content_definition_app_id_name_key;
ALTER TABLE content_definition DROP CONSTRAINT content_definition_app_id_slug_key;

CREATE UNIQUE INDEX uq_content_definition_app_name_active
    ON content_definition(app_id, name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_content_definition_app_slug_active
    ON content_definition(app_id, slug) WHERE deleted_at IS NULL;

-- content_field_definition: (definition_id, field_key)
ALTER TABLE content_field_definition DROP CONSTRAINT content_field_definition_definition_id_field_key_key;

CREATE UNIQUE INDEX uq_content_field_definition_active
    ON content_field_definition(definition_id, field_key) WHERE deleted_at IS NULL;
