# Flow 7: Price alerts

A customer sets an alert: "tell me when TCS goes ABOVE 4,000". The alert fires one time on the first quote at or past the level. Then it waits until the customer re-arms it (decision 0007). The trigger and the queued notification commit in one transaction (decision 0008).

## A. Set an alert

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant IP as InstrumentPage
        participant WA as WatchlistsApi
    end
    box Trade REST API - watchlists module
        participant AC as AlertsController
        participant AS as AlertService
        participant AA as AccountAccess
        participant IM as InstrumentMapper
        participant WM as WatchlistMapper
        participant AM as AlertMapper
    end
    participant DB as trading DB

    C->>IP: ABOVE 4000 on TCS.NS, click Set alert
    IP->>WA: setAlert(accountId, {symbol, direction ABOVE, threshold 4000})
    WA->>AC: POST /api/v1/accounts/{id}/alerts + Bearer
    AC->>AC: @Valid AlertRequest (threshold > 0)
    AC->>AS: create(accountId, request) - @Transactional
    AS->>AA: requireOwn(accountId)
    AS->>IM: findBySymbol(symbol)
    alt unknown or not tradable
        AS-->>IP: 404 INS-404
    else mutual fund
        AS-->>IP: 422 VAL-422 (no live quote for a fund)
    end
    AS->>WM: lockAccount(accountId)
    WM->>DB: pg_advisory_xact_lock(1013, clientId)
    AS->>AS: requireRoomForAnother(accountId)
    AS->>AM: countActive(accountId)
    AM->>DB: SELECT count(*) FROM watch_alert WHERE client_id = ? AND status = ACTIVE
    alt 20 or more
        AS-->>IP: 409 LIM-409
    end
    AS->>AM: insert(AlertRow)
    AM->>DB: INSERT watch_alert (status ACTIVE)
    AS-->>IP: 201 PriceAlert
```

## B. A quote crosses the level

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka market-data
    box Trade REST API - watchlists module
        participant ML as MarketDataListener
        participant QE as QuoteEvaluator
        participant IM as InstrumentMapper
        participant LQ as LatestQuoteMapper
        participant AM as AlertMapper
    end
    box notifications module
        participant NL as NotificationLedger (api.AlertDelivery)
        participant NM as NotificationMapper
    end
    participant DB as trading DB

    K->>ML: QUOTE TCS.NS 4012.50 (group watchlist-service)
    ML->>QE: onQuote(value) -> evaluate(MarketQuote.parse(value))
    Note over QE: one @Transactional method
    QE->>IM: findBySymbol(symbol)
    QE->>LQ: hold(instrumentId, price, change, stale, quoteAsOf, eventId, now)
    LQ->>DB: upsert watch_latest_quote WHERE old.quote_as_of <= new.quote_as_of
    alt 0 rows (older quote arrived late)
        QE-->>ML: return - nothing changes
    end
    QE->>AM: lockCrossed(instrumentId, price)
    AM->>DB: SELECT ACTIVE alerts WHERE (ABOVE and price >= threshold) OR (BELOW and price <= threshold) FOR UPDATE SKIP LOCKED
    loop each crossed alert
        QE->>NL: deliver(AlertNotice(eventId, clientId, alertId, symbol, direction, threshold, price, asOf))
        Note over NL: Propagation.MANDATORY - refuses a call with no transaction
        NL->>NM: insertQueued(row kind PRICE_ALERT, alert_id set)
        NM->>DB: INSERT notif_notification ON CONFLICT (event_id, alert_id) DO NOTHING
        alt 0 rows (replay)
            NL->>NM: findIdBySource(eventId, alertId)
            NL-->>QE: DeliveryReceipt(firstId, duplicate true)
        else new
            NL-->>QE: DeliveryReceipt(notificationId, false)
        end
        QE->>AM: markTriggered(alertId, price, now, notificationId)
        AM->>DB: UPDATE watch_alert SET status TRIGGERED, notification_id WHERE status = ACTIVE
    end
    Note over QE,DB: commit: alert TRIGGERED and notification QUEUED together
    Note over NL: NotificationDispatcher sends it (flow 5, part B)
```

## C. Re-arm or cancel

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant AL as Alerts component
    participant WA as WatchlistsApi
    participant AS as AlertService
    participant AM as AlertMapper
    participant DB as trading DB

    C->>AL: click Re-arm on a TRIGGERED alert
    AL->>AL: rearm(alert) -> act()
    AL->>WA: rearmAlert(accountId, alertId)
    WA->>AS: POST .../alerts/{aid}/rearm -> rearm(accountId, alertId)
    AS->>AM: findOwned(accountId, alertId) (else 404 WCH-404)
    AS->>AS: lockAccount, requireRoomForAnother (cap 20)
    AS->>AM: rearm(accountId, alertId)
    AM->>DB: UPDATE watch_alert SET status ACTIVE, triggered_at NULL
    C->>AL: click Cancel
    AL->>WA: cancelAlert(accountId, alertId)
    WA->>AS: DELETE .../alerts/{aid} -> cancel()
    AS->>AM: cancel(accountId, alertId)
    AM->>DB: UPDATE watch_alert SET status CANCELLED WHERE client_id = ?
```

## Alert states

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : AlertService.create() (max 20 active)
    ACTIVE --> TRIGGERED : QuoteEvaluator.evaluate(), first quote at or past the level
    TRIGGERED --> ACTIVE : AlertService.rearm()
    ACTIVE --> CANCELLED : AlertService.cancel()
    TRIGGERED --> CANCELLED : AlertService.cancel()
```

## Tables written in this flow

| Table | Writer | How |
|---|---|---|
| `watch_alert` | `AlertMapper.insert`, `markTriggered`, `rearm`, `cancel` | Guarded by `status`. Partial index `ix_watch_alert_active`. |
| `watch_latest_quote` | `LatestQuoteMapper.hold` | Newer quote only |
| `notif_notification` | `NotificationMapper.insertQueued` (through `AlertDelivery`) | Same transaction as the trigger |

## Why it is built this way

- **Fire once.** Firing on every quote past the level sends many messages in a minute.
- **Key `(event_id, alert_id)`.** One quote can cross many customers' alerts. A key on `event_id` alone keeps only the first.
- **`MANDATORY` propagation.** An alert can never show `TRIGGERED` with no notification queued.
- **`SKIP LOCKED`.** Two consumer threads never fire the same alert.
