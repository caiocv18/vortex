variable "name" {
  description = "Short key of the site (app, login), used in resource names and the origin id."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,15}$", var.name))
    error_message = "Use a short lowercase key such as app or login."
  }
}

variable "domain_name" {
  description = "Host the site answers on: the distribution's alias and the name of its A, AAAA and HTTPS alias records."
  type        = string
}

variable "bucket_name" {
  description = "Name of the private bucket that holds the built site, in the account regional namespace (ends in -an)."
  type        = string

  validation {
    condition     = endswith(var.bucket_name, "-an") && length(var.bucket_name) <= 63
    error_message = "Give a bucket name in the account regional namespace (ending in -an), at most 63 characters."
  }
}

variable "zone_id" {
  description = "Id of the public hosted zone that gets the alias records."
  type        = string
}

variable "certificate_arn" {
  description = "ARN of an issued ACM certificate in us-east-1 that covers domain_name."
  type        = string
}

variable "cache_policy_id" {
  description = "Id of the cache policy of the default behavior."
  type        = string
}

variable "response_headers_policy_id" {
  description = "Id of the response headers policy of the default behavior."
  type        = string
}

variable "function_arn" {
  description = "ARN of the CloudFront Function run on the viewer-request event."
  type        = string
}
