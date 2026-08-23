INSERT INTO orders
    (account_id, instrument_id, idempotency_key,
     side, order_type, quantity, price, status)
VALUES
    (1, 1, 'order-001',
     'BUY', 'LIMIT', 10, 3500.00, 'EXECUTED'),

    (1, 2, 'order-002',
     'BUY', 'MARKET', 20, NULL, 'EXECUTED'),

    (2, 1, 'order-003',
     'SELL', 'LIMIT', 5, 3600.00, 'REJECTED'),

    (1, 3, 'order-004',
     'BUY', 'LIMIT', 15, 1400.00, 'CANCELLED'),

    (2, 2, 'order-005',
     'BUY', 'LIMIT', 25, 1700.00, 'RECEIVED');