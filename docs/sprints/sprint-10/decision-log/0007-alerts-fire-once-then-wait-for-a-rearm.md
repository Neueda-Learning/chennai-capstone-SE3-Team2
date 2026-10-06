# 0007 A price alert fires once, then waits for the customer to re-arm it

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

An ABOVE alert at 1,000 sees a quote every poll while the price sits above 1,000. The brief lists
three behaviours: deactivate, wait for a reset, or fire on every quote past the level, and notes
the third "sends a customer forty messages in a minute".

## Options considered

| Option | For | Against |
|---|---|---|
| Fire once, become TRIGGERED, re-arm by the customer | One message per crossing; the state says it fired; simple to reason about | A customer who wants the next crossing has to re-arm |
| Re-arm automatically when the price crosses back | Catches every crossing | Oscillation around the level is a stream of messages; needs the previous price per alert |
| Fire on every quote past the level | Nothing to manage | Forty messages a minute, through a module somebody else owns |

## Decision

Fire once. The deciding cost is the customer's inbox and the notifications module's load, both of
which the other options spend.

## Consequences

ABOVE fires at the first quote at or above the threshold, BELOW at or below. Re-arming is a route,
subject to the active-alert cap. A replayed quote cannot fire it twice: it is no longer ACTIVE.
