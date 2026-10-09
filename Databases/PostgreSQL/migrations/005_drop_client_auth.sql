-- =====================================================================
-- 005_drop_client_auth.sql
--
-- Sprint 8 moved credentials into the auth service, which owns the only
-- store in the platform that ever sees one. client_auth has had no
-- reader since: no Java, no mapper, and nothing holds a foreign key to
-- it. Leaving it means the database the Trade REST API connects to
-- still holds a table of password hashes, which is the arrangement
-- Sprint 8 exists to remove.
--
-- The hashes in it were never real. The seed says so: structurally
-- valid bcrypt strings, test data only.
-- =====================================================================

DROP TABLE IF EXISTS client_auth;
