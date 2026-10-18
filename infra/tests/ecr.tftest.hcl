# Mocked plans exercise the configuration without AWS credentials or resources.
mock_provider "aws" {}

variables {
  aws_account_id = "123456789012"
}

run "service_repositories" {
  command = plan

  assert {
    condition = toset([
      for repository in aws_ecr_repository.service : repository.name
    ]) == toset(["carelink/core", "carelink/visit", "carelink/report", "carelink/notification"])
    error_message = "Repository names must match the existing deployment script for all four services."
  }

  assert {
    condition = alltrue([
      for repository in aws_ecr_repository.service :
      repository.image_tag_mutability == "IMMUTABLE" &&
      repository.image_scanning_configuration[0].scan_on_push &&
      !repository.force_delete
    ])
    error_message = "Release images must keep immutable tags, enable scanning and resist deletion while populated."
  }

  assert {
    condition     = output.image_registry == "123456789012.dkr.ecr.ap-southeast-1.amazonaws.com"
    error_message = "The default registry must point to the selected account in Singapore."
  }
}
