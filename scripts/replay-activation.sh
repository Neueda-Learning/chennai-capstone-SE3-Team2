#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Re-send a customer's activation email.
#
#   scripts/replay-activation.sh <clientId>
#
# For when an email was lost, expired, or its event was dead-lettered and
# the cause is now fixed. Writes a fresh ACCOUNT_PROVISIONED row to the
# auth service's outbox; the relay publishes it within seconds, and the
# activation mailer mints a new token -- revoking the old link -- and
# sends a new email. New eventId, so the mailer's duplicate check does
# not swallow it.
#
# Refuses an account that is not provisioned or already has a login.
# ---------------------------------------------------------------------
set -euo pipefail

CLIENT_ID="${1:?usage: $0 <clientId>}"
[[ "$CLIENT_ID" =~ ^[1-9][0-9]*$ ]] || { echo "clientId must be a positive integer" >&2; exit 2; }

AUTH_DB_CONTAINER="${AUTH_DB_CONTAINER:-fauxnance-auth-postgres}"
AUTH_DB_USER="${AUTH_DB_USER:-auth}"
AUTH_DB_NAME="${AUTH_DB_NAME:-auth}"

command -v docker >/dev/null || { echo "need docker on PATH" >&2; exit 2; }

# The envelope is built in SQL, so its eventId and eventTime come from the
# same clock and generator every other outbox row uses. One statement: it
# inserts only when the account is provisioned and unclaimed.
INSERTED="$(docker exec -i "$AUTH_DB_CONTAINER" psql -U "$AUTH_DB_USER" -d "$AUTH_DB_NAME" \
  -v ON_ERROR_STOP=1 -qtA -v client_id="$CLIENT_ID" <<'SQL'
WITH target AS (
    SELECT account_id FROM provisioned_account
     WHERE account_id = :client_id AND claimed_by IS NULL
), ev AS (
    SELECT gen_random_uuid() AS event_id, account_id FROM target
)
INSERT INTO outbox_event (event_id, topic, message_key, envelope)
SELECT event_id, 'account-provisioning', account_id::text,
       jsonb_build_object(
           'eventId', event_id,
           'eventType', 'ACCOUNT_PROVISIONED',
           'eventTime', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'source', 'auth-service',
           'schemaVersion', 1,
           'payload', jsonb_build_object('clientId', account_id))
  FROM ev
RETURNING event_id;
SQL
)"

if [ -z "$INSERTED" ]; then
  echo "client ${CLIENT_ID} is not provisioned, or already has a login: nothing queued" >&2
  exit 1
fi
echo "queued ACCOUNT_PROVISIONED for client ${CLIENT_ID} (eventId ${INSERTED}); the outbox relay sends it within seconds"
