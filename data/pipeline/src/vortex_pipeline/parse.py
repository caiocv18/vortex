"""Parse and validate the raw events, one JSON object per line, as the Kafka capture writes them.

The parsers return the raw record: every column of the raw table, with personal data still in clear.
Fields that name a secret (password reset and e-mail verification token ids) are dropped here, so they
never reach any table, the raw one included.

Producers:
- movements: ``MovimentoEstoqueEventDTO`` of the inventory app (Spring Boot), topic
  ``vortex.movimento.estoque``; its date-times are ``LocalDateTime``, without an offset;
- user events: ``AuthEvent`` subclasses of the authorization service (Quarkus), topic
  ``vortex-user-events``; their date-times are ``OffsetDateTime``.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from decimal import Decimal, InvalidOperation
from typing import Any
from zoneinfo import ZoneInfo

MOVEMENT_EVENT_TYPE = "MOVIMENTO_ESTOQUE"
MOVEMENT_TYPES = frozenset({"ENTRADA", "SAIDA"})
USER_EVENT_TYPES = frozenset(
    {
        "user.created",
        "user.logged_in",
        "user.logged_out",
        "password.changed",
        "password.reset_requested",
        "email.verified",
    }
)
# Compared in lower case, at any depth of the event
SECRET_FIELDS = frozenset(
    {"resettokenid", "verificationtokenid", "token", "accesstoken", "refreshtoken", "password"}
)

# Fields of a user event that get a column of their own; the rest goes to `attributes`
_USER_EVENT_COLUMNS = frozenset(
    {
        "eventId",
        "eventType",
        "version",
        "timestamp",
        "userId",
        "username",
        "userEmail",
        "ipAddress",
        "userAgent",
        "sessionId",
    }
)
_EXTRA_FRACTION_DIGITS = re.compile(r"(\.\d{6})\d+")
_NUMERIC_MAX_SCALE = 9
_INT64_MIN, _INT64_MAX = -(2**63), 2**63 - 1
_EPOCH = datetime(1970, 1, 1, tzinfo=UTC)


class InvalidRecordError(ValueError):
    """The line can't become a row. The message names the problem, never the content of the line."""


@dataclass(frozen=True)
class Context:
    """Where a line came from and which run reads it; `source_tz` gives LocalDateTime values a zone."""

    source_file: str
    run_id: str
    ingested_at: str
    source_tz: ZoneInfo

    def lineage(self) -> dict[str, str]:
        return {"source_file": self.source_file, "run_id": self.run_id, "ingested_at": self.ingested_at}


def parse_movement(line: str, context: Context) -> dict[str, Any]:
    event = _load(line)
    event_type = _string(event, "eventType", required=True)
    if event_type != MOVEMENT_EVENT_TYPE:
        raise InvalidRecordError(f"eventType is not {MOVEMENT_EVENT_TYPE}")
    movement_type = _string(event, "tipoMovimentacao", required=True)
    if movement_type not in MOVEMENT_TYPES:
        raise InvalidRecordError("tipoMovimentacao must be ENTRADA or SAIDA")
    return {
        "event_id": _string(event, "eventId", required=True),
        "event_type": event_type,
        "schema_version": _string(event, "version"),
        "event_ts": _timestamp(event, "timestamp", context.source_tz, required=True),
        "user_id": _string(event, "userId"),
        "session_id": _string(event, "sessionId"),
        "metadata": _string(event, "metadata"),
        "movement_id": _integer(event, "movimentoId", required=True),
        "product_id": _integer(event, "produtoId", required=True),
        "product_description": _string(event, "produtoDescricao"),
        "movement_type": movement_type,
        "quantity": _integer(event, "quantidadeMovimentada", required=True),
        "sale_value": _numeric(event, "valorVenda"),
        "supplier_value": _numeric(event, "valorFornecedor"),
        "stock_before": _integer(event, "estoqueAnterior"),
        "stock_after": _integer(event, "estoqueAtual"),
        "movement_ts": _timestamp(event, "dataMovimento", context.source_tz),
        "profit": _numeric(event, "lucro"),
        "product_type": _string(event, "tipoProduto"),
        **context.lineage(),
    }


def parse_user_event(line: str, context: Context) -> dict[str, Any]:
    event = _load(line)
    event_type = _string(event, "eventType", required=True)
    if event_type not in USER_EVENT_TYPES:
        raise InvalidRecordError("eventType is not a known authorization event")
    extra = _without_secrets({k: v for k, v in event.items() if k not in _USER_EVENT_COLUMNS})
    return {
        "event_id": _string(event, "eventId", required=True),
        "event_type": event_type,
        "schema_version": _string(event, "version"),
        "event_ts": _timestamp(event, "timestamp", context.source_tz, required=True),
        "user_id": _string(event, "userId"),
        "username": _string(event, "username"),
        "user_email": _string(event, "userEmail"),
        "ip_address": _string(event, "ipAddress"),
        "user_agent": _string(event, "userAgent"),
        "session_id": _string(event, "sessionId"),
        "attributes": json_text(extra) if extra else None,
        **context.lineage(),
    }


def json_text(value: Any) -> str:
    """Compact, key-sorted JSON; a Decimal becomes an int or a float."""
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), default=_json_default)


def to_utc(value: Any, source_tz: ZoneInfo, field: str) -> str:
    """An RFC 3339 timestamp in UTC with microseconds, which a BigQuery load reads as TIMESTAMP.

    Accepts an ISO-8601 string (with or without an offset; up to nanoseconds, cut to microseconds), the
    array Jackson writes for a LocalDateTime when WRITE_DATES_AS_TIMESTAMPS is on, and epoch seconds.
    A value without an offset is read in `source_tz`.
    """
    moment = _to_datetime(value, field)
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=source_tz)
    return moment.astimezone(UTC).isoformat(timespec="microseconds")


def _load(line: str) -> dict[str, Any]:
    try:
        event = json.loads(line, parse_float=Decimal)
    except json.JSONDecodeError as error:
        raise InvalidRecordError(f"not JSON ({error.msg})") from None
    if not isinstance(event, dict):
        raise InvalidRecordError("not a JSON object")
    return event


def _string(event: dict[str, Any], field: str, *, required: bool = False) -> str | None:
    value = event.get(field)
    if value is None or (isinstance(value, str) and not value.strip()):
        if required:
            raise InvalidRecordError(f"{field} is missing")
        return None
    if not isinstance(value, str):
        # Jackson writes UUIDs and enums as strings: anything else here is a change in the contract
        raise InvalidRecordError(f"{field} must be a string")
    return value


def _integer(event: dict[str, Any], field: str, *, required: bool = False) -> int | None:
    value = event.get(field)
    if value is None:
        if required:
            raise InvalidRecordError(f"{field} is missing")
        return None
    if isinstance(value, bool) or not isinstance(value, int):
        raise InvalidRecordError(f"{field} must be an integer")
    if not _INT64_MIN <= value <= _INT64_MAX:
        raise InvalidRecordError(f"{field} is out of the INT64 range")
    return value


def _numeric(event: dict[str, Any], field: str) -> str | None:
    """A BigDecimal as a plain decimal string, so no float rounding happens before BigQuery's NUMERIC."""
    value = event.get(field)
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int | Decimal | str):
        raise InvalidRecordError(f"{field} must be a number")
    try:
        number = value if isinstance(value, Decimal) else Decimal(value)
    except InvalidOperation:
        raise InvalidRecordError(f"{field} must be a number") from None
    if not number.is_finite():
        raise InvalidRecordError(f"{field} must be a finite number")
    exponent = number.as_tuple().exponent
    if isinstance(exponent, int) and exponent < -_NUMERIC_MAX_SCALE:
        raise InvalidRecordError(f"{field} has more than {_NUMERIC_MAX_SCALE} decimal places")
    return format(number, "f")


def _timestamp(
    event: dict[str, Any], field: str, source_tz: ZoneInfo, *, required: bool = False
) -> str | None:
    value = event.get(field)
    if value is None:
        if required:
            raise InvalidRecordError(f"{field} is missing")
        return None
    return to_utc(value, source_tz, field)


def _to_datetime(value: Any, field: str) -> datetime:
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(_EXTRA_FRACTION_DIGITS.sub(r"\1", value.strip()))
        except ValueError:
            raise InvalidRecordError(f"{field} is not an ISO-8601 date-time") from None
    if isinstance(value, list) and 5 <= len(value) <= 7 and all(_is_int(part) for part in value):
        year, month, day, hour, minute, *rest = value
        second = rest[0] if rest else 0
        nanos = rest[1] if len(rest) > 1 else 0
        try:
            return datetime(year, month, day, hour, minute, second, nanos // 1000)
        except ValueError:
            raise InvalidRecordError(f"{field} is not a valid date-time") from None
    if _is_int(value) or isinstance(value, Decimal):
        try:
            return _EPOCH + timedelta(seconds=float(value))
        except (OverflowError, ValueError):
            raise InvalidRecordError(f"{field} is out of range") from None
    raise InvalidRecordError(f"{field} is not a date-time")


def _is_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _without_secrets(value: Any) -> Any:
    if isinstance(value, dict):
        return {k: _without_secrets(v) for k, v in value.items() if str(k).lower() not in SECRET_FIELDS}
    if isinstance(value, list):
        return [_without_secrets(item) for item in value]
    return value


def _json_default(value: Any) -> int | float:
    if isinstance(value, Decimal):
        return int(value) if value == value.to_integral_value() else float(value)
    raise TypeError(f"{type(value).__name__} is not JSON serializable")
