# 0016 Advice reaches holdings and watchlists through the modules' published api, and says "no signal" rather than guess

| Field | Value |
|---|---|
| Status | proposed |
| Date | 2026-10-07 |
| Decided by | Drafted with the advice extension to the team's acceptance criteria; for the module's owner and the team to confirm at review |

## Context

The team's acceptance criteria for the extension ask for more than the brief did: signals
generated against the customer's holdings and watchlist, consuming the Portfolio service, and
"no signal when data insufficient". The first version answered for one stock at a time, read
nothing of the customer's, and gave a HOLD when there were fewer than 50 days of prices.

## Options considered

| Option | For | Against |
|---|---|---|
| Advice reads holdings and watchlists through `portfolio.api.Holdings` and `watchlists.api.WatchedInstruments`, interfaces each module publishes and implements | The boundary test stays as it is: only an `api` package crosses; each module keeps its own tables and queries | Two small interfaces to keep |
| Advice reads the position and watch tables itself | No new interfaces | A module naming another's tables, which `ModuleBoundaryTest` fails, and the data owned in two places |
| The browser asks for each stock it shows, one request each | No new route | The holdings and watchlists assembled in the browser, and no answer for an API client |
| Insufficient data is a HOLD, as before | No contract change | A HOLD is a view; with 32 days of prices there is none, and the criteria say so |

## Decision

A route for the account, `GET /api/v1/accounts/{id}/advice`, behind the same own-account check
as every account route. It reads what is held through `portfolio.api.Holdings` and what is
watched through `watchlists.api.WatchedInstruments`, holdings first, each symbol once, and asks
the existing signal service for each, kept and refreshed as before. Where the data cannot
support a view, the signal has no direction and no strength, and the reason says what is
missing: too little history, a fund (no daily candles), prices not readable just now, or no
longer traded. One stock's trouble never fails the list.

At most 30 stocks, holdings first, and the response says when it stopped: each new stock's
candles are a price-service call from the platform's daily budget of 400, kept six hours.

## Consequences

The contract (`openapi/advice.yaml`) gains the route, `AccountAdvice` and `AdviceItem`, and a
nullable `direction` and `strength`. The stock page's panel shows "No signal" with the reason.
A Signals page in the UI lists the account's stocks with their views and reasons. A customer
watching more than 30 stocks sees the first 30 until the budget question is revisited.
