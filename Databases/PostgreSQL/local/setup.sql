-- =====================================================================
-- Builds the platform's database on a PostgreSQL you did not start with
-- docker compose: one you installed yourself, or Amazon RDS.
--
-- It does exactly what the container's init does -- init/00-roles.sh, then
-- schema.sql, then seed_data.sql -- with psql alone, so it runs the same on
-- Windows, macOS, Linux and against RDS.
--
-- ONE database, trading_system_db, with two schemas:
--   public  the trading platform  (trading_app read/write, analytics_ro read)
--   auth    logins and tokens     (auth_app only)
--
-- Run it once, as the superuser (on RDS: the master user), from the
-- repository root:
--
--   psql -h <host> -U postgres -d postgres -v ON_ERROR_STOP=1 \
--     -v trading_pw=<DB_PASSWORD> -v analytics_pw=<ANALYTICS_DB_PASSWORD> \
--     -v auth_pw=<AUTH_DB_PASSWORD> -f Databases/PostgreSQL/local/setup.sql
--
-- The role names are the .env defaults: trading_app, analytics_ro, auth_app.
-- It stops at the first error and refuses to run over an existing
-- trading_system_db; Databases/PostgreSQL/reset.sql drops it, and the roles.
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

-- The user running this owns every table, so default privileges are granted
-- on its behalf -- as 00-roles.sh does for POSTGRES_USER.
\set superuser :USER

-- ---------------------------------------------------------------------
-- Roles and the database
-- ---------------------------------------------------------------------
CREATE ROLE trading_app  LOGIN PASSWORD :'trading_pw';
CREATE ROLE analytics_ro LOGIN PASSWORD :'analytics_pw';
CREATE ROLE auth_app     LOGIN PASSWORD :'auth_pw';

-- UTF8 whatever the installer's default: customer names and addresses are
-- not all ASCII. template0, because template1 carries the server's encoding.
CREATE DATABASE trading_system_db ENCODING 'UTF8' TEMPLATE template0;

REVOKE CONNECT ON DATABASE trading_system_db FROM PUBLIC;
GRANT CONNECT ON DATABASE trading_system_db TO trading_app, analytics_ro, auth_app;

\connect trading_system_db

-- ---------------------------------------------------------------------
-- public: the trading platform
-- ---------------------------------------------------------------------
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO trading_app, analytics_ro;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO trading_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO trading_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA public
    GRANT SELECT ON TABLES TO analytics_ro;

-- ---------------------------------------------------------------------
-- auth: logins and tokens, for the auth service only
-- ---------------------------------------------------------------------
CREATE SCHEMA auth;
REVOKE ALL ON SCHEMA auth FROM PUBLIC;
GRANT USAGE ON SCHEMA auth TO auth_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA auth
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO auth_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"superuser" IN SCHEMA auth
    GRANT USAGE, SELECT ON SEQUENCES TO auth_app;
-- auth's SQL names its tables bare; in this database they mean auth's.
ALTER ROLE auth_app IN DATABASE trading_system_db SET search_path = auth;

-- ---------------------------------------------------------------------
-- Every table (both schemas), then the reference and demo data
-- ---------------------------------------------------------------------
\ir ../schema.sql
\ir ../seed_data.sql

\echo 'Done: trading_system_db (schemas public and auth); roles trading_app, analytics_ro, auth_app.'
