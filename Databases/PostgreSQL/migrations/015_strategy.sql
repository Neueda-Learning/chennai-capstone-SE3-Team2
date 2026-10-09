-- =====================================================================
-- 015_strategy.sql
--
-- Sprint 10, the strategy module (com.yellow.trade.strategy), a stretch
-- extension.
--
-- A strategy is a rule a customer sets once: buy a quantity when the price
-- falls to a level, or sell when it rises to one. The platform places the
-- order through POST /api/v1/orders with a token auth mints for the
-- account (decision log 0012). Every firing and every refusal is a run.
--
-- Bounded here as well as in code: a positive quantity, spend and
-- position, and a status that only the firing transaction moves.
-- strat_polled_symbols is the module's surface to the executor's poller:
-- a stock with an armed strategy is priced even if nobody holds it.
-- =====================================================================

CREATE TABLE IF NOT EXISTS strat_strategy (
    strategy_id   BIGSERIAL     PRIMARY KEY,
    client_id     INTEGER       NOT NULL REFERENCES client_account (client_id),
    instrument_id INTEGER       NOT NULL REFERENCES instrument (instrument_id),
    side          VARCHAR(4)    NOT NULL,
    quantity      INTEGER       NOT NULL,
    trigger_kind  VARCHAR(13)   NOT NULL,
    trigger_price NUMERIC(18,4) NOT NULL,
    max_spend     NUMERIC(18,4) NOT NULL,
    max_position  INTEGER       NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT false,
    status        VARCHAR(7)    NOT NULL DEFAULT 'ARMED',
    failures      INTEGER       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_fired_at TIMESTAMPTZ   NULL,

    CONSTRAINT ck_strat_side      CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_strat_trigger   CHECK (trigger_kind IN ('FALLS_THROUGH', 'RISES_THROUGH')),
    CONSTRAINT ck_strat_status    CHECK (status IN ('ARMED', 'FIRED', 'STOPPED')),
    CONSTRAINT ck_strat_positive  CHECK (quantity > 0 AND trigger_price > 0 AND max_spend > 0 AND max_position > 0),
    CONSTRAINT ck_strat_failures  CHECK (failures BETWEEN 0 AND 3)
);

-- The hot path: which armed, enabled strategies a quote for this instrument could fire.
CREATE INDEX IF NOT EXISTS ix_strat_armed
    ON strat_strategy (instrument_id, trigger_kind, trigger_price) WHERE enabled AND status = 'ARMED';

CREATE INDEX IF NOT EXISTS ix_strat_client ON strat_strategy (client_id, created_at DESC);

CREATE TABLE IF NOT EXISTS strat_run (
    run_id          BIGSERIAL     PRIMARY KEY,
    strategy_id     BIGINT        NOT NULL REFERENCES strat_strategy (strategy_id) ON DELETE CASCADE,
    at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    quote_price     NUMERIC(18,4) NULL,
    outcome         VARCHAR(13)   NOT NULL,
    reason          VARCHAR(300)  NULL,
    -- The order a firing placed, on the platform's orders table.
    order_id        UUID          NULL,
    -- The market-data quote or trade-events event this run came from: a
    -- replay of either finds it here and does nothing twice.
    source_event_id UUID          NOT NULL,

    CONSTRAINT uq_strat_run_source UNIQUE (strategy_id, source_event_id),
    CONSTRAINT ck_strat_run_outcome
        CHECK (outcome IN ('PLACED', 'FILLED', 'REJECTED', 'REFUSED_LIMIT', 'FAILED', 'STOPPED'))
);

CREATE INDEX IF NOT EXISTS ix_strat_run_strategy ON strat_run (strategy_id, at DESC);
CREATE INDEX IF NOT EXISTS ix_strat_run_order ON strat_run (order_id) WHERE order_id IS NOT NULL;

-- What the executor's poller adds to its own set: every instrument an
-- enabled, armed strategy is waiting on.
CREATE OR REPLACE VIEW strat_polled_symbols AS
SELECT DISTINCT instrument_id
FROM strat_strategy
WHERE enabled AND status = 'ARMED';
