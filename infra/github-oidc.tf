# Repository created 2026-10-07: GitHub's default subject includes immutable IDs.
locals {
  github_main_subject = "repo:Care-Giver-SE-Team2@323581092/carelink-platform@1408273257:ref:refs/heads/main"
}

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "ecr_publish" {
  name                 = "CareLinkGitHubEcrPublish"
  max_session_duration = 3600
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = local.github_main_subject
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "ecr_publish" {
  name = "PublishCareLinkImages"
  role = aws_iam_role.ecr_publish.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid    = "PublishAndVerifyImages"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage",
          "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:DescribeImages"
        ]
        Resource = [for repository in aws_ecr_repository.service : repository.arn]
      }
    ]
  })
}
