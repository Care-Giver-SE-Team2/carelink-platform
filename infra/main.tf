provider "aws" {
  region              = var.aws_region
  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      Project   = "CareLink"
      ManagedBy = "Terraform"
    }
  }
}

locals {
  services = toset(["core", "visit", "report", "notification"])
}

resource "aws_ecr_repository" "service" {
  for_each = local.services

  name                 = "carelink/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}
