from __future__ import annotations

import logging
from datetime import datetime, timezone

import pandas as pd

logger = logging.getLogger(__name__)

_COUNTRY_CURRENCY: dict[str, str] = {"IN": "INR", "US": "USD"}
_VALID_SIDES = {"BUY", "SELL"}
_VALID_STATUSES = {"NEW", "FILLED", "REJECTED", "CANCELLED"}


def transform_instruments(df: pd.DataFrame) -> pd.DataFrame:
    """Add derived currency and loaded_at columns."""
    df = df.copy()
    df["currency"] = df["country"].map(_COUNTRY_CURRENCY)
    # MF instruments have no exchange and thus no country row: default INR.
    df.loc[df["exchange"].isna(), "currency"] = "INR"
    df["currency"] = df["currency"].fillna("USD")
    df["loaded_at"] = datetime.now(timezone.utc)
    return df


def transform_accounts(df: pd.DataFrame) -> pd.DataFrame:
    """Normalise timezone-aware timestamps."""
    df = df.copy()
    df["created_at"] = pd.to_datetime(df["created_at"], utc=True)
    df["updated_at"] = pd.to_datetime(df["updated_at"], utc=True)
    df["loaded_at"] = datetime.now(timezone.utc)
    return df


def enrich_orders(
    orders_df: pd.DataFrame,
    instruments_df: pd.DataFrame,
    accounts_df: pd.DataFrame,
) -> pd.DataFrame:
    """
    Resolve instrument_id → symbol and client_id → account_id so that
    the validation step can check referential integrity against the
    dimension tables without an extra Postgres round-trip.
    """
    id_to_symbol = instruments_df.set_index("instrument_id")["symbol"].to_dict()
    id_to_account = accounts_df.set_index("client_id")["account_id"].to_dict()

    df = orders_df.copy()
    df["symbol"] = df["instrument_id"].map(id_to_symbol)
    df["account_ref"] = df["client_id"].map(id_to_account)
    df["created_at"] = pd.to_datetime(df["created_at"], utc=True)
    return df


def validate_orders(
    orders_df: pd.DataFrame,
    valid_symbols: set[str],
    valid_account_ids: set[str],
    valid_date_keys: set[int],
    batch_id: str,
) -> tuple[pd.DataFrame, list[dict]]:
    """
    Run every quality check the contract names before a row reaches
    FACT_TRADES.  Returns (clean_df, rejected_rows).  A failing row is
    dead-lettered with its reason and batch_id; the load continues.
    """
    clean: list[dict] = []
    rejected: list[dict] = []

    for _, row in orders_df.iterrows():
        reason = _check_row(row, valid_symbols, valid_account_ids, valid_date_keys)
        if reason is None:
            clean.append(_enrich_with_trade_value(row))
        else:
            rejected.append({
                "batch_id": batch_id,
                "reason": reason,
                "row": row.to_dict(),
            })

    clean_df = (
        pd.DataFrame(clean)
        if clean
        else pd.DataFrame(columns=list(orders_df.columns) + ["trade_value", "loaded_at"])
    )
    logger.info(
        "validate_orders clean=%d rejected=%d", len(clean), len(rejected)
    )
    return clean_df, rejected


def _check_row(
    row: pd.Series,
    valid_symbols: set[str],
    valid_account_ids: set[str],
    valid_date_keys: set[int],
) -> str | None:
    """Return the first failing reason, or None if the row is valid."""
    qty = row.get("quantity")
    if qty is None or pd.isna(qty):
        return "quantity is null"
    try:
        if float(qty) <= 0:
            return "quantity <= 0"
    except (TypeError, ValueError):
        return f"quantity not numeric: {qty!r}"

    price = row.get("price")
    if price is None or pd.isna(price):
        return "price is null"
    try:
        if float(price) <= 0:
            return "price <= 0"
    except (TypeError, ValueError):
        return f"price not numeric: {price!r}"

    side = row.get("side")
    if side not in _VALID_SIDES:
        return f"invalid side: {side!r}"

    status = row.get("status")
    if status not in _VALID_STATUSES:
        return f"invalid status: {status!r}"

    symbol = row.get("symbol")
    if symbol is None or pd.isna(symbol):
        return f"instrument_id {row.get('instrument_id')!r} has no symbol"
    if symbol not in valid_symbols:
        return f"symbol {symbol!r} not in dim_instrument"

    account_ref = row.get("account_ref")
    if account_ref is None or pd.isna(account_ref):
        return f"client_id {row.get('client_id')!r} has no account_ref"
    if account_ref not in valid_account_ids:
        return f"account_id {account_ref!r} not in dim_account"

    created_at = row.get("created_at")
    if created_at is None or pd.isna(created_at):
        return "created_at is null"
    try:
        date_key = int(pd.to_datetime(created_at).strftime("%Y%m%d"))
    except Exception:
        return f"created_at not parseable: {created_at!r}"
    if date_key not in valid_date_keys:
        return f"date_key {date_key} not in dim_date"

    return None


def _enrich_with_trade_value(row: pd.Series) -> dict:
    """Compute trade_value and add loaded_at.  Never trusts a stored value."""
    d = row.to_dict()
    qty = float(d["quantity"])
    price = float(d["price"])
    executed = d.get("executed_price")

    if d["status"] == "FILLED" and executed is not None and not pd.isna(executed):
        trade_value = round(qty * float(executed), 2)
    else:
        trade_value = round(qty * price, 2)

    d["trade_value"] = trade_value
    d["loaded_at"] = datetime.now(timezone.utc)
    return d
