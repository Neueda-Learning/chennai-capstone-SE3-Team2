INSERT INTO orders
    (account_id, instrument_id, idempotency_key,
     side, order_type, quantity, price, status)
VALUES
    (1, 1, 'probe-idempotency-001',
     'BUY', 'LIMIT', 10, 100.00, 'RECEIVED');

INSERT INTO orders
    (account_id, instrument_id, idempotency_key,
     side, order_type, quantity, price, status)
VALUES
    (1, 1, 'probe-idempotency-001',
     'BUY', 'LIMIT', 10, 100.00, 'RECEIVED');