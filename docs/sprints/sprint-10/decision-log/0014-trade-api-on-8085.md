# 0014 The Trade REST API, and the modules in it, run on 8085, not 8080

| Field | Value |
|---|---|
| Status | superseded on 2026-10-09: the deployment checklist fixes the ports (order-service 8081, executor-service 8082) |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

`Contracts/API Schemas/portfolio-api.yaml` names `localhost:8080` as the server. Our Trade REST API moved to
8085 in Sprint 9, the team's choice when setting up local development on Windows; the UI, compose
and the runbooks have used 8085 since.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep 8085 and record the deviation | Every setup that works today keeps working | The contract's server line is not ours |
| Move back to 8080 | Matches the contract's server line | Breaks the team's working local setups for a port number |

## Decision

8085. The contract's routes, fields and codes are what binds; the port is configuration.

## Consequences

Recorded here, and raised on day one. Nothing else changes: the UI, compose and the runbooks
already use 8085.

## Superseded, 2026-10-09

The LEAP deployment-readiness checklist fixes every port: the Trade REST API (order-service) on
8081 and the Trade Executor (executor-service) on 8082. Both now run there, set in
`Config/application.yml` and `.env.example`. The reasoning above held for the sprint; the
deployment standard overrides it.
