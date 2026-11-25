"""Check what a Dataflow run left in BigQuery against a local run of the same events (scenario B).

    ... | python -m vortex_pipeline.dataflow_check verify --run-id RUN --project INGEST --impersonate SA
    python -m vortex_pipeline.dataflow_check views --project ANALYTICS --impersonate SA

``verify`` reads the local run and the run's quarantine from stdin, one ``name<TAB>json`` per line, where
the name is a table, ``rejected`` (local) or ``quarantine`` (the run's). No row leaves this module: the
output has only counts and hashes.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from collections.abc import Iterable, Mapping, Sequence
from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

from google.cloud import bigquery

from vortex_pipeline.bigquery_sandbox import (
    ANALYTICS_DATASET,
    DATASET_OF,
    LOCATION,
    credentials,
    project_id,
    query_config,
)

VIEWS = ("v_movements", "v_user_events", "v_user_activity", "v_daily_sales")
LINEAGE = frozenset({"source_file", "run_id", "ingested_at"})
LOCAL_REJECTED, RUN_REJECTED = "rejected", "quarantine"
# Fixed, never an argument, like POLICY_TAGS: an argument could name any file
SCHEMAS = Path(__file__).resolve().parents[3] / "schemas"
_RUN_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9-]{0,63}")

Rows = dict[str, list[dict[str, Any]]]


def run_id(value: str) -> str:
    if not _RUN_ID.fullmatch(value):
        raise argparse.ArgumentTypeError(f"not a run id: {value!r}")
    return value


def read_rows(lines: Iterable[str]) -> Rows:
    rows: Rows = {name: [] for name in (*DATASET_OF, LOCAL_REJECTED, RUN_REJECTED)}
    for number, line in enumerate(lines, start=1):
        name, _, text = line.rstrip("\n").partition("\t")
        if name not in rows:
            # The line may be an event, so the message only says where it is
            raise ValueError(f"stdin line {number}: not a known name")
        rows[name].append(json.loads(text))
    return rows


def field_types(table: str) -> dict[str, str]:
    fields = json.loads((SCHEMAS / f"{table}.json").read_text(encoding="utf-8"))
    return {field["name"]: field["type"] for field in fields}


def canonical(value: Any, type_: str) -> Any:
    """One form for a value, whether it came from a local file or from the BigQuery client."""
    if value is None:
        return None
    if type_ == "TIMESTAMP":
        moment = value if isinstance(value, datetime) else datetime.fromisoformat(value)
        return moment.astimezone(UTC).isoformat(timespec="microseconds")
    if type_ == "NUMERIC":
        return format(Decimal(value).normalize(), "f")
    return value


def content(rows: Iterable[Mapping[str, Any]], types: Mapping[str, str]) -> Counter[str]:
    """The rows as a multiset, without the columns that name the run."""
    return Counter(
        json.dumps(
            {name: canonical(row[name], type_) for name, type_ in types.items() if name not in LINEAGE},
            sort_keys=True,
        )
        for row in rows
    )


def rejections(rows: Iterable[Mapping[str, Any]]) -> Counter[tuple[str, str, str]]:
    return Counter((row["kind"], row["error"], row["line"]) for row in rows)


def _of_run(client: Any, select: str, project: str, table: str, run: str) -> list[Any]:
    config = query_config()
    config.query_parameters = [bigquery.ScalarQueryParameter("run_id", "STRING", run)]
    base = f"`{project_id(project)}.{DATASET_OF[table]}.{table}`"
    sql = f"SELECT {select} FROM {base} WHERE run_id = @run_id"  # noqa: S608 (validated id)
    return list(client.query(sql, job_config=config, location=LOCATION).result())


def verify(client: Any, project: str, run: str, rows: Rows) -> list[tuple[str, bool]]:
    """One line per check, and whether it passed. An empty local table fails: it would prove nothing."""
    checks = []
    for table in DATASET_OF:
        local = len(rows[table])
        loaded = int(_of_run(client, "COUNT(*) AS n", project, table, run)[0]["n"])
        checks.append((f"{table}: {loaded} rows of the run, {local} local", loaded == local > 0))
    for table in (name for name, dataset in DATASET_OF.items() if dataset == "vortex_curated"):
        types = field_types(table)
        loaded_rows = _of_run(client, ", ".join(types), project, table, run)
        differ = _difference(content(loaded_rows, types), content(rows[table], types))
        checks.append((f"{table}: {differ} rows differ from the local run", differ == 0))
    local_rejected, run_rejected = rows[LOCAL_REJECTED], rows[RUN_REJECTED]
    differ = _difference(rejections(run_rejected), rejections(local_rejected))
    checks.append(
        (
            f"rejected: {len(run_rejected)} in the run, {len(local_rejected)} local, {differ} differ",
            differ == 0 and bool(local_rejected),
        )
    )
    return checks


def _difference(left: Counter[Any], right: Counter[Any]) -> int:
    return ((left - right) + (right - left)).total()


def view_hashes(client: Any, project: str) -> dict[str, tuple[int, str]]:
    """Rows of each view and the SHA-256 of its content, whatever the row order."""
    hashes = {}
    for view in VIEWS:
        sql = (
            "SELECT TO_JSON_STRING(t) AS j "  # noqa: S608 (validated id)
            f"FROM `{project_id(project)}.{ANALYTICS_DATASET}.{view}` AS t"
        )
        rows = sorted(
            row["j"] for row in client.query(sql, job_config=query_config(), location=LOCATION).result()
        )
        hashes[view] = (len(rows), hashlib.sha256("\n".join(rows).encode()).hexdigest())
    return hashes


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m vortex_pipeline.dataflow_check")
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("verify", help="compare a run with the local one read from stdin")
    check.add_argument("--run-id", type=run_id, required=True)
    views = commands.add_parser("views", help="count and hash the content of the analytics views")
    for command in (check, views):
        command.add_argument("--project", type=project_id, required=True)
        command.add_argument("--impersonate", required=True, help="service account that runs the queries")
    args = parser.parse_args(argv)

    client = bigquery.Client(
        project=args.project, credentials=credentials(args.impersonate), location=LOCATION
    )
    if args.command == "views":
        hashes = view_hashes(client, args.project)
        for view, (count, digest) in hashes.items():
            print(f"{view}: {count} rows, sha256 {digest}{'' if count else ' (EMPTY, proves nothing)'}")
        return 0 if all(count for count, _ in hashes.values()) else 1

    checks = verify(client, args.project, args.run_id, read_rows(sys.stdin))
    for line, passed in checks:
        print(f"{'ok  ' if passed else 'FAIL'} {line}")
    return 0 if all(passed for _, passed in checks) else 1


if __name__ == "__main__":
    sys.exit(main())
