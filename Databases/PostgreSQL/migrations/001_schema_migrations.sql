-- =====================================================================
-- 001_schema_migrations.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- status gates behaviour:
--  ACTIVE, SUSPENDED, CLOSED 
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

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

ALTER TABLE orders_history
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

ALTER TABLE fund_transfer
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_client_idempotency_key
    ON orders (client_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_history_client_idempotency_key
    ON orders_history (client_id, idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fund_transfer_client_idempotency_key
    ON fund_transfer (client_id, idempotency_key);
