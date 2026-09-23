-- 005_fauxnance_instruments.sql
--
-- Symbols that exist in the Fauxnance registry, so orders can be priced
-- live. The fictional tickers in 003 stay tradable and become the
-- no-price demonstration: Fauxnance 404s them.
--
-- Lives in seed/ and not migrations/: it is rows, not schema, and the
-- equity rows reference an exchange that seed/001 creates.
--
-- .NS only. instrument_id is resolved by ISIN, never hand-typed.

INSERT INTO instrument (instrument_type, name, isin, is_tradable) VALUES
                                                                      ('STOCK', 'MRF Limited',                     'INE883A01011', TRUE),
                                                                      ('STOCK', 'Tata Consultancy Services Ltd',   'INE467B01029', TRUE),
                                                                      ('STOCK', 'HDFC Bank Ltd',                   'INE040A01034', TRUE),
                                                                      ('STOCK', 'ITC Ltd',                         'INE154A01025', TRUE)
    ON CONFLICT (isin) DO NOTHING;

-- equity.ticker is the string that goes into GET /quotes/{symbol}.
INSERT INTO equity (instrument_id, instrument_type, ticker, exchange_code, lot_size)
SELECT i.instrument_id, 'STOCK', v.ticker, 'NSE', v.lot_size
FROM (VALUES
          ('INE883A01011', 'MRF.NS',      1),
          ('INE467B01029', 'TCS.NS',      1),
          ('INE040A01034', 'HDFCBANK.NS', 1),
          ('INE154A01025', 'ITC.NS',      1)
     ) AS v (isin, ticker, lot_size)
         JOIN instrument i ON i.isin = v.isin
    ON CONFLICT (instrument_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- A note for the review, on picking a symbol to demonstrate with.
--
-- MRF.NS trades around 125,000 a share. Account 3 holds 750,000 with
-- 220,000 blocked, so 530,000 is available: four shares fit and five do
-- not. That is not a problem to route around -- it is the cleanest
-- INSUFFICIENT_FUNDS demonstration in the seed, using a real price
-- rather than a contrived one, and it exercises rule 6 being re-checked
-- at the executed price rather than the limit.
--
-- ITC.NS and HDFCBANK.NS are in the hundreds and low thousands, so the
-- fill demonstration goes through one of those.
-- ---------------------------------------------------------------------