CREATE TABLE app_dependency (
    app_id          UUID NOT NULL REFERENCES app(id) ON DELETE CASCADE,
    depends_on_id   UUID NOT NULL REFERENCES app(id),
    tenant_id       UUID NOT NULL,
    PRIMARY KEY (app_id, depends_on_id),
    CHECK (app_id != depends_on_id)
);

ALTER TABLE app_dependency ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_app_dependency ON app_dependency
    USING (
        NULLIF(current_setting('app.tenant_id', true), '') IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
