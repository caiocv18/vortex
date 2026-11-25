variable "org_id" {
  description = "Numeric id of the Google Cloud organization (gcloud organizations list). Set it in terraform.tfvars (git-ignored)."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{6,20}$", var.org_id))
    error_message = "Use the numeric organization id, without the organizations/ prefix."
  }
}

variable "operator_member" {
  description = "Principal that runs Terraform and the sandbox scripts, as user:<email> or group:<email>. It gets Token Creator on the two service accounts it acts as. Set it in terraform.tfvars."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^(user|group):[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.operator_member))
    error_message = "Use user:<email> or group:<email>."
  }
}

variable "billing_account" {
  description = "Billing account linked to the ingestion and analytics projects; with it come the budget, the kill-switch and the paid APIs. While null, both run in the BigQuery sandbox: no billing, partitions expire by event date (raw_retention_days, 60 days curated) and views on a fixed date. Set it in terraform.tfvars."
  type        = string
  default     = null

  validation {
    condition     = var.billing_account == null || can(regex("^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$", var.billing_account))
    error_message = "Use the billing account id, such as 012345-6789AB-CDEF01."
  }
}

variable "region" {
  description = "Location of the datasets, the buckets, the HMAC secret and the Dataflow workers."
  type        = string
  default     = "us-central1"
}

variable "project_prefix" {
  description = "Start of the project ids: <prefix>-ingest-<suffix> and <prefix>-analytics-<suffix>."
  type        = string
  default     = "vortex"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,11}$", var.project_prefix))
    error_message = "Use 3 to 12 lowercase letters, digits or dashes, starting with a letter."
  }
}

variable "raw_retention_days" {
  description = "Days a partition of the raw tables (personal data in clear) is kept."
  type        = number
  default     = 30

  validation {
    condition     = var.raw_retention_days >= 1 && var.raw_retention_days <= 60
    error_message = "Keep the raw data between 1 and 60 days."
  }
}

variable "alert_email" {
  description = "Address that receives the budget alerts, through a Cloud Monitoring e-mail channel (Essential Contacts would refuse a Gmail address). Required with billing_account. Set it in terraform.tfvars."
  type        = string
  default     = null
  sensitive   = true

  validation {
    condition     = var.alert_email == null || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alert_email))
    error_message = "Use an e-mail address."
  }
}

variable "budget_amount" {
  description = "Monthly budget of both projects, in the billing account's currency. Alerts at 50, 90 and 100% of the gross cost (credits excluded, so a Free Trial counts as if it were paid)."
  type        = number
  default     = 5

  validation {
    condition     = var.budget_amount > 0
    error_message = "The budget must be positive."
  }
}

variable "kill_switch_amount" {
  description = "Cost of the month, in the billing account's currency, at which the kill-switch unlinks billing from the ingestion project. Keep it well below budget_amount: cost data arrives hours late."
  type        = number
  default     = 3

  validation {
    condition     = var.kill_switch_amount > 0 && var.kill_switch_amount < var.budget_amount
    error_message = "The kill-switch amount must be positive and below budget_amount."
  }
}

variable "email_test_budget" {
  description = "Adds a 1-unit budget on the ingestion project with a 1% threshold, so the first gross cost sends the alert e-mail. Turn it on to prove the e-mail, then back off."
  type        = bool
  default     = false
}
