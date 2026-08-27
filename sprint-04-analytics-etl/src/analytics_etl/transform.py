from __future__ import annotations

import logging
from datetime import datetime
from numbers import Real

import pandas as pd

try:
    from pipeline_exceptions import MalformedEnvelopeError
except ImportError:
    from pipeline_exceptions import MalformedEnvelopeError

logger = logging.getLogger(__name__)

# Tuple, not a set: a set iterates in arbitrary order, so a candle missing
# several fields would report a different reason on different runs.
REQUIRED_FIELDS = (
    "date",
    "open",
    "high",
    "low",
    "close",
    "adjclose",
    "volume",
    "synthetic",
)

NUMERIC_FIELDS = ("open", "high", "low", "close", "adjclose")

CLEAN_COLUMNS = [
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
    "turnover_proxy",
]

REJECTED_COLUMNS = ["symbol", "date", "reason", "candle"]


def transform(raw_response: dict) -> tuple[pd.DataFrame, pd.DataFrame]:
    """
    Transform a raw Fauxnance candles response.

    Returns:
        clean_df:    valid candles ready for analytical loading
        rejected_df: rejected candles with the reason and original payload
    """
    symbol, currency, interval, candles = _read_envelope(raw_response)

    clean_rows = []
    rejected_rows = []

    # Validate each candle independently.
    for candle in candles:
        if not isinstance(candle, dict):
            rejected_rows.append(
                _create_rejected_row(
                    symbol=symbol,
                    candle={"raw": repr(candle)[:200]},
                    reason=f"candle is {type(candle).__name__}, expected object",
                )
            )
            continue

        valid, reason = validate_candle(candle)

        if valid:
            clean_rows.append(candle)
        else:
            rejected_rows.append(
                _create_rejected_row(
                    symbol=symbol, candle=candle, reason=reason
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
                    symbol=symbol, candle=candle, reason="duplicate date"
                )
            )
            continue

        seen_keys.add(key)
        unique_rows.append(candle)

    try:
        clean_df = _build_clean_dataframe(
            unique_rows,
            symbol=symbol,
            currency=currency,
            interval=interval,
        )
    except (ValueError, TypeError, KeyError) as exc:
        logger.error(
            "transform_failed symbol=%s mode=malformed_envelope error=%s",
            symbol,
            exc,
        )
        raise MalformedEnvelopeError(
            f"could not build dataframe for {symbol}: {exc}", symbol=symbol
        ) from exc

    # Columns are set explicitly so an empty frame is still filterable.
    rejected_df = pd.DataFrame(rejected_rows, columns=REJECTED_COLUMNS)

    logger.info(
        "transform_done symbol=%s candles_in=%s clean=%s rejected=%s",
        symbol,
        len(candles),
        len(clean_df),
        len(rejected_df),
    )
    if not rejected_df.empty:
        logger.warning(
            "transform_rejections symbol=%s reasons=%s",
            symbol,
            rejected_df["reason"].value_counts().to_dict(),
        )

    return clean_df, rejected_df


def _read_envelope(raw_response) -> tuple[str, str, str, list]:
    """Pull symbol, currency, interval and candles out of the response."""
    if not isinstance(raw_response, dict):
        raise MalformedEnvelopeError(
            f"response is {type(raw_response).__name__}, expected object"
        )

    data = raw_response.get("data")
    if not isinstance(data, dict):
        raise MalformedEnvelopeError("response has no 'data' object")

    try:
        symbol = data["symbol"]
        currency = data["currency"]
        interval = data["interval"]
    except KeyError as exc:
        raise MalformedEnvelopeError(
            f"response 'data' is missing {exc}"
        ) from exc

    candles = data.get("candles", [])
    if candles is None:
        candles = []
    if not isinstance(candles, list):
        raise MalformedEnvelopeError(
            f"'data.candles' is {type(candles).__name__}, expected array",
            symbol=str(symbol),
        )

    return symbol, currency, interval, candles


def validate_candle(candle: dict) -> tuple[bool, str | None]:
    """
    Validate a single candle.

    Returns (True, None) if valid, (False, reason) if invalid.
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

    # 4. OHLC consistency.
    high = candle["high"]
    low = candle["low"]
    open_price = candle["open"]
    close = candle["close"]

    if high < low:
        return False, "high below low"

    if not (low <= open_price <= high):
        return False, "open outside high-low range"

    if not (low <= close <= high):
        return False, "close outside high-low range"

    # 5. Volume may be NULL, but cannot be negative.
    volume = candle["volume"]

    if volume is not None:
        if isinstance(volume, bool) or not isinstance(volume, Real):
            return False, "invalid volume"

        if volume < 0:
            return False, "negative volume"

    # 6. Synthetic must be boolean.
    if not isinstance(candle["synthetic"], bool):
        return False, "invalid synthetic"

    return True, None


def _create_rejected_row(symbol: str, candle: dict, reason: str) -> dict:
    """Preserve the original malformed candle and the rejection reason."""
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
    if not candles:
        return pd.DataFrame(columns=CLEAN_COLUMNS)

    df = pd.DataFrame(candles)

    # Drop envelope names a candle may already carry, so insert cannot clash.
    df = df.drop(
        columns=[c for c in ("symbol", "currency", "interval") if c in df],
        errors="ignore",
    )

    df.insert(0, "symbol", symbol)
    df.insert(1, "currency", currency)
    df.insert(2, "interval", interval)

    # Convert the validated ISO date to datetime.
    df["date"] = pd.to_datetime(df["date"], format="%Y-%m-%d")

    numeric_columns = [
        "open",
        "high",
        "low",
        "close",
        "adjclose",
        "volume",
    ]

    for column in numeric_columns:
        df[column] = pd.to_numeric(df[column], errors="raise")

    # Sort before calculating previous-close based metrics.
    df = df.sort_values("date").reset_index(drop=True)

    # Percentage change from the previous valid trading day's close.
    df["daily_return"] = df["close"].pct_change()

    # Intraday high-low movement.
    df["daily_range"] = df["high"] - df["low"]

    # Intraday movement relative to opening price.
    df["daily_range_pct"] = df["daily_range"] / df["open"]

    # Trading value proxy. If volume is NULL, turnover_proxy remains NULL.
    df["turnover_proxy"] = df["close"] * df["volume"]

    return df[CLEAN_COLUMNS]