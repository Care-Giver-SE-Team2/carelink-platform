mock_provider "aws" {
  mock_resource "aws_eks_cluster" {
    defaults = { arn = "arn:aws:eks:ap-southeast-1:123456789012:cluster/carelink-runtime" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/CareLinkRuntimeMock" }
  }
  mock_resource "aws_launch_template" {
    defaults = { id = "lt-0123456789abcdef0", latest_version = 1 }
  }
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:ap-southeast-1:123456789012:carelink-staging-events" }
  }
  mock_resource "aws_sqs_queue" {
    defaults = {
      arn = "arn:aws:sqs:ap-southeast-1:123456789012:carelink-staging-mock"
      url = "https://sqs.ap-southeast-1.amazonaws.com/123456789012/carelink-staging-mock"
    }
  }
}

variables {
  aws_account_id         = "123456789012"
  operator_principal_arn = "arn:aws:iam::123456789012:user/carelink-dev"
  admin_ipv4_cidr        = "203.0.113.10/32"
}

run "messaging_disabled_by_default" {
  command = plan
  assert {
    condition = (
      length(aws_sns_topic.events) == 0 && length(aws_sqs_queue.events) == 0 &&
      length(aws_iam_role.service) == 0 && length(aws_eks_pod_identity_association.service) == 0
    )
    error_message = "The existing network/data increments must not silently create workload resources."
  }
}

run "staging_routing_retention_and_permissions" {
  command = apply
  variables { enable_messaging = true }

  override_resource {
    target = aws_sqs_queue.events["report"]
    values = { arn = "arn:aws:sqs:ap-southeast-1:123456789012:carelink-staging-report" }
  }
  override_resource {
    target = aws_sqs_queue.events["notification"]
    values = { arn = "arn:aws:sqs:ap-southeast-1:123456789012:carelink-staging-notification" }
  }

  assert {
    condition = (
      length(aws_sqs_queue.events) == 4 && length(aws_sqs_queue.dead_letter) == 4 &&
      toset(keys(aws_sns_topic_subscription.events)) == toset(["report", "notification"]) &&
      alltrue([for subscription in aws_sns_topic_subscription.events :
        subscription.raw_message_delivery && subscription.filter_policy_scope == "MessageAttributes"
      ]) &&
      jsondecode(aws_sns_topic_subscription.events["notification"].filter_policy).type == ["NotificationRequested"] &&
      length(jsondecode(aws_sns_topic_subscription.events["report"].filter_policy).type) == 15
    )
    error_message = "Only current consumers should subscribe, with raw envelopes and type-attribute filters."
  }
  assert {
    condition = alltrue([for service, queue in aws_sqs_queue.events :
      queue.sqs_managed_sse_enabled && queue.visibility_timeout_seconds == 300 &&
      queue.receive_wait_time_seconds == 20 &&
      jsondecode(queue.redrive_policy).maxReceiveCount == 5 &&
      jsondecode(queue.redrive_policy).deadLetterTargetArn == aws_sqs_queue.dead_letter[service].arn &&
      aws_sqs_queue.dead_letter[service].sqs_managed_sse_enabled &&
      aws_sqs_queue.dead_letter[service].message_retention_seconds > queue.message_retention_seconds &&
      jsondecode(aws_sqs_queue_redrive_allow_policy.events[service].redrive_allow_policy).sourceQueueArns == [queue.arn]
    ])
    error_message = "Each encrypted queue must redrive to its own longer-retained DLQ after five receives."
  }
  assert {
    condition = alltrue([for service, policy in aws_sqs_queue_policy.sns_delivery :
      jsondecode(policy.policy).Statement[0].Principal.Service == "sns.amazonaws.com" &&
      jsondecode(policy.policy).Statement[0].Condition.ArnEquals["aws:SourceArn"] == aws_sns_topic.events[0].arn &&
      jsondecode(policy.policy).Statement[0].Condition.StringEquals["aws:SourceAccount"] == var.aws_account_id
    ])
    error_message = "A different topic or account must not gain SNS delivery permission."
  }
  assert {
    condition = alltrue([for service, role in aws_iam_role.service :
      jsondecode(role.assume_role_policy).Statement[0].Principal.Service == "pods.eks.amazonaws.com" &&
      jsondecode(role.assume_role_policy).Statement[0].Condition.StringEquals["aws:RequestTag/eks-cluster-arn"] == aws_eks_cluster.runtime.arn &&
      jsondecode(role.assume_role_policy).Statement[0].Condition.StringEquals["aws:RequestTag/kubernetes-namespace"] == "staging" &&
      jsondecode(role.assume_role_policy).Statement[0].Condition.StringEquals["aws:RequestTag/kubernetes-service-account"] == service &&
      role.permissions_boundary == "arn:aws:iam::123456789012:policy/CareLinkRuntimeWorkloadBoundary" &&
      aws_eks_pod_identity_association.service[service].service_account == service &&
      !aws_eks_pod_identity_association.service[service].disable_session_tags
    ])
    error_message = "An identity must be restricted to this cluster, environment and service account."
  }
  assert {
    condition = (
      alltrue([for service in ["core", "visit"] :
        length(jsondecode(aws_iam_role_policy.service_events[service].policy).Statement) == 1 &&
        jsondecode(aws_iam_role_policy.service_events[service].policy).Statement[0].Action == ["sns:Publish"] &&
        jsondecode(aws_iam_role_policy.service_events[service].policy).Statement[0].Resource == aws_sns_topic.events[0].arn
      ]) &&
      length(jsondecode(aws_iam_role_policy.service_events["report"].policy).Statement) == 2 &&
      jsondecode(aws_iam_role_policy.service_events["report"].policy).Statement[1].Resource == aws_sqs_queue.events["report"].arn &&
      jsondecode(aws_iam_role_policy.service_events["report"].policy).Statement[1].Action == ["sqs:ReceiveMessage", "sqs:DeleteMessage"] &&
      length(jsondecode(aws_iam_role_policy.service_events["notification"].policy).Statement) == 1 &&
      jsondecode(aws_iam_role_policy.service_events["notification"].policy).Statement[0].Resource == aws_sqs_queue.events["notification"].arn &&
      jsondecode(aws_iam_role_policy.service_events["notification"].policy).Statement[0].Action == ["sqs:ReceiveMessage", "sqs:DeleteMessage"] &&
      aws_sqs_queue.events["report"].arn != aws_sqs_queue.events["notification"].arn
    )
    error_message = "Publishers must not consume; consumers must not access another service's queue or admin APIs."
  }
}

run "demo_identity_is_separate" {
  command = apply
  variables {
    enable_messaging = true
    data_environment = "demo"
  }
  assert {
    condition = (
      aws_sns_topic.events[0].name == "carelink-demo-events" &&
      alltrue([for service, role in aws_iam_role.service :
        role.name == "CareLinkRuntime-demo-${service}" &&
        jsondecode(role.assume_role_policy).Statement[0].Condition.StringEquals["aws:RequestTag/kubernetes-namespace"] == "demo" &&
        aws_eks_pod_identity_association.service[service].namespace == "demo"
      ])
    )
    error_message = "Demo must have distinct resource names and namespace trust."
  }
}
