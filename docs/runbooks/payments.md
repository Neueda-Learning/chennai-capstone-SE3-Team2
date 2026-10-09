# Payments — adding and withdrawing cash

A customer adds cash from, and withdraws cash to, the one bank account they
gave on their application. Nothing else: there is no field anywhere to type
another account. KYC checks that account before the customer can log in.

The payment gateway is a **stub**, decided asynchronously the way KYC is: a
transfer is recorded `PENDING`, and a scheduled job has the gateway decide it a
few seconds later -- the moment a real gateway's confirmation would arrive.
Swapping in a real one is one class (see [A real gateway](#a-real-gateway)).

## How it flows

```
customer ──POST /api/v1/accounts/{id}/deposits──► trade-api        (or /withdrawals)
      checks: own account, ACTIVE, KYC VERIFIED, a bank_account on file
      withdrawal only: blocked_funds += amount, if available cash covers it  (else PAY-400)
      INSERT fund_transfer (PENDING) -- same idempotency key: the first transfer back
      202 {transferId, status: PENDING}
                                    │  payment job, every PAYMENT_JOB_INTERVAL_MS,
                                    │  once the transfer is PAYMENT_DELAY old
                                    ▼
      the gateway decides → one transaction:
        UPDATE fund_transfer … WHERE status = 'PENDING'   (0 rows: another run won)
        deposit    SUCCESS → balance += amount
        withdrawal SUCCESS → balance −= amount, blocked_funds −= amount
        withdrawal FAILED  → blocked_funds −= amount       (the hold is released)
        deposit    FAILED  → nothing moves
                                    ▼
      the Cash page, re-reading every 3 s while anything is PENDING,
      shows SUCCESS, or FAILED with the gateway's reason
```

Every money move is one `UPDATE … SET balance = balance ± amount, version =
version + 1`, in place, so two moves at once cannot lose one another.

## Routes

All on the Trade REST API, behind the bearer token, for the token's own account
only (`ACC-403` otherwise). Described in
`Services/order-service/openapi/trade-api-extensions.yaml`; the UI's client is
generated from it.

| Route | Answers |
|---|---|
| `GET /api/v1/accounts/{id}/bank-account` | `{accountNumberLast4, ifsc, holderName}`. The full number never leaves the server |
| `GET /api/v1/accounts/{id}/transfers` | every transfer, newest first |
| `POST /api/v1/accounts/{id}/deposits` | `202` and the transfer, `PENDING` |
| `POST /api/v1/accounts/{id}/withdrawals` | `202` and the transfer, `PENDING`, the amount already held |

Request body for both: `{"amount": 5000.50, "idempotencyKey": "<8 to 64 characters>"}`.

| Code | HTTP | When |
|---|---|---|
| `PAY-400` | 400 | A withdrawal larger than the available cash (balance less what open orders and withdrawals hold) |
| `PAY-404` | 404 | No bank account on file |
| `PAY-409` | 409 | The idempotency key was already used for a *different* transfer. The same transfer again returns the first one |
| `ACC-403` | 403 | Another customer's account, or an account not `ACTIVE` with KYC `VERIFIED` |
| `VAL-422` | 422 | Amount not above zero, more than two decimals, or a key of the wrong length |

## The stub gateway's rules

| Transfer | Decision |
|---|---|
| Over **₹2,00,000** | `FAILED`, reason `over the ₹2,00,000 per-transfer limit` |
| Anything else | `SUCCESS` |

Its reference is `STUB-<transferId>`, stored in `fund_transfer.reference_id`.
On the application, a bank account number ending `0000` fails KYC
(`bank account could not be verified`), so that customer never reaches here.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `PAYMENT_DELAY` | `3s` | How old a transfer must be before the gateway decides it |
| `PAYMENT_JOB_ENABLED` | `true` | `false` stops deciding: transfers stay `PENDING` (the integration tests do this) |
| `PAYMENT_JOB_INTERVAL_MS` | `2000` | How often the job looks for due transfers |

`docker-compose.yml` passes only `PAYMENT_DELAY` to the container; the other
two take effect when the service runs outside compose (`mvn spring-boot:run`,
the Windows setup). `payments.batch-size` (50 a run) and
`payments.max-attempts` (5) are in `application.yml`.

## When something goes wrong

| What | What happens | What you see |
|---|---|---|
| Deciding a transfer throws (the database, a bug) | That transfer's transaction rolls back and it stays `PENDING`; the others in the run go on. The next run tries again. | `transfer N not decided (attempt n of 5), left PENDING: <ExceptionClass>` in `docker compose logs trade-api` |
| Five failed attempts | The transfer is **set aside**: still `PENDING`, its withdrawal still held, and no run picks it up again. Money no run will move until a person acts -- alert on this. | `PAYMENT_SET_ASIDE transfer N set aside after 5 failed attempts` |
| Two instances decide the same transfer | The guarded update lets one win; the other moves nothing. | `transfer N decided …` once |
| The customer's request gets no answer | The Cash page sends the same idempotency key on the retry, so the server returns the first transfer instead of making a second. | one row per key in `fund_transfer` |

Only the exception's class name is logged and stored in `last_error`, never its
message: a database error can quote the row back, and the row names a bank
account.

Find what is stuck, and retry a set-aside transfer once the cause is fixed:

```bash
docker exec -i fauxnance-postgres psql -U postgres -d trading_system_db -c \
  "SELECT transfer_id, client_id, direction, amount, attempts, last_error, created_at
     FROM fund_transfer WHERE status = 'PENDING' ORDER BY created_at"
docker exec -i fauxnance-postgres psql -U postgres -d trading_system_db -c \
  "UPDATE fund_transfer SET attempts = 0 WHERE transfer_id = <N>"
```

## A real gateway

`PaymentGateway` is the seam: `process(Instruction) → Decision(succeeded,
reference, reason)`. A real one replaces `StubPaymentGateway` and nothing else.
Two things change with it:

- The stub answers at once, so `PaymentDecider` calls it inside the
  transaction. A real network call moves out of it: ask the gateway, then open
  the transaction and apply the decision. The guarded update already makes that
  safe against a second run.
- A real gateway confirms by webhook. The webhook handler calls the same
  decide-and-apply, keyed by `reference_id`; the job stays as the sweep for
  confirmations that never arrived.

## Live checks to run

On the running stack, as a customer with a login (see
`account-activation.md`, "Running it end to end"), with `T` the access token:

```bash
H="Authorization: Bearer $T"; J='Content-Type: application/json'; ID=11
curl -sS localhost:8081/api/v1/accounts/$ID/bank-account -H "$H"           # last four digits only
K=$(cat /proc/sys/kernel/random/uuid)
curl -sS -X POST localhost:8081/api/v1/accounts/$ID/deposits -H "$H" -H "$J" \
  -d "{\"amount\":100000,\"idempotencyKey\":\"$K\"}"                         # 202, PENDING
curl -sS -X POST localhost:8081/api/v1/accounts/$ID/deposits -H "$H" -H "$J" \
  -d "{\"amount\":100000,\"idempotencyKey\":\"$K\"}"                         # the same transferId
curl -sS -X POST localhost:8081/api/v1/accounts/$ID/deposits -H "$H" -H "$J" \
  -d "{\"amount\":5,\"idempotencyKey\":\"$K\"}"                              # PAY-409
sleep 6; curl -sS localhost:8081/api/v1/accounts/$ID/transfers -H "$H"      # SUCCESS
curl -sS localhost:8081/api/v1/accounts/$ID/balance -H "$H"                 # 100000 more
curl -sS -X POST localhost:8081/api/v1/accounts/$ID/withdrawals -H "$H" -H "$J" \
  -d "{\"amount\":250000,\"idempotencyKey\":\"$(cat /proc/sys/kernel/random/uuid)\"}"  # PAY-400: more than available
curl -sS -X POST localhost:8081/api/v1/accounts/$ID/deposits -H "$H" -H "$J" \
  -d "{\"amount\":250000,\"idempotencyKey\":\"$(cat /proc/sys/kernel/random/uuid)\"}"  # later FAILED, over the limit
docker compose logs trade-api | grep -c '<the full account number>'         # 0
```

Or in the browser: **Cash** in the header.
