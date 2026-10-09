from __future__ import annotations

import json
import logging
from pathlib import Path

logger = logging.getLogger(__name__)


def write_dead_letters(
    batch_id: str,
    rejected: list[dict],
    dead_letter_dir: str,
) -> None:
    if not rejected:
        return

    out_dir = Path(dead_letter_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / f"{batch_id}.jsonl"

    with out_file.open("a", encoding="utf-8") as fh:
        for entry in rejected:
            fh.write(json.dumps(entry, default=str) + "\n")

    logger.warning(
        "dead_lettered batch_id=%s count=%d file=%s",
        batch_id,
        len(rejected),
        out_file,
    )
