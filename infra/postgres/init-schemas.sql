-- Allow the app user to create per-tenant schemas at runtime
-- (webcontify_app is created later by Flyway, so this runs via Flyway migration V20260416_003)

CREATE SCHEMA IF NOT EXISTS keycloak;
CREATE SCHEMA IF NOT EXISTS openfga;
GRANT ALL ON SCHEMA keycloak TO webcontify;
GRANT ALL ON SCHEMA openfga TO webcontify;
