from __future__ import annotations

from datetime import datetime, timezone

import duckdb

_KEY = "last_watermark"
_EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)


def read_watermark(conn: duckdb.DuckDBPyConnection) -> datetime:
    row = conn.execute(
        "SELECT value FROM etl_metadata WHERE key = ?", [_KEY]
    ).fetchone()
    if row is None:
        return _EPOCH
    return datetime.fromisoformat(row[0])


def write_watermark(conn: duckdb.DuckDBPyConnection, ts: datetime) -> None:
    conn.execute(
        "INSERT INTO etl_metadata (key, value) VALUES (?, ?)"
        " ON CONFLICT (key) DO UPDATE SET value = excluded.value",
        [_KEY, ts.isoformat()],
    )
