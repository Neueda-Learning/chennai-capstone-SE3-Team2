# Story 609 — fixes needed before 610 can build on it

Found while merging `release/sprint7` into `feature/sprint7/610-614`.

Two of these are **blocking defects** — the service is currently broken in a way
the test suite cannot see. The rest are deviations from
`contracts/kafka-topics.md`, which is binding on all three topics and which
story 607 has already been signed off against.

Nothing in this file has been changed on my branch. It is all in
`trade-api/`, which is 609's file.

---

## Why the green build did not catch any of this

`OrderPlacementCharacterisationTest` and `TradeApiIntegrationTest` are both
annotated:

```java
@EnabledIf(value = "com.yellow.trade.integration.PostgresSupport#databaseAvailable")
```

With no Docker daemon and no `IT_DB_URL`, they **skip silently** — no failure,
no warning, just fewer tests. `OrderServiceTest` and `OrderControllerTest` mock
`OrderMapper`, so a duplicate insert is invisible to them too.

**Before pushing a fix, make sure these actually run:**

```bash
# either start Docker, so Testcontainers can bring up postgres:16-alpine
docker info

# or point at a database you already have
export IT_DB_URL=jdbc:postgresql://localhost:5432/trading
export IT_DB_USER=postgres
export IT_DB_PASSWORD=...

mvn -pl trade-api test
```

Check the output says the characterisation tests **ran**, not that they were
skipped. If they skip, the fix is unverified.

---

## BLOCKER 1 — the order is inserted twice, so every placement 500s

**Where:** `trade-api/src/main/java/com/yellow/trade/services/OrderService.java`,
lines 96–109.

**What happens:**

1. Line 91 — `domainOrderService.placeOrder(request)` ends in
   `orderRepo.save(order)` → `MyBatisOrderRepository.save` →
   `orderMapper.insert(row)`. **The order is already in the database here.**
2. Line 109 — `orderMapper.insert(orderRow)` inserts it **again**, same
   `order.orderId()`.

The second insert violates `orders_pkey` on `order_id`. The
`DuplicateKeyException` handler lives *inside* `MyBatisOrderRepository.save`,
so this one is not caught — it propagates as an unhandled 500 rather than a
clean error from the catalogue.

`POST /api/v1/orders` fails for every order, every time.

**Fix:** delete lines 96–109. The domain already persists the order correctly
at `NEW`, with `fill_price = NULL` and `resolved_at = NULL` — which is exactly
what `ck_orders_resolved_at_matches_status` requires.

```java
// DELETE all of this — domainOrderService.placeOrder() already inserted it
OrderRow orderRow = new OrderRow();
orderRow.setOrderId(order.orderId());
...
orderMapper.insert(orderRow);
```

**Verify:** run the characterisation tests with a database actually available
(see above). `affordableOrderFillsSynchronously` must return 200, not 500.

---

## BLOCKER 2 — cash and position still move at placement, so every order double-debits

**Where:** same file, lines 111–113.

**What is still there:**

```java
// Update cash and positions in single transaction
moveCash(account, order.side(), consideration);
movePosition(order, limitPrice);
```

**Why it is wrong:** the account is debited at the **limit price** the moment
the order is accepted, and a position is written for an order that has not
executed. The Trade Executor then settles the same order and debits **again**,
at the executed price.

That is a double debit on every order — the exact failure this whole sprint
exists to prevent. It is also worse than the Sprint 6 behaviour, because at
least that debit corresponded to a fill that really happened.

Story 609's own first task is *"Remove the synchronous fill from the endpoint;
there is no price in that request and pricing is the executor's job."* The
status update was removed. The money movement was not.

**Fix:** delete both lines. Cash and position become story 611's, inside the
settlement transaction, at the **executed** price.

Once they are gone, `moveCash`, `movePosition` and the `held(...)` helper have
no callers left in `placeOrder`. **Leave them in the file** — 611 needs exactly
that logic, including the "delete a closed position rather than zero it" branch
(`ck_position_quantity_positive`). Mark them for 611 rather than deleting them:

```java
// Unused by placeOrder from Sprint 7 onwards. Story 611 moves this into the
// executor's settlement transaction, priced at the executed price rather
// than the limit price.
```

**Also check:** `consideration` and `money(...)` may now be unused in
`placeOrder`. Sonar will flag them in story 616 — tidy them here rather than
leaving it for that story.

**Verify:** a characterisation test asserting the balance is **unchanged**
after placement. That is a deliberate behaviour change, so per the brief it
gets updated in the same commit, with the reason in the commit message.

---

## Contract deviations in the published event

`contracts/kafka-topics.md` is binding, and 607 was signed off against it. These
are in `trade-api/src/main/java/com/yellow/trade/events/OrderPlacedEvent.java`
and `services/OrderEventPublisher.java`.

### 3. There is no envelope — this is the important one

The contract requires **every message on all three topics** to carry the same
five-field envelope wrapping a `payload`:

```json
{
  "eventId":       "uuid, unique per message",
  "eventType":     "ORDER_PLACED",
  "eventTime":     "2026-09-28T09:14:22Z",
  "source":        "trade-api",
  "schemaVersion": 1,
  "payload":       { ... }
}
```

`OrderPlacedEvent` is a flat payload with no envelope at all.

**Why it matters beyond conformance:**

- `eventId` is *the idempotency key for consumers*, per the contract.
- `eventType` is how a consumer discriminates the payload — and story 613 has
  to dead-letter "an unexpected event type", which is impossible if no message
  carries one.
- The contract's stated reason for one identical envelope is that *"one
  deserialiser and one dead-letter handler cover the platform"*. Three topics
  with three shapes means three of each.
- 607's acceptance criterion — *"every message carries the five-field
  envelope"* — is currently not met by the only producer we have.

**Fix:** wrap the payload. Suggested shape, so 610/611/614 can share one type:

```java
public record EventEnvelope<T>(
        String  eventId,        // UUID.randomUUID().toString(), per message
        String  eventType,      // "ORDER_PLACED"
        Instant eventTime,      // Instant.now() at publish
        String  source,         // "trade-api"
        int     schemaVersion,  // 1
        T       payload) {}
```

Cheapest moment to do this is now, with one producer and one consumer. After
611 and 614 land it is three producers and four consumers.

### 4. `price` is missing from the payload

The contract's `ORDER_PLACED` payload includes `price` — the limit price per
unit, two decimals.

It is the field the fill rule compares against `bid`/`ask`. Without it the
executor has to re-read the order from Postgres purely to recover a value that
should have been on the event, and any Sprint 10 consumer reading `orders` gets
an order with no price on it.

**Fix:** add `price` to the payload, from `order.limitPrice()`.

### 5. `orderId` is a display string, not a UUID

```java
OrderIdentifier.display(order.orderId())   // -> "ORD-6f2b1c2a-..."
```

The contract says `orderId` is a UUID that *"matches `orders.id` in Postgres"*.
`"ORD-"` is a presentation concern for the HTTP response — it should not be on
the wire.

**Fix:** publish `order.orderId().toString()`. Keep `OrderIdentifier.display`
for `OrderResponse`, which is where it belongs and where the characterisation
test correctly pins it.

### 6. `createdOn` is missing; `timestamp` is the wrong time

`timestamp` is set to `Instant.now()` **at publish**, which is already what the
envelope's `eventTime` means. The contract's payload wants `createdOn` — when
the order was *recorded*. Those differ by however long the transaction and the
after-commit hook took, and they differ a lot more if a publish is retried.

**Fix:** rename to `createdOn` and populate from `order.placedAt()`.

### 7. `retries = 3` is not "a high retry count"

`KafkaProducerConfig` sets `ProducerConfig.RETRIES_CONFIG, 3`. Both the brief
and the contract say a high retry count. `acks=all`,
`enable.idempotence=true` and `max.in.flight.requests.per.connection=5` are all
correct — only this one is off.

**Fix:** `Integer.MAX_VALUE`, and bound the total by `delivery.timeout.ms`
(120000 is the default and is fine). That is the idiomatic pairing: retry
indefinitely, but give up on the wall clock.

### 8. Env var name is `KAFKA_BROKERS`, should be `KAFKA_BOOTSTRAP_SERVERS`

`application.yml` reads `${KAFKA_BROKERS:localhost:9092}`. `readme_7.md`
specifies `KAFKA_BOOTSTRAP_SERVERS` pointing at `kafka:29092`, and the executor
will read that name. Two names for one broker address across two services is a
compose-wiring bug waiting to happen.

**Fix:** rename to `KAFKA_BOOTSTRAP_SERVERS` and add it to `.env.example`.

### Not a problem

`status` and `instrumentId` are extra fields not in the contract, but *"adding
an optional field is not a breaking change"*. They can stay. `instrumentId` is
an internal key that no consumer should branch on — worth a comment saying so.

---

## Things 609 got right — keep these

- `@TransactionalEventListener(phase = AFTER_COMMIT)` is exactly the right
  mechanism. Published after commit, not inside the transaction.
- Catching the publish failure and logging *"can be manually replayed from the
  order table"* is the correct choice of recoverable failure, and the log line
  says so. That is the question the brief expects you to be asked at the review.
- `acks=all`, `enable.idempotence=true`,
  `max.in.flight.requests.per.connection=5` — all correct.
- Keying on `order.accountId().toString()` — correct, and correctly a string.
- `OrderEventPublisherTest` covers the after-commit ordering properly.

One doc nit: `OrderEventPublisher`'s javadoc says *"Handles transient failures
with retry logic"*. It does not — the retry is the producer's, via
`RETRIES_CONFIG`. Reword, or the next reader goes looking for a retry loop that
is not there.

---

## Suggested commit order

Small commits, so the history reads. The brief is explicit that *"a history of
one commit per week cannot show this and cannot show anything else either."*

1. `[SEC3-609] Remove the duplicate order insert` — blocker 1
2. `[SEC3-609] Stop moving cash and position at placement` — blocker 2, with
   the characterisation-test update in the **same commit** and the reason in
   the message
3. `[SEC3-609] Wrap ORDER_PLACED in the contract envelope` — items 3–6
4. `[SEC3-609] Producer retry budget and broker env var name` — items 7–8

---

## Definition of done

- [ ] Characterisation and integration tests **run** (not skip) and pass
- [ ] `POST /api/v1/orders` returns 200 with status `NEW`
- [ ] Account balance is **unchanged** after placing an order
- [ ] No `position` row is written at placement
- [ ] The order row exists exactly once, at `NEW`, `fill_price` and
      `resolved_at` both `NULL`
- [ ] A message on `orders` carries the five-field envelope, and its payload
      has `orderId` as a bare UUID, `price`, and `createdOn`
- [ ] The message key is the account id as a string
- [ ] `.env.example` documents `KAFKA_BOOTSTRAP_SERVERS`

Check the last two from the broker rather than from the code:

```bash
docker exec -it fauxnance-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:29092 --topic orders \
  --from-beginning --max-messages 1 \
  --property print.key=true --property key.separator=$'\t'
```

---

## What this blocks

610's Kafka consumer is waiting on the envelope decision (item 3) — every other
part of 610 and 614 is being built in parallel and does not depend on 609.

Blockers 1 and 2 block the live demo entirely: with them in place no order can
be placed at all, and if only blocker 1 is fixed, every order double-debits,
which is the one thing acceptance criterion 4 says must not happen.
