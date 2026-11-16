import json
from datetime import UTC, datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

import pytest
from conftest import column_names

from vortex_pipeline.parse import (
    Context,
    InvalidRecordError,
    json_text,
    parse_movement,
    parse_user_event,
    to_utc,
)

CONTEXT = Context(
    "movements.ndjson", "run-1", "2026-11-16T15:00:00.000000+00:00", ZoneInfo("America/Sao_Paulo")
)
SAO_PAULO = ZoneInfo("America/Sao_Paulo")


def movement(**overrides) -> str:
    event = {
        "eventType": "MOVIMENTO_ESTOQUE",
        "eventId": "e-1",
        "timestamp": "2026-11-10T09:15:02.123456789",
        "version": "1.0",
        "sessionId": "s-1",
        "userId": "alice",
        "metadata": None,
        "movimentoId": 101,
        "produtoId": 7,
        "produtoDescricao": "Caneta azul",
        "tipoMovimentacao": "SAIDA",
        "quantidadeMovimentada": 5,
        "valorVenda": 3.5,
        "valorFornecedor": 1.2,
        "estoqueAnterior": 60,
        "estoqueAtual": 55,
        "dataMovimento": "2026-11-10T09:15:02",
        "lucro": 11.5,
        "tipoProduto": "Papelaria",
    }
    event.update(overrides)
    return json.dumps(event)


def user_event(**overrides) -> str:
    event = {
        "eventId": "u-1",
        "eventType": "password.reset_requested",
        "userId": "5b0c7a52-1f3e-4d6a-9b8c-0a1b2c3d4e02",
        "userEmail": "bob@example.com",
        "timestamp": "2026-11-11T12:00:00Z",
        "ipAddress": "2001:db8::1",
        "userAgent": "Mozilla/5.0",
        "details": {"note": "n", "Token": "nested-secret"},
        "version": "1.0",
        "username": "bob",
        "resetTokenId": "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a",
        "expiresAt": "2026-11-11T13:00:00Z",
        "requestMethod": "EMAIL",
    }
    event.update(overrides)
    return json.dumps(event)


def test_a_movement_fills_every_raw_column():
    record = parse_movement(movement(), CONTEXT)
    assert set(record) == column_names("raw_movements")
    assert record["event_ts"] == "2026-11-10T12:15:02.123456+00:00"
    assert record["movement_ts"] == "2026-11-10T12:15:02.000000+00:00"
    assert record["sale_value"] == "3.5"
    assert record["user_id"] == "alice"
    assert record["source_file"] == "movements.ndjson"


def test_numbers_keep_their_decimal_digits():
    line = movement().replace('"valorFornecedor": 1.2', '"valorFornecedor": 1.20')
    record = parse_movement(line, CONTEXT)
    assert record["supplier_value"] == "1.20"


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ("12.50", "12.50"),
        (3, "3"),
        (None, None),
    ],
)
def test_numeric_accepts_strings_and_integers(value, expected):
    assert parse_movement(movement(valorVenda=value), CONTEXT)["sale_value"] == expected


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ([2026, 11, 10, 11, 30, 45, 250000000], "2026-11-10T14:30:45.250000+00:00"),
        ([2026, 11, 10, 11, 30], "2026-11-10T14:30:00.000000+00:00"),
        ("2026-11-10T12:14:58.120Z", "2026-11-10T12:14:58.120000+00:00"),
        ("2026-11-10T09:14:58-03:00", "2026-11-10T12:14:58.000000+00:00"),
        (0, "1970-01-01T00:00:00.000000+00:00"),
    ],
)
def test_date_times_end_in_utc(value, expected):
    assert to_utc(value, SAO_PAULO, "timestamp") == expected


def test_epoch_seconds_with_a_fraction():
    assert to_utc(Decimal("1794830400.5"), SAO_PAULO, "timestamp") == datetime.fromtimestamp(
        1794830400.5, UTC
    ).isoformat(timespec="microseconds")


@pytest.mark.parametrize(
    ("line", "message"),
    [
        ('{"eventType": "MOVIMENTO_ESTOQUE",', "not JSON"),
        ("[1, 2]", "not a JSON object"),
        (movement(eventType="PRODUTO_EVENT"), "eventType"),
        (movement(eventType=None), "eventType is missing"),
        (movement(tipoMovimentacao="AJUSTE"), "tipoMovimentacao"),
        (movement(timestamp=None), "timestamp is missing"),
        (movement(timestamp="yesterday"), "ISO-8601"),
        (movement(timestamp=[2026, 13, 1, 0, 0]), "not a valid date-time"),
        (movement(timestamp={"at": 1}), "not a date-time"),
        (movement(timestamp=10**20), "out of range"),
        (movement(eventId=5), "eventId must be a string"),
        (movement(eventId="  "), "eventId is missing"),
        (movement(movimentoId="101"), "movimentoId must be an integer"),
        (movement(movimentoId=None), "movimentoId is missing"),
        (movement(quantidadeMovimentada=True), "quantidadeMovimentada must be an integer"),
        (movement(produtoId=2**63), "INT64"),
        (movement(valorVenda="abc"), "valorVenda must be a number"),
        (movement(valorVenda=[1]), "valorVenda must be a number"),
        (movement(valorVenda="Infinity"), "finite"),
        (movement(valorVenda="0.0000000001"), "decimal places"),
    ],
)
def test_bad_movements_are_rejected_with_a_reason(line, message):
    with pytest.raises(InvalidRecordError, match=message):
        parse_movement(line, CONTEXT)


def test_a_nan_literal_is_not_a_number():
    with pytest.raises(InvalidRecordError, match="valorVenda must be a number"):
        parse_movement(movement().replace('"valorVenda": 3.5', '"valorVenda": NaN'), CONTEXT)


def test_a_user_event_fills_every_raw_column_and_drops_secrets():
    record = parse_user_event(user_event(), CONTEXT)
    assert set(record) == column_names("raw_user_events")
    assert record["user_email"] == "bob@example.com"
    assert record["event_ts"] == "2026-11-11T12:00:00.000000+00:00"
    attributes = json.loads(record["attributes"])
    assert attributes == {
        "details": {"note": "n"},
        "expiresAt": "2026-11-11T13:00:00Z",
        "requestMethod": "EMAIL",
    }
    assert "9d8c7b6a" not in json.dumps(record)
    assert "nested-secret" not in json.dumps(record)


def test_a_user_event_without_extra_fields_has_no_attributes():
    line = json.dumps({"eventId": "u-2", "eventType": "user.logged_out", "timestamp": "2026-11-11T12:00:00Z"})
    assert parse_user_event(line, CONTEXT)["attributes"] is None


def test_an_unknown_user_event_is_rejected():
    with pytest.raises(InvalidRecordError, match="known authorization event"):
        parse_user_event(user_event(eventType="user.deleted"), CONTEXT)


def test_json_text_turns_decimals_into_numbers():
    assert json_text({"b": Decimal("2"), "a": Decimal("1.5")}) == '{"a":1.5,"b":2}'


def test_json_text_refuses_unknown_types():
    with pytest.raises(TypeError, match="set"):
        json_text({"a": {1}})
