variable "domain_name" {
  description = "Apex domain of the public hosted zone created by the foundation stack. The inventory app is served at the apex."
  type        = string
  default     = "caiocv.dev"

  validation {
    condition     = can(regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$", var.domain_name))
    error_message = "Use a lowercase apex domain such as example.dev, without a trailing dot."
  }
}

variable "login_subdomain" {
  description = "Label of the subdomain that serves the login app (login.<domain_name>). The certificate covers it through its *.<domain_name> name."
  type        = string
  default     = "login"

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$", var.login_subdomain))
    error_message = "Give a single lowercase DNS label such as login, without dots."
  }
}
