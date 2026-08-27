# Data Dictionary

Schema state: `000_base_schema.sql` + `001_schema_migrations.sql` applied.
Target: PostgreSQL. All money is `NUMERIC` (never float). All quantities are
`NUMERIC` (MF units are fractional).

---

### `client_account` — one row per client
Trading identity, KYC state and money. The row the trading engine touches.

| Column | Type | Meaning |
|---|---|---|
| `client_id` | INTEGER, PK, identity | Surrogate key referenced by every other client-owned table |
| `pan` | VARCHAR(10), UK | Tax ID; mandatory for trading under SEBI rules |
| `demat_id` | VARCHAR(16), UK | Depository account where securities are held |
| `kyc_status` | VARCHAR(10) | PENDING / VERIFIED / REJECTED. Must be VERIFIED to trade |
| `status` | VARCHAR(10) | ACTIVE / SUSPENDED / CLOSED. Account lifecycle |
| `balance` | NUMERIC(18,4) | Total cash held, including blocked |
| `blocked_funds` | NUMERIC(18,4) | Subset of balance held against pending orders |
| `version` | INTEGER | Optimistic-lock counter, bumped on every write |
| `created_at` | TIMESTAMPTZ | Row creation time |

Constraints: balance ≥ 0, blocked ≥ 0, blocked ≤ balance, version ≥ 0.

---

### `client_profile` — one row per client
Personal / KYC-visible details. 1:1 with account.

| Column | Type | Meaning |
|---|---|---|
| `client_id` | INTEGER, PK, FK | Same key as the account |
| `name` | VARCHAR(120) | Full legal name |
| `dob` | DATE | Date of birth |
| `email` | VARCHAR(200), UK | Login identifier and contact |
| `phone_number` | VARCHAR(20) | String, not int — leading zeros and country codes |
| `address` | TEXT | Kept opaque on purpose (see normalisation doc) |

---

### `client_auth` — one row per client
Login credentials, isolated from all other data.

| Column | Type | Meaning |
|---|---|---|
| `client_id` | INTEGER, PK, FK | Same key as the account |
| `password_hash` | VARCHAR(255) | bcrypt/argon2 hash. Never plaintext |
| `last_login` | TIMESTAMPTZ | Nullable until first login |

---

### `fund_transfer` — one row per deposit/withdrawal attempt
Audit trail for money entering and leaving the platform.

| Column | Type | Meaning |
|---|---|---|
| `transfer_id` | BIGINT, PK, identity | Surrogate key |
| `client_id` | INTEGER, FK | Owner |
| `amount` | NUMERIC(18,4) | Always positive; direction carries the sign |
| `direction` | VARCHAR(10) | DEPOSIT / WITHDRAWAL |
| `status` | VARCHAR(10) | PENDING / SUCCESS / FAILED |
| `reference_id` | VARCHAR(100), UK* | Payment gateway's payment ID. *Partial unique index (non-null only) |
| `idempotency_key` | VARCHAR(64) | Caller-generated; unique per client |
| `created_at` | TIMESTAMPTZ | Request time |

---

### `exchange` — one row per trading venue
Lookup table so NSE/BSE isn't retyped on every scrip.

| Column | Type | Meaning |
|---|---|---|
| `exchange_code` | VARCHAR(10), PK | Natural key: 'NSE', 'BSE' |
| `name` | VARCHAR(120) | Full exchange name |
| `country` | CHAR(2) | ISO country code, default 'IN' |
| `is_active` | BOOLEAN | Venue currently operating |

---

### `amc` — one row per fund house
Lookup table so the AMC name isn't repeated on every scheme.

| Column | Type | Meaning |
|---|---|---|
| `amc_id` | INTEGER, PK, identity | Surrogate — insulates schemes from external code changes |
| `amc_code` | VARCHAR(20), UK | External (AMFI) identifier |
| `amc_name` | VARCHAR(120) | Fund house name |
| `is_active` | BOOLEAN | Still operating |

---

### `instrument` — one row per tradeable security
Supertype. Everything orderable on the platform lives here.

| Column | Type | Meaning |
|---|---|---|
| `instrument_id` | INTEGER, PK, identity | Referenced by orders and positions |
| `instrument_type` | VARCHAR(10) | STOCK / ETF / MF. Determines which subtype table holds the child row |
| `name` | VARCHAR(200) | Display name |
| `isin` | VARCHAR(12), UK | Global 12-char identifier, stable across exchanges |
| `is_tradable` | BOOLEAN | FALSE = delisted/suspended: hidden from search, no new orders. Existing holdings stay visible |

Also carries `UNIQUE (instrument_id, instrument_type)` — exists purely as the
target of the subtype composite FKs.

---

### `equity` — one row per exchange-traded scrip
Subtype for stocks and ETFs.

| Column | Type | Meaning |
|---|---|---|
| `instrument_id` | INTEGER, PK, FK | Same key as the parent |
| `instrument_type` | VARCHAR(10), FK | Pinned to STOCK/ETF; half of the composite FK enforcing disjointness |
| `ticker` | VARCHAR(30) | Exchange symbol, e.g. INFY |
| `exchange_code` | VARCHAR(10), FK | Listing venue |
| `lot_size` | INTEGER | Minimum tradeable multiple, default 1 |

Unique on `(ticker, exchange_code)` — the same ticker can exist on both NSE and BSE.

---

### `mutual_fund` — one row per MF scheme
Subtype for mutual funds.

| Column | Type | Meaning |
|---|---|---|
| `instrument_id` | INTEGER, PK, FK | Same key as the parent |
| `instrument_type` | VARCHAR(10), FK | Pinned to 'MF'; half of the composite FK |
| `scheme_code` | VARCHAR(30), UK | AMFI scheme identifier |
| `amc_id` | INTEGER, FK | Managing fund house |
| `plan_type` | VARCHAR(20) | DIRECT_GROWTH / DIRECT_IDCW / REGULAR_GROWTH / REGULAR_IDCW |
| `expense_ratio` | NUMERIC(5,4) | Nullable; ≥ 0 when present |

---

### `orders` — one row per order placed
Live and recent orders. Terminal rows are archived by the EOD job.

| Column | Type | Meaning |
|---|---|---|
| `order_id` | BIGINT, PK, identity | Carried over to history on archival, so unique across both |
| `client_id` | INTEGER, FK | Who placed it |
| `instrument_id` | INTEGER, FK | What was ordered |
| `side` | VARCHAR(4) | BUY / SELL |
| `order_type` | VARCHAR(10) | MARKET / LIMIT |
| `product_type` | VARCHAR(3) | MIS (intraday) / CNC (delivery). Determines the resulting `position_type` |
| `price` | NUMERIC(18,4) | Limit price. Required for LIMIT, nullable for MARKET |
| `quantity` | NUMERIC(18,6) | Units requested. > 0 |
| `fill_price` | NUMERIC(18,4) | Actual executed price. Required when status = SUCCESS |
| `status` | VARCHAR(10) | PENDING / SUCCESS / FAILED / CANCELLED |
| `idempotency_key` | VARCHAR(64) | Caller-generated; unique per client |
| `date_placed` | TIMESTAMPTZ | Submission time |
| `resolved_at` | TIMESTAMPTZ | When the black box resolved it. NULL iff PENDING |

Constraints: LIMIT requires price; SUCCESS requires fill_price; `resolved_at`
is NULL exactly when status is PENDING.

---

### `orders_history` — one row per archived order
Column-identical to `orders`, plus `archived_at`. Keeps `orders` small.

| Column | Type | Meaning |
|---|---|---|
| *(all columns as in `orders`)* | | Carried over unchanged |
| `archived_at` | TIMESTAMPTZ | When the EOD job moved the row |

Status is restricted to terminal values only: SUCCESS / FAILED / CANCELLED.

---

### `position` — one row per client × instrument × position type
Current holdings. INTRADAY feeds the Positions tab, DELIVERY the Holdings tab.

| Column | Type | Meaning |
|---|---|---|
| `position_id` | BIGINT, PK, identity | Surrogate key |
| `client_id` | INTEGER, FK | Owner |
| `instrument_id` | INTEGER, FK | What is held |
| `position_type` | VARCHAR(10) | INTRADAY / DELIVERY |
| `quantity` | NUMERIC(18,6) | Units currently held. > 0 |
| `average_price` | NUMERIC(18,4) | Average cost per unit. > 0. Unchanged by sells |

Unique on `(client_id, instrument_id, position_type)` — the natural key.
Deliberately has **no FK to `orders`**: a holding outlives the archived order
that created it.

---

## Derived values — computed, never stored

```
available funds = balance − blocked_funds
invested value  = Σ (quantity × average_price)
current value   = Σ (quantity × live_price)      ← live_price from black box
P&L             = current value − invested value
net worth       = balance + current value
```
