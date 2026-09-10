# Design — Retention, Grain, Growth and Cost

Sprint 1 data-management design for the trading platform schema.
Figures are extrapolated from measured storage: see §5.

---

## 1. Grain

Grain is what one row represents. If it cannot be stated in one sentence,
the table is doing more than one job.

| Table | One row per… | Natural key |
|---|---|---|
| `client_account` | client | `pan`, `demat_id` |
| `client_profile` | client | `email` |
| `client_auth` | client | `client_id` |
| `fund_transfer` | deposit or withdrawal **attempt** | `reference_id` |
| `exchange` | trading venue | `exchange_code` |
| `amc` | fund house | `amc_code` |
| `instrument` | tradeable security | `isin` |
| `equity` | exchange-traded scrip | `(ticker, exchange_code)` |
| `mutual_fund` | MF scheme | `scheme_code` |
| `orders` | order **placed** | `(client_id, idempotency_key)` |
| `orders_history` | archived order | `(client_id, idempotency_key)` |
| `position` | client × instrument × position type | `(client_id, instrument_id, position_type)` |

Two grain decisions worth defending:

**`fund_transfer` is per *attempt*, not per successful transfer.** A
FAILED deposit is a row. Without it there is no record that the client
tried, which is the first thing support asks about.

**`position` is per client × instrument × *position type*.** The same
client holding the same scrip both intraday and as delivery is two rows,
not one — because they are genuinely different things: one is squared off
tonight, one sits in the demat account. Merging them would lose the
distinction the Positions/Holdings tabs are built on.

---

## 2. Retention

| Table | Live retention | Then | Final disposal |
|---|---|---|---|
| `orders` | 7 days after reaching terminal status | move to `orders_history` | — |
| `orders_history` | 7 years | — | archive to cold storage, then purge |
| `fund_transfer` | 7 years | — | purge |
| `position` | while quantity > 0 | row deleted when position closes | — |
| `client_*` | life of account + 7 years | — | purge after regulatory window |
| `instrument`, `equity`, `mutual_fund` | indefinite | — | never purged |
| `exchange`, `amc` | indefinite | — | never purged |

**Why seven years:** SEBI requires brokers to retain trading and
client records for a minimum period measured in years; seven is the
common industry practice that satisfies it with margin. Confirm the exact
obligation with compliance before the first purge job is written — this
is a placeholder informed by convention, not legal advice.

**Why orders move after seven days, not one:** the "Orders" screen shows
recent activity, and a client checking on Monday expects to see Friday's
orders without visiting Reports. Seven days covers a weekend plus slack.

**`position` deletion is intentional.** When a holding is fully sold the
row is removed rather than zeroed — `ck_position_quantity_positive`
enforces this. History of *how* the position moved lives in the orders
tables, not here.

**Not yet decided:** whether `orders_history` purges at all, or moves to
cold storage (S3/Parquet) and stays queryable. At the volumes in §4 this
is not urgent, but it is a decision the retention job needs before it can
be written.

---

## 3. Population and incremental extraction

**Initial population**

| Table | Source | Frequency |
|---|---|---|
| `exchange`, `amc` | static reference data, seeded | once, rarely amended |
| `instrument` + subtypes | exchange/AMFI master files via the black box | daily sync before market open |
| `client_*` | onboarding flow | continuous |
| `orders`, `position`, `fund_transfer` | application traffic | continuous |

**Incremental extraction** — pulling only what changed since the last
run, for reporting, reconciliation or a future warehouse.

The column that makes this possible is `orders.resolved_at`. A
naive job keying off `date_placed` would **miss any order placed
yesterday and resolved today** — it was already extracted, in its PENDING
state, and would never be revisited. Extracting on `resolved_at` catches
the state change:

```sql
SELECT * FROM orders
WHERE resolved_at > :last_watermark
ORDER BY resolved_at;
```

This is why 001 renamed `last_updated` to `resolved_at` rather than
dropping it. Its value is entirely in enabling this pattern.

**Per-table extraction key:**

| Table | Watermark column | Notes |
|---|---|---|
| `orders` | `resolved_at` | PENDING rows are NULL and extracted only once resolved |
| `orders_history` | `archived_at` | monotonic, set by the archival job |
| `fund_transfer` | `created_at` | ⚠️ **gap — see below** |
| `position` | *none* | ⚠️ **gap — see below** |
| `client_account` | `created_at` | ⚠️ inserts only; updates invisible |

**Three known gaps, stated deliberately:**

1. **`fund_transfer` has no `updated_at`.** A transfer created PENDING
   and later marked SUCCESS changes without moving its `created_at`. Any
   incremental job keyed on `created_at` sees the PENDING version
   forever. Fix: add `resolved_at`, mirroring `orders`.

2. **`position` has no timestamp at all.** It is pure current state with
   no `updated_at`, so incremental extraction is impossible — a consumer
   must re-read the whole table every time. Acceptable at ~2,000 rows,
   untenable at 500,000. Fix: add `updated_at`, maintained on every
   upsert.

3. **`client_account.version` increments on every write** and could serve
   as a change signal, but there is no index on it and no timestamp, so
   it identifies *that* a row changed, not *when*. Not usable as a
   watermark as it stands.

All three are schema additions for a future migration, not sprint 1 work.

---

## 4. Growth

Assumptions, stated so they can be argued with:

- 10,000 active clients at end of year 1
- 5 orders per active client per trading day
- 250 trading days per year
- 30% of clients transact funds twice a month
- 8 open positions per client on average

| Table | Rows / year | Basis |
|---|---|---|
| `orders` (live) | ~62,500 at any moment | 12.5M/yr × 7/250 retention window |
| `orders_history` | **12,500,000** | 10,000 × 5 × 250 |
| `fund_transfer` | 72,000 | 3,000 clients × 24 |
| `position` | ~80,000 steady state | not cumulative — rows are deleted on close |
| `client_account` + profile + auth | 10,000 each | cumulative |
| `instrument` + subtypes | ~5,000 | full NSE+BSE+AMFI coverage, then flat |

**The whole growth story is `orders_history`.** Everything else is
rounding error. Live `orders` stays small *by design* — that is what the
hot/cold split buys, and it is why the archival job is not optional.

---

## 5. Cost

Extrapolated from measured storage on the seeded database (60,000 orders
= 7,520 kB table + 5,904 kB indexes), giving **≈128 bytes/row of table
and ≈100 bytes/row of index** for order-shaped data.

| Horizon | `orders_history` rows | Table | Indexes | Total |
|---|---|---|---|---|
| Year 1 | 12.5M | ~1.6 GB | ~1.3 GB | **~2.9 GB** |
| Year 3 | 37.5M | ~4.8 GB | ~3.8 GB | **~8.6 GB** |
| Year 7 (full retention) | 87.5M | ~11.2 GB | ~8.8 GB | **~20 GB** |

Everything else combined stays under 200 MB across the same period.

**Reading these numbers:**

- **20 GB after seven years is not a scaling problem.** It fits on a
  single modest instance. There is no case for sharding, and the
  partitioning discussion is about query and maintenance convenience, not
  capacity.
- **Indexes are ~78% of table size** on order data — measured, not
  assumed. Every index added to `orders` or `orders_history` costs
  roughly 0.8× what the data costs. This is the strongest argument for
  the discipline in `index_justification.md`.
- **Write cost matters more than storage.** At 12.5M orders/year the
  order path is ~50,000 writes on a busy day, each maintaining every
  index on the table. Index count taxes latency on the hot path, and
  latency is what users feel.

**When to revisit:** if `orders_history` passes ~50M rows, or if archival
queries start scanning ranges rather than single clients, partition it by
month on `date_placed`. Not before — partitioning adds operational
complexity that 12M rows do not warrant.

**What is not costed here:** backup storage (roughly 1× the database per
full backup, times retention), WAL volume, and read replicas. All scale
linearly with the figures above and none change the conclusion.

---

## 6. Summary of deferred schema work

Collected from the gaps named above, for the sprint-2 backlog:

1. `fund_transfer.resolved_at` — makes incremental extraction correct
2. `position.updated_at` — makes incremental extraction possible at all
3. Drop `ix_instrument_tradable` — measured as never used
4. Trade ledger table — balance changes from fills are still unrecorded
5. Decide `orders_history` final disposal: purge vs cold storage
6. Confirm the retention period with compliance
