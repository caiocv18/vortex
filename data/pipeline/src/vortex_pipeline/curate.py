"""Curated records: what the analytics project may see.

Both functions build the record from an allowlist: a column that is not named here never reaches the
curated layer, even if the producer starts sending it. Identifiers become pseudonyms (`user_key`,
`email_key`), the IP address becomes its network, and the user agent, the session, the user's UUID and the
producer's free text stay in the raw layer.
"""

from __future__ import annotations

import json
from typing import Any

from vortex_pipeline.keys import HmacKey
from vortex_pipeline.parse import json_text
from vortex_pipeline.pseudonymize import EMAIL, USER, ip_prefix, pseudonym

MOVEMENT_COLUMNS = (
    "event_id",
    "event_type",
    "schema_version",
    "event_ts",
    "movement_id",
    "product_id",
    "product_description",
    "movement_type",
    "quantity",
    "sale_value",
    "supplier_value",
    "stock_before",
    "stock_after",
    "movement_ts",
    "profit",
    "product_type",
    "source_file",
    "run_id",
    "ingested_at",
)
USER_EVENT_COLUMNS = (
    "event_id",
    "event_type",
    "schema_version",
    "event_ts",
    "source_file",
    "run_id",
    "ingested_at",
)
# Per event type, the extra fields an analyst may read
ATTRIBUTE_ALLOWLIST: dict[str, frozenset[str]] = {
    "user.created": frozenset({"roles", "isVerified", "registrationMethod"}),
    "user.logged_in": frozenset({"roles", "loginMethod"}),
    "user.logged_out": frozenset({"logoutReason"}),
    "password.changed": frozenset({"changeReason", "changeMethod"}),
    "password.reset_requested": frozenset({"requestMethod", "expiresAt"}),
    "email.verified": frozenset({"verificationMethod"}),
}


def curate_movement(raw: dict[str, Any], key: HmacKey) -> dict[str, Any]:
    curated = {column: raw.get(column) for column in MOVEMENT_COLUMNS}
    curated["user_key"] = pseudonym(key, USER, raw.get("user_id"))
    curated["hmac_key_version"] = key.version
    return curated


def curate_user_event(raw: dict[str, Any], key: HmacKey) -> dict[str, Any]:
    curated = {column: raw.get(column) for column in USER_EVENT_COLUMNS}
    # The app's JWT principal is the username, so this is the key that joins with the movements
    curated["user_key"] = pseudonym(key, USER, raw.get("username"))
    curated["email_key"] = pseudonym(key, EMAIL, raw.get("user_email"))
    curated["ip_prefix"] = ip_prefix(raw.get("ip_address"))
    curated["attributes"] = _allowed_attributes(raw.get("event_type"), raw.get("attributes"))
    curated["hmac_key_version"] = key.version
    return curated


def _allowed_attributes(event_type: object, attributes: object) -> str | None:
    allowed = ATTRIBUTE_ALLOWLIST.get(str(event_type), frozenset())
    if not allowed or not isinstance(attributes, str):
        return None
    kept = {k: v for k, v in json.loads(attributes).items() if k in allowed}
    return json_text(kept) if kept else None
