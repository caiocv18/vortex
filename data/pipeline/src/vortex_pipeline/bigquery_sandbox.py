"""Load the pipeline's files into the BigQuery sandbox and prove who can read what (scenario A).

Loads always append. WRITE_TRUNCATE (``bq load --replace``) and CREATE OR REPLACE drop the policy tags of
the destination table, so this module has no way to ask for them; the tables come from Terraform, so a
load never creates one either. Every query carries ``maximum_bytes_billed``: above it the query fails
without being billed.

    python -m vortex_pipeline.bigquery_sandbox load --output-dir DIR --project INGEST [--impersonate SA]
    python -m vortex_pipeline.bigquery_sandbox prove --ingest-project INGEST \\
        --analytics-project ANALYTICS --analyst SA
"""

from __future__ import annotations

import argparse
import re
import sys
from collections.abc import Sequence
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
    return bigquery.QueryJobConfig(maximum_bytes_billed=MAX_BYTES_BILLED, use_legacy_sql=False)


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
    for table, readable in result["readable"].items():
        print(f"{table}, read by the analyst: {'READABLE, access is too wide' if readable else 'refused'}")
    return 1 if any(result["readable"].values()) else 0


if __name__ == "__main__":
    sys.exit(main())
