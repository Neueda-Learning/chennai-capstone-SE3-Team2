#!/usr/bin/env bash
set -euo pipefail

BROKER="${BROKER:-localhost:9092}"
KAFKA_TOPICS="${KAFKA_TOPICS_BIN:-kafka-topics.sh}"

create() {
  local topic="$1" partitions="$2" retention_ms="$3"
  "$KAFKA_TOPICS" --bootstrap-server "$BROKER" --create --if-not-exists \
    --topic "$topic" --partitions "$partitions" --replication-factor 1 \
    --config retention.ms="$retention_ms" --config cleanup.policy=delete
  echo "ensured topic: $topic (partitions=$partitions, retention.ms=$retention_ms)"
}

create orders         3 604800000    # 7 days
create trade-events   3 2592000000   # 30 days
create market-data    6 86400000     # 1 day

# Onboarding. Keyed by client id; low volume, so 3 partitions like orders.
# kyc-events: KYC -> auth. account-provisioning: auth -> the activation mailer.
create kyc-events              3 604800000    # 7 days
create account-provisioning    3 604800000    # 7 days

create orders.DLT         1 604800000
create trade-events.DLT   1 2592000000
create market-data.DLT    1 86400000
create kyc-events.DLT              1 604800000
create account-provisioning.DLT    1 604800000

echo "all ten topics ensured on $BROKER"
