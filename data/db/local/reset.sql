-- =====================================================================
-- Undoes data/db/local/setup.sql: drops the trading and auth databases and
-- the three application roles, so setup.sql can run again on a clean slate.
--
-- EVERYTHING in both databases is lost -- accounts, orders, logins. It
-- touches nothing else on the server. Run as the superuser:
--
--   psql -U postgres -v ON_ERROR_STOP=1 -f data/db/local/reset.sql
-- =====================================================================

\set ON_ERROR_STOP 1

-- A service still connected would block the drop; close the stack first.
DROP DATABASE IF EXISTS trading WITH (FORCE);
DROP DATABASE IF EXISTS auth    WITH (FORCE);

DROP ROLE IF EXISTS trading_app;
DROP ROLE IF EXISTS analytics_ro;
DROP ROLE IF EXISTS auth_app;

\echo 'Dropped databases trading and auth, and roles trading_app, analytics_ro, auth_app.'
