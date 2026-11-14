variable "region" {
  description = "Provider region of the demo."
  type        = string
  default     = "us-east-1"
}

variable "demo_enabled" {
  description = "Whether the demo instance exists. The network, the role and the data volume stay either way, so turning it off and on keeps the data."
  type        = bool
  default     = false
}

variable "instance_type" {
  description = "Instance type of the demo. The user data downloads an aarch64 binary and the Free plan allows this type, so only t4g.small is accepted (ADR 0006)."
  type        = string
  default     = "t4g.small"

  validation {
    condition     = var.instance_type == "t4g.small"
    error_message = "Only t4g.small is accepted; see ADR 0006 for the plan B by architecture."
  }
}

variable "availability_zone_id" {
  description = "Availability zone id (not name) of the subnet and the data volume. Changing it replaces the subnet and the volume, so it is fixed once."
  type        = string
  default     = "use1-az1"

  validation {
    condition     = can(regex("^[a-z]{2,4}[0-9]-az[0-9]+$", var.availability_zone_id))
    error_message = "Give an availability zone id such as use1-az1, not a zone name such as us-east-1a."
  }
}

variable "root_volume_size_gb" {
  description = "Size of the root volume (gp3), in GiB. It goes away with the instance."
  type        = number
  default     = 10

  validation {
    condition     = var.root_volume_size_gb >= 8 && var.root_volume_size_gb <= 20
    error_message = "The Amazon Linux 2023 image needs at least 8 GiB; keep it at 20 GiB or less."
  }
}

variable "data_volume_size_gb" {
  description = "Size of the data volume (gp3) mounted at /var/lib/docker/volumes, in GiB. It outlives the instance and can only grow: the user data grows the filesystem on the next launch."
  type        = number
  default     = 5

  validation {
    condition     = var.data_volume_size_gb >= 5 && var.data_volume_size_gb <= 20
    error_message = "Keep the data volume between 5 and 20 GiB. An EBS volume cannot shrink, and the first one was created with 5 GiB."
  }
}

variable "compose_version" {
  description = "Docker Compose release installed as a CLI plugin by the user data."
  type        = string
  default     = "5.7.1"

  validation {
    condition     = can(regex("^[0-9]+\\.[0-9]+\\.[0-9]+$", var.compose_version))
    error_message = "Give a release version such as 5.7.1, without the leading v."
  }
}

variable "compose_sha256" {
  description = "SHA-256 of docker-compose-linux-aarch64 for compose_version, checked by the user data before installing it."
  type        = string
  default     = "f2f49f903240895fa764c0dca3a14548b754ad9426f6b609ccaf55ec5a3aa2a5"

  validation {
    condition     = can(regex("^[0-9a-f]{64}$", var.compose_sha256))
    error_message = "Give the SHA-256 as 64 lowercase hex digits."
  }
}
