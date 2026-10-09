-- The auth service's own schema. It lives in its own database: a service that
-- holds passwords should not also hold a connection to the trading data.

-- Accounts opened by onboarding and waiting for someone to claim a login.
-- In a real platform this is filled by the onboarding system. Here it is
-- seeded, which stands in for a process the platform does not have yet.
CREATE TABLE IF NOT EXISTS provisioned_account (
    account_id  BIGINT      PRIMARY KEY,
    claimed_by  UUID        NULL,
    claimed_at  TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS credential (
    id            UUID         PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash TEXT         NOT NULL,
    account_id    BIGINT       NOT NULL UNIQUE
                               REFERENCES provisioned_account (account_id),
    roles         TEXT[]       NOT NULL DEFAULT ARRAY['CUSTOMER'],
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_credential_roles_not_empty CHECK (cardinality(roles) > 0)
);

-- The accounts seeded in the trading database, available to be claimed once.
INSERT INTO provisioned_account (account_id) VALUES
    (1), (2), (3), (4), (5), (6), (7), (8), (9), (10)
ON CONFLICT (account_id) DO NOTHING;
