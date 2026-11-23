"""Moves a captured sample forward in time, so its newest event lands at "now".

The BigQuery partitions expire by event date (30 days in the raw tables, 60 in the curated ones), so a sample
captured once rots out of the sandbox. Rebasing shifts every date-time of every file by one shared delta:
the intervals between events stay as they were, and so do the joins between movements and account events.

Only the fields the producers write as date-times move (``DATE_TIME_FIELDS``). A ``LocalDateTime`` stays
without an offset (read in ``--source-timezone``, like the pipeline does), an ``OffsetDateTime`` keeps its
own offset, and the array Jackson writes with ``WRITE_DATES_AS_TIMESTAMPS`` stays an array. Anything else,
a line that is not JSON included, passes untouched: deciding what is invalid is the pipeline's job.

This runs before the pipeline, on the local DirectRunner only; the Dataflow path never sees it. It reads
and writes only inside the repository's ``data/`` folder (``DATA_DIR``), where the fixtures, the Kafka
captures (``data/landing``) and the runs (``data/out``) live: a path that resolves anywhere else is refused.
"""

from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Iterable, Sequence
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from vortex_pipeline.parse import InvalidRecordError, _to_datetime

# timestamp: both producers; dataMovimento: movement; lastLogin and expiresAt: account events
DATE_TIME_FIELDS = ("timestamp", "dataMovimento", "lastLogin", "expiresAt")
# data/ of the repository: src/vortex_pipeline/rebase.py -> vortex_pipeline, src, pipeline, data
DATA_DIR = Path(__file__).resolve().parents[3]


class OutsideDataDirError(ValueError):
    """A path given to the rebase resolves outside ``DATA_DIR``."""


def confined(path: Path) -> Path:
    """The path resolved (symbolic links and ``..`` included), if it stays inside ``DATA_DIR``."""
    resolved = path.resolve()
    if not resolved.is_relative_to(DATA_DIR.resolve()):
        raise OutsideDataDirError(f"{path} is outside {DATA_DIR}")
    return resolved


def _read(value: Any) -> datetime | None:
    """The value as written (naive for a LocalDateTime), or None when it is not an ISO string or an array."""
    if not isinstance(value, str | list):
        return None
    try:
        return _to_datetime(value, "rebase")
    except InvalidRecordError:
        return None


def _moment(value: Any, source_tz: ZoneInfo) -> datetime | None:
    moment = _read(value)
    if moment is None or moment.tzinfo:
        return moment
    return moment.replace(tzinfo=source_tz)


def _shift(value: Any, delta: timedelta, source_tz: ZoneInfo) -> Any:
    written = _read(value)
    if written is None:
        return value
    if written.tzinfo:
        return (written + delta).isoformat()
    # Shift the instant, then go back to the wall clock of the source zone (a DST change moves the hour)
    local = (written.replace(tzinfo=source_tz) + delta).astimezone(source_tz).replace(tzinfo=None)
    if isinstance(value, list):
        return [
            local.year,
            local.month,
            local.day,
            local.hour,
            local.minute,
            local.second,
            local.microsecond * 1000,
        ]
    return local.isoformat()


def _events(lines: Iterable[str]) -> Iterable[dict[str, Any]]:
    for line in lines:
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if isinstance(event, dict):
            yield event


def newest(files: Sequence[Path], source_tz: ZoneInfo) -> datetime | None:
    """The latest ``timestamp`` across all files, or None when no line has a readable one."""
    moments = [
        moment
        for path in files
        for event in _events(path.read_text(encoding="utf-8").splitlines())
        if (moment := _moment(event.get("timestamp"), source_tz)) is not None
    ]
    return max(moments, default=None)


def rebase_line(line: str, delta: timedelta, source_tz: ZoneInfo) -> str:
    try:
        event = json.loads(line)
    except ValueError:
        return line
    if not isinstance(event, dict):
        return line
    shifted = {field: _shift(event[field], delta, source_tz) for field in DATE_TIME_FIELDS if field in event}
    if all(event[field] == value for field, value in shifted.items()):
        return line  # nothing moved: keep the line byte for byte
    return json.dumps(event | shifted, ensure_ascii=False, separators=(",", ":"))


def rebase(files: Sequence[Path], output_dir: Path, now: datetime, source_tz: ZoneInfo) -> timedelta:
    """Writes each file, rebased, to ``output_dir`` under its own name; returns the delta applied.

    Every path must resolve inside ``DATA_DIR``; nothing is read or written when one does not.
    """
    sources = [confined(path) for path in files]
    target = confined(output_dir)
    latest = newest(sources, source_tz)
    delta = now - latest if latest else timedelta(0)
    target.mkdir(parents=True, exist_ok=True)
    for path in sources:
        lines = path.read_text(encoding="utf-8").splitlines()
        rebased = [rebase_line(line, delta, source_tz) if line.strip() else line for line in lines]
        confined(target / path.name).write_text("".join(f"{line}\n" for line in rebased), encoding="utf-8")
    return delta


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="python -m vortex_pipeline.rebase", description=__doc__.split("\n")[0]
    )
    parser.add_argument("files", nargs="+", type=Path, help="NDJSON files rebased together, with one delta")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--source-timezone", type=ZoneInfo, default=ZoneInfo("America/Sao_Paulo"))
    parser.add_argument(
        "--now", type=datetime.fromisoformat, help="ISO-8601 instant with an offset (default: the clock)"
    )
    args = parser.parse_args(argv)
    now = args.now or datetime.now(UTC)
    if now.tzinfo is None:
        parser.error("--now needs an offset, such as 2026-11-23T12:00:00+00:00")
    try:
        delta = rebase(args.files, args.output_dir, now, args.source_timezone)
    except OutsideDataDirError as error:
        parser.error(str(error))
    print(f"rebase: moved {len(args.files)} files by {delta}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
