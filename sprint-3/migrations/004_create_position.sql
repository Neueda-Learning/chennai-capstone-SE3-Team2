CREATE TABLE position (
    position_id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    quantity INTEGER NOT NULL DEFAULT 0,
    average_price NUMERIC(19,4) NOT NULL DEFAULT 0,

    CONSTRAINT fk_position_account
        FOREIGN KEY (account_id)
        REFERENCES account(account_id),

    CONSTRAINT fk_position_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id),

    CONSTRAINT uq_position_account_instrument
        UNIQUE (account_id, instrument_id),

    CONSTRAINT chk_position_quantity
        CHECK (quantity >= 0),

    CONSTRAINT chk_position_average_price
        CHECK (average_price >= 0)
);