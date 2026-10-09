from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import MagicMock

import duckdb
import pandas as pd
import pytest

from etl.db import _SCHEMA_DDL
from etl.pipeline import run_full


def _make_duck() -> duckdb.DuckDBPyConnection:
    conn = duckdb.connect(":memory:")
    conn.execute(_SCHEMA_DDL)
    return conn


def _instruments_df() -> pd.DataFrame:
    return pd.DataFrame([{
        "instrument_id": 1,
        "symbol": "INFY.NS",
        "name": "Infosys Limited",
        "asset_class": "STOCK",
        "exchange": "NSE",
        "country": "IN",
        "tradable": True,
    }])


def _accounts_df() -> pd.DataFrame:
    return pd.DataFrame([{
        "client_id": 1,
        "account_id": "ACC-000001",
        "holder_name": "Alice Kumar",
        "status": "ACTIVE",
        "created_at": pd.Timestamp("2026-01-01", tz="UTC"),
        "updated_at": pd.Timestamp("2026-01-01", tz="UTC"),
    }])


def _orders_df(order_id: str = "order-uuid-1") -> pd.DataFrame:
    return pd.DataFrame([{
        "source_order_id": order_id,
        "client_id": 1,
        "instrument_id": 1,
        "side": "BUY",
        "quantity": 100.0,
        "price": 1500.00,
        "executed_price": 1495.00,
        "status": "FILLED",
        "created_at": pd.Timestamp("2026-09-01 09:00:00", tz="UTC"),
    }])


def _patch_extracts(mocker, orders_df: pd.DataFrame) -> None:
    mocker.patch("etl.pipeline.extract_instruments", return_value=_instruments_df())
    mocker.patch("etl.pipeline.extract_accounts", return_value=_accounts_df())
    mocker.patch("etl.pipeline.extract_orders", return_value=orders_df)


class TestIncrementalLoad:
    def test_incremental_load_populates_fact_trades(self, tmp_path, mocker):
        """An incremental load moves orders from Postgres into FACT_TRADES."""
        duck = _make_duck()
        _patch_extracts(mocker, _orders_df())

        run_full(MagicMock(), duck, "batch-1", str(tmp_path))

        count = duck.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
        assert count == 1

    def test_second_load_with_no_new_data_adds_no_rows(self, tmp_path, mocker):
        """Re-running a load over the same window must not double-count."""
        duck = _make_duck()

        # First run: 1 order.
        _patch_extracts(mocker, _orders_df())
        run_full(MagicMock(), duck, "batch-1", str(tmp_path))

        # Second run: no new orders (watermark now past the only order).
        mocker.patch("etl.pipeline.extract_instruments", return_value=_instruments_df())
        mocker.patch("etl.pipeline.extract_accounts", return_value=_accounts_df())
        mocker.patch("etl.pipeline.extract_orders", return_value=pd.DataFrame())

        run_full(MagicMock(), duck, "batch-2", str(tmp_path))

        count = duck.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
        assert count == 1


class TestTransformHandlesEdgeCases:
    def test_handles_null_price(self, tmp_path, mocker):
        """A row with a null price must be dead-lettered; the load continues."""
        duck = _make_duck()

        bad_order = _orders_df("order-bad")
        bad_order.loc[0, "price"] = None
        good_order = _orders_df("order-good")
        orders = pd.concat([bad_order, good_order], ignore_index=True)

        _patch_extracts(mocker, orders)
        run_full(MagicMock(), duck, "batch-1", str(tmp_path))

        count = duck.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
        assert count == 1

        dlq_files = list(Path(tmp_path).glob("*.jsonl"))
        assert len(dlq_files) == 1
        lines = dlq_files[0].read_text().strip().splitlines()
        assert len(lines) == 1
        entry = json.loads(lines[0])
        assert "price" in entry["reason"]

    def test_handles_null_quantity(self, tmp_path, mocker):
        """A row with null quantity is dead-lettered."""
        duck = _make_duck()
        orders = _orders_df()
        orders.loc[0, "quantity"] = None

        _patch_extracts(mocker, orders)
        run_full(MagicMock(), duck, "batch-1", str(tmp_path))

        count = duck.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
        assert count == 0

        dlq = list(Path(tmp_path).glob("*.jsonl"))
        assert len(dlq) == 1


class TestDeadLettering:
    def test_invalid_row_dead_lettered_and_load_continues(self, tmp_path, mocker):
        """One invalid row is dead-lettered with reason; the valid row is inserted."""
        duck = _make_duck()

        invalid = _orders_df("order-bad")
        invalid.loc[0, "side"] = "HOLD"
        valid = _orders_df("order-good")
        orders = pd.concat([invalid, valid], ignore_index=True)

        _patch_extracts(mocker, orders)
        run_full(MagicMock(), duck, "batch-1", str(tmp_path))

        # Valid row made it in.
        count = duck.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
        assert count == 1

        # Dead-letter file exists and contains the rejection.
        dlq_files = list(Path(tmp_path).glob("*.jsonl"))
        assert len(dlq_files) == 1
        entry = json.loads(dlq_files[0].read_text().strip())
        assert "batch_id" in entry
        assert "reason" in entry
        assert "side" in entry["reason"]

    def test_dead_letter_file_contains_batch_id(self, tmp_path, mocker):
        """The batch_id in the dead-letter entry matches the run's batch_id."""
        duck = _make_duck()
        orders = _orders_df()
        orders.loc[0, "status"] = "UNKNOWN_STATUS"

        _patch_extracts(mocker, orders)
        run_full(MagicMock(), duck, "my-batch-42", str(tmp_path))

        dlq = list(Path(tmp_path).glob("*.jsonl"))
        entry = json.loads(dlq[0].read_text().strip())
        assert entry["batch_id"] == "my-batch-42"
