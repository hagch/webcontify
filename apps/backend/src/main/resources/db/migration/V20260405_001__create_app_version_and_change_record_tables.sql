-- App Versions (immutable snapshots)
CREATE TABLE app_version (
    id              UUID PRIMARY KEY,
    app_id          UUID NOT NULL REFERENCES app(id) ON DELETE CASCADE,
    tenant_id       UUID NOT NULL,
    version         VARCHAR(50) NOT NULL,
    s3_key          VARCHAR(500) NOT NULL,
    release_notes   TEXT,
    published_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_by    UUID,
    UNIQUE(app_id, version)
);

ALTER TABLE app_version ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_app_version ON app_version
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );

-- Change Records (shared, generic audit/revision tracking)
CREATE TABLE change_record (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID NOT NULL,
    change_type     VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    source          VARCHAR(255) NOT NULL,
    draft_version   VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID
);

CREATE INDEX idx_change_record_lookup
    ON change_record(entity_type, entity_id, created_at);
CREATE INDEX idx_change_record_draft
    ON change_record(entity_type, entity_id, draft_version)
    WHERE draft_version IS NOT NULL;

ALTER TABLE change_record ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_change_record ON change_record
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
