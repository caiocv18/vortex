"""End to end on the DirectRunner, over the fixtures: counts, schemas, personal data and the join."""

from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path

import pytest
from conftest import FIXTURES, TEST_KEY, TEST_KEY_LOADER, read_ndjson, schema

from vortex_pipeline.pipeline import TABLES, main, run
from vortex_pipeline.pseudonymize import USER, pseudonym

STARTED = datetime(2026, 11, 16, 15, 0, tzinfo=UTC)
# Everything in the fixtures that identifies a person or is a secret; none of it may reach a curated table
PERSONAL_DATA = (
    "alice",
    "bob",
    "carol",
    "dave",
    "@example.com",
    "203.0.113.10",
    "203.0.113.77",
    "203.0.113.200",
    "198.51.100.23",
    "2001:db8:85a3::8a2e:370:7334",
    "2001:db8:85a3::1",
    "firefox",
    "chrome",
    "safari",
    "sess-",
    "5b0c7a52-",
    "venda balcão",
)
SECRETS = ("9d8c7b6a-5f4e", "0a9b8c7d-6e5f", "tok-should-not-survive")


def options(output: Path, *extra: str) -> list[str]:
    return [
        "--runner=DirectRunner",
        f"--movements_input={FIXTURES / 'movements.ndjson'}",
        f"--user_events_input={FIXTURES / 'user_events.ndjson'}",
        f"--output_dir={output}",
        "--source_timezone=America/Sao_Paulo",
        "--run_id=test-run",
        *extra,
    ]


@pytest.fixture(scope="module")
def output(tmp_path_factory) -> Path:
    folder = tmp_path_factory.mktemp("out")
    run(options(folder), key_loader=TEST_KEY_LOADER, now=STARTED)
    return folder


def test_every_valid_line_becomes_a_row(output):
    counts = {table: len(read_ndjson(output / f"{table}.ndjson")) for table in TABLES}
    assert counts == {
        "raw_movements": 8,
        "curated_movements": 8,
        "raw_user_events": 10,
        "curated_user_events": 10,
    }


def test_bad_lines_go_to_quarantine_with_a_reason(output):
    rejected = read_ndjson(output / "quarantine" / "rejected.ndjson")
    assert sorted((r["kind"], r["error"]) for r in rejected) == [
        ("movements", "eventType is not MOVIMENTO_ESTOQUE"),
        ("movements", "not JSON (Expecting property name enclosed in double quotes)"),
        ("movements", "timestamp is missing"),
        ("movements", "tipoMovimentacao must be ENTRADA or SAIDA"),
        ("user_events", "eventId is missing"),
        ("user_events", "eventType is not a known authorization event"),
        ("user_events", "not a JSON object"),
    ]
    assert all(r["run_id"] == "test-run" for r in rejected)


@pytest.mark.parametrize("table", TABLES)
def test_rows_match_the_bigquery_schema(output, table):
    fields = schema(table)
    for row in read_ndjson(output / f"{table}.ndjson"):
        assert set(row) == {field["name"] for field in fields}
        for field in fields:
            value = row[field["name"]]
            if value is None:
                assert field["mode"] == "NULLABLE", field["name"]
                continue
            match field["type"]:
                case "STRING":
                    assert isinstance(value, str)
                case "INT64":
                    assert isinstance(value, int)
                    assert not isinstance(value, bool)
                case "NUMERIC":
                    assert Decimal(value).is_finite()
                case "TIMESTAMP":
                    assert datetime.fromisoformat(value).utcoffset().total_seconds() == 0
                case other:
                    pytest.fail(f"no check for type {other}")


@pytest.mark.parametrize("table", ["curated_movements", "curated_user_events"])
def test_no_personal_data_reaches_a_curated_table(output, table):
    text = (output / f"{table}.ndjson").read_text(encoding="utf-8").lower()
    for personal in PERSONAL_DATA:
        assert personal not in text, personal


def test_no_secret_reaches_any_table(output):
    for table in TABLES:
        text = (output / f"{table}.ndjson").read_text(encoding="utf-8")
        for secret in SECRETS:
            assert secret not in text, (table, secret)


def test_movements_and_logins_join_by_pseudonym(output):
    movements = read_ndjson(output / "curated_movements.ndjson")
    logins = [
        r for r in read_ndjson(output / "curated_user_events.ndjson") if r["event_type"] == "user.logged_in"
    ]
    joined = {r["user_key"] for r in movements} & {r["user_key"] for r in logins}
    assert joined == {pseudonym(TEST_KEY, USER, name) for name in ("alice", "bob", "carol")}


def test_rows_carry_the_lineage_of_the_run(output):
    row = read_ndjson(output / "curated_movements.ndjson")[0]
    assert row["run_id"] == "test-run"
    assert row["ingested_at"] == "2026-11-16T15:00:00.000000+00:00"
    assert row["source_file"] == "movements.ndjson"
    assert row["hmac_key_version"] == "test-1"


def test_local_date_times_are_read_in_the_source_zone(output):
    first = next(r for r in read_ndjson(output / "raw_movements.ndjson") if r["event_id"].endswith("5b03"))
    assert first["event_ts"] == "2026-11-10T14:30:45.250000+00:00"


def test_the_required_options_are_checked_first(tmp_path):
    with pytest.raises(ValueError, match="--movements_input, --user_events_input, --output_dir"):
        run([], key_loader=TEST_KEY_LOADER)


def test_an_unknown_zone_is_refused(tmp_path):
    argv = options(tmp_path, "--source_timezone=Mars/Olympus")
    with pytest.raises(ValueError, match="source_timezone"):
        run(argv, key_loader=TEST_KEY_LOADER)


def test_main_reports_a_bad_command_line_without_a_traceback():
    with pytest.raises(SystemExit, match="vortex_pipeline: missing"):
        main(["--output_dir=/tmp/x"])
