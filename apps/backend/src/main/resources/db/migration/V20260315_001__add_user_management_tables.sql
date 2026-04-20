-- V20260315_001__add_user_management_tables.sql

CREATE TABLE tenant_user (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    email           VARCHAR(255) NOT NULL,
    first_name      VARCHAR(255) NOT NULL,
    last_name       VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'INVITED',
    invited_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    CONSTRAINT uq_tenant_user_email UNIQUE (tenant_id, email)
);

CREATE TABLE tenant_role (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenant(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    is_system_role  BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID NOT NULL,
    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID,
    CONSTRAINT uq_tenant_role_name UNIQUE (tenant_id, name)
);

-- Keine tenant_user_role Tabelle — Zuordnung User<->Rolle laeuft ueber OpenFGA (role#assignee)

CREATE INDEX idx_tenant_user_tenant_id ON tenant_user(tenant_id);
CREATE INDEX idx_tenant_role_tenant_id ON tenant_role(tenant_id);

ALTER TABLE tenant_user ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenant_user ON tenant_user
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );

ALTER TABLE tenant_role ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenant_role ON tenant_role
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id = current_setting('app.tenant_id')::uuid
    );
