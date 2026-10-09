-- 007_mf_nav_funds.sql
--
-- Funds that exist in AMFI's NAV file, so mutual fund orders can be priced
-- by the MF NAV service (MF_NAV_BASE_URL). The fictional funds in 003 stay
-- tradable and become the no-price demonstration, as 003's fictional
-- tickers are for Fauxnance: the service does not know them.
--
-- Lives in seed/ and not migrations/: it is rows, not schema. Names, ISINs
-- and AMFI scheme codes are exactly as the service returned them on
-- 4 Oct 2026. mutual_fund.scheme_code is the AMFI scheme code, which is what
-- an order names and what the executor asks the service for.
--
-- Direct Plan, Growth option only. Expense ratio left NULL: not published
-- by the service, and not guessed. instrument_id and amc_id are resolved
-- by ISIN and AMC code, never hand-typed.

INSERT INTO amc (amc_code, amc_name, is_active) VALUES
    ('ICICIPRU', 'ICICI Prudential Mutual Fund', TRUE),
    ('PPFAS',    'PPFAS Mutual Fund',            TRUE),
    ('UTI',      'UTI Mutual Fund',              TRUE),
    ('HDFC',     'HDFC Mutual Fund',             TRUE)
    ON CONFLICT (amc_code) DO NOTHING;

INSERT INTO instrument (instrument_type, name, isin, is_tradable) VALUES
    ('MF', 'ICICI Prudential Large Cap Fund (erstwhile Bluechip Fund) - Direct Plan - Growth', 'INF109K016L0', TRUE),
    ('MF', 'Parag Parikh Flexi Cap Fund - Direct Plan - Growth',                              'INF879O01027', TRUE),
    ('MF', 'UTI Nifty 50 Index Fund - Direct Plan - Growth',                                  'INF789F01XA0', TRUE),
    ('MF', 'HDFC Mid Cap Fund - Direct Plan - Growth Option',                                 'INF179K01XQ0', TRUE)
    ON CONFLICT (isin) DO NOTHING;

INSERT INTO mutual_fund (instrument_id, instrument_type, scheme_code, amc_id, plan_type, expense_ratio)
SELECT i.instrument_id, 'MF', v.scheme_code, a.amc_id, 'DIRECT_GROWTH', NULL
FROM (VALUES
          ('INF109K016L0', '120586', 'ICICIPRU'),
          ('INF879O01027', '122639', 'PPFAS'),
          ('INF789F01XA0', '120716', 'UTI'),
          ('INF179K01XQ0', '118989', 'HDFC')
     ) AS v (isin, scheme_code, amc_code)
         JOIN instrument i ON i.isin = v.isin
         JOIN amc a ON a.amc_code = v.amc_code
    ON CONFLICT (instrument_id) DO NOTHING;
