-- =====================================================================
-- 014_portfolio.sql
--
-- Sprint 10, the portfolio module (com.yellow.trade.portfolio).
--
-- Realised profit and loss, booked at the moment of each sale and never
-- recomputed (contracts/portfolio-api.yaml; decision log 0011): one row per
-- sale, from the ORDER_FILLED event trade-events carried for it, booked only
-- for a sell the platform's own orders table recorded as filled.
--
-- realised = (sale price - average cost at the sale) * quantity sold.
-- Nothing about today's price is here. Positions, cash and prices are read
-- from where they live; this module writes nothing but this table.
-- =====================================================================

CREATE TABLE IF NOT EXISTS pf_realised (
    event_id      UUID          PRIMARY KEY,
    order_id      UUID          NOT NULL UNIQUE,
    client_id     INTEGER       NOT NULL REFERENCES client_account (client_id),
    instrument_id INTEGER       NOT NULL REFERENCES instrument (instrument_id),
    symbol        VARCHAR(30)   NOT NULL,
    quantity      NUMERIC(18,6) NOT NULL,
    sale_price    NUMERIC(18,4) NOT NULL,
    average_cost  NUMERIC(18,4) NOT NULL,
    realised      NUMERIC(18,4) NOT NULL,
    booked_at     TIMESTAMPTZ   NOT NULL,
    recorded_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_pf_realised_quantity CHECK (quantity > 0),
    -- The arithmetic is the contract's, and the row says so.
    CONSTRAINT ck_pf_realised_sum CHECK (realised = round((sale_price - average_cost) * quantity, 4))
);

-- An account's realised figure, over a range of days.
CREATE INDEX IF NOT EXISTS ix_pf_realised_client ON pf_realised (client_id, booked_at);
