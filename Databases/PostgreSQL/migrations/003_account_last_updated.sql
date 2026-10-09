-- =====================================================================
-- 003_account_last_updated.sql
--
-- AccountResponse.lastUpdated is a required field of the binding
-- contract, and client_account carried only created_at.
--
-- It is maintained by a trigger rather than by the application. Every
-- write to this row already goes through the optimistic lock, and there
-- are two of them today with more coming in Sprint 7 -- a timestamp set
-- in Java is one that a future writer forgets to set, and the field it
-- silently stops updating is the one a client uses to decide whether
-- its cached view is stale.
-- =====================================================================

ALTER TABLE client_account
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Existing rows have never been written since creation, so their last
-- update genuinely is their creation.
UPDATE client_account
    SET updated_at = created_at
    WHERE updated_at < created_at;

CREATE OR REPLACE FUNCTION touch_client_account_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_client_account_updated_at ON client_account;

CREATE TRIGGER tr_client_account_updated_at
    BEFORE UPDATE ON client_account
    FOR EACH ROW
    EXECUTE FUNCTION touch_client_account_updated_at();
