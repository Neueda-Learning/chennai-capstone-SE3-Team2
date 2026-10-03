-- =====================================================================
-- 010_payments.sql
--
-- Deposits and withdrawals through a payment gateway, recorded in the
-- fund_transfer table Sprint 3 already designed: direction DEPOSIT or
-- WITHDRAWAL, status PENDING -> SUCCESS or FAILED, one idempotency key per
-- client. This adds what deciding a transfer needs, as kyc_verification has:
-- why it failed, when it was decided, and a count of failed attempts so a
-- transfer whose processing keeps failing is set aside rather than retried
-- forever.
--
-- No CHECK ties reason or decided_at to status, unlike kyc_verification:
-- Sprint 3's seed inserts finished transfers without either, and seed files
-- run after migrations. The service writes them together.
-- =====================================================================

ALTER TABLE fund_transfer
    ADD COLUMN IF NOT EXISTS reason     TEXT        NULL,
    ADD COLUMN IF NOT EXISTS decided_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS attempts   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error TEXT        NULL;

-- The payment job reads only the PENDING ones, oldest first.
CREATE INDEX IF NOT EXISTS ix_fund_transfer_pending
    ON fund_transfer (created_at) WHERE status = 'PENDING';
