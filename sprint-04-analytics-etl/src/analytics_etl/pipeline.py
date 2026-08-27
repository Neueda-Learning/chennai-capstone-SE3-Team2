from __future__ import annotations

import sys

from .extract import extract
from .transform import transform
from .load import load

def run_pipeline(symbol: str) -> None:
    """
    Run the complete ETL pipeline for a given symbol.

    Flow:
        Extract -> Transform -> Load
    """

    # Extract raw market data
    raw_response = extract(symbol)
    print(raw_response)

    # Transform and validate the raw data
    clean_df, rejected_df = transform(raw_response)

    # Load cleaned analytical data into DuckDB
    load(clean_df,rejected_df)


    print(f"ETL pipeline completed successfully for {symbol}")


if __name__ == "__main__":
    symbol = sys.argv[1]
    run_pipeline(symbol)