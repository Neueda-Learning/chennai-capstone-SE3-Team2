-- =====================================================================
-- 006_bank_accounts.sql
-- A registered bank account for each of the ten seeded clients, so the
-- demo accounts can deposit and withdraw like a customer who applied.
--
-- Fake numbers at a fake bank (IFSC prefix DEMO). None ends in 0000, the
-- KYC stub's "bank account not found" rule. The holder name is copied from
-- the profile, so the two cannot disagree.
-- =====================================================================

INSERT INTO bank_account (client_id, account_number, ifsc, holder_name)
SELECT p.client_id,
       '9100000000' || lpad(p.client_id::text, 4, '0') || '1',
       'DEMO0000001',
       p.name
FROM client_profile p
WHERE p.client_id BETWEEN 1 AND 10
ON CONFLICT (client_id) DO NOTHING;
