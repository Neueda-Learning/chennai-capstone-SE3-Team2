from __future__ import annotations

import logging
import sys
from datetime import date, datetime, timedelta, timezone

import pandas as pd

from .config import DEAD_LETTER_DIR, DUCKDB_PATH
from .db import get_duckdb_conn, get_postgres_conn
from .dead_letter import write_dead_letters
from .extract import extract_accounts, extract_instruments, extract_orders
from .load import (
    load_dim_account,
    load_dim_date,
    load_dim_instrument,
    load_fact_trades,
)
from .transform import enrich_orders, transform_accounts, transform_instruments, validate_orders
from .watermark import read_watermark, write_watermark

logger = logging.getLogger(__name__)

_USAGE = """\
usage: python -m etl <command>

commands:
  dims   Load dim_date, dim_instrument and dim_account from Postgres.
  facts  Load fact_trades since the last watermark.
  run    Full incremental run: dims then facts (recommended).
"""

# Default dim_date coverage when running dims without any orders present.
_DIMS_DATE_START = date(2020, 1, 1)
_DIMS_DATE_END = date(2030, 12, 31)


def run_dims(pg_conn, duck_conn) -> None:
    """Load all three dimensions from Postgres into DuckDB."""
    instruments_pg = extract_instruments(pg_conn)
    accounts_pg = extract_accounts(pg_conn)

    instruments_t = transform_instruments(instruments_pg)
    accounts_t = transform_accounts(accounts_pg)

    load_dim_date(duck_conn, _DIMS_DATE_START, _DIMS_DATE_END)
    load_dim_instrument(duck_conn, instruments_t)
    load_dim_account(duck_conn, accounts_t)

    logger.info("dims_done")


def run_facts(
    pg_conn,
    duck_conn,
    batch_id: str,
    dead_letter_dir: str = DEAD_LETTER_DIR,
) -> int:
    """
    Incremental load of fact_trades since the last watermark.
    Returns the number of new rows inserted.
    """
    instruments_pg = extract_instruments(pg_conn)
    accounts_pg = extract_accounts(pg_conn)
    watermark = read_watermark(duck_conn)
    orders_pg = extract_orders(pg_conn, watermark)

    if orders_pg.empty:
        logger.info("run_facts no_new_orders watermark=%s", watermark.isoformat())
        return 0

    # Extend dim_date to cover any new order dates before resolving date_keys.
    min_date = pd.to_datetime(orders_pg["created_at"]).dt.date.min()
    max_date = pd.to_datetime(orders_pg["created_at"]).dt.date.max()
    load_dim_date(duck_conn, min_date, max_date)

    valid_symbols: set[str] = {
        row[0]
        for row in duck_conn.execute("SELECT symbol FROM dim_instrument").fetchall()
    }
    valid_account_ids: set[str] = {
        row[0]
        for row in duck_conn.execute(
            "SELECT account_id FROM dim_account WHERE is_current = TRUE"
        ).fetchall()
    }
    valid_date_keys: set[int] = {
        row[0]
        for row in duck_conn.execute("SELECT date_key FROM dim_date").fetchall()
    }

    enriched = enrich_orders(orders_pg, instruments_pg, accounts_pg)
    clean_df, rejected_validation = validate_orders(
        enriched, valid_symbols, valid_account_ids, valid_date_keys, batch_id
    )
    write_dead_letters(batch_id, rejected_validation, dead_letter_dir)

    n_inserted, rejected_load = load_fact_trades(duck_conn, clean_df, batch_id)
    write_dead_letters(batch_id, rejected_load, dead_letter_dir)

    if not clean_df.empty:
        new_watermark = pd.to_datetime(clean_df["created_at"]).max().to_pydatetime()
        if new_watermark.tzinfo is None:
            new_watermark = new_watermark.replace(tzinfo=timezone.utc)
        write_watermark(duck_conn, new_watermark)
        logger.info("watermark_updated to=%s", new_watermark.isoformat())

    total_rejected = len(rejected_validation) + len(rejected_load)
    logger.info(
        "run_facts_done inserted=%d rejected=%d", n_inserted, total_rejected
    )
    return n_inserted


def run_full(
    pg_conn,
    duck_conn,
    batch_id: str,
    dead_letter_dir: str = DEAD_LETTER_DIR,
) -> int:
    """dims then facts — the standard incremental run."""
    run_dims(pg_conn, duck_conn)
    return run_facts(pg_conn, duck_conn, batch_id, dead_letter_dir)


def main(argv: list[str] | None = None) -> int:
    argv = sys.argv[1:] if argv is None else argv

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)-8s %(name)s %(message)s",
    )

    if not argv or argv[0] not in {"dims", "facts", "run"}:
        print(_USAGE, file=sys.stderr)
        return 2

    command = argv[0]
    batch_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")

    try:
        pg_conn = get_postgres_conn()
        duck_conn = get_duckdb_conn(DUCKDB_PATH)
    except Exception as exc:
        logger.error("startup_failed %s: %s", type(exc).__name__, exc)
        return 1

    try:
        if command == "dims":
            run_dims(pg_conn, duck_conn)
        elif command == "facts":
            run_facts(pg_conn, duck_conn, batch_id)
        else:
            run_full(pg_conn, duck_conn, batch_id)
        return 0
    except Exception as exc:
        logger.exception("pipeline_failed %s: %s", type(exc).__name__, exc)
        return 1
    finally:
        pg_conn.close()
        duck_conn.close()


if __name__ == "__main__":
    sys.exit(main())
