# Flow 9: Automated strategy

A strategy is a rule that places an order when no customer is signed in. Example: "BUY 5 TCS when the price FALLS_THROUGH 3,900, spend at most 20,000, hold at most 50". Four triggers exist: `FALLS_THROUGH`, `RISES_THROUGH` (a price level), `MA_CROSSOVER` and `BOLLINGER` (indicators from daily candles, decision 0017).

A strategy fires through the **public order route** with a **5-minute token** that auth mints (decision 0012). So every order check applies to it.

## A. Create and switch on a strategy

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant SU as Strategies component
        participant SA as StrategiesApi
    end
    box Trade REST API - strategy module
        participant SC as StrategyController
        participant SS as StrategyService
        participant IM as InstrumentMapper
        participant SM as StrategyMapper
    end
    participant DB as trading DB

    C->>SU: TCS.NS, BUY 5, FALLS_THROUGH 3900, maxSpend 20000, maxPosition 50
    SU->>SU: create() -> request() builds StrategyRequest
    SU->>SA: create(accountId, request)
    SA->>SC: POST /api/v1/accounts/{id}/strategies + Bearer
    SC->>SC: @Valid StrategyRequest
    SC->>SS: create(accountId, request) - @Transactional
    SS->>SS: AccountAccess.requireOwn()
    SS->>SS: level trigger needs a price, indicator trigger takes none (else 422)
    SS->>IM: findBySymbol(symbol) - unknown 404, fund 422, not tradable 404
    SS->>SM: lockAccount(accountId)
    SM->>DB: pg_advisory_xact_lock(1015, clientId)
    SS->>SM: countForClient(accountId)
    alt 10 or more
        SS-->>SU: 409 LIM-409
    end
    SS->>SM: insert(StrategyRow)
    SM->>DB: INSERT strat_strategy (enabled false, status ARMED, failures 0)
    SS-->>SU: 201 Strategy (switched off)

    C->>SU: click Switch on
    SU->>SU: toggle(strategy) -> act()
    SU->>SA: setEnabled(accountId, strategyId, true)
    SA->>SC: PUT .../strategies/{sid}/enabled {enabled true}
    SC->>SS: setEnabled(accountId, sid, true)
    SS->>SM: enable(sid) - UPDATE strat_strategy SET enabled true, status ARMED, failures reset
    Note over DB: the view strat_polled_symbols now lists TCS.NS,<br/>so the executor's poller prices it
```

## B. A quote fires the strategy

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka market-data
    box Trade REST API - strategy module
        participant SL as StrategyListener
        participant ST as StrategyTrigger
        participant IR as IndicatorReader
        participant SF as StrategyFirer
        participant SM as StrategyMapper
        participant OP as HttpOrderPlacer
        participant TC as StrategyTokenClient
    end
    participant DB as trading DB
    box Auth service
        participant IG as InternalSecretGuard
        participant SK as StrategyTokenController
        participant AT as AccessTokenService
    end
    participant OR as POST /api/v1/orders (flow 2)

    K->>SL: QUOTE TCS.NS 3895 (group strategy-service, starts at latest)
    SL->>SL: on(value, topic) - topic is market-data
    SL->>ST: onQuote(StrategyQuote.parse(value))
    ST->>SM: findCrossed(instrumentId, price)
    SM->>DB: SELECT strategy_id FROM strat_strategy WHERE enabled AND ARMED AND level crossed
    opt indicator strategies exist
        ST->>SM: findArmedIndicators(instrumentId)
        ST->>IR: view(symbol, quote) - CandleService.candles(SIX_MONTHS) -> Indicators.read()
    end
    loop each strategyId
        ST->>SF: fire(strategyId, quote [, view]) - @Transactional, one per strategy
        SF->>SM: lockFireable(strategyId)
        SM->>DB: SELECT FROM strat_strategy WHERE id = ? AND enabled AND ARMED FOR UPDATE
        SF->>SF: triggered(strategy, quote, view)? (read under the lock)
        SF->>SF: limit = ask x 1.005 (BUY) or bid x 0.995 (SELL)
        opt BUY
            SF->>SF: refusal() - limit x qty > maxSpend? held + qty > maxPosition?
            SF->>DB: PositionMapper.findOne() SELECT FROM position
        end
        alt over a bound
            SF->>SM: insertRun(REFUSED_LIMIT), stop(strategyId)
            SM->>DB: INSERT strat_run, UPDATE strat_strategy SET status STOPPED
        else inside bounds
            SF->>SM: insertRun(PLACED, source_event_id = quote eventId)
            SM->>DB: INSERT strat_run ON CONFLICT (strategy_id, source_event_id) DO NOTHING
            alt 0 rows (quote replayed)
                SF-->>ST: return - nothing placed again
            end
            SF->>OP: place(accountId, symbol, side, qty, limit, "strategy-{id}-{eventId}")
            OP->>TC: mint(accountId)
            TC->>IG: POST /internal/strategy-tokens, x-internal-secret
            IG->>SK: mint({accountId}) - credentials.findByAccountId()
            SK->>AT: issueForStrategy(credential) - 300 s, roles CUSTOMER + STRATEGY
            SK-->>TC: {accessToken, expiresIn 300} (never logged)
            OP->>OR: POST /api/v1/orders + Bearer strategy token
            Note over OR: JWT filter, canReach, domain rules 1 to 8,<br/>blockFunds, ORDER_PLACED to Kafka
            alt 200 NEW
                OR-->>OP: {orderId ORD-uuid}
                SF->>SM: markPlaced(runId, orderId), markFired(strategyId)
                SM->>DB: UPDATE strat_run SET order_id, UPDATE strat_strategy SET status FIRED
            else error envelope
                SF->>SM: markFailed(runId, reason), recordFailure(strategyId)
                opt failures >= 3
                    SF->>SM: insertRun(STOPPED)
                end
            end
        end
    end
```

## C. The order result comes back

```mermaid
sequenceDiagram
    autonumber
    participant T as Kafka trade-events
    participant SL as StrategyListener
    participant SO as StrategyOutcomes
    participant SM as StrategyMapper
    participant DB as trading DB

    T->>SL: ORDER_FILLED / ORDER_REJECTED / ORDER_CANCELLED
    SL->>SO: on(value, topic trade-events) -> apply(value) - @Transactional
    SO->>SM: findByPlacedOrder(orderId)
    alt null (not a strategy order)
        SO-->>SL: return - ignore
    end
    alt ORDER_FILLED
        SO->>SM: insertRun(FILLED, executedPrice, source_event_id = eventId)
    else ORDER_REJECTED
        SO->>SM: insertRun(REJECTED, reason), recordFailure(strategyId)
        opt failures >= 3
            SO->>SM: insertRun(STOPPED)
        end
    else ORDER_CANCELLED
        SO->>SM: insertRun(REJECTED, "The order was cancelled.")
    end
    SM->>DB: INSERT strat_run ON CONFLICT DO NOTHING (replay safe)
```

## Strategy states

```mermaid
stateDiagram-v2
    [*] --> ARMED : StrategyService.create() (enabled = false)
    ARMED --> FIRED : StrategyFirer.fire(), order placed
    ARMED --> STOPPED : bound refused, or 3 failures
    FIRED --> ARMED : customer switches it on again
    STOPPED --> ARMED : customer switches it on again
```

## Tables written in this flow

| Table | Writer | How |
|---|---|---|
| `strat_strategy` | `StrategyMapper.insert`, `enable`, `disable`, `markFired`, `stop`, `recordFailure` | Row lock `FOR UPDATE` while firing |
| `strat_run` | `StrategyMapper.insertRun`, `markPlaced`, `markFailed` | `UNIQUE (strategy_id, source_event_id)` |
| `orders`, `client_account` | the public order route (flow 2) | Same checks as a customer order |

## Why it is built this way

- **The public order route.** Its token check, validation and idempotency key stand between a strategy defect and a real position.
- **A 5-minute token.** It is used at once for one order. A longer life only makes a leak worse.
- **Bounds.** `maxSpend`, `maxPosition` and a stop after 3 failures limit what a wrong rule can spend.
- **Row lock while firing.** A "switch off" that arrives during a firing waits, then applies to the next quote.
- **Group starts at `latest`.** An old quote must never spend money now.
