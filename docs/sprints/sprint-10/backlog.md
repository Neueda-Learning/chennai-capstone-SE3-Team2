# Sprint 10 backlog

Written on day one, before any module code, for all six modules and the process. Each story's
acceptance criteria are the ones it is done against. Mirrors the stories in our tracker; where
we decided something the stories leave open, the decision log entry is named.

## S10-0 Day one: briefs, scope, backlog

- Every member has read all the briefs, and the scope is confirmed with an instructor on day one.
- This backlog covers all six modules before code is written.
- The APIs of the five modules without a contract are written as OpenAPI and brought to the
  instructor: `services/trade-api/openapi/{preferences,notifications,watchlists,advice,strategy}.yaml`.
- Work is planned by the chain, not one person per module.

## S10-1 The two integration seams

- `ChannelResolver` and `AlertDelivery` are written down as Java interfaces with their shapes and
  their behaviour when nothing is stored or the callee fails (README, "The two seams").
- How a module reaches another is recorded: in process, through the published interface, never
  through a route a customer token could call ([0002](decision-log/0002-seams-are-java-interfaces.md)).
- Every consumer has an explicit group id, listed in the README and shared with nothing else.
- `ModuleBoundaryTest` fails the build if anything but a module's `api` package crosses a
  boundary, or a module names another's tables.

## S10-2 Customer preferences (`com.yellow.trade.preferences`)

- A record per customer holds a default account, a default landing screen and an alert channel;
  it survives a restart (a table, not memory).
- The contact detail is referenced from the customer's profile, not copied
  ([0003](decision-log/0003-contact-details-are-referenced-not-copied.md)).
- The stored landing screen is applied at the customer's next sign-in in the Angular
  application ([0005](decision-log/0005-default-account-and-landing-screen.md)).
- `ChannelResolver` answers the documented default when nothing is stored
  ([0004](decision-log/0004-default-channel-when-nothing-is-stored.md)); there is no resolution
  route a customer can reach.
- Every read and write is scoped to the token's account; another account's preferences are
  refused with `ACC-403`, not answered empty.
- Tests: saved and read back; survive a restart; another account refused; the default applied at
  next sign-in; the default when nothing is stored.

## S10-3 Customer notifications (`com.yellow.trade.notifications`)

- Consumes `trade-events` in group `notification-service`: filled, rejected and cancelled each
  produce a notification.
- The Trade REST API publishes `ORDER_CANCELLED` on a cancel (today nothing does).
- The channel is resolved through `ChannelResolver` on every send and recorded on the
  notification; changing the preference changes the channel used.
- The offset is committed once the notification is recorded QUEUED; QUEUED, SENT and FAILED are
  tracked apart from it ([0006](decision-log/0006-notification-ledger-offset-after-queued.md)).
- Keyed on `eventId`: a replayed event records and sends nothing twice.
- Delivered by email through the platform's mail sender, and kept in the in-app inbox; history is
  readable only by the owning account.
- `AlertDelivery` is implemented for the watchlists module.
- No destination comes from a customer: email goes only to the profile's address.
- Tests: a fill notifies; a rejection notifies; a cancellation notifies; the channel follows the
  preference; a replay sends once; history refused to another account.

## S10-4 Watchlists and price alerts (`com.yellow.trade.watchlists`)

- Consumes `market-data` only, in group `watchlist-service`; never `orders` or `trade-events`.
- A customer creates watchlists, adds and removes instruments, and sees the latest price from the
  stream beside each entry; a watched instrument need not be held.
- A price alert has a threshold and a direction, is checked against every quote through an
  index on the active alerts, fires once and waits for a re-arm
  ([0007](decision-log/0007-alerts-fire-once-then-wait-for-a-rearm.md)).
- A crossed alert is handed to `AlertDelivery` in the transaction that marks it triggered
  ([0008](decision-log/0008-trigger-and-enqueue-in-one-transaction.md)); never written only to a
  log.
- Alert state (ACTIVE, TRIGGERED, CANCELLED) and its notification are visible to the customer.
- Caps: 5 watchlists, 50 entries each, 20 active alerts per account (`LIM-409`).
- The poller polls watched and alerted stocks
  ([0009](decision-log/0009-watched-symbols-reach-the-poller-through-a-view.md)).
- The market watch in the UI becomes the server-side watchlist.
- Tests: a watchlist is scoped to its account; an added instrument shows a live price; a crossing
  quote triggers; a quote short of it does not; delivery goes through notifications; caps hold;
  a replayed quote fires nothing twice.

## S10-5 Portfolio and P&L (`com.yellow.trade.portfolio`)

- Implements `contracts/portfolio-api.yaml` exactly: the summary, positions and P&L routes and
  `/health`, the field names, and the error catalogue including `MKT-503`.
- Prices from live quotes, batched 25 to a call and cached; funds at their NAV; every priced
  figure carries `priceAsOf` and `stale` ([0010](decision-log/0010-portfolio-prices-funds-at-nav-inr-only.md)).
- Nothing priced: `503 MKT-503`. Some priced: `200`, `partial` true, the unpriced positions marked
  as the contract says.
- Realised P&L is booked at each sale from `trade-events` (group `portfolio-service`), keyed on
  `eventId`, and only for orders the platform recorded; never recomputed from today's price
  ([0011](decision-log/0011-realised-pnl-from-a-trade-events-projection.md)).
- Read-only on the trading tables; writes only its own.
- Holdings and the dashboard read these routes.
- Tests: priced from a live quote; a cached quote reused in the window; none priced is `MKT-503`;
  some priced is partial; realised booked at the sale; a replay does not double-count; another
  account refused.

## S10-6 Trade advice and signals (`com.yellow.trade.advice`), stretch

- One methodology from real candles: the 20-day against the 50-day moving average, confirmed by
  RSI(14); BUY, SELL or HOLD with a strength, the figures and a sentence.
- Candles cached for a day; the latest price from `market-data` (group `advice-service`);
  recomputed on a timer, not per quote.
- The response and the screen say it is information, not advice.
- Tests: an uptrend reads BUY; a downtrend SELL; too little history HOLD with the reason; a fund
  refused; candles cached.

## S10-7 Automated strategy execution (`com.yellow.trade.strategy`), stretch

- A rule buys a quantity when the price falls through a level, or sells when it rises through
  one; triggered by `market-data`, outcomes read from `trade-events` (group `strategy-service`).
- Orders go only through `POST /api/v1/orders`, with a minutes-long token auth issues for the
  strategy's account ([0012](decision-log/0012-strategy-identity-is-an-auth-minted-short-token.md)).
- Bounded in code: maximum spend, maximum position, stopped after three failures; disabling stops
  it at once (checked in the transaction that would fire it); 10 strategies per account.
- Every firing, and every refusal, is a run the customer can read.
- Tests: fires on the crossing quote; does not fire short of it; refuses past the spend or the
  position; a disabled strategy does not fire; stops after three failures; a replayed quote
  fires once.

## S10-8 End-to-end on live data

- A stored channel preference drives a trade notification; a crossing quote's alert arrives on
  the same channel; nothing stubbed.
- Each module's routes check the same platform token; the Angular application reaches all six.
- `scripts/publish-trade-event.sh` and `scripts/publish-market-data.sh` publish a trade event and
  a quote in the contract's envelopes, for the harness and the demonstration.

## S10-9 One combined OWASP review

- `security-review/sprint-10-owasp.md`: one document, the full Top Ten across the six modules,
  each finding naming its module; every category dispositioned, out-of-scope ones with the
  reason.
- Starts with access control; looks by name for a route another module calls that a customer can
  reach, and for a customer-supplied destination the platform posts to.
- Findings fixed with the commit, mitigated, or accepted with the residual risk; open ones in the
  outstanding table with an owner.

## S10-10 Architecture decision log

- `decision-log/`, one file per decision in the template's shape, written as decided; at least
  the number the manifest asks for.

## S10-11 Platform demonstration

- The running platform and the six modules against a live stack: the chain, a replay producing
  one message, and a portfolio figure traced to a live quote.
- Every member can walk any module unaided.
