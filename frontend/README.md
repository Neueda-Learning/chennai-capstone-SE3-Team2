# Trading UI

The Angular front end for the platform: sign-in, a dashboard, an order ticket
and a blotter, against the Trade REST API (`localhost:8085`) and the Auth
service (`localhost:3000`). Sprint 9.

## Requirements

- Node `^20.19`, `^22.12` or `>=24` — Angular 21 refuses to start below.
- npm 10 or later. `package-lock.json` is committed; install with `npm ci`.

## Commands

```bash
cd frontend
npm ci          # exactly the committed lock file
npm start       # dev server on http://localhost:4200
npm run build   # production bundle into dist/
npm test        # unit tests (Vitest), once, then exit
```

## Engineering rules

- Angular 21, **standalone components and signals throughout. No `NgModule`.**
  The application is zoneless: change detection follows signals.
- **Observables stop at `HttpClient`.** The wrappers in `core/api/` turn each
  call into a `Promise`; components hold their state in signals.
- Every spec is a `*.spec.ts` beside the file it covers.

## Where things live

Decided once, so a reviewer can find a feature without searching. Each row
names the story that fills it.

```
frontend/
├── src/
│   ├── main.ts                 bootstraps App with app.config.ts
│   ├── styles.css              tokens and the shared patterns (buttons, fields, alerts, tables)
│   ├── generated/              typed clients generated from contracts/ — machine output, never edited  (630)
│   │   ├── trade/              from contracts/trade-api.yaml
│   │   ├── auth/               from contracts/auth-api.yaml
│   │   └── extensions/         from services/trade-api/openapi/trade-api-extensions.yaml — our own routes
│   └── app/
│       ├── app.ts / app.html   the shell: header, navigation, <router-outlet>
│       ├── app.config.ts       providers: router, HttpClient, the one interceptor, the API base paths
│       ├── app.routes.ts       every route, lazily loaded; the guard on all but sign-in  (633)
│       ├── core/               rules that are not a screen
│       │   ├── config/         where the two APIs are                                (630)
│       │   ├── api/            our services around the generated clients            (630, 631, 634, 636)
│       │   ├── session/        the signed-in session: tokens, account, sign-out, renewal  (631)
│       │   ├── http/           the one interceptor that sets Authorization          (632)
│       │   ├── guards/         the route guard and the return-address check         (633)
│       │   └── errors/         every error code mapped to a sentence                (635)
│       ├── features/           one folder per screen
│       │   ├── sign-in/                                                              (631)
│       │   ├── apply/          opening an account; public, like sign-in
│       │   ├── dashboard/      account summary and the blotter                      (629, 636)
│       │   ├── order-ticket/                                                         (634)
│       │   ├── blotter/        order history, status badges, the NEW re-read, cancel  (636)
│       │   ├── holdings/       what the account holds, on the dashboard
│       │   └── cash/           adding and withdrawing cash, and every transfer
│       └── shared/             small pieces used by several screens, and the re-read policy  (635, 636)
├── e2e/                        Playwright journeys against the running stack        (637)
└── scripts/                    the bundle secret scan                                (638)
```

## The typed clients are generated

Both clients in `src/generated/` are written by
[OpenAPI Generator](https://openapi-generator.tech) from `../contracts/`, never
by hand.

| | |
|---|---|
| Generator | `typescript-angular`, jar **7.25.0** |
| Runner | `@openapitools/openapi-generator-cli` **2.41.0**, an exact devDependency |
| Configuration | `openapitools.json`: one entry per contract, each into its own subdirectory |
| `trade` | `../contracts/trade-api.yaml` → `src/generated/trade/` |
| `auth` | `../contracts/auth-api.yaml` → `src/generated/auth/` |
| `extensions` | `../services/trade-api/openapi/trade-api-extensions.yaml` → `src/generated/extensions/` |

**The third client is ours.** The Trade REST API serves routes the programme's
contracts do not describe -- applying for an account, the instrument list, and
deposits and withdrawals -- and the contracts are not ours to edit. So those
routes are described in `services/trade-api/openapi/trade-api-extensions.yaml`,
beside the service that implements them, and generated like the other two: no
call in this application is hand-written. In the service's own build,
`OpenApiExtensionContractTest` fails if a route, a field or an error code in
that file stops matching the Java code. It is OpenAPI 3.0.3, so it is generated
with the generator's validation on.

Both versions are pinned, so two people generating on different days get the
same files.

```bash
npm run generate:api    # regenerate both; needs Java 11+ (the generator runs on the JVM)
npm run check:api       # regenerate into a scratch directory and diff against the committed tree
```

Run `check:api` before every review, and `generate:api` on every contract
change, committing the result in the same commit as the code that adapts to it.
The build itself needs neither Java nor the network: the output is committed.

**Never edit a file in `src/generated/`.** Nobody reviews its formatting or its
naming; the next generation reverts anything tidied. When a generated shape is
awkward, wrap it in a service under `src/app/core/api/`. `check:api` fails on a
changed file and on a file the generator did not write.

**`skipValidateSpec` is set, knowingly.** The generator's OpenAPI 3.1 validator
asks for `info.license.identifier`, which the specification makes optional and
the contracts do not carry. The contracts are correct and are not ours to edit.

**The tree contains an `NgModule`, and nothing uses it.** The generator always
writes `api.module.ts` and re-exports it from its own `index.ts`, so it cannot
be left out without editing generated files. This workspace provides the
clients with the generator's standalone `provideApi()` instead, in
`src/app/core/api/provide-clients.ts`, which is also where the clients are
pointed at `src/environments/environment.ts`.

## Error messages

Both contracts answer a failure with one envelope, `{ "errorCode", "message" }`.
`src/app/core/errors/error-messages.ts` turns it into a sentence a trader can
act on, and `<app-error-message>` (in `shared/`) renders it as an alert.

- **It branches on `errorCode`, never on `message`.** The message is for a
  developer reading a log; it changes without notice and is never shown.
- **Complete by construction.** The mapping is a `Record` over the code types
  the generator read out of both contracts, so a code added to a contract stops
  the build until it has a sentence. Its spec checks every generated code, and
  that together they are the eight the catalogues declare.
- **Our extension routes' codes** come from their own generated client and are
  held to the same rule: `PAY-400` (not enough available cash to withdraw),
  `PAY-404` (no bank account on file), `PAY-409` (a retry key reused for a
  different transfer) and `RATE-429` (too many applications).
- **Two codes outside every catalogue** are mapped too, because our services send
  them: `AUTH-429` (the login throttle) and `SRV-500`.
- **Status 0** — no response at all, usually a service that is down or a CORS
  rule that does not allow this origin — has its own sentence, and an unknown
  code falls back to one. Neither renders a blank panel.

## Opening an account

`/apply` is public, like `/sign-in`: an applicant has no login yet. It is linked
from the sign-in page ("Open an account"). The form checks what the server
checks before sending -- name, a date of birth at least 18 years ago, email, a
10-digit mobile (the form adds `+91`), PAN, the bank account number (9 to 18
digits) and its IFSC -- and posts to `POST /onboarding/applications` through the
generated extensions client, with no token.

The confirmation reads the same whatever became of the application: the server
answers a PAN or email already registered exactly like a new one, so the page
cannot be used to find out who is a customer. KYC decides shortly after; a
customer who passes is emailed the activation link.

## Signing in

`/sign-in` posts the username and password to `POST /auth/login` on the Auth
service and keeps the token pair it returns.

- **Where the tokens live:** `sessionStorage`, through `core/session/session.ts`
  and nowhere else. A reload keeps the customer signed in; closing the tab, or
  **Sign out** in the header, clears both. An expired access token counts as
  signed out and is cleared; the refresh token stays, to renew it.
- **The account comes from the token.** Its `accountId` claim is the one
  account this session may trade. The browser reads the claims but cannot
  verify them -- it holds no secret -- and does not need to: every API checks
  the token on every call.
- **A refused sign-in** shows "That username and password don't match" for
  `AUTH-401`, and the catalogue's sentence for everything else, including the
  throttle's `AUTH-429`.
- The username field, the password field and the submit button carry
  `data-testid="sign-in-username"`, `"sign-in-password"` and `"sign-in-submit"`,
  so the Playwright journeys find them by identifier, not by label text.

The Auth service answers the browser only because it allows this origin:
`CORS_ALLOWED_ORIGINS` (default `http://localhost:4200`) in `.env`.

To sign in for real you need a login: apply, pass KYC and activate as in
`docs/runbooks/account-activation.md`, "Running it end to end".

## Staying signed in

The access token lasts **15 minutes**; the refresh token **7 days**, and works
**once**. `core/session/session-refresh.ts` exchanges the refresh token with
`POST /auth/refresh` **a minute before the access token runs out**, and keeps
the new pair. Nobody is signed out in the middle of a demo.

| The exchange... | Then |
|---|---|
| succeeds | the new pair replaces the old; the next renewal is scheduled from the new token |
| is refused (`401`: expired, revoked, or already used; `422`: malformed) | the session ends and the customer is taken to sign-in, returning to the same page |
| gets no answer, or a `5xx` | the session stays; tried again every 30 seconds while the access token lasts |

The route guard renews too: an access token that ran out while the laptop
slept, or before a reload, is renewed on the next navigation if the refresh
token still works, instead of sending the customer to sign-in.

**One exchange at a time.** The Auth service treats a refresh token presented
twice as stolen and **revokes every refresh token the user has**, so two
renewals racing -- the timer and the guard -- share one request.

**Two consequences worth knowing:**

- **A duplicated tab** (the browser's *Duplicate*) copies `sessionStorage`, so
  both tabs hold the same refresh token. The first to renew rotates it; the
  second then presents a used token, and the theft rule signs both out. Open a
  new tab and sign in instead.
- **Sign out** clears both tokens from the browser, but the Auth contract has
  no route to revoke a refresh token, so it stays valid on the server until
  it expires. Nothing in the browser holds it any more.

The refresh token sits in `sessionStorage` beside the access token. It is
worth more -- days, not minutes -- but the contract hands it over in the
response body rather than as an `HttpOnly` cookie, so script can reach it
wherever it is kept; rotation and the theft rule are what limit a stolen one.

## The bearer token, and where it is not sent

`src/app/core/http/auth.interceptor.ts` is the only code that sets an
`Authorization` header, registered once in `app.config.ts` with
`withInterceptors`. A service added tomorrow cannot forget it, and cannot set
it somewhere it should not go.

It decides by an **allow list**, built from the two configured origins:

| Origin | Paths | Token |
|---|---|---|
| Trade REST API (`tradeApiUrl`) | `/api/v1/**` | yes |
| Auth service (`authApiUrl`) | `/auth/me` | yes |
| Auth service | `/auth/login`, `/auth/register`, `/auth/refresh` | no — `security: []` in the contract |
| anything else: the market-data API, an analytics script, this app's own origin, a lookalike such as `localhost:8080.evil.example` | | **no** |

The token is a bearer credential: whoever holds it is the customer until it
expires. Sent to a third party it lands in their access log, their analytics
and their error tracker, and stays there. An allow list fails closed when a new
third party appears; a deny list fails open, silently.

The generated clients could add a header themselves, but only if given
credentials, and `provideClients` gives them a base path and nothing else. A
spec proves a generated call made without the interceptor carries no header.

The two assessed cases are named for what they assert, in
`auth.interceptor.spec.ts`: *attaches the bearer token to a request to the
Trade REST API* and *does not attach the bearer token to a request to a
third-party origin*.

The Trade REST API answers the browser only because it allows this origin:
`CORS_ALLOWED_ORIGINS` again (`services/trade-api`, `CorsConfig`). Its CORS
filter runs before the token filter, because a browser's preflight carries no
token and would otherwise be refused.

## Route guards and the return address

Sign-in is the only route an unauthenticated visitor may reach. Every other
route is a child of one guarded parent in `app.routes.ts`
(`canActivateChild: [authGuard]`), so a route added later is guarded without
anyone remembering to; a spec checks that every top-level route but sign-in
carries the guard.

A signed-out visitor is redirected to `/sign-in?returnUrl=<where they were
going>` and told "Sign in to continue to that page" -- never shown an empty
screen. After signing in they land there, but only if `safeReturnUrl()`
(`core/guards/return-url.ts`) accepts it as a **path on this origin**. Absolute
URLs, `//host`, backslash and control-character tricks, and `javascript:` all
fall back to the dashboard: a return address followed unchecked is an open
redirect, and on a trading sign-in page that is a phishing kit somebody else
assembles for free.

Said out loud at the review: **the guard is a usability control, not a
security control.** The bundle is public and every route in it is readable.
Authorisation is the Trade REST API's decision, taken on every `/api/v1/**`
call.

## The order ticket

`/trade` places an order with `POST /api/v1/orders`. Before anything is sent:

| Field | Rule |
|---|---|
| Instrument | **Picked from a list**, not typed: every tradable instrument from `GET /api/v1/instruments`, grouped as Stocks, ETFs and Mutual funds. Delisted ones are not offered, so the API cannot be sent a symbol it does not know |
| Quantity | A whole number, 1 or more |
| Limit price | Above zero, at most two decimal places |
| Account | **Read-only**, from the token's `accountId`, and shown as the reference the customer knows (`ACC-000003`) rather than the numeric key. An account field the user could edit is an authorisation decision moved into the browser |

The checks live in `features/order-ticket/order-validators.ts`. They are not
enforcement: business rules 1 to 8 live in the Trade REST API, and whatever
gets past the form comes back as a catalogue code and is rendered as one.

**Opened from a holding's Sell link** (`/trade?symbol=TCS.NS&side=SELL`), the
ticket starts with that instrument and Sell chosen -- once the list confirms
the symbol is tradable; anything else in the link is ignored.

**The result is whatever the API returned**, most often `NEW`: accepted, and
not yet executed. The ticket says so ("still working") rather than presenting
it as finished, and links to the dashboard, where it updates.

**Idempotency.** Each new order gets a new key. If a submit gets no response
at all (status 0), the order may or may not have landed, so a retry of the
same order reuses the same key: if the first attempt did land, the API answers
`ORD-409` instead of placing it twice.

## The dashboard and the blotter

There are two dashboards, `/stocks` (stocks and ETFs) and `/mutual-funds`.
Home (`/`) is the stocks dashboard for a signed-in customer and the public
landing page for a visitor; sign-in lands there. Each dashboard shows the
token's account from `GET /api/v1/accounts/{id}`, who is signed in from
`GET /auth/me`, its holdings, and its orders. Which dashboard an instrument
belongs on comes from its type in `GET /api/v1/instruments`
(`core/api/instrument-catalog.ts`, read once a session), never from what its
symbol looks like.

**What a customer sees, and what they do not.** A stock is named by its
ticker (`MRF.NS`) and a fund by its name, in the ticket's list, the holdings
and the order history alike; an AMFI scheme code means nothing to them. The
header shows the customer's name and account reference (`ACC-000003`), never
the numeric account key. The order id appears nowhere -- not in the history,
not in the ticket's confirmation, not in a button's label -- and an error is a
sentence without its code. Money is written in lakhs and crores
(`₹4,29,877.57`): `LOCALE_ID` is `en-IN` in `app.config.ts`.

**Holdings** come from `GET /api/v1/accounts/{id}/positions`: each instrument
held, its quantity, its average cost and what it cost in total, with a **Sell**
button that opens the ticket filled in -- for an instrument still tradable; a
delisted holding has nowhere to be sold, so it has none. There is no market value: the platform
prices an order when it executes and keeps no live prices to show. A position
sold to zero is gone from the list.

**When an order settles** -- leaves `NEW` for `FILLED`, `REJECTED` or
`CANCELLED` -- the blotter tells the dashboard, which reads the cash and the
holdings again. A buy that fills appears in the holdings without a reload.

The blotter lists every order from `GET /api/v1/accounts/{id}/orders`,
**newest first, rejections included**: the rejection is the record that the desk
tried and was refused. Each status has a badge carrying the **word and a shape
as well as a colour** -- roughly one man in twelve cannot tell red from green:
`◷ NEW`, `✓ FILLED`, `✕ REJECTED`, `– CANCELLED`.

**An order at `NEW` is normal.** The Trade REST API answers before the executor
resolves the order, and neither contract pushes to the browser. While anything
is at `NEW` the blotter says it is still working and **re-reads order history
every 3 seconds, at most 10 times** -- 30 seconds and 10 requests per burst. An
order normally resolves in a second or two, so the first re-read usually ends
it; 30 seconds covers a slow executor without polling for as long as the tab is
open. It stops as soon as nothing is at `NEW`, says so when it gives up, and
**Refresh** starts a new burst. It only ever re-reads: re-posting an order
would get `ORD-409` with the same idempotency key, or a second order with a new
one. The numbers are `REREAD_POLICY` in `shared/reread/reread-policy.ts`, which
the Cash page uses too.

**Cancel.** An order still `NEW` has a **Cancel** button, which sends
`DELETE /api/v1/orders/{id}` -- with the bare UUID: the route refuses the
`ORD-` prefix the API reports the order with (`VAL-422`). The executor may fill the
order first; then the API answers `ORD-409` and the blotter says the order had
already finished. Either way it re-reads, and shows what actually happened. A
cancelled buy gives its reserved cash back.

## Adding and withdrawing cash

`/cash` (linked from the header and the dashboard) shows the available cash --
the balance less what open orders and withdrawals hold, from
`GET /api/v1/accounts/{id}/balance` -- and the bank account given on the
application, masked to its last four digits. Money only moves to and from that
account; the page has nowhere to type another.

One amount, two buttons: **Add cash** posts to `/deposits`, **Withdraw** to
`/withdrawals`. The form refuses an amount that is not above zero with at most
two decimals, and a withdrawal over the available cash, before sending; the
server refuses both too (`VAL-422`, `PAY-400`).

**A transfer is `PENDING` until the payment gateway decides it**, a few seconds
later -- the server answers `202` at once. The list shows it as `◷ PROCESSING`,
then `✓ SUCCESS` or `✕ FAILED` with the gateway's reason. While anything is
pending the page re-reads the transfers and the available cash on the same
bounded policy as the blotter, and never re-sends. A withdrawal is held the
moment it is accepted, so the available cash drops before the bank answers.

Every request carries an idempotency key. If a request gets no answer at all
(the network dropped it), pressing the button again with the same amount sends
the **same key**, so the server returns the first transfer instead of moving
the money twice.

## End-to-end journeys

The Playwright journeys in `e2e/`, against the **real running stack** -- the UI,
the Trade REST API and the Auth service. An end-to-end test that talks to a
stub proves nothing about integration.

| File | Covers |
|---|---|
| `e2e/login.spec.ts` | the guard redirect, a refused sign-in, a successful sign-in arriving where it was going -- and that the token went only to `/api/v1/**` and `/auth/me`, never to `/auth/login` |
| `e2e/place-order.spec.ts` | the read-only account, an invalid order stopped before it is sent, a placed order and whatever status came back |
| `e2e/cash.spec.ts` | on Funds: the bank account masked, a deposit shown processing and then decided without a reload, a withdrawal over the available cash stopped before it is sent |
| `e2e/portfolio.spec.ts` | a market buy at the live price moving from Open to Executed on the Orders page, and Holdings pricing every holding live with totals |
| `e2e/preferences.spec.ts` | Sprint 10: a landing screen saved in Settings is where the next sign-in opens |
| `e2e/notifications.spec.ts` | Sprint 10: with the channel set to the inbox, a market buy's outcome arrives in the inbox on its own, and the bell counts it until it is read |
| `e2e/watchlists.spec.ts` | Sprint 10: the market watch kept on the server, a stock added in one browser there in the next; an alert at the market price firing on the poller's next quote and arriving in the inbox |
| `e2e/advice.spec.ts` | Sprint 10: a stock page reads its signal on request, from real daily candles: a view, the reason, and that it is not advice |
| `e2e/strategies.spec.ts` | Sprint 10: a strategy switched on firing on the poller's next quote, its order placed by the platform through the order route, and the executor's answer back in its runs |
| `e2e/market-watch.spec.ts` | the market watch beside the dashboard with platform prices, a stock added through the search, its chart and range buttons, and Buy opening the order window filled in |

A placed order passes on `NEW`, `FILLED` or `REJECTED`. Asserting `FILLED`
would fail the week the executor is switched off, which is the wrong signal.

Every address and credential comes from the environment, under these names
and no others:

| Variable | Example |
|---|---|
| `E2E_BASE_URL` | `http://localhost:4200` |
| `E2E_TRADE_API` | `http://localhost:8085` |
| `E2E_AUTH_API` | `http://localhost:3000` |
| `E2E_USERNAME`, `E2E_PASSWORD` | a login you created through activation |
| `E2E_ACCOUNT_ID` | that login's account, e.g. `3` |
| `E2E_SYMBOL` | a tradable NSE stock, e.g. `ITC.NS` -- the journeys search for it and pick it from the results |

The account needs cash for the order journey; a brand-new account has none,
so run the cash journey first (each run deposits ₹100), or add cash on the
Cash page.

```bash
docker compose --profile platform up -d      # the stack, from the repository root
npm start                                    # the UI, in another terminal
npx playwright install chromium              # once per machine
npm run e2e:login                            # each journey in its own process --
npm run e2e:cash                             # one that only passes after its neighbour fails here
npm run e2e:order
npm run e2e:market
npm run e2e:portfolio
npm run e2e:preferences
npm run e2e:notifications
npm run e2e:watchlists
npm run e2e:advice
npm run e2e:strategies
npm run e2e                                  # every journey
```

Each test starts in a fresh browser context and shares nothing: no token in a
module variable, no order one spec places for another to find. The refused
sign-in counts against the login throttle (five failures per address, then a
minute's cooldown), so do not loop it.

## Nothing secret in the bundle

`npm run build` writes files every browser downloading this application
receives. There is no private part of a front-end build: minification is not
obfuscation, and a source map is the source back again. **This application
never calls the market-data API**; prices reach the browser, when they do,
through a service of ours that holds the key server-side.

```bash
npm run check:bundle     # production build, then search every file in dist/
npm run test:scripts     # the scanner's own tests
```

`scripts/check-bundle-secrets.mjs` fails the run on any of:

| Search | Why |
|---|---|
| `x-api-key`, `api_key`, `api-key`, `fauxnance` | a market-data key in the bundle is a key published |
| an `execute-api.<region>.amazonaws.com` host | the browser never talks to the market-data API |
| `jwt_secret`, a secret assigned a long literal, a three-part JWT | a signing secret in the browser lets any reader mint a token the whole platform accepts |
| a `.map` file | the source, back again |
| **the literal value of every secret in the repository's `.env`** (`*SECRET*`, `*PASSWORD*`, `*KEY*`, `*TOKEN*`, `REDIS_URL`, `*DATABASE_URL`), and of the same names in the environment | catches a value pasted in under a name none of the patterns would match. A hit names the variable, never prints the value |

Point it at another env file with `BUNDLE_SECRETS_ENV_FILE=/path/to/.env`.

**A key or secret that reached a bundle has been published. Revoke it** --
deleting the line does not unpublish it. Run this before every review, and in
front of the panel.
