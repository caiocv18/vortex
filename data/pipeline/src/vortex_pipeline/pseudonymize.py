"""Pseudonymization primitives: keyed hashes for identifiers, network prefixes for IP addresses.

A keyed hash (HMAC-SHA256) is deterministic, so the same username gives the same pseudonym in every table
and the tables still join; without the key, nobody can rebuild the pseudonym from a guessed username,
which a plain SHA-256 would allow. Under the LGPD this is pseudonymization, not anonymization: whoever
holds the key can link a pseudonym back to a person, so the curated data is still personal data.
"""

from __future__ import annotations

import hashlib
import hmac
import ipaddress

from vortex_pipeline.keys import HmacKey

# Purposes: the same text hashed as a username and as an e-mail gives two unrelated pseudonyms
USER = "user"
EMAIL = "email"

_IPV4_PREFIX = 24
_IPV6_PREFIX = 48


def normalize(value: object) -> str | None:
    """Trim and lowercase, so `Bob`, `bob ` and `BOB` are one person; None for anything else."""
    if not isinstance(value, str):
        return None
    normalized = value.strip().lower()
    return normalized or None


def pseudonym(key: HmacKey, purpose: str, value: object) -> str | None:
    """HMAC-SHA256 of the normalized value, bound to a purpose, as 64 hex characters."""
    normalized = normalize(value)
    if normalized is None:
        return None
    message = purpose.encode("ascii") + b"\x00" + normalized.encode("utf-8")
    return hmac.new(key.secret, message, hashlib.sha256).hexdigest()


def ip_prefix(value: object) -> str | None:
    """The /24 (IPv4) or /48 (IPv6) network of the address, or None when it isn't an IP address.

    Accepts what the authorization service may record: a plain address, an X-Forwarded-For list (the
    client comes first), an IPv4 address with a port, a bracketed IPv6 address with a port, and an
    IPv4-mapped IPv6 address, which is reported as IPv4.
    """
    address = _parse_address(value)
    if address is None:
        return None
    prefix = _IPV4_PREFIX if address.version == 4 else _IPV6_PREFIX
    return str(ipaddress.ip_network((address, prefix), strict=False))


def _parse_address(value: object) -> ipaddress.IPv4Address | ipaddress.IPv6Address | None:
    if not isinstance(value, str):
        return None
    candidate = value.split(",")[0].strip()
    if candidate.startswith("["):
        candidate = candidate[1:].split("]")[0]
    elif candidate.count(":") == 1:
        candidate = candidate.split(":")[0]
    try:
        address = ipaddress.ip_address(candidate)
    except ValueError:
        return None
    if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped is not None:
        return address.ipv4_mapped
    return address
