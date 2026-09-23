-- =====================================================================
-- 004_transactions.sql
-- Archived orders (terminal only).

INSERT INTO orders_history
    (order_id, client_id, instrument_id, side, order_type, product_type,
     price, quantity, fill_price, status, idempotency_key,
     date_placed, resolved_at, archived_at) VALUES
    ('9d583c29-c392-5ec1-af4b-2587eb26953b', 1,  1, 'BUY',  'LIMIT',  'CNC', 1450.0000,  50.000000, 1448.5000, 'FILLED',   'hist-0001', '2026-07-10 09:20:00+05:30', '2026-07-10 09:20:04+05:30', '2026-07-11 20:00:00+05:30'),
    ('98f6a694-f42c-585f-8556-a5167ae7c8b2', 1,  3, 'BUY',  'MARKET', 'CNC',      NULL,  20.000000,  892.7500, 'FILLED',   'hist-0002', '2026-07-12 10:05:00+05:30', '2026-07-12 10:05:01+05:30', '2026-07-13 20:00:00+05:30'),
    ('214b622d-9bee-560e-bdde-1fcc7af9bbc3', 2,  8, 'BUY',  'LIMIT',  'CNC',  245.0000, 100.000000,  244.8000, 'FILLED',   'hist-0003', '2026-07-15 11:40:00+05:30', '2026-07-15 11:40:07+05:30', '2026-07-16 20:00:00+05:30'),
    ('34b83fc5-0f4a-5781-b99b-8eaea19c13c3', 3,  2, 'BUY',  'LIMIT',  'CNC', 3120.0000,  75.000000,      NULL, 'REJECTED',    'hist-0004', '2026-07-18 14:12:00+05:30', '2026-07-18 14:12:03+05:30', '2026-07-19 20:00:00+05:30'),
    ('57171b07-281e-5973-ab94-11c704040196', 3,  2, 'BUY',  'LIMIT',  'CNC', 3180.0000,  75.000000, 3176.2500, 'FILLED',   'hist-0005', '2026-07-18 14:15:00+05:30', '2026-07-18 14:15:02+05:30', '2026-07-19 20:00:00+05:30'),
    ('32638ed6-1c24-55ef-be3f-8c093f52e09a', 5,  5, 'BUY',  'MARKET', 'MIS',      NULL, 200.000000,  318.4000, 'FILLED',   'hist-0006', '2026-07-22 09:35:00+05:30', '2026-07-22 09:35:01+05:30', '2026-07-23 20:00:00+05:30'),
    ('2074ab0e-0024-5eb9-b679-b1f18fcb901d', 5,  5, 'SELL', 'MARKET', 'MIS',      NULL, 200.000000,  322.1000, 'FILLED',   'hist-0007', '2026-07-22 15:10:00+05:30', '2026-07-22 15:10:01+05:30', '2026-07-23 20:00:00+05:30'),
    ('34b8ab6e-184f-59bf-a067-982d6d83b1c7', 6,  4, 'BUY',  'LIMIT',  'CNC',  610.0000,  40.000000,      NULL, 'CANCELLED', 'hist-0008', '2026-07-25 12:00:00+05:30', '2026-07-25 12:45:00+05:30', '2026-07-26 20:00:00+05:30'),
    ('396597a7-4527-51af-823b-b86274e49144', 7, 10, 'BUY',  'MARKET', 'CNC',      NULL, 152.386000,   65.6200, 'FILLED',   'hist-0009', '2026-07-28 13:00:00+05:30', '2026-07-29 06:00:00+05:30', '2026-07-30 20:00:00+05:30'),
    ('fe33da1f-09c7-512a-a882-4f5911cd8d3f', 8,  6, 'SELL', 'LIMIT',  'CNC',  188.0000,  30.000000,  188.9000, 'FILLED',   'hist-0010', '2026-08-01 10:30:00+05:30', '2026-08-01 10:31:00+05:30', '2026-08-02 20:00:00+05:30');

-- The identity sequence is gone: 002_api_alignment.sql made order_id a
-- UUID supplied by the application, so there is nothing to advance.

-- ---------------------------------------------------------------------
-- Live orders. Ids are UUIDs, minted here as the application mints them.
--
-- Two rows are deliberately unusual:
--   * client 8 is SUSPENDED, so their only live order is a SELL
--   * order from client 4 has no idempotency_key, exercising the
--     nullable path (a caller that forgoes retry protection)
-- ---------------------------------------------------------------------
INSERT INTO orders
    (order_id, client_id, instrument_id, side, order_type, product_type,
     price, quantity, fill_price, status, idempotency_key,
     date_placed, resolved_at) VALUES
    ('16b2f1f4-1aed-546a-8201-a2edfe1d6047', 1,  1, 'BUY',  'LIMIT',  'CNC', 1495.0000,  25.000000, 1493.2000, 'FILLED',   'ord-0011', '2026-08-24 09:16:00+05:30', '2026-08-24 09:16:02+05:30'),
    ('28cd1970-f6af-57e7-ba2a-50567e3b594c', 1,  8, 'BUY',  'MARKET', 'CNC',      NULL,  60.000000,  251.4000, 'FILLED',   'ord-0012', '2026-08-24 09:45:00+05:30', '2026-08-24 09:45:01+05:30'),
    ('69fae69b-f297-5983-8c96-43812b37f6f9', 1,  3, 'SELL', 'LIMIT',  'CNC',  915.0000,  10.000000,      NULL, 'NEW',   'ord-0013', '2026-08-25 09:30:00+05:30', NULL),
    ('d5485525-d77b-5025-8016-5270543551aa', 2,  8, 'SELL', 'LIMIT',  'CNC',  256.0000,  40.000000,  256.3000, 'FILLED',   'ord-0014', '2026-08-24 14:20:00+05:30', '2026-08-24 14:20:05+05:30'),
    ('4e7fa8d1-8981-50f0-b91f-86222a6a3754', 2,  2, 'BUY',  'LIMIT',  'MIS', 3050.0000,  15.000000,      NULL, 'CANCELLED', 'ord-0015', '2026-08-25 10:00:00+05:30', '2026-08-25 10:12:00+05:30'),
    ('e09ca6a1-2563-55d2-a10f-770c139616c3', 3,  2, 'BUY',  'MARKET', 'CNC',      NULL, 100.000000, 3201.5000, 'FILLED',   'ord-0016', '2026-08-24 11:00:00+05:30', '2026-08-24 11:00:01+05:30'),
    ('7ce4af97-a2bb-57b6-805a-14004168678c', 3,  4, 'BUY',  'LIMIT',  'CNC',  598.0000, 150.000000,  597.5000, 'FILLED',   'ord-0017', '2026-08-24 11:30:00+05:30', '2026-08-24 11:30:04+05:30'),
    ('12a8f1a9-c1ee-5fc5-80fd-d585cc22e7e1', 3,  9, 'BUY',  'LIMIT',  'CNC',   72.5000, 500.000000,      NULL, 'NEW',   'ord-0018', '2026-08-25 09:35:00+05:30', NULL),
    ('4236e9ef-4a39-52c1-8267-97959d64d129', 4,  1, 'BUY',  'LIMIT',  'MIS', 1480.0000,   5.000000,      NULL, 'REJECTED',    NULL,       '2026-08-25 09:50:00+05:30', '2026-08-25 09:50:02+05:30'),
    ('040650b4-a4d8-5ea2-858f-b113c2966f7a', 4,  6, 'BUY',  'MARKET', 'CNC',      NULL,  12.000000,  191.2000, 'FILLED',   'ord-0020', '2026-08-25 10:15:00+05:30', '2026-08-25 10:15:01+05:30'),
    ('f918faa6-4e2a-5a53-be8d-00f841d5f217', 5,  5, 'BUY',  'MARKET', 'MIS',      NULL, 300.000000,  325.6000, 'FILLED',   'ord-0021', '2026-08-25 09:20:00+05:30', '2026-08-25 09:20:01+05:30'),
    ('c0079197-faf0-5649-aa75-a50adedcc4ef', 5, 11, 'BUY',  'MARKET', 'CNC',      NULL, 240.117000,   41.6500, 'FILLED',   'ord-0022', '2026-08-24 13:00:00+05:30', '2026-08-25 06:00:00+05:30'),
    ('b6e65f63-e012-5a5e-ad3b-898227ce6fa1', 6,  4, 'BUY',  'LIMIT',  'CNC',  605.0000,  40.000000,  604.2500, 'FILLED',   'ord-0023', '2026-08-24 15:05:00+05:30', '2026-08-24 15:05:03+05:30'),
    ('b19bff49-25e4-5453-bd29-fdb6cac4a122', 6, 12, 'BUY',  'MARKET', 'CNC',      NULL, 980.500000,   10.2000, 'FILLED',   'ord-0024', '2026-08-24 16:00:00+05:30', '2026-08-25 06:00:00+05:30'),
    ('c3949029-d943-5845-9cff-30a086fd3b06', 7, 10, 'SELL', 'MARKET', 'CNC',      NULL,  50.000000,   66.8000, 'FILLED',   'ord-0025', '2026-08-25 11:00:00+05:30', '2026-08-25 11:00:02+05:30'),
    ('e218530c-3dcb-52e2-9a78-3de0c2109502', 7,  1, 'BUY',  'LIMIT',  'CNC', 1470.0000,  30.000000,      NULL, 'NEW',   'ord-0026', '2026-08-25 11:20:00+05:30', NULL),
    ('5951d4ec-22a0-53e1-80f1-1d31b158a96d', 7,  3, 'BUY',  'LIMIT',  'MIS',  880.0000,  25.000000,      NULL, 'CANCELLED', 'ord-0027', '2026-08-25 12:00:00+05:30', '2026-08-25 12:30:00+05:30'),
    ('97e37551-c876-5cda-8a45-a5d2b81657c8', 8,  6, 'SELL', 'LIMIT',  'CNC',  195.0000,  20.000000,      NULL, 'NEW',   'ord-0028', '2026-08-25 10:40:00+05:30', NULL),
    ('78d62368-44a9-5327-bfd8-04c266f1f934', 2,  1, 'BUY',  'LIMIT',  'CNC', 1460.0000,  10.000000,      NULL, 'REJECTED',    'ord-0029', '2026-08-25 13:10:00+05:30', '2026-08-25 13:10:02+05:30'),
    ('70329607-fed3-5784-aa36-7a8a3c672bf8', 5,  8, 'BUY',  'LIMIT',  'CNC',  248.0000,  80.000000,  247.9000, 'FILLED',   'ord-0030', '2026-08-25 13:45:00+05:30', '2026-08-25 13:45:06+05:30');

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
