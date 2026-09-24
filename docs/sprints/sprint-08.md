# Sprint 8 — the auth service

The one component that ever sees a password. Everything else verifies a
signature with no network call and no shared database.

Runs on port 3000, reserved for it in `infra/README.md`.

```bash
cd services/auth
npm ci
npm run build
npm test
```

## Password hashing

<!-- 620: the algorithm, the cost parameters, and the measurement behind them -->

## Login throttle

<!-- 624: cooldown window and attempt count -->

## OpenAPI

<!-- 626: the human page and the JSON document paths -->

## Adopting the service in the Trade REST API

<!-- 625: what changed, and the evidence no Java did -->

## Security review

<!-- 627: the filename of the completed review -->
