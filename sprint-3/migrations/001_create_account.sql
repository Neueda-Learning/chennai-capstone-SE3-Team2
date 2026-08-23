CREATE TABLE account (
    account_id BIGSERIAL PRIMARY KEY,
    holder_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL,
    demat_id VARCHAR(50) NOT NULL UNIQUE,
    pan VARCHAR(10) NOT NULL UNIQUE,

    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    account_state VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_account_state
        CHECK (account_state IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),

    CONSTRAINT chk_account_balance
        CHECK (balance >= 0),

    CONSTRAINT chk_account_version
        CHECK (version >= 0)
);