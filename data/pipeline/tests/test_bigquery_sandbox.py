"""The sandbox loader and the access proof, against a fake BigQuery client."""

import argparse
from types import SimpleNamespace

import pytest
from google.api_core import exceptions
from google.cloud import bigquery

from vortex_pipeline import bigquery_sandbox as sandbox


class FakeJob:
    def __init__(self, rows=None, error=None, output_rows=None):
        self._rows = rows or []
        self._error = error
        self.output_rows = output_rows

    def result(self):
        if self._error:
            raise self._error
        return iter(self._rows)


class FakeClient:
    def __init__(self, forbidden=(), counts=3, activity=True):
        self.forbidden = forbidden
        self.counts = counts
        self.activity = activity
        self.loads = []
        self.queries = []

    def load_table_from_file(self, source, destination, job_config, location):
        lines = source.read().splitlines()
        self.loads.append((destination, job_config, location))
        return FakeJob(output_rows=len(lines))

    def query(self, sql, job_config, location):
        self.queries.append((sql, job_config, location))
        if any(name in sql for name in self.forbidden):
            return FakeJob(error=exceptions.Forbidden("Access Denied"))
        if "v_user_activity" in sql:
            if not self.activity:
                return FakeJob(rows=[])
            return FakeJob(rows=[{"user_key": "ab" * 32, "movements": 2, "units_out": 2, "logins": 2}])
        return FakeJob(rows=[{"n": self.counts}])


def test_loads_append_and_never_create_or_truncate(tmp_path):
    (tmp_path / "raw_movements.ndjson").write_text('{"a":1}\n{"a":2}\n', encoding="utf-8")
    (tmp_path / "curated_movements.ndjson").write_text("", encoding="utf-8")
    client = FakeClient()
    loaded = sandbox.load_outputs(client, tmp_path, "vortex-ingest-abc123")
    assert loaded == {
        "raw_movements": 2,
        "raw_user_events": 0,
        "curated_movements": 0,
        "curated_user_events": 0,
    }
    ((destination, config, location),) = client.loads
    assert destination == "vortex-ingest-abc123.vortex_raw.raw_movements"
    assert config.write_disposition == bigquery.WriteDisposition.WRITE_APPEND
    assert config.create_disposition == bigquery.CreateDisposition.CREATE_NEVER
    assert config.source_format == bigquery.SourceFormat.NEWLINE_DELIMITED_JSON
    assert location == "us-central1"


def test_every_query_has_a_byte_ceiling():
    client = FakeClient()
    assert sandbox.count_rows(client, "vortex-ingest-abc123")["curated_user_events"] == 3
    assert all(config.maximum_bytes_billed == sandbox.MAX_BYTES_BILLED for _, config, _ in client.queries)


def test_the_proof_passes_when_the_base_tables_refuse_the_analyst():
    client = FakeClient(forbidden=("vortex_curated.", "vortex_raw."))
    result = sandbox.prove(client, "vortex-ingest-abc123", "vortex-analytics-abc123")
    assert result["readable"] == {"curated_movements": False, "raw_user_events": False}
    assert result["activity"][0]["movements"] == 2


def test_the_proof_flags_a_base_table_the_analyst_can_read():
    result = sandbox.prove(FakeClient(), "vortex-ingest-abc123", "vortex-analytics-abc123")
    assert result["readable"] == {"curated_movements": True, "raw_user_events": True}


@pytest.mark.parametrize("value", ["Vortex", "a`b", "x.y", "abc", "vortex-ingest-abc123; DROP"])
def test_project_ids_are_validated_before_reaching_sql(value):
    with pytest.raises(argparse.ArgumentTypeError):
        sandbox.project_id(value)


def test_credentials_impersonate_only_when_asked(monkeypatch):
    source = object()
    monkeypatch.setattr(sandbox.google.auth, "default", lambda scopes: (source, "p"))
    made = []
    monkeypatch.setattr(
        sandbox.impersonated_credentials,
        "Credentials",
        lambda **kwargs: made.append(kwargs) or "impersonated",
    )
    assert sandbox.credentials() is source
    assert sandbox.credentials("analyst-reader@p.iam.gserviceaccount.com") == "impersonated"
    assert made[0]["target_principal"] == "analyst-reader@p.iam.gserviceaccount.com"
    assert made[0]["lifetime"] == 900


@pytest.fixture
def fake_clients(monkeypatch):
    clients = []

    def make(project, credentials, location):
        client = FakeClient(forbidden=("vortex_curated.", "vortex_raw.") if "analytics" in project else ())
        client.project = project
        clients.append(client)
        return client

    monkeypatch.setattr(
        sandbox, "credentials", lambda service_account=None: SimpleNamespace(sa=service_account)
    )
    monkeypatch.setattr(sandbox.bigquery, "Client", make)
    return clients


def test_main_load(tmp_path, fake_clients, capsys):
    (tmp_path / "raw_user_events.ndjson").write_text('{"a":1}\n', encoding="utf-8")
    code = sandbox.main(["load", "--output-dir", str(tmp_path), "--project", "vortex-ingest-abc123"])
    assert code == 0
    assert "raw_user_events: 1 rows appended" in capsys.readouterr().out


PROVE = [
    "prove",
    "--ingest-project",
    "vortex-ingest-abc123",
    "--analytics-project",
    "vortex-analytics-abc123",
    "--analyst",
    "analyst-reader@vortex-analytics-abc123.iam.gserviceaccount.com",
]


def test_main_prove(fake_clients, capsys):
    code = sandbox.main(PROVE)
    out = capsys.readouterr().out
    assert code == 0
    assert "curated_movements, read by the analyst: refused" in out
    assert "raw_movements: 3 rows" in out


def test_main_prove_fails_when_the_view_is_empty(monkeypatch, capsys):
    """Expired partitions empty the view; the proof must turn red instead of passing on nothing."""
    monkeypatch.setattr(
        sandbox, "credentials", lambda service_account=None: SimpleNamespace(sa=service_account)
    )

    def make(project, credentials, location):
        forbidden = ("vortex_curated.", "vortex_raw.") if "analytics" in project else ()
        return FakeClient(forbidden=forbidden, activity=False)

    monkeypatch.setattr(sandbox.bigquery, "Client", make)
    code = sandbox.main(PROVE)
    out = capsys.readouterr().out
    assert code == 1
    assert "v_user_activity is EMPTY" in out
    assert "raw_user_events, read by the analyst: refused" in out
