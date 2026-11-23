"""Rebasing a captured sample: one delta for every file, each date-time kept in the shape it was written."""

import io
import json
import runpy
import sys
import warnings
from datetime import UTC, datetime, timedelta
from zoneinfo import ZoneInfo

import pytest
from conftest import FIXTURES

from vortex_pipeline import rebase
from vortex_pipeline.parse import Context, parse_movement, parse_user_event

SP = ZoneInfo("America/Sao_Paulo")
NOW = datetime(2026, 11, 23, 15, 0, tzinfo=UTC)


def _lines(*events):
    return [e if isinstance(e, str) else json.dumps(e) for e in events]


def _shifted(lines, now=NOW):
    latest = rebase.newest(lines, SP)
    delta = now - latest if latest else timedelta(0)
    return list(rebase.rebase_lines(lines, delta, SP))


def _run(argv, text):
    out = io.StringIO()
    code = rebase.main(argv, stdin=io.StringIO(text), stdout=out)
    return code, out.getvalue()


def test_the_newest_event_lands_at_now_and_the_intervals_stay():
    movements = _lines(
        {"timestamp": "2026-11-10T09:00:00", "dataMovimento": "2026-11-10T09:00:00"},
        {"timestamp": "2026-11-10T10:30:00.123456789"},
    )
    events = _lines(
        {"timestamp": "2026-11-10T12:00:00Z", "lastLogin": "2026-11-10T12:00:00Z", "expiresAt": None},
    )
    # 10:30 in São Paulo (13:30Z) is newer than the account event's 12:00Z
    latest = rebase.newest(movements + events, SP)
    assert latest == datetime(2026, 11, 10, 13, 30, 0, 123456, tzinfo=UTC)
    delta = NOW - latest

    moved = [json.loads(line) for line in rebase.rebase_lines(movements, delta, SP)]
    assert moved[1]["timestamp"] == "2026-11-23T12:00:00"  # wall clock of São Paulo, still no offset
    assert moved[0]["timestamp"] == moved[0]["dataMovimento"]
    later = datetime.fromisoformat(moved[1]["timestamp"]) - datetime.fromisoformat(moved[0]["timestamp"])
    assert later == timedelta(hours=1, minutes=30, microseconds=123456)
    account = json.loads(next(iter(rebase.rebase_lines(events, delta, SP))))
    assert account["timestamp"] == account["lastLogin"]
    assert datetime.fromisoformat(account["timestamp"]).utcoffset() == timedelta(0)
    assert account["expiresAt"] is None


def test_a_jackson_array_stays_an_array():
    moved = _shifted(_lines({"timestamp": [2026, 11, 22, 12, 0]}))
    assert json.loads(moved[0])["timestamp"] == [2026, 11, 23, 12, 0, 0, 0]


def test_what_is_not_a_date_time_passes_untouched():
    lines = [
        "not json",
        "[1, 2]",
        json.dumps({"timestamp": "2026-11-10T09:00:00", "other": "2026-11-10T09:00:00"}),
        json.dumps({"timestamp": "yesterday", "dataMovimento": 1700000000, "lastLogin": [1, 2]}),
        "",
    ]
    out = _shifted(lines, now=datetime(2026, 11, 11, 12, 0, tzinfo=UTC))
    assert out[:2] == lines[:2]
    assert json.loads(out[2]) == {"timestamp": "2026-11-11T09:00:00", "other": "2026-11-10T09:00:00"}
    assert json.loads(out[3]) == json.loads(lines[3])
    assert out[4] == ""


def test_a_line_with_nothing_to_shift_is_kept_byte_for_byte():
    lines = _lines({"timestamp": "never"}, '{"a": 1,  "b": "ç"}', "{oops")
    assert rebase.newest(lines, SP) is None
    assert list(rebase.rebase_lines(lines, timedelta(days=3), SP)) == lines


def test_the_rebased_fixtures_still_parse_into_today():
    """Every fixture line the pipeline accepts before rebasing is still accepted, now dated today."""
    names = ("movements.ndjson", "user_events.ndjson")
    before = {name: (FIXTURES / name).read_text(encoding="utf-8").splitlines() for name in names}
    latest = rebase.newest([line for name in names for line in before[name]], SP)
    context = Context(source_file="f", run_id="r", ingested_at="2026-11-23T15:00:00Z", source_tz=SP)
    for name, parse in zip(names, (parse_movement, parse_user_event), strict=True):
        after = list(rebase.rebase_lines(before[name], NOW - latest, SP))
        assert len(before[name]) == len(after)
        accepted = 0
        for old, new in zip(before[name], after, strict=True):
            try:
                parse(old, context)
            except ValueError:
                continue
            row = parse(new, context)
            accepted += 1
            assert row["event_ts"] <= "2026-11-23T15:00:00.000000Z"
            assert row["event_ts"] >= "2026-11-20"
        assert accepted


def test_main_newest_prints_the_instant_or_nothing():
    assert _run(["newest"], '{"timestamp": "2026-11-10T09:00:00"}\n') == (0, "2026-11-10T09:00:00-03:00\n")
    assert _run(["newest"], "not json\n") == (0, "\n")


def test_main_shift_moves_stdin_to_stdout():
    stdin = '{"timestamp": "2026-11-10T09:00:00Z"}\n\n'
    argv = ["shift", "--newest", "2026-11-10T09:00:00+00:00", "--now", "2026-11-11T09:00:00+00:00"]
    code, out = _run(argv, stdin)
    assert code == 0
    assert out == '{"timestamp":"2026-11-11T09:00:00+00:00"}\n\n'


def test_main_shift_without_newest_copies_stdin():
    line = '{"timestamp": "2026-11-10T09:00:00Z"}\n'
    assert _run(["shift"], line) == (0, line)


def test_main_shift_uses_the_clock_without_now():
    before = datetime.now(UTC)
    line = '{"timestamp": "2026-11-10T09:00:00Z"}\n'
    code, out = _run(["shift", "--newest", "2026-11-10T09:00:00+00:00"], line)
    assert code == 0
    assert before <= datetime.fromisoformat(json.loads(out)["timestamp"]) <= datetime.now(UTC)


@pytest.mark.parametrize("flag", ["--newest", "--now"])
def test_main_shift_refuses_an_instant_without_an_offset(flag, capsys):
    argv = ["shift", "--newest", "2026-11-10T09:00:00+00:00", flag, "2026-11-11T09:00:00"]
    with pytest.raises(SystemExit) as exit_info:
        _run(argv, "")
    assert exit_info.value.code == 2
    assert "needs an offset" in capsys.readouterr().err


def test_main_reads_the_real_standard_streams(monkeypatch, capsys):
    monkeypatch.setattr(sys, "stdin", io.StringIO('{"timestamp": "2026-11-10T09:00:00Z"}\n'))
    assert rebase.main(["newest"]) == 0
    assert capsys.readouterr().out == "2026-11-10T09:00:00+00:00\n"


def test_the_module_runs_main(monkeypatch, capsys):
    monkeypatch.setattr(sys, "argv", ["rebase"])
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)
        with pytest.raises(SystemExit) as exit_info:
            runpy.run_module("vortex_pipeline.rebase", run_name="__main__")
    assert exit_info.value.code == 2
    assert "the following arguments are required" in capsys.readouterr().err
