-- =====================================================================
-- The analytics star schema, in DuckDB (Databases/DuckDB/analytics/).
--
-- One fact table, fact_trades (one row per order the extract loads), and
-- three dimensions: dim_account (type 2: a new row when an account's status
-- changes), dim_instrument and dim_date. etl_metadata holds the extract's
-- watermark.
--
-- The ETL (ETL Layer/src/etl/db.py) creates exactly this on its first run.
-- Deviation: fact_trades.quantity is DECIMAL(18,6), not INTEGER, because
-- mutual-fund quantities are fractional (Contracts/API Schemas/DEVIATIONS.md).
-- =====================================================================

CREATE TABLE IF NOT EXISTS etl_metadata (
    key   VARCHAR NOT NULL,
    value VARCHAR NOT NULL,
    CONSTRAINT pk_etl_metadata PRIMARY KEY (key)
);

CREATE TABLE IF NOT EXISTS dim_date (
    date_key    INTEGER    NOT NULL,
    full_date   DATE       NOT NULL,
    day         INTEGER    NOT NULL,
    month       INTEGER    NOT NULL,
    year        INTEGER    NOT NULL,
    quarter     INTEGER    NOT NULL,
    day_of_week INTEGER    NOT NULL,
    day_name    VARCHAR(9) NOT NULL,
    month_name  VARCHAR(9) NOT NULL,
    is_weekday  BOOLEAN    NOT NULL,
    CONSTRAINT pk_dim_date PRIMARY KEY (date_key),
    CONSTRAINT uq_dim_date_full_date UNIQUE (full_date)
);

CREATE TABLE IF NOT EXISTS dim_instrument (
    instrument_key BIGINT       NOT NULL,
    symbol         VARCHAR(20)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    asset_class    VARCHAR(20)  NOT NULL,
    currency       CHAR(3)      NOT NULL,
    exchange       VARCHAR(20),
    tradable       BOOLEAN      NOT NULL,
    loaded_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_dim_instrument PRIMARY KEY (instrument_key),
    CONSTRAINT uq_dim_instrument_symbol UNIQUE (symbol)
);

CREATE TABLE IF NOT EXISTS dim_account (
    account_key    BIGINT       NOT NULL,
    account_id     VARCHAR(32)  NOT NULL,
    holder_name    VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    effective_date DATE         NOT NULL,
    end_date       DATE,
    is_current     BOOLEAN      NOT NULL,
    source_id      BIGINT       NOT NULL,
    loaded_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_dim_account PRIMARY KEY (account_key)
);

CREATE TABLE IF NOT EXISTS fact_trades (
    trade_key       BIGINT        NOT NULL,
    account_key     BIGINT        NOT NULL,
    instrument_key  BIGINT        NOT NULL,
    date_key        INTEGER       NOT NULL,
    side            VARCHAR(4)    NOT NULL,
    quantity        DECIMAL(18,6) NOT NULL,
    price           DECIMAL(18,2) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    executed_price  DECIMAL(18,2),
    trade_value     DECIMAL(18,2) NOT NULL,
    source_order_id VARCHAR(36)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    loaded_at       TIMESTAMP     NOT NULL,
    CONSTRAINT pk_fact_trades PRIMARY KEY (trade_key),
    CONSTRAINT uq_fact_trades_source UNIQUE (source_order_id),
    CONSTRAINT fk_fact_trades_account
        FOREIGN KEY (account_key)    REFERENCES dim_account (account_key),
    CONSTRAINT fk_fact_trades_instrument
        FOREIGN KEY (instrument_key) REFERENCES dim_instrument (instrument_key),
    CONSTRAINT fk_fact_trades_date
        FOREIGN KEY (date_key)       REFERENCES dim_date (date_key)
);
