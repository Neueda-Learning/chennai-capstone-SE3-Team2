from __future__ import annotations

from datetime import date, datetime, timezone

import pandas as pd
import pytest

from etl.load import (
    _build_date_rows,
    load_dim_account,
    load_dim_date,
    load_dim_instrument,
    load_fact_trades,
)


class TestLoadDimDate:
    def test_populates_range(self, duck):
        load_dim_date(duck, date(2026, 9, 1), date(2026, 9, 3))
        count = duck.execute("SELECT COUNT(*) FROM dim_date").fetchone()[0]
        assert count == 3

    def test_idempotent(self, duck):
        load_dim_date(duck, date(2026, 9, 1), date(2026, 9, 3))
        load_dim_date(duck, date(2026, 9, 1), date(2026, 9, 3))
        count = duck.execute("SELECT COUNT(*) FROM dim_date").fetchone()[0]
        assert count == 3

    def test_date_key_is_yyyymmdd(self, duck):
        load_dim_date(duck, date(2026, 9, 15), date(2026, 9, 15))
        key = duck.execute("SELECT date_key FROM dim_date").fetchone()[0]
        assert key == 20260915

    def test_weekday_flag(self, duck):
        # 2026-09-15 is a Tuesday
        load_dim_date(duck, date(2026, 9, 15), date(2026, 9, 15))
        is_weekday = duck.execute("SELECT is_weekday FROM dim_date").fetchone()[0]
        assert is_weekday is True

    def test_weekend_flag(self, duck):
        # 2026-09-13 is a Sunday
        load_dim_date(duck, date(2026, 9, 13), date(2026, 9, 13))
        is_weekday = duck.execute("SELECT is_weekday FROM dim_date").fetchone()[0]
        assert is_weekday is False


class TestLoadDimInstrument:
    def _instruments_df(self) -> pd.DataFrame:
        return pd.DataFrame([{
            "instrument_id": 1,
            "symbol": "INFY.NS",
            "name": "Infosys Limited",
            "asset_class": "STOCK",
            "currency": "INR",
            "exchange": "NSE",
            "tradable": True,
            "loaded_at": datetime.now(timezone.utc),
        }])

    def test_inserts_new_instrument(self, duck):
        load_dim_instrument(duck, self._instruments_df())
        count = duck.execute("SELECT COUNT(*) FROM dim_instrument").fetchone()[0]
        assert count == 1

    def test_assigns_surrogate_key(self, duck):
        load_dim_instrument(duck, self._instruments_df())
        key = duck.execute(
            "SELECT instrument_key FROM dim_instrument WHERE symbol = 'INFY.NS'"
        ).fetchone()[0]
        assert key == 1

    def test_type1_updates_name_in_place(self, duck):
        load_dim_instrument(duck, self._instruments_df())
        updated = self._instruments_df().copy()
        updated.loc[0, "name"] = "Infosys Ltd (Updated)"
        load_dim_instrument(duck, updated)

        count = duck.execute("SELECT COUNT(*) FROM dim_instrument").fetchone()[0]
        assert count == 1

        name = duck.execute(
            "SELECT name FROM dim_instrument WHERE symbol = 'INFY.NS'"
        ).fetchone()[0]
        assert name == "Infosys Ltd (Updated)"

    def test_returns_symbol_key_map(self, duck):
        result = load_dim_instrument(duck, self._instruments_df())
        assert "INFY.NS" in result
        assert isinstance(result["INFY.NS"], int)


class TestLoadDimAccount:
    def _accounts_df(self, status: str = "ACTIVE") -> pd.DataFrame:
        return pd.DataFrame([{
            "client_id": 1,
            "account_id": "ACC-000001",
            "holder_name": "Alice Kumar",
            "status": status,
            "created_at": pd.Timestamp("2026-01-01", tz="UTC"),
            "updated_at": pd.Timestamp("2026-06-01", tz="UTC"),
            "loaded_at": datetime.now(timezone.utc),
        }])

    def test_inserts_new_account(self, duck):
        load_dim_account(duck, self._accounts_df())
        count = duck.execute("SELECT COUNT(*) FROM dim_account").fetchone()[0]
        assert count == 1

    def test_new_account_is_current(self, duck):
        load_dim_account(duck, self._accounts_df())
        is_current = duck.execute(
            "SELECT is_current FROM dim_account WHERE account_id = 'ACC-000001'"
        ).fetchone()[0]
        assert is_current is True

    def test_unchanged_status_is_no_op(self, duck):
        load_dim_account(duck, self._accounts_df())
        load_dim_account(duck, self._accounts_df())
        count = duck.execute("SELECT COUNT(*) FROM dim_account").fetchone()[0]
        assert count == 1

    def test_scd_type2_on_status_change(self, duck):
        load_dim_account(duck, self._accounts_df("ACTIVE"))
        load_dim_account(duck, self._accounts_df("SUSPENDED"))

        count = duck.execute("SELECT COUNT(*) FROM dim_account").fetchone()[0]
        assert count == 2

        current = duck.execute(
            "SELECT status FROM dim_account WHERE is_current = TRUE"
        ).fetchone()[0]
        assert current == "SUSPENDED"

        old_end = duck.execute(
            "SELECT end_date FROM dim_account WHERE is_current = FALSE"
        ).fetchone()[0]
        assert old_end is not None

    def test_returns_account_key_map(self, duck):
        result = load_dim_account(duck, self._accounts_df())
        assert "ACC-000001" in result


class TestLoadFactTrades:
    def _seed_dims(self, duck) -> None:
        load_dim_date(duck, date(2026, 9, 1), date(2026, 9, 1))
        load_dim_instrument(duck, pd.DataFrame([{
            "instrument_id": 1, "symbol": "INFY.NS", "name": "Infosys",
            "asset_class": "STOCK", "currency": "INR", "exchange": "NSE",
            "tradable": True, "loaded_at": datetime.now(timezone.utc),
        }]))
        load_dim_account(duck, pd.DataFrame([{
            "client_id": 1, "account_id": "ACC-000001",
            "holder_name": "Alice Kumar", "status": "ACTIVE",
            "created_at": pd.Timestamp("2026-01-01", tz="UTC"),
            "updated_at": pd.Timestamp("2026-01-01", tz="UTC"),
            "loaded_at": datetime.now(timezone.utc),
        }]))

    def _orders_df(self, order_id: str = "order-1") -> pd.DataFrame:
        return pd.DataFrame([{
            "source_order_id": order_id,
            "client_id": 1, "instrument_id": 1,
            "side": "BUY", "quantity": 100.0, "price": 1500.0,
            "executed_price": 1495.0, "status": "FILLED",
            "created_at": pd.Timestamp("2026-09-01 09:00:00", tz="UTC"),
            "symbol": "INFY.NS", "account_ref": "ACC-000001",
            "trade_value": 149500.0,
            "loaded_at": datetime.now(timezone.utc),
        }])

    def test_inserts_row(self, duck):
        self._seed_dims(duck)
        n, rejected = load_fact_trades(duck, self._orders_df(), "b1")
        assert n == 1
        assert rejected == []

    def test_idempotent_on_source_order_id(self, duck):
        self._seed_dims(duck)
        load_fact_trades(duck, self._orders_df(), "b1")
        n, _ = load_fact_trades(duck, self._orders_df(), "b2")
        assert n == 0
        count = duck.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
        assert count == 1

    def test_unknown_symbol_is_rejected(self, duck):
        self._seed_dims(duck)
        df = self._orders_df()
        df.loc[0, "symbol"] = "UNKNOWN"
        n, rejected = load_fact_trades(duck, df, "b1")
        assert n == 0
        assert len(rejected) == 1
        assert "dim_instrument" in rejected[0]["reason"]

    def test_unknown_account_is_rejected(self, duck):
        self._seed_dims(duck)
        df = self._orders_df()
        df.loc[0, "account_ref"] = "ACC-999999"
        n, rejected = load_fact_trades(duck, df, "b1")
        assert n == 0
        assert len(rejected) == 1
        assert "dim_account" in rejected[0]["reason"]

    def test_null_executed_price_allowed_for_rejected_order(self, duck):
        self._seed_dims(duck)
        df = self._orders_df()
        df.loc[0, "executed_price"] = None
        df.loc[0, "status"] = "REJECTED"
        n, rejected = load_fact_trades(duck, df, "b1")
        assert n == 1
        assert rejected == []
