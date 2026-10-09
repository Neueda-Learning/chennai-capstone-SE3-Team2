-- =====================================================================
-- 008_kyc_attempts.sql
--
-- Counts failed KYC checks, so a customer whose check keeps failing is
-- set aside rather than retried forever.
--
-- The job takes the longest-waiting customers first. Without a count, one
-- whose check always fails stays at the front: enough of them would fill
-- every batch and nobody behind them would ever be checked. At
-- kyc.max-attempts the job stops picking the customer up. They stay
-- PENDING -- nothing was decided -- until someone fixes the cause and sets
-- attempts back to 0.
--
-- last_error holds the exception's class name only: a database error's
-- message can quote the customer's data back.
-- =====================================================================

ALTER TABLE kyc_verification
    ADD COLUMN IF NOT EXISTS attempts   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error TEXT    NULL;
