# Deviations from the binding contract

`trade-api.yaml` and `auth-api.yaml` in this folder are the **programme's own
contracts, verbatim**. They are the specification; we neither author nor change
them. This file records every place our implementation knowingly differs, so
the review reads a decision rather than a defect.

There are two.

---

## Quantity is decimal on responses, not `int32`

**Contract:** `OrderResponse.quantity`, `OrderHistoryEntry.quantity` and
`PositionResponse.quantity` are `integer / int32`.

**Ours:** those three are decimal. `PlaceOrderRequest.quantity` is unchanged and
remains `integer / int32`.

**Why.** The platform trades mutual funds, and an MF allotment is not a whole
number of units — it is the money divided by that day's NAV. Our Sprint 3
schema types `orders.quantity` and `position.quantity` as `NUMERIC(18,6)` for
exactly that reason, and the seeded data contains real fractional holdings
(152.386000, 240.117000, 980.500000). Reading any of those into a 32-bit
integer throws, and rounding them reports a holding the client does not have.

**Scope.** Input is untouched: this API accepts whole units only, so an order
placed through it is always integral. The deviation is on the way out, where
rows created by other channels already exist.

**Cost, stated honestly.** A field-for-field check against the contract will
flag these three. In the generated Angular client it changes nothing: an
OpenAPI `integer` and a `number` both generate `number` in TypeScript.

**If the programme rules against this**, the change is three field types and
three seeded quantities, and nothing else in the service moves.

---

## Registration takes an activation token, not an account number

**Contract:** `auth-api.yaml`, `RegisterRequest`:

```yaml
additionalProperties: false
required: [username, password, accountId]
properties: { username, password, accountId, roles }
```

**Ours:** `{ username, password, activationToken }`, all three required,
`additionalProperties: false` kept. `accountId` and `roles` are gone:
sending either is refused with `VAL-422`. `activationToken` is 64 lowercase hex
characters, from the activation email.

`POST /auth/register` also gains a `401` response: an unknown, expired,
already-used or superseded token, or a token whose account already has a login,
answers `AUTH-401 Unauthorised`, identically in every case. `201`, `409` and
`422` are unchanged, and `UserResponse` is unchanged — `accountId` is still
returned, as a number, resolved from the token.

**Why.** Security review A01 (`docs/security/sprint-08-auth-review.md`). With
`accountId` in the request, knowing a provisioned account number was the whole
entitlement to claim it, and account numbers are small sequential integers.
Now the account comes from a one-time token that only the address on
`client_profile` receives. Removing `roles` closes the review's second accepted
risk under A01: a registrant could ask for `ADMIN`.

**Scope.** Registration only. Login, refresh, `/auth/me`, the JWT claim set
(`sub, accountId, roles, iat, exp, iss`, `accountId` numeric) and every response
body are unchanged. The internal token route (`POST /internal/activation-tokens`)
and the activation page (`/activate`) are not part of this contract and are
excluded from the served OpenAPI document.

**Cost, stated honestly.** A generated client built from the contract sends
`accountId` and gets `422`. The served document at `/docs/json` describes the
new request, so it and `auth-api.yaml` now disagree on this one schema. Needs
cohort agreement, since the Angular registration screen is built from it.

**If the programme rules against this**, A01 reopens: the change is the
`RegisterDto` fields and `AuthService.register`, and the activation token can
stay as an additional required field only if `additionalProperties` is relaxed.
