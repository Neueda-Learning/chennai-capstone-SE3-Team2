# Flow 3: Cancel an order

A customer can cancel an order only while it is `NEW`. The cancel and the executor can race. The database lets only one of them win. Since Sprint 10, a cancel also announces `ORDER_CANCELLED` on `trade-events` through the outbox, so notifications and strategy hear about it.

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant B as Blotter component
        participant TA as TradeApi
    end
    box Trade REST API
        participant F as JwtAuthenticationFilter
        participant OC as OrderController
        participant OS as OrderService
        participant OM as OrderMapper
        participant AM as AccountMapper
        participant AN as OrderCancelledAnnouncer
        participant OB as OutboxMapper
        participant RE as OutboxRelay
    end
    participant DB as trading DB
    participant T as Kafka trade-events

    C->>B: click Cancel on a NEW order
    B->>B: cancel(orderId) - cancelling.set(orderId)
    B->>TA: cancelOrder(orderId) - strips ORD- prefix
    TA->>F: DELETE /api/v1/orders/{uuid} + Bearer
    F->>OC: verified, CallerAccount set
    OC->>OS: cancelOrder(id) - @Transactional
    OS->>OM: findById(orderId)
    OM->>DB: SELECT FROM orders WHERE order_id = ?
    alt no row
        OS-->>B: 404 ORD-409 (no ORD-404 in the contract)
    end
    OS->>OS: caller.canReach(order.clientId)? else 403 ACC-403 (logged)
    OS->>OM: cancelIfNew(orderId, cancelledAt)
    OM->>DB: UPDATE orders SET status = CANCELLED, resolved_at WHERE order_id = ? AND status = NEW
    alt 0 rows (executor won the race, or already resolved)
        OS-->>B: 409 ORD-409 not cancellable
    end
    opt side = BUY
        OS->>AM: findById(clientId)
        OS->>OS: releaseFunds(account, qty x price)
        OS->>AM: releaseFunds(clientId, amount, version)
        AM->>DB: UPDATE client_account SET blocked_funds - amount, version + 1 WHERE version = ?
    end
    OS->>AN: publishEvent(OrderCancelledDomainEvent) - @EventListener, same transaction
    AN->>AN: on(cancelled) - TradeEventPayload status CANCELLED, cashDelta 0
    AN->>OB: insert(eventId, trade-events, clientId, envelope)
    OB->>DB: INSERT outbox_event ORDER_CANCELLED
    OS-->>OC: OrderResponse status CANCELLED
    Note over OS,DB: commit: cancel, funds and event together
    OC-->>TA: 200 OK
    B->>B: refresh() - orderHistory() re-read
    loop every 2 s
        RE->>OB: relayOnce() -> lockBatch(20) FOR UPDATE SKIP LOCKED
        RE->>T: KafkaTemplate.send(trade-events, key accountId).get()
        RE->>OB: markPublished(eventId)
    end
    Note over T: notification-service and strategy-service consume it
```

## The race with the executor

```mermaid
sequenceDiagram
    participant API as OrderService.cancelOrder()
    participant DB as orders row (status NEW)
    participant EX as FullSettlement.settle()
    par customer cancels
        API->>DB: UPDATE ... SET CANCELLED WHERE status = NEW
    and executor fills
        EX->>DB: UPDATE ... SET FILLED WHERE status = NEW
    end
    Note over DB: Postgres serialises the two updates on the row lock.<br/>The first gets 1 row. The second gets 0 rows.
    DB-->>API: 1 row: CANCELLED, release funds
    DB-->>EX: 0 rows: ALREADY_SETTLED, publish nothing
```

## Tables written in this flow

| Table | Writer | How |
|---|---|---|
| `orders` | `OrderMapper.cancelIfNew` (`OrderMapper.xml:125`) | Guarded UPDATE `WHERE status = 'NEW'` |
| `client_account` | `AccountMapper.releaseFunds` | BUY only. Optimistic lock on `version`. |
| `outbox_event` | `OutboxMapper.insert`, `markPublished` | Same transaction as the cancel |

## Why it is built this way

- **Release funds only after the guarded update gives 1 row.** Otherwise a cancel that lost the race would release money a fill already used.
- **Outbox, not `AFTER_COMMIT`.** The cancel event commits with the cancel or not at all.
