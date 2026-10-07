# 0017 Indicator triggers read daily history, with the live price as today's close

| Field | Value |
|---|---|
| Status | proposed |
| Date | 2026-10-07 |
| Decided by | The team's lead with the strategy extension, after the team's acceptance criteria asked for moving-average crossover and Bollinger-band triggers; for the module's owner and the team to confirm at review |

## Context

The acceptance criteria ask for strategies that run against the real-time price stream and
fire on a moving-average crossover or a Bollinger band. Both need a run of prices, twenty and
fifty of them. The stream carries about one quote a stock a minute; the platform also keeps
daily candles, cached six hours.

## Options considered

| Option | For | Against |
|---|---|---|
| The stream only: the last 20 and 50 quotes | Literally the real-time stream; a crossover can happen within the hour | About 50 minutes before anything can fire after a start or a new strategy; flat outside market hours; minute-to-minute noise fires on moves that mean nothing |
| Daily closes only | The averages a reviewer expects (the 20-day and 50-day); no warm-up; almost no quota | The stream barely matters: a daily close changes once a day |
| Daily closes for the history, the live price as today's close, evaluated on every quote | Fires on the quote that makes the cross, so it acts on the stream; meaningful averages from the first quote; the advice module reads prices the same way | A real 20/50-day crossover is rare, so a live demonstration may not see one fire |

## Decision

The third. Every quote for a stock with an armed indicator strategy reads its daily closes
(the platform's CandleService, kept six hours, so a quote costs no price-service call) and
counts the quote's price as today's close. A crossover is the 20-day on one side of the 50-day
at yesterday's close and on the other now: a buy on the cross above, a sell on the cross below.
The Bollinger band is the 20-day average two standard deviations either side, the deviation
over the 20 as Bollinger takes it: a buy at or below the lower band, a sell at or above the
upper. Without the history for an average or a band, nothing fires.

Everything else is unchanged: the condition is read under the strategy's row lock in the
transaction that fires, the order goes through `POST /api/v1/orders`, the spend and position
bounds and the three-failure stop apply, and every firing is a run, now saying why it fired.

## Consequences

Migration 016 makes `trigger_price` null for indicator triggers and required for the level
ones. The contract gains the two triggers and a `StrategyIndicator`: what each indicator
strategy waits on, from the last quote seen, so the Strategies page shows how far it is from
firing without reading anything. The tests prove a crossover fires with made-up histories;
live, the figures show the distance.
