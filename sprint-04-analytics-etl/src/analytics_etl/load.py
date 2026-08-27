from __future__ import annotations

from pathlib import Path

import pandas as pd


# Folder where CSV files will be stored
DATA_DIR = Path("data")

CLEAN_FILE = DATA_DIR / "clean_candles.csv"
REJECTED_FILE = DATA_DIR / "rejected_candles.csv"



def load(
    clean_df: pd.DataFrame,
    rejected_df: pd.DataFrame,
) -> None:
    """
    Load transformed data into CSV files.

    clean_df:
        Valid candles ready for analytics.

    rejected_df:
        Invalid candles with rejection reasons.

    Behaviour:
        - Creates CSV files automatically if missing.
        - Appends new pipeline runs.
        - Preserves existing data.
    """

    DATA_DIR.mkdir(
        parents=True,
        exist_ok=True,
    )


    # -----------------------------
    # Load clean candle data
    # -----------------------------

    if not clean_df.empty:

        file_exists = CLEAN_FILE.exists()

        clean_df.to_csv(
            CLEAN_FILE,
            mode="a",
            header=not file_exists,
            index=False,
        )

        print(
            f"Loaded {len(clean_df)} clean records "
            f"into {CLEAN_FILE}"
        )

    else:
        print("No clean records to load.")



    # -----------------------------
    # Load rejected candle data
    # -----------------------------

    if not rejected_df.empty:

        file_exists = REJECTED_FILE.exists()

        rejected_df.to_csv(
            REJECTED_FILE,
            mode="a",
            header=not file_exists,
            index=False,
        )

        print(
            f"Loaded {len(rejected_df)} rejected records "
            f"into {REJECTED_FILE}"
        )

    else:
        print("No rejected records to load.")



    print("CSV load completed successfully.")