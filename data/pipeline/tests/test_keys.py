from types import SimpleNamespace

import pytest
from google.cloud import secretmanager

from vortex_pipeline.keys import (
    KEY_ENV,
    VERSION_ENV,
    HmacKey,
    load_key_from_env,
    load_key_from_secret_manager,
    parse_env_payload,
)


def test_loads_a_hex_key_and_its_version():
    key = load_key_from_env({KEY_ENV: "AB" * 32, VERSION_ENV: "local-20261116"})
    assert key.secret == bytes([0xAB]) * 32
    assert key.version == "local-20261116"


def test_reads_the_process_environment(monkeypatch):
    monkeypatch.setenv(KEY_ENV, "cd" * 32)
    monkeypatch.setenv(VERSION_ENV, "env-1")
    assert load_key_from_env().version == "env-1"


@pytest.mark.parametrize("raw", ["", "ab" * 31, "zz" * 32, "ab" * 33])
def test_rejects_a_malformed_key_without_echoing_it(raw):
    with pytest.raises(ValueError, match=KEY_ENV) as error:
        load_key_from_env({KEY_ENV: raw, VERSION_ENV: "v1"})
    if raw:
        assert raw not in str(error.value)


@pytest.mark.parametrize("version", ["", "has space", "x" * 65])
def test_rejects_a_bad_version(version):
    with pytest.raises(ValueError, match="version"):
        load_key_from_env({KEY_ENV: "ab" * 32, VERSION_ENV: version})


def test_rejects_a_secret_that_is_not_32_bytes():
    with pytest.raises(ValueError, match="32 bytes"):
        HmacKey(version="v1", secret=b"short")


def test_the_secret_never_shows_in_repr():
    key = HmacKey(version="v1", secret=b"\x01" * 32)
    assert repr(key) == "HmacKey(version='v1')"


VERSION_NAME = "projects/vortex-ingest-abc123/secrets/vortex-hmac-key/versions/1"
PAYLOAD = f"{KEY_ENV}={'ab' * 32}\n{VERSION_ENV}=local-20261116\n"


@pytest.fixture
def payload(monkeypatch):
    """Sets what the fake Secret Manager answers; the names asked for end up in `asked`."""
    answer = SimpleNamespace(data=b"", asked=[])

    class FakeSecretManager:
        def access_secret_version(self, name):
            answer.asked.append(name)
            return SimpleNamespace(payload=SimpleNamespace(data=answer.data))

    monkeypatch.setattr(secretmanager, "SecretManagerServiceClient", FakeSecretManager)
    return answer


def test_loads_the_key_from_secret_manager(payload):
    payload.data = PAYLOAD.encode()
    key = load_key_from_secret_manager(VERSION_NAME)
    assert key.secret == bytes([0xAB]) * 32
    assert key.version == "local-20261116"
    assert payload.asked == [VERSION_NAME]


def test_a_payload_that_is_not_utf8_is_not_echoed(payload):
    payload.data = b"\xff\xfe" + PAYLOAD.encode()
    with pytest.raises(ValueError, match="not UTF-8") as error:
        load_key_from_secret_manager(VERSION_NAME)
    assert error.value.__cause__ is None
    assert "ab" * 32 not in str(error.value)


def test_a_payload_without_the_key_is_refused(payload):
    payload.data = f"{VERSION_ENV}=v1\n".encode()
    with pytest.raises(ValueError, match=KEY_ENV):
        load_key_from_secret_manager(VERSION_NAME)


@pytest.mark.parametrize(
    "text",
    [PAYLOAD, PAYLOAD.rstrip("\n"), PAYLOAD.replace("\n", "\r\n"), "\n" + PAYLOAD + "\n\n"],
)
def test_parses_the_env_payload(text):
    assert parse_env_payload(text) == {KEY_ENV: "ab" * 32, VERSION_ENV: "local-20261116"}


@pytest.mark.parametrize(
    "line",
    ["cd" * 32, f"export {'cd' * 32}", f"{'cd' * 32}=x", f"OTHER={'cd' * 32}", f"{KEY_ENV}={'cd' * 32}"],
)
def test_an_extra_or_repeated_line_is_refused_without_echoing_it(line):
    with pytest.raises(ValueError, match="the secret payload, line 3") as error:
        parse_env_payload(PAYLOAD + line + "\n")
    assert "cd" * 32 not in str(error.value)
    assert "ab" * 32 not in str(error.value)


def test_a_repeated_line_is_refused_even_with_the_same_value():
    with pytest.raises(ValueError, match=f"{VERSION_ENV} is repeated"):
        parse_env_payload(PAYLOAD + f"{VERSION_ENV}=local-20261116\n")
