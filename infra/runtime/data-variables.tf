variable "enable_data_services" {
  description = "Opt into RDS, Redis and populated credential Secrets; false keeps the network/EKS-only increment."
  type        = bool
  default     = false
}

variable "data_environment" {
  description = "One complete environment at a time on the two medium nodes."
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "demo"], var.data_environment)
    error_message = "Choose staging or demo."
  }
}

variable "data_run_id" {
  description = "Stable ID for this data deployment, such as 20261012-01. Use a new ID after teardown to avoid retained snapshot/Secret name conflicts."
  type        = string
  default     = null

  validation {
    condition     = !var.enable_data_services || can(regex("^[a-z0-9][a-z0-9-]{3,23}$", var.data_run_id))
    error_message = "Enabled data services require a 4-24 character lowercase run ID."
  }
}

variable "database_deletion_protection" {
  description = "Protect the primary database. Disable explicitly in a reviewed update before teardown; final snapshots remain mandatory."
  type        = bool
  default     = true
}

variable "database_snapshot_identifier" {
  description = "Restore the primary from an existing CareLink final snapshot; null creates an empty database."
  type        = string
  default     = null

  validation {
    condition     = var.database_snapshot_identifier == null || can(regex("^carelink-(staging|demo)-mysql-final-[a-z0-9-]+$", var.database_snapshot_identifier))
    error_message = "Use an existing CareLink final snapshot identifier."
  }
}

variable "data_credentials_version" {
  description = "Increment only with a coordinated password update and database-user bootstrap; write-only values cannot be compared for drift."
  type        = number
  default     = 1

  validation {
    condition     = var.data_credentials_version >= 1 && floor(var.data_credentials_version) == var.data_credentials_version
    error_message = "Use a positive integer credential version."
  }
}

variable "data_credentials" {
  description = "Local credentials supplied again for plan/apply; omitted from Terraform state and saved plans. Never put them in versioned files."
  type = object({
    mysql_admin_password = string
    redis_auth_token     = string
    service_passwords    = map(string)
  })
  default   = null
  sensitive = true
  ephemeral = true

  validation {
    condition = !var.enable_data_services || try(
      toset(keys(var.data_credentials.service_passwords)) == toset(["core", "visit", "report", "notification"]) &&
      length(var.data_credentials.mysql_admin_password) <= 41 &&
      alltrue([for password in concat(
        [var.data_credentials.mysql_admin_password, var.data_credentials.redis_auth_token],
        values(var.data_credentials.service_passwords)
      ) : can(regex("^[A-Za-z0-9]{32,128}$", password))]) &&
      length(toset(concat(
        [var.data_credentials.mysql_admin_password, var.data_credentials.redis_auth_token],
        values(var.data_credentials.service_passwords)
      ))) == 6,
      false
    )
    error_message = "Supply six distinct alphanumeric credentials: a 32-41 character RDS MySQL administrator password and 32-128 characters for Redis and each service."
  }
}
