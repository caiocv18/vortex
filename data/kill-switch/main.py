"""Kill-switch of the Vortex ingestion project (ADR 0008, amendment of 2026-11-23).

A Cloud Billing budget publishes its state to a Pub/Sub topic several times a day, whether or not a
threshold was crossed. This function reads each notification and, once the month's cost reaches
KILL_SWITCH_AMOUNT (in the billing account's currency), unlinks the billing account from TARGET_PROJECT.
Without billing, Dataflow, Cloud Storage and every other paid service in that project stop.

Linking billing back is a human decision, taken with `gcloud billing projects link`: Terraform keeps
the old billing account in its state, so its plan does not show the cut.
"""

import base64
import json
import logging
import os
from collections.abc import Mapping
from decimal import Decimal, InvalidOperation
from typing import Any

import functions_framework
from google.cloud import billing_v1

log = logging.getLogger("kill_switch")
# force: functions-framework configures the root logger before importing this module, which turns a
# plain basicConfig into a no-op and leaves INFO (the cost of every notification) out of Cloud Logging
logging.basicConfig(level=logging.INFO, force=True)


def parse_notification(data: str) -> dict[str, Any] | None:
    """Decodes the base64 JSON body of a budget notification; None when it is not one."""
    try:
        notification = json.loads(base64.b64decode(data, validate=True))
    # binascii.Error and json.JSONDecodeError are both ValueError
    except (ValueError, TypeError):
        return None
    if not isinstance(notification, dict):
        return None
    try:
        notification["costAmount"] = Decimal(str(notification["costAmount"]))
    except (KeyError, InvalidOperation):
        return None
    return notification


def should_disable(notification: Mapping[str, Any], threshold: Decimal) -> bool:
    """Decides whether this notification must cut billing.

    `notification["costAmount"]` is already a Decimal (parse_notification). The other keys are the ones
    the budget sends: budgetAmount, currencyCode, costIntervalStart, alertThresholdExceeded (only present
    once a threshold was crossed).
    """
    # The month's cost against the switch, at or above: alertThresholdExceeded belongs to the budget's
    # own thresholds (50/90/100% of budget_amount), not to the switch, so it never decides alone. The
    # currency is not compared: both amounts are in the billing account's currency by construction
    return notification["costAmount"] >= threshold


def disable_billing(client: billing_v1.CloudBillingClient, project_id: str) -> bool:
    """Unlinks billing from the project; False when it was already off (nothing to do)."""
    name = f"projects/{project_id}"
    info = client.get_project_billing_info(name=name)
    if not info.billing_enabled:
        log.info("billing already disabled on %s", project_id)
        return False
    client.update_project_billing_info(
        name=name, project_billing_info=billing_v1.ProjectBillingInfo(billing_account_name="")
    )
    log.warning("billing DISABLED on %s", project_id)
    return True


def handle(data: str, client: billing_v1.CloudBillingClient, project_id: str, threshold: Decimal) -> bool:
    """One Pub/Sub message: True only when this call unlinked billing."""
    notification = parse_notification(data)
    if notification is None:
        # Logged, never raised: a raise would only make Pub/Sub deliver the same bad message again
        log.error("ignored a message that is not a budget notification")
        return False
    log.info(
        "cost %s %s, kill switch at %s",
        notification["costAmount"],
        notification.get("currencyCode", "?"),
        threshold,
    )
    if not should_disable(notification, threshold):
        return False
    return disable_billing(client, project_id)


@functions_framework.cloud_event
def kill_switch(cloud_event) -> None:
    """Entry point named in Terraform (google_cloudfunctions2_function.build_config.entry_point)."""
    handle(
        cloud_event.data["message"]["data"],
        billing_v1.CloudBillingClient(),
        os.environ["TARGET_PROJECT"],
        Decimal(os.environ["KILL_SWITCH_AMOUNT"]),
    )
