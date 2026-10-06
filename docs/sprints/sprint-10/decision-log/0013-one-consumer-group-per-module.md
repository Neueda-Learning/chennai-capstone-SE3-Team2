# 0013 One consumer group per module, named as the topic contract reserves

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

Four modules read topics something else already reads: notifications and portfolio read
`trade-events`, watchlists, advice and strategy read `market-data`, strategy reads both. They run
in one process, and Kafka shares a topic's partitions within a group.

## Options considered

| Option | For | Against |
|---|---|---|
| A group per module: `notification-service`, `watchlist-service`, `portfolio-service`, `advice-service`, `strategy-service` | Each module sees the whole stream with its own offsets; a redeployed module does not move another's | Five groups to name and keep distinct |
| One shared group for the Trade REST API | One name | The modules split the partitions and each sees part of the stream, which presents as messages missing at random |

## Decision

A group per module, using the names `contracts/kafka-topics.md` already reserves.

## Consequences

The README lists them. A new consumer must not reuse one.
