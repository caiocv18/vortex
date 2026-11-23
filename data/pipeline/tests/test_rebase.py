"""Rebasing a captured sample: one delta for every file, each date-time kept in the shape it was written."""

import json
import runpy
import sys
import tempfile
import warnings
from datetime import UTC, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

import pytest
from conftest import FIXTURES, read_ndjson

from vortex_pipeline import rebase
from vortex_pipeline.parse import Context, parse_movement, parse_user_event

SP = ZoneInfo("America/Sao_Paulo")
NOW = datetime(2026, 11, 23, 15, 0, tzinfo=UTC)


def _write(path, *events):
    path.write_text(
        "".join(f"{e if isinstance(e, str) else json.dumps(e)}\n" for e in events), encoding="utf-8"
    )
    return path


def test_the_newest_event_lands_at_now_and_the_intervals_stay(tmp_path):
    movements = _write(
        tmp_path / "movements.ndjson",
        {"timestamp": "2026-11-10T09:00:00", "dataMovimento": "2026-11-10T09:00:00"},
        {"timestamp": "2026-11-10T10:30:00.123456789"},
    )
    events = _write(
        tmp_path / "user_events.ndjson",
        {"timestamp": "2026-11-10T12:00:00Z", "lastLogin": "2026-11-10T12:00:00Z", "expiresAt": None},
    )
    delta = rebase.rebase([movements, events], tmp_path / "out", NOW, SP)

    # 10:30 in São Paulo (13:30Z) is newer than the account event's 12:00Z
    assert delta == NOW - datetime(2026, 11, 10, 13, 30, 0, 123456, tzinfo=UTC)
    moved = read_ndjson(tmp_path / "out" / "movements.ndjson")
    assert moved[1]["timestamp"] == "2026-11-23T12:00:00"  # wall clock of São Paulo, still no offset
    assert moved[0]["timestamp"] == moved[0]["dataMovimento"]
    later = datetime.fromisoformat(moved[1]["timestamp"]) - datetime.fromisoformat(moved[0]["timestamp"])
    assert later == timedelta(hours=1, minutes=30, microseconds=123456)
    account = read_ndjson(tmp_path / "out" / "user_events.ndjson")[0]
    assert account["timestamp"] == account["lastLogin"]
    assert datetime.fromisoformat(account["timestamp"]).utcoffset() == timedelta(0)
    assert account["expiresAt"] is None


def test_a_jackson_array_stays_an_array(tmp_path):
    source = _write(tmp_path / "m.ndjson", {"timestamp": [2026, 11, 22, 12, 0]})
    rebase.rebase([source], tmp_path / "out", datetime(2026, 11, 23, 15, 0, tzinfo=UTC), SP)
    assert read_ndjson(tmp_path / "out" / "m.ndjson")[0]["timestamp"] == [2026, 11, 23, 12, 0, 0, 0]


def test_what_is_not_a_date_time_passes_untouched(tmp_path):
    lines = [
        "not json",
        "[1, 2]",
        json.dumps({"timestamp": "2026-11-10T09:00:00", "other": "2026-11-10T09:00:00"}),
        json.dumps({"timestamp": "yesterday", "dataMovimento": 1700000000, "lastLogin": [1, 2]}),
        "",
    ]
    source = _write(tmp_path / "m.ndjson", *lines)
    rebase.rebase([source], tmp_path / "out", datetime(2026, 11, 11, 12, 0, tzinfo=UTC), SP)
    out = (tmp_path / "out" / "m.ndjson").read_text(encoding="utf-8").splitlines()
    assert out[:2] == lines[:2]
    assert json.loads(out[2]) == {"timestamp": "2026-11-11T09:00:00", "other": "2026-11-10T09:00:00"}
    assert json.loads(out[3]) == json.loads(lines[3])
    assert out[4] == ""


def test_a_sample_without_a_readable_timestamp_is_copied_as_is(tmp_path):
    source = _write(tmp_path / "m.ndjson", {"timestamp": "never"}, "{oops")
    assert rebase.rebase([source], tmp_path / "out", NOW, SP) == timedelta(0)
    assert (tmp_path / "out" / "m.ndjson").read_text(encoding="utf-8") == source.read_text(encoding="utf-8")


def test_the_rebased_fixtures_still_parse_into_today():
    """Every fixture line the pipeline accepts before rebasing is still accepted, now dated today."""
    with tempfile.TemporaryDirectory() as directory:
        out = Path(directory)
        files = [FIXTURES / "movements.ndjson", FIXTURES / "user_events.ndjson"]
        rebase.rebase(files, out, NOW, SP)
        context = Context(source_file="f", run_id="r", ingested_at="2026-11-23T15:00:00Z", source_tz=SP)
        for name, parse in (("movements.ndjson", parse_movement), ("user_events.ndjson", parse_user_event)):
            before = (FIXTURES / name).read_text(encoding="utf-8").splitlines()
            after = (out / name).read_text(encoding="utf-8").splitlines()
            assert len(before) == len(after)
            accepted = 0
            for old, new in zip(before, after, strict=True):
                try:
                    parse(old, context)
                except ValueError:
                    continue
                row = parse(new, context)
                accepted += 1
                assert row["event_ts"] <= "2026-11-23T15:00:00.000000Z"
                assert row["event_ts"] >= "2026-11-20"
            assert accepted


def test_main_rebases_and_reports(tmp_path, capsys):
    source = _write(tmp_path / "m.ndjson", {"timestamp": "2026-11-10T09:00:00Z"})
    code = rebase.main(
        [str(source), "--output-dir", str(tmp_path / "o"), "--now", "2026-11-11T09:00:00+00:00"]
    )
    assert code == 0
    assert "moved 1 files by 1 day, 0:00:00" in capsys.readouterr().out
    assert read_ndjson(tmp_path / "o" / "m.ndjson")[0]["timestamp"] == "2026-11-11T09:00:00+00:00"


def test_main_uses_the_clock_without_now(tmp_path):
    source = _write(tmp_path / "m.ndjson", {"timestamp": "2026-11-10T09:00:00Z"})
    before = datetime.now(UTC)
    assert rebase.main([str(source), "--output-dir", str(tmp_path / "o")]) == 0
    moved = datetime.fromisoformat(read_ndjson(tmp_path / "o" / "m.ndjson")[0]["timestamp"])
    assert before <= moved <= datetime.now(UTC)


def test_main_refuses_a_now_without_an_offset(tmp_path, capsys):
    source = _write(tmp_path / "m.ndjson", {"timestamp": "2026-11-10T09:00:00Z"})
    with pytest.raises(SystemExit) as exit_info:
        rebase.main([str(source), "--output-dir", str(tmp_path / "o"), "--now", "2026-11-11T09:00:00"])
    assert exit_info.value.code == 2
    assert "--now needs an offset" in capsys.readouterr().err


def test_the_module_runs_main(monkeypatch, capsys):
    monkeypatch.setattr(sys, "argv", ["rebase"])
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)
        with pytest.raises(SystemExit) as exit_info:
            runpy.run_module("vortex_pipeline.rebase", run_name="__main__")
    assert exit_info.value.code == 2
    assert "the following arguments are required" in capsys.readouterr().err
