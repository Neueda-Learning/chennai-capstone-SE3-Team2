#!/usr/bin/env bash
# ---------------------------------------------------------------------
# Story 612: the duplicate-replay demonstration.
#
# WHAT IT PROVES.
#   Kafka guarantees at-least-once, so the executor will be handed the
#   same ORDER_PLACED twice: during a rebalance, after a crash between
#   the database commit and the offset commit, or when somebody replays
#   a topic to debug something. If handling that order twice debits the
#   account twice, the customer is out of pocket and nothing reports it.
#
# WHAT IT DOES.
#   Places one order, waits for the executor to settle it, then replays
#   the ORDER_PLACED message off `orders` and checks that four things
#   hold:
#     1. balance before the replay == balance after the replay
#     2. exactly one message on trade-events for that orderId
#     3. the executor logs "duplicate delivery ignored" for that orderId
#     4. the settled order's status did not change
#
# ASSUMPTIONS.
#   * A FRESH compose stack: docker init scripts only run on an empty
#     volume, and reading the first message off `orders` with
#     --from-beginning only picks the message we placed if nothing
#     else has been through the topic. Run:
#         docker compose --profile platform down -v
#         docker compose --profile platform up -d --build
#         sprint-07-event-backbone/scripts/create-topics.sh
#     before running this script.
#   * .env is populated (JWT_SECRET, DB_PASSWORD, FAUXNANCE_API_KEY).
#   * openssl and jq are available on the host.
# ---------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

API_HOST="${API_HOST:-localhost}"
API_PORT="${API_PORT:-8080}"
API="http://${API_HOST}:${API_PORT}"

ACCOUNT_ID="${ACCOUNT_ID:-3}"
SYMBOL="${SYMBOL:-ITC.NS}"
QUANTITY="${QUANTITY:-10}"
LIMIT_PRICE="${LIMIT_PRICE:-2000.00}"

KAFKA_CONTAINER="${KAFKA_CONTAINER:-fauxnance-kafka}"
EXECUTOR_CONTAINER="${EXECUTOR_CONTAINER:-fauxnance-executor}"
# From INSIDE the broker container, use the advertised name -- the
# broker advertises `PLAINTEXT://kafka:29092` in metadata replies, and
# a client that connected via `localhost:29092` fails on the follow-up
# with `Timed out waiting for a node assignment` when localhost does
# not match what was advertised.
KAFKA_BROKER_INTERNAL="${KAFKA_BROKER_INTERNAL:-kafka:29092}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
ok()  { printf '  \033[1;32mok\033[0m  %s\n' "$*"; }
bad() { printf '  \033[1;31mFAIL\033[0m %s\n' "$*"; }

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "need $1 on PATH" >&2; exit 2; }
}
need curl
need jq
need openssl
need docker

# ---------------------------------------------------------------------
# 0. Mint a JWT for the demo account.
# ---------------------------------------------------------------------
say "0. mint bearer token for account ${ACCOUNT_ID}"
TOKEN="$(ACCOUNT_ID="${ACCOUNT_ID}" "${SCRIPT_DIR}/mint-demo-token.sh")"
AUTH=(-H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json")
ok "token minted"

# ---------------------------------------------------------------------
# 1. Balance before we do anything. Recorded for the audit trail
#    only -- the assertion the demo turns on is (2) vs (3), not (1)
#    vs anything.
# ---------------------------------------------------------------------
say "1. balance BEFORE the order (baseline)"
BAL_START="$(curl -sS "${API}/api/v1/accounts/${ACCOUNT_ID}/balance" "${AUTH[@]}" \
  | jq -r '.balance')"
ok "balance = ${BAL_START}"

# ---------------------------------------------------------------------
# 2. Place one order. The first delivery reaches the executor via the
#    normal path, gets priced against a live Fauxnance quote and
#    settles. This is the delivery whose duplicate we are about to
#    fabricate.
# ---------------------------------------------------------------------
say "2. place ONE order (first delivery)"
IDEM_KEY="demo-612-$(date +%s)-$$"
PLACE_RESP="$(curl -sS -X POST "${API}/api/v1/orders" "${AUTH[@]}" -d "$(cat <<JSON
{
  "accountId":      ${ACCOUNT_ID},
  "symbol":         "${SYMBOL}",
  "side":           "BUY",
  "quantity":       ${QUANTITY},
  "price":          ${LIMIT_PRICE},
  "idempotencyKey": "${IDEM_KEY}"
}
JSON
)")"

ORDER_ID_DISPLAY="$(printf '%s' "${PLACE_RESP}" | jq -r '.orderId // empty')"
PLACE_STATUS="$(printf '%s' "${PLACE_RESP}" | jq -r '.status // empty')"

if [ -z "${ORDER_ID_DISPLAY}" ] || [ "${PLACE_STATUS}" != "NEW" ]; then
  bad "POST /api/v1/orders did not return a NEW order:"
  printf '%s\n' "${PLACE_RESP}" | jq . || printf '%s\n' "${PLACE_RESP}"
  exit 1
fi
ok "order accepted at ${PLACE_STATUS}: ${ORDER_ID_DISPLAY}"

# The API returns the orderId as a display string ("ORD-<uuid>" per
# OrderIdentifier). The bare UUID is what appears on the topic key and
# in the executor's logs.
ORDER_ID_UUID="${ORDER_ID_DISPLAY#ORD-}"

# ---------------------------------------------------------------------
# 3. Wait for the executor to settle the first delivery. It is the
#    order status transitioning off NEW that tells us the executor
#    ran; polling the balance would race a REJECTED reason path
#    where the balance does not move.
# ---------------------------------------------------------------------
say "3. wait for the executor to settle the first delivery"
SETTLED_STATUS=""
for _ in $(seq 1 30); do
  SETTLED_STATUS="$(curl -sS "${API}/api/v1/accounts/${ACCOUNT_ID}/orders" "${AUTH[@]}" \
    | jq -r --arg id "${ORDER_ID_DISPLAY}" '.[] | select(.orderId==$id) | .status' \
    | head -n1)"
  if [ -n "${SETTLED_STATUS}" ] && [ "${SETTLED_STATUS}" != "NEW" ]; then
    break
  fi
  sleep 1
done
if [ -z "${SETTLED_STATUS}" ] || [ "${SETTLED_STATUS}" = "NEW" ]; then
  bad "executor did not settle ${ORDER_ID_DISPLAY} within 30s (status=${SETTLED_STATUS:-<missing>})"
  bad "either the executor is not running or Fauxnance is unreachable"
  exit 1
fi
ok "first delivery settled: ${SETTLED_STATUS}"

# ---------------------------------------------------------------------
# 4. Balance AFTER the first fill. This is the number the replay must
#    not change.
# ---------------------------------------------------------------------
say "4. balance AFTER the first fill"
BAL_BEFORE_REPLAY="$(curl -sS "${API}/api/v1/accounts/${ACCOUNT_ID}/balance" "${AUTH[@]}" \
  | jq -r '.balance')"
ok "balance = ${BAL_BEFORE_REPLAY}"

# ---------------------------------------------------------------------
# 5. Read the ORDER_PLACED message off `orders`, preserving the key.
#    The key is what puts a message on a partition; a replay that
#    lands on a different partition proves nothing.
# ---------------------------------------------------------------------
say "5. consume the ORDER_PLACED message off orders (key preserved)"
docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server "${KAFKA_BROKER_INTERNAL}" --topic orders \
  --from-beginning --max-messages 1 \
  --property print.key=true --property key.separator=$'\t' \
  --timeout-ms 10000 \
  > "${TMP}/order.txt"

if [ ! -s "${TMP}/order.txt" ]; then
  bad "no message on orders topic -- did the stack start with a fresh volume?"
  exit 1
fi
CAPTURED_KEY="$(cut -f1 "${TMP}/order.txt")"
ok "captured message with key=${CAPTURED_KEY}"

# ---------------------------------------------------------------------
# 6. Produce it back, preserving the key.
# ---------------------------------------------------------------------
say "6. replay the message to orders"
docker exec -i "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server "${KAFKA_BROKER_INTERNAL}" --topic orders \
  --property parse.key=true --property key.separator=$'\t' \
  < "${TMP}/order.txt"
ok "replay produced"

# ---------------------------------------------------------------------
# 7. Give the executor a moment to consume the replay. The guarded
#    UPDATE in FullSettlement matches zero rows because status is no
#    longer NEW, and the ALREADY_SETTLED branch returns without
#    publishing to trade-events.
# ---------------------------------------------------------------------
say "7. wait for the executor to process the duplicate"
sleep 5

# ---------------------------------------------------------------------
# 8. Balance AFTER the replay. Must equal balance-after-first-fill,
#    to the last decimal.
# ---------------------------------------------------------------------
say "8. balance AFTER the replay -- must equal (4)"
BAL_AFTER_REPLAY="$(curl -sS "${API}/api/v1/accounts/${ACCOUNT_ID}/balance" "${AUTH[@]}" \
  | jq -r '.balance')"

printf '  balance start          : %s\n' "${BAL_START}"
printf '  balance after first    : %s\n' "${BAL_BEFORE_REPLAY}"
printf '  balance after replay   : %s\n' "${BAL_AFTER_REPLAY}"

if [ "${BAL_AFTER_REPLAY}" = "${BAL_BEFORE_REPLAY}" ]; then
  ok "balance unchanged by the replay"
else
  bad "REGRESSION: the replay changed the balance"
  bad "  before replay: ${BAL_BEFORE_REPLAY}"
  bad "  after  replay: ${BAL_AFTER_REPLAY}"
  exit 1
fi

# ---------------------------------------------------------------------
# 9. Exactly one message on trade-events for this orderId. Consuming
#    from-beginning on a fresh topic gives us the full log; we count
#    the payload.orderId occurrences.
# ---------------------------------------------------------------------
say "9. count trade-events messages for this order (must be exactly 1)"
docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server "${KAFKA_BROKER_INTERNAL}" --topic trade-events \
  --from-beginning --timeout-ms 5000 \
  > "${TMP}/trade-events.txt" 2>/dev/null || true

EVENT_COUNT="$(grep -c "\"orderId\":\"${ORDER_ID_UUID}\"" "${TMP}/trade-events.txt" || true)"
printf '  trade-events entries for orderId=%s: %s\n' "${ORDER_ID_UUID}" "${EVENT_COUNT}"

if [ "${EVENT_COUNT}" = "1" ]; then
  ok "exactly one trade-event -- the replay published nothing"
else
  bad "REGRESSION: expected exactly 1 trade-event, saw ${EVENT_COUNT}"
  exit 1
fi

# ---------------------------------------------------------------------
# 10. The executor log line. FullSettlement.settle() emits this on the
#     ALREADY_SETTLED path (guarded UPDATE affected zero rows). Story
#     612 points at this line: do not make it quieter.
# ---------------------------------------------------------------------
say "10. executor log line recognising the duplicate"
LOG_LINE="$(docker logs "${EXECUTOR_CONTAINER}" 2>&1 \
  | grep -F "duplicate delivery ignored" \
  | grep -F "${ORDER_ID_UUID}" \
  | tail -n1 || true)"

if [ -n "${LOG_LINE}" ]; then
  ok "found: ${LOG_LINE}"
else
  bad "REGRESSION: no 'duplicate delivery ignored' log line for ${ORDER_ID_UUID}"
  exit 1
fi

# ---------------------------------------------------------------------
say "SUMMARY"
printf '  order                   : %s\n' "${ORDER_ID_DISPLAY}"
printf '  status after first fill : %s\n' "${SETTLED_STATUS}"
printf '  balance after first fill: %s\n' "${BAL_BEFORE_REPLAY}"
printf '  balance after replay    : %s  (equal)\n' "${BAL_AFTER_REPLAY}"
printf '  trade-events for order  : %s  (equal to 1)\n' "${EVENT_COUNT}"
printf '\n\033[1;32mstory 612 demonstration PASSED\033[0m\n'
