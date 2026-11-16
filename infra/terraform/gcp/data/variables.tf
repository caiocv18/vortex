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
  description = "Billing account linked to the ingestion and analytics projects. While null, both run in the BigQuery sandbox: no billing, and tables and partitions expire in 60 days."
  type        = string
  default     = null

  validation {
    condition     = var.billing_account == null || can(regex("^[0-9A-F]{6}-[0-9A-F]{6}-[0-9A-F]{6}$", var.billing_account))
    error_message = "Use the billing account id, such as 012345-6789AB-CDEF01."
  }
}

variable "region" {
  description = "Location of the datasets and, later, of the buckets and the Dataflow workers."
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
    error_message = "Keep the raw data between 1 and 60 days (60 is also the sandbox ceiling)."
  }
}
