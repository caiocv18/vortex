"""Batch pipeline: Vortex events (NDJSON files) to raw and curated records, one NDJSON file per table.

Scenario A (no billing): the DirectRunner writes the files locally and ``bigquery_sandbox load`` appends
them to the sandbox tables. Scenario B (billing on) reads the landing bucket and swaps the file sink for
BigQuery file loads on Dataflow; parsing and curation stay the same.

Lines that can't become a row go to ``quarantine/rejected.ndjson`` with the reason. They hold the original
line, personal data included, so that file stays next to the raw layer and never reaches a curated table.
"""

from __future__ import annotations

import logging
import sys
import uuid
from collections.abc import Callable, Sequence
from datetime import UTC, datetime
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import apache_beam as beam
from apache_beam.io.textio import ReadFromTextWithFilename, WriteToText
from apache_beam.metrics import Metrics
from apache_beam.options.pipeline_options import PipelineOptions
from apache_beam.runners.runner import PipelineResult

from vortex_pipeline.curate import curate_movement, curate_user_event
from vortex_pipeline.keys import HmacKey, load_key_from_env
from vortex_pipeline.parse import Context, InvalidRecordError, json_text, parse_movement, parse_user_event

REJECTED = "rejected"
TABLES = ("raw_movements", "curated_movements", "raw_user_events", "curated_user_events")
_REQUIRED_OPTIONS = ("movements_input", "user_events_input", "output_dir")

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
        folder = f"{output_dir}/quarantine" if name == REJECTED else output_dir
        _ = (
            records
            | f"Serialize {name}" >> beam.Map(json_text)
            | f"Write {name}"
            >> WriteToText(
                f"{folder}/{name}", file_name_suffix=".ndjson", shard_name_template="", num_shards=1
            )
        )
    return outputs


def run(
    argv: Sequence[str],
    *,
    key_loader: KeyLoader = load_key_from_env,
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
    )
    result = pipeline.run()
    result.wait_until_finish()
    _LOG.info("run %s finished in state %s", run_id, result.state)
    return result


def main(argv: Sequence[str] | None = None) -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")
    try:
        run(sys.argv[1:] if argv is None else argv)
    except ValueError as error:
        sys.exit(f"vortex_pipeline: {error}")


def _lineage_name(path: str) -> str:
    """Keeps a bucket URI whole; a local path becomes its file name, so no home folder lands in a table."""
    return path if "://" in path else path.rsplit("/", 1)[-1]
