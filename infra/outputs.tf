output "image_registry" {
  description = "IMAGE_REGISTRY for scripts/deploy.sh."
  value       = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}

output "repository_urls" {
  description = "ECR repository URL for each service."
  value       = { for service, repository in aws_ecr_repository.service : service => repository.repository_url }
}

output "ecr_publish_role_arn" {
  description = "AWS_ECR_PUBLISH_ROLE_ARN repository variable for GitHub Actions."
  value       = aws_iam_role.ecr_publish.arn
}
