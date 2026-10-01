-- One-time activation tokens, stored as a SHA-256 hash like refresh_token.
-- Read access to this table is then not account takeover: what is here cannot
-- be presented to the service.
CREATE TABLE IF NOT EXISTS activation_token (
    token_hash  CHAR(64)     PRIMARY KEY,
    client_id   BIGINT       NOT NULL REFERENCES provisioned_account (account_id),
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    -- Set when the token registers a login. A token is good for one use.
    used_at     TIMESTAMPTZ  NULL,
    -- Set when a newer token is minted for the same client, so only the latest
    -- email's link works.
    revoked_at  TIMESTAMPTZ  NULL
);

CREATE INDEX IF NOT EXISTS ix_activation_token_live
    ON activation_token (client_id) WHERE used_at IS NULL AND revoked_at IS NULL;
