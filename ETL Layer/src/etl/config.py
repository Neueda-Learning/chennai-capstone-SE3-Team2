from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

# Anchor: the etl/ project root (three parents above this file).
_ETL_ROOT = Path(__file__).resolve().parent.parent.parent
_REPO_ROOT = _ETL_ROOT.parent


def _from_repo(path: str) -> str:
    """A relative path is taken from the repository root, as .env.example writes it."""
    candidate = Path(path)
    return str(candidate if candidate.is_absolute() else _REPO_ROOT / candidate)

# Postgres — same env var names as docker-compose so no extra configuration
# is needed when running against the local stack.
DB_HOST: str = os.getenv("DB_HOST", "localhost")
DB_PORT: int = int(os.getenv("DB_PORT", "5432"))
DB_NAME: str = os.getenv("DB_NAME", "trading_system_db")
# The extract only reads, so it connects as the read-only role when one is
# configured, and falls back to DB_USER otherwise.
DB_USER: str = os.getenv("ANALYTICS_DB_USER") or os.getenv("DB_USER", "analytics_ro")
DB_PASSWORD: str = os.getenv("ANALYTICS_DB_PASSWORD") or os.getenv("DB_PASSWORD", "")

DUCKDB_PATH: str = _from_repo(os.getenv(
    "DUCKDB_PATH", "Databases/DuckDB/analytics/analytics.duckdb")
)
DEAD_LETTER_DIR: str = _from_repo(os.getenv(
    "DEAD_LETTER_DIR", "ETL Layer/data/dead_letters")
)
