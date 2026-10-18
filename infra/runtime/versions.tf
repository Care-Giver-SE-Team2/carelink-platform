terraform {
  required_version = ">= 1.11, < 2.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Separate from infra/: removing a runtime must not remove ECR or GitHub OIDC.
  backend "s3" {}
}

provider "aws" {
  region              = "ap-southeast-1"
  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      Project   = "CareLink"
      ManagedBy = "Terraform"
      Component = "Runtime"
    }
  }
}
