CREATE TABLE orders (
    order_id BIGSERIAL PRIMARY KEY,

    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,

    idempotency_key VARCHAR(100) NOT NULL UNIQUE,

    side VARCHAR(4) NOT NULL,
    order_type VARCHAR(6) NOT NULL,

    quantity INTEGER NOT NULL,
    price NUMERIC(19,4),

    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_account
        FOREIGN KEY (account_id)
        REFERENCES account(account_id),

    CONSTRAINT fk_order_instrument
        FOREIGN KEY (instrument_id)
        REFERENCES instrument(instrument_id),

    CONSTRAINT chk_order_side
        CHECK (side IN ('BUY', 'SELL')),

    CONSTRAINT chk_order_type
        CHECK (order_type IN ('MARKET', 'LIMIT')),

    CONSTRAINT chk_order_status
        CHECK (
            status IN (
                'RECEIVED',
                'EXECUTED',
                'CANCELLED',
                'REJECTED'
            )
        ),

    CONSTRAINT chk_order_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_order_price
        CHECK (
            (order_type = 'MARKET' AND price IS NULL)
            OR
            (order_type = 'LIMIT' AND price > 0)
        )
);

CREATE INDEX idx_orders_account_date
    ON orders(account_id, received_at DESC);

CREATE INDEX idx_orders_instrument
    ON orders(instrument_id);