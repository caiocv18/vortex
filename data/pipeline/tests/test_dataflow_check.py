"""The comparison of a Dataflow run with a local one, against a fake BigQuery client."""

import argparse
import io
import json
from datetime import UTC, datetime
from decimal import Decimal
from types import SimpleNamespace

import pytest
from conftest import schema

from vortex_pipeline import dataflow_check as check
from vortex_pipeline.bigquery_sandbox import DATASET_OF, MAX_BYTES_BILLED

PROJECT = "vortex-ingest-abc123"
RUN = "dataflow-20261125t120000z"
CURATED = [table for table, dataset in DATASET_OF.items() if dataset == "vortex_curated"]
LOCAL = {"STRING": "x", "INT64": 1, "NUMERIC": "12.50", "TIMESTAMP": "2026-11-10T14:30:45.250000+00:00"}
REMOTE = {**LOCAL, "NUMERIC": Decimal("12.5"), "TIMESTAMP": datetime(2026, 11, 10, 14, 30, 45, 250000, UTC)}
REJECTED = {"kind": "movements", "error": "not JSON", "line": "{", "run_id": "any"}


def row(table, values, **overrides):
    return {field["name"]: values[field["type"]] for field in schema(table)} | overrides


class FakeClient:
    def __init__(self, counts=1, views=('{"b":1}', '{"a":1}')):
        self.counts = counts
        self.views = views
        self.queries = []

    def query(self, sql, job_config, location):
        self.queries.append((sql, job_config, location))
        return SimpleNamespace(result=lambda: iter(self.answer(sql)))

    def answer(self, sql):
        if "TO_JSON_STRING" in sql:
            return [{"j": text} for text in self.views]
        if "COUNT(*)" in sql:
            return [{"n": self.counts}]
        (table,) = (name for name in CURATED if f".{name}`" in sql)
        return [row(table, REMOTE, run_id=RUN, source_file="gs://landing/fixtures/movements.ndjson")]


def local_rows(**overrides):
    rows = {table: [row(table, LOCAL)] for table in DATASET_OF}
    return {**rows, "rejected": [REJECTED], "quarantine": [REJECTED | {"run_id": RUN}], **overrides}


def failed(rows, client=None):
    return [line for line, passed in check.verify(client or FakeClient(), PROJECT, RUN, rows) if not passed]


def test_a_run_equal_to_the_local_one_passes():
    client = FakeClient()
    assert failed(local_rows(), client) == []
    for _, config, location in client.queries:
        assert [(p.name, p.value) for p in config.query_parameters] == [("run_id", RUN)]
        assert config.maximum_bytes_billed == MAX_BYTES_BILLED
        assert location == "us-central1"
    assert all(sql.endswith("WHERE run_id = @run_id") for sql, _, _ in client.queries)
    assert not any(RUN in sql for sql, _, _ in client.queries)


def test_the_raw_tables_are_only_counted():
    client = FakeClient()
    check.verify(client, PROJECT, RUN, local_rows())
    assert all("COUNT(*)" in sql for sql, _, _ in client.queries if "vortex_raw" in sql)


@pytest.mark.parametrize("counts", [0, 2])
def test_another_number_of_rows_fails(counts):
    lines = failed(local_rows(), FakeClient(counts=counts))
    assert len(lines) == 4
    assert lines[0] == f"raw_movements: {counts} rows of the run, 1 local"


def test_an_empty_local_run_proves_nothing():
    rows = local_rows(raw_movements=[], rejected=[], quarantine=[])
    lines = failed(rows, FakeClient(counts=0))
    assert "raw_movements: 0 rows of the run, 0 local" in lines
    assert "rejected: 0 in the run, 0 local, 0 differ" in lines


@pytest.mark.parametrize(
    "change",
    [{"sale_value": "12.51"}, {"event_ts": "2026-11-10T14:30:45.250001+00:00"}, {"user_key": None}],
)
def test_one_changed_value_fails(change):
    rows = local_rows(curated_movements=[row("curated_movements", LOCAL, **change)])
    assert failed(rows) == ["curated_movements: 2 rows differ from the local run"]


def test_a_repeated_row_is_not_lost():
    rows = local_rows(curated_movements=[row("curated_movements", LOCAL)] * 2)
    assert "curated_movements: 1 rows differ from the local run" in failed(rows)


def test_another_rejected_line_fails():
    assert failed(local_rows(quarantine=[REJECTED | {"line": "["}])) == [
        "rejected: 1 in the run, 1 local, 2 differ"
    ]


def test_reads_the_rows_by_name():
    rows = check.read_rows(['raw_movements\t{"a":"x\\ty"}\n', 'quarantine\t{"b":1}'])
    assert rows["raw_movements"] == [{"a": "x\ty"}]
    assert rows["quarantine"] == [{"b": 1}]
    assert rows["rejected"] == []


def test_an_unknown_name_is_refused_without_echoing_the_line():
    with pytest.raises(ValueError, match="stdin line 1") as error:
        check.read_rows(['alice@example.com\t{"a":1}'])
    assert "alice" not in str(error.value)


@pytest.mark.parametrize("value", ["", "a/b", "../x", "a b", "-a", "x" * 65])
def test_run_ids_are_validated(value):
    with pytest.raises(argparse.ArgumentTypeError):
        check.run_id(value)


def test_the_hash_ignores_the_order_and_sees_the_content():
    one = check.view_hashes(FakeClient(), PROJECT)
    assert set(one) == set(check.VIEWS)
    assert one == check.view_hashes(FakeClient(views=('{"a":1}', '{"b":1}')), PROJECT)
    assert one != check.view_hashes(FakeClient(views=('{"a":1}', '{"b":2}')), PROJECT)
    assert one != check.view_hashes(FakeClient(views=('{"a":1}', '{"b":1}', '{"b":1}')), PROJECT)


@pytest.fixture
def client(monkeypatch):
    fake = FakeClient()
    monkeypatch.setattr(check, "credentials", lambda service_account: service_account)

    def made(**kwargs):
        fake.made_with = kwargs
        return fake

    monkeypatch.setattr(check.bigquery, "Client", made)
    return fake


def stdin(rows):
    return io.StringIO("".join(f"{name}\t{json.dumps(r)}\n" for name, group in rows.items() for r in group))


VERIFY = ["verify", "--run-id", RUN, "--project", PROJECT, "--impersonate", "launcher@p"]


@pytest.mark.parametrize(("counts", "code", "mark"), [(1, 0, "ok  "), (3, 1, "FAIL")])
def test_main_verify(client, monkeypatch, capsys, counts, code, mark):
    client.counts = counts
    monkeypatch.setattr(check.sys, "stdin", stdin(local_rows()))
    assert check.main(VERIFY) == code
    assert (client.made_with["project"], client.made_with["credentials"]) == (PROJECT, "launcher@p")
    assert f"{mark} raw_movements: {counts} rows of the run, 1 local" in capsys.readouterr().out


@pytest.mark.parametrize(("views", "code"), [(('{"a":1}',), 0), ((), 1)])
def test_main_views(client, capsys, views, code):
    client.views = views
    assert check.main(["views", "--project", PROJECT, "--impersonate", "analyst@p"]) == code
    assert (client.made_with["project"], client.made_with["credentials"]) == (PROJECT, "analyst@p")
    out = capsys.readouterr().out
    assert f"v_daily_sales: {len(views)} rows, sha256 " in out
    assert ("EMPTY" in out) == (code == 1)
