ALTER TABLE content_field_definition
    ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pending_action VARCHAR(20) NULL;

-- All existing fields are assumed to have been DDL-applied prior to this feature
UPDATE content_field_definition SET published = TRUE WHERE deleted_at IS NULL;
