-- =====================================================================
-- 001_reference_data.sql
-- Exchanges and fund houses. No FK dependencies - loads first.
-- =====================================================================

INSERT INTO exchange (exchange_code, name, country, is_active) VALUES
    ('NSE', 'National Stock Exchange of India', 'IN', TRUE),
    ('BSE', 'BSE Limited',                      'IN', TRUE)
ON CONFLICT (exchange_code) DO NOTHING;

INSERT INTO amc (amc_code, amc_name, is_active) VALUES
    ('AMC001', 'Bluepeak Asset Management',    TRUE),
    ('AMC002', 'Northwind Mutual',             TRUE),
    ('AMC003', 'Sterling Investment Managers', TRUE),
    ('AMC004', 'Meridian Fund House',          FALSE)   -- wound up
ON CONFLICT (amc_code) DO NOTHING;
