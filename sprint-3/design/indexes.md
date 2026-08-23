# Index Notes

## 1. Indexes Created

Indexes were added based on the queries that are expected to be performed frequently:

1) `idx_orders_account_received` — helps retrieve orders for a particular client, sorted by most recent order.

2) `idx_orders_instrument` — helps find orders associated with a particular instrument.

3) Primary key and unique constraints also create indexes automatically and are used for lookups and enforcing uniqueness.

## 2. Index Justification

### `idx_orders_account_received`

**Query:** Retrieve a client's orders, newest first.

**Why the index is useful:**
The query filters by `account_id` and sorts by `received_at DESC`.
The composite index supports both operations.

**Without the index:**
PostgreSQL may perform a sequential scan followed by a sort.

**With the index:**
PostgreSQL can locate the client's orders through the index and
read them in the required order.

**Write cost:**
Every order insert requires an additional index entry.

### `idx_orders_instrument`

**Query:** Find all orders for a particular instrument.

**Why the index is useful:**
The query filters by `instrument_id`, which is the leading column
of the index.

**Without the index:**
PostgreSQL may scan the entire `orders` table.

**With the index:**
PostgreSQL can use an index scan to find matching orders.

**Write cost:**
Every order insert requires the index to be updated.

