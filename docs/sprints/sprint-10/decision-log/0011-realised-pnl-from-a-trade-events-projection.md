# 0011 Realised P&L is accumulated from trade-events, for orders the platform recorded

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

Realised P&L is booked at a sale, `(fillPrice - averageCostAtSale) x quantitySold`, and never
recomputed. A sell's `ORDER_FILLED` on `trade-events` carries the executed price, the quantity and
`averageCostAfter`, which a sale leaves unchanged. Kafka delivers at least once, and testing
publishes events by hand.

## Options considered

| Option | For | Against |
|---|---|---|
| Consume trade-events in `portfolio-service`, book each SELL fill once on its eventId, only when the order exists in `orders` | Booked at the moment of sale; a replay books nothing twice; a hand-published event cannot invent P&L | A read of `orders` per sale |
| Recompute from the sell orders in `orders` on each request | No consumer | Needs the average cost at the time of each sale, which `orders` does not keep |

## Decision

The projection, checked against `orders`. It is what the contract names, and the check keeps test
events out of a customer's figures.

## Consequences

`pf_realised.event_id` is unique. Sales before the module started are not in the projection;
recorded as accepted, since the topic keeps thirty days.
