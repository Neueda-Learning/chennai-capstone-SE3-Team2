#!/usr/bin/env bash
# ---------------------------------------------------------------------
# TESTING AND DEMONSTRATION. Publishes one trade-events message by hand,
# in the envelope contracts/kafka-topics.md fixes, keyed by the account:
# what the executor publishes when it fills or rejects an order, and the
# Trade REST API when one is cancelled. For the Sprint 10 harness's
# trade-event probe, and for showing that a REPLAY sends nothing twice:
# pass the same eventId again.
#
#   scripts/publish-trade-event.sh FILLED|REJECTED|CANCELLED <accountId> <symbol> \
#       <BUY|SELL> <quantity> <price> [eventId]
#
# FILLED carries the price as the executed price; REJECTED and CANCELLED
# carry none, with a reason. cashDelta, positionQuantityAfter and
# averageCostAfter are illustrative: nothing in the trading database
# changes, and a consumer must not take them as the account's state.
# ---------------------------------------------------------------------
set -euo pipefail

usage="usage: $0 FILLED|REJECTED|CANCELLED <accountId> <symbol> <BUY|SELL> <quantity> <price> [eventId]"
OUTCOME="${1:?$usage}"
ACCOUNT_ID="${2:?$usage}"
SYMBOL="${3:?$usage}"
SIDE="${4:?$usage}"
QUANTITY="${5:?$usage}"
PRICE="${6:?$usage}"
EVENT_ID="${7:-}"

case "$OUTCOME" in FILLED|REJECTED|CANCELLED) ;; *) echo "$usage" >&2; exit 2 ;; esac
case "$SIDE" in BUY|SELL) ;; *) echo "side must be BUY or SELL" >&2; exit 2 ;; esac
[[ "$ACCOUNT_ID" =~ ^[1-9][0-9]*$ ]] || { echo "accountId must be a positive integer" >&2; exit 2; }
[[ "$QUANTITY" =~ ^[1-9][0-9]*$ ]] || { echo "quantity must be a positive integer" >&2; exit 2; }
[[ "$PRICE" =~ ^[0-9]+(\.[0-9]{1,4})?$ ]] || { echo "price must be a number" >&2; exit 2; }
# In a variable: a literal & in a [[ =~ ]] pattern is a syntax error. M&M.NS is a symbol.
SYMBOL_PATTERN='^[A-Za-z0-9.:&-]{1,30}$'
[[ "$SYMBOL" =~ $SYMBOL_PATTERN ]] || { echo "symbol looks wrong" >&2; exit 2; }

KAFKA_CONTAINER="${KAFKA_CONTAINER:-fauxnance-kafka}"
KAFKA_BROKER_INTERNAL="${KAFKA_BROKER_INTERNAL:-kafka:29092}"
command -v docker >/dev/null || { echo "need docker on PATH" >&2; exit 2; }

uuid() {
  if [ -r /proc/sys/kernel/random/uuid ]; then cat /proc/sys/kernel/random/uuid; else uuidgen | tr '[:upper:]' '[:lower:]'; fi
}
EVENT_ID="${EVENT_ID:-$(uuid)}"
ORDER_ID="$(uuid)"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

case "$OUTCOME" in
  FILLED)    TYPE=ORDER_FILLED;    EXECUTED="$PRICE"; REASON=null ;;
  REJECTED)  TYPE=ORDER_REJECTED;  EXECUTED=null;     REASON='"PRICE_NOT_MET"' ;;
  CANCELLED) TYPE=ORDER_CANCELLED; EXECUTED=null;     REASON='"CANCELLED_BY_CUSTOMER"' ;;
esac
if [ "$OUTCOME" = FILLED ]; then
  CASH_DELTA="$(awk -v q="$QUANTITY" -v p="$PRICE" -v s="$SIDE" 'BEGIN { printf "%.2f", (s == "BUY" ? -1 : 1) * q * p }')"
else
  CASH_DELTA=0
fi

ENVELOPE="{\"eventId\":\"${EVENT_ID}\",\"eventType\":\"${TYPE}\",\"eventTime\":\"${NOW}\",\"source\":\"manual-trade-event\",\"schemaVersion\":1,\"payload\":{\"orderId\":\"${ORDER_ID}\",\"accountId\":${ACCOUNT_ID},\"symbol\":\"${SYMBOL}\",\"side\":\"${SIDE}\",\"quantity\":${QUANTITY},\"price\":${PRICE},\"executedPrice\":${EXECUTED},\"status\":\"${OUTCOME}\",\"reason\":${REASON},\"cashDelta\":${CASH_DELTA},\"positionQuantityAfter\":0,\"averageCostAfter\":${PRICE},\"executedOn\":\"${NOW}\"}}"

printf '%s\t%s\n' "$ACCOUNT_ID" "$ENVELOPE" | docker exec -i "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server "$KAFKA_BROKER_INTERNAL" --topic trade-events \
  --property parse.key=true --property key.separator=$'\t'

echo "published ${TYPE} for account ${ACCOUNT_ID}, ${SIDE} ${QUANTITY} ${SYMBOL} (eventId ${EVENT_ID})"
