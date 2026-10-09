from __future__ import annotations

from datetime import datetime, timezone

import duckdb
import pandas as pd
import pytest

from etl.db import _SCHEMA_DDL


@pytest.fixture
def duck():
    conn = duckdb.connect(":memory:")
    conn.execute(_SCHEMA_DDL)
    yield conn
    conn.close()


@pytest.fixture
def sample_instruments_df():
    return pd.DataFrame([
        {
            "instrument_id": 1,
            "symbol": "INFY.NS",
            "name": "Infosys Limited",
            "asset_class": "STOCK",
            "exchange": "NSE",
            "country": "IN",
            "tradable": True,
        },
        {
            "instrument_id": 2,
            "symbol": "RELIANCE.NS",
            "name": "Reliance Industries",
            "asset_class": "STOCK",
            "exchange": "NSE",
            "country": "IN",
            "tradable": True,
        },
    ])


@pytest.fixture
def sample_accounts_df():
    return pd.DataFrame([
        {
            "client_id": 1,
            "account_id": "ACC-000001",
            "holder_name": "Alice Kumar",
            "status": "ACTIVE",
            "created_at": pd.Timestamp("2026-01-01 00:00:00", tz="UTC"),
            "updated_at": pd.Timestamp("2026-01-01 00:00:00", tz="UTC"),
        },
    ])


@pytest.fixture
def sample_orders_df():
    return pd.DataFrame([
        {
            "source_order_id": "order-uuid-1",
            "client_id": 1,
            "instrument_id": 1,
            "side": "BUY",
            "quantity": 100.0,
            "price": 1500.00,
            "executed_price": 1495.00,
            "status": "FILLED",
            "created_at": pd.Timestamp("2026-09-01 09:00:00", tz="UTC"),
        },
    ])
