
from __future__ import annotations

import json
import logging
import os
import time
from pathlib import Path

import requests
from dotenv import load_dotenv
load_dotenv()

logger = logging.getLogger(__name__)

BASE_URL = os.getenv(
    "FAUXNANCE_BASE_URL",
    "http://localhost:8000",
)


CACHE_DIR = Path(
    os.getenv("ETL_CACHE_DIR", ".cache")
)
CACHE_TTL_SECONDS = int(
    os.getenv("ETL_CACHE_TTL_SECONDS", "3600")
)

MAX_RETRIES = 3
INITIAL_BACKOFF = 1


class RateLimitError(Exception):
    """Raised when the API returns HTTP 429."""


class ExtractionError(Exception):
    """Raised when extraction fails for a symbol."""


def extract(symbol: str) -> dict:
    """
    Extract a raw candles response for a symbol.

    Cache is checked before making a network request.

    Returns the raw API JSON unchanged.

    Raises:
        RateLimitError:
            API returned HTTP 429.

        ExtractionError:
            A non-retryable extraction error occurred.
    """

    cache_path = _cache_path(symbol)

    # ---------------------------------------------------------
    # 1. Check cache
    # ---------------------------------------------------------
    if cache_path.exists():
        cached_response = _read_cache(cache_path)

        if cached_response is not None:
            logger.info(
                "cache_hit symbol=%s",
                symbol,
            )
            return cached_response

        logger.info(
            "cache_stale symbol=%s",
            symbol,
        )
    else:
        logger.info(
            "cache_miss symbol=%s",
            symbol,
        )

    # ---------------------------------------------------------
    # 2. Get API key
    # ---------------------------------------------------------
    api_key = os.getenv("FAUXNANCE_API_KEY")

    if not api_key:
        raise ExtractionError(
            f"{"FAUXNANCE_API_KEY"} is not set"
        )

    url = f"{BASE_URL}/candles/{symbol}"

    headers = {
        "X-Api-Key": api_key,
    }

    # ---------------------------------------------------------
    # 3. Request with retry handling
    # ---------------------------------------------------------
    for attempt in range(MAX_RETRIES + 1):

        try:
            response = requests.get(
                url,
                headers=headers,
                timeout=10,
            )

        except (requests.ConnectionError, requests.Timeout) as exc:

            if attempt >= MAX_RETRIES:
                logger.error(
                    "connection_failure symbol=%s attempts=%s",
                    symbol,
                    attempt + 1,
                )

                raise ExtractionError(
                    f"Connection failed after "
                    f"{attempt + 1} attempts for {symbol}"
                ) from exc

            delay = INITIAL_BACKOFF * (2 ** attempt)

            logger.warning(
                "connection_failure symbol=%s "
                "attempt=%s retry_in=%s",
                symbol,
                attempt + 1,
                delay,
            )

            time.sleep(delay)
            continue

        # -----------------------------------------------------
        # 4. Rate limit
        # -----------------------------------------------------
        if response.status_code == 429:

            retry_after = response.headers.get(
                "Retry-After"
            )

            logger.error(
                "rate_limit symbol=%s retry_after=%s",
                symbol,
                retry_after,
            )

            raise RateLimitError(
                f"Rate limited for {symbol}; "
                f"Retry-After={retry_after}"
            )

        # -----------------------------------------------------
        # 5. Other client errors
        # -----------------------------------------------------
        if 400 <= response.status_code < 500:

            message = response.text

            logger.error(
                "client_error symbol=%s status=%s message=%s",
                symbol,
                response.status_code,
                message,
            )

            raise ExtractionError(
                f"HTTP {response.status_code} for "
                f"{symbol}: {message}"
            )

        # -----------------------------------------------------
        # 6. Server errors
        #
        # Treat these as extraction failures. They are not
        # one of the four required modes, so we don't retry
        # them here unless the API contract later requires it.
        # -----------------------------------------------------
        if response.status_code >= 500:

            logger.error(
                "server_error symbol=%s status=%s",
                symbol,
                response.status_code,
            )

            raise ExtractionError(
                f"HTTP {response.status_code} for {symbol}"
            )

        # -----------------------------------------------------
        # 7. Successful response
        # -----------------------------------------------------
        if response.status_code == 200:

            try:
                raw_response = response.json()
            except ValueError as exc:

                logger.error(
                    "invalid_json symbol=%s",
                    symbol,
                )

                raise ExtractionError(
                    f"Invalid JSON response for {symbol}"
                ) from exc

            # IMPORTANT:
            #
            # We deliberately do not validate the candle
            # payload here.
            #
            # A 200 response containing bad candle data
            # belongs to transform.py.
            _write_cache(
                cache_path,
                raw_response,
            )

            logger.info(
                "extraction_success symbol=%s",
                symbol,
            )

            return raw_response

        # Unexpected status code
        raise ExtractionError(
            f"Unexpected HTTP {response.status_code} "
            f"for {symbol}"
        )

    raise ExtractionError(
        f"Extraction failed for {symbol}"
    )


def _cache_path(symbol: str) -> Path:
    """
    Return the cache path for a symbol.
    """

    safe_symbol = symbol.replace("/", "_")

    return CACHE_DIR / f"{safe_symbol}.json"


def _read_cache(path: Path) -> dict | None:
    """
    Read a cached response if it exists and is still fresh.

    Returns None when:
    - no cache exists
    - cache is stale
    - cache cannot be decoded
    """

    if not path.exists():
        return None

    try:
        age = time.time() - path.stat().st_mtime

        if age > CACHE_TTL_SECONDS:
            return None

        with path.open("r", encoding="utf-8") as file:
            cached = json.load(file)

        return cached

    except (OSError, json.JSONDecodeError) as exc:
        logger.warning(
            "cache_read_failure path=%s error=%s",
            path,
            exc,
        )

        return None


def _write_cache(path: Path, response: dict) -> None:
    """
    Persist a raw API response to the cache.
    """

    try:
        path.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        with path.open(
            "w",
            encoding="utf-8",
        ) as file:
            json.dump(
                response,
                file,
                indent=2,
            )

    except OSError as exc:

        logger.warning(
            "cache_write_failure path=%s error=%s",
            path,
            exc,
        )

