mock_provider "aws" {}

variables {
  aws_account_id = "123456789012"
}

# Mocked apply resolves generated ARNs without contacting AWS.
run "main_only_publisher" {
  command = apply

  assert {
    condition = jsondecode(aws_iam_role.ecr_publish.assume_role_policy).Statement[0].Condition == {
      StringEquals = {
        "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        "token.actions.githubusercontent.com:sub" = "repo:Care-Giver-SE-Team2@323581092/carelink-platform@1408273257:ref:refs/heads/main"
      }
    }
    error_message = "Only this repository's main branch with the AWS audience may assume the role; PRs and other branches must not match."
  }

  assert {
    condition = (
      aws_iam_openid_connect_provider.github.url == "https://token.actions.githubusercontent.com" &&
      toset(aws_iam_openid_connect_provider.github.client_id_list) == toset(["sts.amazonaws.com"]) &&
      jsondecode(aws_iam_role.ecr_publish.assume_role_policy).Statement[0].Principal.Federated == aws_iam_openid_connect_provider.github.arn
    )
    error_message = "The role must trust GitHub OIDC with the AWS audience only."
  }

  assert {
    condition = (
      length(jsondecode(aws_iam_role_policy.ecr_publish.policy).Statement) == 2 &&
      toset(jsondecode(aws_iam_role_policy.ecr_publish.policy).Statement[0].Action) == toset(["ecr:GetAuthorizationToken"]) &&
      jsondecode(aws_iam_role_policy.ecr_publish.policy).Statement[0].Resource == "*" &&
      toset(jsondecode(aws_iam_role_policy.ecr_publish.policy).Statement[1].Resource) == toset([for repository in aws_ecr_repository.service : repository.arn]) &&
      toset(jsondecode(aws_iam_role_policy.ecr_publish.policy).Statement[1].Action) == toset([
        "ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer", "ecr:DescribeImages"
      ])
    )
    error_message = "The publisher may only authenticate and publish/query images in the four managed repositories."
  }
}
