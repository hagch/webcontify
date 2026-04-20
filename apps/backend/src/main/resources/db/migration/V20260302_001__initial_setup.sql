-- Enable required PostgreSQL extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Application variable for Row-Level Security
-- Used by DataSource Proxy: SET LOCAL app.tenant_id = '<uuid>'
-- Platform Admin: variable not set -> NULL -> sees all rows
DO $$
BEGIN
    PERFORM set_config('app.tenant_id', NULL, true);
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;
