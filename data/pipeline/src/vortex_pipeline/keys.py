"""The HMAC key behind every pseudonym.

The key is read where the records are curated (inside the workers), never passed as a pipeline option:
Dataflow shows every option of a job in its console and keeps them in the job graph. Locally it comes from
the environment (``data/.env``, git-ignored); on Dataflow, from a Secret Manager version that holds that
same file, and only the version's name travels with the job.
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


def load_key_from_secret_manager(version_name: str) -> HmacKey:
    """Read the key from a Secret Manager version whose payload is a ``data/.env`` file.

    `version_name` is the full name (``projects/P/secrets/S/versions/V``). The client is imported here, so
    a local run needs neither it nor credentials. Error messages never hold the payload.
    """
    from google.cloud import secretmanager

    response = secretmanager.SecretManagerServiceClient().access_secret_version(name=version_name)
    try:
        text = response.payload.data.decode("utf-8")
    except UnicodeDecodeError:
        # The original error carries the bytes it could not decode
        raise ValueError("the secret payload is not UTF-8") from None
    return load_key_from_env(parse_env_payload(text))


def parse_env_payload(text: str) -> dict[str, str]:
    """The variables of a ``data/.env`` payload: one ``NAME=value`` per line, the first ``=`` splits.

    Blank lines are skipped. Any other line, a repeated name included, is a ValueError: an ambiguous payload
    could label one key with the version of another, and the tables are append only. No message holds any
    part of a line (a malformed one may be the key itself).
    """
    variables: dict[str, str] = {}
    for number, line in enumerate(text.splitlines(), start=1):
        if not line.strip():
            continue
        name, separator, value = line.partition("=")
        if not separator or name not in (KEY_ENV, VERSION_ENV):
            raise ValueError(f"the secret payload, line {number}: not {KEY_ENV} or {VERSION_ENV}")
        if name in variables:
            raise ValueError(f"the secret payload, line {number}: {name} is repeated")
        variables[name] = value
    return variables
