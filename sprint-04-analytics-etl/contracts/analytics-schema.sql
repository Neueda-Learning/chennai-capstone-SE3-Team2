CREATE TABLE IF NOT EXISTS DIM_ACCOUNT
(
    account_key INTEGER PRIMARY KEY,
    account_id VARCHAR,
    account_type VARCHAR
);


CREATE TABLE IF NOT EXISTS DIM_INSTRUMENT
(
    instrument_key INTEGER PRIMARY KEY,
    symbol VARCHAR,
    currency VARCHAR
);


CREATE TABLE IF NOT EXISTS DIM_DATE
(
    date_key INTEGER PRIMARY KEY,
    full_date DATE,
    year INTEGER,
    month INTEGER,
    day INTEGER
);


CREATE TABLE IF NOT EXISTS FACT_TRADES
(
    trade_key INTEGER PRIMARY KEY,

    account_key INTEGER,
    instrument_key INTEGER,
    date_key INTEGER,

    open_price DOUBLE,
    high_price DOUBLE,
    low_price DOUBLE,
    close_price DOUBLE,
    adj_close DOUBLE,

    volume BIGINT,

    daily_return DOUBLE,
    daily_range DOUBLE,
    daily_range_pct DOUBLE,
    turnover DOUBLE,

    FOREIGN KEY(account_key)
        REFERENCES DIM_ACCOUNT(account_key),

    FOREIGN KEY(instrument_key)
        REFERENCES DIM_INSTRUMENT(instrument_key),

    FOREIGN KEY(date_key)
        REFERENCES DIM_DATE(date_key)
);