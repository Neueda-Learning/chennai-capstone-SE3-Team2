# eda.py
# Thin wrapper around windowed_eda's "All Available" window, so the same
# metric calculation isn't duplicated in two places.
from windowed_eda import analyze_windows


def analyze(symbol: str) -> dict:
    """Run standard EDA on any symbol's full available candle history."""
    result = analyze_windows(symbol)
    all_label = next(k for k in result["windows"] if k.startswith("All Available"))
    m = result["windows"][all_label]
    return {
        "symbol": symbol,
        "date_range": (m["start"], m["end"]),
        "n_days": m["n_days"],
        "n_synthetic": m["n_synthetic"],
        "avg_close": m["avg_close"],
        "volatility_pct": m["volatility_pct"],
        "avg_daily_volume": m["avg_daily_volume"],
        "avg_true_range": m["avg_true_range"],
        "biggest_move_date": m["biggest_move_date"],
        "biggest_move_pct": m["biggest_move_pct"],
    }


def print_dashboard(results):
    """Print EDA results in a reusable dashboard format."""
    print("\n" + "=" * 75)
    print("                         EDA DASHBOARD")
    print("=" * 75)

    for result in results:
        print("\n" + "-" * 75)
        print(f"  {result['symbol']}")
        print("-" * 75)

        start, end = result["date_range"]
        print(f"  Date range          : {start} → {end}")
        print(f"  Trading days        : {result['n_days']}")
        print(f"  Synthetic candles   : {result['n_synthetic']}")
        print(f"  Average close       : ₹{result['avg_close']:,.2f}")
        print(f"  Volatility          : {result['volatility_pct']:.2f}%")

        volume = result["avg_daily_volume"]
        print(f"  Avg daily volume    : {volume:,.0f} shares" if volume is not None else "  Avg daily volume    : N/A")
        print(f"  Avg true range      : ₹{result['avg_true_range']:.2f}")
        print(f"  Biggest move date   : {result['biggest_move_date']}")
        print(f"  Biggest move        : {result['biggest_move_pct']:.2f}%")

    print("\n" + "=" * 75)
    print("                         END OF EDA")
    print("=" * 75 + "\n")


if __name__ == "__main__":
    import sys
    symbols = sys.argv[1:] or ["INFY.NS", "RELIANCE.NS", "TATASTEEL.BO"]
    results = [analyze(symbol) for symbol in symbols]
    print_dashboard(results)