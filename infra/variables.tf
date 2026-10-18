variable "aws_account_id" {
  description = "The AWS account that this configuration is allowed to manage."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account ID."
  }
}

variable "aws_region" {
  description = "AWS region for the CareLink infrastructure."
  type        = string
  default     = "ap-southeast-1"
}
