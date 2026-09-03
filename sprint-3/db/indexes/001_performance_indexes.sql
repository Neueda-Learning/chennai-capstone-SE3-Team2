-- =====================================================================
-- 002_performance_indexes.sql
-- =====================================================================

-- Order book / "my orders" screen: a client's orders, newest first.
CREATE INDEX IF NOT EXISTS ix_orders_client_placed
    ON orders (client_id, date_placed DESC);

-- The black box poller and the EOD archival job both scan by status.
CREATE INDEX IF NOT EXISTS ix_orders_status
    ON orders (status);

-- Past-orders report, same access pattern as the live screen.
CREATE INDEX IF NOT EXISTS ix_orders_history_client_placed
    ON orders_history (client_id, date_placed DESC);

-- Portfolio screen: every position for one client.
CREATE INDEX IF NOT EXISTS ix_position_client
    ON position (client_id);

-- Funds / statement screen: a client's transfers, newest first.
CREATE INDEX IF NOT EXISTS ix_fund_transfer_client_created
    ON fund_transfer (client_id, created_at DESC);


-- Instrument search excludes delisted scrips. Partial index: only
-- tradable rows are indexed, so it stays small as delistings accumulate.
CREATE INDEX IF NOT EXISTS ix_instrument_tradable
    ON instrument (is_tradable)
    WHERE is_tradable;
