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

argon2id at **64 MiB, timeCost 4, parallelism 1**, giving roughly **135 ms per
verification** on the machine we deploy to. Measured, five runs per setting:

| Parameters | ms / verify | |
|---|---:|---|
| 19 MiB, t=2, p=1 | 21.7 | OWASP floor. Faster than we want |
| 64 MiB, t=3, p=4 | 38.3 | the library default |
| **64 MiB, t=4, p=1** | **135.4** | **chosen** |
| 128 MiB, t=3, p=1 | 202.2 | login becomes the cheapest thing in the platform to flood |

Too low and an offline attacker gets the same speedup we did; too high and the
login route is a denial-of-service target. 135 ms sits on the brief's order of a
tenth of a second.

`parallelism = 1` is deliberate. At p=4 one login competes for four cores, so
concurrent logins fight each other. At p=1 they scale across cores instead.

Passwords never reach a log: `RedactingLogger` is the only logger the service
installs, and it redacts by key name at any depth — through nested objects,
arrays, and errors with a DTO attached.

## Login throttle

<!-- 624: cooldown window and attempt count -->

## OpenAPI

<!-- 626: the human page and the JSON document paths -->

## Adopting the service in the Trade REST API

<!-- 625: what changed, and the evidence no Java did -->

## Security review

<!-- 627: the filename of the completed review -->
