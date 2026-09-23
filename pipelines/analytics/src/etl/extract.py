from __future__ import annotations

import logging
from datetime import datetime

import pandas as pd
import psycopg2

logger = logging.getLogger(__name__)

_INSTRUMENTS_SQL = """
SELECT
    i.instrument_id,
    COALESCE(e.ticker, mf.scheme_code) AS symbol,
    i.name,
    i.instrument_type                   AS asset_class,
    e.exchange_code                     AS exchange,
    ex.country                          AS country,
    i.is_tradable                       AS tradable
FROM instrument i
LEFT JOIN equity      e  ON e.instrument_id  = i.instrument_id
LEFT JOIN mutual_fund mf ON mf.instrument_id = i.instrument_id
LEFT JOIN exchange    ex ON ex.exchange_code = e.exchange_code
WHERE COALESCE(e.ticker, mf.scheme_code) IS NOT NULL
"""

_ACCOUNTS_SQL = """
SELECT
    ca.client_id,
    ca.account_ref AS account_id,
    cp.name        AS holder_name,
    ca.status,
    ca.created_at,
    ca.updated_at
FROM client_account ca
JOIN client_profile cp ON cp.client_id = ca.client_id
"""

# Watermark is on date_placed (the column the contract calls "created_on").
_ORDERS_SQL = """
SELECT
    o.order_id::TEXT AS source_order_id,
    o.client_id,
    o.instrument_id,
    o.side,
    o.quantity,
    o.price,
    o.fill_price     AS executed_price,
    o.status,
    o.date_placed    AS created_at
FROM orders o
WHERE o.date_placed > %s
ORDER BY o.date_placed ASC
"""


def extract_instruments(pg_conn: psycopg2.extensions.connection) -> pd.DataFrame:
    df = pd.read_sql(_INSTRUMENTS_SQL, pg_conn)
    logger.info("extract_instruments rows=%d", len(df))
    return df


def extract_accounts(pg_conn: psycopg2.extensions.connection) -> pd.DataFrame:
    df = pd.read_sql(_ACCOUNTS_SQL, pg_conn)
    logger.info("extract_accounts rows=%d", len(df))
    return df


def extract_orders(
    pg_conn: psycopg2.extensions.connection,
    since: datetime,
) -> pd.DataFrame:
    df = pd.read_sql(_ORDERS_SQL, pg_conn, params=(since,))
    logger.info("extract_orders since=%s rows=%d", since.isoformat(), len(df))
    return df
