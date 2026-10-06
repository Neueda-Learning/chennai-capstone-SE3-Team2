# 0012 A strategy's orders carry a minutes-long token the auth service issues for its account

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Shiva Sai Adithiyan, drafting the day-one design for the team; for the team and the instructor to confirm on day one |

## Context

A strategy fires when nobody is signed in and must place its order through `POST /api/v1/orders`,
whose token check, validation and idempotency are what stand between a strategy bug and a
position. It cannot borrow a customer's token.

## Options considered

| Option | For | Against |
|---|---|---|
| Auth issues a short-lived token for the strategy's account on an internal route behind the service secret | The order route is unchanged and checks it like any token; the credential lives minutes | A new internal route on auth, and a secret that can mint a token for any account |
| The order route accepts a service credential and takes the account from the strategy | No change to auth | A second authentication path on the route that spends money |
| Store a long-lived customer token per strategy | No new route anywhere | A fifteen-minute credential turned into a permanent one |

## Decision

The minted token. Keeping the order route's single path is worth more than avoiding a change to
auth, and the token's life bounds the damage of a leak.

## Consequences

The internal route sits beside the activation route, behind the same kind of secret compared in
constant time. Every firing is a run row, so each order a token placed is traceable.
