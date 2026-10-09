-- =====================================================================
-- 011_preferences.sql
--
-- Sprint 10, the preferences module (com.yellow.trade.preferences).
--
-- One row per customer, written the first time they save Settings. No row
-- means the documented defaults (decision log 0004): their own account,
-- the dashboard, and email. The contact detail is NOT here: email goes to
-- client_profile.email, read when a message is sent (decision log 0003).
--
-- default_account_id must be the customer's own; with one account per
-- login that is client_id, and the CHECK says so rather than trusting the
-- service alone.
-- =====================================================================

CREATE TABLE IF NOT EXISTS pref_preference (
    client_id          INTEGER     PRIMARY KEY
                                   REFERENCES client_account (client_id),
    default_account_id INTEGER     NOT NULL
                                   REFERENCES client_account (client_id),
    landing_screen     VARCHAR(20) NOT NULL,
    alert_channel      VARCHAR(10) NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_pref_landing_screen
        CHECK (landing_screen IN ('dashboard', 'orders', 'holdings', 'market-watch')),
    CONSTRAINT ck_pref_alert_channel
        CHECK (alert_channel IN ('EMAIL', 'IN_APP')),
    CONSTRAINT ck_pref_default_account_is_own
        CHECK (default_account_id = client_id)
);
