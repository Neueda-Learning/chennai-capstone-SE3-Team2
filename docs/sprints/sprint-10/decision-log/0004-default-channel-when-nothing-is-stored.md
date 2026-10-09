# 0004 With no preference stored, alerts go by email to the registered address

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

A customer who has never opened Settings has no preference row, and a fill or an alert still has
to reach them. The brief: "hold the message, or send on a documented default. Choosing nothing
means the message is lost and nobody finds out."

## Options considered

| Option | For | Against |
|---|---|---|
| Send on a documented default: EMAIL to the profile's address, plus the inbox | The customer hears about their own trade from the first day; the routing is still real and recorded | Email a customer never chose; one more email for those who did not want it |
| Hold the message until a preference is set | Nothing is sent that was not asked for | A rejection the customer never hears about; held messages to reconcile |

## Decision

The documented default. A customer who placed an order expects to hear what happened to it, and
a rejection unheard is the worse failure. `ResolvedChannel.fromDefault` says when the default was
used, and the notification records it.

## Consequences

The default is written on `ChannelResolver` and shown on the Settings screen as what is in force.
A customer with no address resolves to IN_APP rather than to nothing.
