#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Publish one KYC_VERIFIED event by hand, standing in for KYC until it
# exists. The auth service consumes it, provisions the account, and the
# activation chain takes over.
#
#   scripts/publish-kyc-verified.sh <clientId>
#
# The client must exist in the trading database with a client_profile
# row, or the activation mailer dead-letters the event for want of an
# address. A client already in provisioned_account publishes nothing
# further: provisioning is once per account.
# ---------------------------------------------------------------------
set -euo pipefail

CLIENT_ID="${1:?usage: $0 <clientId>}"
[[ "$CLIENT_ID" =~ ^[1-9][0-9]*$ ]] || { echo "clientId must be a positive integer" >&2; exit 2; }

KAFKA_CONTAINER="${KAFKA_CONTAINER:-fauxnance-kafka}"
KAFKA_BROKER_INTERNAL="${KAFKA_BROKER_INTERNAL:-kafka:29092}"

command -v docker >/dev/null || { echo "need docker on PATH" >&2; exit 2; }

if [ -r /proc/sys/kernel/random/uuid ]; then
  EVENT_ID="$(cat /proc/sys/kernel/random/uuid)"
else
  EVENT_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
fi
EVENT_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

ENVELOPE="{\"eventId\":\"${EVENT_ID}\",\"eventType\":\"KYC_VERIFIED\",\"eventTime\":\"${EVENT_TIME}\",\"source\":\"manual-kyc\",\"schemaVersion\":1,\"payload\":{\"clientId\":${CLIENT_ID}}}"

printf '%s\t%s\n' "$CLIENT_ID" "$ENVELOPE" | docker exec -i "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server "$KAFKA_BROKER_INTERNAL" --topic kyc-events \
  --property parse.key=true --property key.separator=$'\t'

echo "published KYC_VERIFIED for client ${CLIENT_ID} (eventId ${EVENT_ID})"
