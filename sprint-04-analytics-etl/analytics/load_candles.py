import json
from pathlib import Path
import pandas as pd
from fetch_candles import fetch_and_cache

CANDLE_DIR = Path("candle-data")


def load_symbol(symbol: str, force_refresh: bool = False) -> pd.DataFrame:
    """
    Load one symbol's candle data into a clean DataFrame.
    Fetches from the API and caches locally if not already present
    (or if force_refresh=True); otherwise reads the cached file.
    This is the only function eda.py and visualize_claims.py call,
    so nothing downstream needs to change.
    """
    path = fetch_and_cache(symbol, force=force_refresh)
    raw = json.loads(path.read_text())
    candles = raw["data"]["candles"]
    df = pd.DataFrame(candles)

    df["date"] = pd.to_datetime(df["date"])
    df["symbol"] = raw["data"]["symbol"]
    df["currency"] = raw["data"]["currency"]

    n_synthetic = df["synthetic"].sum()
    if n_synthetic:
        print(f"[{symbol}] {n_synthetic} synthetic candle(s) found (volume unreliable) — excluding from volume claims")

    return df.sort_values("date").reset_index(drop=True)