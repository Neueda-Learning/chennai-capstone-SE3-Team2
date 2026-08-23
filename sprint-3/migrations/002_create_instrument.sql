CREATE TABLE instrument (
    instrument_id BIGSERIAL PRIMARY KEY,

    ticker VARCHAR(20) NOT NULL,
    isin VARCHAR(12) NOT NULL UNIQUE,
    trading_status VARCHAR(20) NOT NULL DEFAULT 'TRADING',

    CONSTRAINT chk_instrument_trading_status
        CHECK (trading_status IN ('TRADING', 'STOPPED')),

    CONSTRAINT chk_instrument_isin_length
        CHECK (LENGTH(isin) = 12)
);