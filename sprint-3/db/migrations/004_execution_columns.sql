-- =====================================================================
-- 004_execution_columns.sql
--
-- Sprint 7. The column the Trade Executor writes that the Sprint 3
-- schema does not have yet: why an order was rejected.
--
-- WHAT WAS ALREADY HERE. readme_7.md asks for three columns -- the
-- price a fill happened at, when it happened, and why an order was
-- rejected. Two of the three already exist under our own names, added
-- by 001:
--
--     orders.fill_price    NUMERIC(18,4)   the brief calls it executed_price
--     orders.resolved_at   TIMESTAMPTZ     the brief calls it executed_on
--
-- They are not renamed. The names are load-bearing in the MyBatis
-- result maps, the Sprint 6 service and the characterisation tests, and
-- a rename would move all three for no behavioural gain. What the brief
-- fixes is that the executor has somewhere to record the executed price
-- and the time, not what the columns are called.
--
-- So this migration adds one column, and the constraint that says when
-- it is allowed to be populated.
--
-- Idempotent, in the style of 001 to 003: a re-run on an
-- already-migrated database is a no-op rather than an error.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. The rejection reason.
--
-- WHY IT IS A COLUMN AND NOT A LOG LINE. An order that was rejected is
-- a thing a customer phones up about, and "why" is the first question.
-- A log line is retained for days, is not joinable, and is gone from
-- the analytics estate entirely -- and Sprint 7's star schema loads
-- rejected orders precisely because fill rate cannot be computed from
-- fills alone. The reason has to travel with the row.
--
-- WHY VARCHAR(40) AND NOT AN ENUM TYPE. The values are the platform's
-- reason vocabulary: INSUFFICIENT_FUNDS, INSUFFICIENT_HOLDINGS,
-- PRICE_NOT_MET, INSTRUMENT_NOT_TRADABLE, INSTRUMENT_NOT_PRICEABLE,
-- ACCOUNT_NOT_ACTIVE, NO_PRICE, CANCELLED_BY_CUSTOMER. The longest is
-- 24 characters, so 40 leaves room without inviting prose.
--
-- contracts/kafka-topics.md types the matching event field as "string
-- or null" with examples rather than a closed enum, deliberately, so
-- that a new reason is an additive change rather than a migration on
-- every consumer. A CHECK constraint enumerating the values here would
-- undo that: adding a reason would then need a schema change, and a
-- consumer would receive a value the producer's own database refused to
-- store. The vocabulary is agreed in code and in design/kafka.md, which
-- is where an additive list belongs.
-- ---------------------------------------------------------------------

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(40);

-- ORDERS_HISTORY must stay column-identical to ORDERS, because the
-- archival job in DESIGN.md copies rows between them by position. 001
-- and 002 both moved the two tables together for the same reason.
ALTER TABLE orders_history
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(40);


-- ---------------------------------------------------------------------
-- 2. A reason belongs only to an order that did not fill.
--
-- A FILLED order carrying "INSUFFICIENT_FUNDS" is a contradiction the
-- database can refuse outright, and a NEW order carrying any reason at
-- all means something wrote a verdict without writing the status --
-- exactly the half-applied update the executor's single transaction
-- exists to prevent.
--
-- NULL stays legal for every status: a cancelled order need not say
-- why, and the Sprint 6 cancel path does not set one.
-- ---------------------------------------------------------------------

ALTER TABLE orders
DROP CONSTRAINT IF EXISTS ck_orders_reason_matches_status;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_reason_matches_status
        CHECK (rejection_reason IS NULL
            OR status IN ('REJECTED', 'CANCELLED'));

ALTER TABLE orders_history
DROP CONSTRAINT IF EXISTS ck_orders_history_reason_matches_status;

ALTER TABLE orders_history
    ADD CONSTRAINT ck_orders_history_reason_matches_status
        CHECK (rejection_reason IS NULL
            OR status IN ('REJECTED', 'CANCELLED'));


-- ---------------------------------------------------------------------
-- 3. Finding the orders the executor is yet to resolve.
--
-- The recovery path the event contract names: an order that committed
-- and was never published can be replayed from the order table. That
-- query is "every order still NEW, oldest first", and without an index
-- it is a sequential scan over a table that grows by every order ever
-- placed.
--
-- Partial, because NEW is the small minority -- orders reach a terminal
-- status within seconds and are archived after seven days -- so the
-- index stays tiny and is not touched when a terminal row is written.
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS ix_orders_unresolved
    ON orders (date_placed)
    WHERE status = 'NEW';