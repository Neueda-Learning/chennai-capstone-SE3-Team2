# 0006 A notification is recorded before the offset is committed, and sent afterwards

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

Notifications consumes `trade-events` and sends email through Gmail. Consumption and delivery
fail differently: a slow mail provider must not stall a partition, and a crash must not lose a
customer's message or send it twice. Kafka delivers at least once.

## Options considered

| Option | For | Against |
|---|---|---|
| Record QUEUED keyed on eventId, commit the offset, send from a dispatcher | A provider outage queues mail instead of stalling consumption; a replay is a no-op on the unique key; delivery state is visible | Two moving parts; a message may wait a dispatcher interval |
| Send inside the consumer, then commit | Simplest; the message goes at once | A Gmail outage blocks the partition and every account on it; a crash after sending and before committing sends twice |

## Decision

The ledger. The brief says it in so many words: commit once the notification is durably recorded
for delivery, not once the provider confirms.

## Consequences

`notif_notification.event_id` is unique; QUEUED, SENT and FAILED are tracked with attempts. The
channel is resolved at send time, so a preference changed while a message waits is honoured.
