# eda.py
import pandas as pd
from load_candles import load_symbol


def analyze(symbol: str) -> dict:
    """Run standard EDA on any symbol's candle data. Returns metrics you can turn into claims."""
    df = load_symbol(symbol)
    real = df[~df["synthetic"]].copy()   # real trades only, for volume-based metrics

    df["daily_return"] = df["close"].pct_change()
    df["true_range"] = df["high"] - df["low"]

    return {
        "symbol": symbol,
        "date_range": (df["date"].min().date(), df["date"].max().date()),
        "n_days": len(df),
        "n_synthetic": int(df["synthetic"].sum()),
        "avg_close": round(df["close"].mean(), 2),
        "volatility_pct": round(df["daily_return"].std() * 100, 2),
        "avg_daily_volume": round(real["volume"].mean(), 0) if len(real) else None,
        "avg_true_range": round(df["true_range"].mean(), 2),
        "biggest_move_date": df.loc[df["daily_return"].abs().idxmax(), "date"].date()
                              if df["daily_return"].notna().any() else None,
        "biggest_move_pct": round(df["daily_return"].abs().max() * 100, 2)
                             if df["daily_return"].notna().any() else None,
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
        if volume is not None:
            print(f"  Avg daily volume    : {volume:,.0f} shares")
        else:
            print(f"  Avg daily volume    : N/A")

        print(f"  Avg true range      : ₹{result['avg_true_range']:.2f}")
        print(f"  Biggest move date   : {result['biggest_move_date']}")
        print(f"  Biggest move        : {result['biggest_move_pct']:.2f}%")

    print("\n" + "=" * 75)
    print("                         END OF EDA")
    print("=" * 75 + "\n")


if __name__ == "__main__":
    symbols = ["INFY.NS", "RELIANCE.NS", "TATASTEEL.BO"]

    results = [analyze(symbol) for symbol in symbols]

    print_dashboard(results)