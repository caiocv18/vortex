resource "aws_budgets_budget" "gross_monthly" {
  name         = "portfolio-gross-monthly"
  budget_type  = "COST"
  limit_amount = format("%.1f", var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # Gross cost, before credits. The Free plan credits net every charge to zero,
  # so a budget that counts them stays silent until the credits run out.
  cost_types {
    include_credit = false
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 50
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.budget_alert_emails
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.budget_alert_emails
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = var.budget_alert_emails
  }

  # A domain registered or renewed in Route 53 trips the alerts above on its own
  # in that month; this one still fires if real spend comes on top of the fee.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 300
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.budget_alert_emails
  }
}
