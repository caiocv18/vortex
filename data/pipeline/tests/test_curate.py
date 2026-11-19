import json
from zoneinfo import ZoneInfo

from conftest import TEST_KEY, column_names

from vortex_pipeline.curate import curate_movement, curate_user_event
from vortex_pipeline.parse import Context, parse_movement, parse_user_event
from vortex_pipeline.pseudonymize import EMAIL, USER, pseudonym

CONTEXT = Context("f.ndjson", "run-1", "2026-11-16T15:00:00.000000+00:00", ZoneInfo("UTC"))

MOVEMENT = json.dumps(
    {
        "eventType": "MOVIMENTO_ESTOQUE",
        "eventId": "e-1",
        "timestamp": "2026-11-10T09:15:02",
        "sessionId": "s-1",
        "userId": "bob",
        "metadata": "free text",
        "movimentoId": 1,
        "produtoId": 2,
        "tipoMovimentacao": "SAIDA",
        "quantidadeMovimentada": 3,
    }
)
LOGIN = json.dumps(
    {
        "eventId": "u-1",
        "eventType": "user.logged_in",
        "userId": "5b0c7a52-1f3e-4d6a-9b8c-0a1b2c3d4e02",
        "userEmail": " Bob@Example.com",
        "timestamp": "2026-11-10T12:00:00Z",
        "ipAddress": "203.0.113.10",
        "userAgent": "Mozilla/5.0",
        "username": "Bob",
        "roles": ["USER"],
        "loginMethod": "PASSWORD",
        "lastLogin": "2026-11-09T12:00:00Z",
        "sessionId": "s-1",
    }
)


def test_a_curated_movement_keeps_no_identifier_in_clear():
    curated = curate_movement(parse_movement(MOVEMENT, CONTEXT), TEST_KEY)
    assert set(curated) == column_names("curated_movements")
    assert curated["user_key"] == pseudonym(TEST_KEY, USER, "bob")
    assert curated["hmac_key_version"] == "test-1"
    assert "bob" not in json.dumps(curated)
    assert "free text" not in json.dumps(curated)


def test_a_curated_login_joins_with_the_movements_of_the_same_user():
    movement = curate_movement(parse_movement(MOVEMENT, CONTEXT), TEST_KEY)
    login = curate_user_event(parse_user_event(LOGIN, CONTEXT), TEST_KEY)
    assert set(login) == column_names("curated_user_events")
    assert login["user_key"] == movement["user_key"]
    assert login["email_key"] == pseudonym(TEST_KEY, EMAIL, "bob@example.com")
    assert login["ip_prefix"] == "203.0.113.0/24"
    assert json.loads(login["attributes"]) == {"loginMethod": "PASSWORD", "roles": ["USER"]}
    text = json.dumps(login).lower()
    for personal in ("bob", "203.0.113.10", "mozilla", "5b0c7a52", "s-1", "lastlogin"):
        assert personal not in text


def test_attributes_outside_the_allowlist_disappear():
    line = json.dumps(
        {"eventId": "u-2", "eventType": "user.logged_out", "timestamp": "2026-11-10T12:00:00Z", "other": 1}
    )
    assert curate_user_event(parse_user_event(line, CONTEXT), TEST_KEY)["attributes"] is None


def test_an_event_type_without_an_allowlist_keeps_no_attributes():
    raw = {"event_type": "user.unknown", "attributes": '{"roles": ["USER"]}'}
    assert curate_user_event(raw, TEST_KEY)["attributes"] is None
