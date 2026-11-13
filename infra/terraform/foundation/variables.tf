variable "region" {
  description = "Provider region. Route 53 and Budgets are global services."
  type        = string
  default     = "us-east-1"
}

variable "domain_name" {
  description = "Apex domain of the public hosted zone; projects live on subdomains (vortex.<domain>, turnward.<domain>)."
  type        = string
  default     = "caio.engineer"

  validation {
    condition     = can(regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$", var.domain_name))
    error_message = "Use a lowercase apex domain such as example.dev, without a trailing dot."
  }
}

variable "monthly_budget_usd" {
  description = "Ceiling of the gross monthly cost budget, in US dollars."
  type        = number
  default     = 10

  validation {
    condition     = var.monthly_budget_usd > 0
    error_message = "The budget must be greater than zero."
  }
}

variable "budget_alert_emails" {
  description = "Addresses that receive the budget alerts. Set it in terraform.tfvars (git-ignored)."
  type        = list(string)
  sensitive   = true

  validation {
    condition     = length(var.budget_alert_emails) > 0 && alltrue([for e in var.budget_alert_emails : can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", e))])
    error_message = "Give at least one valid email address."
  }
}
