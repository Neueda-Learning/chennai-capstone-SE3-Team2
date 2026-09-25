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

**Five failed attempts from one address, then a sixty second cooldown.**
In-memory, so it is per-instance; behind more than one process it would need a
shared store. A successful login clears the count.

The throttle limits how fast an attacker can use a disclosure. It does not
close one — the uniform failure below is what closes it.

## One answer for every failed login

`AUTH-401` with the message `Unauthorised`, and HTTP 401, for all five causes:
unknown user, wrong password, expired token, wrongly signed token, malformed
header.

The timing is the part that fails by accident. Where the username is not found
we verify the supplied password against a dummy hash of the same algorithm and
the same parameters, discard the result, and fail identically. Measured, ten
runs each:

| Path | Median |
|---|---:|
| wrong password, user exists | 128.6 ms |
| unknown user, dummy hash verified | 135.6 ms |
| **ratio** | **1.05x** |

Judge the shape rather than the size: one path taking twice as long as the other
is an early return even when both numbers are small. The dummy hash is computed
once at startup, not per request — hashing it each time would double the cost of
every miss.

Run the comparison **outside** the throttle window, or a burst of failures
collides with it.

## OpenAPI

Generated from the decorators on the controller and the DTOs, and served by the
running process:

| | |
|---|---|
| Human page | `http://localhost:3000/docs` |
| JSON document | `http://localhost:3000/docs/json` |

```bash
curl -sS http://localhost:3000/docs/json | jq '.paths | keys'
```

Four paths, OpenAPI 3.0. This does not replace `contracts/auth-api.yaml` — it
is the evidence our code still matches it.

## Adopting the service in the Trade REST API

<!-- 625: what changed, and the evidence no Java did -->

## Security review

<!-- 627: the filename of the completed review -->
