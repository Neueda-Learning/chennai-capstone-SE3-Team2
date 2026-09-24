-- Refresh tokens, stored as a hash. Read access to this database is then not
-- session takeover: what is here cannot be presented to the service.
CREATE TABLE IF NOT EXISTS refresh_token (
    token_hash    CHAR(64)     PRIMARY KEY,
    credential_id UUID         NOT NULL REFERENCES credential (id) ON DELETE CASCADE,
    issued_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    -- Set when this token is exchanged. A second presentation of an already
    -- exchanged token is the alarm: either a client repeated a request or
    -- somebody stole a token, and the service cannot tell which.
    exchanged_at  TIMESTAMPTZ  NULL
);

CREATE INDEX IF NOT EXISTS ix_refresh_token_credential
    ON refresh_token (credential_id) WHERE exchanged_at IS NULL;
