-- =====================================================================
-- named_queries.sql
--
-- The six queries the application actually runs, named Q1-Q6.
-- Each maps to exactly one index in indexes/001_performance_indexes.sql;
-- see index_justification.md for the measured before/after evidence.
--
-- :client is a psql variable. Run with:
--   psql -v client=42 -f queries/named_queries.sql
-- =====================================================================

\if :{?client}
\else
    \set client 42
\endif


-- ---------------------------------------------------------------------
-- Q1  ORDER BOOK
-- Screen: "Orders" tab. The most-hit authenticated query in the app.
-- Returns one client's most recent orders, newest first.
-- Index: ix_orders_client_placed (client_id, date_placed DESC)
-- ---------------------------------------------------------------------
SELECT o.order_id,
       i.name          AS instrument,
       o.side,
       o.order_type,
       o.product_type,
       o.quantity,
       o.price,
       o.fill_price,
       o.status,
       o.date_placed
FROM orders o
JOIN instrument i ON i.instrument_id = o.instrument_id
WHERE o.client_id = :client
ORDER BY o.date_placed DESC
LIMIT 20;


-- ---------------------------------------------------------------------
-- Q2  PENDING ORDER SWEEP
-- Job: the poller that asks the execution black box to resolve orders
-- still sitting in NEW. Runs every few seconds, platform-wide.
-- Index: ix_orders_status (status)
-- ---------------------------------------------------------------------
SELECT order_id,
       client_id,
       instrument_id,
       side,
       quantity,
       date_placed,
       now() - date_placed AS age
FROM orders
WHERE status = 'NEW'
ORDER BY date_placed
LIMIT 100;


-- ---------------------------------------------------------------------
-- Q3  PORTFOLIO LOAD
-- Screen: Holdings and Positions tabs. Runs on every app open.
-- Current value and P&L need the live price from the black box, so only
-- invested value is computable here.
-- Index: ix_position_client (client_id)
-- ---------------------------------------------------------------------
SELECT p.position_type,
       i.name          AS instrument,
       i.instrument_type,
       i.is_tradable,
       p.quantity,
       p.average_price,
       round(p.quantity * p.average_price, 2) AS invested_value
FROM position p
JOIN instrument i ON i.instrument_id = p.instrument_id
WHERE p.client_id = :client
ORDER BY invested_value DESC;


-- ---------------------------------------------------------------------
-- Q4  FUNDS STATEMENT
-- Screen: "Funds" tab. One client's deposits and withdrawals, newest
-- first.
-- Index: ix_fund_transfer_client_created (client_id, created_at DESC)
-- ---------------------------------------------------------------------
SELECT transfer_id,
       amount,
       direction,
       status,
       reference_id,
       created_at
FROM fund_transfer
WHERE client_id = :client
ORDER BY created_at DESC
LIMIT 25;


-- ---------------------------------------------------------------------
-- Q5  INSTRUMENT SEARCH
-- Screen: the search bar. Only tradable instruments may be ordered, so
-- delisted scrips are excluded here even though clients may still hold
-- them (see Q3, which does not filter).
-- Index: ix_instrument_tradable (is_tradable) WHERE is_tradable
-- ---------------------------------------------------------------------
SELECT i.instrument_id,
       i.name,
       i.instrument_type,
       i.isin,
       e.ticker,
       e.exchange_code
FROM instrument i
LEFT JOIN equity e ON e.instrument_id = i.instrument_id
WHERE i.is_tradable
ORDER BY i.name
LIMIT 25;


-- ---------------------------------------------------------------------
-- Q6  ARCHIVED ORDER HISTORY
-- Screen: "Reports > Past orders". Reads the archive rather than the
-- live table, which is the entire point of the hot/cold split.
-- Index: ix_orders_history_client_placed (client_id, date_placed DESC)
-- ---------------------------------------------------------------------
SELECT h.order_id,
       i.name          AS instrument,
       h.side,
       h.quantity,
       h.fill_price,
       h.status,
       h.date_placed,
       h.archived_at
FROM orders_history h
JOIN instrument i ON i.instrument_id = h.instrument_id
WHERE h.client_id = :client
ORDER BY h.date_placed DESC
LIMIT 20;
