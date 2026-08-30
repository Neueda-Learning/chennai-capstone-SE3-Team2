# windowed_eda.py
from datetime import timedelta
import pandas as pd
from load_candles import load_symbol

# (window length in days, display label)
WINDOW_CANDIDATES = [(7, "1 Week"), (30, "1 Month"), (365, "1 Year")]

# Skip a window if the data's full span isn't at least this many times longer
# than the window -- otherwise a symbol with 29 days of history would get a
# "1 Month" section that's really just "all the data" wearing a fake label.
DUPLICATE_BUFFER = 1.2


def _metrics_for_slice(df: pd.DataFrame) -> dict:
    """Compute the standard EDA metrics for one slice of a symbol's data."""
    real = df[~df["synthetic"]]
    daily_return = df["close"].pct_change()
    true_range = df["high"] - df["low"]
    has_return = daily_return.notna().any()

    return {
        "n_days": len(df),
        "n_synthetic": int(df["synthetic"].sum()),
        "start": df["date"].min().date(),
        "end": df["date"].max().date(),
        "avg_close": round(df["close"].mean(), 2),
        "volatility_pct": round(daily_return.std() * 100, 2) if has_return else None,
        "avg_daily_volume": round(real["volume"].mean(), 0) if len(real) else None,
        "avg_true_range": round(true_range.mean(), 2),
        "biggest_move_date": df.loc[daily_return.abs().idxmax(), "date"].date() if has_return else None,
        "biggest_move_pct": round(daily_return.abs().max() * 100, 2) if has_return else None,
        "prices": df[["date", "close"]].reset_index(drop=True),
        "volumes": real[["date", "volume"]].reset_index(drop=True),
    }


def analyze_windows(symbol: str) -> dict:
    """
    Returns EDA metrics for every time window that's meaningfully available
    for this symbol: 1 Week / 1 Month / 1 Year (whichever actually fit inside
    the data), plus an "All Available" window covering everything.
    """
    df = load_symbol(symbol)
    df = df.sort_values("date").reset_index(drop=True)
    max_date = df["date"].max()
    full_span_days = (max_date - df["date"].min()).days

    windows = {}
    for period_days, label in WINDOW_CANDIDATES:
        if full_span_days > period_days * DUPLICATE_BUFFER:
            cutoff = max_date - timedelta(days=period_days)
            windows[label] = _metrics_for_slice(df[df["date"] >= cutoff])

    all_label = f"All Available ({full_span_days + 1} days)"
    windows[all_label] = _metrics_for_slice(df)

    return {"symbol": symbol, "windows": windows}


if __name__ == "__main__":
    import sys
    symbols = sys.argv[1:] or ["INFY.NS", "RELIANCE.NS", "TATASTEEL.BO"]
    for sym in symbols:
        result = analyze_windows(sym)
        print(f"\n{sym}: {list(result['windows'].keys())}")
        for label, m in result["windows"].items():
            print(f"  {label}: {m['start']} to {m['end']}, vol={m['volatility_pct']}%")