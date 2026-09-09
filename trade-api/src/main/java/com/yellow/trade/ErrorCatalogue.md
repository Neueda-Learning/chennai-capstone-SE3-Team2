# Error Catalogue

This document is the single reference for the error codes exposed by the Trade REST API.

## Error Envelope

Every API failure returns the same response shape:

``` json
{
  "errorCode": "...",
  "message": "..."
}
```

The `errorCode` is the value clients should use for branching. 
HTTP status alone is not sufficient because multiple error codes can share the same status.

## Error Catalogue

| Error Code | HTTP Status | Meaning / Raised When | Human-facing Message |
|---|---:|---|---|
| `ACC-404` | 404 | No account exists with the requested key | `Account not found` |
| `ACC-403` | 403 | Account is not active, or the token does not reach the account | `Account not active` |
| `INS-404` | 404 | Instrument is unknown or no longer tradable | `Instrument not found` |
| `ORD-400` | 400 | A buy costs more than the available cash | `Insufficient funds` |
| `ORD-409` | 409 | Insufficient holdings | `Insufficient holdings` |
| `ORD-409` | 409 | Reused idempotency key / duplicate order | `Duplicate order` |
| `ORD-409` | 409 | Order cannot be cancelled | `Order is not cancellable` |
| `ORD-409` | 409 | Order was not found (as currently defined by the domain exception) | `Order not found` |
| `ORD-422` | 422 | Invalid order input (as currently defined by the domain exception) | `Invalid Order` |
| `VAL-422` | 422 | Request failed field validation | `Invalid input` |
| `AUTH-401` | 401 | Missing, malformed, expired, or wrongly signed token | Same generic authentication failure message |