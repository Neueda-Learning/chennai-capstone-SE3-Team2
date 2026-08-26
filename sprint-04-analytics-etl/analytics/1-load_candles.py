# load_candles.py
import json
from pathlib import Path
import pandas as pd

CANDLE_DIR = Path("candle-data")

def load_symbol(symbol: str) -> pd.DataFrame:
    """Load one symbol's raw candle JSON into a clean DataFrame."""
    path = CANDLE_DIR / f"{symbol}-candle.json"
    raw = json.loads(path.read_text())
    candles = raw["data"]["candles"]
    df = pd.DataFrame(candles)

    df["date"] = pd.to_datetime(df["date"])
    df["symbol"] = raw["data"]["symbol"]
    df["currency"] = raw["data"]["currency"]

    # Data quality: flag synthetic/interpolated candles instead of silently using them
    n_synthetic = df["synthetic"].sum()
    if n_synthetic:
        print(f"[{symbol}] {n_synthetic} synthetic candle(s) found (volume unreliable) — excluding from volume claims")

    return df.sort_values("date").reset_index(drop=True)