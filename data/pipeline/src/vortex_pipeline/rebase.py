"""Moves a captured sample forward in time, so its newest event lands at "now".

The BigQuery partitions expire by event date (30 days in the raw tables, 60 in the curated ones), so a sample
captured once rots out of the sandbox. Rebasing shifts every date-time of every file by one shared delta:
the intervals between events stay as they were, and so do the joins between movements and account events.

Only the fields the producers write as date-times move (``DATE_TIME_FIELDS``). A ``LocalDateTime`` stays
without an offset (read in ``--source-timezone``, like the pipeline does), an ``OffsetDateTime`` keeps its
own offset, and the array Jackson writes with ``WRITE_DATES_AS_TIMESTAMPS`` stays an array. Anything else,
a line that is not JSON included, passes untouched: deciding what is invalid is the pipeline's job.

This runs before the pipeline, on the local DirectRunner only; the Dataflow path never sees it. It is a
filter: lines come in on stdin and go out on stdout, and the module never opens a file (``run-local.sh``
does the reading and writing). One delta for several files takes two steps:

    cat movements.ndjson user_events.ndjson | python -m vortex_pipeline.rebase newest     # prints an instant
    python -m vortex_pipeline.rebase shift --newest <instant> < movements.ndjson > out/movements.ndjson
"""

from __future__ import annotations

import argparse
import json
import sys
from collections.abc import Iterable, Sequence
from datetime import UTC, datetime, timedelta
from typing import Any, TextIO
from zoneinfo import ZoneInfo

from vortex_pipeline.parse import InvalidRecordError, _to_datetime

# timestamp: both producers; dataMovimento: movement; lastLogin and expiresAt: account events
DATE_TIME_FIELDS = ("timestamp", "dataMovimento", "lastLogin", "expiresAt")


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


def newest(lines: Iterable[str], source_tz: ZoneInfo) -> datetime | None:
    """The latest ``timestamp`` among the lines, or None when no line has a readable one."""
    moments = (_moment(event.get("timestamp"), source_tz) for event in _events(lines))
    return max((moment for moment in moments if moment is not None), default=None)


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


def rebase_lines(lines: Iterable[str], delta: timedelta, source_tz: ZoneInfo) -> Iterable[str]:
    """Each line shifted by ``delta``; with no delta, the lines exactly as they came."""
    if not delta:
        yield from lines
        return
    for line in lines:
        yield rebase_line(line, delta, source_tz) if line.strip() else line


def _instant(value: str) -> datetime:
    moment = datetime.fromisoformat(value)
    if moment.tzinfo is None:
        raise ValueError("an instant needs an offset, such as 2026-11-23T12:00:00+00:00")
    return moment


def main(
    argv: Sequence[str] | None = None, stdin: TextIO | None = None, stdout: TextIO | None = None
) -> None:
    source, sink = stdin or sys.stdin, stdout or sys.stdout
    parser = argparse.ArgumentParser(
        prog="python -m vortex_pipeline.rebase", description=__doc__.split("\n", 1)[0]
    )
    parser.add_argument("--source-timezone", type=ZoneInfo, default=ZoneInfo("America/Sao_Paulo"))
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("newest", help="print the newest timestamp of stdin (nothing if none is readable)")
    shift = commands.add_parser("shift", help="copy stdin to stdout, shifted so --newest lands on --now")
    shift.add_argument("--newest", default="", help="the instant `newest` printed; empty copies stdin as is")
    shift.add_argument("--now", help="ISO-8601 instant with an offset (default: the clock)")
    args = parser.parse_args(argv)
    lines = source.read().splitlines()

    if args.command == "newest":
        latest = newest(lines, args.source_timezone)
        sink.write(f"{latest.isoformat()}\n" if latest else "\n")
        return

    try:
        latest = _instant(args.newest) if args.newest.strip() else None
        now = _instant(args.now) if args.now else datetime.now(UTC)
    except ValueError as error:
        parser.error(str(error))
    delta = now - latest if latest else timedelta(0)
    sink.writelines(f"{line}\n" for line in rebase_lines(lines, delta, args.source_timezone))


if __name__ == "__main__":
    main()
