-- 004_execution_columns.sql
--
-- Sprint 7. Adds the one column the executor writes that the schema did
-- not have. fill_price and resolved_at already exist, added by 001 under
-- our own names rather than the brief's executed_price / executed_on.
--
-- Idempotent, like 001 to 003.

-- The rejection reason. A column rather than a log line: it is the first
-- thing a customer asks about, and the analytics load reads it.
--
-- VARCHAR rather than an enum type, because the event contract types the
-- matching field as a free string so a new reason stays additive.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(40);

-- orders_history stays column-identical: the archival job copies by position.
ALTER TABLE orders_history
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(40);

-- A reason belongs only to an order that did not fill. NULL stays legal
-- for every status.

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

-- Finds orders the executor has not resolved, for the replay path.
-- Partial, because NEW is a small and short-lived minority.

CREATE INDEX IF NOT EXISTS ix_orders_unresolved
    ON orders (date_placed)
    WHERE status = 'NEW';