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
| **A02 Cryptographic failures** | Yes | Passwords are argon2id at 64 MiB / t=4 / p=1, measured at 135 ms per verification — chosen against a benchmark, recorded in `docs/sprints/sprint-08.md`. Access tokens are HS256 and the algorithm is pinned on verification, so an `alg: none` token is refused before any claim is read. Refresh tokens are stored as SHA-256 fingerprints, never as the value handed out. **The development `JWT_SECRET` in `.env.example` is published in a public repository.** | **Fixed.** The secret is rotated for any environment a real issuer signs for; `.env.example` keeps a placeholder that is not a usable key. SHA-256 for refresh tokens is deliberate and not the password mistake: the value is already 256 bits of random, so it has no low-entropy preimage to brute-force. |
| **A03 Injection** | Yes | **None.** Every statement in `credential.repository.ts` and `refresh-token.repository.ts` is parameterised with `$1`-style placeholders through `pg`; none is assembled by string concatenation or template literal. Checked by reading all seven queries. Inputs are additionally constrained by `class-validator` before reaching a repository, and `forbidNonWhitelisted` rejects unknown fields rather than passing them through. | **Out of scope for further work.** No change needed. |
| **A04 Insecure design** | Yes | Refresh rotation issues a new token on every exchange **and revokes the presented one**. A replayed token is treated as theft: every live token for that credential is revoked and the attempt is logged. The access token remains non-revocable for its fifteen-minute life. | **Fixed**, beyond the cohort requirement, which made revocation optional. Residual risk accepted: a stolen access token is valid until it expires, and shortening that window further would push a login onto the user too often. Fifteen minutes is the size of that compromise and it is a deliberate figure. |
| **A05 Security misconfiguration** | Yes | `JWT_SECRET` and `AUTH_DATABASE_URL` have no defaults in `env.ts`; the service refuses to start without them rather than running on a guess. The container runs as a non-root user and carries no compiler or package manager. **The auth service has its own database**, so a compromise of it does not come with a connection to the trading data. Errors leave through one filter as `{ errorCode, message }`, and an unhandled exception answers `SRV-500` with no internal message. | **Fixed.** No secret is present in any committed file. |
| **A06 Vulnerable and outdated components** | Yes | `npm audit` at the time of review. Dependencies are pinned by a committed `package-lock.json`, so `npm ci` installs exactly the reviewed tree. | **Accepted with a check.** Run `npm audit` before the review and record the result here; anything high or critical is fixed rather than carried. |
| **A07 Identification and authentication failures** | Yes | An unknown user, a wrong password, an expired token, a wrongly signed token and a malformed header all answer HTTP 401 with `{"errorCode":"AUTH-401","message":"Unauthorised"}` — the same status, body and message. Timing was measured over ten runs: 128.6 ms for a wrong password against 135.6 ms for an unknown user, a ratio of 1.05, because the miss verifies against a dummy hash of the same algorithm and parameters. A login throttle allows five failures per address then a sixty-second cooldown. | **Fixed.** Residual risk accepted: the throttle is in-memory and therefore per-instance. Behind more than one process it would need a shared store, and a distributed attacker is limited per address rather than in total. |
| **A09 Security logging and monitoring failures** | Yes | **No password or token reaches a log by any route we could find.** `RedactingLogger` is the only logger installed, and it redacts by key name at any depth — through nested objects, arrays, and errors with a DTO attached, which are the indirect routes that matter. A replayed refresh token is logged as a warning naming the credential and the number of tokens revoked. | **Fixed.** Residual risk accepted: there is no alerting on that warning. It lands in the container log and nobody is paged. A platform with a log sink would alert on it. |

## What we would do at 09:00 on the morning somebody reports a stolen refresh token

1. Find the credential from the report, and revoke every live refresh token for it — `revokeAllFor` already exists and the replay path calls it.
2. Read back the `refresh_token` rows for that credential. `exchanged_at` timestamps show when two sessions diverged, which is when the theft was first used.
3. The thief keeps a working **access** token for up to fifteen minutes. There is no revocation for it, so that window is simply waited out.
4. Force a password change, which invalidates nothing by itself today — a gap worth naming.
5. If more than one account is involved, rotate `JWT_SECRET`. That invalidates every access token on the platform at once, including honest ones, and is the blunt instrument of last resort.
