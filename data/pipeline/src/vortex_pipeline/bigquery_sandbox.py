"""Load the pipeline's files into the BigQuery sandbox and prove who can read what (scenario A).

Loads always append. WRITE_TRUNCATE (``bq load --replace``) and CREATE OR REPLACE drop the policy tags of
the destination table, so this module has no way to ask for them; the tables come from Terraform, so a
load never creates one either. Every query carries ``maximum_bytes_billed``: above it the query fails
without being billed.

    python -m vortex_pipeline.bigquery_sandbox load --output-dir DIR --project INGEST [--impersonate SA]
    python -m vortex_pipeline.bigquery_sandbox prove --ingest-project INGEST \\
        --analytics-project ANALYTICS --analyst SA --launcher SA --policy-tags data/policy_tags.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections.abc import Mapping, Sequence
from pathlib import Path
from typing import Any

import google.auth
from google.api_core import exceptions
from google.auth import impersonated_credentials
from google.cloud import bigquery

DATASET_OF = {
    "raw_movements": "vortex_raw",
    "raw_user_events": "vortex_raw",
    "curated_movements": "vortex_curated",
    "curated_user_events": "vortex_curated",
}
ANALYTICS_DATASET = "vortex_analytics"
LOCATION = "us-central1"
MAX_BYTES_BILLED = 100 * 1024 * 1024
SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]
# Only what a project id can be, so an argument can't change the SQL it is placed in
_PROJECT_ID = re.compile(r"[a-z][a-z0-9-]{4,28}[a-z0-9]")
_COLUMN = re.compile(r"[a-z][a-z0-9_]*")


def project_id(value: str) -> str:
    if not _PROJECT_ID.fullmatch(value):
        raise argparse.ArgumentTypeError(f"not a Google Cloud project id: {value!r}")
    return value


def credentials(service_account: str | None = None) -> Any:
    """Application Default Credentials, or a short-lived token of the service account they impersonate.

    The organization forbids service account keys, so impersonation (Token Creator on the account) is the
    only way to act as one.
    """
    source, _ = google.auth.default(scopes=SCOPES)
    if not service_account:
        return source
    return impersonated_credentials.Credentials(
        source_credentials=source,
        target_principal=service_account,
        target_scopes=SCOPES,
        lifetime=900,
    )


def load_job_config() -> bigquery.LoadJobConfig:
    return bigquery.LoadJobConfig(
        source_format=bigquery.SourceFormat.NEWLINE_DELIMITED_JSON,
        write_disposition=bigquery.WriteDisposition.WRITE_APPEND,
        create_disposition=bigquery.CreateDisposition.CREATE_NEVER,
        ignore_unknown_values=False,
    )


def query_config() -> bigquery.QueryJobConfig:
    # No cache: a result stored before a policy tag would still answer
    return bigquery.QueryJobConfig(
        maximum_bytes_billed=MAX_BYTES_BILLED, use_legacy_sql=False, use_query_cache=False
    )


def load_outputs(client: Any, output_dir: Path, project: str) -> dict[str, int]:
    """Appends each table's file. A missing or empty file is skipped: a load of nothing is an error."""
    loaded = {}
    for table, dataset in DATASET_OF.items():
        path = output_dir / f"{table}.ndjson"
        if not path.is_file() or path.stat().st_size == 0:
            loaded[table] = 0
            continue
        with path.open("rb") as source:
            job = client.load_table_from_file(
                source, f"{project}.{dataset}.{table}", job_config=load_job_config(), location=LOCATION
            )
        job.result()
        loaded[table] = int(job.output_rows or 0)
    return loaded


def count_rows(client: Any, project: str) -> dict[str, int]:
    counts = {}
    for table, dataset in DATASET_OF.items():
        sql = f"SELECT COUNT(*) AS n FROM `{project_id(project)}.{dataset}.{table}`"  # noqa: S608 (validated id)
        rows = client.query(sql, job_config=query_config(), location=LOCATION).result()
        counts[table] = int(next(iter(rows))["n"])
    return counts


def prove(analyst: Any, ingest_project: str, analytics_project: str) -> dict[str, Any]:
    """As the analyst: the authorized view answers, the base tables refuse."""
    view = f"`{project_id(analytics_project)}.{ANALYTICS_DATASET}.v_user_activity`"
    sql = (
        "SELECT user_key, movements, units_out, logins, last_login_at "  # noqa: S608 (validated id)
        f"FROM {view} ORDER BY movements DESC, user_key LIMIT 10"
    )
    activity = [
        dict(row.items()) for row in analyst.query(sql, job_config=query_config(), location=LOCATION).result()
    ]
    readable = {}
    for table in ("curated_movements", "raw_user_events"):
        base = f"`{project_id(ingest_project)}.{DATASET_OF[table]}.{table}`"
        try:
            analyst.query(
                f"SELECT COUNT(*) AS n FROM {base}",  # noqa: S608 (validated id)
                job_config=query_config(),
                location=LOCATION,
            ).result()
            readable[table] = True
        except exceptions.Forbidden:
            readable[table] = False
    return {"activity": activity, "readable": readable}


def policy_tags(path: Path) -> dict[str, list[str]]:
    """The tagged columns of each raw table, from the file Terraform reads too."""
    tagged = json.loads(path.read_text(encoding="utf-8"))
    for table, columns in tagged.items():
        if table not in DATASET_OF or not columns or not all(_COLUMN.fullmatch(c) for c in columns):
            raise ValueError(f"{path}: {table} is not a known table with a list of column names")
    if not tagged:
        raise ValueError(f"{path}: no table")
    return tagged


def is_policy_tag_refusal(error: exceptions.Forbidden, column: str) -> bool:
    """Whether BigQuery refused this column because of its policy tag.

    A dataset or job permission that is missing is a 403 as well, and would make the proof pass for the
    wrong reason, so the message must name both the policy tag and the column.
    """
    message = str(error).lower()
    return "policy tag" in message and re.search(rf"\b{re.escape(column)}\b", message) is not None


def _read(client: Any, select: str, project: str, table: str) -> str | exceptions.Forbidden:
    """ "readable", or the refusal. No value leaves this function."""
    base = f"`{project_id(project)}.{DATASET_OF[table]}.{table}`"
    try:
        client.query(
            f"SELECT {select} FROM {base} LIMIT 1",  # noqa: S608 (validated names)
            job_config=query_config(),
            location=LOCATION,
        ).result()
    except exceptions.Forbidden as error:
        return error
    return "readable"


def prove_policy_tags(
    launcher: Any, operator: Any, project: str, tagged: Mapping[str, Sequence[str]]
) -> list[tuple[str, bool]]:
    """One line per check, and whether it passed.

    The launcher writes the raw tables, so the dataset lets it read: only the policy tag can refuse it.
    The operator holds Fine-Grained Reader, and reads every tagged column.
    """
    checks = []
    for table, columns in tagged.items():
        for column in columns:
            got = _read(launcher, column, project, table)
            if got == "readable":
                outcome, passed = "READABLE, the policy tag is missing", False
            elif is_policy_tag_refusal(got, column):
                outcome, passed = "refused by the policy tag", True
            else:
                outcome, passed = "REFUSED FOR ANOTHER REASON, proves nothing", False
            checks.append((f"{table}.{column}, read by the launcher: {outcome}", passed))
        rest = _read(launcher, f"* EXCEPT ({', '.join(columns)})", project, table) == "readable"
        checks.append((f"{table}, other columns read by the launcher: {'ok' if rest else 'REFUSED'}", rest))
        whole = _read(operator, ", ".join(columns), project, table) == "readable"
        checks.append(
            (f"{table}, tagged columns read by the operator: {'ok' if whole else 'REFUSED'}", whole)
        )
    return checks


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m vortex_pipeline.bigquery_sandbox")
    commands = parser.add_subparsers(dest="command", required=True)
    load = commands.add_parser("load", help="append the pipeline's files to the ingestion tables")
    load.add_argument("--output-dir", type=Path, required=True)
    load.add_argument("--project", type=project_id, required=True, help="ingestion project id")
    load.add_argument("--impersonate", help="service account that runs the loads (pipeline-launcher)")
    check = commands.add_parser("prove", help="count the rows and check what the analyst can read")
    check.add_argument("--ingest-project", type=project_id, required=True)
    check.add_argument("--analytics-project", type=project_id, required=True)
    check.add_argument("--analyst", required=True, help="service account of the analyst (analyst-reader)")
    check.add_argument("--launcher", required=True, help="service account of the loads (pipeline-launcher)")
    check.add_argument("--policy-tags", type=Path, required=True, help="data/policy_tags.json")
    args = parser.parse_args(argv)

    if args.command == "load":
        client = bigquery.Client(
            project=args.project, credentials=credentials(args.impersonate), location=LOCATION
        )
        for table, rows in load_outputs(client, args.output_dir, args.project).items():
            print(f"{table}: {rows} rows appended")
        return 0

    operator = bigquery.Client(project=args.ingest_project, credentials=credentials(), location=LOCATION)
    for table, rows in count_rows(operator, args.ingest_project).items():
        print(f"{table}: {rows} rows")
    analyst = bigquery.Client(
        project=args.analytics_project, credentials=credentials(args.analyst), location=LOCATION
    )
    result = prove(analyst, args.ingest_project, args.analytics_project)
    print("v_user_activity, read by the analyst:")
    for row in result["activity"]:
        print("  " + ", ".join(f"{column}={value}" for column, value in row.items()))
    if not result["activity"]:
        # Partitions expire by event date, so an old sample leaves the view empty; that proves nothing
        print("  v_user_activity is EMPTY: reload a sample (data/scripts/run-local.sh --rebase-to-today)")
    for table, readable in result["readable"].items():
        print(f"{table}, read by the analyst: {'READABLE, access is too wide' if readable else 'refused'}")
    launcher = bigquery.Client(
        project=args.ingest_project, credentials=credentials(args.launcher), location=LOCATION
    )
    checks = prove_policy_tags(launcher, operator, args.ingest_project, policy_tags(args.policy_tags))
    for line, _ in checks:
        print(line)
    failed = any(result["readable"].values()) or not all(passed for _, passed in checks)
    return 1 if failed or not result["activity"] else 0


if __name__ == "__main__":
    sys.exit(main())
