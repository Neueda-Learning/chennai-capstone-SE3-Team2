from __future__ import annotations

import json
import logging
import os
import time
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

import requests
from dotenv import load_dotenv

try:
    from pipeline_exceptions import (
        BadRequestError,
        MalformedResponseError,
        MissingCredentialError,
        QuotaExhaustedError,
        ServiceUnreachableError,
        UpstreamServiceError,
    )
except ImportError:
    from pipeline_errors import (
        BadRequestError,
        MalformedResponseError,
        MissingCredentialError,
        QuotaExhaustedError,
        ServiceUnreachableError,
        UpstreamServiceError,
    )

load_dotenv()

logger = logging.getLogger(__name__)

BASE_URL = os.getenv("FAUXNANCE_BASE_URL", "http://localhost:8000")
API_KEY_ENV = "FAUXNANCE_API_KEY"

CACHE_DIR = Path(os.getenv("ETL_CACHE_DIR", ".cache"))
CACHE_TTL_SECONDS = int(os.getenv("ETL_CACHE_TTL_SECONDS", "3600"))

MAX_RETRIES = 3
INITIAL_BACKOFF = 1
REQUEST_TIMEOUT = 10
MAX_LOGGED_BODY = 500
MIN_REDACTABLE_KEY = 8


def extract(symbol: str) -> dict:
    """
    Extract a raw candles response for a symbol.

    Cache is checked before making a network request.
    Returns the raw API JSON unchanged.
    """
    cache_path = _cache_path(symbol)

    # 1. Check cache
    if cache_path.exists():
        cached_response = _read_cache(cache_path)
        if cached_response is not None:
            logger.info("cache_hit symbol=%s", symbol)
            return cached_response
        logger.info("cache_stale symbol=%s", symbol)
    else:
        logger.info("cache_miss symbol=%s", symbol)

    # 2. Get API key
    api_key = os.getenv(API_KEY_ENV)
    if not api_key:
        logger.error(
            "extract_failed symbol=%s mode=missing_credential env=%s",
            symbol,
            API_KEY_ENV,
        )
        raise MissingCredentialError(
            f"{API_KEY_ENV} is not set", symbol=symbol
        )

    url = f"{BASE_URL}/candles/{symbol}"
    headers = {"X-Api-Key": api_key}

    # 3. Request with retry handling
    for attempt in range(MAX_RETRIES + 1):
        attempts_made = attempt + 1

        try:
            response = requests.get(
                url, headers=headers, timeout=REQUEST_TIMEOUT
            )

        except (requests.ConnectionError, requests.Timeout) as exc:
            if attempt >= MAX_RETRIES:
                logger.error(
                    "extract_failed symbol=%s mode=unreachable "
                    "attempts=%s error=%s",
                    symbol,
                    attempts_made,
                    type(exc).__name__,
                )
                raise ServiceUnreachableError(
                    f"Connection failed after {attempts_made} attempts "
                    f"for {symbol}",
                    symbol=symbol,
                    attempts=attempts_made,
                ) from exc

            delay = INITIAL_BACKOFF * (2 ** attempt)
            logger.warning(
                "extract_retry symbol=%s mode=unreachable attempt=%s "
                "retry_in=%s error=%s",
                symbol,
                attempts_made,
                delay,
                type(exc).__name__,
            )
            time.sleep(delay)
            continue

        except requests.RequestException as exc:
            logger.error(
                "extract_failed symbol=%s mode=unreachable error=%s",
                symbol,
                type(exc).__name__,
            )
            raise ServiceUnreachableError(
                f"Request failed for {symbol}: {type(exc).__name__}",
                symbol=symbol,
                attempts=attempts_made,
            ) from exc

        status = response.status_code

        # 4. Rate limit - no retry, no sleep
        if status == 429:
            raw_retry_after = response.headers.get("Retry-After")
            seconds, resets_at = _parse_retry_after(raw_retry_after)

            logger.error(
                "extract_failed symbol=%s mode=quota_exhausted "
                "retry_after=%s resets_at=%s",
                symbol,
                seconds if seconds is not None else "unknown",
                resets_at.isoformat() if resets_at else "unknown",
            )
            raise QuotaExhaustedError(
                f"Rate limited for {symbol}; Retry-After={raw_retry_after}",
                symbol=symbol,
                retry_after=raw_retry_after,
                retry_after_seconds=seconds,
                resets_at=resets_at,
            )

        # 5. Other client errors - no retry
        if 400 <= status < 500:
            body = _safe_body(response, api_key)
            logger.error(
                "extract_failed symbol=%s mode=bad_request status=%s body=%r",
                symbol,
                status,
                body,
            )
            raise BadRequestError(
                f"HTTP {status} for {symbol}: {body}",
                symbol=symbol,
                status_code=status,
                body=body,
            )

        # 6. Server errors
        if status >= 500:
            logger.error(
                "extract_failed symbol=%s mode=upstream_error status=%s",
                symbol,
                status,
            )
            raise UpstreamServiceError(
                f"HTTP {status} for {symbol}",
                symbol=symbol,
                status_code=status,
            )

        # 7. Successful response
        if status == 200:
            try:
                raw_response = response.json()
            except ValueError as exc:
                body = _safe_body(response, api_key)
                logger.error(
                    "extract_failed symbol=%s mode=malformed_response "
                    "body=%r",
                    symbol,
                    body,
                )
                raise MalformedResponseError(
                    f"Invalid JSON response for {symbol}", symbol=symbol
                ) from exc

            # Candle contents are not validated here - that is transform's job.
            _write_cache(cache_path, raw_response)
            logger.info("extract_ok symbol=%s attempts=%s", symbol, attempts_made)
            return raw_response

        # Unexpected status code
        logger.error(
            "extract_failed symbol=%s mode=bad_request status=%s",
            symbol,
            status,
        )
        raise BadRequestError(
            f"Unexpected HTTP {status} for {symbol}",
            symbol=symbol,
            status_code=status,
        )

    raise ServiceUnreachableError(
        f"Extraction failed for {symbol}",
        symbol=symbol,
        attempts=MAX_RETRIES + 1,
    )


def _parse_retry_after(
    value: str | None,
) -> tuple[float | None, datetime | None]:
    """Parse Retry-After (delta-seconds or HTTP-date) into (seconds, reset)."""
    if not value:
        return None, None

    value = value.strip()

    try:
        seconds = float(value)
        if seconds < 0:
            return None, None
        reset_ts = datetime.now(timezone.utc).timestamp() + seconds
        return seconds, datetime.fromtimestamp(reset_ts, tz=timezone.utc)
    except ValueError:
        pass

    try:
        resets_at = parsedate_to_datetime(value)
        if resets_at.tzinfo is None:
            resets_at = resets_at.replace(tzinfo=timezone.utc)
        seconds = (resets_at - datetime.now(timezone.utc)).total_seconds()
        return max(seconds, 0.0), resets_at
    except (TypeError, ValueError):
        return None, None


def _safe_body(response: requests.Response, api_key: str | None) -> str:
    """Truncate the body and scrub the API key before logging it."""
    try:
        body = response.text or ""
    except Exception:  # noqa: BLE001
        return "<unreadable body>"

    # Only redact keys long enough to be real. A short test key like "k"
    # would otherwise match inside ordinary words and mangle the message.
    if api_key and len(api_key) >= MIN_REDACTABLE_KEY:
        body = body.replace(api_key, "***REDACTED***")

    if len(body) > MAX_LOGGED_BODY:
        body = body[:MAX_LOGGED_BODY] + f"... [{len(body)} bytes total]"

    return body


def _cache_path(symbol: str) -> Path:
    """Return the cache path for a symbol."""
    safe_symbol = symbol.replace("/", "_")
    return CACHE_DIR / f"{safe_symbol}.json"


def _read_cache(path: Path) -> dict | None:
    """
    Read a cached response if it exists and is still fresh.

    Returns None when there is no cache, it is stale, or it cannot be
    decoded. A broken cache never fails the run - it falls back to a fetch.
    """
    if not path.exists():
        return None

    try:
        age = time.time() - path.stat().st_mtime
        if age > CACHE_TTL_SECONDS:
            return None
        with path.open("r", encoding="utf-8") as file:
            return json.load(file)
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        logger.warning("cache_read_failure path=%s error=%s", path, exc)
        return None


def _write_cache(path: Path, response: dict) -> None:
    """
    Persist a raw API response to the cache.

    Written to a temp file then renamed, so an interrupted write cannot
    leave a truncated file that later parses as a valid half-response.
    """
    tmp_path = path.with_suffix(path.suffix + ".tmp")
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with tmp_path.open("w", encoding="utf-8") as file:
            json.dump(response, file, indent=2)
        tmp_path.replace(path)
    except (OSError, TypeError, ValueError) as exc:
        logger.warning("cache_write_failure path=%s error=%s", path, exc)
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass