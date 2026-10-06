# 0009 Watched and alerted symbols reach the poller through a view the watchlists module owns

| Field | Value |
|---|---|
| Status | proposed |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

The executor's poller publishes `market-data` for stocks someone holds or has an order working
on. An alert on a stock nobody holds would never see a quote. The poller runs in another service
(the executor) reading the trading database; its quota model stretches the interval to the number
of symbols.

## Options considered

| Option | For | Against |
|---|---|---|
| The watchlists migration creates a view of its watched and alerted symbols; the poller unions it in | The executor reads a named, documented surface, not the module's tables; the quota model covers the new symbols | The executor depends on a view another team's module owns |
| The watchlists module fetches its own prices from Fauxnance | No change to the executor | The criterion is monitoring `market-data`; a second poller spends the shared quota |

## Decision

The view. It keeps `market-data` the one stream every consumer reads, and the poller's budget
remains the one place the quota is spent on it.

## Consequences

`watch_polled_symbols` is the module's published surface to the executor and is covered by its
tests. More watched symbols lengthen the poll interval within the poller's budget.
