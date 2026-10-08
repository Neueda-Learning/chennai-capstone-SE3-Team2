# Flow 4: Watchlist

A customer keeps up to 5 watchlists with up to 50 instruments in each. The list lives on the server (`watch_list`, `watch_item`). Each item shows the last price that the `market-data` consumer stored in `watch_latest_quote`.

Part A adds an instrument. Part B shows how the prices arrive. Part C shows how the screen reads the list.

## A. Add an instrument to a watchlist

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant MW as MarketWatch component
        participant IS as InstrumentSearch
        participant WL as MarketWatchList (store)
        participant WA as WatchlistsApi
    end
    box Trade REST API
        participant WC as WatchlistsController
        participant WS as WatchlistService
        participant AA as AccountAccess
        participant IM as InstrumentMapper
        participant WM as WatchlistMapper
    end
    participant DB as trading DB

    C->>IS: type "TCS"
    IS->>IS: InstrumentsApi.search() GET /api/v1/instruments?q=
    C->>MW: pick TCS.NS
    MW->>MW: add(instrument)
    MW->>WL: has(symbol)? then add(symbol)
    WL->>WL: change() - show the row at once (optimistic UI)
    WL->>WA: addItem(accountId, watchlistId, symbol)
    WA->>WC: PUT /api/v1/accounts/{id}/watchlists/{wid}/items/{symbol} + Bearer
    WC->>WS: addItem(accountId, watchlistId, symbol) - @Transactional
    WS->>AA: requireOwn(accountId)
    AA->>AA: caller.canReach()? else 403 ACC-403 (logged)
    WS->>WS: owned(accountId, watchlistId)
    WS->>WM: findOwned(accountId, watchlistId)
    WM->>DB: SELECT FROM watch_list WHERE client_id = ? AND watchlist_id = ?
    alt not this customer's list
        WS-->>WA: 404 WCH-404 (same as a list that does not exist)
    end
    WS->>IM: findBySymbol(symbol)
    IM->>DB: SELECT FROM instrument JOIN equity / mutual_fund
    alt unknown symbol
        WS-->>WA: 404 INS-404
    end
    WS->>WM: lockAccount(accountId)
    WM->>DB: SELECT pg_advisory_xact_lock(1013, clientId) - one writer for this account
    WS->>WM: hasItem(watchlistId, instrumentId)
    alt already there
        WS-->>WC: return (no change)
    end
    WS->>WM: countItems(watchlistId)
    alt 50 or more
        WS-->>WA: 409 LIM-409
    end
    WS->>WM: insertItem(watchlistId, instrumentId)
    WM->>DB: INSERT watch_item (position = last + 1)
    WC-->>WA: 204 No Content
    alt request failed
        WL->>WL: remove the optimistic row, show error
    end
```

## B. How the prices reach the watchlist

The executor's poller prices only symbols that someone needs. The watchlists module owns the view `watch_polled_symbols`. The poller reads that view (decision 0009).

```mermaid
sequenceDiagram
    autonumber
    box Trade Executor
        participant MP as MarketDataPoller
        participant EM as ExecutionMapper
        participant FQ as FauxnanceQuoteClient
    end
    participant DB as trading DB
    participant FX as Fauxnance API
    participant K as Kafka market-data
    box Trade REST API
        participant ML as MarketDataListener
        participant QE as QuoteEvaluator
        participant LQ as LatestQuoteMapper
    end

    loop each poll interval (60 s or more)
        MP->>MP: pollOnce() -> runCycle()
        MP->>EM: findSymbolsWorthPolling()
        EM->>DB: held + working orders + watch_polled_symbols + strat_polled_symbols
        MP->>MP: schedule.callsPerCycle(n) = ceil(n / 25), quota.canAfford()?
        MP->>FQ: quotes(batch of 25)
        FQ->>FX: GET quotes
        loop each symbol
            MP->>K: publish(symbol, quote) - QUOTE, key symbol
        end
    end
    K->>ML: QUOTE (group watchlist-service)
    ML->>QE: onQuote(value) -> evaluate(MarketQuote.parse())
    QE->>LQ: hold(instrumentId, price, change, stale, quoteAsOf, eventId)
    LQ->>DB: INSERT watch_latest_quote ON CONFLICT DO UPDATE WHERE old.quote_as_of <= new
    Note over QE: then alerts are checked (see flow 7)
```

## C. Read the watchlists

```mermaid
sequenceDiagram
    autonumber
    participant WL as MarketWatchList (store)
    participant WA as WatchlistsApi
    participant WS as WatchlistService
    participant WM as WatchlistMapper
    participant DB as trading DB
    participant LP as LivePrices (UI)

    WL->>WL: refresh()
    WL->>WA: list(accountId)
    WA->>WS: GET /api/v1/accounts/{id}/watchlists -> list(accountId)
    WS->>WM: findForClient(accountId), findItemsForClient(accountId)
    WM->>DB: SELECT watch_list, watch_item LEFT JOIN watch_latest_quote
    WS-->>WL: Watchlist[] with lastPrice, changePercent, stale
    loop every 15 s while the tab is visible
        LP->>LP: tick() - MarketDataApi.quotes() GET /api/v1/quotes
    end
```

## Tables written in this flow

| Table | Writer | How |
|---|---|---|
| `watch_list` | `WatchlistMapper.insert`, `rename`, `delete` | Max 5 for each account, under the advisory lock |
| `watch_item` | `WatchlistMapper.insertItem`, `deleteItem` | Max 50 for each list |
| `watch_latest_quote` | `LatestQuoteMapper.hold` | Upsert only when the quote is newer |

## Why it is built this way

- **Caps (`WatchLimits`).** Without a cap, the alert and item tables grow without limit, and the `market-data` consumer becomes slow.
- **Advisory lock on the account.** Two parallel requests cannot both pass the "count < 5" check.
- **A view for the poller.** The executor does not read the module's tables. The module can change its tables and keep the view.
