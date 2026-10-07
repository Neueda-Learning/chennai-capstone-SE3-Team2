# Sprint 10: extensions

The scope we bring to the instructor on day one, and the shape every module is built to. The
brief is `sprint-10-extensions/` in the programme repository; the stories are in our tracker.

## Six modules inside the Trade REST API

Not new services: one package per extension under `com.yellow.trade`, each with its own
controllers, services, mappers and tables, its own routes under `/api/v1/` (so the existing
token filter authenticates them), and its own consumer group where it reads Kafka. The brief,
`contracts/portfolio-api.yaml` and the Sprint 11 brief all place them there; our stories said
separate services, and we followed the brief ([0001](decision-log/0001-modules-inside-trade-api.md)).

| # | Package | Kind | Reads | Consumer group | API |
|---|---|---|---|---|---|
| 1 | `com.yellow.trade.preferences` | mandatory | its own table, the customer's profile | none | `services/trade-api/openapi/preferences.yaml` |
| 2 | `com.yellow.trade.notifications` | mandatory | `trade-events` | `notification-service` | `services/trade-api/openapi/notifications.yaml` |
| 3 | `com.yellow.trade.watchlists` | mandatory | `market-data` | `watchlist-service` | `services/trade-api/openapi/watchlists.yaml` |
| 4 | `com.yellow.trade.portfolio` | mandatory | positions, `trade-events` | `portfolio-service` | `contracts/portfolio-api.yaml` (binding) |
| 5 | `com.yellow.trade.advice` | stretch | daily candles, `market-data` | `advice-service` | `services/trade-api/openapi/advice.yaml` |
| 6 | `com.yellow.trade.strategy` | stretch | `market-data`, `trade-events` | `strategy-service` | `services/trade-api/openapi/strategy.yaml` |

The group ids are the ones `contracts/kafka-topics.md` reserves, and no two consumers share one
([0013](decision-log/0013-one-consumer-group-per-module.md)).

### The chain, and the build order

```
preferences  ──ChannelResolver──▶  notifications  ◀──AlertDelivery──  watchlists
portfolio   (independent)          advice (independent)     strategy (auth + the orders route)
```

Built and merged in this order, one feature branch each: preferences, notifications,
watchlists, portfolio, advice, strategy. Shared files (`docker-compose.yml`, `data/db/local/setup.sql`,
the test schema lists) grow by one entry per module, so the order is also the merge order.

### The two seams

Java interfaces, never HTTP routes, so no customer token can reach either
([0002](decision-log/0002-seams-are-java-interfaces.md)). Each lives in its module's `api`
package, the only thing another module may import; `ModuleBoundaryTest` fails the build if
anything else crosses, or if a module names another's tables.

| Seam | Interface | What it answers when nothing is stored, or it fails |
|---|---|---|
| notifications → preferences | `preferences.api.ChannelResolver#resolve(accountId)` → `ResolvedChannel(channel, destination, fromDefault)` | Nothing stored: EMAIL to the profile's address, `fromDefault` true ([0004](decision-log/0004-default-channel-when-nothing-is-stored.md)). The resolver failing leaves the notification QUEUED to be retried, never sent on a guess. |
| watchlists → notifications | `notifications.api.AlertDelivery#deliver(AlertNotice)` → `DeliveryReceipt(notificationId, duplicate)` | Queues in the caller's transaction, so a failure rolls the trigger back and the next quote fires it again ([0008](decision-log/0008-trigger-and-enqueue-in-one-transaction.md)). |

## What every module does

- Every route compares the token's `accountId` claim with the account in the path; a mismatch is
  `403 ACC-403` and is logged. Public market data (advice) has no account in its path.
- Errors leave as `{ "errorCode", "message" }`. New codes, only where nothing in the catalogue
  fits: `LIM-409` (a per-account cap reached), `NTF-404`, `WCH-404`, `STR-404` (no such record on
  this account).
- A consumer acknowledges after its database write, is idempotent on `eventId`, and dead-letters
  what can never be processed.
- The OpenAPI file is written before the controller; a contract test fails the build when the
  code and the file drift, as `OpenApiExtensionContractTest` already does for our extensions.
- Each module owns its tables, prefixed `pref_`, `notif_`, `watch_`, `pf_`, `advice_`, `strat_`,
  in its own migration.

## Platform changes the modules need

- **Cancelling an order publishes `ORDER_CANCELLED`** on `trade-events`. Today only the executor
  publishes (fills and rejections), so a cancellation reaches no consumer. Built with
  notifications.
- **The poller prices watched and alerted stocks**, not only held and working ones, or an alert on
  a stock nobody holds never sees a quote. The watchlists module owns a view of its symbols; the
  executor's poller reads it ([0009](decision-log/0009-watched-symbols-reach-the-poller-through-a-view.md)).
- **The browser may send `PUT`.** Sprint 9's CORS rule allowed `GET`, `POST` and `DELETE`;
  saving preferences is a `PUT`, so the rule lists it too. Built with preferences.
- **A sale that closes a position announces its average cost.** The executor published
  `averageCostAfter: null` for one, since the position row is gone; realised P&L needs the cost
  the units were sold against ([0011](decision-log/0011-realised-pnl-from-a-trade-events-projection.md)).
  Built with portfolio.
- **Auth issues a short-lived token for a strategy**, on an internal route behind the service
  secret, so a strategy's order goes through the order route like any other
  ([0012](decision-log/0012-strategy-identity-is-an-auth-minted-short-token.md)).

## Not built this sprint

Percentage-move alerts, a push channel to the browser, digest emails, SMS (not provisioned),
alert history per delivery, tax lots, time-weighted returns, performance attribution, fund charts
(the NAV service has no history yet), and anything that needs a price history the platform does
not store.

## Deviations to confirm on day one

- The Trade REST API runs on **8085**, not 8080 ([0014](decision-log/0014-trade-api-on-8085.md)).
- Portfolio prices funds from the **MF NAV service**, not Fauxnance, and the universe is **INR
  only**, which the contract allows when said ([0010](decision-log/0010-portfolio-prices-funds-at-nav-inr-only.md)).
- One login has one account, so **"default account"** is paired with a default landing screen to
  make the preference visible ([0005](decision-log/0005-default-account-and-landing-screen.md)).
- `PricedPosition.quantity` in `contracts/portfolio-api.yaml` is an integer; a fund holds
  fractional units, so the portfolio module answers it as a number (`12.345678`). A stock's is
  still whole.

## Open questions for the instructor

1. The stories mention a Sprint 10 harness, a `manifest.env` and "two publish commands". Where
   does the manifest go, what are its keys, and how many decision-log entries does it ask for?
   The commands are ready: `scripts/publish-trade-event.sh` and `scripts/publish-market-data.sh`.
2. Our stories say separate services; the brief and the portfolio contract say modules. We built
   modules: confirm.

## In this folder

| File | What |
|---|---|
| `backlog.md` | Every story with its acceptance criteria, all six modules and the process stories |
| `decision-log/` | One file per decision, in the programme's template shape |
| `security-review/sprint-10-owasp.md` | One OWASP Top Ten review across the six modules, written as we build |
