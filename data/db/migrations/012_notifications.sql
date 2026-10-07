-- =====================================================================
-- 012_notifications.sql
--
-- Sprint 10, the notifications module (com.yellow.trade.notifications).
--
-- The ledger. One row per message a customer is owed: recorded QUEUED
-- before the Kafka offset is committed, then SENT or FAILED by the
-- dispatcher (decision log 0006). The channel and the destination are
-- filled in when it is sent, from the preferences module at that moment,
-- so they are null while a row waits.
--
-- The key is the event the message is about and, for a price alert, the
-- alert: one quote can cross several customers' alerts, and each of them
-- is owed its own message, but the same quote delivered twice for the
-- same alert is owed nothing more. NULLS NOT DISTINCT makes a trade event
-- (no alert) unique on its event id alone.
--
-- The destination is stored masked, never the address in full.
-- =====================================================================

CREATE TABLE IF NOT EXISTS notif_notification (
    notification_id UUID          PRIMARY KEY,
    event_id        UUID          NOT NULL,
    alert_id        BIGINT        NULL,
    client_id       INTEGER       NOT NULL REFERENCES client_account (client_id),
    kind            VARCHAR(20)   NOT NULL,
    subject         VARCHAR(200)  NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'QUEUED',
    channel         VARCHAR(10)   NULL,
    destination     VARCHAR(320)  NULL,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    last_error      VARCHAR(500)  NULL,
    next_attempt_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ   NULL,
    read_at         TIMESTAMPTZ   NULL,

    CONSTRAINT uq_notif_source UNIQUE NULLS NOT DISTINCT (event_id, alert_id),
    CONSTRAINT ck_notif_kind
        CHECK (kind IN ('ORDER_FILLED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'PRICE_ALERT')),
    CONSTRAINT ck_notif_status
        CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    CONSTRAINT ck_notif_channel
        CHECK (channel IS NULL OR channel IN ('EMAIL', 'IN_APP')),
    -- Only a price alert names an alert, and every price alert does.
    CONSTRAINT ck_notif_alert
        CHECK ((kind = 'PRICE_ALERT') = (alert_id IS NOT NULL)),
    -- Sent means it went somewhere: the channel is known.
    CONSTRAINT ck_notif_sent_has_channel
        CHECK (status <> 'SENT' OR (channel IS NOT NULL AND sent_at IS NOT NULL))
);

-- The inbox: one account's notifications, newest first.
CREATE INDEX IF NOT EXISTS ix_notif_client_created
    ON notif_notification (client_id, created_at DESC);

-- The dispatcher: what is still waiting, oldest due first.
CREATE INDEX IF NOT EXISTS ix_notif_queued
    ON notif_notification (next_attempt_at) WHERE status = 'QUEUED';
