"""Batch pipeline: Vortex events (NDJSON files) to raw and curated records.

Scenario A: the DirectRunner writes one NDJSON file per table and ``bigquery_sandbox load`` appends them.
Scenario B (``--bigquery_project`` and ``--hmac_secret``, on Dataflow) reads the landing bucket and appends
to the tables with BigQuery file loads; parsing and curation stay the same.

Lines that can't become a row go to ``quarantine/rejected.ndjson`` (``quarantine/<run id>/`` in scenario B)
with the reason. They hold the original line, personal data included, so that file stays next to the raw
layer and never reaches a curated table.
"""

from __future__ import annotations

import logging
import re
import sys
import uuid
from collections.abc import Callable, Sequence
from datetime import UTC, datetime
from functools import partial
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import apache_beam as beam
from apache_beam.io.gcp.bigquery import BigQueryDisposition, WriteToBigQuery
from apache_beam.io.textio import ReadFromTextWithFilename, WriteToText
from apache_beam.metrics import Metrics
from apache_beam.options.pipeline_options import GoogleCloudOptions, PipelineOptions
from apache_beam.runners.runner import PipelineResult, PipelineState

from vortex_pipeline.bigquery_sandbox import DATASET_OF, project_id
from vortex_pipeline.curate import curate_movement, curate_user_event
from vortex_pipeline.keys import HmacKey, load_key_from_env, load_key_from_secret_manager
from vortex_pipeline.parse import Context, InvalidRecordError, json_text, parse_movement, parse_user_event

REJECTED = "rejected"
TABLES = ("raw_movements", "curated_movements", "raw_user_events", "curated_user_events")
_REQUIRED_OPTIONS = ("movements_input", "user_events_input", "output_dir")
_SECRET_VERSION = re.compile(r"projects/[a-z0-9-]+/secrets/[A-Za-z0-9_-]+/versions/(?:latest|[0-9]+)")
_GCS_FOLDER = re.compile(r"gs://[a-z0-9][a-z0-9._-]+/.+")

Parser = Callable[[str, Context], dict[str, Any]]
Curator = Callable[[dict[str, Any], HmacKey], dict[str, Any]]
KeyLoader = Callable[[], HmacKey]

_LOG = logging.getLogger(__name__)


class VortexOptions(PipelineOptions):
    @classmethod
    def _add_argparse_args(cls, parser):
        parser.add_argument("--movements_input", help="File pattern of the movement events (NDJSON)")
        parser.add_argument("--user_events_input", help="File pattern of the authorization events (NDJSON)")
        parser.add_argument("--output_dir", help="Folder for one NDJSON file per table, plus quarantine/")
        parser.add_argument(
            "--source_timezone",
            default="UTC",
            help="Zone of the date-times that carry no offset (the inventory app's LocalDateTime)",
        )
        parser.add_argument(
            "--bigquery_project",
            type=project_id,
            help="Append to the tables of this project instead of writing files; needs --hmac_secret",
        )
        parser.add_argument(
            "--hmac_secret",
            help="Secret Manager version that holds the HMAC key (projects/P/secrets/S/versions/V)",
        )
        parser.add_argument(
            "--run_id", help="Id written on every row; default: start time plus a random suffix"
        )


class ParseEvents(beam.DoFn):
    """Parses each (file, line) pair; a line that can't become a row goes to the `rejected` output."""

    def __init__(self, kind: str, parser: Parser, run_id: str, ingested_at: str, source_timezone: str):
        self._kind = kind
        self._parser = parser
        self._run_id = run_id
        self._ingested_at = ingested_at
        self._source_timezone = source_timezone
        self._parsed = Metrics.counter("vortex", f"{kind}_parsed")
        self._rejected = Metrics.counter("vortex", f"{kind}_rejected")
        self._tz = ZoneInfo("UTC")

    def setup(self):
        self._tz = ZoneInfo(self._source_timezone)

    def process(self, element):
        source_file, line = element
        if not line.strip():
            return
        context = Context(_lineage_name(source_file), self._run_id, self._ingested_at, self._tz)
        try:
            record = self._parser(line, context)
        except InvalidRecordError as error:
            self._rejected.inc()
            rejected = {"kind": self._kind, "error": str(error), "line": line, **context.lineage()}
            yield beam.pvalue.TaggedOutput(REJECTED, rejected)
            return
        self._parsed.inc()
        yield record


class Curate(beam.DoFn):
    """Loads the HMAC key once per worker, in setup, and curates each raw record with it."""

    def __init__(self, curator: Curator, key_loader: KeyLoader):
        self._curator = curator
        self._key_loader = key_loader
        self._key: HmacKey | None = None

    def setup(self):
        self._key = self._key_loader()

    def process(self, element):
        yield self._curator(element, self._key)


def build(
    pipeline: beam.Pipeline,
    *,
    movements_input: str,
    user_events_input: str,
    output_dir: str,
    run_id: str,
    ingested_at: str,
    source_timezone: str,
    key_loader: KeyLoader,
    bigquery_project: str | None = None,
    temp_location: str | None = None,
) -> dict[str, beam.PCollection]:
    sources = (
        ("movements", movements_input, parse_movement, curate_movement),
        ("user_events", user_events_input, parse_user_event, curate_user_event),
    )
    outputs: dict[str, beam.PCollection] = {}
    rejected = []
    for kind, pattern, parser, curator in sources:
        parsed = (
            pipeline
            | f"Read {kind}" >> ReadFromTextWithFilename(pattern)
            | f"Parse {kind}"
            >> beam.ParDo(ParseEvents(kind, parser, run_id, ingested_at, source_timezone)).with_outputs(
                REJECTED, main="raw"
            )
        )
        outputs[f"raw_{kind}"] = parsed.raw
        outputs[f"curated_{kind}"] = parsed.raw | f"Curate {kind}" >> beam.ParDo(Curate(curator, key_loader))
        rejected.append(parsed[REJECTED])
    outputs[REJECTED] = tuple(rejected) | "Merge rejected" >> beam.Flatten()

    for name, records in outputs.items():
        if bigquery_project and name != REJECTED:
            _ = records | f"Load {name}" >> _append_to_bigquery(bigquery_project, name, temp_location)
            continue
        folder = f"{output_dir}/quarantine" if name == REJECTED else output_dir
        if bigquery_project:
            # Always under quarantine/: the worker could create objects anywhere in the landing bucket
            folder = f"{folder}/{run_id}"
        _ = (
            records
            | f"Serialize {name}" >> beam.Map(json_text)
            | f"Write {name}"
            >> WriteToText(
                f"{folder}/{name}", file_name_suffix=".ndjson", shard_name_template="", num_shards=1
            )
        )
    return outputs


def _append_to_bigquery(project: str, table: str, temp_location: str | None) -> WriteToBigQuery:
    """Load jobs straight into the table Terraform made.

    Static table and no schema, on purpose: a schema, a callable or schema_update_options makes Beam write
    the table's schema, and that can take the policy tags off.
    """
    return WriteToBigQuery(
        table=f"{project}:{DATASET_OF[table]}.{table}",
        method=WriteToBigQuery.Method.FILE_LOADS,
        write_disposition=BigQueryDisposition.WRITE_APPEND,
        create_disposition=BigQueryDisposition.CREATE_NEVER,
        schema=None,
        custom_gcs_temp_location=temp_location,
    )


def run(
    argv: Sequence[str],
    *,
    key_loader: KeyLoader | None = None,
    now: datetime | None = None,
) -> PipelineResult:
    options = PipelineOptions(list(argv))
    vortex = options.view_as(VortexOptions)
    missing = [f"--{name}" for name in _REQUIRED_OPTIONS if not getattr(vortex, name)]
    if missing:
        raise ValueError("missing " + ", ".join(missing))
    try:
        ZoneInfo(vortex.source_timezone)
    except (ZoneInfoNotFoundError, ValueError):
        raise ValueError(f"unknown --source_timezone {vortex.source_timezone!r}") from None
    temp_location = options.view_as(GoogleCloudOptions).temp_location
    if vortex.bigquery_project or vortex.hmac_secret:
        _check_bigquery_options(vortex, temp_location)
    if key_loader is None:
        # A partial of a module function pickles by reference: only the version's name reaches the job
        key_loader = (
            partial(load_key_from_secret_manager, vortex.hmac_secret)
            if vortex.hmac_secret
            else load_key_from_env
        )

    started = (now or datetime.now(UTC)).astimezone(UTC)
    run_id = vortex.run_id or f"{started:%Y%m%dT%H%M%SZ}-{uuid.uuid4().hex[:8]}"
    pipeline = beam.Pipeline(options=options)
    build(
        pipeline,
        movements_input=vortex.movements_input,
        user_events_input=vortex.user_events_input,
        output_dir=vortex.output_dir.rstrip("/"),
        run_id=run_id,
        ingested_at=started.isoformat(timespec="microseconds"),
        source_timezone=vortex.source_timezone,
        key_loader=key_loader,
        bigquery_project=vortex.bigquery_project,
        temp_location=temp_location,
    )
    result = pipeline.run()
    result.wait_until_finish()
    # A Dataflow job that was already cancelled when the wait began ends it without an error
    if result.state != PipelineState.DONE:
        raise RuntimeError(f"run {run_id} ended in state {result.state}")
    _LOG.info("run %s finished in state %s", run_id, result.state)
    return result


def _check_bigquery_options(vortex: VortexOptions, temp_location: str | None) -> None:
    """Beam checks none of these at launch: a wrong one would only fail inside the paid job."""
    if not vortex.bigquery_project or not vortex.hmac_secret:
        raise ValueError("--bigquery_project and --hmac_secret go together")
    if not _SECRET_VERSION.fullmatch(vortex.hmac_secret):
        raise ValueError("--hmac_secret must be projects/P/secrets/S/versions/V")
    if not _GCS_FOLDER.fullmatch(temp_location or ""):
        raise ValueError("--bigquery_project needs a --temp_location folder under gs://")
    if not vortex.output_dir.startswith("gs://"):
        raise ValueError("--bigquery_project needs an --output_dir under gs://")


def main(argv: Sequence[str] | None = None) -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
    try:
        run(sys.argv[1:] if argv is None else argv)
    except (ValueError, OSError) as error:
        # OSError: no file matches an input pattern
        sys.exit(f"vortex_pipeline: {error}")


def _lineage_name(path: str) -> str:
    """Keeps a bucket URI whole; a local path becomes its file name, so no home folder lands in a table."""
    return path if "://" in path else path.rsplit("/", 1)[-1]
