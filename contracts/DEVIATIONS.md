# Deviations from the binding contract

`trade-api.yaml` and `auth-api.yaml` in this folder are the **programme's own
contracts, verbatim**. They are the specification; we neither author nor change
them. This file records every place our implementation knowingly differs, so
the review reads a decision rather than a defect.

There is exactly one.

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
