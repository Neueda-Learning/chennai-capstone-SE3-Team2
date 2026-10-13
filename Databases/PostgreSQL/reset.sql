-- =====================================================================
-- Undoes Databases/PostgreSQL/local/setup.sql: drops trading_system_db (both
-- its schemas, public and auth) and the three application roles, so
-- setup.sql can run again on a clean slate.
--
-- EVERYTHING in it is lost -- accounts, orders, logins. It touches nothing
-- else on the server. Run as the superuser (on RDS: the master user):
--
--   psql -h <host> -U postgres -d postgres -v ON_ERROR_STOP=1 -f Databases/PostgreSQL/reset.sql
-- =====================================================================

\set ON_ERROR_STOP 1

-- A service still connected would block the drop; close the stack first.
DROP DATABASE IF EXISTS trading_system_db WITH (FORCE);
-- A separate auth database is what the platform used before auth moved into
-- a schema; dropped too, so an older install resets cleanly.
DROP DATABASE IF EXISTS auth WITH (FORCE);

DROP ROLE IF EXISTS trading_app;
DROP ROLE IF EXISTS analytics_ro;
DROP ROLE IF EXISTS auth_app;

\echo 'Dropped trading_system_db and roles trading_app, analytics_ro, auth_app.'
