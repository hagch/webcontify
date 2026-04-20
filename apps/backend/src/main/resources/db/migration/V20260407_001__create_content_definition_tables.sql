-- Content Definition
CREATE TABLE content_definition (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES tenant(id),
    app_id           UUID NOT NULL REFERENCES app(id),
    name             VARCHAR(255) NOT NULL,
    slug             VARCHAR(255) NOT NULL,
    description      TEXT,
    display_format   VARCHAR(500),
    position_x       DOUBLE PRECISION,
    position_y       DOUBLE PRECISION,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    deleted_by       UUID,
    UNIQUE(app_id, name),
    UNIQUE(app_id, slug)
);

CREATE INDEX idx_cd_tenant_id ON content_definition(tenant_id);
CREATE INDEX idx_cd_app_id ON content_definition(app_id);

ALTER TABLE content_definition ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_content_definition ON content_definition
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );

-- Field Definition
CREATE TABLE content_field_definition (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    definition_id    UUID NOT NULL REFERENCES content_definition(id) ON DELETE CASCADE,
    field_key        VARCHAR(255) NOT NULL,
    field_type       VARCHAR(50) NOT NULL,
    required         BOOLEAN NOT NULL DEFAULT false,
    unique_value     BOOLEAN NOT NULL DEFAULT false,
    sortable         BOOLEAN NOT NULL DEFAULT false,
    filterable       BOOLEAN NOT NULL DEFAULT false,
    config           JSONB,
    position         INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    deleted_by       UUID,
    UNIQUE(definition_id, field_key)
);

CREATE INDEX idx_cfd_definition_id ON content_field_definition(definition_id);
CREATE INDEX idx_cfd_field_type ON content_field_definition(field_type);

ALTER TABLE content_field_definition ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_content_field_definition ON content_field_definition
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );

-- Content Reference join table (populated during Content Management phase, schema needed now)
CREATE TABLE content_reference (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    source_field_id UUID NOT NULL REFERENCES content_field_definition(id),
    source_entry_id UUID NOT NULL,
    target_entry_id UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    UNIQUE(source_field_id, source_entry_id, target_entry_id)
);

CREATE INDEX idx_cr_source_field ON content_reference(source_field_id);
CREATE INDEX idx_cr_source_entry ON content_reference(source_entry_id);
CREATE INDEX idx_cr_target_entry ON content_reference(target_entry_id);

ALTER TABLE content_reference ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_content_reference ON content_reference
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );
