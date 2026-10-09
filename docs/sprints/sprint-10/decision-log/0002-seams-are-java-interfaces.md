# 0002 The two seams are Java interfaces, never HTTP routes

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

Notifications must resolve a customer's channel through preferences, and watchlists must have an
alert delivered through notifications. Both calls happen with no customer signed in: a Kafka
consumer acts on an event. The brief names, as the finding the review looks for first, "a
resolution route that exists only so another module can call it, reachable by anyone holding a
customer token". The modules run in one process (0001).

## Options considered

| Option | For | Against |
|---|---|---|
| A Java interface per seam, in the callee's `api` package | Unreachable from outside the process; typed; can be replaced behind the interface; no credential to manage | Couples the two modules' release, though they are already one deployable |
| An internal HTTP route behind a service secret | The same shape a separate service would need; testable with curl | A route a mis-set filter or a leaked secret exposes; a second authentication path to review; a network hop inside one process |

## Decision

Interfaces: `preferences.api.ChannelResolver` and `notifications.api.AlertDelivery`. What decided
it is that the safest route is the one that does not exist: nothing in the HTTP layer can reach a
resolution or a delivery, so a customer token cannot either.

## Consequences

The interfaces are the only things a module may import from another, checked by
`ModuleBoundaryTest`. Each seam's behaviour when nothing is stored or the callee fails is written
on the interface (0004, 0008). Should a module ever be split out, the interface is where the HTTP
client goes, and a service credential with it.
