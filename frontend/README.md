# Trading UI

The Angular front end for the platform: sign-in, a dashboard, an order ticket
and a blotter, against the Trade REST API (`localhost:8080`) and the Auth
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
│   │   └── auth/               from contracts/auth-api.yaml
│   └── app/
│       ├── app.ts / app.html   the shell: header, navigation, <router-outlet>
│       ├── app.config.ts       providers: router, HttpClient, the one interceptor, the API base paths
│       ├── app.routes.ts       every route, lazily loaded; the guard on all but sign-in  (633)
│       ├── core/               rules that are not a screen
│       │   ├── config/         where the two APIs are                                (630)
│       │   ├── api/            our services around the generated clients            (630, 631, 634, 636)
│       │   ├── session/        the signed-in session: token, account, sign-out      (631)
│       │   ├── http/           the one interceptor that sets Authorization          (632)
│       │   ├── guards/         the route guard and the return-address check         (633)
│       │   └── errors/         every error code mapped to a sentence                (635)
│       ├── features/           one folder per screen
│       │   ├── sign-in/                                                              (631)
│       │   ├── dashboard/      account summary and the blotter                      (629, 636)
│       │   ├── order-ticket/                                                         (634)
│       │   └── blotter/        order history, status badges, the NEW re-read        (636)
│       └── shared/             small presentational pieces used by several screens  (635, 636)
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
- **Two codes outside the catalogues** are mapped too, because our services send
  them: `AUTH-429` (the login throttle) and `SRV-500`.
- **Status 0** — no response at all, usually a service that is down or a CORS
  rule that does not allow this origin — has its own sentence, and an unknown
  code falls back to one. Neither renders a blank panel.

## Signing in

`/sign-in` posts the username and password to `POST /auth/login` on the Auth
service and keeps the access token it returns.

- **Where the token lives:** `sessionStorage`, through `core/session/session.ts`
  and nowhere else. A reload keeps the customer signed in; closing the tab, or
  **Sign out** in the header, clears it. An expired token counts as signed out
  and is cleared.
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
| Instrument | A shape the contract allows: `AAPL`, `INFY.NS` / `.BO`, `FX:EURUSD`, `X:BTCUSD`; at most 20 characters, sent upper-cased |
| Quantity | A whole number, 1 or more |
| Limit price | Above zero, at most two decimal places |
| Account | **Read-only**, from the token's `accountId`. An account field the user could edit is an authorisation decision moved into the browser |

The checks live in `features/order-ticket/order-validators.ts`. They are not
enforcement: business rules 1 to 8 live in the Trade REST API, and whatever
gets past the form comes back as a catalogue code and is rendered as one.

**The result is whatever the API returned**, most often `NEW`: accepted, and
not yet executed. The ticket says so ("still working") rather than presenting
it as finished, and links to the dashboard, where it updates.

**Idempotency.** Each new order gets a new key. If a submit gets no response
at all (status 0), the order may or may not have landed, so a retry of the
same order reuses the same key: if the first attempt did land, the API answers
`ORD-409` instead of placing it twice.
