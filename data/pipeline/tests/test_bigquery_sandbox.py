"""The sandbox loader and the access proof, against a fake BigQuery client."""

import argparse
import json
from types import SimpleNamespace

import pytest
from conftest import SCHEMAS, column_names, schema
from google.api_core import exceptions
from google.cloud import bigquery

from vortex_pipeline import bigquery_sandbox as sandbox

POLICY_TAGS = SCHEMAS.parent / "policy_tags.json"


class FakeJob:
    def __init__(self, rows=None, error=None, output_rows=None):
        self._rows = rows or []
        self._error = error
        self.output_rows = output_rows

    def result(self):
        if self._error:
            raise self._error
        return iter(self._rows)


def refusal(column):
    return exceptions.Forbidden(
        "Access Denied: BigQuery BigQuery: User has neither fine-grained reader nor masked get permission to "
        f'get data protected by policy tag "vortex-pii : pii" on column p.vortex_raw.t.{column}.'
    )


class FakeClient:
    def __init__(self, forbidden=(), counts=3, activity=True, tagged=()):
        self.forbidden = forbidden
        self.counts = counts
        self.activity = activity
        self.tagged = tagged
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
        columns = sql.removeprefix("SELECT ").split(" FROM ")[0]
        if not columns.startswith("*"):
            for column in columns.split(", "):
                if column in self.tagged:
                    return FakeJob(error=refusal(column))
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


LAUNCHER = "pipeline-launcher@vortex-ingest-abc123.iam.gserviceaccount.com"
ALL_TAGGED = {column for columns in sandbox.policy_tags(POLICY_TAGS).values() for column in columns}


@pytest.fixture
def fake_clients(monkeypatch):
    clients = []

    def make(project, credentials, location):
        client = FakeClient(
            forbidden=("vortex_curated.", "vortex_raw.") if "analytics" in project else (),
            tagged=ALL_TAGGED if credentials.sa == LAUNCHER else (),
        )
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
    "--launcher",
    LAUNCHER,
    "--policy-tags",
    str(POLICY_TAGS),
]


def test_main_prove(fake_clients, capsys):
    code = sandbox.main(PROVE)
    out = capsys.readouterr().out
    assert code == 0
    assert "curated_movements, read by the analyst: refused" in out
    assert "raw_movements: 3 rows" in out
    assert out.count("refused by the policy tag") == 10
    assert "raw_user_events, tagged columns read by the operator: ok" in out


def test_main_prove_fails_on_a_column_without_its_tag(fake_clients, monkeypatch, capsys):
    monkeypatch.setattr(f"{__name__}.ALL_TAGGED", ALL_TAGGED - {"user_email"})
    assert sandbox.main(PROVE) == 1
    assert "raw_user_events.user_email, read by the launcher: READABLE" in capsys.readouterr().out


def failures(launcher, operator):
    tagged = {"raw_movements": ["user_id", "metadata"]}
    checks = sandbox.prove_policy_tags(launcher, operator, "vortex-ingest-abc123", tagged)
    return [line for line, passed in checks if not passed]


def test_every_tagged_column_is_refused_and_the_operator_reads_them():
    launcher = FakeClient(tagged={"user_id", "metadata"})
    assert failures(launcher, FakeClient()) == []
    assert any("* EXCEPT (user_id, metadata)" in sql for sql, _, _ in launcher.queries)
    assert all(config.use_query_cache is False for _, config, _ in launcher.queries)


def test_a_refusal_by_the_dataset_proves_nothing():
    lines = failures(FakeClient(forbidden=("vortex_raw.",)), FakeClient())
    assert len(lines) == 3
    assert "raw_movements.user_id, read by the launcher: REFUSED FOR ANOTHER REASON, proves nothing" in lines
    assert "raw_movements, other columns read by the launcher: REFUSED" in lines


def test_an_operator_without_fine_grained_reader_fails():
    lines = failures(FakeClient(tagged={"user_id", "metadata"}), FakeClient(tagged={"metadata"}))
    assert lines == ["raw_movements, tagged columns read by the operator: REFUSED"]


def test_a_refusal_must_name_the_tag_and_the_column():
    assert sandbox.is_policy_tag_refusal(refusal("user_id"), "user_id")
    assert not sandbox.is_policy_tag_refusal(refusal("user_id_hash"), "user_id")
    assert not sandbox.is_policy_tag_refusal(exceptions.Forbidden("no access to user_id"), "user_id")


@pytest.mark.parametrize(
    "content",
    [{}, {"users": ["a"]}, {"raw_movements": []}, {"raw_movements": ["user_id) FROM x --"]}],
)
def test_a_bad_policy_tags_file_is_refused(tmp_path, content):
    path = tmp_path / "policy_tags.json"
    path.write_text(json.dumps(content), encoding="utf-8")
    with pytest.raises(ValueError, match=r"policy_tags\.json"):
        sandbox.policy_tags(path)


@pytest.mark.parametrize("table", ["raw_movements", "raw_user_events"])
def test_every_personal_column_of_the_raw_schemas_is_tagged(table):
    tagged = set(sandbox.policy_tags(POLICY_TAGS)[table])
    personal = {field["name"] for field in schema(table) if field["description"].startswith("Personal data")}
    # Open text from the producer: attributes has carried the session id inside details
    open_text = {"attributes", "metadata"} & column_names(table)
    assert tagged == personal | open_text


def test_main_prove_fails_when_the_view_is_empty(monkeypatch, capsys):
    """Expired partitions empty the view; the proof must turn red instead of passing on nothing."""
    monkeypatch.setattr(
        sandbox, "credentials", lambda service_account=None: SimpleNamespace(sa=service_account)
    )

    def make(project, credentials, location):
        forbidden = ("vortex_curated.", "vortex_raw.") if "analytics" in project else ()
        tagged = ALL_TAGGED if credentials.sa == LAUNCHER else ()
        return FakeClient(forbidden=forbidden, activity=False, tagged=tagged)

    monkeypatch.setattr(sandbox.bigquery, "Client", make)
    code = sandbox.main(PROVE)
    out = capsys.readouterr().out
    assert code == 1
    assert "v_user_activity is EMPTY" in out
    assert "raw_user_events, read by the analyst: refused" in out
