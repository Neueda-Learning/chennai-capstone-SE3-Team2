from __future__ import annotations

import logging
import sys

from extract import extract
from load import load
from pipeline_exceptions import PipelineError
from transform import transform

logger = logging.getLogger(__name__)


def run_pipeline(symbol: str) -> None:
    """
    Run the complete ETL pipeline for a given symbol.

    Flow:
        Extract -> Transform -> Load

    Raises PipelineError. Callers decide whether to stop or continue by
    reading exc.fatal rather than by catching a specific type.
    """
    raw_response = extract(symbol)

    clean_df, rejected_df = transform(raw_response)

    load(clean_df, rejected_df)

    print(f"ETL pipeline completed successfully for {symbol}")


def main(argv: list[str] | None = None) -> int:
    argv = sys.argv[1:] if argv is None else argv

    logging.basicConfig(
        level=logging.INFO,
        format="%(levelname)-8s %(name)s %(message)s",
    )

    if not argv:
        print("usage: python pipeline.py SYMBOL [SYMBOL ...]", file=sys.stderr)
        return 2

    succeeded = []
    failed = {}
    stop_reason = None
    not_attempted = []

    for index, symbol in enumerate(argv):
        try:
            run_pipeline(symbol)
            succeeded.append(symbol)

        except PipelineError as exc:
            if exc.fatal:
                not_attempted = argv[index + 1:]
                stop_reason = _describe_stop(exc, symbol, not_attempted)
                logger.error(
                    "run_stopped %s remaining=%s",
                    exc.log_fields(),
                    len(not_attempted),
                )
                break

            failed[symbol] = f"{exc.mode}: {exc}"
            logger.warning("symbol_failed %s continuing=true", exc.log_fields())

        except Exception as exc:  # noqa: BLE001
            failed[symbol] = f"unhandled {type(exc).__name__}: {exc}"
            logger.exception(
                "symbol_failed_unhandled symbol=%s type=%s",
                symbol,
                type(exc).__name__,
            )

    _print_summary(succeeded, failed, not_attempted, stop_reason)

    # 0 = all done, 1 = some symbols failed, 2 = run stopped early
    if stop_reason:
        return 2
    if failed:
        return 1
    return 0


def _describe_stop(exc: PipelineError, symbol: str, remaining: list) -> str:
    detail = f"{exc.mode} at {symbol}: {exc}"

    resets_at = getattr(exc, "resets_at", None)
    retry_after = getattr(exc, "retry_after_seconds", None)

    if resets_at is not None:
        detail += f"; quota resets at {resets_at.isoformat()}"
        if retry_after is not None:
            detail += f" (in {retry_after:.0f}s)"
    elif exc.mode == "quota_exhausted":
        detail += "; reset time unknown (no usable Retry-After header)"

    if remaining:
        detail += f"; {len(remaining)} symbol(s) not attempted"

    return detail


def _print_summary(succeeded, failed, not_attempted, stop_reason) -> None:
    print("\n" + "=" * 60)
    print("PIPELINE SUMMARY")
    print("=" * 60)
    print(f"succeeded      : {len(succeeded)}")
    print(f"failed         : {len(failed)}")

    for symbol, reason in failed.items():
        print(f"  {symbol}: {reason}")

    if not_attempted:
        print(f"never attempted: {len(not_attempted)} {not_attempted}")

    if stop_reason:
        print(f"\nRUN STOPPED EARLY: {stop_reason}")

    print("=" * 60)


if __name__ == "__main__":
    sys.exit(main())