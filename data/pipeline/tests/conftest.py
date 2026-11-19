from __future__ import annotations

import json
from functools import partial
from pathlib import Path
from typing import Any

from vortex_pipeline.keys import HmacKey

FIXTURES = Path(__file__).parent / "fixtures"
SCHEMAS = Path(__file__).resolve().parents[2] / "schemas"

TEST_KEY = HmacKey(version="test-1", secret=bytes(range(32)))
# A partial of the class pickles by reference, so the Beam workers can rebuild the key loader
TEST_KEY_LOADER = partial(HmacKey, "test-1", bytes(range(32)))


def read_ndjson(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def schema(table: str) -> list[dict[str, str]]:
    return json.loads((SCHEMAS / f"{table}.json").read_text(encoding="utf-8"))


def column_names(table: str) -> set[str]:
    return {field["name"] for field in schema(table)}
