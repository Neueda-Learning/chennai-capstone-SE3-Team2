-- =====================================================================
-- 001_schema_migrations.sql
-- Sprint 1 / Story 3 - first schema migration
--
-- Applies on top of 000_base_schema.sql:
--   1. Account lifecycle states (ACTIVE / SUSPENDED / CLOSED)
--   2. Optimistic-locking version column on the account
--   3. Tradability flag for delisted / suspended instruments
--   4. Removal of the PARTIAL fill concept (black box is all-or-nothing)
--   5. Idempotency keys on orders and fund transfers
--
-- The whole file runs in one transaction: a failure leaves the schema
-- untouched rather than half-migrated.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1 + 2. Account lifecycle state and optimistic-locking version.
--
-- status gates behaviour:
--   ACTIVE    - all actions permitted
--   SUSPENDED - no new BUY orders; SELL of existing holdings/positions,
--               withdrawals and read access remain permitted
--   CLOSED    - no actions; an account may only be closed once it holds
--               no open positions and no blocked funds (enforced by the
--               close-account flow, not by this constraint)
--
-- version is incremented on every write to the row, so read-modify-write
-- flows can detect a concurrent change via the affected-row count.
-- ---------------------------------------------------------------------
ALTER TABLE client_account
    ADD COLUMN IF NOT EXISTS status  VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS version INTEGER     NOT NULL DEFAULT 0;

ALTER TABLE client_account
    DROP CONSTRAINT IF EXISTS ck_client_account_status;

ALTER TABLE client_account
    ADD CONSTRAINT ck_client_account_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));

ALTER TABLE client_account
    DROP CONSTRAINT IF EXISTS ck_client_account_version_non_negative;

ALTER TABLE client_account
    ADD CONSTRAINT ck_client_account_version_non_negative
        CHECK (version >= 0);

-- ---------------------------------------------------------------------
-- 3. Tradability flag on the instrument supertype only.
--
-- is_tradable = FALSE means: hidden from search, no new orders accepted.
-- Existing positions in the instrument remain valid and visible, so a
-- client holding a delisted scrip still sees it in Holdings.
-- ---------------------------------------------------------------------
ALTER TABLE instrument
    ADD COLUMN IF NOT EXISTS is_tradable BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------------------------------------------------------------------
-- 4. Remove the PARTIAL fill concept from ORDERS.
--
-- The execution black box is all-or-nothing: an order either fills in
-- full or fails. filled_quantity therefore carries no information
-- (it always equals 0 or quantity) and is dropped.
-- average_fill_price is kept but renamed fill_price - "average" is
-- meaningless with a single fill, but the executed price still differs
-- from the requested price for MARKET orders and for LIMIT orders that
-- fill better than the limit.
-- last_updated is renamed resolved_at: with one transition per order,
-- it records when the black box resolved it. Kept (not dropped) because
-- it is the column that makes incremental extraction possible - a row
-- placed today and resolved tomorrow would be missed by any job that
-- keys off date_placed alone.
--
-- ORDERING NOTE: the CHECK constraint on status is dropped before the
-- new one is added, and column renames are done before any constraint
-- that references the new names.
-- ---------------------------------------------------------------------
ALTER TABLE orders
    DROP COLUMN IF EXISTS filled_quantity;

ALTER TABLE orders
    RENAME COLUMN average_fill_price TO fill_price;

ALTER TABLE orders
    RENAME COLUMN last_updated TO resolved_at;

ALTER TABLE orders
    ALTER COLUMN resolved_at DROP NOT NULL,
    ALTER COLUMN resolved_at DROP DEFAULT;

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS ck_orders_status;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED'));

-- A resolved order must carry its resolution timestamp; a pending one
-- must not. A successful order must carry the price it filled at.
ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS ck_orders_resolved_at_matches_status;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_resolved_at_matches_status
        CHECK (
            (status = 'PENDING' AND resolved_at IS NULL)
            OR (status <> 'PENDING' AND resolved_at IS NOT NULL)
        );

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS ck_orders_success_needs_fill_price;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_success_needs_fill_price
        CHECK (status <> 'SUCCESS' OR fill_price IS NOT NULL);

-- ORDERS_HISTORY must stay column-identical to ORDERS, otherwise the
-- archival job breaks.
ALTER TABLE orders_history
    DROP COLUMN IF EXISTS filled_quantity;

ALTER TABLE orders_history
    RENAME COLUMN average_fill_price TO fill_price;

ALTER TABLE orders_history
    RENAME COLUMN last_updated TO resolved_at;

-- ---------------------------------------------------------------------
-- 5. Idempotency keys.
--
-- The key is generated by the caller, not the server: only the caller
-- can distinguish "the same intent, retried after a lost response" from
-- "a genuine second request". The frontend sends a fresh UUID per user
-- action and reuses it on retry; backend jobs (e.g. EOD square-off)
-- derive a deterministic key from the intent, so a rerun produces the
-- same key and is rejected the same way.
--
-- Uniqueness is scoped per client so that one client's key space can
-- never collide with another's. Duplicate detection happens in the
-- database via this constraint - never via a SELECT-then-INSERT in
-- application code, which is not race-proof.
--
-- The column is nullable: in PostgreSQL, NULLs are never equal, so a
-- unique index permits any number of NULL keys. Callers that omit a key
-- simply forgo retry protection.
-- ---------------------------------------------------------------------
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

-- ORDERS_HISTORY carries the column to stay structurally identical, and
-- gets its own unique index so archived keys cannot collide with each
-- other.
--
-- KNOWN LIMITATION, recorded deliberately: this does NOT make idempotency
-- permanent. The unique index on ORDERS only sees live rows, so once an
-- order is archived its key leaves that index, and a replayed request
-- carrying that key would be accepted as new. Retry protection is
-- therefore bounded by the archival window (days), while real retries
-- occur within seconds - so the exposure is theoretical. If the
-- requirement ever tightens, the fix is a dedicated long-lived
-- idempotency registry keyed on (client_id, key) that outlives archival,
-- not a wider index here.
ALTER TABLE orders_history
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

ALTER TABLE fund_transfer
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

-- The unique indexes below are correctness constraints, not performance
-- tuning: they are the only race-proof duplicate rejection in the
-- system. Without them a retried request creates a second order or a
-- second deposit. They therefore live with the schema change that
-- introduced the column, NOT in indexes/, whose contents can be dropped
-- and recreated freely.
--
-- Scoped per client so one client's key space can never collide with
-- another's. Nullable: in PostgreSQL NULLs are never equal, so the index
-- permits any number of them, letting callers that omit a key simply
-- forgo retry protection.
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_client_idempotency_key
    ON orders (client_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_history_client_idempotency_key
    ON orders_history (client_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fund_transfer_client_idempotency_key
    ON fund_transfer (client_id, idempotency_key);
