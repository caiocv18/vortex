"""The kill-switch against a fake Cloud Billing client."""

import base64
import json
from decimal import Decimal
from types import SimpleNamespace

import pytest

import main

THRESHOLD = Decimal("3")
PROJECT = "vortex-ingest-test"


def encode(body) -> str:
    raw = body if isinstance(body, bytes) else json.dumps(body).encode()
    return base64.b64encode(raw).decode()


def notification(cost, **extra) -> str:
    body = {
        "budgetDisplayName": "vortex-gcp-monthly",
        "costAmount": cost,
        "costIntervalStart": "2026-11-01T07:00:00Z",
        "budgetAmount": 5.0,
        "budgetAmountType": "SPECIFIED_AMOUNT",
        "currencyCode": "USD",
    }
    body.update(extra)
    return encode(body)


class FakeBilling:
    def __init__(self, enabled=True):
        self.enabled = enabled
        self.updates = []

    def get_project_billing_info(self, name):
        return SimpleNamespace(name=name, billing_enabled=self.enabled)

    def update_project_billing_info(self, name, project_billing_info):
        self.updates.append((name, project_billing_info.billing_account_name))
        self.enabled = False


@pytest.mark.parametrize(
    "data",
    [
        "not base64!",
        encode(b"not json"),
        encode([1, 2]),
        encode({"budgetAmount": 5}),
        encode({"costAmount": "lots"}),
    ],
)
def test_a_message_that_is_not_a_notification_is_ignored(data):
    client = FakeBilling()
    assert main.handle(data, client, PROJECT, THRESHOLD) is False
    assert client.updates == []


def test_parse_keeps_the_cost_exact():
    parsed = main.parse_notification(notification(2.99))
    assert parsed["costAmount"] == Decimal("2.99")


def test_below_the_threshold_billing_stays():
    client = FakeBilling()
    assert main.handle(notification(2.99), client, PROJECT, THRESHOLD) is False
    assert client.updates == []


def test_a_crossed_budget_threshold_alone_does_not_cut():
    # alertThresholdExceeded is about the budget (US$ 5), not the kill switch
    client = FakeBilling()
    assert main.handle(notification(2.6, alertThresholdExceeded=0.5), client, PROJECT, THRESHOLD) is False
    assert client.updates == []


@pytest.mark.parametrize("cost", [3, 3.5, 40])
def test_at_or_above_the_threshold_billing_is_unlinked(cost):
    client = FakeBilling()
    assert main.handle(notification(cost), client, PROJECT, THRESHOLD) is True
    assert client.updates == [(f"projects/{PROJECT}", "")]


def test_already_disabled_is_left_alone():
    client = FakeBilling(enabled=False)
    assert main.handle(notification(10), client, PROJECT, THRESHOLD) is False
    assert client.updates == []


def test_entry_point_reads_its_configuration(monkeypatch):
    client = FakeBilling()
    monkeypatch.setattr(main.billing_v1, "CloudBillingClient", lambda: client)
    monkeypatch.setenv("TARGET_PROJECT", PROJECT)
    monkeypatch.setenv("KILL_SWITCH_AMOUNT", "3")
    main.kill_switch(SimpleNamespace(data={"message": {"data": notification(3.01)}}))
    assert client.updates == [(f"projects/{PROJECT}", "")]
