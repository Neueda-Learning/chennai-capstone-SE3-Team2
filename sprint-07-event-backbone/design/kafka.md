# Kafka topics: decisions and justification

## How the topics are created
One command, safe to re-run against an empty broker: `executor/scripts/create-topics.sh`
Run it against the `kafka` service after `docker compose --profile platform up -d kafka` reports healthy.

## Why the key decides the partition, and the partition decides ordering
Kafka only orders messages within one partition, never across partitions.
The key picks the partition via a hash, so the key you choose is the set of
messages you're promising to keep in order relative to each other.

## orders / trade-events: keyed by accountId
Two orders on one account must execute in the order they were accepted — a
sell can depend on the buy that funded it. Keying by accountId keeps one
account's messages on one partition, strictly ordered. Different accounts
have no ordering requirement between them.

Keying by orderId instead would hash every message to essentially its own
partition (each order ID is unique) — an account's own history would arrive
out of order at the executor.

## market-data: keyed by symbol
Ordering matters per instrument (never see an older AAPL quote after a
newer one); different symbols are independent.

## Partition counts
- orders: 3 — lets up to 3 trade-executor instances each own a partition;
  a consumer group can't usefully exceed the partition count.
- trade-events: 3 — same key, same volume profile as orders.
- market-data: 6 — higher message rate, more consumer groups read it in
  parallel (portfolio, watchlist, advice, strategy services).

Partitions can be increased, never decreased. Increasing rehashes keys, so
an account's or symbol's history splits across partitions from that point
on — anything relying on "this account's history is one partition" (our
guarded UPDATE) needs re-checking after a resize.

## Dead-letter topics
orders.DLT / trade-events.DLT / market-data.DLT — 1 partition each (no
ordering needed for investigation), retention matching the parent topic,
original message as the value, failure reason in a header.
