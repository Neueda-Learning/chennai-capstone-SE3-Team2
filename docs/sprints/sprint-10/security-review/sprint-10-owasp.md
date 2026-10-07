# Security review: Sprint 10 extensions

One review across the six modules added to the Trade REST API, written as they are built, from
the programme's Sprint 8 template. The subject is the modules and the routes they add, and what
each changes in the service that was already there. A finding names the module or modules it
belongs to; where a category lands differently on two modules, it says so.

Day one fills in what the design decides. Each row is revisited when a module merges, with the
commit.

## Header

| Field | Value |
|---|---|
| Service | Trade REST API, Sprint 10 modules: preferences, notifications, watchlists, portfolio, advice, strategy |
| Reviewed by | The team; drafted by Shiva Sai Adithiyan on day one |
| Date of review | 2026-10-06 (day one); revised as each module merges |
| Commit reviewed | `release/sprint10`, day-one pack |
| Version of the OWASP Top Ten used | 2021 |

## Categories

The full Top Ten: six modules that consume events, call each other, call a third party and
serve a customer touch more of it than one service did. No row is deleted; one that applies to no
module is dispositioned out of scope with the reason.

| Category | In scope | Finding | Disposition |
|---|---|---|---|
| **A01 Broken access control** | Yes, all six | Every module holds one customer's data in the service that places orders, reachable with any valid token. The first risk the brief names. The two risks it names by name: **a route another module calls that a customer's token can also reach**, and **a customer-supplied destination the platform posts to** (A10). | **Designed out, verified per module as it merges.** Every route compares the `accountId` claim with the path account and answers `403 ACC-403`, logged, on a mismatch (README, "What every module does"). The seams are Java interfaces, not routes, so no resolution or delivery path exists for a token to reach (decision log 0002). Advice has no account in its path: market data is public to any signed-in customer, a decision recorded here. Each module's tests include "another account is refused". |
| **A02 Cryptographic failures** | Yes: strategy, notifications | Strategy's orders carry a token auth mints on an internal route (0012): a secret that can mint a token for any account. Notifications stores where it sent a message. | **Open, strategy and notifications.** The minted token lives minutes; the internal route's secret is compared in constant time, as activation's is. A notification stores its destination masked, never the address in full. Reviewed when strategy and notifications merge. |
| **A03 Injection** | Yes: watchlists, advice, strategy, portfolio | Symbols a customer types reach SQL in four modules (watchlist items, alerts, signals, strategies). | **Open.** Every statement binds its parameters through MyBatis `#{}`, and the Sprint 6 allowlist of statements allowed to interpolate, `MAPPER_INTERPOLATION_ALLOWLIST` in `services/trade-api/manifest.env`, stays empty. Confirmed per module by reading its mapper. |
| **A04 Insecure design** | Yes: watchlists, strategy, notifications | An unbounded alert route turns the market-data consumer into the thing that takes the order service down; a strategy that keeps firing spends a customer's money; a replayed event sends a message twice. | **Designed in.** Caps: 5 watchlists, 50 entries, 20 active alerts, 10 strategies an account (`LIM-409`). Alerts fire once (0007). Strategies bounded by spend, position and three failures; disabling is checked in the transaction that fires (0012). Notifications keyed on `eventId` (0006). Verified by each module's tests. |
| **A05 Security misconfiguration** | Yes: notifications, portfolio, strategy | The modules share the Trade REST API's database role, so nothing but review stops one writing another's tables or the trading tables; portfolio's contract makes `/health` public. | **Open.** `ModuleBoundaryTest` fails the build if a module names another's tables or uses another's insides. Portfolio's `/health` answers status only, no data. Reviewed per module, including any filter or security rule changed. |
| **A06 Vulnerable and outdated components** | Yes | The UI gained `lightweight-charts` (Apache-2.0) on the trading-terminal branch; the modules add no Java dependency by design. | **Open.** `npm audit` and the Maven dependency tree are run and recorded under Evidence before the demonstration. |
| **A07 Identification and authentication failures** | Yes: strategy | A strategy acts with no customer signed in. | **Designed in (0012).** Its token is issued for one account, lives minutes and passes the same verification as any token. Reviewed when strategy merges. Every other module relies on the existing filter and adds no verifier. |
| **A08 Software and data integrity failures** | Yes: notifications, watchlists, portfolio, strategy | Four consumers act on Kafka messages; a forged or hand-published event could notify, fire an alert, book P&L or place an order. | **Partly designed in.** Portfolio books realised P&L only for orders `orders` recorded (0011). The broker is local and unauthenticated, an accepted platform risk outside this sprint; recorded here with its residual risk once each consumer merges. |
| **A09 Security logging and monitoring failures** | Yes | An access-control refusal must be logged; personal data must not be. | **Open.** Every `ACC-403` is logged with the claim and the path account. Email addresses never reach a log: `ResolvedChannel.toString()` leaves the destination out. Confirmed per module. |
| **A10 Server-side request forgery** | Yes: notifications | A notification channel is the shape a customer-supplied destination takes "if nobody thinks about it". | **Designed out (0003).** No module sends to a destination a customer supplies: email goes only to the profile's registered address, read at send time, and there is no webhook or URL channel. |

## Evidence

| Check | How it was performed | Result |
|---|---|---|
| Module boundaries | `ModuleBoundaryTest`, run in the build; a planted violation (a platform class using a watchlists class and naming a watchlists table) failed both checks | Holds |
| Seam reachability | The seams are interfaces in `*.api` packages; no controller maps them | No route exists |
| Preferences, A01 | `PreferencesControllerTest`, `PreferencesIntegrationTest`; live, with curl against the stack: another account's preferences, and a default account that is not the caller's | `403 ACC-403` both times, each logged with the token's account and the one addressed |
| Preferences, A01/A07 | Live: no token, and a token signed with another key | `401` both times, before any preference code runs |
| Preferences, A05 | Sprint 9's CORS rule allowed `GET`, `POST` and `DELETE`; Settings saves with `PUT`, so the rule now lists `PUT` too (`CorsConfig`, `CorsConfigTest`). The origins stay the exact list; no header added | The UI's preflight for a `PUT` answers 200; another origin's is still refused |
| Preferences, A09 | `MaskingTest`, `ProfileChannelResolverTest`: the address on screen is masked, and `ResolvedChannel` prints without it | The address appears only in `contact`, masked, and in no log line |
| Preferences, A03 | `PreferenceMapper` read: every value bound with `#{}` | No interpolation |

## Outstanding items

| Item | Owner | Target date |
|---|---|---|
| A02, A07: review the strategy token route when strategy merges | to assign on day one | before the demonstration |
| A03: read every new mapper for bound parameters | to assign on day one | as each module merges |
| A05: review any filter or security rule a module changes | to assign on day one | as each module merges |
| A06: run and record `npm audit` and the Maven tree | to assign on day one | before the demonstration |
| A08: record the residual risk for each consumer | to assign on day one | as each module merges |
