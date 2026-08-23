INSERT INTO orders
    (account_id, instrument_id, idempotency_key,
     side, order_type, quantity, price, status)
VALUES
    (999999, 1, 'probe-orphan-001',
     'BUY', 'LIMIT', 10, 100.00, 'RECEIVED');