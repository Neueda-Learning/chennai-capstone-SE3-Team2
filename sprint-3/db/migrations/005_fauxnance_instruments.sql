-- =====================================================================
-- 005_fauxnance_instruments.sql
--
-- Sprint 7. Instruments whose symbols exist in the Fauxnance registry,
-- so that an order can be priced against a live quote.
--
-- WHY A SECOND SET OF INSTRUMENTS.
--
-- 003_instruments.sql seeds a fictional universe -- APEX, ORIONM,
-- NIFTYBEES and the rest -- invented for Sprint 3 to exercise the
-- schema. Sprint 7 introduces something Sprint 3 had no reason to care
-- about: the executor prices every order against a real HTTP API, and
-- that API has never heard of APEX. Every order on a fictional symbol
-- comes back 404.
--
-- The fictional instruments are deliberately left tradable rather than
-- being switched off. They are the platform's demonstration of the
-- no-price path: an order on APEX reaches the executor, cannot be
-- priced, and is resolved as rejected with NO_PRICE rather than being
-- left at NEW for ever. That is one of story 610's acceptance criteria
-- and this is the honest way to show it, without staging an outage.
--
-- They also stay because the Sprint 6 characterisation tests pin
-- behaviour against APEX. Those tests record what the service did
-- before this sprint changed it, and moving the data underneath them
-- would invalidate the one thing they exist to prove.
--
-- WHY .NS ONLY. The team trades Indian markets. Fauxnance resolves a
-- plain ticker to a US listing, .NS to the NSE and .BO to the BSE, and
-- our exchange table holds NSE and BSE already -- so an NSE symbol
-- needs no new exchange row and no new FK. Mutual funds are absent on
-- purpose: Fauxnance's registry is closed at equity, etf, fx and
-- crypto, and a fund has no bid or ask to fill against in any case.
--
-- WHY NO POSITIONS ARE SEEDED HERE. A holding on one of these would
-- change the count that TradeApiIntegrationTest pins for account 3, and
-- the demonstration reads better without one: buy ITC.NS, watch the
-- executor write the position, then sell it back. The round trip shows
-- the spread being charged twice, which is the point of settling at bid
-- and ask rather than at the last traded price.
--
-- The ISINs are the real ones. instrument_id is never hand-typed: 003
-- numbers its equity rows 1 to 12 by hand, so a literal here would
-- break the moment anybody inserts a row above it. Each subtype row
-- resolves its parent by ISIN instead, which cannot drift.
-- =====================================================================

INSERT INTO instrument (instrument_type, name, isin, is_tradable) VALUES
                                                                      ('STOCK', 'MRF Limited',                     'INE883A01011', TRUE),
                                                                      ('STOCK', 'Tata Consultancy Services Ltd',   'INE467B01029', TRUE),
                                                                      ('STOCK', 'HDFC Bank Ltd',                   'INE040A01034', TRUE),
                                                                      ('STOCK', 'ITC Ltd',                         'INE154A01025', TRUE)
    ON CONFLICT (isin) DO NOTHING;


-- The symbol the customer posts and the executor prices is equity.ticker,
-- read through COALESCE(e.ticker, m.scheme_code). It carries the Fauxnance
-- suffix because that is the string that goes into GET /quotes/{symbol}.
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