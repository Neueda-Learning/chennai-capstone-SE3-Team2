from __future__ import annotations

import csv
import io
import logging
import os
from pathlib import Path

import pandas as pd

try:
    from pipeline_exceptions import (
        LoadWriteError,
        OutputUnavailableError,
        SchemaDriftError,
    )
except ImportError:
    from pipeline_errors import (
        LoadWriteError,
        OutputUnavailableError,
        SchemaDriftError,
    )

logger = logging.getLogger(__name__)

# Folder where CSV files will be stored
DATA_DIR = Path("data")

CLEAN_FILE = DATA_DIR / "clean_candles.csv"
REJECTED_FILE = DATA_DIR / "rejected_candles.csv"


def load(clean_df: pd.DataFrame, rejected_df: pd.DataFrame) -> None:
    """
    Load transformed data into CSV files.

    Behaviour:
        - Creates CSV files automatically if missing.
        - Appends new pipeline runs.
        - Preserves existing data.
    """
    symbol = _peek_symbol(clean_df, rejected_df)

    _ensure_output_dir(symbol)

    # Rejects are written first so that if the clean write then fails,
    # the rejects append can be rolled back and the two files stay in step.
    rejected_rollback = None

    if _has_rows(rejected_df):
        rejected_rollback = _file_length(REJECTED_FILE)
        _append_csv(rejected_df, REJECTED_FILE, symbol)
        print(
            f"Loaded {len(rejected_df)} rejected records "
            f"into {REJECTED_FILE}"
        )
    else:
        print("No rejected records to load.")

    if _has_rows(clean_df):
        try:
            _append_csv(clean_df, CLEAN_FILE, symbol)
        except (LoadWriteError, SchemaDriftError):
            if rejected_rollback is not None:
                _truncate(REJECTED_FILE, rejected_rollback, symbol)
                logger.warning(
                    "load_rolled_back symbol=%s file=%s",
                    symbol,
                    REJECTED_FILE.name,
                )
            raise

        print(
            f"Loaded {len(clean_df)} clean records into {CLEAN_FILE}"
        )
    else:
        print("No clean records to load.")

    print("CSV load completed successfully.")


def _peek_symbol(clean_df, rejected_df) -> str:
    for df in (clean_df, rejected_df):
        if isinstance(df, pd.DataFrame) and "symbol" in getattr(df, "columns", []):
            if not df.empty:
                return str(df["symbol"].iloc[0])
    return "?"


def _has_rows(df) -> bool:
    """True when there is something to write. Tolerates None."""
    return isinstance(df, pd.DataFrame) and not df.empty


def _ensure_output_dir(symbol: str) -> None:
    try:
        DATA_DIR.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        logger.error(
            "load_failed symbol=%s mode=output_unavailable dir=%s error=%s",
            symbol,
            DATA_DIR,
            exc,
        )
        raise OutputUnavailableError(
            f"cannot create output directory {DATA_DIR}: {exc}", symbol=symbol
        ) from exc

    if not os.access(DATA_DIR, os.W_OK):
        raise OutputUnavailableError(
            f"output directory {DATA_DIR} is not writable", symbol=symbol
        )


def _file_length(path: Path) -> int:
    try:
        return path.stat().st_size if path.exists() else 0
    except OSError:
        return 0


def _truncate(path: Path, length: int, symbol: str) -> None:
    """Undo an append by cutting the file back to its previous length."""
    try:
        if length == 0:
            path.unlink(missing_ok=True)
        else:
            with path.open("r+b") as handle:
                handle.truncate(length)
    except OSError as exc:
        logger.error(
            "load_rollback_failed symbol=%s path=%s error=%s",
            symbol,
            path,
            exc,
        )


def _read_header(path: Path) -> list[str] | None:
    """Return the header row of an existing CSV, or None if there is none."""
    if not path.exists() or path.stat().st_size == 0:
        return None
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            first = handle.readline()
        if not first.strip():
            return None
        return next(csv.reader([first]))
    except (OSError, StopIteration, csv.Error) as exc:
        logger.warning("header_read_failure path=%s error=%s", path, exc)
        return None


def _append_csv(df: pd.DataFrame, path: Path, symbol: str) -> None:
    """
    Append a dataframe to a CSV.

    The header is compared first: pandas will happily append columns in a
    different order under an existing header, silently misaligning every
    row from that point on.

    The file length is recorded before writing, so a failed append can be
    truncated away instead of leaving a partial row.
    """
    existing_header = _read_header(path)
    incoming = list(df.columns)

    if existing_header is not None and existing_header != incoming:
        missing = [c for c in existing_header if c not in incoming]
        extra = [c for c in incoming if c not in existing_header]
        detail = (
            "columns reordered"
            if not missing and not extra
            else f"missing={missing} unexpected={extra}"
        )
        logger.error(
            "load_failed symbol=%s mode=schema_drift path=%s %s",
            symbol,
            path,
            detail,
        )
        raise SchemaDriftError(
            f"{path.name} header does not match the dataframe ({detail})",
            symbol=symbol,
        )

    try:
        buffer = io.StringIO()
        df.to_csv(buffer, header=existing_header is None, index=False)
        payload = buffer.getvalue().encode("utf-8")
    except (ValueError, TypeError, UnicodeError) as exc:
        logger.error(
            "load_failed symbol=%s mode=load_write_failed path=%s error=%s",
            symbol,
            path,
            exc,
        )
        raise LoadWriteError(
            f"could not serialise rows for {path.name}: {exc}", symbol=symbol
        ) from exc

    rollback_to = _file_length(path)

    try:
        with path.open("ab") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
    except OSError as exc:
        _truncate(path, rollback_to, symbol)
        logger.error(
            "load_failed symbol=%s mode=load_write_failed path=%s error=%s",
            symbol,
            path,
            exc,
        )
        raise LoadWriteError(
            f"append to {path.name} failed and was rolled back: {exc}",
            symbol=symbol,
        ) from exc