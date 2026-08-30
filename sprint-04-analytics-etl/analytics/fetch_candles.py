"""
Fetches candle data from the Fauxnance API for whatever symbol you ask for,
and caches the raw response to candle-data/{symbol}-candle.json.

Usage:
    export FAUXNANCE_API_KEY=your_key_here     # do this once per terminal session
    python fetch_candles.py INFY.NS RELIANCE.NS TATASTEEL.BO
"""
import json
import os
import sys
from pathlib import Path
import requests
from dotenv import load_dotenv


def _find_env(start: Path) -> Path | None:
    """Walk upward from `start` looking for .env, stopping at the filesystem root."""
    for folder in [start, *start.parents]:
        candidate = folder / ".env"
        if candidate.exists():
            return candidate
    return None

env_path = _find_env(Path(__file__).parent)
if env_path:
    load_dotenv(env_path)
else:
    print("Warning: .env not found in any parent folder — FAUXNANCE_API_KEY may be unset.")

BASE_URL = "https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1/candles"
CANDLE_DIR = Path("candle-data")


def fetch_candles(symbol: str, interval: str = "1d", from_date: str = None, to_date: str = None) -> dict:
    """Fetch one symbol's candle data from the API. Returns the parsed JSON response."""
    api_key = os.environ.get("FAUXNANCE_API_KEY")
    if not api_key:
        raise RuntimeError(
            "FAUXNANCE_API_KEY is not set. Run: export FAUXNANCE_API_KEY=your_key_here"
        )

    params = {"interval": interval}
    if from_date:
        params["from"] = from_date
    if to_date:
        params["to"] = to_date

    resp = requests.get(
        f"{BASE_URL}/{symbol}",
        headers={"Accept": "application/json", "X-Api-Key": api_key},
        params=params,
        timeout=30,
    )
    resp.raise_for_status()
    return resp.json()


def fetch_and_cache(symbol: str, force: bool = False, **kwargs) -> Path:
    """
    Fetch a symbol and save it to candle-data/{symbol}-candle.json.
    Skips the network call if the file already exists, unless force=True.
    Returns the path to the cached file.
    """
    CANDLE_DIR.mkdir(exist_ok=True)
    out_path = CANDLE_DIR / f"{symbol}-candle.json"

    if out_path.exists() and not force:
        print(f"[{symbol}] using cached file at {out_path} (pass force=True to refetch)")
        return out_path

    print(f"[{symbol}] fetching from API...")
    data = fetch_candles(symbol, **kwargs)
    out_path.write_text(json.dumps(data))
    n = len(data.get("data", {}).get("candles", []))
    print(f"[{symbol}] saved {n} candle(s) to {out_path}")
    return out_path


if __name__ == "__main__":
    symbols = sys.argv[1:] or ["INFY.NS", "RELIANCE.NS", "TATASTEEL.BO"]
    for sym in symbols:
        fetch_and_cache(sym, force=True)