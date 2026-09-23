from __future__ import annotations

import logging
from datetime import date, datetime, timedelta, timezone

import duckdb
import pandas as pd

logger = logging.getLogger(__name__)

_DAY_NAMES = [
    "Monday", "Tuesday", "Wednesday", "Thursday",
    "Friday", "Saturday", "Sunday",
]
_MONTH_NAMES = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
]


def load_dim_date(
    conn: duckdb.DuckDBPyConnection,
    min_date: date,
    max_date: date,
) -> None:
    """Populate dim_date for the full range; idempotent on date_key."""
    rows = _build_date_rows(min_date, max_date)
    if not rows:
        return

    date_df = pd.DataFrame(rows)
    conn.register("_date_df", date_df)
    try:
        conn.execute("""
            INSERT OR IGNORE INTO dim_date
            SELECT date_key, full_date, day, month, year, quarter,
                   day_of_week, day_name, month_name, is_weekday
            FROM _date_df
        """)
    finally:
        conn.unregister("_date_df")
    logger.info("load_dim_date min=%s max=%s rows=%d", min_date, max_date, len(rows))


def load_dim_instrument(
    conn: duckdb.DuckDBPyConnection,
    df: pd.DataFrame,
) -> dict[str, int]:
    """
    Type 1 upsert on symbol: insert new instruments, update existing in place.
    Returns the full symbol → instrument_key map after loading.
    """
    if df.empty:
        return {}

    now = datetime.now(timezone.utc)
    existing: dict[str, int] = {
        row[0]: row[1]
        for row in conn.execute(
            "SELECT symbol, instrument_key FROM dim_instrument"
        ).fetchall()
    }
    max_key: int = conn.execute(
        "SELECT COALESCE(MAX(instrument_key), 0) FROM dim_instrument"
    ).fetchone()[0]

    key_counter = max_key
    inserted = updated = 0

    for _, row in df.iterrows():
        symbol = row.get("symbol")
        if symbol is None or pd.isna(symbol):
            continue

        name = str(row["name"])
        asset_class = str(row["asset_class"])
        currency = str(row["currency"])
        exchange = None if pd.isna(row.get("exchange")) else str(row["exchange"])
        tradable = bool(row["tradable"])

        if symbol in existing:
            conn.execute(
                """
                UPDATE dim_instrument
                   SET name        = ?,
                       asset_class = ?,
                       currency    = ?,
                       exchange    = ?,
                       tradable    = ?,
                       loaded_at   = ?
                 WHERE symbol = ?
                """,
                [name, asset_class, currency, exchange, tradable, now, symbol],
            )
            updated += 1
        else:
            key_counter += 1
            conn.execute(
                """
                INSERT OR IGNORE INTO dim_instrument
                    (instrument_key, symbol, name, asset_class,
                     currency, exchange, tradable, loaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                [key_counter, symbol, name, asset_class,
                 currency, exchange, tradable, now],
            )
            inserted += 1

    logger.info(
        "load_dim_instrument inserted=%d updated=%d", inserted, updated
    )

    return {
        row[0]: row[1]
        for row in conn.execute(
            "SELECT symbol, instrument_key FROM dim_instrument"
        ).fetchall()
    }


def load_dim_account(
    conn: duckdb.DuckDBPyConnection,
    df: pd.DataFrame,
) -> dict[str, int]:
    """
    Type 2 SCD merge.  A status change closes the current version and opens
    a new one; an unchanged status is a no-op.
    Returns account_id → account_key for all current rows.
    """
    if df.empty:
        return {}

    now = datetime.now(timezone.utc)
    max_key: int = conn.execute(
        "SELECT COALESCE(MAX(account_key), 0) FROM dim_account"
    ).fetchone()[0]

    key_counter = max_key
    inserted = scd_updated = unchanged = 0

    for _, pg_row in df.iterrows():
        account_id = str(pg_row["account_id"])
        pg_status = str(pg_row["status"])
        holder_name = str(pg_row["holder_name"])
        source_id = int(pg_row["client_id"])
        created_date = pd.to_datetime(pg_row["created_at"]).date()
        updated_date = pd.to_datetime(pg_row["updated_at"]).date()

        current = conn.execute(
            """
            SELECT account_key, status
            FROM dim_account
            WHERE account_id = ? AND is_current = TRUE
            """,
            [account_id],
        ).fetchone()

        if current is None:
            key_counter += 1
            conn.execute(
                """
                INSERT INTO dim_account
                    (account_key, account_id, holder_name, status,
                     effective_date, end_date, is_current, source_id, loaded_at)
                VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, ?)
                """,
                [key_counter, account_id, holder_name, pg_status,
                 created_date, source_id, now],
            )
            inserted += 1

        elif current[1] != pg_status:
            old_key = current[0]
            end_date = updated_date - timedelta(days=1)
            conn.execute(
                "UPDATE dim_account SET end_date = ?, is_current = FALSE"
                " WHERE account_key = ?",
                [end_date, old_key],
            )
            key_counter += 1
            conn.execute(
                """
                INSERT INTO dim_account
                    (account_key, account_id, holder_name, status,
                     effective_date, end_date, is_current, source_id, loaded_at)
                VALUES (?, ?, ?, ?, ?, NULL, TRUE, ?, ?)
                """,
                [key_counter, account_id, holder_name, pg_status,
                 updated_date, source_id, now],
            )
            scd_updated += 1

        else:
            unchanged += 1

    logger.info(
        "load_dim_account inserted=%d scd_updated=%d unchanged=%d",
        inserted, scd_updated, unchanged,
    )

    return {
        row[0]: row[1]
        for row in conn.execute(
            "SELECT account_id, account_key FROM dim_account WHERE is_current = TRUE"
        ).fetchall()
    }


def load_fact_trades(
    conn: duckdb.DuckDBPyConnection,
    df: pd.DataFrame,
    batch_id: str,
) -> tuple[int, list[dict]]:
    """
    Resolve dimension surrogate keys and insert into FACT_TRADES.
    Rows that cannot be resolved are returned as rejected dicts
    (caller writes them to the dead-letter file).
    Duplicate source_order_id rows are silently skipped (INSERT OR IGNORE).
    Returns (rows_inserted, rejected_rows).
    """
    if df.empty:
        return 0, []

    now = datetime.now(timezone.utc)
    max_key: int = conn.execute(
        "SELECT COALESCE(MAX(trade_key), 0) FROM fact_trades"
    ).fetchone()[0]

    symbol_key_map: dict[str, int] = {
        row[0]: row[1]
        for row in conn.execute(
            "SELECT symbol, instrument_key FROM dim_instrument"
        ).fetchall()
    }
    account_key_map: dict[str, int] = {
        row[0]: row[1]
        for row in conn.execute(
            "SELECT account_id, account_key FROM dim_account WHERE is_current = TRUE"
        ).fetchall()
    }

    rows_to_insert: list[list] = []
    rejected: list[dict] = []
    key_counter = max_key

    for _, row in df.iterrows():
        symbol = row.get("symbol")
        instrument_key = symbol_key_map.get(str(symbol)) if symbol else None
        if instrument_key is None:
            rejected.append({
                "batch_id": batch_id,
                "reason": f"symbol {symbol!r} not found in dim_instrument",
                "row": row.to_dict(),
            })
            continue

        account_ref = row.get("account_ref")
        account_key = account_key_map.get(str(account_ref)) if account_ref else None
        if account_key is None:
            rejected.append({
                "batch_id": batch_id,
                "reason": f"account_id {account_ref!r} not found in dim_account",
                "row": row.to_dict(),
            })
            continue

        created_at = pd.to_datetime(row["created_at"])
        date_key = int(created_at.strftime("%Y%m%d"))

        executed_price = row.get("executed_price")
        if executed_price is not None and pd.isna(executed_price):
            executed_price = None
        if executed_price is not None:
            executed_price = float(executed_price)

        key_counter += 1
        rows_to_insert.append([
            key_counter,
            account_key,
            instrument_key,
            date_key,
            str(row["side"]),
            float(row["quantity"]),
            float(row["price"]),
            str(row["status"]),
            executed_price,
            float(row["trade_value"]),
            str(row["source_order_id"]),
            created_at.to_pydatetime(),
            now,
        ])

    if not rows_to_insert:
        logger.info("load_fact_trades inserted=0 rejected=%d", len(rejected))
        return 0, rejected

    before: int = conn.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    conn.executemany(
        """
        INSERT OR IGNORE INTO fact_trades
            (trade_key, account_key, instrument_key, date_key,
             side, quantity, price, status, executed_price,
             trade_value, source_order_id, created_at, loaded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rows_to_insert,
    )
    after: int = conn.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
    inserted = after - before

    logger.info(
        "load_fact_trades inserted=%d rejected=%d", inserted, len(rejected)
    )
    return inserted, rejected


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _build_date_rows(start: date, end: date) -> list[dict]:
    rows: list[dict] = []
    current = start
    while current <= end:
        dow = current.weekday()
        rows.append({
            "date_key":    int(current.strftime("%Y%m%d")),
            "full_date":   current,
            "day":         current.day,
            "month":       current.month,
            "year":        current.year,
            "quarter":     (current.month - 1) // 3 + 1,
            "day_of_week": dow,
            "day_name":    _DAY_NAMES[dow],
            "month_name":  _MONTH_NAMES[current.month - 1],
            "is_weekday":  dow < 5,
        })
        current += timedelta(days=1)
    return rows
