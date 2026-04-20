CREATE TABLE content_field_change_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    field_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL,
    old_type VARCHAR(50) NULL,
    new_type VARCHAR(50) NULL,
    data_loss BOOLEAN NOT NULL DEFAULT FALSE,
    executed_at TIMESTAMPTZ NOT NULL,
    executed_by UUID NOT NULL
);

CREATE INDEX idx_content_field_change_log_definition_id ON content_field_change_log(definition_id);

ALTER TABLE content_field_change_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON content_field_change_log USING (tenant_id::text = current_setting('app.tenant_id', true));
