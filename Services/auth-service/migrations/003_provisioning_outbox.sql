-- Events waiting to be published, written in the SAME transaction as the change
-- they announce. A rolled-back provisioning therefore leaves no row here and
-- publishes nothing; a committed one always has a row, and the relay keeps
-- trying until the broker takes it. The broker being down delays an activation
-- email; it never loses one and never fails a provisioning.
CREATE TABLE IF NOT EXISTS outbox_event (
    event_id      UUID         PRIMARY KEY,
    topic         VARCHAR(249) NOT NULL,
    message_key   VARCHAR(64)  NOT NULL,
    -- The whole envelope, exactly as it goes on the wire.
    envelope      JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ  NULL,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    TEXT         NULL
);

CREATE INDEX IF NOT EXISTS ix_outbox_event_unpublished
    ON outbox_event (created_at) WHERE published_at IS NULL;
