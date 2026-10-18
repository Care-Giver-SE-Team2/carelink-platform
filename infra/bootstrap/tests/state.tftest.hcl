mock_provider "aws" {}

variables {
  aws_account_id = "123456789012"
}

run "protected_state_bucket" {
  command = plan

  assert {
    condition = (
      aws_s3_bucket_public_access_block.state.block_public_acls &&
      aws_s3_bucket_public_access_block.state.block_public_policy &&
      aws_s3_bucket_public_access_block.state.ignore_public_acls &&
      aws_s3_bucket_public_access_block.state.restrict_public_buckets &&
      aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled" &&
      one(aws_s3_bucket_server_side_encryption_configuration.state.rule).apply_server_side_encryption_by_default[0].sse_algorithm == "AES256" &&
      !aws_s3_bucket.state.force_destroy
    )
    error_message = "State storage must remain private, versioned and encrypted, with no forced object cleanup."
  }

  assert {
    condition = (
      jsondecode(aws_s3_bucket_policy.state.policy).Statement[0].Effect == "Deny" &&
      jsondecode(aws_s3_bucket_policy.state.policy).Statement[0].Condition.Bool["aws:SecureTransport"] == "false"
    )
    error_message = "State access must reject unencrypted transport."
  }
}
