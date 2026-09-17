# Sprint 7 — team TODO

Everything except stories **610** (Trade Executor) and **614** (market-data
poller), which are Shiva's and are being built on
`feature/sprint7/610-614`.

This file exists so the other stories integrate with 610/614 without a
merge-day surprise. **Section 1 is the shared contract.** If you are about to
invent a field name, a topic name, a reason code or a group id, it is probably
already fixed below. If it is genuinely missing, raise it in the team channel
rather than picking one — the whole point is that six people pick the same one.

---

## 1. The shared contract — read this before you write anything

### 1.1 Topics (done, story 607)

| Topic | Key | Partitions | Retention |
|---|---|---|---|
| `orders` | `accountId` as a **string** | 3 | 7 days |
| `trade-events` | `accountId` as a **string** | 3 | 30 days |
| `market-data` | `symbol` | 6 | 1 day |
| `orders.DLT`, `trade-events.DLT`, `market-data.DLT` | — | 1 | matches parent |

Created by `sprint-07-event-backbone/scripts/create-topics.sh`. Auto-creation
is **off** on the broker, so a producer writing to a topic nobody created gets
an error rather than a silently wrong one-partition topic.

The key is a **string**, always. `accountId` is a `BIGINT` in the payload and
`String.valueOf(accountId)` as the message key. Producing key `1` as an integer
and key `"1"` as a string puts the same account on two different partitions.

### 1.2 The envelope — identical on all three topics

```json
{
  "eventId":       "uuid, unique per message",
  "eventType":     "ORDER_PLACED | ORDER_FILLED | ORDER_REJECTED | ORDER_CANCELLED | QUOTE",
  "eventTime":     "RFC 3339 UTC, when the PRODUCER created the event",
  "source":        "trade-api | trade-executor | market-poller",
  "schemaVersion": 1,
  "payload":       { }
}
```

Rules that are not negotiable:

- **Consumers ignore unknown fields.** Jackson: `FAIL_ON_UNKNOWN_PROPERTIES =
  false` on every consumer. A consumer that throws on a new field turns an
  additive change into a platform outage. Note this is the **opposite** of
  `trade-api`'s HTTP setting, which deliberately fails on unknown properties
  because the OpenAPI contract says `additionalProperties: false`. Different
  boundary, different rule — do not "fix" one to match the other.
- Adding an optional field does **not** bump `schemaVersion`. Removing,
  renaming or retyping one does.
- `source` names the *component*, not the container. Quotes carry
  `market-poller` even though the poller runs inside the executor process.

### 1.3 `ORDER_PLACED` payload — story 609 produces, 610 consumes

```json
{
  "orderId":        "uuid, matches orders.order_id",
  "accountId":      1,
  "symbol":         "INFY.NS",
  "side":           "BUY",
  "quantity":       100,
  "price":          233.00,
  "idempotencyKey": "the client key that was accepted",
  "createdOn":      "2026-09-28T09:14:22Z"
}
```

`price` is the **limit price**, two decimal places. `symbol` is what
`COALESCE(equity.ticker, mutual_fund.scheme_code)` returns — the same string
the customer posted.

### 1.4 `trade-events` payload — story 611 produces

```json
{
  "orderId":               "uuid",
  "accountId":             1,
  "symbol":                "INFY.NS",
  "side":                  "BUY",
  "quantity":              100,
  "price":                 233.00,
  "executedPrice":         232.71,
  "status":                "FILLED",
  "reason":                null,
  "cashDelta":             -23271.00,
  "positionQuantityAfter": 300,
  "averageCostAfter":      229.83,
  "executedOn":            "2026-09-28T09:14:24Z"
}
```

- `executedPrice` and `reason` are mutually exclusive in practice:
  `FILLED` carries a price and a null reason, `REJECTED` the other way round.
- `cashDelta` is **signed**: negative for a BUY, positive for a SELL.
- `positionQuantityAfter` / `averageCostAfter` exist so a downstream consumer
  can maintain a portfolio projection without reading Postgres. Populate them
  from the values you just wrote, inside the same unit of work.
- **A rejection is an event. Publish it.** A consumer that only ever sees fills
  reports a 100% fill rate.

### 1.5 Reason vocabulary

`reason` is a free string in the contract, with examples rather than a closed
enum. These are the values this platform uses. Do not invent a seventh without
saying so in the channel.

| Reason | Class | Meaning |
|---|---|---|
| `INSUFFICIENT_FUNDS` | permanent | Re-checked at execution price, account can no longer afford it |
| `INSUFFICIENT_HOLDINGS` | permanent | SELL re-checked at execution, holding no longer covers it |
| `PRICE_NOT_MET` | permanent | Outside the marketable range: BUY limit < ask, or SELL limit > bid |
| `INSTRUMENT_NOT_TRADABLE` | permanent | `instrument.is_tradable = false` at execution time |
| `INSTRUMENT_NOT_PRICEABLE` | permanent | No two-sided market exists for it, ever. Mutual funds |
| `ACCOUNT_NOT_ACTIVE` | permanent | Suspended or closed **after** the order was accepted |
| `NO_PRICE` | transient | Fauxnance unreachable / out of quota / retry budget spent |
| `CANCELLED_BY_CUSTOMER` | — | Set by the cancel path, not the executor |

The permanent/transient split is the point. `NO_PRICE` is worth a manual retry
tomorrow; `PRICE_NOT_MET` never is. A downstream consumer has to be able to
tell them apart from the event alone.

### 1.6 Consumer groups

| Group | Reads | Owner |
|---|---|---|
| `trade-executor` | `orders` | story 610 — **fixed by contract, do not change** |
| `analytics-loader` | `trade-events` (optional) | story 615 |

`orders` has **exactly one** consumer group. It is a work queue: a second group
means two services executing the same order. If something else needs to know an
order was placed, it reads `trade-events`.

Every consumer sets an explicit `group.id`. Two consumers sharing one id split
the partitions and each sees only part of the stream — which presents as
messages going missing at random, and costs a day to diagnose.

### 1.7 Producer configuration — everywhere that produces

```properties
acks=all
enable.idempotence=true
retries=<high>
max.in.flight.requests.per.connection=5
```

An idempotent producer removes duplicates caused by a **producer** retry. It
does not remove duplicates caused by an **application** retrying after a crash,
which is why the executor still needs the guarded transition.

### 1.8 Consumer configuration — everywhere that consumes

```properties
enable.auto.commit=false
```

Process, then commit. Committing first loses a message on a crash; committing
after reprocesses it. Reprocessing is survivable because the handler is
idempotent. Losing a trade is not.

### 1.9 Database columns the executor touches

Already present, from Sprint 3 — **do not re-add these**:

| Column | Type | Note |
|---|---|---|
| `orders.fill_price` | `NUMERIC(18,4)` | The brief calls this `executed_price`. Ours is `fill_price` |
| `orders.resolved_at` | `TIMESTAMPTZ` | The brief calls this `executed_on` |
| `client_account.version` | `INTEGER` | The optimistic lock |

Missing, and **owned by story 611**: a rejection reason column, as
`sprint-3/db/migrations/004_execution_columns.sql`, in the same style as 001–003.

Two existing check constraints will bite you if you write a partial update:

- `ck_orders_resolved_at_matches_status` — `NEW` requires `resolved_at IS NULL`;
  any other status requires it `NOT NULL`. So the guarded `UPDATE` **must** set
  status and `resolved_at` in the same statement.
- `ck_orders_fill_price_matches_status` — `FILLED` requires `fill_price NOT NULL`.

Also live: `ck_client_account_balance_non_negative` (`balance >= 0`). A debit
that overdraws throws a constraint violation rather than returning zero rows —
handle it as a business rejection, not a retryable failure.

### 1.10 Where the executor's seam is

610 delivers a `SettlementPort` with a thin implementation (guarded `UPDATE ...
WHERE status = 'NEW'`) so the executor runs end to end. **Story 611 replaces the
implementation, not the interface.** Shape, roughly:

```java
SettlementResult settle(FillDecision decision, OrderRow order);
// SettlementResult: SETTLED | ALREADY_SETTLED | LOCK_EXHAUSTED
```

`ALREADY_SETTLED` means zero rows affected — another delivery got there first.
Publish nothing, acknowledge the offset, log it. That log line is what story
612 demonstrates.

---

## 2. Story 609 — Trade REST API publishes `ORDER_PLACED`

**Blocks 610.** Until this lands, the executor has nothing to consume.
Currently in progress.

### Acceptance criteria
- `POST /api/v1/orders` writes the order at `NEW`, answers `NEW`, publishes
  `ORDER_PLACED` to `orders` keyed by the account.
- The event is published **after** the transaction commits, not inside it.
- Nothing else changes: validation, layering, error catalogue, optimistic lock,
  token verification all stay as they are.

### Tasks
- [ ] Remove the synchronous fill from `trade-api/.../services/OrderService.java`.
      Today it calls `moveCash`, `movePosition` and `orderMapper.fillIfNew`
      inside `placeOrder`. All three go — pricing is the executor's job now and
      there is no price in the request.
- [ ] Return `OrderStatus.NEW` with message `"Order accepted"`
      (`messageFor(NEW)` already returns it).
- [ ] The order row is inserted at `NEW` with `resolved_at = NULL` and
      `fill_price = NULL`. `ck_orders_resolved_at_matches_status` enforces this.
- [ ] Add a Kafka producer with the config in §1.7.
- [ ] Publish **after commit**. Use
      `TransactionSynchronizationManager.registerSynchronization(...)` with
      `afterCommit()`, or `@TransactionalEventListener(phase = AFTER_COMMIT)`.
      Not inside `@Transactional`.
- [ ] Key the message `String.valueOf(accountId)`.
- [ ] Build the envelope per §1.2 with `source = "trade-api"`.
- [ ] Add `KAFKA_BOOTSTRAP_SERVERS` to the `trade-api` service in
      `docker-compose.yml`, plus `depends_on: kafka: condition: service_healthy`.

### Update these characterisation tests in the SAME commit
This is the deliberate behaviour change the sprint is built around, so say so in
the commit message. `trade-api/src/test/java/com/yellow/trade/characterisation/OrderPlacementCharacterisationTest.java`:
- [ ] `affordableOrderFillsSynchronously` — pins `FILLED` / `"Order executed"`.
      Becomes `NEW` / `"Order accepted"`.
- [ ] Any pin asserting cash moved or a position was written on placement.
- [ ] Leave every error-path pin alone. Reused idempotency key, unaffordable
      buy, unknown symbol, non-`ACTIVE` account must all still behave exactly
      as pinned — the brief says validation does not change, and those pins are
      how you prove it.

`TradeApiIntegrationTest` will need the same treatment.

### Test paths required
- [ ] an accepted order is written at `NEW` and answered `NEW`
- [ ] `ORDER_PLACED` is published for a newly accepted order
- [ ] the event is published only after the transaction has committed
- [ ] an order that fails validation publishes nothing

The third is the interesting one: assert ordering, not just that both happened.
A rolled-back transaction must publish nothing.

### Why publish after the commit
An order that committed and was never published is recoverable — replay it from
the order table. An event for an order that rolled back cannot be undone.
Choose the recoverable failure. Expect to be asked this at the review.

---

## 3. Story 611 — settle in one transaction, publish, then acknowledge

**Depends on 610.** Build against the `SettlementPort` in §1.10.

### Acceptance criteria
- Status change, cash movement and position write happen in **one**
  transaction. If any of the three fails, none of them happened.
- The guarded transition is the **first write**, conditional on `status = 'NEW'`.
  Zero rows affected means another delivery got there first.
- The event is published after the commit, and the offset acknowledged after
  the publish.

### Tasks
- [ ] Write migration `sprint-3/db/migrations/004_execution_columns.sql` adding
      the rejection reason column. Same header-comment style as 001–003, and
      idempotent (`ADD COLUMN IF NOT EXISTS`) so a re-run is a no-op.
      Also add the mount line to `docker-compose.yml` **and** the filename to
      `PostgresSupport.SCHEMA_FILES` — see §6.1, that list is hardcoded.
- [ ] Guarded transition as the first write:
      ```sql
      UPDATE orders SET status = ?, fill_price = ?, resolved_at = ?, <reason col> = ?
       WHERE order_id = ? AND status = 'NEW'
      ```
      Set status and `resolved_at` together or the check constraint fails (§1.9).
- [ ] Zero rows affected → return `ALREADY_SETTLED`. Change nothing, publish
      nothing, acknowledge. **Log it clearly** — story 612 points at that line.
- [ ] Cash movement with the optimistic lock on `client_account.version`.
      `trade-api`'s `AccountMapper.debitBalance` / `creditBalance` already do
      `WHERE client_id = ? AND version = ?` with `version = version + 1` —
      copy that shape into the executor's own mapper.
- [ ] Zero rows on the lock is **not** a failure: re-read, retry, bounded
      attempts. Only an exhausted budget is an error.
- [ ] Position write. `trade-api`'s `movePosition` has the buy/sell/close logic
      including deleting a closed position rather than zeroing it
      (`ck_position_quantity_positive`). Reuse the approach.
- [ ] Publish `ORDER_FILLED` / `ORDER_REJECTED` to `trade-events` keyed by
      account, per §1.4 — **after** commit.
- [ ] Acknowledge the offset **after** the publish.

### Test paths required
- [ ] a settled order writes status, cash and position together
- [ ] a failure in any of the three leaves none of them written
- [ ] a second delivery affects zero rows and publishes nothing
- [ ] an exhausted optimistic-lock budget is reported as an error, not ignored

### Order of operations, and why
Commit → publish → acknowledge. Publishing before the commit risks an event for
a transaction that rolled back. Acknowledging before publishing risks an order
that settled in Postgres and told nobody. An order marked filled beside cash
that did not move leaves the audit trail disagreeing with the balance, and
nothing reconciles the two for you.

---

## 4. Story 612 — the duplicate-replay demonstration

**Mandatory for this cohort.** Depends on 609, 610, 611.

### Acceptance criteria
- A replayed message does not debit the account twice and produces no second
  message on `trade-events`.
- The team can give the demonstration **on demand, at any point** in the review.

### Tasks
- [ ] Read one message off `orders` with the console consumer, preserving the key:
      ```bash
      kafka-console-consumer.sh \
        --bootstrap-server localhost:9092 --topic orders \
        --from-beginning --max-messages 1 \
        --property print.key=true --property key.separator=$'\t' > order.txt
      ```
- [ ] Produce it back, preserving the key:
      ```bash
      kafka-console-producer.sh \
        --bootstrap-server localhost:9092 --topic orders \
        --property parse.key=true --property key.separator=$'\t' < order.txt
      ```
      The key must survive the round trip. A replay that loses the key lands on
      a different partition and proves nothing.
- [ ] Script the four pieces of evidence so they can be shown in one pass:
      1. balance before
      2. balance after — **identical**
      3. the executor log line recognising the duplicate (the `ALREADY_SETTLED`
         path from §3)
      4. no second message on `trade-events`
- [ ] Write it up in the sprint README as a runnable sequence, and **rehearse
      it as a team**. Reproducing it once is not the same as being able to
      demonstrate it.

### Note
Our containers expose the broker's own tools inside the `fauxnance-kafka`
container at `/opt/kafka/bin/`. From the host, `docker exec -it fauxnance-kafka
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 ...`
— note `29092` from inside the container, `9092` from the host.

---

## 5. Story 613 — retry vs dead-letter

**Depends on 610.** This wraps the executor's consumer.

### Acceptance criteria
- A message that will never succeed is dead-lettered on the **first attempt**.
- One that will succeed later is retried with a growing backoff and
  dead-lettered once the budget is spent.
- No poison message is retried indefinitely.

### The split — this is the assessed part

| Class | Examples | Handling |
|---|---|---|
| Will never succeed | Malformed JSON, missing `orderId`, an `orderId` not in Postgres, unexpected `eventType` | Dead-letter on the first attempt |
| Will succeed later | Broker briefly unreachable, lost DB connection, exhausted optimistic-lock budget | Retry with growing backoff, dead-letter when the budget is spent |

**A Fauxnance outage is in neither row.** It is retried inside the quote client
(story 610 owns that), and if the budget is spent the order is **rejected**
with `NO_PRICE`. A price feed being down is a business outcome, not a
message-processing failure. Dead-lettering it would leave the order at `NEW`
for ever, which is the one thing the sprint forbids.

### Tasks
- [ ] Classify before you retry. A `DeserializationException` and a
      `DataAccessResourceFailureException` must not take the same path.
- [ ] Non-retryable → `<topic>.DLT` immediately.
- [ ] Retryable → exponential backoff, bounded attempts, then `.DLT`.
- [ ] DLT message: **the original message as the value**, unmodified, and the
      failure reason in a **header**. Do not wrap the payload in a new envelope
      — someone replaying from the DLT needs the original bytes.
- [ ] Suggested headers: `x-failure-reason`, `x-failure-class`,
      `x-original-topic`, `x-original-partition`, `x-original-offset`,
      `x-attempt-count`, `x-failed-at`. Agree these in the channel before
      writing them; 615 may want to read them.
- [ ] Make sure a poison message does not block the partition. One bad message
      blocking a partition stops **every account keyed to that partition** —
      with 3 partitions that is roughly a third of your customers.

### Test paths required
- [ ] a malformed message is dead-lettered on the first attempt
- [ ] a transient failure is retried and then succeeds
- [ ] a poison message does not block the partition

The third needs two messages: a poison one and a good one behind it on the same
partition. Assert the good one still gets processed. Same key, so they land on
the same partition.

---

## 6. Story 615 — incremental analytics load

Independent of 610/614 — can start any time. Reads Postgres, writes DuckDB.

### Acceptance criteria
- One incremental load moves trade data from Postgres into `FACT_TRADES` and
  its three dimensions, driven by a watermark on the order creation timestamp.
- Re-running does not double-count.
- A row failing a quality check is dead-lettered **with the reason and the
  batch it came from**, not dropped.

### Project shape — fixed by the brief
- One Python project rooted at `sprint-07-event-backbone/etl/`, Python 3.12+.
- Importable from `src/`, installable from its own `pyproject.toml`.
- Test deps under a `dev` optional-dependency group, so
  `pip install -e 'etl[dev]'` into an **empty** environment leaves a teammate
  able to run the suite. Do that once yourself — that empty environment is what
  a fresh clone starts from.
- Sprint 4's `sprint-04-analytics-etl/` on the `sprint4` branch is a good
  reference for structure (`extract.py` / `transform.py` / `load.py` /
  `pipeline.py` with typed exceptions). The retry and error handling in its
  `extract.py` is worth copying wholesale.

### Tasks
- [ ] Load order, strictly: `dim_date` (covering the whole range) →
      `dim_instrument` → `dim_account` → `fact_trades`. A fact row that
      references a key that does not exist yet is the failure this prevents.
- [ ] Load orders in **whatever status they reached**, including `REJECTED` and
      `CANCELLED`. Fill rate is one of the analytics the model must answer and
      it cannot be computed from fills alone.
- [ ] Watermark on `orders.date_placed`, so a second run reads what is new
      rather than the whole table.
- [ ] **Merge** on the natural key (`source_order_id`), never a blind insert.
      Rely on the unique constraint the contract declares.
- [ ] Every quality check from the contract's load-and-data-quality section,
      **before** a row reaches the fact table: referential integrity into all
      three dimensions, positive quantity and price, valid side and status, and
      `trade_value` **recomputed rather than trusted**.
- [ ] Dead-letter a failing row with its reason and its batch id.
- [ ] Record the three pipeline commands in the sprint README so a teammate can
      run any stage on its own.

### Test paths required
- [ ] an incremental load populates `FACT_TRADES`
- [ ] a second load with no new data adds no rows
- [ ] the transform handles nulls and type mismatches
- [ ] an invalid row is dead-lettered with its reason and the load continues

### Two traps called out in the brief
- **Never insert a placeholder dimension row** to make an unresolved key pass.
  It hides the fault, which is almost always that the dimension load was
  skipped.
- **Dropping a row silently and dead-lettering it produce the same fact table.**
  Only one of them can be investigated on Monday morning.

### Note on our data
`orders.quantity` is `NUMERIC(18,6)` and genuinely fractional for mutual funds
(seeded: `152.386000`, `240.117000`, `980.500000`) — see `contracts/DEVIATIONS.md`.
Do not type it as an integer anywhere in the pipeline.

---

## 7. Story 616 — SonarQube gate

Last, once there is code to scan. Needs the executor and the pipeline.

### Acceptance criteria
- Gate passes on **both** the Java service and the pipeline: no new blocker or
  critical issue, no new security hotspot left unreviewed, duplication and
  coverage on new code inside the thresholds.
- SAST, dependency scanning and secret detection run locally, and anything they
  report is **fixed**, not left for the review.

### Tasks
- [ ] Run SonarQube in a container **named for our team** — several of these run
      on one machine during the sprint and a second `--name sonarqube` fails
      against another team's container rather than starting ours:
      ```bash
      docker run -d --name sonarqube-team2 -p 9000:9000 sonarqube:community
      ```
- [ ] `http://localhost:9000`, sign in `admin`/`admin`, change the password,
      create a project, generate a token.
- [ ] **Export the token, never commit it.** It is a credential.
      ```bash
      export SONAR_TOKEN=the-token-you-generated
      ```
- [ ] Scan the executor:
      ```bash
      cd sprint-07-event-backbone/executor && mvn -B verify \
        org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
        -Dsonar.host.url=http://localhost:9000 \
        -Dsonar.projectKey=trade-executor -Dsonar.token="${SONAR_TOKEN}"
      ```
- [ ] Scan the pipeline. **We are on Linux, so `host.docker.internal` does not
      resolve** — add `--network host` and use `http://localhost:9000`:
      ```bash
      cd ../etl && docker run --rm --network host -v "${PWD}:/usr/src" \
        -e SONAR_HOST_URL=http://localhost:9000 \
        -e SONAR_TOKEN="${SONAR_TOKEN}" \
        sonarsource/sonar-scanner-cli -Dsonar.projectKey=analytics-pipeline \
        -Dsonar.sources=src -Dsonar.tests=tests
      ```
- [ ] Interpret the findings and fix them.

### Do not
Mark findings as "won't fix" to go green. It is visible in the dashboard and it
is not passing. The gate is read at the review, on your screen, on the project
you scanned.

---

## 8. Carry-over from 607/608 (done, small fixes)

- [ ] `sprint-07-event-backbone/design/kafka.md` says the creation command lives
      at `executor/scripts/create-topics.sh`. It is actually at
      `sprint-07-event-backbone/scripts/create-topics.sh`. One-line fix — the
      brief says `design/kafka.md` **names** the command, and the instructor
      will run what it names.
- [ ] `design/kafka.md` currently restates the contract's reasoning. The brief
      is explicit that repeating it back is *not* the deliverable — **applying**
      it is. Add: how many executor instances we can usefully run and why,
      what raising the partition count in Sprint 10 does to per-account
      ordering, and which of our consumers would notice.

---

## 9. Shared files — coordinate before editing

These are touched by more than one story. Say so in the channel before you
push, or expect a conflict.

| File | Who touches it | For what |
|---|---|---|
| `docker-compose.yml` | 609, 610, 611 | Kafka env on trade-api; the new `executor` service; the 004 migration mount |
| `.env.example` | 609, 610, 614 | `KAFKA_BOOTSTRAP_SERVERS`, `FAUXNANCE_API_KEY`, `POLL_INTERVAL_SECONDS` |
| `sprint-3/db/migrations/` | 611 | `004_execution_columns.sql` — **only 611**, so we do not both write a `004` |
| `sprint-3/db/seed/` | seeding task | `005_fauxnance_instruments.sql` |
| `PostgresSupport.SCHEMA_FILES` | 611, seeding | **Hardcoded list**, not a glob — see §6.1 below |
| `design/kafka.md` | 607 carry-over | |
| `trade-api/.../OrderService.java` | 609 only | |

### 9.1 The two gotchas that will cost someone an afternoon

**`PostgresSupport.SCHEMA_FILES` is a hardcoded list.**
`sprint-3/db/apply.sh` globs `*.sql` and sorts, so it picks up new files
automatically. `trade-api/src/test/java/com/yellow/trade/integration/PostgresSupport.java`
does **not** — it lists the nine files explicitly. Add a migration or a seed
file without adding it there and every integration and characterisation test
runs against a database that does not have your change, and the failure looks
like it is coming from somewhere else entirely.

**Docker init scripts only run on an empty volume.**
The seed and migration files are mounted into `/docker-entrypoint-initdb.d/`,
which Postgres runs **only on first initialisation**. Adding a file to
`docker-compose.yml` does nothing to a volume that already exists. After
pulling a change that adds one:

```bash
docker compose --profile platform down -v   # -v destroys the data volume
docker compose --profile platform up -d --build
```

Or apply it by hand with `psql`. There is no warning when this is skipped — the
container just starts and the table is not there.

---

## 10. Suggested order of work

```
609 (in progress) ──┬──> 610 ──┬──> 611 ──> 612
                    │          │
       seeding ─────┘          └──> 613
                    614 (parallel, needs nothing but the executor project)
                    615 (parallel, independent)
                                                  616 (last, needs both projects)
```

- **609 first** — 610 has nothing to consume without it.
- **614 needs no database** and is the smaller half of the executor, so it
  lands early and gives `market-data` something on it to work against.
- **615 is independent** all week. Start it in parallel; do not leave it to the
  last day because the brief calls it "narrow".
- **616 last**, but not *on the last day* — the gate failing is normal and
  fixing findings takes longer than running the scan.

---

## 11. Open decisions

| # | Decision | Status |
|---|---|---|
| 1 | Seed `005_fauxnance_instruments.sql` with real tickers; fictional ones stay tradable and exercise the no-price path | **Agreed.** Risks in §9.1 |
| 2 | Mutual funds reject with `INSTRUMENT_NOT_PRICEABLE` this sprint | **Agreed.** Future scope: a NAV source behind the same `QuoteSource` port |
| 3 | Rejection reason column name in migration 004 | **Open** — 611's call, tell the channel |
| 4 | `.DLT` header names (§5) | **Open** — 613's call, 615 may read them |
| 5 | `POLL_INTERVAL_SECONDS` = 60, floor of 60 enforced in code | 614's call, arithmetic in `design/` |

---

## 12. Before the review, bring

Straight from the brief. Worth reading a week early rather than the night before.

- the running stack
- the topics described from the broker (`kafka-topics.sh --describe`) with
  `design/kafka.md` open beside them
- one order traced from HTTP request → topic → committed rows → published event
- the duplicate replay, performed live
- a quote arriving on `market-data` inside one polling interval, with several
  distinct symbols in the cycle
- a bad row landing in the dead-letter path rather than in `FACT_TRADES`
- a second load over the same data that adds nothing
- the fill rule and the reasoning behind it
- the quota arithmetic for our configuration
- the SonarQube dashboard
- `git log` showing the characterisation tests arriving before the change

A green suite proves less here than anywhere else in the programme. A duplicate
that moved no money once says nothing about whether the mechanism holds under
load, and a row that was dead-lettered says nothing about whether the reason
recorded with it is usable on Monday morning.
