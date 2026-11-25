"""The BigQuery sink of scenario B: its exact arguments, its graph and its options, all offline."""

import json
from contextlib import suppress
from functools import partial
from types import SimpleNamespace
from zoneinfo import ZoneInfo

import apache_beam as beam
import pytest
from apache_beam.io.gcp.bigquery import WriteToBigQuery
from apache_beam.io.gcp.bigquery_tools import RowAsDictJsonCoder
from apache_beam.options.pipeline_options import PipelineOptions
from conftest import FIXTURES, TEST_KEY_LOADER

from vortex_pipeline import pipeline
from vortex_pipeline.curate import curate_movement, curate_user_event
from vortex_pipeline.keys import load_key_from_secret_manager
from vortex_pipeline.parse import Context, InvalidRecordError, json_text, parse_movement, parse_user_event
from vortex_pipeline.pipeline import TABLES, build, main, run

PROJECT = "vortex-ingest-abc123"
TEMP = "gs://vortex-temp/tmp"
SECRET = f"projects/{PROJECT}/secrets/vortex-hmac-key/versions/latest"


class RecordingSink(beam.PTransform):
    Method = WriteToBigQuery.Method
    made: list = []  # noqa: RUF012 (reset by the fixture)

    def __init__(self, **kwargs):
        super().__init__()
        RecordingSink.made.append(kwargs)

    def expand(self, rows):
        return rows | beam.Map(json.dumps)


@pytest.fixture
def sinks(monkeypatch):
    RecordingSink.made = []
    monkeypatch.setattr(pipeline, "WriteToBigQuery", RecordingSink)
    return RecordingSink.made


def built(**overrides):
    options = PipelineOptions(["--runner=DirectRunner"])
    graph = beam.Pipeline(options=options)
    arguments = {
        "movements_input": str(FIXTURES / "movements.ndjson"),
        "user_events_input": str(FIXTURES / "user_events.ndjson"),
        "output_dir": "/tmp/landing",  # noqa: S108 (never written, the graph does not run)
        "run_id": "run-1",
        "ingested_at": "2026-11-25T00:00:00.000000+00:00",
        "source_timezone": "America/Sao_Paulo",
        "key_loader": TEST_KEY_LOADER,
        "bigquery_project": PROJECT,
        "temp_location": TEMP,
        **overrides,
    }
    build(graph, **arguments)
    return graph


def test_every_table_is_appended_and_never_created_or_given_a_schema(sinks):
    built()
    assert sinks == [
        {
            "table": f"{PROJECT}:{dataset}.{table}",
            "method": "FILE_LOADS",
            "write_disposition": "WRITE_APPEND",
            "create_disposition": "CREATE_NEVER",
            "schema": None,
            "custom_gcs_temp_location": TEMP,
        }
        for dataset, table in (
            ("vortex_raw", "raw_movements"),
            ("vortex_curated", "curated_movements"),
            ("vortex_raw", "raw_user_events"),
            ("vortex_curated", "curated_user_events"),
        )
    ]


def test_without_a_project_nothing_goes_to_bigquery(sinks):
    built(bigquery_project=None, temp_location=None)
    assert sinks == []


def test_the_real_sink_builds_without_any_network_call():
    graph = built().to_runner_api()
    labels = {transform.unique_name for transform in graph.components.transforms.values()}
    assert {f"Load {table}" for table in TABLES} <= labels
    assert "Write rejected" in labels
    assert not any(f"Write {table}" in labels for table in TABLES)


def test_the_quarantine_of_a_run_has_its_own_folder_under_quarantine(sinks, monkeypatch):
    written = []
    real = pipeline.WriteToText
    monkeypatch.setattr(
        pipeline, "WriteToText", lambda path, **kwargs: written.append(path) or real(path, **kwargs)
    )
    built()
    assert written == ["/tmp/landing/quarantine/run-1/rejected"]  # noqa: S108


def accepted(parser, name):
    """The in-memory records, before json_text: the sink gets these, not the local file's lines."""
    context = Context(name, "run-1", "2026-11-25T00:00:00.000000+00:00", ZoneInfo("America/Sao_Paulo"))
    for line in (FIXTURES / name).read_text(encoding="utf-8").splitlines():
        with suppress(InvalidRecordError):
            yield parser(line, context)


@pytest.mark.parametrize(
    ("parser", "curator", "name", "expected"),
    [
        (parse_movement, curate_movement, "movements.ndjson", 8),
        (parse_user_event, curate_user_event, "user_events.ndjson", 10),
    ],
)
def test_the_sink_writes_the_same_content_as_the_local_files(parser, curator, name, expected):
    raw = [record for record in accepted(parser, name) if record]
    assert len(raw) == expected
    key = TEST_KEY_LOADER()
    for record in [*raw, *(curator(row, key) for row in raw)]:
        assert json.loads(RowAsDictJsonCoder().encode(record)) == json.loads(json_text(record))


def argv(**overrides):
    options = {
        "movements_input": str(FIXTURES / "movements.ndjson"),
        "user_events_input": str(FIXTURES / "user_events.ndjson"),
        "output_dir": "gs://vortex-landing/",
        "temp_location": TEMP,
        "bigquery_project": PROJECT,
        "hmac_secret": SECRET,
        "run_id": "run-1",
        **overrides,
    }
    return [f"--{name}={value}" for name, value in options.items() if value is not None]


@pytest.mark.parametrize(
    ("overrides", "message"),
    [
        ({"hmac_secret": None}, "go together"),
        ({"bigquery_project": None}, "go together"),
        ({"hmac_secret": "vortex-hmac-key"}, "hmac_secret must be"),
        ({"hmac_secret": SECRET.replace("latest", "\u0663")}, "hmac_secret must be"),
        ({"temp_location": None}, "temp_location"),
        ({"temp_location": "gs://vortex-temp"}, "temp_location"),
        ({"output_dir": "/tmp/out"}, "output_dir"),  # noqa: S108
    ],
)
def test_options_that_would_only_fail_in_the_paid_job_are_refused(overrides, message):
    arguments = argv(**overrides)
    with pytest.raises(ValueError, match=message):
        run(arguments)


def test_a_bad_project_id_is_refused():
    arguments = argv(bigquery_project="Not A Project")
    with pytest.raises(SystemExit):
        run(arguments)


@pytest.fixture
def ran(monkeypatch):
    """Replaces the graph and the runner, and keeps what run() handed to them."""
    seen = SimpleNamespace(state="DONE", build=None)

    class FakePipeline:
        def __init__(self, options):
            pass

        def run(self):
            return SimpleNamespace(state=seen.state, wait_until_finish=lambda: None)

    monkeypatch.setattr(pipeline.beam, "Pipeline", FakePipeline)
    monkeypatch.setattr(pipeline, "build", lambda _, **kwargs: setattr(seen, "build", kwargs))
    return seen


def test_only_the_name_of_the_secret_travels_with_the_job(ran):
    run(argv())
    loader = ran.build["key_loader"]
    assert isinstance(loader, partial)
    assert (loader.func, loader.args, loader.keywords) == (load_key_from_secret_manager, (SECRET,), {})
    assert ran.build["bigquery_project"] == PROJECT
    assert ran.build["temp_location"] == TEMP
    assert ran.build["output_dir"] == "gs://vortex-landing"


def test_without_a_secret_the_key_comes_from_the_environment(ran):
    run(argv(bigquery_project=None, hmac_secret=None))
    assert ran.build["key_loader"] is pipeline.load_key_from_env


@pytest.mark.parametrize("state", ["CANCELLED", "FAILED", "UNKNOWN"])
def test_any_state_but_done_is_an_error(ran, state):
    ran.state = state
    arguments = argv()
    with pytest.raises(RuntimeError, match=f"run run-1 ended in state {state}"):
        run(arguments)


def test_main_reports_an_input_that_matches_no_file_without_a_traceback(tmp_path):
    with pytest.raises(SystemExit, match="vortex_pipeline: No files found"):
        main(
            [
                "--runner=DirectRunner",
                f"--movements_input={tmp_path / 'none.ndjson'}",
                f"--user_events_input={tmp_path / 'none.ndjson'}",
                f"--output_dir={tmp_path}",
            ]
        )
