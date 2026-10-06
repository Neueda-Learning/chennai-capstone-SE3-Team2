# 0014 The Trade REST API, and the modules in it, run on 8085, not 8080

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

`contracts/portfolio-api.yaml` names `localhost:8080` as the server. Our Trade REST API moved to
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
