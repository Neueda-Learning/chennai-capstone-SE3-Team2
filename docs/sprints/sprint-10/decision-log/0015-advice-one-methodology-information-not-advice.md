# 0015 Advice: one methodology, recomputed on a timer, and information rather than advice

| Field | Value |
|---|---|
| Status | proposed |
| Date | 2026-10-07 |
| Decided by | Drafted with the advice module (Sprint 10, zip 5); for the module's owner and the team to confirm at review |

## Context

The stretch brief says the interesting decisions are about cost and cadence, and about what a
signal is allowed to claim: "a generated number presented as a recommendation is a product and
legal problem before it is an engineering one", and "one methodology computed from real candles,
explained in the response and rendered in the UI, is worth more here than three nobody can
defend."

## Options considered

| Option | For | Against |
|---|---|---|
| One methodology: 20- against 50-day SMA, confirmed by RSI(14); recomputed every five minutes; a signal computed only when someone asks | Every figure is shown and explainable; candles are read at most every six hours per stock (the platform's `CandleService` cache); a stock nobody asks about costs nothing | A crossing inside five minutes shows late |
| Recompute on every quote | Fresh to the minute | Mostly noise from a daily methodology; work on every message for every stock, asked about or not |
| Several indicators combined into a score | Looks richer | Nobody can say why it says BUY; a score is read as a recommendation |

## Decision

One methodology, on a timer, on demand. Every response names the methodology, gives the figures
and a sentence saying what produced it, and carries a disclaimer: "Information, not advice.
Computed from delayed educational data; past prices do not predict future ones." The UI shows
the disclaimer before a signal is read and beside it after. A fund has no daily candles here and
is refused (`VAL-422`) rather than given a signal from something else.

## Consequences

No table: signals and the latest prices live in memory and are rebuilt within one poll and one
timer pass after a restart. HOLD covers too little history (fewer than 50 days), averages level
within 0.25%, and a trend RSI contradicts (overbought at 70, oversold at 30).
