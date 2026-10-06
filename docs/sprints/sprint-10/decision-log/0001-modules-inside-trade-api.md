# 0001 The extensions are modules inside the Trade REST API, not services

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

Two sources disagreed. Our Sprint 10 stories ask for each extension "on its own port, in its own
folder under services/, with its own Dockerfile, compose entry and README", verifying the
platform token itself. The programme's Sprint 10 brief says the opposite in as many words ("The
extensions are not new services"), `contracts/portfolio-api.yaml` says its routes "are served by
the Trade REST API on 8080, as a module", and the Sprint 11 brief says the Trade REST API "hosts
your four extensions". Six extensions are in scope.

## Options considered

| Option | For | Against |
|---|---|---|
| Modules in the Trade REST API | Matches the brief, the binding portfolio contract and Sprint 11; one token filter already authenticates every `/api/v1/` route; no new ports, images or verifiers | Nothing but the build and review stops one module reaching into another, in one Maven project |
| Six separate services | A network hop is a boundary nobody crosses by accident; matches our stories | Six Dockerfiles, ports, compose entries and JWT verifiers before a feature exists; service-to-service credentials for both seams; contradicts the portfolio contract |

## Decision

Modules. The binding contract decides it: portfolio's routes are specified as served by the Trade
REST API, and a contract is not ours to move. The boundary the separate services would have given
for free is held by `ModuleBoundaryTest`, which fails the build when anything but a module's
`api` package is used from outside it, or a module names another module's tables.

## Consequences

The seams become Java interfaces, not routes (0002). The stories' Dockerfile, port and
service-credential tasks do not apply, which we raise with the instructor on day one. The cost
accepted: a defect in one module runs in the process that places orders, so every module is held
to "nothing built in Sprint 6 regresses", and the review checks what each grants itself.
