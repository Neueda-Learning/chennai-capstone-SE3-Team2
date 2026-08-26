-- =====================================================================
-- 003_instruments.sql
-- 12 instruments: 7 STOCK, 2 ETF, 3 MF.
--
-- Every instrument gets exactly one subtype row, and the subtype's
-- instrument_type must match the parent's - the composite FK rejects
-- any disagreement.
--
-- Instrument 7 (Meridian Steel) is is_tradable = FALSE: delisted, so no
-- new orders, but client 3 still holds it and must still see it.
-- =====================================================================

INSERT INTO instrument (instrument_type, name, isin, is_tradable) VALUES
    ('STOCK', 'Apex Industries Ltd',        'INE001A01011', TRUE),
    ('STOCK', 'Orion Motors Ltd',           'INE002A01012', TRUE),
    ('STOCK', 'Vertex Pharma Ltd',          'INE003A01013', TRUE),
    ('STOCK', 'Summit Cement Ltd',          'INE004A01014', TRUE),
    ('STOCK', 'Pinnacle Energy Ltd',        'INE005A01015', TRUE),
    ('STOCK', 'Zenith Textiles Ltd',        'INE006A01016', TRUE),
    ('STOCK', 'Meridian Steel Ltd',         'INE007A01017', FALSE),  -- delisted
    ('ETF',   'Nifty 50 ETF',               'INE008A01018', TRUE),
    ('ETF',   'Gold ETF',                   'INE009A01019', TRUE),
    ('MF',    'Bluechip Growth Fund',       'INF010A01010', TRUE),
    ('MF',    'Flexi Cap Fund',             'INF011A01011', TRUE),
    ('MF',    'Liquid Fund',                'INF012A01012', TRUE);

-- Exchange-traded subtype: stocks and ETFs.
-- Apex is dual-listed, so it appears on both NSE and BSE... which is
-- exactly why the unique constraint is on (ticker, exchange_code)
-- rather than ticker alone.
INSERT INTO equity (instrument_id, instrument_type, ticker, exchange_code, lot_size) VALUES
    ( 1, 'STOCK', 'APEX',     'NSE', 1),
    ( 2, 'STOCK', 'ORIONM',   'NSE', 1),
    ( 3, 'STOCK', 'VERTEXP',  'NSE', 1),
    ( 4, 'STOCK', 'SUMCEM',   'BSE', 1),
    ( 5, 'STOCK', 'PINEN',    'NSE', 1),
    ( 6, 'STOCK', 'ZENTEX',   'BSE', 1),
    ( 7, 'STOCK', 'MERSTL',   'NSE', 1),
    ( 8, 'ETF',   'NIFTYBEES','NSE', 1),
    ( 9, 'ETF',   'GOLDBEES', 'NSE', 1);

-- Mutual fund subtype.
INSERT INTO mutual_fund (instrument_id, instrument_type, scheme_code,
                         amc_id, plan_type, expense_ratio) VALUES
    (10, 'MF', 'SCH100001', 1, 'DIRECT_GROWTH',  0.0085),
    (11, 'MF', 'SCH100002', 2, 'REGULAR_GROWTH', 0.0175),
    (12, 'MF', 'SCH100003', 3, 'DIRECT_GROWTH',  0.0022);
