-- =====================================================================
-- 002_api_alignment.sql
--
-- Sprint 6. Aligns the schema with contracts/trade-api.yaml, which the
-- Trade REST API implements and from which Sprint 9 generates its
-- Angular client.
--
-- Four changes, each with a reason:
--
--   1. orders.order_id and orders_history.order_id become UUID.
--   2. Order status literals become the contract's.
--   3. order_type and product_type get defaults.
--   4. client_account gains the string business reference the contract
--      calls AccountResponse.accountId.
--
-- Idempotent where it can be, so a re-run on an already-migrated
-- database is a no-op rather than an error.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Order identity: BIGINT identity -> UUID.
--
-- WHY. The domain generates an order's identity in Order.place(),
-- before any database round-trip, which is what lets the rules be
-- tested with plain objects and lets a retry carry the identity it was
-- given. An identity column can only answer AFTER the insert, so the
-- domain would have to hold an order with no id until the row came
-- back, and the Trade Executor in Sprint 7 would have no id to put on
-- a Kafka event until the same round-trip had happened.
--
-- COST, stated so it can be argued with: 16 bytes per row instead of 8,
-- which at the year-7 volume in DESIGN.md (87.5M archived orders) is
-- roughly 0.7 GB more across table and index, and the loss of a
-- chronologically ordered primary key. The second matters less than it
-- looks: every query that wants recent orders goes through
-- ix_orders_client_placed on date_placed, not through the PK.
--
-- Both tables must move together. DESIGN.md requires orders_history to
-- stay column-identical to orders, because the archival job copies rows
-- between them by position.
-- ---------------------------------------------------------------------

-- gen_random_uuid() is built in from PostgreSQL 13. The extension is a
-- no-op there and the fallback for anything older.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders'
          AND column_name = 'order_id'
          AND data_type <> 'uuid'
    ) THEN
        -- Existing rows keep a row, not their number: the old BIGINT is
        -- discarded and a fresh UUID minted. Nothing outside these two
        -- tables references order_id -- position deliberately has no FK
        -- to orders, because a holding outlives the archived order that
        -- created it -- so no foreign key has to be rewritten.
        ALTER TABLE orders ADD COLUMN order_uuid UUID NOT NULL DEFAULT gen_random_uuid();
        ALTER TABLE orders DROP CONSTRAINT orders_pkey;
        ALTER TABLE orders DROP COLUMN order_id;   -- drops the identity sequence with it
        ALTER TABLE orders RENAME COLUMN order_uuid TO order_id;
        ALTER TABLE orders ALTER COLUMN order_id DROP DEFAULT;
        ALTER TABLE orders ADD CONSTRAINT orders_pkey PRIMARY KEY (order_id);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'orders_history'
          AND column_name = 'order_id'
          AND data_type <> 'uuid'
    ) THEN
        ALTER TABLE orders_history ADD COLUMN order_uuid UUID NOT NULL DEFAULT gen_random_uuid();
        ALTER TABLE orders_history DROP CONSTRAINT orders_history_pkey;
        ALTER TABLE orders_history DROP COLUMN order_id;
        ALTER TABLE orders_history RENAME COLUMN order_uuid TO order_id;
        ALTER TABLE orders_history ALTER COLUMN order_id DROP DEFAULT;
        ALTER TABLE orders_history ADD CONSTRAINT orders_history_pkey PRIMARY KEY (order_id);
    END IF;
END $$;

-- The application supplies the id on every insert. A default here would
-- silently paper over a bug in which it forgot to.


-- ---------------------------------------------------------------------
-- 2. Order status literals.
--
-- WHY. AccountStatus, OrderSide and OrderStatus are fixed by the
-- contract, the database stores the same strings, and Sprint 9
-- generates its TypeScript from that file. The base schema predates the
-- contract and used the black box's vocabulary. One of the two had to
-- move, and the enum appears in three places against the schema's one.
--
--   PENDING   -> NEW        the working state
--   SUCCESS   -> FILLED     there is no partial fill; an order fills in
--                           full or is rejected
--   FAILED    -> REJECTED
--   CANCELLED -> CANCELLED  unchanged, and spelled with two Ls
--
-- Existing rows are translated before the constraint is tightened,
-- otherwise the ALTER fails on its own data.
-- ---------------------------------------------------------------------

ALTER TABLE orders           DROP CONSTRAINT IF EXISTS ck_orders_status;
ALTER TABLE orders_history   DROP CONSTRAINT IF EXISTS ck_orders_history_status;
ALTER TABLE orders           DROP CONSTRAINT IF EXISTS ck_orders_resolved_at_matches_status;
ALTER TABLE orders           DROP CONSTRAINT IF EXISTS ck_orders_success_needs_fill_price;

UPDATE orders SET status = CASE status
    WHEN 'PENDING' THEN 'NEW'
    WHEN 'SUCCESS' THEN 'FILLED'
    WHEN 'FAILED'  THEN 'REJECTED'
    ELSE status
END WHERE status IN ('PENDING', 'SUCCESS', 'FAILED');

UPDATE orders_history SET status = CASE status
    WHEN 'SUCCESS' THEN 'FILLED'
    WHEN 'FAILED'  THEN 'REJECTED'
    ELSE status
END WHERE status IN ('SUCCESS', 'FAILED');

ALTER TABLE orders
    ALTER COLUMN status SET DEFAULT 'NEW';

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_status
        CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED'));

-- History holds terminal rows only. NEW is absent on purpose: an order
-- still working has not finished and must not be archived.
ALTER TABLE orders_history
    ADD CONSTRAINT ck_orders_history_status
        CHECK (status IN ('FILLED', 'REJECTED', 'CANCELLED'));

-- A resolved order carries its resolution timestamp; a working one must
-- not. Same rule as 001, restated over the new literal.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_resolved_at_matches_status
        CHECK (
            (status =  'NEW' AND resolved_at IS NULL)
         OR (status <> 'NEW' AND resolved_at IS NOT NULL)
        );

-- A filled order carries the price it filled at. A rejected or
-- cancelled one never does, which is why this is an implication and not
-- a NOT NULL.
ALTER TABLE orders
    ADD CONSTRAINT ck_orders_fill_price_matches_status
        CHECK (status <> 'FILLED' OR fill_price IS NOT NULL);

ALTER TABLE orders_history
    DROP CONSTRAINT IF EXISTS ck_orders_history_fill_price_matches_status;

ALTER TABLE orders_history
    ADD CONSTRAINT ck_orders_history_fill_price_matches_status
        CHECK (status <> 'FILLED' OR fill_price IS NOT NULL);


-- ---------------------------------------------------------------------
-- 3. Defaults for order_type and product_type.
--
-- WHY. Both are NOT NULL and neither has a field in PlaceOrderRequest,
-- whose six fields are fixed by the contract. Without a default no
-- order the API accepts can be inserted at all.
--
-- MARKET is the default because of what the customer actually does: on
-- the Sprint 9 screen, buying is one click. Requiring a limit price
-- before an order can be placed would make the common path the harder
-- one. The price the customer submits travels with the order as a
-- ceiling on a buy and a floor on a sell -- ck_orders_limit_needs_price
-- constrains LIMIT orders to carry one and says nothing about MARKET
-- orders that do.
--
-- CNC (delivery) is the default because an intraday position is squared
-- off the same evening by a job that does not exist yet. Every position
-- this service creates is therefore DELIVERY, and the Positions tab
-- stays empty until MIS is offered.
--
-- Both are defaults rather than dropped columns: the Trade Executor and
-- the Sprint 10 extensions will set them explicitly, and the black box
-- already distinguishes them.
-- ---------------------------------------------------------------------

ALTER TABLE orders
    ALTER COLUMN order_type   SET DEFAULT 'MARKET',
    ALTER COLUMN product_type SET DEFAULT 'CNC';


-- ---------------------------------------------------------------------
-- 4. The account business reference.
--
-- WHY. The contract's AccountResponse.accountId is the one place in the
-- platform where that name means a string rather than the numeric key.
-- It is what a customer reads off a statement and quotes to support,
-- and it is deliberately not the primary key: a support call should not
-- be able to guess the next account by adding one, and the surrogate
-- key should be free to change representation without a customer ever
-- seeing it.
--
-- Backfilled deterministically from client_id for the existing rows so
-- the seed data stays legible in a review. A real onboarding flow would
-- mint these from a non-sequential source.
-- ---------------------------------------------------------------------

ALTER TABLE client_account
    ADD COLUMN IF NOT EXISTS account_ref VARCHAR(20);

UPDATE client_account
    SET account_ref = 'ACC-' || lpad(client_id::text, 6, '0')
    WHERE account_ref IS NULL;

ALTER TABLE client_account
    ALTER COLUMN account_ref SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_client_account_ref
    ON client_account (account_ref);
