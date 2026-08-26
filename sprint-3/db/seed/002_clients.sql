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

INSERT INTO client_account
    (pan, demat_id, kyc_status, status, balance, blocked_funds, version) VALUES
    ('ABCPS1234A', 'IN30001000000001', 'VERIFIED', 'ACTIVE',      125000.0000,  15000.0000, 3),
    ('BXYPK5678B', 'IN30001000000002', 'VERIFIED', 'ACTIVE',       48500.5000,      0.0000, 1),
    ('CDEPR9012C', 'IN30001000000003', 'VERIFIED', 'ACTIVE',      750000.0000, 220000.0000, 7),
    ('DFGPT3456D', 'IN30001000000004', 'VERIFIED', 'ACTIVE',        9200.7500,   1200.0000, 2),
    ('EHIPV7890E', 'IN30001000000005', 'VERIFIED', 'ACTIVE',      310000.0000,      0.0000, 5),
    ('FJKPW2345F', 'IN30001000000006', 'VERIFIED', 'ACTIVE',       67800.2500,   5400.0000, 4),
    ('GLMPY6789G', 'IN30001000000007', 'VERIFIED', 'ACTIVE',      158000.0000,  32000.0000, 6),
    ('HNOPA0123H', 'IN30001000000008', 'VERIFIED', 'SUSPENDED',    22000.0000,      0.0000, 2),
    ('IPQPC4567I', 'IN30001000000009', 'PENDING',  'ACTIVE',        5000.0000,      0.0000, 0),
    ('JRSPE8901J', 'IN30001000000010', 'VERIFIED', 'CLOSED',           0.0000,      0.0000, 9);

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

-- Password hashes are structurally valid bcrypt strings, not real
-- hashes. Seed data only - never a real credential.
INSERT INTO client_auth (client_id, password_hash, last_login) VALUES
    ( 1, '$2b$12$KIXQJ8fZ1kL0pQxRvNbYceH3mWs5tUuVwXyZaBcDeFgHiJkLmNoPq', '2026-08-25 09:14:00+05:30'),
    ( 2, '$2b$12$LJYRK9gA2mM1qRySwOcZdfI4nXt6uVvWxYzAbCdEfGhIjKlMnOpQr', '2026-08-24 18:02:00+05:30'),
    ( 3, '$2b$12$MKZSL0hB3nN2rSzTxPdAegJ5oYu7vWwXyZaBcDeFgHiJkLmNoPqRs', '2026-08-25 08:47:00+05:30'),
    ( 4, '$2b$12$NLATM1iC4oO3sTAyQeBfhK6pZv8wXxYzAbCdEfGhIjKlMnOpQrStU', '2026-08-22 11:30:00+05:30'),
    ( 5, '$2b$12$OMBUN2jD5pP4tUBzRfCgiL7qAw9xYyZaBcDeFgHiJkLmNoPqRsTuV', '2026-08-25 07:20:00+05:30'),
    ( 6, '$2b$12$PNCVO3kE6qQ5uVCASgDhjM8rBx0yZzAbCdEfGhIjKlMnOpQrStUvW', '2026-08-23 16:55:00+05:30'),
    ( 7, '$2b$12$QODWP4lF7rR6vWDBThEikN9sCy1zAaBcDeFgHiJkLmNoPqRsTuVwX', '2026-08-25 10:05:00+05:30'),
    ( 8, '$2b$12$RPEXQ5mG8sS7wXECUiFjlO0tDz2aBbCdEfGhIjKlMnOpQrStUvWxY', '2026-08-10 12:00:00+05:30'),
    ( 9, '$2b$12$SQFYR6nH9tT8xYFDVjGkmP1uEA3bCcDeFgHiJkLmNoPqRsTuVwXyZ', NULL),   -- never logged in
    (10, '$2b$12$TRGZS7oI0uU9yZGEWkHlnQ2vFB4cDdEfGhIjKlMnOpQrStUvWxYzA', '2026-06-30 14:22:00+05:30');
