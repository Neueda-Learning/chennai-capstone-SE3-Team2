-- =====================================================================
-- 000_base_schema.sql
-- Enterprise Trading Platform - Base schema (design v6)
-- Target: PostgreSQL
--
-- v6 changes over v5 (all from the story-2 normalisation review):
--   * EXCHANGE and AMC extracted as lookup relations, removing the
--     repeated-string modelling smells in EQUITY and MUTUAL_FUND.
--   * Subtype disjointness enforced with a composite FK, so an
--     instrument can no longer contradict its own type.
--   * fund_transfer.reference_id given a partial unique index.
--
-- Story-3 changes are applied separately in 001_*.sql.
-- Idempotent: safe to run against an existing database.
-- =====================================================================

-- ---------------------------------------------------------------------
-- CLIENT_ACCOUNT : trading identity, KYC state and money balances.
-- Grain: one row per client.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS client_account (
    client_id       INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pan             VARCHAR(10)  NOT NULL UNIQUE,
    demat_id        VARCHAR(16)  NOT NULL UNIQUE,
    kyc_status      VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    balance         NUMERIC(18,4) NOT NULL DEFAULT 0,
    blocked_funds   NUMERIC(18,4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_client_account_kyc_status
        CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_client_account_balance_non_negative
        CHECK (balance >= 0),
    CONSTRAINT ck_client_account_blocked_non_negative
        CHECK (blocked_funds >= 0),
    CONSTRAINT ck_client_account_blocked_within_balance
        CHECK (blocked_funds <= balance)
);

-- ---------------------------------------------------------------------
-- CLIENT_PROFILE : personal / KYC-visible details. 1:1 with account.
--
-- address is deliberately kept as one opaque TEXT column. Splitting it
-- into city / state / pincode would introduce pincode -> city, state,
-- a transitive dependency requiring a ~19,000-row pincode reference
-- table. Not worth it until an address-driven requirement appears.
-- Grain: one row per client.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS client_profile (
    client_id       INTEGER      PRIMARY KEY
                                 REFERENCES client_account (client_id),
    name            VARCHAR(120) NOT NULL,
    dob             DATE         NOT NULL,
    email           VARCHAR(200) NOT NULL UNIQUE,
    phone_number    VARCHAR(20)  NOT NULL,
    address         TEXT
);

-- ---------------------------------------------------------------------
-- CLIENT_AUTH : login credentials. 1:1 with account.
-- Password is stored only as a hash (bcrypt / argon2).
-- Grain: one row per client.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS client_auth (
    client_id       INTEGER      PRIMARY KEY
                                 REFERENCES client_account (client_id),
    password_hash   VARCHAR(255) NOT NULL,
    last_login      TIMESTAMPTZ
);

-- ---------------------------------------------------------------------
-- FUND_TRANSFER : deposits and withdrawals via the payment gateway.
--
-- reference_id is the gateway's own payment identifier. Where present it
-- uniquely identifies a payment, so it is an alternate key - enforced
-- with a PARTIAL unique index, since the column is null until the
-- gateway responds. Without this, two transfers could claim the same
-- gateway payment: the double-credit that idempotency exists to stop,
-- arriving through a different door.
-- Grain: one row per money-movement attempt.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fund_transfer (
    transfer_id     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id       INTEGER      NOT NULL
                                 REFERENCES client_account (client_id),
    amount          NUMERIC(18,4) NOT NULL,
    direction       VARCHAR(10)  NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    reference_id    VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_fund_transfer_direction
        CHECK (direction IN ('DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT ck_fund_transfer_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_fund_transfer_amount_positive
        CHECK (amount > 0)
);

-- ---------------------------------------------------------------------
-- EXCHANGE : trading venue reference data.
--
-- Natural key: the exchange code is the universally used identifier and
-- appears in every UI and every order. A surrogate integer would buy
-- nothing and force a join for display. The set is tiny and stable.
-- Grain: one row per exchange.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exchange (
    exchange_code   VARCHAR(10)  PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    country         CHAR(2)      NOT NULL DEFAULT 'IN',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- AMC : asset management company (mutual fund house).
--
-- Surrogate key here, unlike EXCHANGE: the AMC set grows, names change
-- (mergers, rebrands), and amc_code is an external identifier we do not
-- control. Keeping the PK internal insulates every referencing row from
-- an external code change.
-- Grain: one row per fund house.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS amc (
    amc_id          INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    amc_code        VARCHAR(20)  NOT NULL UNIQUE,
    amc_name        VARCHAR(120) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- INSTRUMENT : supertype for everything tradeable on the platform.
-- Subtypes (equity / mutual_fund) share this primary key.
--
-- The UNIQUE (instrument_id, instrument_type) below is redundant as a
-- uniqueness rule - instrument_id is already the PK - but it exists to
-- be the target of a composite FK from each subtype, which is what
-- makes subtype disjointness enforceable by the database.
-- Grain: one row per tradeable security.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS instrument (
    instrument_id   INTEGER      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_type VARCHAR(10)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    isin            VARCHAR(12)  NOT NULL UNIQUE,

    CONSTRAINT ck_instrument_type
        CHECK (instrument_type IN ('STOCK', 'ETF', 'MF')),
    CONSTRAINT uq_instrument_id_type
        UNIQUE (instrument_id, instrument_type)
);

-- ---------------------------------------------------------------------
-- EQUITY : subtype of instrument for exchange-traded scrips
-- (stocks and ETFs). PK is also FK to the parent.
--
-- instrument_type is carried here purely so the composite FK can pin it:
-- a row in this table forces the parent's type to be STOCK or ETF, which
-- makes it impossible for one instrument to hold both an equity and a
-- mutual_fund row. This is the disjointness guarantee.
-- (Completeness - every instrument having exactly one subtype row -
-- cannot be expressed with FKs and stays an application-layer rule.)
-- Grain: one row per exchange-traded instrument.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS equity (
    instrument_id   INTEGER      PRIMARY KEY
                                 REFERENCES instrument (instrument_id),
    instrument_type VARCHAR(10)  NOT NULL,
    ticker          VARCHAR(30)  NOT NULL,
    exchange_code   VARCHAR(10)  NOT NULL
                                 REFERENCES exchange (exchange_code),
    lot_size        INTEGER      NOT NULL DEFAULT 1,

    CONSTRAINT ck_equity_instrument_type
        CHECK (instrument_type IN ('STOCK', 'ETF')),
    CONSTRAINT fk_equity_instrument_type
        FOREIGN KEY (instrument_id, instrument_type)
        REFERENCES instrument (instrument_id, instrument_type),
    CONSTRAINT ck_equity_lot_size_positive
        CHECK (lot_size > 0),
    CONSTRAINT uq_equity_ticker_exchange
        UNIQUE (ticker, exchange_code)
);

-- ---------------------------------------------------------------------
-- MUTUAL_FUND : subtype of instrument for MF schemes.
-- PK is also FK to the parent. Same composite-FK pattern as EQUITY.
-- Grain: one row per MF scheme.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mutual_fund (
    instrument_id   INTEGER      PRIMARY KEY
                                 REFERENCES instrument (instrument_id),
    instrument_type VARCHAR(10)  NOT NULL,
    scheme_code     VARCHAR(30)  NOT NULL UNIQUE,
    amc_id          INTEGER      NOT NULL
                                 REFERENCES amc (amc_id),
    plan_type       VARCHAR(20)  NOT NULL,
    expense_ratio   NUMERIC(5,4),

    CONSTRAINT ck_mutual_fund_instrument_type
        CHECK (instrument_type = 'MF'),
    CONSTRAINT fk_mutual_fund_instrument_type
        FOREIGN KEY (instrument_id, instrument_type)
        REFERENCES instrument (instrument_id, instrument_type),
    CONSTRAINT ck_mutual_fund_plan_type
        CHECK (plan_type IN ('DIRECT_GROWTH', 'DIRECT_IDCW',
                             'REGULAR_GROWTH', 'REGULAR_IDCW')),
    CONSTRAINT ck_mutual_fund_expense_ratio
        CHECK (expense_ratio IS NULL OR expense_ratio >= 0)
);

-- ---------------------------------------------------------------------
-- ORDERS : live and recent orders.
-- Terminal rows are archived to orders_history by the EOD job.
-- Grain: one row per order placed by a client.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    order_id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id          INTEGER      NOT NULL
                                    REFERENCES client_account (client_id),
    instrument_id      INTEGER      NOT NULL
                                    REFERENCES instrument (instrument_id),
    side               VARCHAR(4)   NOT NULL,
    order_type         VARCHAR(10)  NOT NULL,
    product_type       VARCHAR(3)   NOT NULL,
    price              NUMERIC(18,4),
    quantity           NUMERIC(18,6) NOT NULL,
    filled_quantity    NUMERIC(18,6) NOT NULL DEFAULT 0,
    average_fill_price NUMERIC(18,4),
    status             VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    date_placed        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_updated       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_orders_side
        CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_orders_order_type
        CHECK (order_type IN ('MARKET', 'LIMIT')),
    CONSTRAINT ck_orders_product_type
        CHECK (product_type IN ('MIS', 'CNC')),
    CONSTRAINT ck_orders_status
        CHECK (status IN ('PENDING', 'PARTIAL', 'SUCCESS',
                          'FAILED', 'CANCELLED')),
    CONSTRAINT ck_orders_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT ck_orders_limit_needs_price
        CHECK (order_type <> 'LIMIT' OR price IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- ORDERS_HISTORY : archive of terminal orders.
-- Column-identical to orders, plus archived_at.
-- order_id is carried over, so it stays unique across both tables.
-- Grain: one row per archived order.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders_history (
    order_id           BIGINT       PRIMARY KEY,
    client_id          INTEGER      NOT NULL
                                    REFERENCES client_account (client_id),
    instrument_id      INTEGER      NOT NULL
                                    REFERENCES instrument (instrument_id),
    side               VARCHAR(4)   NOT NULL,
    order_type         VARCHAR(10)  NOT NULL,
    product_type       VARCHAR(3)   NOT NULL,
    price              NUMERIC(18,4),
    quantity           NUMERIC(18,6) NOT NULL,
    filled_quantity    NUMERIC(18,6) NOT NULL DEFAULT 0,
    average_fill_price NUMERIC(18,4),
    status             VARCHAR(10)  NOT NULL,
    date_placed        TIMESTAMPTZ  NOT NULL,
    last_updated       TIMESTAMPTZ  NOT NULL,
    archived_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_orders_history_side
        CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_orders_history_order_type
        CHECK (order_type IN ('MARKET', 'LIMIT')),
    CONSTRAINT ck_orders_history_product_type
        CHECK (product_type IN ('MIS', 'CNC')),
    CONSTRAINT ck_orders_history_status
        CHECK (status IN ('SUCCESS', 'FAILED', 'CANCELLED'))
);

-- ---------------------------------------------------------------------
-- POSITION : current holdings.
-- INTRADAY rows feed the Positions tab, DELIVERY rows the Holdings tab.
-- Deliberately has no FK to orders: a holding outlives the (archived)
-- order that created it.
-- Grain: one row per client per instrument per position type.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS position (
    position_id     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id       INTEGER      NOT NULL
                                 REFERENCES client_account (client_id),
    instrument_id   INTEGER      NOT NULL
                                 REFERENCES instrument (instrument_id),
    position_type   VARCHAR(10)  NOT NULL,
    quantity        NUMERIC(18,6) NOT NULL,
    average_price   NUMERIC(18,4) NOT NULL,

    CONSTRAINT ck_position_type
        CHECK (position_type IN ('INTRADAY', 'DELIVERY')),
    CONSTRAINT ck_position_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT ck_position_average_price_positive
        CHECK (average_price > 0),
    CONSTRAINT uq_position_client_instrument_type
        UNIQUE (client_id, instrument_id, position_type)
);


-- ---------------------------------------------------------------------
-- Correctness constraint, not a performance index.
--
-- The payment gateway's own reference uniquely identifies a payment
-- where present, so two transfers must never claim the same one -
-- that is the double-credit idempotency exists to prevent, arriving
-- through a different door.
--
-- Partial, because the column is null until the gateway responds.
-- Lives here rather than in indexes/ because dropping it changes
-- behaviour; the files in indexes/ can be dropped freely.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_fund_transfer_reference_id
    ON fund_transfer (reference_id)
    WHERE reference_id IS NOT NULL;
