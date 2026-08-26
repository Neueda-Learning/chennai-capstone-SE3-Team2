from __future__ import annotations
from datetime import datetime
from numbers import Real

import pandas as pd


REQUIRED_FIELDS = {
    "date",
    "open",
    "high",
    "low",
    "close",
    "adjclose",
    "volume",
    "synthetic",
}

NUMERIC_FIELDS = {
    "open",
    "high",
    "low",
    "close",
    "adjclose",
}


def transform(raw_response: dict) -> tuple[pd.DataFrame, pd.DataFrame]:
    """
    Transform a raw Fauxnance candles response.

    Returns:
        clean_df:
            Valid candles ready for analytical loading.

        rejected_df:
            Rejected candles, including the original malformed
            candle and the reason it was rejected.
    """

    data = raw_response["data"]

    symbol = data["symbol"]
    currency = data["currency"]
    interval = data["interval"]
    candles = data.get("candles", [])

    clean_rows = []
    rejected_rows = []

    # Validate each candle independently.
    for candle in candles:
        valid, reason = validate_candle(candle)

        if valid:
            clean_rows.append(candle)
        else:
            rejected_rows.append(
                _create_rejected_row(
                    symbol=symbol,
                    candle=candle,
                    reason=reason,
                )
            )

    # Enforce the analytical grain:
    # one symbol + one trading date = one candle.
    seen_keys = set()
    unique_rows = []

    for candle in clean_rows:
        key = (symbol, candle["date"])

        if key in seen_keys:
            rejected_rows.append(
                _create_rejected_row(
                    symbol=symbol,
                    candle=candle,
                    reason="duplicate date",
                )
            )
            continue

        seen_keys.add(key)
        unique_rows.append(candle)

    clean_df = _build_clean_dataframe(
        unique_rows,
        symbol=symbol,
        currency=currency,
        interval=interval,
    )

    rejected_df = pd.DataFrame(rejected_rows)

    return clean_df, rejected_df


def validate_candle(candle: dict) -> tuple[bool, str | None]:
    """
    Validate a single candle.

    Returns:
        (True, None) if valid.
        (False, reason) if invalid.
    """

    # 1. Required fields must exist.
    for field in REQUIRED_FIELDS:
        if field not in candle:
            return False, f"missing {field}"

    # 2. Date must use ISO YYYY-MM-DD format.
    try:
        datetime.strptime(candle["date"], "%Y-%m-%d")
    except (TypeError, ValueError):
        return False, "invalid date"

    # 3. Price fields must contain numeric values.
    for field in NUMERIC_FIELDS:
        value = candle[field]

        if isinstance(value, bool) or not isinstance(value, Real):
            return False, f"invalid {field}"

    # 4. High cannot be below low.
    if candle["high"] < candle["low"]:
        return False, "high below low"

    # 5. Volume may be NULL, but cannot be negative.
    volume = candle["volume"]

    if volume is not None:
        if isinstance(volume, bool) or not isinstance(volume, Real):
            return False, "invalid volume"

        if volume < 0:
            return False, "negative volume"

    return True, None


def _create_rejected_row(
    symbol: str,
    candle: dict,
    reason: str,
) -> dict:
    """
    Preserve the original malformed candle together with
    the reason it was rejected.
    """

    return {
        "symbol": symbol,
        "date": candle.get("date"),
        "reason": reason,
        "candle": candle,
    }


def _build_clean_dataframe(
    candles: list[dict],
    symbol: str,
    currency: str,
    interval: str,
) -> pd.DataFrame:

    columns = [
        "symbol",
        "currency",
        "interval",
        "date",
        "open",
        "high",
        "low",
        "close",
        "adjclose",
        "volume",
        "synthetic",
        "daily_return",
        "daily_range",
        "daily_range_pct",
        "turnover",
    ]

    if not candles:
        return pd.DataFrame(columns=columns)

    df = pd.DataFrame(candles)

    df.insert(0, "symbol", symbol)
    df.insert(1, "currency", currency)
    df.insert(2, "interval", interval)

    # Convert the validated ISO date to datetime.
    df["date"] = pd.to_datetime(
        df["date"],
        format="%Y-%m-%d",
    )

    numeric_columns = [
        "open",
        "high",
        "low",
        "close",
        "adjclose",
        "volume",
    ]

    for column in numeric_columns:
        df[column] = pd.to_numeric(
            df[column],
            errors="raise",
        )

    # Sort before calculating previous-close based metrics.
    df = df.sort_values("date").reset_index(drop=True)

    # Percentage change from the previous valid trading day's close.
    df["daily_return"] = df["close"].pct_change()

    # Intraday high-low movement.
    df["daily_range"] = df["high"] - df["low"]

    # Intraday movement relative to opening price.
    df["daily_range_pct"] = (
        df["daily_range"] / df["open"]
    )

    # Trading value proxy.
    # If volume is NULL, turnover remains NULL.
    df["turnover"] = df["close"] * df["volume"]

    return df[columns]

