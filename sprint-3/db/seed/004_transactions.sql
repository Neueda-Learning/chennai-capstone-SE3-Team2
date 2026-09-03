-- =====================================================================
-- 004_transactions.sql
-- Archived orders (terminal only).

INSERT INTO orders_history
    (order_id, client_id, instrument_id, side, order_type, product_type,
     price, quantity, fill_price, status, idempotency_key,
     date_placed, resolved_at, archived_at) VALUES
    ( 1, 1,  1, 'BUY',  'LIMIT',  'CNC', 1450.0000,  50.000000, 1448.5000, 'SUCCESS',   'hist-0001', '2026-07-10 09:20:00+05:30', '2026-07-10 09:20:04+05:30', '2026-07-11 20:00:00+05:30'),
    ( 2, 1,  3, 'BUY',  'MARKET', 'CNC',      NULL,  20.000000,  892.7500, 'SUCCESS',   'hist-0002', '2026-07-12 10:05:00+05:30', '2026-07-12 10:05:01+05:30', '2026-07-13 20:00:00+05:30'),
    ( 3, 2,  8, 'BUY',  'LIMIT',  'CNC',  245.0000, 100.000000,  244.8000, 'SUCCESS',   'hist-0003', '2026-07-15 11:40:00+05:30', '2026-07-15 11:40:07+05:30', '2026-07-16 20:00:00+05:30'),
    ( 4, 3,  2, 'BUY',  'LIMIT',  'CNC', 3120.0000,  75.000000,      NULL, 'FAILED',    'hist-0004', '2026-07-18 14:12:00+05:30', '2026-07-18 14:12:03+05:30', '2026-07-19 20:00:00+05:30'),
    ( 5, 3,  2, 'BUY',  'LIMIT',  'CNC', 3180.0000,  75.000000, 3176.2500, 'SUCCESS',   'hist-0005', '2026-07-18 14:15:00+05:30', '2026-07-18 14:15:02+05:30', '2026-07-19 20:00:00+05:30'),
    ( 6, 5,  5, 'BUY',  'MARKET', 'MIS',      NULL, 200.000000,  318.4000, 'SUCCESS',   'hist-0006', '2026-07-22 09:35:00+05:30', '2026-07-22 09:35:01+05:30', '2026-07-23 20:00:00+05:30'),
    ( 7, 5,  5, 'SELL', 'MARKET', 'MIS',      NULL, 200.000000,  322.1000, 'SUCCESS',   'hist-0007', '2026-07-22 15:10:00+05:30', '2026-07-22 15:10:01+05:30', '2026-07-23 20:00:00+05:30'),
    ( 8, 6,  4, 'BUY',  'LIMIT',  'CNC',  610.0000,  40.000000,      NULL, 'CANCELLED', 'hist-0008', '2026-07-25 12:00:00+05:30', '2026-07-25 12:45:00+05:30', '2026-07-26 20:00:00+05:30'),
    ( 9, 7, 10, 'BUY',  'MARKET', 'CNC',      NULL, 152.386000,   65.6200, 'SUCCESS',   'hist-0009', '2026-07-28 13:00:00+05:30', '2026-07-29 06:00:00+05:30', '2026-07-30 20:00:00+05:30'),
    (10, 8,  6, 'SELL', 'LIMIT',  'CNC',  188.0000,  30.000000,  188.9000, 'SUCCESS',   'hist-0010', '2026-08-01 10:30:00+05:30', '2026-08-01 10:31:00+05:30', '2026-08-02 20:00:00+05:30');

-- Advance the identity sequence past the archived range.
SELECT setval(pg_get_serial_sequence('orders', 'order_id'),
              (SELECT max(order_id) FROM orders_history));

-- ---------------------------------------------------------------------
-- Live orders. order_id continues from 11.
--
-- Two rows are deliberately unusual:
--   * client 8 is SUSPENDED, so their only live order is a SELL
--   * order from client 4 has no idempotency_key, exercising the
--     nullable path (a caller that forgoes retry protection)
-- ---------------------------------------------------------------------
INSERT INTO orders
    (client_id, instrument_id, side, order_type, product_type,
     price, quantity, fill_price, status, idempotency_key,
     date_placed, resolved_at) VALUES
    (1,  1, 'BUY',  'LIMIT',  'CNC', 1495.0000,  25.000000, 1493.2000, 'SUCCESS',   'ord-0011', '2026-08-24 09:16:00+05:30', '2026-08-24 09:16:02+05:30'),
    (1,  8, 'BUY',  'MARKET', 'CNC',      NULL,  60.000000,  251.4000, 'SUCCESS',   'ord-0012', '2026-08-24 09:45:00+05:30', '2026-08-24 09:45:01+05:30'),
    (1,  3, 'SELL', 'LIMIT',  'CNC',  915.0000,  10.000000,      NULL, 'PENDING',   'ord-0013', '2026-08-25 09:30:00+05:30', NULL),
    (2,  8, 'SELL', 'LIMIT',  'CNC',  256.0000,  40.000000,  256.3000, 'SUCCESS',   'ord-0014', '2026-08-24 14:20:00+05:30', '2026-08-24 14:20:05+05:30'),
    (2,  2, 'BUY',  'LIMIT',  'MIS', 3050.0000,  15.000000,      NULL, 'CANCELLED', 'ord-0015', '2026-08-25 10:00:00+05:30', '2026-08-25 10:12:00+05:30'),
    (3,  2, 'BUY',  'MARKET', 'CNC',      NULL, 100.000000, 3201.5000, 'SUCCESS',   'ord-0016', '2026-08-24 11:00:00+05:30', '2026-08-24 11:00:01+05:30'),
    (3,  4, 'BUY',  'LIMIT',  'CNC',  598.0000, 150.000000,  597.5000, 'SUCCESS',   'ord-0017', '2026-08-24 11:30:00+05:30', '2026-08-24 11:30:04+05:30'),
    (3,  9, 'BUY',  'LIMIT',  'CNC',   72.5000, 500.000000,      NULL, 'PENDING',   'ord-0018', '2026-08-25 09:35:00+05:30', NULL),
    (4,  1, 'BUY',  'LIMIT',  'MIS', 1480.0000,   5.000000,      NULL, 'FAILED',    NULL,       '2026-08-25 09:50:00+05:30', '2026-08-25 09:50:02+05:30'),
    (4,  6, 'BUY',  'MARKET', 'CNC',      NULL,  12.000000,  191.2000, 'SUCCESS',   'ord-0020', '2026-08-25 10:15:00+05:30', '2026-08-25 10:15:01+05:30'),
    (5,  5, 'BUY',  'MARKET', 'MIS',      NULL, 300.000000,  325.6000, 'SUCCESS',   'ord-0021', '2026-08-25 09:20:00+05:30', '2026-08-25 09:20:01+05:30'),
    (5, 11, 'BUY',  'MARKET', 'CNC',      NULL, 240.117000,   41.6500, 'SUCCESS',   'ord-0022', '2026-08-24 13:00:00+05:30', '2026-08-25 06:00:00+05:30'),
    (6,  4, 'BUY',  'LIMIT',  'CNC',  605.0000,  40.000000,  604.2500, 'SUCCESS',   'ord-0023', '2026-08-24 15:05:00+05:30', '2026-08-24 15:05:03+05:30'),
    (6, 12, 'BUY',  'MARKET', 'CNC',      NULL, 980.500000,   10.2000, 'SUCCESS',   'ord-0024', '2026-08-24 16:00:00+05:30', '2026-08-25 06:00:00+05:30'),
    (7, 10, 'SELL', 'MARKET', 'CNC',      NULL,  50.000000,   66.8000, 'SUCCESS',   'ord-0025', '2026-08-25 11:00:00+05:30', '2026-08-25 11:00:02+05:30'),
    (7,  1, 'BUY',  'LIMIT',  'CNC', 1470.0000,  30.000000,      NULL, 'PENDING',   'ord-0026', '2026-08-25 11:20:00+05:30', NULL),
    (7,  3, 'BUY',  'LIMIT',  'MIS',  880.0000,  25.000000,      NULL, 'CANCELLED', 'ord-0027', '2026-08-25 12:00:00+05:30', '2026-08-25 12:30:00+05:30'),
    (8,  6, 'SELL', 'LIMIT',  'CNC',  195.0000,  20.000000,      NULL, 'PENDING',   'ord-0028', '2026-08-25 10:40:00+05:30', NULL),
    (2,  1, 'BUY',  'LIMIT',  'CNC', 1460.0000,  10.000000,      NULL, 'FAILED',    'ord-0029', '2026-08-25 13:10:00+05:30', '2026-08-25 13:10:02+05:30'),
    (5,  8, 'BUY',  'LIMIT',  'CNC',  248.0000,  80.000000,  247.9000, 'SUCCESS',   'ord-0030', '2026-08-25 13:45:00+05:30', '2026-08-25 13:45:06+05:30');

-- ---------------------------------------------------------------------
-- Positions: what clients currently hold.
--
-- INTRADAY rows feed the Positions tab, DELIVERY the Holdings tab.
-- Client 3 holds instrument 7 (Meridian Steel, delisted) - the case
-- is_tradable exists for: no new orders, but the holding stays visible.
-- Clients 9 and 10 hold nothing (9 is unverified, 10 is closed).
-- ---------------------------------------------------------------------
INSERT INTO position (client_id, instrument_id, position_type, quantity, average_price) VALUES
    (1,  1, 'DELIVERY',   75.000000, 1465.4000),
    (1,  3, 'DELIVERY',   20.000000,  892.7500),
    (1,  8, 'DELIVERY',   60.000000,  251.4000),
    (2,  8, 'DELIVERY',   60.000000,  244.8000),
    (3,  2, 'DELIVERY',  175.000000, 3212.1000),
    (3,  4, 'DELIVERY',  150.000000,  597.5000),
    (3,  7, 'DELIVERY',  500.000000,   84.3000),   -- delisted instrument
    (5,  5, 'INTRADAY',  300.000000,  325.6000),
    (5, 11, 'DELIVERY',  240.117000,   41.6500),
    (6,  4, 'DELIVERY',   40.000000,  604.2500),
    (7, 10, 'DELIVERY',  102.386000,   65.6200),
    (8,  6, 'DELIVERY',   50.000000,  180.1000);

-- ---------------------------------------------------------------------
-- Fund transfers: deposits and withdrawals.
-- PENDING rows have no reference_id yet - the gateway has not responded.
-- ---------------------------------------------------------------------
INSERT INTO fund_transfer
    (client_id, amount, direction, status, reference_id, idempotency_key, created_at) VALUES
    ( 1, 200000.0000, 'DEPOSIT',    'SUCCESS', 'PG-REF-0000000001', 'ft-0001', '2026-07-01 10:00:00+05:30'),
    ( 1,  50000.0000, 'WITHDRAWAL', 'SUCCESS', 'PG-REF-0000000002', 'ft-0002', '2026-08-05 16:30:00+05:30'),
    ( 2,  60000.0000, 'DEPOSIT',    'SUCCESS', 'PG-REF-0000000003', 'ft-0003', '2026-07-03 09:15:00+05:30'),
    ( 3, 900000.0000, 'DEPOSIT',    'SUCCESS', 'PG-REF-0000000004', 'ft-0004', '2026-06-20 11:45:00+05:30'),
    ( 3, 100000.0000, 'WITHDRAWAL', 'SUCCESS', 'PG-REF-0000000005', 'ft-0005', '2026-08-12 14:00:00+05:30'),
    ( 4,  15000.0000, 'DEPOSIT',    'SUCCESS', 'PG-REF-0000000006', 'ft-0006', '2026-07-28 18:20:00+05:30'),
    ( 4,   3000.0000, 'DEPOSIT',    'FAILED',  'PG-REF-0000000007', 'ft-0007', '2026-08-19 20:05:00+05:30'),
    ( 5, 400000.0000, 'DEPOSIT',    'SUCCESS', 'PG-REF-0000000008', 'ft-0008', '2026-06-15 08:30:00+05:30'),
    ( 6,  80000.0000, 'DEPOSIT',    'SUCCESS', 'PG-REF-0000000009', 'ft-0009', '2026-07-09 12:10:00+05:30'),
    ( 7, 250000.0000, 'DEPOSIT',    'SUCCESS', 'PG-REF-0000000010', 'ft-0010', '2026-07-02 15:40:00+05:30'),
    ( 8,  22000.0000, 'DEPOSIT',    'SUCCESS', 'PG-REF-0000000011', 'ft-0011', '2026-05-30 10:00:00+05:30'),
    ( 9,   5000.0000, 'DEPOSIT',    'PENDING',  NULL,               'ft-0012', '2026-08-25 12:30:00+05:30');

ANALYZE;
