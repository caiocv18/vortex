import hashlib
import hmac

import pytest

from vortex_pipeline.keys import HmacKey
from vortex_pipeline.pseudonymize import EMAIL, USER, ip_prefix, normalize, pseudonym

KEY = HmacKey("k1", bytes(range(32)))
OTHER_KEY = HmacKey("k2", bytes(range(1, 33)))


def test_matches_a_reference_hmac_sha256():
    expected = hmac.new(bytes(range(32)), b"user\x00bob", hashlib.sha256).hexdigest()
    assert pseudonym(KEY, USER, "bob") == expected


def test_normalization_makes_one_person_of_case_and_spaces():
    assert pseudonym(KEY, USER, " Bob ") == pseudonym(KEY, USER, "bob") == pseudonym(KEY, USER, "BOB")


def test_purposes_never_collide():
    assert pseudonym(KEY, USER, "bob@example.com") != pseudonym(KEY, EMAIL, "bob@example.com")


def test_another_key_gives_another_pseudonym():
    assert pseudonym(KEY, USER, "bob") != pseudonym(OTHER_KEY, USER, "bob")


@pytest.mark.parametrize("value", [None, "", "   ", 42])
def test_no_pseudonym_without_a_value(value):
    assert normalize(value) is None
    assert pseudonym(KEY, USER, value) is None


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ("203.0.113.10", "203.0.113.0/24"),
        ("203.0.113.10:51234", "203.0.113.0/24"),
        ("198.51.100.23, 10.0.0.1", "198.51.100.0/24"),
        ("2001:db8:85a3::8a2e:370:7334", "2001:db8:85a3::/48"),
        ("[2001:db8:85a3::1]:443", "2001:db8:85a3::/48"),
        ("::ffff:203.0.113.77", "203.0.113.0/24"),
        ("not-an-ip", None),
        ("", None),
        (None, None),
        (12, None),
    ],
)
def test_ip_prefix(value, expected):
    assert ip_prefix(value) == expected
