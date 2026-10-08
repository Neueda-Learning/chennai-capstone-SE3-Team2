# Flow 8: Trade signals (advice)

The advice module gives a BUY, SELL or HOLD signal for a stock. It uses one methodology: **20-day SMA against 50-day SMA, confirmed by RSI(14)**. Every answer carries the disclaimer: "Information, not advice." The module has no table. Signals live in memory (decision 0015). A customer can ask for one stock, or for every stock they hold or watch (decision 0016).

## A. Signals for the customer's holdings and watchlists

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    box Angular UI
        participant SG as Signals component
        participant AV as AdviceApi
    end
    box Trade REST API - advice module
        participant AC as AccountAdviceController
        participant AAS as AccountAdviceService
        participant AS as AdviceService
        participant LP as LatestPrices (memory)
        participant ME as Methodology
    end
    box other modules (api only)
        participant HO as portfolio api.Holdings
        participant WI as watchlists api.WatchedInstruments
    end
    participant CS as CandleService (core)
    participant DB as trading DB
    participant FX as Fauxnance API

    C->>SG: open Signals
    SG->>SG: refresh()
    SG->>AV: forAccount(accountId)
    AV->>AC: GET /api/v1/accounts/{id}/advice + Bearer
    AC->>AAS: forAccount(accountId)
    AAS->>AAS: AccountAccess.requireOwn() (403 before any read)
    AAS->>HO: heldSymbols(accountId)
    HO->>DB: SELECT symbols FROM position JOIN instrument
    AAS->>WI: watchedSymbols(accountId)
    WI->>DB: SELECT symbols FROM watch_item JOIN watch_list
    AAS->>AAS: union, holdings first, limit 30 (truncated flag)
    loop each symbol
        AAS->>AAS: signalFor(symbol, now)
        alt unknown or fund
            AAS->>AAS: none(symbol, NOT_TRADED or FUND)
        else stock
            AAS->>AS: signal(symbol)
            alt kept and computed less than 10 min ago
                AS-->>AAS: kept signal
            else
                AS->>AS: compute(symbol, now)
                AS->>CS: candles(symbol, SIX_MONTHS) - cached 6 h
                CS->>FX: GET daily candles (on cache miss)
                AS->>LP: of(symbol) - latest quote from market-data
                AS->>AS: today's live price counts as today's close
                AS->>ME: read(closes)
                ME->>ME: sma(20), sma(50), Wilder RSI(14), gap %, strength
                ME-->>AS: Reading(direction, strength, reason, days)
                AS->>AS: keep(symbol, signal) - LRU cap
            end
            alt PricingUnavailable or ChartUnavailable
                AAS->>AAS: none(symbol, UNREADABLE) - no guess
            end
        end
    end
    AAS-->>SG: AccountAdvice {items[held, watched, signal], truncated, disclaimer}
```

## B. The signal for one stock, and how it stays fresh

```mermaid
sequenceDiagram
    autonumber
    participant ASG as AdviceSignal component
    participant ADC as AdviceController
    participant AS as AdviceService
    participant J as AdviceRefreshJob (own thread)
    participant K as Kafka market-data
    participant ML as AdviceMarketDataListener
    participant LP as LatestPrices

    ASG->>ASG: read() on the instrument page
    ASG->>ADC: AdviceApi.signal(symbol) - GET /api/v1/advice/{symbol}
    ADC->>AS: signal(symbol)
    alt fund
        AS-->>ASG: 422 VAL-422 (no daily candles for a fund)
    end
    AS-->>ASG: Signal {direction, strength, reason, figures, methodology, disclaimer}

    K->>ML: QUOTE (group advice-service)
    ML->>LP: onQuote(value) -> offer(message, json)
    LP->>LP: keep only if newer than the price held

    loop every 5 minutes
        J->>AS: refreshAll()
        AS->>AS: forget symbols not asked for in 1 day
        AS->>AS: compute() again for each kept symbol
    end
```

## The rule

```mermaid
flowchart TD
    A["Daily closes + today's live price"] --> B{"50 or more closes?"}
    B -- no --> H1["HOLD: no 50-day average yet"]
    B -- yes --> G["gap = (SMA20 - SMA50) / SMA50 x 100"]
    G --> L{"abs(gap) under 0.25 %?"}
    L -- yes --> H2["HOLD: averages level"]
    L -- no --> U{"gap > 0?"}
    U -- yes --> OB{"RSI >= 70?"}
    OB -- yes --> H3["HOLD: overbought"]
    OB -- no --> BUY["BUY"]
    U -- no --> OS{"RSI <= 30?"}
    OS -- yes --> H4["HOLD: oversold"]
    OS -- no --> SELL["SELL"]
```

Strength (0 to 100) = `60 x min(1, |gap| / 4) + 40 x clamp((RSI - 50) / 20)`.

## Tables in this flow

The advice module writes no table. It reads `position` through `Holdings`, `watch_item` through `WatchedInstruments`, and `instrument` through `InstrumentMapper`.

## Why it is built this way

- **One methodology.** One rule that we can explain is better than three that we cannot. A combined score looks like a recommendation.
- **Computed on request, then on a timer, never for each quote.** A page that never asks costs nothing.
- **No signal from bad data.** A fund, a missing chart or a failed price gives "no signal", not a guess.
