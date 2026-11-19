"""The HMAC key behind every pseudonym.

The key is read where the records are curated (inside the workers), never passed as a pipeline option:
Dataflow shows every option of a job in its console and keeps them in the job graph. Locally it comes from
the environment (``data/.env``, git-ignored); on Dataflow it will come from Secret Manager.
"""

from __future__ import annotations

import os
import re
from collections.abc import Mapping
from dataclasses import dataclass, field

KEY_ENV = "VORTEX_HMAC_KEY"
VERSION_ENV = "VORTEX_HMAC_KEY_VERSION"

_HEX_KEY = re.compile(r"[0-9a-f]{64}")
_VERSION = re.compile(r"[A-Za-z0-9._-]{1,64}")


@dataclass(frozen=True)
class HmacKey:
    """A 32-byte key and the version written next to each pseudonym, so a rotation can be traced."""

    version: str
    secret: bytes = field(repr=False)

    def __post_init__(self) -> None:
        if len(self.secret) != 32:
            raise ValueError("the HMAC key must have 32 bytes")
        if not _VERSION.fullmatch(self.version):
            raise ValueError("the key version must be 1 to 64 letters, digits, dots, dashes or underscores")


def load_key_from_env(environ: Mapping[str, str] | None = None) -> HmacKey:
    """Read the key from VORTEX_HMAC_KEY (64 hex characters) and VORTEX_HMAC_KEY_VERSION.

    Error messages name the variable, never its value.
    """
    env = os.environ if environ is None else environ
    raw = env.get(KEY_ENV, "").strip().lower()
    if not _HEX_KEY.fullmatch(raw):
        raise ValueError(f"{KEY_ENV} must hold 64 hex characters (a 32-byte key)")
    return HmacKey(version=env.get(VERSION_ENV, "").strip(), secret=bytes.fromhex(raw))
