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
        "ohlc": df[["date", "open", "high", "low", "close", "synthetic"]].reset_index(drop=True),
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


def print_dashboard(all_results: list):
    """Print EDA results (All Available window per symbol) in a readable console format."""
    print("\n" + "=" * 75)
    print("                         EDA DASHBOARD")
    print("=" * 75)

    for result in all_results:
        symbol = result["symbol"]
        all_label = next(k for k in result["windows"] if k.startswith("All Available"))
        m = result["windows"][all_label]

        print("\n" + "-" * 75)
        print(f"  {symbol}")
        print("-" * 75)
        print(f"  Date range          : {m['start']} → {m['end']}")
        print(f"  Trading days        : {m['n_days']}")
        print(f"  Synthetic candles   : {m['n_synthetic']}")
        print(f"  Average close       : ₹{m['avg_close']:,.2f}")
        print(f"  Volatility          : {m['volatility_pct']:.2f}%" if m['volatility_pct'] is not None else "  Volatility          : N/A")
        vol = m["avg_daily_volume"]
        print(f"  Avg daily volume    : {vol:,.0f} shares" if vol is not None else "  Avg daily volume    : N/A")
        print(f"  Avg true range      : ₹{m['avg_true_range']:.2f}")
        print(f"  Biggest move date   : {m['biggest_move_date']}")
        print(f"  Biggest move        : {m['biggest_move_pct']:.2f}%" if m['biggest_move_pct'] is not None else "  Biggest move        : N/A")

        other_windows = [k for k in result["windows"] if k != all_label]
        if other_windows:
            print(f"  Also available      : {', '.join(other_windows)}")

    print("\n" + "=" * 75)
    print("                         END OF EDA")
    print("=" * 75 + "\n")


if __name__ == "__main__":
    import sys
    symbols = sys.argv[1:] or ["INFY.NS", "RELIANCE.NS", "TATASTEEL.BO"]
    results = [analyze_windows(sym) for sym in symbols]
    print_dashboard(results)