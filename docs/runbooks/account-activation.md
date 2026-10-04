# Sprint 9 — account activation

Closes security review item A01. A customer can no longer claim an account by
knowing its number: once KYC passes, the auth service provisions the account,
the Trade REST API emails the customer a one-time link, and the customer
registers from it without ever typing an account number.

The front of the chain is built too: a customer applies on a public route, a
scheduled job checks them, and only a pass starts what follows. Until KYC
passes, the account exists but cannot trade (`ACC-403 KYC not verified`) and
has no login. See [Applying and KYC](#applying-and-kyc).

## How it flows

```
customer ──POST /onboarding/applications──► trade-api
      client_account (kyc_status PENDING) + client_profile
      + kyc_verification (PENDING), one transaction
                                    │  KYC job, every KYC_JOB_INTERVAL_MS,
                                    │  once the application is KYC_DELAY old
                                    ▼
      age, then the provider → kyc_verification + client_account.kyc_status
      + on a pass, an outbox_event row, one transaction
                                    │  trade-api outbox relay, after commit
                                    ▼
      KYC_VERIFIED {clientId} ──► kyc-events
                                    │  auth, group auth-provisioning
                                    ▼
      auth: INSERT provisioned_account + outbox row, one transaction
                                    │  outbox relay, after commit
                                    ▼
      ACCOUNT_PROVISIONED {clientId} ──► account-provisioning
                                    │  trade-api, group activation-mailer
                                    ▼
      trade-api: already emailed for this eventId? → stop
                 SELECT email FROM client_profile       (trading DB)
                 POST auth /internal/activation-tokens  (shared secret)
                 send the email over SMTP
                                    ▼
      customer opens  auth /activate?token=…  → sets username + password
                 → POST /auth/register {username, password, activationToken}
                 → "Your login is ready" (/activate/done), which links on
                   to ACTIVATION_HOME_URL; the customer logs in
```

The token never rides Kafka: it is a credential. Its plaintext exists only in
the HTTP response from auth to the mailer, and in the email. Auth stores its
SHA-256. Auth never connects to the trading database; the mailer never connects
to the auth database.

## Applying and KYC

`POST /onboarding/applications` on the Trade REST API. Public: no token, since
the customer has no login yet.

```json
{ "name": "Priya Menon", "dob": "1990-05-17", "email": "priya@example.com",
  "phoneNumber": "+919812345611", "pan": "ABCPM1234Q", "address": "12 Anna Nagar, Chennai",
  "bankAccountNumber": "509876543210", "ifsc": "DEMO0000001" }
```

| Answer | When |
|---|---|
| `202` `{"status":"RECEIVED", …}` | Created — **or** the PAN or email is already registered. The two are byte-identical and nothing is created for the duplicate, so the route never reveals who is a customer |
| `422` `VAL-422` | A field fails its rule (`pan` `[A-Z]{5}[0-9]{4}[A-Z]`, `phoneNumber` `+91` then 10 digits starting 6–9, `dob` a real past date, `bankAccountNumber` 9–18 digits, `ifsc` four letters, `0`, six letters or digits). The message names the field, never the value |
| `429` `RATE-429` | More than 5 applications in an hour from one address. Counted in memory, per instance |

The account is created `ACTIVE` with `kyc_status = PENDING`. Orders are refused
with `ACC-403 KYC not verified` until it is `VERIFIED`; the gate reads
`client_account.kyc_status`, never the event.

**The checks.** The job runs every `KYC_JOB_INTERVAL_MS` (10 s) and takes
applications at least `KYC_DELAY` (30 s) old, so a decision lands 30–40 s after
applying. Age first, then the provider; the first failure decides. The provider
is a deterministic stub standing in for a KRA, so a demonstration can produce
any outcome:

| Rule | Fails with | To demonstrate |
|---|---|---|
| 18 or over today, in `Asia/Kolkata` | `under 18` — and nothing is sent to the provider | `dob` less than 18 years ago |
| PAN holder type: 4th character `P` (individual) | `PAN is not an individual's` | PAN `ABCFS1234A` (a firm) |
| Registry: PAN digits not `0000` | `PAN not found at the registry` | PAN `ABCPS0000A` |
| Bank account: on file, number not ending `0000` (a real provider sends it a rupee) | `bank account could not be verified` | `bankAccountNumber` `509876540000` |

The decision is written to `kyc_verification` and `client_account.kyc_status`
together. `REJECTED` is final: one verification per customer, and the PAN stays
registered. Only `VERIFIED` queues `KYC_VERIFIED`, in the same transaction, and
trade-api's outbox relay publishes it every `OUTBOX_POLL_MS`.

```bash
docker exec -i fauxnance-postgres psql -U postgres -d trading -c \
  "SELECT kv.status, kv.reason, kv.checks, kv.attempts, kv.last_error, ca.kyc_status
     FROM kyc_verification kv JOIN client_account ca USING (client_id)
    WHERE client_id = <id>"
```

**A check that keeps failing** — an error, not a rejection — leaves the customer
`PENDING` and is retried each run. After `KYC_MAX_ATTEMPTS` (5) it is set aside:
no longer picked up, so it cannot hold a place in every batch, and the
trade-api log says `KYC_SET_ASIDE KYC client <id> …` once. `last_error` holds
the exception's class, never its message. Fix the cause, then:

```sql
UPDATE kyc_verification SET attempts = 0 WHERE client_id = <id>;
```

## Topics

| Topic | Producer | Consumer group | Key | Partitions | Retention | DLT |
|---|---|---|---|---|---|---|
| `kyc-events` | KYC in trade-api, through its outbox (`scripts/publish-kyc-verified.sh` publishes one by hand, for testing auth alone) | `auth-provisioning` (auth) | clientId string | 3 | 7 days | `kyc-events.DLT`, 1 partition |
| `account-provisioning` | auth, via its outbox | `activation-mailer` (trade-api) | clientId string | 3 | 7 days | `account-provisioning.DLT`, 1 partition |

Why these numbers: keyed by client, so ordering per customer is what matters;
3 partitions matches `orders` for low-volume traffic. Seven days covers a
weekend outage of either consumer with room to spare, and onboarding volume is
tiny. `scripts/create-topics.sh` creates all four with `--if-not-exists`, so it
is safe to re-run.

Both group ids are used nowhere else:

```bash
git grep -n "auth-provisioning\|activation-mailer" -- ':!docs' ':!contracts'
```

## Environment variables

Every variable this work reads. Secrets have no defaults: a service missing one
refuses to start and names it.

| Variable | Read by | Default | What |
|---|---|---|---|
| `KAFKA_BROKERS` | auth | none — required | Comma-separated brokers. `kafka:29092` in compose |
| `ACTIVATION_INTERNAL_SECRET` | auth **and** trade-api | none — required, secret | Guards `POST /internal/activation-tokens`. Same value both sides. `openssl rand -base64 48` |
| `ACTIVATION_HOME_URL` | auth | `http://localhost:4200/` | Linked from the "Your login is ready" page after registering |
| `OUTBOX_POLL_MS` | auth **and** trade-api | `2000` | How often each outbox relay looks for unsent events |
| `AUTH_INTERNAL_URL` | trade-api | `http://localhost:3000` (compose: `http://auth:3000`) | Base URL for minting tokens |
| `ACTIVATION_LINK_BASE_URL` | trade-api | `http://localhost:3000/activate` | The link in the email; `?token=` is appended |
| `ACTIVATION_MAIL_FROM` | trade-api | none — required | Sender address. For Gmail, the account itself |
| `SMTP_HOST` | trade-api | `smtp.gmail.com` | |
| `SMTP_PORT` | trade-api | `587` | STARTTLS is required |
| `SMTP_USERNAME` | trade-api | none — required | Gmail address |
| `SMTP_PASSWORD` | trade-api | none — required, secret | A Gmail **app password** (Google Account → Security → 2-Step Verification → App passwords), not the account password |
| `ACTIVATION_CONSUMER_ENABLED` | trade-api | `true` | `false` stops the mailer's listener starting; the integration tests use it |
| `KYC_DELAY` | trade-api | `30s` | How old an application must be before it is checked. Shorten it for a demo |
| `KYC_JOB_INTERVAL_MS` | trade-api | `10000` | How often the KYC job looks |
| `KYC_MAX_ATTEMPTS` | trade-api | `5` | Failed checks before a customer is set aside |
| `KYC_BATCH_SIZE` | trade-api | `50` | Checks per run. Not passed by compose |
| `KYC_JOB_ENABLED`, `OUTBOX_RELAY_ENABLED` | trade-api | `true` | `false` stops the job or the relay; the integration tests use both. Not passed by compose |

Scripts additionally read `SPRINT8_END` (check 6's pinned commit, default
`fcd04df`), `KAFKA_CONTAINER`, `KAFKA_BROKER_INTERNAL`, `AUTH_DB_CONTAINER`,
`AUTH_DB_USER` and `AUTH_DB_NAME`, all with defaults matching compose.

Running auth outside compose (`npm run start` in `services/auth`) now also needs
`KAFKA_BROKERS` and `ACTIVATION_INTERNAL_SECRET` in `services/auth/.env`.

## The token

- 32 random bytes from a CSPRNG, hex: 64 characters. Stored as SHA-256 in
  `activation_token.token_hash`, never in plaintext.
- **Lifetime: 24 hours.** Long enough for someone who reads email once a day;
  short enough that a link found in an old inbox is dead.
- **Single use.** `used_at` is set in the same transaction that claims the
  account and creates the credential.
- **Resend revokes.** Minting a new token for a client sets `revoked_at` on any
  earlier unused one, so only the newest email's link works.
- Unknown, expired, used, revoked, or for an already-claimed account: all
  answer `AUTH-401 Unauthorised`, and `/activate` shows one "link not valid" page.

## When something is down

| What | What happens | What you see |
|---|---|---|
| Kafka, while auth provisions | The account and its outbox row commit anyway. The relay retries every `OUTBOX_POLL_MS` and sends when the broker returns. Logins are unaffected. | `OUTBOX_PUBLISH_FAILED event=<uuid> …` warnings in `docker compose logs auth`; `SELECT * FROM outbox_event WHERE published_at IS NULL` in the auth DB |
| Kafka, at auth startup | Auth starts and serves HTTP; the `kyc-events` consumer retries every 10 s. | `kyc-events consumer not running (…); retrying in 10s` |
| Auth database, while consuming `kyc-events` | The message is not committed and kafkajs retries it. A verification is never dropped. | kafkajs retry errors in the auth log |
| Auth (HTTP), while the mailer runs | Retried 3 times with backoff (0.5 s, 2 s, 8 s), then dead-lettered to `account-provisioning.DLT` with `x-failure-class: TRANSIENT`. Not lost. | `activation retry n for account-provisioning-…` in the trade-api log |
| SMTP (Gmail) | Same as auth being down: retried, then dead-lettered. | same |
| No `client_profile` row | Dead-lettered on the first attempt, `x-failure-class: POISON`. A real inconsistency. | DLT record, reason `no client_profile row for client N` |
| Account already has a login | Nothing sent, message acknowledged. | `client N already has a login; nothing sent` |
| A malformed event on either topic | Dead-lettered on the first attempt with the original bytes. | DLT record with `x-failure-reason` |
| Kafka, while trade-api relays `KYC_VERIFIED` | KYC keeps deciding; the event waits in trade-api's `outbox_event` and is retried every `OUTBOX_POLL_MS`, without limit, and sent when the broker returns. | `OUTBOX_PUBLISH_FAILED event=<uuid> …` in `docker compose logs trade-api`; `SELECT * FROM outbox_event WHERE published_at IS NULL` in the trading DB |
| A KYC check that errors every time | Retried each run, then set aside, still `PENDING`. | `KYC_SET_ASIDE` in the trade-api log; see [Applying and KYC](#applying-and-kyc) |

Read a dead-letter topic:

```bash
docker exec fauxnance-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 --topic account-provisioning.DLT \
  --from-beginning --property print.headers=true --timeout-ms 5000
```

## Replaying a lost activation

Email lost, link expired, or the event dead-lettered and the cause is now fixed:

```bash
bash scripts/replay-activation.sh <clientId>
```

It queues a fresh `ACCOUNT_PROVISIONED` in the auth outbox (a new `eventId`, so
the mailer's duplicate check does not swallow it). The mailer mints a new token,
revoking the old link, and sends a new email. It refuses an account that is not
provisioned or already has a login.

## Running it end to end

On a freshly reset stack, from the application onwards. Use an inbox you can
read; Gmail delivers `you+anything@gmail.com` to `you@gmail.com`, which gives
as many distinct addresses as a run needs.

```bash
docker compose --profile platform down -v
docker compose --profile platform up -d --build
bash scripts/create-topics.sh
# no Kafka CLI on the host? run it inside the broker container instead:
#   docker exec -i -e BROKER=localhost:29092 -e KAFKA_TOPICS_BIN=/opt/kafka/bin/kafka-topics.sh \
#     fauxnance-kafka bash -s < scripts/create-topics.sh

curl -sS -X POST localhost:8085/onboarding/applications -H 'Content-Type: application/json' \
  -d '{"name":"Demo Customer","dob":"1995-06-15","email":"you+kyc1@gmail.com",
       "phoneNumber":"+919812345611","pan":"DEMPS1234K","address":"12 Anna Nagar, Chennai",
       "bankAccountNumber":"509876543210","ifsc":"DEMO0000001"}'
```

Then, about 40 seconds later: `KYC client 11 decided VERIFIED` in the trade-api
log → `account 11 provisioned` in the auth log → the email arrives → open the
link → choose a username and password → "Your login is ready" → log in:

```bash
curl -sS -X POST localhost:3000/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"<chosen>","password":"<chosen>"}' | jq -r .accessToken
curl -sS localhost:8085/api/v1/accounts/11 -H "Authorization: Bearer <token>"
```

Open the same link again: "This link is not valid".

A new account has no money, so its first order is `ORD-400 Insufficient funds`
— which already shows the KYC gate let it through. Add cash from the bank
account given on the application, and it is credited a few seconds later
(`payments.md`):

```bash
curl -sS -X POST localhost:8085/api/v1/accounts/11/deposits -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' -d '{"amount":100000,"idempotencyKey":"first-deposit-11"}'
```

### The same, in the browser

With the UI running (`cd frontend && npm start`), all of it is screens:

1. `http://localhost:4200/sign-in` → **New customer? Open an account** → the
   application, bank account included → "Application received".
2. About 40 seconds later the email arrives → open the link → choose a username
   and password → "Your login is ready" → **Sign in**.
3. **Cash** → an amount → **Add cash** → `◷ PROCESSING`, then `✓ SUCCESS` a few
   seconds later; the available cash goes up.
4. **Place an order** → pick the instrument from the list → Buy → the result,
   usually `NEW` → the dashboard's blotter updates it; a fill appears under
   **Your holdings**.
5. **Sell** on a holding → the ticket opens with that instrument and Sell chosen.
6. **Cash** → **Withdraw** → held at once, `✓ SUCCESS` a few seconds later.

An order is `FILLED` only when the executor can price it: a valid
`FAUXNANCE_API_KEY`, an instrument Fauxnance knows (`seed/005`: `TCS.NS`,
`ITC.NS`, `HDFCBANK.NS`, `MRF.NS`), and a quote Fauxnance does not flag
`stale` -- the executor refuses a stale one. When Fauxnance's Indian data is
behind (`GET /health` reports the `IN` market `stale`), the `.NS` quotes come
from its cache and every one is rejected `NO_PRICE`, except `MRF.NS`, which it
prices synthetically. The fictional tickers from `seed/003` are always
`REJECTED` for want of a price.

The rejection path: apply again with PAN `ABCPS0000A` and another address →
`REJECTED`, reason `PAN not found at the registry`, no event, no email, no login.

### Testing auth without KYC

`scripts/publish-kyc-verified.sh <clientId>` publishes a `KYC_VERIFIED` by hand,
with no checks. The ten seeded accounts are already provisioned, so give one
back first:

```bash
docker exec -i fauxnance-postgres psql -U postgres -d trading \
  -c "UPDATE client_profile SET email = 'you+activation@gmail.com' WHERE client_id = 5"
docker exec -i fauxnance-postgres psql -U postgres -d auth \
  -c "DELETE FROM provisioned_account WHERE account_id = 5 AND claimed_by IS NULL"
bash scripts/publish-kyc-verified.sh 5
```

It changes nothing in the trading database: the order gate still reads
`client_account.kyc_status`, so an account it provisions trades only if that
already says `VERIFIED`.

## Live checks to run

These need the running stack, so they were not run when the code was written.
Record each result in the security review's evidence table.

**Stage 1 — auth publishes**
1. `bash scripts/create-topics.sh` twice: second run changes nothing.
2. `publish-kyc-verified.sh 5` (after the un-provision step in "Testing auth without KYC") → exactly one
   message on `account-provisioning`; key `5`; envelope fields
   `eventId, eventType, eventTime, source, schemaVersion, payload`; payload
   exactly `{"clientId":5}`.
3. Publish the same `KYC_VERIFIED` again → no second message.
4. Rollback: in the auth DB,
   `BEGIN; INSERT INTO provisioned_account VALUES (99); INSERT INTO outbox_event (event_id, topic, message_key, envelope) VALUES (gen_random_uuid(), 'account-provisioning', '99', '{}'); ROLLBACK;`
   → nothing published.
5. Broker down after a provisioning committed. Provisioning itself arrives over
   Kafka, so simulate the committed outbox row directly: `docker compose stop kafka`,
   then `bash scripts/replay-activation.sh 6` (it writes exactly the row
   provisioning writes) → it succeeds, and `OUTBOX_PUBLISH_FAILED` appears in
   `docker compose logs auth`. `docker compose start kafka` → within a few
   seconds the message is on `account-provisioning` and
   `outbox_event.published_at` is set.

**Stage 2 — the token endpoint**
1. After `down -v` / `up`: `\d activation_token` in the auth DB.
2. `curl -X POST localhost:3000/internal/activation-tokens -H "X-Internal-Secret: $ACTIVATION_INTERNAL_SECRET" -H 'Content-Type: application/json' -d '{"clientId":6}'`
   → `201` with `activationToken` and `expiresAt`.
3. `SELECT token_hash, length(token_hash) FROM activation_token` → 64 characters;
   `SELECT count(*) FROM activation_token WHERE token_hash = '<plaintext>'` → 0.
4. No header, and a wrong header → both `401`, bodies identical.
5. Unset `ACTIVATION_INTERNAL_SECRET` and start auth → exits with
   `ACTIVATION_INTERNAL_SECRET is not set`.

**Stage 3 — the mailer**
1. A real email arrives (end-to-end run above).
2. Duplicate: consume one `ACCOUNT_PROVISIONED` off the topic and produce it
   back with the same key (as `scripts/duplicate-replay.sh` does for `orders`)
   → no second email; `already emailed; nothing sent` in the trade-api log.
3. Auth unreachable. `docker compose stop auth`, then produce an
   `ACCOUNT_PROVISIONED` by hand (auth's outbox cannot, since auth is stopped):
   ```bash
   printf '7\t{"eventId":"%s","eventType":"ACCOUNT_PROVISIONED","eventTime":"%s","source":"auth-service","schemaVersion":1,"payload":{"clientId":7}}\n' \
     "$(cat /proc/sys/kernel/random/uuid)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
   | docker exec -i fauxnance-kafka /opt/kafka/bin/kafka-console-producer.sh \
       --bootstrap-server kafka:29092 --topic account-provisioning \
       --property parse.key=true --property key.separator=$'\t'
   ```
   → three `activation retry` lines in the trade-api log, then a record on
   `account-provisioning.DLT` with `x-failure-class: TRANSIENT`. Not lost.
4. The same command with `7` replaced by `999` (no `client_profile`), auth
   running → `account-provisioning.DLT`, `x-failure-class: POISON`, first attempt.
5. `docker compose logs trade-api auth | grep -c '<token or email>'` → 0.

**Stage 4 — register**
1. The link flow above registers with no account number.
2. The same link twice → second refused; `SELECT count(*) FROM credential` unchanged.
3. `bash scripts/auth-integration-check.sh` → six checks pass.

**Stage 5 — onboarding and KYC**
1. Apply with a clean PAN → `202`; nothing decided for `KYC_DELAY`; then
   `VERIFIED` on both tables and exactly one `outbox_event` row, published.
2. Apply with `ABCPS0000A`, `ABCFS1234A` and an under-18 `dob` → `REJECTED` with
   each reason on both tables; no outbox row.
3. Apply twice with the same PAN, then the same email → identical `202`s; one
   account. A sixth application within the hour → `429 RATE-429`.
4. `docker compose stop kafka`, apply, wait → `VERIFIED`, and
   `OUTBOX_PUBLISH_FAILED` in the trade-api log; `docker compose start kafka` →
   the event is sent and auth provisions the account.
5. Order as seeded account 9 (`kyc_status PENDING`) → `403 ACC-403 KYC not verified`.
6. `docker compose logs trade-api auth postgres | grep -c '<name|email|PAN>'` → 0.
