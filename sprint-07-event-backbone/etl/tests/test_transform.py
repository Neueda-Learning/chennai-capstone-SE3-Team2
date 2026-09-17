from __future__ import annotations

import pandas as pd
import pytest

from etl.transform import (
    _check_row,
    _enrich_with_trade_value,
    enrich_orders,
    transform_instruments,
    validate_orders,
)


class TestTransformInstruments:
    def test_indian_equity_gets_inr(self):
        df = pd.DataFrame([{
            "instrument_id": 1, "symbol": "INFY.NS", "name": "Infosys",
            "asset_class": "STOCK", "exchange": "NSE", "country": "IN",
            "tradable": True,
        }])
        result = transform_instruments(df)
        assert result.iloc[0]["currency"] == "INR"

    def test_mf_with_no_exchange_gets_inr(self):
        df = pd.DataFrame([{
            "instrument_id": 2, "symbol": "INF174KA1DQ2", "name": "Axis Fund",
            "asset_class": "MF", "exchange": None, "country": None,
            "tradable": True,
        }])
        result = transform_instruments(df)
        assert result.iloc[0]["currency"] == "INR"

    def test_loaded_at_is_set(self):
        df = pd.DataFrame([{
            "instrument_id": 1, "symbol": "INFY.NS", "name": "Infosys",
            "asset_class": "STOCK", "exchange": "NSE", "country": "IN",
            "tradable": True,
        }])
        result = transform_instruments(df)
        assert result.iloc[0]["loaded_at"] is not None


class TestCheckRow:
    def _make_row(self, **overrides) -> pd.Series:
        base = {
            "quantity": 100.0,
            "price": 1500.0,
            "side": "BUY",
            "status": "FILLED",
            "symbol": "INFY.NS",
            "account_ref": "ACC-000001",
            "created_at": pd.Timestamp("2026-09-01", tz="UTC"),
            "instrument_id": 1,
            "client_id": 1,
        }
        base.update(overrides)
        return pd.Series(base)

    def test_valid_row_passes(self):
        row = self._make_row()
        reason = _check_row(
            row,
            valid_symbols={"INFY.NS"},
            valid_account_ids={"ACC-000001"},
            valid_date_keys={20260901},
        )
        assert reason is None

    def test_null_quantity_rejected(self):
        row = self._make_row(quantity=None)
        reason = _check_row(row, {"INFY.NS"}, {"ACC-000001"}, {20260901})
        assert reason is not None and "quantity" in reason

    def test_zero_quantity_rejected(self):
        row = self._make_row(quantity=0.0)
        reason = _check_row(row, {"INFY.NS"}, {"ACC-000001"}, {20260901})
        assert reason is not None and "quantity" in reason

    def test_null_price_rejected(self):
        row = self._make_row(price=None)
        reason = _check_row(row, {"INFY.NS"}, {"ACC-000001"}, {20260901})
        assert reason is not None and "price" in reason

    def test_invalid_side_rejected(self):
        row = self._make_row(side="HOLD")
        reason = _check_row(row, {"INFY.NS"}, {"ACC-000001"}, {20260901})
        assert reason is not None and "side" in reason

    def test_invalid_status_rejected(self):
        row = self._make_row(status="PARTIAL")
        reason = _check_row(row, {"INFY.NS"}, {"ACC-000001"}, {20260901})
        assert reason is not None and "status" in reason

    def test_unknown_symbol_rejected(self):
        row = self._make_row(symbol="UNKNOWN")
        reason = _check_row(row, {"INFY.NS"}, {"ACC-000001"}, {20260901})
        assert reason is not None and "dim_instrument" in reason

    def test_unknown_account_rejected(self):
        row = self._make_row(account_ref="ACC-999999")
        reason = _check_row(row, {"INFY.NS"}, {"ACC-000001"}, {20260901})
        assert reason is not None and "dim_account" in reason

    def test_date_not_in_dim_date_rejected(self):
        row = self._make_row()
        reason = _check_row(row, {"INFY.NS"}, {"ACC-000001"}, set())
        assert reason is not None and "dim_date" in reason


class TestEnrichWithTradeValue:
    def test_filled_uses_executed_price(self):
        row = pd.Series({
            "quantity": 100.0, "price": 1500.0,
            "executed_price": 1495.0, "status": "FILLED",
        })
        d = _enrich_with_trade_value(row)
        assert d["trade_value"] == round(100.0 * 1495.0, 2)

    def test_rejected_uses_limit_price(self):
        row = pd.Series({
            "quantity": 50.0, "price": 1500.0,
            "executed_price": None, "status": "REJECTED",
        })
        d = _enrich_with_trade_value(row)
        assert d["trade_value"] == round(50.0 * 1500.0, 2)

    def test_fractional_quantity_preserved(self):
        row = pd.Series({
            "quantity": 152.386, "price": 100.0,
            "executed_price": None, "status": "NEW",
        })
        d = _enrich_with_trade_value(row)
        assert d["trade_value"] == round(152.386 * 100.0, 2)


class TestValidateOrders:
    def _make_enriched_df(self, **overrides) -> pd.DataFrame:
        base = {
            "source_order_id": "order-1",
            "client_id": 1, "instrument_id": 1,
            "side": "BUY", "quantity": 100.0, "price": 1500.0,
            "executed_price": 1495.0, "status": "FILLED",
            "created_at": pd.Timestamp("2026-09-01", tz="UTC"),
            "symbol": "INFY.NS", "account_ref": "ACC-000001",
        }
        base.update(overrides)
        return pd.DataFrame([base])

    def test_valid_row_goes_to_clean(self):
        df = self._make_enriched_df()
        clean, rejected = validate_orders(
            df, {"INFY.NS"}, {"ACC-000001"}, {20260901}, "batch-1"
        )
        assert len(clean) == 1
        assert len(rejected) == 0

    def test_invalid_row_goes_to_rejected_with_reason(self):
        df = self._make_enriched_df(quantity=0.0)
        clean, rejected = validate_orders(
            df, {"INFY.NS"}, {"ACC-000001"}, {20260901}, "batch-1"
        )
        assert len(clean) == 0
        assert len(rejected) == 1
        assert "reason" in rejected[0]
        assert "batch_id" in rejected[0]

    def test_load_continues_after_bad_row(self):
        bad = self._make_enriched_df(quantity=0.0)
        good = self._make_enriched_df(source_order_id="order-2", quantity=50.0)
        df = pd.concat([bad, good], ignore_index=True)
        clean, rejected = validate_orders(
            df, {"INFY.NS"}, {"ACC-000001"}, {20260901}, "batch-1"
        )
        assert len(clean) == 1
        assert len(rejected) == 1
