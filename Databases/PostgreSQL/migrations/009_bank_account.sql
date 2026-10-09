-- =====================================================================
-- 009_bank_account.sql
--
-- The customer's one registered bank account. Money moves only between the
-- trading account and this bank account: deposits come from it, withdrawals
-- go to it, never anywhere else. It is given on the application and checked
-- by KYC before the customer can trade.
--
-- One per customer, keyed by client_id. The number is personal data: never
-- logged, and shown to the customer only masked (last four digits).
-- holder_name is the name the payout is addressed to -- the applicant's.
-- =====================================================================

CREATE TABLE IF NOT EXISTS bank_account (
    client_id       INTEGER       PRIMARY KEY
                                  REFERENCES client_account (client_id),
    account_number  VARCHAR(18)   NOT NULL,
    ifsc            CHAR(11)      NOT NULL,
    holder_name     VARCHAR(120)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Indian account numbers run 9 to 18 digits; an IFSC is four letters
    -- for the bank, a zero, then six letters or digits for the branch.
    CONSTRAINT ck_bank_account_number CHECK (account_number ~ '^[0-9]{9,18}$'),
    CONSTRAINT ck_bank_account_ifsc   CHECK (ifsc ~ '^[A-Z]{4}0[A-Z0-9]{6}$')
);
