# 0008 Triggering an alert and queuing its notification are one transaction

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

The watchlists consumer marks an alert TRIGGERED and has notifications deliver it. If those are
separate, a crash between them leaves an alert that says it fired with no message, which a
customer cannot tell from a delivery that never happened.

## Options considered

| Option | For | Against |
|---|---|---|
| One transaction: the trigger and `AlertDelivery.deliver`, which inserts QUEUED | Never TRIGGERED without a queued notification; a failure rolls both back and the next quote retries | Couples the two modules' writes in one transaction |
| Trigger first, deliver after commit, retry failures | The modules' writes stay apart | A window in which the alert says fired and nothing is queued; a retry table to manage |

## Decision

One transaction. Both modules share one database and one process (0001), so atomicity costs
nothing to arrange, and it removes the state a customer could not interpret.

## Consequences

`AlertDelivery.deliver` only records; sending happens after commit (0006). The alert stores the
`notificationId` it got, and the customer sees delivery state on the inbox.
