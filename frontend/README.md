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
