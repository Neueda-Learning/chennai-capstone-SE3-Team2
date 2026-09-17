# Sprint 7 — event backbone

The three topics, the Trade REST API that produces `ORDER_PLACED`, the
Trade Executor that consumes it and publishes to `trade-events`, the
market-data poller that shares the executor's process, and the
analytics pipeline that reads Postgres into DuckDB.

This file is the review companion. It tells you where things live,
where the decisions are written down, and how to give the two
demonstrations the sprint is assessed on: the duplicate replay
(story 612) and the retry/dead-letter split (story 613).

## Layout

| Where | What |
|---|---|
| `executor/` | Trade Executor. Consumes `orders`, prices against Fauxnance, settles, publishes to `trade-events`. Hosts the market-data poller. |
| `etl/` | Python 3.12 analytics pipeline. Reads Postgres, writes DuckDB. Story 615. |
| `design/kafka.md` | Topic partition counts, key choices, DLT decisions. What we defend at the review. |
| `scripts/create-topics.sh` | Creates the six topics (three + DLTs). Run against a healthy broker. |
| `scripts/duplicate-replay.sh` | Story 612 demonstration, runnable end to end. |
| `scripts/mint-demo-token.sh` | HS256 JWT for the demonstration; reads `JWT_SECRET` from `.env`. |

## Running the stack

Compose lives at the repository root. Everything is in the `platform`
profile so `docker compose up` in isolation starts nothing.

```bash
cp .env.example .env               # set JWT_SECRET, DB_PASSWORD, FAUXNANCE_API_KEY
docker compose --profile platform up -d --build
sprint-07-event-backbone/scripts/create-topics.sh
```

Auto-creation is off on the broker on purpose (`docker-compose.yml:29`):
a producer that writes to a topic nobody created gets an error, not a
silently wrong one-partition topic.

## Story 612 — the duplicate-replay demonstration

**What the review is looking for.** Kafka guarantees at-least-once
delivery, so the executor will be handed the same `ORDER_PLACED`
twice — during a rebalance, after a crash between the DB commit and
the offset commit, or because somebody replayed a topic to debug
something. If handling the order twice debits the account twice, the
customer is out of pocket and nothing reports it. The mechanism that
prevents that is the guarded `UPDATE ... WHERE status = 'NEW'` in
`FullSettlement.settle()`: a second delivery matches zero rows and
the ALREADY_SETTLED path returns without touching cash or publishing
an event.

The demonstration proves it by fabricating the duplicate on demand.

### Prerequisites

- **Fresh stack**: Postgres' `docker-entrypoint-initdb.d/` scripts
  only run on an empty volume, and the demo reads the first message
  off `orders` with `--from-beginning`, so any prior traffic on the
  topic would be picked up instead of ours.

  ```bash
  docker compose --profile platform down -v          # -v destroys volumes
  docker compose --profile platform up -d --build
  sprint-07-event-backbone/scripts/create-topics.sh
  ```

- `.env` populated with `JWT_SECRET`, `DB_PASSWORD`, `FAUXNANCE_API_KEY`.
- `openssl`, `jq`, `curl`, `docker` on `PATH`.

### Runnable sequence

```bash
sprint-07-event-backbone/scripts/duplicate-replay.sh
```

The script exits non-zero on any regression. In sequence it:

1. Mints an HS256 bearer token via `mint-demo-token.sh` — signed with
   `JWT_SECRET`, issuer `auth-service`, `accountId=3`. That is exactly
   what `PostgresSupport.tokenFor()` mints in integration tests, so a
   token minted here is indistinguishable from one a real auth
   service sharing the secret would issue.
2. Reads the baseline balance for the audit trail (`GET /api/v1/accounts/3/balance`).
3. `POST /api/v1/orders` with a BUY that will fill against a live
   Fauxnance quote (`ITC.NS`, 10 shares, generous limit price).
   Answered `NEW`, per story 609 — pricing is the executor's job now.
4. Polls `GET /api/v1/accounts/3/orders` until the order's status
   comes off `NEW`. That is what tells us the executor consumed and
   settled it via the ordinary path; polling the balance instead
   would race a `REJECTED` path where the balance does not move.
5. Records the balance **after the first fill**. This is the number
   the replay must not change.
6. Reads one message off `orders` with the console consumer,
   preserving the key on the `\t` separator so the replay lands on
   the same partition. Console tools run inside the broker
   container (`docker exec fauxnance-kafka /opt/kafka/bin/...`) so
   there is nothing extra to install on the host.
7. Produces the same message back to `orders`, key preserved.
8. Sleeps a few seconds — the executor consumer runs on its own
   thread with a bounded poll, and needs a moment to pick up the
   replayed record.
9. Reads the balance again and asserts it equals the one from (5).
10. Reads `trade-events` from the beginning and asserts the count of
    payload entries carrying the order's UUID is exactly **1**.
    The first delivery published a `FILLED`; the ALREADY_SETTLED
    path in `FullSettlement.java:73` deliberately publishes nothing.
11. Greps `docker logs fauxnance-executor` for the line
    `duplicate delivery ignored: order <uuid> on account 3 was
    already settled, nothing written and nothing published`. That is
    what the executor prints on the zero-rows-affected branch, and
    it is what the review is asked to see.

### The four pieces of evidence

| # | What | Where the script asserts it |
|---|---|---|
| 1 | balance before the replay | step 5 records; step 8 compares |
| 2 | balance after the replay — **identical** | step 8 compares |
| 3 | executor log line recognising the duplicate | step 11 greps |
| 4 | no second message on `trade-events` | step 10 counts |

### If the demo fails

- `executor did not settle within 30s` → `FAUXNANCE_API_KEY` is
  wrong or Fauxnance is unreachable. `docker logs fauxnance-executor`
  will show `NO_PRICE` or an HTTP error from the quote client.
- `no message on orders topic` → the volume was not reset before the
  run. Repeat the prerequisites.
- `expected exactly 1 trade-event, saw 2` → the replay published,
  which means the guarded UPDATE matched a row. Either the first
  delivery had not settled yet at step 7 (increase the sleep at
  step 8) or the guard in `FullSettlement.settle()` regressed.
- `no 'duplicate delivery ignored' log line` → the executor either
  did not consume the replay, or `FullSettlement`'s log line was
  changed. Story 612 explicitly points at that line; keep it.

## Story 613 — retry vs dead-letter

The executor's Kafka consumer is wrapped by a Spring `DefaultErrorHandler`
that classifies exceptions into two buckets. **Poison** exceptions go
to `orders.DLT` on the first attempt; **transient** exceptions retry
with exponential backoff, then dead-letter once the budget is spent.
The classification and the retry budget are settled at the review; the
code is at `executor/src/main/java/com/yellow/executor/consume/ConsumerErrorHandling.java`.

### The split

| Class | Examples | Handling |
|---|---|---|
| Poison — will never succeed | Malformed JSON, missing `orderId`, an `orderId` not in Postgres, unexpected `eventType` | DLT on the first attempt |
| Transient — will succeed later | Broker briefly unreachable, lost DB connection, exhausted optimistic-lock budget | 3 retries with backoff (500ms → 2s → 8s, capped at 10s), then DLT |

**A Fauxnance outage belongs in neither row.** It is retried inside
`FauxnanceQuoteClient`, and if the retry budget there is spent, the
order is settled as `REJECTED` with reason `NO_PRICE`
(`OrderExecutionService.java:142-151`). A price feed being down is a
business outcome, not a message-processing failure — dead-lettering
the order would leave it at `NEW` for ever, and retrying it inside
the consumer would spin the partition against a service that is not
coming back on that timescale.

### Headers on every dead-lettered record

| Header | Value |
|---|---|
| `x-failure-reason` | The exception message |
| `x-failure-class` | `POISON` or `TRANSIENT` |
| `x-failure-class-fqcn` | The exception class name — enough to locate the code without a stacktrace |
| `x-original-topic` | The topic the record came from |
| `x-original-partition` | Its partition |
| `x-original-offset` | Its offset |
| `x-attempt-count` | `1` for poison, `4` for transient (initial + 3 retries) |
| `x-failed-at` | RFC 3339 UTC |

`x-*` rather than Spring's default `kafka_dlt-*` because the analytics
loader (story 615) reads these from Python, and `x-*` keeps the header
names portable and self-describing.

### Showing it works

Two integration tests, both under `executor/src/test/java/com/yellow/executor/consume/`:

- `ConsumerErrorHandlingClassifierTest` — the classifier and the
  header enricher, in isolation.
- `ConsumerErrorHandlingIntegrationTest` — three end-to-end paths
  against an embedded Kafka broker:
  1. a malformed JSON record lands on `orders.DLT` after **one**
     attempt (no retries).
  2. a transient failure is retried and then processed cleanly, with
     no DLT record.
  3. a poison record and a good record on the **same partition
     key** are both handled: the poison one dead-letters and the good
     one still gets processed. That is the "a poison message must
     not block the partition" property the review checks.

## The rest of the review checklist

Straight from the brief. Bring these on demand:

- the running stack
- `docker exec fauxnance-kafka /opt/kafka/bin/kafka-topics.sh
  --describe --bootstrap-server localhost:29092` with `design/kafka.md`
  open beside them
- one order traced end to end: HTTP request → `orders` topic →
  committed rows in Postgres → `trade-events` published
- the duplicate replay (`scripts/duplicate-replay.sh`), performed live
- a quote arriving on `market-data` inside one polling interval, with
  several distinct symbols in the cycle
- a bad row landing in the dead-letter path rather than in `FACT_TRADES`
- a second load over the same data that adds nothing
- the fill rule and the reasoning behind it
- the quota arithmetic for our configuration (see `.env.example`)
- the SonarQube dashboard
- `git log` showing the characterisation tests arriving before the
  Sprint 6 change
