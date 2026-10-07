-- =====================================================================
-- 013_watchlists.sql
--
-- Sprint 10, the watchlists module (com.yellow.trade.watchlists).
--
-- Watchlists a customer names and fills, price alerts they set, and the
-- latest price market-data has carried for each instrument. Instruments are
-- referenced by key, never copied: a symbol is resolved through the
-- platform's instrument tables.
--
-- watch_polled_symbols is this module's one published surface outside the
-- Trade REST API: the executor's poller reads it, so a stock somebody only
-- watches, or has an alert on, is priced too (decision log 0009).
-- =====================================================================

CREATE TABLE IF NOT EXISTS watch_list (
    watchlist_id BIGSERIAL    PRIMARY KEY,
    client_id    INTEGER      NOT NULL REFERENCES client_account (client_id),
    name         VARCHAR(40)  NOT NULL,
    position     INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_watch_list_name CHECK (length(btrim(name)) > 0)
);

CREATE INDEX IF NOT EXISTS ix_watch_list_client ON watch_list (client_id, position);

CREATE TABLE IF NOT EXISTS watch_item (
    watchlist_id  BIGINT      NOT NULL REFERENCES watch_list (watchlist_id) ON DELETE CASCADE,
    instrument_id INTEGER     NOT NULL REFERENCES instrument (instrument_id),
    position      INTEGER     NOT NULL,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (watchlist_id, instrument_id)
);

CREATE TABLE IF NOT EXISTS watch_alert (
    alert_id        BIGSERIAL     PRIMARY KEY,
    client_id       INTEGER       NOT NULL REFERENCES client_account (client_id),
    instrument_id   INTEGER       NOT NULL REFERENCES instrument (instrument_id),
    direction       VARCHAR(5)    NOT NULL,
    threshold       NUMERIC(18,4) NOT NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    triggered_at    TIMESTAMPTZ   NULL,
    triggered_price NUMERIC(18,4) NULL,
    -- The notification its firing queued, in the notifications module. A
    -- key, not a foreign key: that table is another module's.
    notification_id UUID          NULL,

    CONSTRAINT ck_watch_alert_direction CHECK (direction IN ('ABOVE', 'BELOW')),
    CONSTRAINT ck_watch_alert_status    CHECK (status IN ('ACTIVE', 'TRIGGERED', 'CANCELLED')),
    CONSTRAINT ck_watch_alert_threshold CHECK (threshold > 0),
    -- Triggered means it fired: when, at what price, and what it queued.
    CONSTRAINT ck_watch_alert_triggered
        CHECK (status <> 'TRIGGERED'
               OR (triggered_at IS NOT NULL AND triggered_price IS NOT NULL AND notification_id IS NOT NULL))
);

-- The hot path: every quote asks which ACTIVE alerts on its instrument it
-- crosses. Only active alerts are indexed, so a customer's history of fired
-- and cancelled ones costs the consumer nothing.
CREATE INDEX IF NOT EXISTS ix_watch_alert_active
    ON watch_alert (instrument_id, direction, threshold) WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS ix_watch_alert_client ON watch_alert (client_id, created_at DESC);

-- The latest quote market-data carried for an instrument: one row each,
-- replaced only by a quote observed later than the one it holds.
CREATE TABLE IF NOT EXISTS watch_latest_quote (
    instrument_id  INTEGER       PRIMARY KEY REFERENCES instrument (instrument_id),
    price          NUMERIC(18,4) NOT NULL,
    change_percent NUMERIC(12,4) NULL,
    stale          BOOLEAN       NOT NULL,
    quote_as_of    TIMESTAMPTZ   NOT NULL,
    event_id       UUID          NOT NULL,
    received_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- What the executor's poller adds to its own set: every instrument on a
-- watchlist, and every one with an alert waiting to fire.
CREATE OR REPLACE VIEW watch_polled_symbols AS
SELECT DISTINCT i.instrument_id, COALESCE(e.ticker, m.scheme_code) AS symbol
FROM (SELECT instrument_id FROM watch_item
      UNION
      SELECT instrument_id FROM watch_alert WHERE status = 'ACTIVE') watched
JOIN instrument i       ON i.instrument_id = watched.instrument_id
LEFT JOIN equity e      ON e.instrument_id = i.instrument_id
LEFT JOIN mutual_fund m ON m.instrument_id = i.instrument_id;
