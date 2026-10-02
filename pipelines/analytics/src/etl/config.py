from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

# Anchor: the etl/ project root (three parents above this file).
_ETL_ROOT = Path(__file__).resolve().parent.parent.parent

# Postgres — same env var names as docker-compose so no extra configuration
# is needed when running against the local stack.
DB_HOST: str = os.getenv("DB_HOST", "localhost")
DB_PORT: int = int(os.getenv("DB_PORT", "5432"))
DB_NAME: str = os.getenv("DB_NAME", "trading")
# The extract only reads, so it connects as the read-only role when one is
# configured, and falls back to DB_USER otherwise.
DB_USER: str = os.getenv("ANALYTICS_DB_USER") or os.getenv("DB_USER", "analytics_ro")
DB_PASSWORD: str = os.getenv("ANALYTICS_DB_PASSWORD") or os.getenv("DB_PASSWORD", "")

DUCKDB_PATH: str = os.getenv(
    "DUCKDB_PATH", str(_ETL_ROOT / "data" / "analytics.duckdb")
)
DEAD_LETTER_DIR: str = os.getenv(
    "DEAD_LETTER_DIR", str(_ETL_ROOT / "data" / "dead_letters")
)
