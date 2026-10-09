-- =====================================================================
-- Builds the platform's databases on a PostgreSQL you installed yourself
-- (a Windows laptop, say), instead of in the postgres container.
--
-- It does exactly what the container's init does -- Databases/PostgreSQL/init/00-roles.sh,
-- then every schema, migration, index and seed file in the order
-- docker-compose.yml mounts them, then Databases/PostgreSQL/init/13-auth-schema.sh -- with
-- psql alone, so it runs the same on Windows, macOS and Linux.
--
-- Run it once, as the superuser, from the repository root:
--
--   psql -U postgres -v ON_ERROR_STOP=1 -v trading_pw=<DB_PASSWORD> -v analytics_pw=<ANALYTICS_DB_PASSWORD> -v auth_pw=<AUTH_DB_PASSWORD> -f Databases/PostgreSQL/local/setup.sql
--
-- The role names are the .env defaults: trading_app, analytics_ro, auth_app.
-- It stops at the first error and refuses to run over an existing `trading`
-- or `auth` database; Databases/PostgreSQL/reset.sql drops both, and the roles.
-- See docs/runbooks/windows-dev-setup.md.
-- =====================================================================

\set ON_ERROR_STOP 1

\if :{?trading_pw}
\else
  \echo 'Pass the passwords: -v trading_pw=... -v analytics_pw=... -v auth_pw=...'
  \quit
\endif
\if :{?analytics_pw}
\else
  \echo 'Pass -v analytics_pw=...'
  \quit
\endif
\if :{?auth_pw}
\else
  \echo 'Pass -v auth_pw=...'
  \quit
\endif

-- The superuser running this owns every table, so default privileges are
-- granted on its behalf -- as 00-roles.sh does for POSTGRES_USER.
\set superuser :USER

-- ---------------------------------------------------------------------
-- Roles and databases (00-roles.sh)
-- ---------------------------------------------------------------------
CREATE ROLE trading_app  LOGIN PASSWORD :'trading_pw';
CREATE ROLE analytics_ro LOGIN PASSWORD :'analytics_pw';
CREATE ROLE auth_app     LOGIN PASSWORD :'auth_pw';

-- UTF8 whatever the installer's default: customer names and addresses are
-- not all ASCII. template0, because template1 carries the server's encoding.
CREATE DATABASE trading_system_db ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE auth    ENCODING 'UTF8' TEMPLATE template0;

REVOKE CONNECT ON DATABASE trading_system_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE auth    FROM PUBLIC;
GRANT CONNECT ON DATABASE trading_system_db TO trading_app, analytics_ro;
GRANT CONNECT ON DATABASE auth    TO auth_app;

-- ---------------------------------------------------------------------
-- trading: privileges, then the schema in docker-compose.yml's order
-- ---------------------------------------------------------------------
\connect trading_system_db

GRANT USAGE ON SCHEMA public TO trading_app, analytics_ro;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO trading_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO trading_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT SELECT ON TABLES TO analytics_ro;

\ir ../base/000_base_schema.sql
\ir ../migrations/001_schema_migrations.sql
\ir ../migrations/002_api_alignment.sql
\ir ../migrations/003_account_last_updated.sql
\ir ../migrations/004_execution_columns.sql
\ir ../migrations/005_drop_client_auth.sql
\ir ../migrations/006_activation_email.sql
\ir ../migrations/007_kyc.sql
\ir ../migrations/008_kyc_attempts.sql
\ir ../migrations/009_bank_account.sql
\ir ../migrations/010_payments.sql
\ir ../migrations/011_preferences.sql
\ir ../migrations/012_notifications.sql
\ir ../migrations/013_watchlists.sql
\ir ../migrations/014_portfolio.sql
\ir ../migrations/015_strategy.sql
\ir ../migrations/016_strategy_indicators.sql
\ir ../indexes/001_performance_indexes.sql
\ir ../seed/001_reference_data.sql
\ir ../seed/002_clients.sql
\ir ../seed/003_instruments.sql
\ir ../seed/004_transactions.sql
\ir ../seed/005_fauxnance_instruments.sql
\ir ../seed/006_bank_accounts.sql
\ir ../seed/007_mf_nav_funds.sql
\ir ../seed/008_instrument_universe.sql

-- ---------------------------------------------------------------------
-- auth: privileges, then its migrations (13-auth-schema.sh)
-- ---------------------------------------------------------------------
\connect auth

GRANT USAGE ON SCHEMA public TO auth_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO auth_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO auth_app;

\ir ../../../Services/auth-service/migrations/001_credentials.sql
\ir ../../../Services/auth-service/migrations/002_refresh_tokens.sql
\ir ../../../Services/auth-service/migrations/003_provisioning_outbox.sql
\ir ../../../Services/auth-service/migrations/004_activation_tokens.sql

\echo 'Done: databases trading and auth; roles trading_app, analytics_ro, auth_app.'
