variable "aws_account_id" {
  description = "Only this AWS account may host the runtime."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "Supply a 12-digit AWS account ID."
  }
}

variable "operator_principal_arn" {
  description = "IAM user/role granted cluster administration; not root or an STS session ARN."
  type        = string

  validation {
    condition = (
      startswith(var.operator_principal_arn, "arn:aws:iam::${var.aws_account_id}:") &&
      can(regex(":(user|role)/.+$", var.operator_principal_arn))
    )
    error_message = "The operator must be an IAM user or role in the selected account."
  }
}

variable "admin_ipv4_cidr" {
  description = "The operator's public IPv4 /32 permitted to reach the EKS API."
  type        = string

  validation {
    condition = (
      can(cidrhost(var.admin_ipv4_cidr, 0)) &&
      can(regex("^[0-9.]+/32$", var.admin_ipv4_cidr))
    )
    error_message = "Supply one IPv4 address with /32; do not open the API to 0.0.0.0/0."
  }
}

variable "high_availability" {
  description = "Development uses one zonal NAT; HA exercises use one NAT per AZ. Data redundancy is added in the data increment."
  type        = bool
  default     = false
}

# A staged worker migration: add capacity before draining/removing old nodes.
variable "ha_worker_migration" {
  type    = string
  default = "legacy"
  validation {
    condition     = contains(["legacy", "expand", "complete"], var.ha_worker_migration)
    error_message = "Use legacy, expand, or complete; complete is only for drained legacy groups."
  }
}

variable "ha_apply_immediately" {
  description = "Apply reviewed RDS/Redis HA changes in the approved migration window. Check pending changes first."
  type        = bool
  default     = false
}
