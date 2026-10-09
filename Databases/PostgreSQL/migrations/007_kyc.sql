-- =====================================================================
-- 007_kyc.sql
--
-- KYC: one verification per customer, and the outbox that announces a
-- pass on kyc-events.
--
-- kyc_verification is keyed by client_id, so a customer has exactly one.
-- That makes submission idempotent and resubmission impossible, which is
-- the decision: REJECTED is terminal. The check constraints hold the shape
-- of a decision in the database rather than in code -- a decided row
-- carries its time, and only a rejection carries a reason.
--
-- No personal data here. The checks are read from client_profile and
-- client_account when they run; this table records the outcome only.
-- =====================================================================

CREATE TABLE IF NOT EXISTS kyc_verification (
    client_id     INTEGER      PRIMARY KEY
                               REFERENCES client_account (client_id),
    status        VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    -- Set on REJECTED, and only then.
    reason        TEXT         NULL,
    -- Which checks ran and which failed, for the audit trail.
    checks        JSONB        NULL,
    submitted_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_at    TIMESTAMPTZ  NULL,

    CONSTRAINT ck_kyc_verification_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_kyc_verification_decided_at
        CHECK ((status = 'PENDING') = (decided_at IS NULL)),
    CONSTRAINT ck_kyc_verification_reason
        CHECK ((status = 'REJECTED') = (reason IS NOT NULL))
);

-- The scheduled job's only query: pending checks, oldest first.
CREATE INDEX IF NOT EXISTS ix_kyc_verification_pending
    ON kyc_verification (submitted_at) WHERE status = 'PENDING';

-- Events waiting to be published, written in the same transaction as the
-- change they announce: a rolled-back decision leaves no row and publishes
-- nothing, and a committed one is retried until the broker takes it. Same
-- shape as the auth service's outbox_event, which lives in its own database.
CREATE TABLE IF NOT EXISTS outbox_event (
    event_id      UUID         PRIMARY KEY,
    topic         VARCHAR(249) NOT NULL,
    message_key   VARCHAR(64)  NOT NULL,
    -- The whole envelope, exactly as it goes on the wire.
    envelope      JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ  NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    TEXT         NULL
);

CREATE INDEX IF NOT EXISTS ix_outbox_event_unpublished
    ON outbox_event (created_at) WHERE published_at IS NULL;
