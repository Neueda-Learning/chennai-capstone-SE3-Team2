# Flow 2: Place an order (buy and sell)

The Trade REST API **accepts** the order and puts it on Kafka. The Trade Executor **prices and settles** it later. Part A is the accept path. Part B is the execute path. The table at the end shows how a BUY and a SELL differ.

## A. Accept the order (Trade REST API)

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant OT as OrderTicket component
        participant TA as TradeApi
        participant AI as authInterceptor
    end
    box Trade REST API
        participant F as JwtAuthenticationFilter
        participant V as JwtTokenVerifier
        participant OC as OrderController
        participant OS as OrderService (trade-api)
        participant D as OrderService (libs/domain)
        participant AM as AccountMapper
        participant OM as OrderMapper
        participant P as OrderEventPublisher
    end
    participant DB as trading DB
    participant K as Kafka orders

    C->>OT: symbol, BUY or SELL, quantity, market or limit
    OT->>OT: submit() - checks units, price, shortfall()
    OT->>OT: idempotencyKey = crypto.randomUUID() (same key on a retry)
    OT->>TA: placeOrder({accountId, symbol, side, quantity, price, idempotencyKey})
    TA->>AI: OrdersService.placeOrder() (generated client)
    AI->>AI: carriesToken(url) - allow list match
    AI->>F: POST /api/v1/orders + Authorization Bearer
    F->>F: doFilterInternal() - bearerTokenFrom(request)
    F->>V: verifyAndExtractAccountId(token)
    V->>V: signature HS256, exp, iss = auth-service, alg from verified header
    alt any check fails
        F-->>OT: 401 AUTH-401 Unauthorised
    end
    F->>OC: request with CallerAccount set
    OC->>OC: @Valid PlaceOrderRequest (else 422 VAL-422)
    OC->>OS: placeOrder(request) - @Transactional
    OS->>AM: findById(accountId)
    AM->>DB: SELECT FROM client_account
    OS->>OS: caller.canReach(accountId)? else 403 ACC-403 (logged)
    OS->>D: placeOrder(request)
    D->>D: rule 1 account exists (404 ACC-404)
    D->>D: rule 2 ACTIVE, rule 3 KYC VERIFIED (403 ACC-403)
    D->>D: rule 4 instrumentRepo.findBySymbol() + isTradable (404 INS-404)
    D->>D: rule 5 quantity > 0, price > 0 (422 VAL-422)
    alt side = BUY
        D->>D: rule 6 account.canAfford(qty x price) (400 ORD-400)
    else side = SELL
        D->>DB: positionRepo.find() SELECT FROM position
        D->>D: rule 7 position.canSell(qty) (409 ORD-409)
    end
    D->>DB: orderRepo.existsByAccountAndKey() SELECT FROM orders
    D->>D: rule 8 key unused (409 ORD-409)
    D->>D: Order.place() - UUID made in the domain, status NEW
    D->>OM: orderRepo.save() -> OrderMapper.insert
    OM->>DB: INSERT orders (status NEW)
    D-->>OS: Order
    opt side = BUY
        OS->>OS: blockFunds(account, qty x limit)
        OS->>AM: blockFunds(clientId, amount, version)
        AM->>DB: UPDATE client_account SET blocked_funds + amount, version + 1 WHERE version = ?
        alt 0 rows (lost optimistic lock)
            OS-->>OT: 409 ORD-409 (transaction rolls back)
        end
    end
    OS->>OS: applicationEventPublisher.publishEvent(OrderPlacedDomainEvent)
    OS-->>OC: OrderResponse {orderId ORD-uuid, status NEW, "Order accepted"}
    Note over OS,DB: transaction commits
    P->>P: publishOrderPlaced() - @TransactionalEventListener(AFTER_COMMIT)
    P->>K: KafkaTemplate.send(orders, key accountId, ORDER_PLACED envelope)
    OC-->>TA: 200 OK
    TA-->>OT: OrderResponse
    OT->>OT: current.refresh(), readPositions()
```

## B. Execute the order (Trade Executor)

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka orders
    box Trade Executor
        participant PC as OrderPlacedConsumer
        participant X as OrderExecutionService
        participant PT as PreTradeChecks
        participant Q as FauxnanceQuoteClient / MfNavClient
        participant FR as EquityFillRule / MutualFundFillRule
        participant S as FullSettlement
        participant EM as ExecutionMapper
    end
    participant DB as trading DB
    participant T as Kafka trade-events

    K->>PC: ORDER_PLACED (group trade-executor)
    PC->>X: onOrderPlaced() -> execute(orderId)
    X->>EM: findOrder(orderId)
    EM->>DB: SELECT orders JOIN instrument
    alt no row
        X-->>PC: UnknownOrderException (poison, to orders.DLT)
    end
    X->>X: order.isWorking()? status not NEW = stop
    X->>EM: findAccount(accountId)
    EM->>DB: SELECT FROM client_account
    X->>PT: beforePricing(instrument, account)
    alt not tradable or account not ACTIVE
        X->>S: settleAndPublish(Reject reason)
    end
    alt stock
        X->>Q: quote(symbol) - 3 attempts, Retry-After
    else mutual fund
        X->>Q: nav(symbol) - one NAV both ways
    end
    alt QuoteUnavailableException
        X->>S: settleAndPublish(Reject NO_PRICE)
    end
    X->>FR: decide(order, quote)
    FR->>FR: BUY marketable if limit >= ask, SELL if limit <= bid (round first)
    opt Fill
        X->>EM: findPosition(accountId, instrumentId)
        X->>PT: atExecution(order, account, held, executedPrice)
        Note over PT: funds and holdings checked again at the real price
    end
    X->>S: settle(decision, order) - @Transactional
    S->>EM: settleIfNew(orderId, status, fillPrice, now, reason)
    EM->>DB: UPDATE orders SET status, fill_price, resolved_at WHERE order_id = ? AND status = NEW
    alt 0 rows
        S-->>X: ALREADY_SETTLED (duplicate delivery, nothing moves)
    else 1 row
        S->>S: updateAccountWithRetry() - max 5 attempts
        S->>EM: updateAccount(accountId, balanceDelta, blockedDelta, version)
        EM->>DB: UPDATE client_account SET balance, blocked_funds, version + 1 WHERE version = ?
        opt FILLED
            S->>S: writePosition(order, executedPrice)
            S->>EM: insertPosition / updatePosition / deletePosition
            EM->>DB: INSERT, UPDATE or DELETE position
        end
        S-->>X: SETTLED (commit)
        X->>X: publishTradeEvent(decision, order, heldBefore)
        X->>T: send(trade-events, key accountId, ORDER_FILLED or ORDER_REJECTED)
    end
    PC->>K: acknowledge offset (AckMode.RECORD)
```

## BUY compared with SELL

| Step | BUY | SELL |
|---|---|---|
| UI check | `shortfall()`: cost is more than available cash | quantity is more than the holding |
| Domain rule | Rule 6: `account.canAfford(qty x price)`, else `400 ORD-400` | Rule 7: `positionRepo.find()` + `position.canSell(qty)`, else `409 ORD-409` |
| At accept | `OrderService.blockFunds()`: `blocked_funds += qty x limit` | Nothing is reserved (known gap U3) |
| Fill rule | Fill if `limit >= ask`. Pays the **ask**. | Fill if `limit <= bid`. Receives the **bid**. |
| Settle cash on FILLED | `balance -= qty x executed`, `blocked -= qty x limit` | `balance += qty x executed` |
| Settle cash on REJECTED | `blocked -= qty x limit` | No change |
| Position on FILLED | INSERT, or UPDATE with weighted average cost | UPDATE quantity, or DELETE when it reaches 0 |
| Event | `ORDER_FILLED` with `cashDelta < 0` | `ORDER_FILLED` with `cashDelta > 0`, `averageCostAfter` set even at 0 |

## Tables written in this flow

| Table | Writer | How |
|---|---|---|
| `orders` | `OrderMapper.insert`; `ExecutionMapper.settleIfNew` | INSERT NEW; guarded UPDATE out of NEW |
| `client_account` | `AccountMapper.blockFunds`; `ExecutionMapper.updateAccount` | Optimistic lock on `version` |
| `position` | `ExecutionMapper.insertPosition`, `updatePosition`, `deletePosition` | Weighted average on buy. Delete at zero. |

## Why it is built this way

- **`AFTER_COMMIT` publish.** A publish inside the transaction can announce an order that rolls back. After commit, a lost event can be replayed from the table.
- **Key `accountId`.** Kafka keeps order inside one partition. A SELL comes after the BUY that funded it.
- **Guarded update `WHERE status = 'NEW'`.** A duplicate message or a race with cancel gives 0 rows. Nothing moves two times.
- **Check again at execution.** The price moved after accept, so funds and holdings are checked at the executed price.
