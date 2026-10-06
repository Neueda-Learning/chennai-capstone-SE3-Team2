#!/usr/bin/env bash
# ---------------------------------------------------------------------
# TESTING AND DEMONSTRATION. Publishes one QUOTE on market-data by hand,
# in the envelope contracts/kafka-topics.md fixes, keyed by the symbol:
# what the executor's poller publishes every interval. For the Sprint 10
# harness's market-data probe, and for showing a price alert cross:
# publish a price past an ACTIVE alert's threshold and the alert fires.
# Pass the same eventId again to show a replay fires nothing twice.
#
#   scripts/publish-market-data.sh <symbol> <price> [eventId]
#
# The bid and ask sit a tenth of a per cent either side of the price, as
# Fauxnance models them; previousClose is the price, so the day's change
# reads 0. Nothing in the trading database changes.
# ---------------------------------------------------------------------
set -euo pipefail

usage="usage: $0 <symbol> <price> [eventId]"
SYMBOL="${1:?$usage}"
PRICE="${2:?$usage}"
EVENT_ID="${3:-}"

# In a variable: a literal & in a [[ =~ ]] pattern is a syntax error. M&M.NS is a symbol.
SYMBOL_PATTERN='^[A-Za-z0-9.:&-]{1,30}$'
[[ "$SYMBOL" =~ $SYMBOL_PATTERN ]] || { echo "symbol looks wrong" >&2; exit 2; }
[[ "$PRICE" =~ ^[0-9]+(\.[0-9]{1,4})?$ ]] || { echo "price must be a number" >&2; exit 2; }

KAFKA_CONTAINER="${KAFKA_CONTAINER:-fauxnance-kafka}"
KAFKA_BROKER_INTERNAL="${KAFKA_BROKER_INTERNAL:-kafka:29092}"
command -v docker >/dev/null || { echo "need docker on PATH" >&2; exit 2; }

if [ -z "$EVENT_ID" ]; then
  if [ -r /proc/sys/kernel/random/uuid ]; then EVENT_ID="$(cat /proc/sys/kernel/random/uuid)"; else EVENT_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"; fi
fi
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
BID="$(awk -v p="$PRICE" 'BEGIN { printf "%.2f", p * 0.999 }')"
ASK="$(awk -v p="$PRICE" 'BEGIN { printf "%.2f", p * 1.001 }')"

ENVELOPE="{\"eventId\":\"${EVENT_ID}\",\"eventType\":\"QUOTE\",\"eventTime\":\"${NOW}\",\"source\":\"manual-market-data\",\"schemaVersion\":1,\"payload\":{\"symbol\":\"${SYMBOL}\",\"price\":${PRICE},\"bid\":${BID},\"ask\":${ASK},\"currency\":\"INR\",\"change\":0,\"changePercent\":0,\"previousClose\":${PRICE},\"marketState\":\"open\",\"stale\":false,\"quoteAsOf\":\"${NOW}\"}}"

printf '%s\t%s\n' "$SYMBOL" "$ENVELOPE" | docker exec -i "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server "$KAFKA_BROKER_INTERNAL" --topic market-data \
  --property parse.key=true --property key.separator=$'\t'

echo "published QUOTE ${SYMBOL} at ${PRICE} (eventId ${EVENT_ID})"
