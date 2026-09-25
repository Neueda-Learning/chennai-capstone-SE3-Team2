# Security review: auth service

Filled in as the service was built, not the night before the demonstration.

## Header

| Field | Value |
|---|---|
| Service | auth service, Sprint 8 |
| Reviewed by | KS, with the story owners for each area |
| Date of review | 2026-09-24 |
| Commit reviewed | `release/sprint8` at the Sprint 8 merge |
| Version of the OWASP Top Ten used | 2021 |

## Categories

| Category | In scope | Finding | Disposition |
|---|---|---|---|
| **A01 Broken access control** | Yes | **Registration binds a credential to an account with no proof of entitlement beyond knowing its number.** `auth.service.ts` checks that `accountId` is in `provisioned_account` and unclaimed, then claims it. Anyone who guesses a provisioned number can claim it before its owner does. Separately, `/auth/me` reads identity only from `CurrentUser`, which the guard populated from a verified token — never from a route parameter or body. Self-declared `roles` on the public registration route are accepted as given. | **Accepted, and mitigated in part.** Mitigated: the account must already be provisioned, and each can be claimed exactly once, by a conditional `UPDATE ... WHERE claimed_by IS NULL` that two simultaneous registrations cannot both win. Accepted residual risk: no activation factor. Production needs a one-time token from onboarding, sent to a channel this platform does not have. Self-declared roles are a second accepted risk: a registrant may ask for `ADMIN`, and nothing in the platform yet reads that claim, so it grants nothing today. |
| **A02 Cryptographic failures** | Yes | Passwords are argon2id at 64 MiB / t=4 / p=1, measured at 135 ms per verification — chosen against a benchmark, recorded in `docs/sprints/sprint-08.md`. Access tokens are HS256 and the algorithm is pinned on verification, so an `alg: none` token is refused before any claim is read. Refresh tokens are stored as SHA-256 fingerprints, never as the value handed out. **The development `JWT_SECRET` in `.env.example` is published in a public repository.** | **Fixed.** The secret is rotated for any environment a real issuer signs for; `.env.example` keeps a placeholder that is not a usable key. SHA-256 for refresh tokens is deliberate and not the password mistake: the value is already 256 bits of random, so it has no low-entropy preimage to brute-force. Also found and removed: **`client_auth`, a second credential store** left in the *trading* database by Sprint 3, holding a `password_hash` column for all ten seeded clients. No Java, no mapper and no foreign key referenced it — it had no reader after Sprint 3. Dropped by `data/db/migrations/005_drop_client_auth.sql`, because a table of password hashes in the database the Trade REST API connects to is the exact arrangement this sprint exists to remove. The hashes in it were never real; the seed said so. |
| **A03 Injection** | Yes | **None.** Every statement in `credential.repository.ts` and `refresh-token.repository.ts` is parameterised with `$1`-style placeholders through `pg`; none is assembled by string concatenation or template literal. Checked by reading all seven queries. Inputs are additionally constrained by `class-validator` before reaching a repository, and `forbidNonWhitelisted` rejects unknown fields rather than passing them through. | **Out of scope for further work.** No change needed. |
| **A04 Insecure design** | Yes | Refresh rotation issues a new token on every exchange **and revokes the presented one**. A replayed token is treated as theft: every live token for that credential is revoked and the attempt is logged. The access token remains non-revocable for its fifteen-minute life. | **Fixed**, beyond the cohort requirement, which made revocation optional. Residual risk accepted: a stolen access token is valid until it expires, and shortening that window further would push a login onto the user too often. Fifteen minutes is the size of that compromise and it is a deliberate figure. |
| **A05 Security misconfiguration** | Yes | `JWT_SECRET` and `AUTH_DATABASE_URL` have no defaults in `env.ts`; the service refuses to start without them rather than running on a guess. The container runs as a non-root user and carries no compiler or package manager. **The auth service has its own database**, so a compromise of it does not come with a connection to the trading data. Errors leave through one filter as `{ errorCode, message }`, and an unhandled exception answers `SRV-500` with no internal message. | **Fixed.** No secret is present in any committed file. |
| **A06 Vulnerable and outdated components** | Yes | `npm audit` on 2026-09-25: **24 advisories — 4 low, 12 moderate, 8 high, and no critical.** Thirteen of them are build tooling (`@nestjs/cli`, `webpack`, `tmp`, `inquirer`) that the multi-stage Dockerfile never copies into the shipped image; they are a risk to a developer's machine, not to the running service. Eleven were in the production tree. Of those, `multer` (high) arrives transitively through `@nestjs/platform-express` and **this service has no upload route** — checked, no `FileInterceptor` and no `@UploadedFile` anywhere in `src/`. The one genuinely reachable advisory was `qs`, which parses a query string on every request. | **Partly fixed, the rest accepted with reasons.** `npm audit fix` (non-breaking) was run and `qs` among others resolved; the tree went 24 → 23, production 11 → 10, with 47 tests still passing. The remainder all require `@nestjs/*` major upgrades — NestJS 10 → 11 — which `npm audit fix --force` would apply. **We did not take that upgrade**: a framework major on the last day of the sprint risks the working integration for advisories that are unreachable in this service. Dependencies are pinned by a committed `package-lock.json`, so `npm ci` installs exactly the reviewed tree. Owner for the NestJS 11 upgrade is named in Outstanding items below. |
| **A07 Identification and authentication failures** | Yes | An unknown user, a wrong password, an expired token, a wrongly signed token and a malformed header all answer HTTP 401 with `{"errorCode":"AUTH-401","message":"Unauthorised"}` — the same status, body and message. Timing was measured over ten runs: 128.6 ms for a wrong password against 135.6 ms for an unknown user, a ratio of 1.05, because the miss verifies against a dummy hash of the same algorithm and parameters. A login throttle allows five failures per address then a sixty-second cooldown. | **Fixed.** Residual risk accepted: the throttle is in-memory and therefore per-instance. Behind more than one process it would need a shared store, and a distributed attacker is limited per address rather than in total. |
| **A09 Security logging and monitoring failures** | Yes | **No password or token reaches a log by any route we could find.** `RedactingLogger` is the only logger installed, and it redacts by key name at any depth — through nested objects, arrays, and errors with a DTO attached, which are the indirect routes that matter. A replayed refresh token is logged as a warning naming the credential and the number of tokens revoked. | **Fixed.** Residual risk accepted: there is no alerting on that warning. It lands in the container log and nobody is paged. A platform with a log sink would alert on it. |

## What we would do at 09:00 on the morning somebody reports a stolen refresh token

1. Find the credential from the report, and revoke every live refresh token for it — `revokeAllFor` already exists and the replay path calls it.
2. Read back the `refresh_token` rows for that credential. `exchanged_at` timestamps show when two sessions diverged, which is when the theft was first used.
3. The thief keeps a working **access** token for up to fifteen minutes. There is no revocation for it, so that window is simply waited out.
4. Force a password change, which invalidates nothing by itself today — a gap worth naming.
5. If more than one account is involved, rotate `JWT_SECRET`. That invalidates every access token on the platform at once, including honest ones, and is the blunt instrument of last resort.

## Evidence

What was actually run or read, so the review is repeatable.

| Check | How it was performed | Result |
|---|---|---|
| Uniform failure, body | `POST /auth/login` with a wrong password, then with an unknown user, against the running service | Both `401` with `{"errorCode":"AUTH-401","message":"Unauthorised"}` — identical |
| Uniform failure, timing | Five runs of each path, mean of `curl -w '%{time_total}'` | **133.7 ms** wrong password vs **135.7 ms** unknown user, ratio 1.01. Before the dummy-hash defence was merged the same measurement read **137.0 ms vs 1.9 ms**, a 72× gap |
| Hash cost | `argon2.hash` benchmarked at the chosen parameters | 64 MiB, t=4, p=1 → **135 ms** per verification |
| Password at rest | `SELECT password_hash FROM credential` on the running database | `$argon2id$v=19$m=65536,t=4,p=1$…` — no plaintext, no general-purpose digest |
| Claim set | Decoded the payload of a live token | Exactly `sub, accountId, roles, iat, exp, iss`; `exp − iat = 900` |
| Algorithm pinning | Read `jwt-auth.guard.ts`; `access-token.service.spec.ts` asserts on the key set | HS256 pinned on verification, not read from the token header |
| Refresh rotation and replay | Logged in, refreshed, used the new token, then replayed the old one | New token differs and works; replay answers `401`; store went to **0 still live** for that credential |
| Replay logging | `docker compose logs auth` after the replay | `replayed refresh token for credential <uuid>: revoked 1 live token(s)` — names the credential, never the token |
| Injection | Read all seven statements in `credential.repository.ts` and `refresh-token.repository.ts` | Every value bound with a `$n` placeholder; none concatenated |
| Log redaction | Read `redacting-logger.ts` and its spec | Redacts by key name at any depth: nested objects, arrays, and errors carrying a DTO |
| Secrets have no defaults | Read `config/env.ts` | Throws when `JWT_SECRET` or `AUTH_DATABASE_URL` is absent; the service refuses to start |
| Dependency audit | `npm audit` and `npm audit --omit=dev` | 24 advisories, none critical; 13 build-only. See A06 |
| Second credential store | `grep -rn client_auth` across SQL, Java and mappers | Found in the trading schema with no reader anywhere. Dropped — see A02 |
| Trade REST API integration | `scripts/auth-integration-check.sh` against the running stack | Six checks pass, including `401` for a token signed with an untrusted key |
| No Java changed | `git diff --name-only <base>...HEAD -- '*.java'` where base is the commit before `services/auth` first appeared | Empty. Outside `services/auth/`, Sprint 8 touched four files: `.env.example`, `.gitignore`, `docker-compose.yml`, `docs/sprints/sprint-08.md` |

## Outstanding items

| Item | Owner | Target date |
|---|---|---|
| **Build the activation factor (A01)** — a one-time token sent to a channel the customer controls, so registration needs more than a provisioned account number. Today, knowing the number is the whole entitlement. | NL | Sprint 9 |
| Demote the Trade REST API off the Postgres superuser role (A05) — it connects as `postgres` today and needs about six tables | AA | Sprint 9 |
| Move the login throttle to a shared store so the 5-attempt limit holds across more than one instance, not 5 per process (A07) | SS | Sprint 9 |
| Alert on the replayed-refresh-token warning (A09) — it is the strongest compromise signal the platform emits and today nobody is paged for it | KS | when a log sink exists |
| Upgrade `@nestjs/*` to 11, clearing the remaining 10 production advisories (A06) | SA | before Sprint 9 closes |
