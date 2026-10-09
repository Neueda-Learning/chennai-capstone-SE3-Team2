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

Built 2026-10-07 with the strategy module:

- **The route is `POST /internal/strategy-tokens`** on auth, behind `InternalSecretGuard`, the
  activation route's guard. It mints only for an account that has a login (`404 ACT-404`
  otherwise: there is nobody to act for), for 300 seconds, with the customer's roles and
  `STRATEGY` beside them. The order route needed no change; the role tells a log which orders a
  strategy placed.
- **The order is idempotent at the route as well as in the module.** Its idempotency key is
  `strategy-{strategyId}-{quoteEventId}`, and the run is keyed on the same quote, so a replayed
  quote finds its run, and a retried call finds its order.
- **Disabling waits for a firing under way.** The firing transaction holds the strategy's row
  lock across the call to the order route (a 2-second connect and a 10-second read at most), so a
  disable that lands mid-firing takes effect for the next quote, never half-way through one. The
  order route's own transaction touches no strategy table, so the two cannot deadlock.
- **A new `strategy-service` group starts at the latest offset.** A quote from before the module
  ran must never spend a customer's money now.
- **The executor's poller reads `strat_polled_symbols`**, a view of the instruments an enabled,
  armed strategy waits on, as it reads the watchlists view (0009): a strategy on a stock nobody
  holds still sees the quote that fires it.
