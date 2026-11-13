variable "region" {
  description = "AWS region for the state bucket. Route 53 and Budgets are global, so one region is enough."
  type        = string
  default     = "us-east-1"
}

variable "noncurrent_version_days" {
  description = "Days a replaced or deleted state version is kept before S3 expires it."
  type        = number
  default     = 90

  validation {
    condition     = var.noncurrent_version_days >= 30
    error_message = "Keep old state versions for at least 30 days, so a bad apply can still be rolled back."
  }
}
