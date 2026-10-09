# Index Justification — Measured Evidence

Every number below was measured, not estimated. Method:

```bash
psql -f tools/drop_performance_indexes.sql && psql -c 'ANALYZE;'
# run Q1-Q6 with EXPLAIN (ANALYZE, BUFFERS)          -> "without"
psql -f indexes/001_performance_indexes.sql && psql -c 'ANALYZE;'
# run Q1-Q6 again                                     -> "with"
```

Dataset: 60,000 live orders, 40,000 archived, 6,000 transfers, 1,980
positions, 500 clients, 250 instruments. PostgreSQL 16.

Read **buffers**, not milliseconds: buffer counts are deterministic,
timings move with cache state. A buffer is an 8 kB page read.

---

## Results

| Query | Index | Buffers before → after | Time before → after | Plan change | Verdict |
|---|---|---|---|---|---|
| **Q1** Order book | `ix_orders_client_placed` | 128 → **22** | 0.685 → **0.193 ms** | Bitmap + sort → Index Scan, no sort | **Justified** |
| **Q2** Pending sweep | `ix_orders_status` | 939 → 939 | 7.075 → **3.371 ms** | Seq Scan → Bitmap Index Scan | **Justified** |
| **Q3** Portfolio load | `ix_position_client` | 9 → 7 | 0.147 → 0.182 | none meaningful | **Not yet justified** |
| **Q4** Funds statement | `ix_fund_transfer_client_created` | 17 → 15 | 0.114 → 0.123 | none meaningful | **Not yet justified** |
| **Q5** Instrument search | `ix_instrument_tradable` | 8 → 8 | 0.243 → 0.265 | none — index never used | **Not justified** |
| **Q6** Archived orders | `ix_orders_history_client_placed` | 88 → **60** | 0.469 → **0.137 ms** | Bitmap + sort → Index Scan, no sort | **Justified** |

**Three of six indexes earn their place at current scale. Three do not.**
That is the honest reading, and the rest of this document explains why —
including why two of the three are still worth keeping.

---

## The justified three

### Q1 — Order book · `ix_orders_client_placed (client_id, date_placed DESC)`

**5.8× fewer buffers.** The composite index serves the filter *and* the
sort, so the plan goes from "find 120 rows, sort them, discard 100" to
"walk the index in order, stop after 20". The `Sort` node disappears
entirely.

The index column order is the whole trick: `client_id` first for the
equality filter, `date_placed DESC` second to match the ORDER BY.
Reverse them and the index would be useless for this query.

### Q2 — Pending sweep · `ix_orders_status (status)`

**2.1× faster.** Seq Scan → Bitmap Index Scan. Buffers stay at 939
because the 4,800 matching rows are scattered across nearly every heap
page, so the table still gets read — the saving is in not *examining*
55,200 non-matching rows.

This index shines brightest when the query needs no heap access at all.
Measured separately: `SELECT count(*) FROM orders WHERE status='PENDING'`
goes **936 → 7 buffers, 12.97 → 0.62 ms** as an Index Only Scan with
`Heap Fetches: 0`.

Worth noting this query runs every few seconds from the black-box poller.
Its cost is paid continuously, unlike the per-user screens.

### Q6 — Archived orders · `ix_orders_history_client_placed`

**3.4× faster**, same mechanism as Q1. Confirms the hot/cold split works:
the archive is queried the same way as the live table and needs the same
index shape.

---

## The unjustified three — and why

### Q3, Q4 — the unique constraints already cover them

This is the most interesting finding. Without `ix_position_client`, the
planner does not fall back to a sequential scan — it uses
`uq_position_client_instrument_type`, the unique constraint on
`(client_id, instrument_id, position_type)`. That index leads on
`client_id`, so it answers the same filter.

Identically for Q4: `uq_fund_transfer_client_idempotency_key` leads on
`client_id` and serves the funds statement.

**Every unique constraint is also a usable index for any query filtering
on a leading prefix of its columns.** Our idempotency and natural-key
constraints happen to lead on `client_id`, which is the filter for most
per-client screens. So several "obvious" client_id indexes are redundant.

The same effect explains why Q1's "without" case is only 128 buffers
rather than a full scan — it borrowed
`uq_orders_client_idempotency_key`. The dedicated index still wins there
because it also provides the sort order, which the unique constraint
does not.

**Recommendation:** keep both. `ix_position_client` and
`ix_fund_transfer_client_created` cost 40 kB and 200 kB respectively —
negligible — and the fallback they rely on is an accident of our
idempotency design. If idempotency keys ever move to a separate registry
table (a limitation already recorded in the README), that fallback
vanishes and these indexes become load-bearing. Keeping them is cheap
insurance against a change we have already contemplated.

### Q5 — `ix_instrument_tradable` is genuinely unused

`pg_stat_user_indexes` reports **`idx_scan = 0`**: never used, not once.

The reason is scale. `instrument` holds 250 rows in three heap pages.
Postgres reads all three faster than it could consult an index and then
fetch pages anyway. It is also unselective — 94% of rows have
`is_tradable = TRUE`, and an index that matches almost everything saves
nothing.

**Recommendation: drop it.** It is 16 kB of pure overhead — every
instrument insert and every delisting update maintains an index nothing
reads. An instrument master reaches perhaps 5,000 rows at NSE+BSE full
coverage, still small enough for a sequential scan.

If instrument search becomes slow, the fix is a **text search index**
(GIN/trigram on `name`), not a boolean flag index. `WHERE name ILIKE
'%pharma%'` is the query users actually run; `is_tradable` is just a
filter riding along.

---

## Storage cost

| Index | Size | Rows covered |
|---|---|---|
| `ix_orders_client_placed` | 1,872 kB | 60,000 |
| `ix_orders_history_client_placed` | 1,248 kB | 40,000 |
| `ix_orders_status` | 424 kB | 60,000 |
| `ix_fund_transfer_client_created` | 200 kB | 6,000 |
| `ix_position_client` | 40 kB | 1,980 |
| `ix_instrument_tradable` | 16 kB | 250 |
| **Total** | **≈3.7 MB** | |

For context, `orders` is 7,520 kB of table and 5,904 kB of *total* index
(performance + unique + primary key). **Indexes cost 78% of the table
size on `orders`** — that ratio is the real price, and it is easy to
miss when thinking only about the indexes you added deliberately.

The cost is not only disk. Every index is maintained on every INSERT and
UPDATE. `orders` is write-heavy — one insert per order, one update per
resolution — so index count directly taxes the order path.

---

## Conclusions

1. **Q1, Q2, Q6 are proven.** Ship them.
2. **Q3, Q4 are redundant today** but cost ~240 kB combined and protect
   against a design change already on the roadmap. Keep, with this note.
3. **Q5 is dead weight.** Drop `ix_instrument_tradable`.
4. **Measure before adding indexes.** Three of six were added on
   plausible reasoning and two turned out redundant, one useless. The
   reasoning was not wrong — it just failed to account for unique
   constraints doubling as indexes, and for small tables never needing
   one.
5. **Re-measure at 10× volume.** Every verdict here is scale-dependent.
   Q3 and Q4 look pointless at 1,980 and 6,000 rows; at 200,000 they may
   not.
