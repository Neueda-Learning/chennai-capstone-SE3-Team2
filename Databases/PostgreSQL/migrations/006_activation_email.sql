-- =====================================================================
-- 006_activation_email.sql
--
-- One row per ACCOUNT_PROVISIONED event the activation mailer has sent an
-- email for. Kafka delivers at least once, so the same event arrives twice
-- eventually; the event id is the idempotency key, and a second delivery
-- that finds its row sends nothing.
--
-- No email address and no token: this table records THAT an email went, not
-- what was in it.
-- =====================================================================

CREATE TABLE IF NOT EXISTS activation_email (
    event_id    UUID         PRIMARY KEY,
    client_id   INTEGER      NOT NULL REFERENCES client_account (client_id),
    sent_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
