-- =====================================================================
-- 002_clients.sql
-- 10 clients. Each gets one account + one profile + one auth row (1:1).
--
-- Covers the states the app has to handle:
--   client 1-7   ACTIVE + VERIFIED   - normal trading clients
--   client 8     SUSPENDED           - may sell, may not buy
--   client 9     ACTIVE + PENDING    - onboarded, cannot trade yet
--   client 10    CLOSED              - no positions, no blocked funds
--
-- client_id is GENERATED ALWAYS AS IDENTITY and these are the first
-- rows inserted, so ids land as 1..10 and later files reference them
-- directly.
-- =====================================================================

-- account_ref is the string business reference the contract calls
-- AccountResponse.accountId. 002_api_alignment.sql made it NOT NULL.
INSERT INTO client_account
    (account_ref, pan, demat_id, kyc_status, status, balance, blocked_funds, version) VALUES
    ('ACC-000001', 'ABCPS1234A', 'IN30001000000001', 'VERIFIED', 'ACTIVE',      125000.0000,  15000.0000, 3),
    ('ACC-000002', 'BXYPK5678B', 'IN30001000000002', 'VERIFIED', 'ACTIVE',       48500.5000,      0.0000, 1),
    ('ACC-000003', 'CDEPR9012C', 'IN30001000000003', 'VERIFIED', 'ACTIVE',      750000.0000, 220000.0000, 7),
    ('ACC-000004', 'DFGPT3456D', 'IN30001000000004', 'VERIFIED', 'ACTIVE',        9200.7500,   1200.0000, 2),
    ('ACC-000005', 'EHIPV7890E', 'IN30001000000005', 'VERIFIED', 'ACTIVE',      310000.0000,      0.0000, 5),
    ('ACC-000006', 'FJKPW2345F', 'IN30001000000006', 'VERIFIED', 'ACTIVE',       67800.2500,   5400.0000, 4),
    ('ACC-000007', 'GLMPY6789G', 'IN30001000000007', 'VERIFIED', 'ACTIVE',      158000.0000,  32000.0000, 6),
    ('ACC-000008', 'HNOPA0123H', 'IN30001000000008', 'VERIFIED', 'SUSPENDED',    22000.0000,      0.0000, 2),
    ('ACC-000009', 'IPQPC4567I', 'IN30001000000009', 'PENDING',  'ACTIVE',        5000.0000,      0.0000, 0),
    ('ACC-000010', 'JRSPE8901J', 'IN30001000000010', 'VERIFIED', 'CLOSED',           0.0000,      0.0000, 9);

INSERT INTO client_profile (client_id, name, dob, email, phone_number, address) VALUES
    ( 1, 'Aarav Sharma',     '1988-04-12', 'aarav.sharma@example.com',   '+919812345601', 'Flat 12, Anna Nagar, Chennai 600040'),
    ( 2, 'Diya Iyer',        '1995-11-30', 'diya.iyer@example.com',      '+919812345602', '4B Besant Nagar, Chennai 600090'),
    ( 3, 'Rohan Nair',       '1979-02-08', 'rohan.nair@example.com',     '+919812345603', '22 Indiranagar, Bengaluru 560038'),
    ( 4, 'Ishita Patel',     '2001-07-19', 'ishita.patel@example.com',   '+919812345604', '7 Satellite Road, Ahmedabad 380015'),
    ( 5, 'Kabir Reddy',      '1985-09-25', 'kabir.reddy@example.com',    '+919812345605', '15 Jubilee Hills, Hyderabad 500033'),
    ( 6, 'Meera Kulkarni',   '1992-01-14', 'meera.kulkarni@example.com', '+919812345606', '9 Kothrud, Pune 411038'),
    ( 7, 'Arjun Bose',       '1975-06-03', 'arjun.bose@example.com',     '+919812345607', '31 Salt Lake, Kolkata 700091'),
    ( 8, 'Sana Menon',       '1998-12-21', 'sana.menon@example.com',     '+919812345608', '5 Panampilly Nagar, Kochi 682036'),
    ( 9, 'Vikram Gupta',     '1990-03-17', 'vikram.gupta@example.com',   '+919812345609', '18 Vasant Kunj, New Delhi 110070'),
    (10, 'Priya Rao',        '1983-08-09', 'priya.rao@example.com',      '+919812345610', '2 Adyar, Chennai 600020');
